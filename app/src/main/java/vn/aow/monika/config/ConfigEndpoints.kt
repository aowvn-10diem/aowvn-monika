package vn.aow.monika.config

import vn.aow.monika.BuildConfig

/**
 * V181: hai nguồn cấu hình từ xa của app.
 * - **Pages** (`monika.aow.vn`, nhánh `trang-web`) đọc trước: CDN GitHub, không tốn lượt gọi Worker.
 * - **Worker** (`BuildConfig.REMOTE_CONFIG_URL`) là nguồn dự phòng, chỉ gọi khi Pages lỗi.
 *
 * URL Pages nằm DUY NHẤT ở đây (không rải rác trong mã). V181 không được sửa `build.gradle.kts`
 * nên chưa khai được thành `BuildConfig` field; khi mở ngoại lệ đó thì chuyển hằng số này sang đó.
 */
object ConfigEndpoints {
    const val PAGES_CONFIG_URL = "https://monika.aow.vn/config.json"

    /** Worker giữ nguyên link build sẵn của APK (không hard-code lại). */
    val WORKER_CONFIG_URL: String get() = BuildConfig.REMOTE_CONFIG_URL
}
