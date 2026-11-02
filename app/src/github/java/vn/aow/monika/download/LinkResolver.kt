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

    fun extract(html: String, hosts: List<DownloadHost>): List<DownloadLink> =
        anchorRegex.findAll(html)
            .mapNotNull { m ->
                val url = m.groupValues[1].replace("&amp;", "&")
                val label = m.groupValues[2].replace(tagRegex, "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
                resolve(url, hosts)?.copy(label = label)
            }
            .distinctBy { it.pageUrl }
            .toList()

    fun resolve(url: String, hosts: List<DownloadHost>): DownloadLink? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
        for (h in hosts) {
            if (host != h.host && !host.endsWith("." + h.host)) continue
            when (h.mode) {
                "direct" -> {
                    val match = h.pattern?.let { Regex(it).find(url) } ?: continue
                    val direct = h.directUrl ?: continue
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
            uri.host?.removePrefix("www.") == "aow.vn" && uri.path.orEmpty().startsWith("/p/")
        } ?: false
}
