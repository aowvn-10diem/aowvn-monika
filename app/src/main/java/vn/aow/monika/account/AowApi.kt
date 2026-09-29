package vn.aow.monika.account

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.aow.monika.config.ConfigRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Profile(val points: Long, val streak: Int, val progress: Int, val lastCheckin: Long) {
    /** Hôm nay (giờ máy) đã điểm danh chưa. */
    fun checkedInToday(zone: ZoneId = ZoneId.systemDefault()) =
        lastCheckin > 0 && Instant.ofEpochMilli(lastCheckin).atZone(zone).toLocalDate() == LocalDate.now(zone)
}

data class CheckinResult(val profile: Profile, val pointAwarded: Boolean, val already: Boolean)

data class Review(
    val uid: String, val name: String, val photo: String, val recommended: Boolean,
    val content: String, val teamName: String, val createdAt: Long, val updatedAt: Long,
)

/**
 * Dữ liệu tài khoản AowVN qua REST của Firebase Realtime Database — cùng đường dẫn, cùng cấu trúc với web aow.vn
 * (đọc từ mã trang /p/vote-game.html, KHÔNG theo tài liệu cũ vì tài liệu lệch với dữ liệu thật).
 */
class AowApi(private val http: OkHttpClient, private val config: ConfigRepository, private val account: Account) {
    private val json = Json { ignoreUnknownKeys = true }
    private val base get() = config.current.account.databaseUrl.trimEnd('/')

    private suspend fun url(path: String, auth: Boolean): String {
        val token = if (auth) account.idToken() ?: throw NotSignedIn() else null
        return "$base/$path.json" + (token?.let { "?auth=" + Uri.encode(it) } ?: "")
    }

    class NotSignedIn : Exception("Bạn cần đăng nhập tài khoản AowVN")

    private suspend fun call(method: String, path: String, auth: Boolean, body: JsonElement? = null): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(path, auth))
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val text = r.body!!.string()
            if (r.code == 401 || text.contains("Permission denied")) throw IllegalStateException("Máy chủ từ chối (không có quyền)")
            check(r.isSuccessful) { "Lỗi máy chủ (HTTP ${r.code})" }
            if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
        }
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    // ---------- Hồ sơ + điểm danh ----------

    suspend fun profile(): Profile {
        val uid = account.session.value?.uid ?: throw NotSignedIn()
        val o = call("GET", "users/$uid", auth = true).obj() ?: JsonObject(emptyMap())
        val c = o["checkin"].obj() ?: JsonObject(emptyMap())
        return Profile(o.long("points"), c.long("streak").toInt(), c.long("progress").toInt(), c.long("lastCheckinTime"))
    }

    /**
     * Điểm danh — y hệt web: bỏ lỡ 1 ngày thì chuỗi về 1; đủ 14 ngày liên tiếp → +1 điểm tích lũy, chu kỳ về 0.
     */
    suspend fun checkin(zone: ZoneId = ZoneId.systemDefault()): CheckinResult {
        val s = account.session.value ?: throw NotSignedIn()
        val p = profile()
        if (p.checkedInToday(zone)) return CheckinResult(p, pointAwarded = false, already = true)
        val next = nextCheckin(p, System.currentTimeMillis(), zone)
        call("PATCH", "users/${s.uid}", auth = true, body = buildJsonObject {
            put("checkin/lastCheckinTime", next.profile.lastCheckin)
            put("checkin/streak", next.profile.streak)
            put("checkin/progress", next.profile.progress)
            put("points", next.profile.points)
            if (s.email.isNotBlank()) put("email", s.email)
            if (s.name.isNotBlank()) put("name", s.name)
        })
        return next
    }

    // ---------- Đánh giá bản dịch (post_reviews/{mã bài Blogger}/{uid}) ----------

    suspend fun reviews(postId: String): List<Review> {
        val o = call("GET", "post_reviews/$postId", auth = false).obj() ?: return emptyList()
        return o.values.mapNotNull { it.obj() }.map { r ->
            Review(r.str("uid"), r.str("displayName").ifBlank { "Game thủ AowVN" }, r.str("photoURL"),
                (r["recommended"] as? JsonPrimitive)?.booleanOrNull ?: true, r.str("content"), r.str("teamName"),
                r.long("createdAt"), r.long("updatedAt"))
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun saveReview(postId: String, recommended: Boolean, content: String, createdAt: Long?) {
        val s = account.session.value ?: throw NotSignedIn()
        val now = System.currentTimeMillis()
        call("PUT", "post_reviews/$postId/${s.uid}", auth = true, body = buildJsonObject {
            put("uid", s.uid)
            put("displayName", s.name.ifBlank { s.email.substringBefore('@').ifBlank { "Game thủ AowVN" } })
            put("photoURL", s.photo)
            put("recommended", recommended)
            put("teamId", "default")
            put("teamName", "Bản Dịch Chung")
            put("content", content.trim())
            put("createdAt", createdAt ?: now)
            put("updatedAt", now)
        })
    }

    suspend fun deleteReview(postId: String) {
        val s = account.session.value ?: throw NotSignedIn()
        call("DELETE", "post_reviews/$postId/${s.uid}", auth = true)
    }

    // ---------- Donate (chỉ tạo mã QR; danh sách vote đã bỏ để khỏi tốn băng thông Firebase) ----------

    /** Nội dung chuyển khoản để hệ thống tự ghi nhận donate (giống web). */
    fun donateMemo(gameId: String): String {
        val short = account.session.value?.uid?.take(8) ?: "GUEST"
        return "${config.current.account.donate.memoPrefix} DONATE $short $gameId"
    }

    /** Ảnh QR VietQR (quét bằng app ngân hàng bất kỳ). */
    fun donateQr(gameId: String, amount: Long): String {
        val d = config.current.account.donate
        return "https://img.vietqr.io/image/${d.bankId}-${d.accountNo}-${d.template}.png" +
            "?amount=$amount&addInfo=${Uri.encode(donateMemo(gameId))}&accountName=${Uri.encode(d.accountName)}"
    }

    companion object {
        /** Tính lượt điểm danh mới (thuần logic, để test). */
        fun nextCheckin(p: Profile, now: Long, zone: ZoneId): CheckinResult {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val last = if (p.lastCheckin > 0) Instant.ofEpochMilli(p.lastCheckin).atZone(zone).toLocalDate() else null
            if (last == today) return CheckinResult(p, pointAwarded = false, already = true)
            var streak = 1
            var progress = 1
            if (last == today.minusDays(1)) { streak = p.streak + 1; progress = p.progress + 1 }
            var points = p.points
            var awarded = false
            if (progress >= 14) { points += 1; progress = 0; awarded = true }
            return CheckinResult(Profile(points, streak, progress, now), awarded, already = false)
        }
    }
}
