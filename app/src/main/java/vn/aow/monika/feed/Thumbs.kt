package vn.aow.monika.feed

/**
 * Đổi link ảnh Blogger sang đúng cỡ cần hiển thị (ảnh sắc nét, không tải thừa).
 * Blogger có nhiều dạng: `/s72-c/`, `/s72-c-rw/`, `/s72-c-d/`, `/w640-h360-c/`, `=s72-c-rw`, `=w400-h300`…
 * Link không có đoạn cỡ ảnh (ảnh gốc) → giữ nguyên.
 */
object Thumbs {
    private val SEGMENT = Regex("""([/=])(?:s\d+|w\d+(?:-h\d+)?)(?:-[a-z]+)*(?=/|$|\?)""")

    fun sized(url: String?, width: Int, height: Int? = null): String? {
        url ?: return null
        if (!url.contains("googleusercontent.com") && !url.contains("bp.blogspot.com")) return url
        val size = if (height != null) "w$width-h$height-c" else "w$width"
        // Chỉ thay đoạn cỡ ảnh CUỐI (tên thư mục trước tên file, hoặc hậu tố =...).
        val m = SEGMENT.findAll(url).lastOrNull() ?: return url
        return url.replaceRange(m.range, m.groupValues[1] + size + "-rw") // -rw: WebP, nhẹ hơn JPEG.
    }

    /** Ảnh lớn nhất màn hình (thẻ lớn Trang chủ, đầu bài viết). */
    fun hero(url: String?) = sized(url, 1200, 1100)
    /** Thẻ ngang (danh sách game). */
    fun card(url: String?) = sized(url, 800, 450)
    /** Ảnh bìa dọc (thư viện, game nổi bật). */
    fun cover(url: String?) = sized(url, 600, 800)
}
