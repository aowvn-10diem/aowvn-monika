package vn.aow.monika.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Thông tin game lấy từ bài viết aow.vn lúc tải: tên đẹp, ảnh bìa, link bài.
 * Lưu thành file ẩn `.monika.json` trong thư mục game → xóa/đổi tên thư mục vẫn giữ được.
 */
@Serializable
data class GameMeta(
    val title: String = "",
    val cover: String? = null,
    val postUrl: String? = null,
    val postId: String? = null,
    val labels: List<String> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private const val FILE = ".monika.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String?): GameMeta? = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
        fun read(dir: File): GameMeta? = File(dir, FILE).takeIf { it.isFile }?.let { fromJson(it.readText()) }
        fun write(dir: File, meta: GameMeta) = runCatching { File(dir, FILE).writeText(meta.toJson()) }

        /** "[MỚI] Game Pokemon Fire Red Việt Hóa | GBA Android PC - ..." → "Pokemon Fire Red". */
        fun cleanTitle(title: String): String = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFC)
            .substringBefore(" | ").substringBefore(" - ")
            .replace(Regex("""\[[^\]]*]"""), "")
            // So khớp "Việt Hóa/Hoá" không phụ thuộc cách mã hóa dấu (dựng sẵn hay tổ hợp).
            .let { s ->
                val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                val cut = Regex("""(?i)vi\p{M}*e\p{M}*t\s*ho\p{M}*a\p{M}*""").replace(nfd, "")
                java.text.Normalizer.normalize(cut, java.text.Normalizer.Form.NFC)
            }
            .replace(Regex("""(?i)^\s*(fan\s+)?game\s+"""), "")
            .replace(Regex("""\s+"""), " ").trim()
            .ifBlank { title }

        /** Ảnh bìa lớn hơn (Blogger cho đổi cỡ qua đường dẫn). */
        fun largeCover(url: String?): String? = vn.aow.monika.feed.Thumbs.cover(url)
    }
}
