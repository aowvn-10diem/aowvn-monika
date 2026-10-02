/*
 * Aow Monika: cầu nối RetroAchievements (xem achievements.h).
 */

#include "achievements.h"

#include <android/log.h>

#include <cstring>
#include <sstream>

#define RA_TAG "MonikaRA"
#define RA_LOGW(...) __android_log_print(ANDROID_LOG_WARN, RA_TAG, __VA_ARGS__)
#define RA_LOGI(...) __android_log_print(ANDROID_LOG_INFO, RA_TAG, __VA_ARGS__)

namespace libretrodroid {

namespace {
JavaVM* javaVm = nullptr;
jclass raClass = nullptr;            // com.swordfish.libretrodroid.RetroAchievements (global ref)
jmethodID midHttpRequest = nullptr;  // (long id, String url, String post, String contentType, String userAgent)
jmethodID midOnEvent = nullptr;      // (int type, int id, String title, String desc, String badgeUrl, int points)
jmethodID midOnState = nullptr;      // (int what, String message)
Achievements* instance = nullptr;
std::mutex instanceLock; // bảo vệ `instance` giữa luồng mạng (phản hồi) và luồng chạy lõi (hủy)
Core* activeCore = nullptr;

// Lấy JNIEnv của luồng hiện tại, gắn luồng vào JVM nếu cần (luồng mạng/luồng native khác).
struct EnvScope {
    JNIEnv* env = nullptr;
    bool attached = false;
    EnvScope() {
        if (!javaVm) return;
        if (javaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (javaVm->AttachCurrentThread(&env, nullptr) == JNI_OK) attached = true; else env = nullptr;
        }
    }
    ~EnvScope() { if (attached && javaVm) javaVm->DetachCurrentThread(); }
};

jstring toJString(JNIEnv* env, const char* s) { return s ? env->NewStringUTF(s) : nullptr; }

void notifyState(int what, const char* message) {
    EnvScope scope;
    if (!scope.env || !raClass || !midOnState) return;
    jstring msg = toJString(scope.env, message);
    scope.env->CallStaticVoidMethod(raClass, midOnState, what, msg);
    if (msg) scope.env->DeleteLocalRef(msg);
    if (scope.env->ExceptionCheck()) scope.env->ExceptionClear();
}

std::string jsonEscape(const char* s) {
    std::string out;
    if (!s) return out;
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(s); *p; ++p) {
        switch (*p) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (*p < 0x20) { char buf[8]; snprintf(buf, sizeof(buf), "\\u%04x", *p); out += buf; }
                else out += static_cast<char>(*p);
        }
    }
    return out;
}
}

void Achievements::initJava(JNIEnv* env) {
    if (javaVm) return;
    env->GetJavaVM(&javaVm);
    jclass local = env->FindClass("com/swordfish/libretrodroid/RetroAchievements");
    if (!local) { env->ExceptionClear(); RA_LOGW("Không tìm thấy lớp RetroAchievements"); return; }
    raClass = reinterpret_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    midHttpRequest = env->GetStaticMethodID(raClass, "httpRequest", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    midOnEvent = env->GetStaticMethodID(raClass, "onEvent", "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
    midOnState = env->GetStaticMethodID(raClass, "onState", "(ILjava/lang/String;)V");
    if (env->ExceptionCheck()) { env->ExceptionClear(); RA_LOGW("Thiếu phương thức Java của RetroAchievements"); }
}

Achievements* Achievements::current() { return instance; }

void Achievements::dispatchHttpResponse(int64_t id, int status, const char* body, size_t length) {
    std::lock_guard<std::mutex> guard(instanceLock);
    if (instance) instance->onHttpResponse(id, status, body, length);
}

Achievements::Achievements(Core* core, const Config& config) : core(core), config(config) {
    instance = this;
    activeCore = core;
    client = rc_client_create(&Achievements::readMemory, &Achievements::serverCall);
    rc_client_set_userdata(client, this);
    rc_client_set_event_handler(client, &Achievements::handleEvent);
    rc_client_enable_logging(client, RC_CLIENT_LOG_LEVEL_WARN, &Achievements::logCallback);
    rc_client_set_hardcore_enabled(client, config.hardcore ? 1 : 0);
    char clause[128] = {0};
    rc_client_get_user_agent_clause(client, clause, sizeof(clause));
    fullUserAgent = config.userAgent + " " + clause;
}

Achievements::~Achievements() {
    std::lock_guard<std::mutex> guard(instanceLock);
    if (client) {
        rc_client_unload_game(client);
        rc_client_destroy(client);
        client = nullptr;
    }
    if (regionsReady) { rc_libretro_memory_destroy(&regions); regionsReady = false; }
    if (instance == this) { instance = nullptr; activeCore = nullptr; }
}

void Achievements::coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info) {
    info->data = nullptr;
    info->size = 0;
    if (!activeCore) return;
    info->data = static_cast<uint8_t*>(activeCore->retro_get_memory_data(id));
    info->size = activeCore->retro_get_memory_size(id);
}

void Achievements::start(const std::string& path, const struct retro_memory_map* mmap) {
    gamePath = path;
    if (!rc_libretro_memory_init(&regions, mmap, &Achievements::coreMemoryInfo, static_cast<uint32_t>(config.consoleId))) {
        // Lõi không cho đọc bộ nhớ → thành tựu không chạy được; vẫn để rc_client hoạt động (đọc trả 0) nhưng báo Java.
        RA_LOGW("rc_libretro_memory_init thất bại (console %d): lõi không khai bộ nhớ", config.consoleId);
        notifyState(4, "Lõi giả lập này chưa hỗ trợ đọc bộ nhớ cho thành tựu.");
        return;
    }
    regionsReady = true;
    rc_client_begin_login_with_token(client, config.user.c_str(), config.token.c_str(), &Achievements::loginCallback, this);
}

void Achievements::loginCallback(int result, const char* errorMessage, rc_client_t* client, void* userdata) {
    auto* self = static_cast<Achievements*>(userdata);
    if (result != RC_OK) {
        RA_LOGW("Đăng nhập RA lỗi: %s", errorMessage ? errorMessage : "?");
        notifyState(1, errorMessage ? errorMessage : "Đăng nhập RetroAchievements thất bại");
        return;
    }
    rc_client_begin_identify_and_load_game(client, static_cast<uint32_t>(self->config.consoleId),
        self->gamePath.c_str(), nullptr, 0, &Achievements::loadCallback, self);
}

void Achievements::loadCallback(int result, const char* errorMessage, rc_client_t* client, void* userdata) {
    if (result != RC_OK) {
        RA_LOGW("Nạp game RA lỗi (%d): %s", result, errorMessage ? errorMessage : "?");
        notifyState(3, errorMessage ? errorMessage : "Game này chưa có thành tựu trên RetroAchievements");
        return;
    }
    const rc_client_game_t* game = rc_client_get_game_info(client);
    RA_LOGI("Đã nạp game RA: %s", game && game->title ? game->title : "?");
    notifyState(2, game ? game->title : nullptr);
}

void Achievements::doFrame() {
    if (client && regionsReady) rc_client_do_frame(client);
}

void Achievements::setHardcore(bool enabled) {
    if (client) rc_client_set_hardcore_enabled(client, enabled ? 1 : 0);
}

uint32_t Achievements::readMemory(uint32_t address, uint8_t* buffer, uint32_t numBytes, rc_client_t* client) {
    auto* self = static_cast<Achievements*>(rc_client_get_userdata(client));
    if (!self || !self->regionsReady) return 0;
    return rc_libretro_memory_read(&self->regions, address, buffer, numBytes);
}

void Achievements::serverCall(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callbackData, rc_client_t* client) {
    auto* self = static_cast<Achievements*>(rc_client_get_userdata(client));
    int64_t id;
    {
        std::lock_guard<std::mutex> lock(self->pendingLock);
        id = self->nextRequestId++;
        self->pending[id] = Pending { callback, callbackData };
    }
    EnvScope scope;
    bool sent = false;
    if (scope.env && raClass && midHttpRequest) {
        jstring url = toJString(scope.env, request->url);
        jstring post = toJString(scope.env, request->post_data);
        jstring type = toJString(scope.env, request->content_type);
        jstring ua = toJString(scope.env, self->fullUserAgent.c_str());
        scope.env->CallStaticVoidMethod(raClass, midHttpRequest, static_cast<jlong>(id), url, post, type, ua);
        sent = !scope.env->ExceptionCheck();
        if (!sent) scope.env->ExceptionClear();
        if (url) scope.env->DeleteLocalRef(url);
        if (post) scope.env->DeleteLocalRef(post);
        if (type) scope.env->DeleteLocalRef(type);
        if (ua) scope.env->DeleteLocalRef(ua);
    }
    if (!sent) self->onHttpResponse(id, RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR, "", 0);
}

void Achievements::onHttpResponse(int64_t id, int status, const char* body, size_t length) {
    Pending p {};
    {
        std::lock_guard<std::mutex> lock(pendingLock);
        auto it = pending.find(id);
        if (it == pending.end()) return;
        p = it->second;
        pending.erase(it);
    }
    rc_api_server_response_t response {};
    response.body = body;
    response.body_length = length;
    response.http_status_code = status;
    p.callback(&response, p.callbackData);
}

void Achievements::handleEvent(const rc_client_event_t* event, rc_client_t* client) {
    auto* self = static_cast<Achievements*>(rc_client_get_userdata(client));
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            const rc_client_achievement_t* a = event->achievement;
            EnvScope scope;
            if (!scope.env || !raClass || !midOnEvent || !a) return;
            jstring title = toJString(scope.env, a->title);
            jstring desc = toJString(scope.env, a->description);
            jstring badge = toJString(scope.env, a->badge_url);
            scope.env->CallStaticVoidMethod(raClass, midOnEvent, static_cast<jint>(event->type), static_cast<jint>(a->id), title, desc, badge, static_cast<jint>(a->points));
            if (scope.env->ExceptionCheck()) scope.env->ExceptionClear();
            if (title) scope.env->DeleteLocalRef(title);
            if (desc) scope.env->DeleteLocalRef(desc);
            if (badge) scope.env->DeleteLocalRef(badge);
            break;
        }
        case RC_CLIENT_EVENT_GAME_COMPLETED:
            notifyState(5, "Hoàn thành mọi thành tựu của game này!");
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR:
            if (event->server_error) RA_LOGW("Lỗi máy chủ RA (%s): %s", event->server_error->api, event->server_error->error_message);
            break;
        case RC_CLIENT_EVENT_DISCONNECTED:
            notifyState(6, "Mất kết nối RetroAchievements: thành tựu sẽ gửi lại khi có mạng.");
            break;
        case RC_CLIENT_EVENT_RECONNECTED:
            notifyState(7, "Đã kết nối lại RetroAchievements.");
            break;
        case RC_CLIENT_EVENT_RESET:
            // Bật hardcore giữa game: phải reset máy ảo.
            if (self && self->core && self->core->retro_reset) self->core->retro_reset();
            rc_client_reset(client);
            break;
        default:
            break;
    }
}

void Achievements::logCallback(const char* message, const rc_client_t* client) {
    RA_LOGW("%s", message);
}

std::string Achievements::describeJson() {
    if (!client || !rc_client_is_game_loaded(client)) return std::string();
    const rc_client_game_t* game = rc_client_get_game_info(client);
    rc_client_user_game_summary_t summary {};
    rc_client_get_user_game_summary(client, &summary);
    std::ostringstream out;
    out << "{\"gameId\":" << (game ? game->id : 0)
        << ",\"title\":\"" << jsonEscape(game ? game->title : "") << "\""
        << ",\"hardcore\":" << (rc_client_get_hardcore_enabled(client) ? "true" : "false")
        << ",\"total\":" << summary.num_core_achievements
        << ",\"unlocked\":" << summary.num_unlocked_achievements
        << ",\"points\":" << summary.points_core
        << ",\"pointsUnlocked\":" << summary.points_unlocked
        << ",\"achievements\":[";
    rc_client_achievement_list_t* list = rc_client_create_achievement_list(client,
        RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
    bool first = true;
    if (list) {
        for (uint32_t b = 0; b < list->num_buckets; b++) {
            const rc_client_achievement_bucket_t& bucket = list->buckets[b];
            for (uint32_t i = 0; i < bucket.num_achievements; i++) {
                const rc_client_achievement_t* a = bucket.achievements[i];
                if (!first) out << ",";
                first = false;
                out << "{\"id\":" << a->id
                    << ",\"title\":\"" << jsonEscape(a->title) << "\""
                    << ",\"description\":\"" << jsonEscape(a->description) << "\""
                    << ",\"points\":" << a->points
                    << ",\"unlocked\":" << (a->unlocked ? "true" : "false")
                    << ",\"bucket\":" << static_cast<int>(a->bucket)
                    << ",\"progress\":\"" << jsonEscape(a->measured_progress) << "\""
                    << ",\"badge\":\"" << jsonEscape(a->unlocked ? a->badge_url : a->badge_locked_url) << "\"}";
            }
        }
        rc_client_destroy_achievement_list(list);
    }
    out << "]}";
    return out.str();
}

}
