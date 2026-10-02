/*
 * Aow Monika: cầu nối RetroAchievements (rcheevos rc_client) cho LibretroDroid.
 * Nhận khung hình từ vòng chạy lõi (doFrame), đọc bộ nhớ lõi, gọi mạng qua Java và báo sự kiện lên Java.
 * Giấy phép: GPLv3 như phần còn lại của LibretroDroid.
 */

#ifndef LIBRETRODROID_ACHIEVEMENTS_H
#define LIBRETRODROID_ACHIEVEMENTS_H

#include <jni.h>

#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <string>

#include "core.h"
#include "rcheevos/include/rc_client.h"
#include "rcheevos/src/rc_libretro.h"

namespace libretrodroid {

class Achievements {
public:
    struct Config {
        int consoleId = 0;
        std::string userAgent;   // "AowMonika/x.y (Android z)"; native tự nối thêm mệnh đề rcheevos
        bool hardcore = false;
        std::string user;
        std::string token;
    };

    // Gọi 1 lần khi nạp thư viện (từ JNI) để native gọi ngược được lên Java.
    static void initJava(JNIEnv* env);
    static Achievements* current();

    Achievements(Core* core, const Config& config);
    ~Achievements();

    // Sau retro_load_game thành công: dựng vùng nhớ, đăng nhập bằng token rồi nhận diện + nạp game.
    void start(const std::string& gamePath, const struct retro_memory_map* mmap);
    // Gọi sau MỖI retro_run.
    void doFrame();
    // Java trả kết quả HTTP (từ luồng bất kỳ).
    void onHttpResponse(int64_t id, int status, const char* body, size_t length);
    // Như onHttpResponse nhưng an toàn khi đối tượng đã bị hủy (bỏ qua).
    static void dispatchHttpResponse(int64_t id, int status, const char* body, size_t length);
    // Thông tin game + danh sách thành tựu dạng JSON (rỗng nếu chưa nạp xong).
    std::string describeJson();
    void setHardcore(bool enabled);

private:
    static uint32_t readMemory(uint32_t address, uint8_t* buffer, uint32_t numBytes, rc_client_t* client);
    static void serverCall(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callbackData, rc_client_t* client);
    static void handleEvent(const rc_client_event_t* event, rc_client_t* client);
    static void loginCallback(int result, const char* errorMessage, rc_client_t* client, void* userdata);
    static void loadCallback(int result, const char* errorMessage, rc_client_t* client, void* userdata);
    static void logCallback(const char* message, const rc_client_t* client);
    static void coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info);

    struct Pending {
        rc_client_server_callback_t callback;
        void* callbackData;
    };

    Core* core;
    Config config;
    std::string fullUserAgent;
    std::string gamePath;
    rc_client_t* client = nullptr;
    rc_libretro_memory_regions_t regions {};
    bool regionsReady = false;

    std::mutex pendingLock;
    std::map<int64_t, Pending> pending;
    int64_t nextRequestId = 1;
};

}

#endif //LIBRETRODROID_ACHIEVEMENTS_H
