package vn.aow.monika.forum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import java.io.File
import java.time.Duration
import java.time.OffsetDateTime

/** 1 chủ đề trên diễn đàn AowVN (Flarum). */
data class ForumTopic(
    val id: String, val title: String, val url: String, val replies: Int, val views: Int, val lastPostedAt: String,
    val tags: List<String>, val author: String, val avatar: String?, val sticky: Boolean,
)

/** 1 chuyên mục (tag) của diễn đàn. */
data class ForumTag(val name: String, val slug: String, val color: String?, val topics: Int, val url: String)

/**
 * Đọc diễn đàn AowVN qua API công khai của Flarum (chỉ đọc, không cần đăng nhập). Lưu bộ đệm 10 phút để mở Trang chủ nhanh
 * và không gọi máy chủ diễn đàn quá nhiều.
 */
class ForumRepository(private val http: OkHttpClient, private val config: ConfigRepository, private val dir: File) {
    private val cfg get() = config.current.forum

    /** Bản đã lưu (kể cả cũ) để hiện ngay; null nếu chưa có. */
    fun cachedTopics(): List<ForumTopic>? = read("topics")?.let { runCatching { parseTopics(it, cfg.baseUrl, cfg.hiddenTags) }.getOrNull() }?.take(cfg.limit)
    fun cachedTags(): List<ForumTag>? = read("tags")?.let { runCatching { parseTags(it, cfg.baseUrl, cfg.hiddenTags) }.getOrNull() }

    suspend fun topics(): Result<List<ForumTopic>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = cachedBody("topics", 10 * 60_000L) {
                get("${cfg.baseUrl}/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=${cfg.limit + 8}&include=tags%2Cuser")
            }
            parseTopics(body, cfg.baseUrl, cfg.hiddenTags).take(cfg.limit)
        }
    }

    suspend fun tags(): Result<List<ForumTag>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = cachedBody("tags", 6 * 3_600_000L) { get("${cfg.baseUrl}/api/tags") }
            parseTags(body, cfg.baseUrl, cfg.hiddenTags)
        }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("Accept", "application/vnd.api+json").build()
        return http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            r.body!!.string()
        }
    }

    private fun file(key: String) = File(dir, "$key.json")
    private fun read(key: String): String? = file(key).takeIf { it.isFile }?.readText()

    private inline fun cachedBody(key: String, ttlMs: Long, fetch: () -> String): String {
        val f = file(key)
        if (f.isFile && System.currentTimeMillis() - f.lastModified() < ttlMs) return f.readText()
        return try {
            fetch().also { runCatching { dir.mkdirs(); f.writeText(it) } }
        } catch (e: Exception) {
            if (f.isFile) f.readText() else throw e // Mất mạng → dùng bản cũ
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun JsonObject.attr(name: String): JsonElement? = this["attributes"]?.jsonObject?.get(name)
        private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonElement?.int(): Int = (this as? JsonPrimitive)?.intOrNull ?: 0

        fun parseTopics(text: String, base: String, hiddenTags: List<String> = emptyList()): List<ForumTopic> {
            val root = json.parseToJsonElement(text).jsonObject
            val included = (root["included"] as? JsonArray).orEmpty().map { it.jsonObject }
            fun inc(type: String, id: String) = included.firstOrNull { it["type"].str() == type && it["id"].str() == id }
            val hidden = hiddenTags.map { it.lowercase() }.toSet()
            return (root["data"] as? JsonArray).orEmpty().mapNotNull { el ->
                val d = el.jsonObject
                val id = d["id"].str() ?: return@mapNotNull null
                val title = d.attr("title").str() ?: return@mapNotNull null
                val slug = d.attr("slug").str() ?: id
                val rel = d["relationships"]?.jsonObject
                val tags = rel?.get("tags")?.jsonObject?.get("data")?.jsonArray.orEmpty()
                    .mapNotNull { inc("tags", it.jsonObject["id"].str() ?: "")?.attr("name").str() }
                if (tags.any { it.lowercase() in hidden }) return@mapNotNull null
                val user = rel?.get("user")?.jsonObject?.get("data")?.jsonObject?.get("id").str()?.let { inc("users", it) }
                ForumTopic(
                    id, title, "${base.trimEnd('/')}/d/$slug",
                    replies = (d.attr("commentCount").int() - 1).coerceAtLeast(0), views = d.attr("viewCount").int(),
                    lastPostedAt = d.attr("lastPostedAt").str().orEmpty(), tags = tags,
                    author = user?.attr("displayName").str() ?: user?.attr("username").str().orEmpty(),
                    avatar = user?.attr("avatarUrl").str()?.takeIf { it.isNotBlank() },
                    sticky = (d.attr("isSticky") as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            }
        }

        fun parseTags(text: String, base: String, hiddenTags: List<String> = emptyList()): List<ForumTag> {
            val hidden = hiddenTags.map { it.lowercase() }.toSet()
            return (json.parseToJsonElement(text).jsonObject["data"] as? JsonArray).orEmpty().mapNotNull { el ->
                val d = el.jsonObject
                val name = d.attr("name").str() ?: return@mapNotNull null
                val slug = d.attr("slug").str() ?: return@mapNotNull null
                val n = d.attr("discussionCount").int()
                if (n <= 0 || name.lowercase() in hidden) return@mapNotNull null
                ForumTag(name, slug, d.attr("color").str()?.takeIf { it.isNotBlank() }, n, "${base.trimEnd('/')}/t/$slug")
            }.sortedByDescending { it.topics }
        }

        /** "2026-09-15T05:57:10+00:00" → "3 ngày trước". */
        fun relative(iso: String, now: OffsetDateTime = OffsetDateTime.now()): String {
            val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
            val m = Duration.between(t, now).toMinutes()
            return when {
                m < 1 -> "vừa xong"
                m < 60 -> "$m phút trước"
                m < 24 * 60 -> "${m / 60} giờ trước"
                m < 14 * 24 * 60 -> "${m / (24 * 60)} ngày trước"
                m < 60 * 24 * 60 -> "${m / (7 * 24 * 60)} tuần trước"
                m < 365 * 24 * 60 -> "${m / (30 * 24 * 60)} tháng trước"
                else -> "${m / (365 * 24 * 60)} năm trước"
            }
        }

        /** 24127 → "24,1K"; 830 → "830". */
        fun compact(n: Int): String = when {
            n >= 1_000_000 -> String.format(java.util.Locale("vi"), "%.1fM", n / 1_000_000.0)
            n >= 1000 -> String.format(java.util.Locale("vi"), "%.1fK", n / 1000.0).replace(",0K", "K")
            else -> n.toString()
        }

        private fun fold(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd')

        /** Thể loại game → emoji 3D riêng cho từng loại (từ khóa không dấu). Không khớp → null để nơi gọi chia emoji còn trống. */
        private val GENRES = listOf(
            listOf("visual novel", "vn ", "novel") to "fluent3d_books",
            listOf("rpg", "nhap vai", "role") to "fluent3d_crown",
            listOf("pokemon", "monster") to "fluent3d_star",
            listOf("dua xe", "racing", "toc do") to "fluent3d_stopwatch",
            listOf("hanh dong", "action", "chien dau", "fight", "danh nhau") to "fluent3d_high_voltage",
            listOf("phieu luu", "adventure") to "fluent3d_rocket",
            listOf("chien thuat", "strategy", "tactic") to "fluent3d_shield",
            listOf("giai do", "puzzle", "tri tue") to "fluent3d_puzzle_piece",
            listOf("mo phong", "simulation", "nuoi", "quan ly") to "fluent3d_hammer_and_wrench",
            listOf("the thao", "sport", "bong da", "football") to "fluent3d_trophy",
            listOf("bai", "casino", "co ", "board", "cotuong") to "fluent3d_game_die",
            listOf("kinh di", "horror", "ma ") to "fluent3d_eyes",
            listOf("tinh cam", "dating", "romance", "love") to "fluent3d_red_heart",
            listOf("java", "j2me", "mobile", "dien thoai") to "fluent3d_mobile_phone",
            listOf("nds", "gba", "psp", "ps1", "ps2", "snes", "nes", "arcade", "may choi game", "console") to "fluent3d_joystick",
            listOf("viet hoa", "dich", "translate") to "fluent3d_sparkles",
            listOf("mod", "hack", "cheat") to "fluent3d_key",
            listOf("ve", "anime", "manga", "art") to "fluent3d_artist_palette",
            listOf("robot", "mecha") to "fluent3d_robot",
        )
        /** Nơi gọi không thấy khớp thì lấy lần lượt trong nhóm này (mỗi thẻ 1 icon khác nhau). */
        val GENRE_POOL = listOf(
            "fluent3d_video_game", "fluent3d_joystick", "fluent3d_game_die", "fluent3d_puzzle_piece", "fluent3d_trophy", "fluent3d_crown",
            "fluent3d_rocket", "fluent3d_star", "fluent3d_high_voltage", "fluent3d_shield", "fluent3d_artist_palette", "fluent3d_robot",
            "fluent3d_wrapped_gift", "fluent3d_party_popper", "fluent3d_television", "fluent3d_stopwatch",
        )

        fun genreEmoji(name: String): String? {
            val t = fold(name)
            return GENRES.firstOrNull { (keys, _) -> keys.any { k -> Regex("\\b" + Regex.escape(k.trim())).containsMatchIn(t) } }?.second
        }

        /** Gán emoji cho danh sách tên thể loại: khớp từ khóa trước, còn lại chia từ [GENRE_POOL]; không có 2 thẻ trùng nhau (khi còn emoji trống). */
        fun assignGenreEmojis(names: List<String>): List<String> {
            val used = HashSet<String>()
            val out = arrayOfNulls<String>(names.size)
            names.forEachIndexed { i, n -> genreEmoji(n)?.takeIf { used.add(it) }?.let { out[i] = it } }
            val free = GENRE_POOL.filter { it !in used }.iterator()
            names.indices.forEach { i -> if (out[i] == null) out[i] = if (free.hasNext()) free.next().also { used.add(it) } else GENRE_POOL[i % GENRE_POOL.size] }
            return out.map { it!! }
        }

        /** Emoji 3D (tên drawable) hợp với chuyên mục / chủ đề. */
        fun emojiFor(tags: List<String>, sticky: Boolean = false): String {
            val t = java.text.Normalizer.normalize(tags.joinToString(" ").lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            return when {
                sticky -> "fluent3d_pushpin"
                "thong bao" in t || "dai tieng noi" in t -> "fluent3d_megaphone"
                "huong dan" in t || "thu vien" in t || "dao tao" in t -> "fluent3d_books"
                "meo" in t || "thu thuat" in t -> "fluent3d_light_bulb"
                "viet hoa" in t -> "fluent3d_sparkles"
                "chia se app" in t || "chia se" in t -> "fluent3d_package"
                "game" in t -> "fluent3d_video_game"
                "hoi dap" in t -> "fluent3d_speaking_head"
                "am nhac" in t || "music" in t -> "fluent3d_musical_note"
                "anh dep" in t || "wallpaper" in t -> "fluent3d_globe_with_meridians"
                "sanh chinh" in t -> "fluent3d_classical_building"
                else -> "fluent3d_speech_balloon"
            }
        }
    }
}
