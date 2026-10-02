/*
 * Aow Monika: JNI cho com.swordfish.libretrodroid.RetroAchievements.
 */

#include <jni.h>

#include <string>

#include "achievements.h"
#include "libretrodroid.h"

namespace {
std::string toStdString(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}
}

extern "C" {

// Gọi từ Java trước khi nạp game: lưu cấu hình, LibretroDroid dựng Achievements sau retro_load_game.
JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_RetroAchievements_nativeConfigure(
    JNIEnv* env, jclass, jint consoleId, jstring userAgent, jboolean hardcore, jstring user, jstring token
) {
    libretrodroid::Achievements::initJava(env);
    libretrodroid::Achievements::Config config;
    config.consoleId = consoleId;
    config.userAgent = toStdString(env, userAgent);
    config.hardcore = hardcore;
    config.user = toStdString(env, user);
    config.token = toStdString(env, token);
    libretrodroid::LibretroDroid::getInstance().configureAchievements(config);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_RetroAchievements_nativeOnHttpResponse(
    JNIEnv* env, jclass, jlong id, jint status, jbyteArray body
) {
    jsize len = body ? env->GetArrayLength(body) : 0;
    std::string data(static_cast<size_t>(len), '\0');
    if (len > 0) env->GetByteArrayRegion(body, 0, len, reinterpret_cast<jbyte*>(&data[0]));
    libretrodroid::Achievements::dispatchHttpResponse(id, status, data.c_str(), data.size());
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_RetroAchievements_nativeDescribe(JNIEnv* env, jclass) {
    auto* a = libretrodroid::Achievements::current();
    std::string json = a ? a->describeJson() : std::string();
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_RetroAchievements_nativeSetHardcore(JNIEnv*, jclass, jboolean enabled) {
    auto* a = libretrodroid::Achievements::current();
    if (a) a->setHardcore(enabled);
}

}
