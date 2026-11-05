package vn.aow.monika.download

import vn.aow.monika.config.DownloadHost
import java.net.URI

data class DownloadLink(
    val hostName: String,
    val pageUrl: String,
    /** Có giá trị = tải thẳng trong app. Null = mở trình duyệt. */
    val directUrl: String?,
    /** Chữ trên nút trong bài (vd. "TẢI VỀ", "DỰ PHÒNG", "RPG Maker Plugin"). */
    val label: String = "",
)

/** Tách link tải trong nội dung bài/trang theo danh sách downloadHosts của cấu hình. */
object LinkResolver {
    private val anchorRegex = Regex("""<a\b[^>]*href\s*=\s*["'](https?://[^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRegex = Regex("<[^>]+>")

    /** Chuẩn hoá host: bỏ khoảng trắng, hạ chữ thường, bỏ "www." và dấu chấm cuối; rỗng → null. */
    // diag: bỏ qua URL không hợp lệ vì đây là đầu vào người dùng/HTML, không phải lỗi app.
    internal fun normalizeHost(url: String): String? = runCatching { URI(url).host }.getOrNull()
        ?.trim()?.lowercase()?.removePrefix("www.")?.trimEnd('.')?.takeIf { it.isNotEmpty() }

    fun extract(html: String, hosts: List<DownloadHost>): List<DownloadLink> =
        anchorRegex.findAll(html)
            .mapNotNull { m ->
                val url = m.groupValues[1].replace("&amp;", "&")
                val label = m.groupValues[2].replace(tagRegex, "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
                resolve(url, hosts)?.copy(label = label)
            }
            .distinctBy { it.pageUrl }
            .toList()

    fun resolve(url: String, hosts: List<DownloadHost>, manual: Boolean = false): DownloadLink? {
        val host = normalizeHost(url) ?: return null
        for (h in hosts) {
            val wanted = h.host.trim().lowercase().removePrefix("www.").trimEnd('.')
            if (wanted.isEmpty()) continue
            if (host != wanted && !host.endsWith("." + wanted)) continue
            when (h.mode) {
                "direct" -> {
                    val match = h.pattern?.let { Regex(it).find(url) } ?: continue
                    val direct = h.directUrl ?: continue
                    // V193 G2: mặc định không tự tải host chưa duyệt (Pixeldrain) — chỉ trả link
                    // để UI mở trình duyệt; đường dán link thủ công truyền manual = true sau cảnh báo.
                    if (!manual && !h.autoDirect) return DownloadLink(h.name, url, null)
                    return DownloadLink(h.name, url, direct.replace("$1", match.groupValues.getOrElse(1) { "" }))
                }
                "browser" -> return DownloadLink(h.name, url, null)
            }
        }
        return null
    }

    /** Link trang tĩnh của blog (aow.vn/p/...) — app đọc trang qua feed để lấy link tải. */
    fun isBlogPage(url: String): Boolean =
        runCatching { URI(url) }.getOrNull()?.let { uri ->
            normalizeHost(url) == "aow.vn" && uri.path.orEmpty().startsWith("/p/")
        } ?: false
}
