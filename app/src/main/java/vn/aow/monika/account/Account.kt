package vn.aow.monika.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import java.security.SecureRandom

/** Người dùng đã đăng nhập (tài khoản Google trên aow.vn). */
@Serializable
data class Session(
    val uid: String,
    val name: String,
    val email: String,
    val photo: String,
    val idToken: String,
    /** Hạn của idToken (ms). Firebase cho 1 giờ. */
    val idTokenExp: Long,
    val refreshToken: String,
)

/**
 * Đăng nhập tài khoản AowVN:
 * 1. [startLogin] mở trang web aow.vn/p/device-login.html (Custom Tab) kèm `state` ngẫu nhiên.
 * 2. Người dùng đăng nhập Google trên web → trang chuyển về `aowmonika://login#...` → [AuthCallbackActivity] gọi [complete].
 * 3. idToken hết hạn (1 giờ) → tự đổi bằng refresh token qua securetoken.googleapis.com.
 * Token lưu trong bộ nhớ riêng của app (không app khác đọc được).
 */
class Account(private val context: Context, private val http: OkHttpClient, private val config: ConfigRepository) {
    private val sp = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session
    private val refreshLock = Mutex()

    private fun load(): Session? = sp.getString("session", null)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }

    private fun save(s: Session?) {
        sp.edit().apply { if (s == null) remove("session") else putString("session", json.encodeToString(Session.serializer(), s)) }.apply()
        _session.value = s
    }

    /** Hồ sơ lần trước (điểm, chuỗi, điểm danh) lưu theo tài khoản → mở app là hiện ngay, mạng cập nhật sau. */
    fun cachedProfile(uid: String): Profile? = sp.getString("profile_$uid", null)?.split('|')?.takeIf { it.size == 4 }?.let {
        runCatching { Profile(it[0].toLong(), it[1].toInt(), it[2].toInt(), it[3].toLong()) }.getOrNull()
    }
    fun cacheProfile(uid: String, p: Profile) { sp.edit().putString("profile_$uid", "${p.points}|${p.streak}|${p.progress}|${p.lastCheckin}").apply() }

    /** Mở trang đăng nhập trên web. */
    fun startLogin(activity: Activity) {
        val state = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
        sp.edit().putString("pending_state", state).putLong("pending_at", System.currentTimeMillis()).apply()
        val url = Uri.parse(config.current.account.loginUrl).buildUpon()
            .appendQueryParameter("app", "monika").appendQueryParameter("state", state).build()
        vn.aow.monika.browser.CustomTab.open(activity, url)
    }

    /**
     * Nhận kết quả từ trang web. Trả về thông báo lỗi (null = thành công).
     * Kiểm `state` khớp lượt đăng nhập vừa mở (chống trang lạ nhét tài khoản khác vào app).
     */
    fun complete(fragment: String?): String? {
        val p = Uri.parse("x://x?" + (fragment ?: "")) // chuỗi dạng a=1&b=2 (đã mã hóa URL)
        val expected = sp.getString("pending_state", null)
        val at = sp.getLong("pending_at", 0)
        sp.edit().remove("pending_state").remove("pending_at").apply()
        if (expected == null || p.getQueryParameter("state") != expected) return "Phiên đăng nhập không hợp lệ. Hãy bấm Đăng nhập lại trong app."
        if (System.currentTimeMillis() - at > 30 * 60_000) return "Phiên đăng nhập đã quá hạn. Hãy thử lại."
        val uid = p.getQueryParameter("uid").orEmpty()
        val idToken = p.getQueryParameter("id_token").orEmpty()
        val refresh = p.getQueryParameter("refresh_token").orEmpty()
        if (uid.isBlank() || idToken.isBlank() || refresh.isBlank()) return "Thiếu thông tin đăng nhập. Hãy thử lại."
        save(Session(
            uid = uid, name = p.getQueryParameter("name").orEmpty(), email = p.getQueryParameter("email").orEmpty(),
            photo = p.getQueryParameter("photo").orEmpty(), idToken = idToken,
            idTokenExp = System.currentTimeMillis() + 55 * 60_000, refreshToken = refresh,
        ))
        return null
    }

    fun signOut() = save(null)

    /** idToken còn hạn (tự làm mới nếu sắp hết). Null = chưa đăng nhập hoặc phiên đã bị thu hồi. */
    suspend fun idToken(): String? = refreshLock.withLock {
        val s = _session.value ?: return null
        if (System.currentTimeMillis() < s.idTokenExp - 60_000) return s.idToken
        withContext(Dispatchers.IO) {
            val key = config.current.account.apiKey
            val body = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", s.refreshToken).build()
            val res = runCatching {
                http.newCall(Request.Builder().url("https://securetoken.googleapis.com/v1/token?key=$key").post(body).build()).execute().use { r ->
                    val text = r.body!!.string()
                    if (r.code == 400) return@use null // Refresh token bị thu hồi / tài khoản bị khóa → đăng xuất.
                    check(r.isSuccessful) { "HTTP ${r.code}" }
                    json.parseToJsonElement(text).jsonObject
                }
            }
            val o = res.getOrElse { return@withContext s.idToken } // Lỗi mạng: dùng tạm token cũ.
            if (o == null) { save(null); return@withContext null }
            val fresh = s.copy(
                idToken = o["id_token"]!!.jsonPrimitive.content,
                refreshToken = o["refresh_token"]?.jsonPrimitive?.content ?: s.refreshToken,
                idTokenExp = System.currentTimeMillis() + ((o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600) - 300) * 1000,
            )
            save(fresh)
            fresh.idToken
        }
    }
}
