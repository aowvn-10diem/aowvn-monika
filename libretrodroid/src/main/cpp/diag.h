/*
 *     Aow Monika: vệt chẩn đoán quanh cửa sổ unload lõi.
 *
 *     Mục đích: khi tiến trình :game chết bằng SIGSEGV trong .so của lõi,
 *     báo cáo không có khung nào phía libretrodroid. Vệt dưới đây trả lời
 *     "chết trong cửa sổ unload hay không" mà không đổi thứ tự gọi của lõi.
 *
 *     Ghi vào 2 nơi:
 *       1. logcat (tag "MonikaUnload") — Diagnostics.logcat(pid) lấy vào báo cáo;
 *       2. file vệt sẵn có diag/crumbs/<pid>.log — hiện ở mục "vệt sự kiện trước lỗi".
 *     Ghi file là best-effort: không mở được thì bỏ qua, không ném lỗi.
 */

#ifndef LIBRETRODROID_DIAG_H
#define LIBRETRODROID_DIAG_H

#include <string>

namespace libretrodroid {
namespace diag {

/** Gọi ngay sau dlopen thành công: nhớ đường dẫn lõi, handle và BuildId (nếu đọc được). */
void setCore(const std::string& soCorePath, void* handle);

/**
 * Ghi một mốc: giờ, số thread còn sống của tiến trình (/proc/self/task),
 * tên thread, core, handle, BuildId, đồng hồ monotonic.
 * Không chặn, không ném, không đổi luồng.
 */
void mark(const char* stage);

} // namespace diag
} // namespace libretrodroid

#endif //LIBRETRODROID_DIAG_H
