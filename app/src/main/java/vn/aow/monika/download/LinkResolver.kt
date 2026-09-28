package vn.aow.monika.download

import vn.aow.monika.config.DownloadHost
import java.net.URI

data class DownloadLink(
    val hostName: String,
    val pageUrl: String,
    /** Có giá trị = tải thẳng trong app. Null = mở trình duyệt. */
    val directUrl: String?,
)

/** Tách link tải trong nội dung bài theo danh sách downloadHosts của cấu hình. */
object LinkResolver {
    private val hrefRegex = Regex("""href\s*=\s*"(https?://[^"]+)"""", RegexOption.IGNORE_CASE)

    fun extract(html: String, hosts: List<DownloadHost>): List<DownloadLink> =
        hrefRegex.findAll(html)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .distinct()
            .mapNotNull { resolve(it, hosts) }
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
}
