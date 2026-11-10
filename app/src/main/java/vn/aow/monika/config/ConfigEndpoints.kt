package vn.aow.monika.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /** V206: bản đã ký (cùng nội dung + chữ ký Ed25519 ở dòng đầu), tải 1 lượt GET. */
    val PAGES_SIGNED_CONFIG_URL: String get() = signedConfigUrl(PAGES_CONFIG_URL).orEmpty()

    /** Worker giữ nguyên link build sẵn của APK (không hard-code lại). */
    val WORKER_CONFIG_URL: String get() = BuildConfig.REMOTE_CONFIG_URL

    /** V206: bản đã ký cùng nguồn Worker; rỗng khi URL không đúng mẫu (vd. bản Play để trống). */
    val WORKER_SIGNED_CONFIG_URL: String get() = signedConfigUrl(WORKER_CONFIG_URL).orEmpty()

    /**
     * V206: suy URL bản ký từ URL cấu hình bằng cách dựng lại từ URL đã parse (giữ nguyên query),
     * không `replace` chuỗi. Trả null khi URL rỗng/không hợp lệ hoặc không kết thúc bằng `/config.json`
     * — thà bỏ qua bản ký còn hơn tải nhầm URL thường rồi verify hỏng im lặng.
     */
    fun signedConfigUrl(configUrl: String): String? {
        if (configUrl.isBlank()) return null
        val url = configUrl.toHttpUrlOrNull() ?: return null
        val path = url.encodedPath
        if (!path.endsWith("/config.json")) return null
        return url.newBuilder()
            .encodedPath(path.removeSuffix("config.json") + "config.signed.json")
            .build()
            .toString()
    }
}
