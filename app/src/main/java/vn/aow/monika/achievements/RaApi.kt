package vn.aow.monika.achievements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Khóa web API sai / tên người dùng không có (HTTP 401/403). */
class RaAuthException(message: String) : IOException(message)

/**
 * Gọi RetroAchievements Web API (https://api-docs.retroachievements.org). Mọi lệnh cần khóa web API của người dùng (tham số `y`).
 * Lỗi mạng → IOException; khóa sai → [RaAuthException].
 */
class RaApi(private val http: OkHttpClient, private val base: String = "https://retroachievements.org") {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private suspend fun get(endpoint: String, key: String, vararg params: Pair<String, String>): String = withContext(Dispatchers.IO) {
        val url = "$base/API/$endpoint.php".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("y", key)
        }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw RaAuthException("Khóa web API hoặc tên người dùng không đúng")
            if (!r.isSuccessful) throw IOException("RetroAchievements trả lỗi ${r.code}")
            r.body!!.string()
        }
    }

    /** Đăng nhập bằng mật khẩu để lấy mã đăng nhập (token) cho rcheevos trong game. Mật khẩu không được lưu. */
    suspend fun loginWithPassword(user: String, password: String): String = withContext(Dispatchers.IO) {
        val form = okhttp3.FormBody.Builder().add("r", "login2").add("u", user.trim()).add("p", password).build()
        http.newCall(Request.Builder().url("$base/dorequest.php").post(form).header("User-Agent", "AowMonika (Android)").build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("RetroAchievements trả lỗi ${r.code}")
            val o = runCatching { Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject }.getOrNull()
                ?: throw IOException("Phản hồi RetroAchievements không đọc được")
            val ok = (o["Success"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            val token = (o["Token"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (!ok || token.isNullOrBlank()) throw RaAuthException((o["Error"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Sai tên hoặc mật khẩu")
            token
        }
    }

    suspend fun profile(user: String, key: String): RaProfile {
        val body = get("API_GetUserProfile", key, "u" to user)
        val p = json.decodeFromString(RaProfile.serializer(), body)
        if (p.user.isBlank()) throw RaAuthException("Không tìm thấy người dùng RetroAchievements này")
        return p
    }

    suspend fun recentGames(user: String, key: String, count: Int = 20): List<RaRecentGame> =
        json.decodeFromString(ListSerializer(RaRecentGame.serializer()), get("API_GetUserRecentlyPlayedGames", key, "u" to user, "c" to count.toString()))

    /** Thành tựu đã mở trong [minutes] phút gần đây (mặc định 7 ngày). */
    suspend fun recentUnlocks(user: String, key: String, minutes: Int = 7 * 24 * 60): List<RaUnlock> =
        json.decodeFromString(ListSerializer(RaUnlock.serializer()), get("API_GetUserRecentAchievements", key, "u" to user, "m" to minutes.toString()))

    suspend fun gameProgress(gameId: Int, user: String, key: String): RaGameProgress =
        json.decodeFromString(RaGameProgress.serializer(), get("API_GetGameInfoAndUserProgress", key, "g" to gameId.toString(), "u" to user))

    /** Game của 1 hệ máy RA (chỉ game có thành tựu), kèm hash. */
    suspend fun gameList(consoleId: Int, key: String): List<RaGameListItem> =
        json.decodeFromString(ListSerializer(RaGameListItem.serializer()), get("API_GetGameList", key, "i" to consoleId.toString(), "h" to "1", "f" to "1"))

    companion object {
        const val MEDIA = "https://media.retroachievements.org"
        fun media(path: String) = if (path.startsWith("http")) path else MEDIA + path
        fun badge(name: String, locked: Boolean = false) = "$MEDIA/Badge/$name${if (locked) "_lock" else ""}.png"
    }
}
