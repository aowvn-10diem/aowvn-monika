/*
 *     Aow Monika: vệt chẩn đoán quanh cửa sổ unload lõi (xem diag.h).
 */

#include "diag.h"

#include <android/log.h>

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

namespace libretrodroid {
namespace diag {

namespace {

const char* const kTag = "MonikaUnload";

// Trần danh sách thread: tiến trình :game có thể có 30–40 thread (main, GLThread,
// oboe, JavaBinder, RenderThread, GPU…), nên phải đủ chỗ cho ~48. Phần vượt trần
// (nếu có) được ghi thành "(+N)" và thread của lõi luôn được ưu tiên (xem diag::mark).
constexpr size_t kMaxThreads = 48;
constexpr size_t kMaxThreadName = 12;
constexpr size_t kMaxBuildIdDesc = 64;
constexpr size_t kMaxNotes = 65536;

// Ngân sách một dòng vệt. Diagnostics.logcat cắt mỗi dòng logcat còn 300 ký tự
// (app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:380), nên tự cắt ở 290 ký tự
// để phần bị cắt không bao giờ là core=/bid= (đặt ở đầu dòng) hay tên thread lõi.
constexpr size_t kMaxLineChars = 290;

std::mutex g_lock;
std::string g_corePath;
std::string g_coreName;
std::string g_buildId;
void* g_handle = nullptr;

bool g_dirResolved = false;
std::string g_crumbDir;
bool g_missingDirLogged = false;

// Tập tid lúc dlopen-done (trước retro_init) = mốc gốc "thread không phải của lõi".
std::set<std::string> g_baselineTids;
bool g_captureBaseline = false;

uint16_t le16(const unsigned char* p) { return (uint16_t) (p[0] | (p[1] << 8)); }

uint32_t le32(const unsigned char* p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

uint64_t le64(const unsigned char* p) {
    return (uint64_t) le32(p) | ((uint64_t) le32(p + 4) << 32);
}

std::string toHex(const unsigned char* data, size_t size) {
    static const char* const digits = "0123456789abcdef";
    std::string out;
    out.reserve(size * 2);
    for (size_t i = 0; i < size; ++i) {
        out.push_back(digits[data[i] >> 4]);
        out.push_back(digits[data[i] & 0x0f]);
    }
    return out;
}

std::string baseName(const std::string& path) {
    size_t slash = path.find_last_of('/');
    return slash == std::string::npos ? path : path.substr(slash + 1);
}

/**
 * Đọc GNU BuildId từ note PT_NOTE của file .so. Chỉ đọc phần header + note,
 * có kiểm biên; mọi lỗi trả chuỗi rỗng (không ném).
 */
std::string readBuildId(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return "";

    unsigned char eh[64];
    ssize_t got = pread(fd, eh, sizeof eh, 0);
    if (got < 52 || memcmp(eh, "\177ELF", 4) != 0) {
        close(fd);
        return "";
    }

    bool is64 = eh[4] == 2;
    uint64_t phoff;
    uint16_t phentsize;
    uint16_t phnum;
    size_t minPhentsize = is64 ? 56 : 32;

    if (is64) {
        if (got < 64) { close(fd); return ""; }
        phoff = le64(eh + 32);
        phentsize = le16(eh + 54);
        phnum = le16(eh + 56);
    } else {
        phoff = le32(eh + 28);
        phentsize = le16(eh + 42);
        phnum = le16(eh + 44);
    }
    if (phentsize < minPhentsize || phnum == 0 || phnum > 512 || phoff == 0) {
        close(fd);
        return "";
    }

    std::vector<unsigned char> ph(phentsize);
    for (uint16_t i = 0; i < phnum; ++i) {
        off_t at = (off_t) (phoff + (uint64_t) i * phentsize);
        if (pread(fd, ph.data(), phentsize, at) != (ssize_t) phentsize) break;
        if (le32(ph.data()) != 4 /* PT_NOTE */) continue;

        uint64_t offset;
        uint64_t size;
        if (is64) {
            offset = le64(ph.data() + 8);
            size = le64(ph.data() + 32);
        } else {
            offset = le32(ph.data() + 4);
            size = le32(ph.data() + 16);
        }
        if (size == 0 || size > kMaxNotes) continue;

        std::vector<unsigned char> notes(size);
        if (pread(fd, notes.data(), size, (off_t) offset) != (ssize_t) size) continue;

        size_t p = 0;
        while (p + 12 <= notes.size()) {
            uint32_t namesz = le32(&notes[p]);
            uint32_t descsz = le32(&notes[p + 4]);
            uint32_t type = le32(&notes[p + 8]);
            p += 12;

            if (p + namesz > notes.size()) break;
            std::string name((const char*) &notes[p], namesz);
            p = (p + namesz + 3u) & ~(size_t) 3u;

            if (p + descsz > notes.size()) break;
            if (type == 3 /* NT_GNU_BUILD_ID */ && name.rfind("GNU", 0) == 0
                && descsz > 0 && descsz <= kMaxBuildIdDesc) {
                std::string id = toHex(&notes[p], descsz);
                close(fd);
                return id;
            }
            p = (p + descsz + 3u) & ~(size_t) 3u;
        }
    }

    close(fd);
    return "";
}

struct ThreadInfo {
    std::string tid;
    std::string name;
    bool isNew = false;
};

/**
 * Đọc thread còn sống của tiến trình: tid + tên (/proc/self/task/<tid>/comm).
 * Trả false khi không mở được thư mục (ghi "thr=?"), không ném.
 */
bool collectThreads(std::vector<ThreadInfo>* out) {
    out->clear();
    DIR* dir = opendir("/proc/self/task");
    if (dir == nullptr) return false;

    errno = 0;
    while (struct dirent* entry = readdir(dir)) {
        if (entry->d_name[0] == '.') continue;

        char commPath[320];
        snprintf(commPath, sizeof commPath, "/proc/self/task/%s/comm", entry->d_name);
        std::string name = "?";
        int fd = open(commPath, O_RDONLY | O_CLOEXEC);
        if (fd >= 0) {
            char buf[64];
            ssize_t n = read(fd, buf, sizeof buf - 1);
            close(fd);
            if (n > 0) {
                buf[n] = '\0';
                name.assign(buf, strnlen(buf, (size_t) n));
            }
        }
        if (name.size() > kMaxThreadName) name.resize(kMaxThreadName);
        for (size_t i = 0; i < name.size(); ++i) {
            if (name[i] == ' ' || name[i] == ',' || name[i] == '[' || name[i] == ']') name[i] = '_';
        }

        ThreadInfo info;
        info.tid = entry->d_name;
        info.name = name;
        out->push_back(info);
    }
    int readErr = errno;
    closedir(dir);

    return !(out->empty() && readErr != 0);
}

/**
 * Tên thread đặc trưng của lõi libretro: ppsspp dùng "Emu"/"GPU"/"Audio"/"Render",
 * DeSmuME và vài lõi khác dùng "core N". So khớp CHÍNH XÁC để không nhận nhầm
 * thread của app ("GLThread", "RenderThread", "AudioTrack", "AudioRecord"…).
 */
bool looksLikeCoreThread(const std::string& name) {
    static const char* const kExact[] = { "Emu", "GPU", "Audio", "Render", "PPSSPP", "video", "Video" };
    for (const char* candidate : kExact) {
        if (name == candidate) return true;
    }
    return name.rfind("core", 0) == 0 || name.rfind("Core", 0) == 0;
}

/**
 * Dựng phần "thr=N [...]" của dòng vệt, tôn trọng ngân sách [budget] ký tự.
 *
 * Thứ tự ưu tiên (mất phần đuôi trước, không bao giờ mất phần đầu):
 *   0. thread sinh SAU dlopen-done — nhóm quyết định của bảng §4.2 (đánh dấu '*');
 *   1. tên đặc trưng của lõi (lưới an toàn khi mốc gốc không chụp được);
 *   2. phần còn lại.
 * Phần bị cắt ghi rõ "(+N)".
 */
std::string threadField(std::vector<ThreadInfo>& threads, size_t budget) {
    const size_t total = threads.size();
    std::string out = "thr=" + std::to_string(total);
    if (total == 0) return out + " []";

    std::sort(threads.begin(), threads.end(), [](const ThreadInfo& a, const ThreadInfo& b) {
        int pa = a.isNew ? 0 : (looksLikeCoreThread(a.name) ? 1 : 2);
        int pb = b.isNew ? 0 : (looksLikeCoreThread(b.name) ? 1 : 2);
        if (pa != pb) return pa < pb;
        if (a.tid.size() != b.tid.size()) return a.tid.size() < b.tid.size();
        return a.tid < b.tid;
    });

    // Không đủ chỗ cho cả danh sách: chỉ giữ con số + số bị cắt.
    if (budget < 16) return out + " (+" + std::to_string(total) + ")";

    out += " [";
    size_t used = out.size();
    size_t included = 0;
    for (size_t i = 0; i < total && included < kMaxThreads; ++i) {
        std::string item = (threads[i].isNew ? "*" : "") + threads[i].tid + ":" + threads[i].name;
        size_t reserve = (i + 1 < total) ? 10 : 0;              // " (+12345)]"
        size_t extra = (included > 0 ? 1 : 0) + item.size();
        if (used + extra + reserve > budget) break;
        if (included > 0) out.push_back(',');
        out += item;
        used += extra;
        ++included;
    }
    if (included < total) out += " (+" + std::to_string(total - included) + ")";
    out += ']';
    return out;
}

/**
 * Thư mục vệt: c.filesDir/diag/crumbs (Breadcrumbs.kt). Suy từ tiến trình:
 * /proc/self/cmdline = "<gói>[:tiến trình]" và uid = userId * 100000 + appId.
 * Chỉ nhận thư mục đã tồn tại; không tạo gì.
 */
std::string resolveCrumbDir() {
    char cmdbuf[256];
    memset(cmdbuf, 0, sizeof cmdbuf);
    ssize_t n = 0;
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        n = read(fd, cmdbuf, sizeof cmdbuf - 1);
        close(fd);
    }
    if (n <= 0) return "";

    std::string proc(cmdbuf, strnlen(cmdbuf, (size_t) n));
    size_t colon = proc.find(':');
    std::string pkg = colon == std::string::npos ? proc : proc.substr(0, colon);
    if (pkg.empty() || pkg.find('/') != std::string::npos || pkg.find(' ') != std::string::npos) return "";

    int userId = (int) (getuid() / 100000u);

    char byUser[512];
    snprintf(byUser, sizeof byUser, "/data/user/%d/%s/files/diag/crumbs", userId, pkg.c_str());
    char byData[512];
    snprintf(byData, sizeof byData, "/data/data/%s/files/diag/crumbs", pkg.c_str());

    const char* candidates[2] = { byUser, byData };
    struct stat st;
    for (int i = 0; i < 2; ++i) {
        if (stat(candidates[i], &st) == 0 && S_ISDIR(st.st_mode)) return candidates[i];
    }
    return "";
}

void appendCrumbLine(const std::string& line) {
    if (!g_dirResolved) {
        g_crumbDir = resolveCrumbDir();
        g_dirResolved = true;
        if (g_crumbDir.empty() && !g_missingDirLogged) {
            g_missingDirLogged = true;
            __android_log_print(ANDROID_LOG_WARN, kTag, "không thấy thư mục vệt; chỉ ghi logcat");
        }
    }
    if (g_crumbDir.empty()) return;

    char path[640];
    snprintf(path, sizeof path, "%s/%d.log", g_crumbDir.c_str(), (int) getpid());
    int fd = open(path, O_WRONLY | O_APPEND | O_CREAT | O_CLOEXEC, 0600);
    if (fd < 0) return;

    size_t left = line.size();
    const char* at = line.data();
    while (left > 0) {
        ssize_t wrote = write(fd, at, left);
        if (wrote <= 0) break;
        at += wrote;
        left -= (size_t) wrote;
    }
    close(fd);
}

} // namespace

void setCore(const std::string& soCorePath, void* handle) {
    std::lock_guard<std::mutex> lock(g_lock);
    g_corePath = soCorePath;
    g_coreName = baseName(soCorePath);
    g_handle = handle;
    g_buildId = soCorePath.empty() ? "" : readBuildId(soCorePath);
    // Mốc kế tiếp (dlopen-done) chụp tập thread trước khi lõi chạy retro_init.
    g_baselineTids.clear();
    g_captureBaseline = true;
}

void mark(const char* stage) {
    if (stage == nullptr) return;

    std::lock_guard<std::mutex> lock(g_lock);

    std::vector<ThreadInfo> threads;
    bool threadsOk = collectThreads(&threads);

    if (g_captureBaseline) {
        for (const ThreadInfo& thread : threads) g_baselineTids.insert(thread.tid);
        g_captureBaseline = false;
    } else if (!g_baselineTids.empty()) {
        for (ThreadInfo& thread : threads) thread.isNew = g_baselineTids.count(thread.tid) == 0;
    }

    struct timeval tv;
    gettimeofday(&tv, nullptr);
    struct tm local;
    time_t seconds = tv.tv_sec;
    localtime_r(&seconds, &local);
    char stamp[24];
    snprintf(stamp, sizeof stamp, "%02d:%02d:%02d.%03d",
             local.tm_hour, local.tm_min, local.tm_sec, (int) (tv.tv_usec / 1000));

    struct timespec mono;
    clock_gettime(CLOCK_MONOTONIC, &mono);
    long long monoMs = (long long) mono.tv_sec * 1000 + mono.tv_nsec / 1000000;

    // core= và bid= đứng đầu dòng: nếu báo cáo cắt dòng (logcat 300 ký tự) thì
    // hai trường quyết định vẫn còn, chỉ danh sách thread bị cắt bớt.
    char head[256];
    snprintf(head, sizeof head, "%s [unload] %s core=%s bid=%s dl=%p t=%lld ",
             stamp,
             stage,
             g_coreName.empty() ? "-" : g_coreName.c_str(),
             g_buildId.empty() ? "-" : g_buildId.c_str(),
             g_handle,
             monoMs);

    size_t headLen = strlen(head);
    size_t budget = (headLen + 1 < kMaxLineChars) ? kMaxLineChars - headLen - 1 : 0;

    std::string line(head);
    line += threadsOk ? threadField(threads, budget) : "thr=? ";

    // 1) logcat: Diagnostics.logcat(pid) lấy vào trường log của báo cáo.
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", line.c_str());
    // 2) file vệt: hiện ở "vệt sự kiện trước lỗi".
    line += '\n';
    appendCrumbLine(line);
}

} // namespace diag
} // namespace libretrodroid
