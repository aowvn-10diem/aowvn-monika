package vn.aow.monika.achievements

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vn.aow.monika.apkinstall.repack.AndroidKeystoreWrap
import vn.aow.monika.apkinstall.repack.KeyWrap

/** Tài khoản RetroAchievements trên máy: tên + ULID + khóa web API (khóa được mã hóa bằng Android Keystore). */
class RaAccount(context: Context, private val api: RaApi, private val wrap: KeyWrap = AndroidKeystoreWrap) {
    data class Creds(val user: String, val ulid: String, val key: String)

    private val sp = context.getSharedPreferences("ra", Context.MODE_PRIVATE)
    private val _creds = MutableStateFlow(load())
    val creds: StateFlow<Creds?> = _creds

    private fun load(): Creds? {
        val user = sp.getString("user", null) ?: return null
        val enc = sp.getString("key", null) ?: return null
        val key = runCatching { String(wrap.unwrap(Base64.decode(enc, Base64.NO_WRAP))) }.getOrNull() ?: return null
        return Creds(user, sp.getString("ulid", "") ?: "", key)
    }

    /** Kiểm tra bằng cách gọi thử hồ sơ; đúng thì lưu. Sai → [RaAuthException]. */
    suspend fun signIn(user: String, key: String): RaProfile {
        val u = user.trim(); val k = key.trim()
        val p = api.profile(u, k)
        sp.edit().putString("user", p.user.ifBlank { u }).putString("ulid", p.ulid)
            .putString("key", Base64.encodeToString(wrap.wrap(k.toByteArray()), Base64.NO_WRAP)).apply()
        _creds.value = load()
        return p
    }

    fun signOut() { sp.edit().clear().apply(); _creds.value = null; _inGame.value = false }

    // ---- Thành tựu trong game (rcheevos) ----
    private val _inGame = MutableStateFlow(loadToken() != null)
    /** Đã có mã đăng nhập để mở khóa thành tựu trong game. */
    val inGame: StateFlow<Boolean> = _inGame

    private fun loadToken(): String? {
        val enc = sp.getString("token", null) ?: return null
        return runCatching { String(wrap.unwrap(Base64.decode(enc, Base64.NO_WRAP))) }.getOrNull()
    }

    /** Mã đăng nhập (đã giải mã) cho rcheevos; null nếu chưa bật. */
    fun token(): String? = loadToken()

    /** Đổi mật khẩu lấy token rồi lưu mã hóa; mật khẩu bỏ ngay. */
    suspend fun enableInGame(password: String) {
        val user = _creds.value?.user ?: throw RaAuthException("Hãy đăng nhập RetroAchievements trước")
        val token = api.loginWithPassword(user, password)
        sp.edit().putString("token", Base64.encodeToString(wrap.wrap(token.toByteArray()), Base64.NO_WRAP)).apply()
        _inGame.value = true
    }

    fun disableInGame() { sp.edit().remove("token").apply(); _inGame.value = false }

    /** Hardcore (mặc định bật): khóa nạp state, cheat, chạy chậm khi chơi game có thành tựu. */
    var hardcore: Boolean
        get() = sp.getBoolean("hardcore", true)
        set(v) { sp.edit().putBoolean("hardcore", v).apply() }

    /** Từ lần đầu dùng ULID (tên có thể đổi). */
    val idForApi: String? get() = _creds.value?.let { it.ulid.ifBlank { it.user } }
}
