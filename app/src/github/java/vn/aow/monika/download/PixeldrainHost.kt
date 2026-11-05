package vn.aow.monika.download

import vn.aow.monika.config.DownloadHost

/**
 * V193 G2: Pixeldrain không được tự tải. Mọi đường tự động (bài viết, trang công cụ, trình duyệt nhúng)
 * đều dừng lại; chỉ hộp thoại "Tải từ link" (người dùng dán + xác nhận cảnh báo) mới được tải.
 */
object PixeldrainHost {
    const val WARNING = "Tệp do bên thứ ba (Pixeldrain), AowVN không kiểm soát."

    fun matches(url: String): Boolean {
        val host = LinkResolver.normalizeHost(url) ?: return false
        return host == "pixeldrain.com" || host.endsWith(".pixeldrain.com")
    }

    /** Chỉ dùng sau khi người dùng dán link và bấm xác nhận trong hộp thoại cảnh báo. */
    fun manualDirect(url: String, hosts: List<DownloadHost>): String? =
        LinkResolver.resolve(url, hosts, manual = true)?.directUrl
}
