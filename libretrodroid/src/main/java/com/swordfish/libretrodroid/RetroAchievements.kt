package com.swordfish.libretrodroid

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Aow Monika: RetroAchievements trong game (rcheevos qua JNI). Chỉ bật khi [GLRetroViewData.achievements] khác null.
 * Native gọi ngược [httpRequest], [onEvent], [onState] (static, tên cố định — đừng đổi tên khi không sửa achievements.cpp).
 */
object RetroAchievements {
    enum class State { LOGIN_FAILED, GAME_LOADED, GAME_NOT_FOUND, MEMORY_UNSUPPORTED, COMPLETED, DISCONNECTED, RECONNECTED }

    interface Listener {
        /** Mở khóa 1 thành tựu (gọi trên luồng giao diện). */
        fun onUnlocked(id: Int, title: String, description: String, badgeUrl: String, points: Int)
        fun onState(state: State, message: String?)
    }

    /**
     * @param consoleId mã hệ máy RetroAchievements (xem bảng trong app).
     * @param userAgent "AowMonika/<phiên bản> (Android <bản>)"; native tự nối mệnh đề rcheevos.
     * @param token mã đăng nhập RA (không phải khóa web API, không phải mật khẩu).
     */
    data class Config(
        val consoleId: Int,
        val userAgent: String,
        val hardcore: Boolean,
        val user: String,
        val token: String,
        val listener: Listener? = null,
    )

    @Volatile private var listener: Listener? = null
    private val main = Handler(Looper.getMainLooper())
    private val network = Executors.newFixedThreadPool(3)

    init {
        Class.forName("com.swordfish.libretrodroid.LibretroDroid") // chạy khối static nạp thư viện native trước khi gọi JNI
    }

    /** Gọi trước khi nạp game. */
    fun configure(config: Config) {
        listener = config.listener
        nativeConfigure(config.consoleId, config.userAgent, config.hardcore, config.user, config.token)
    }

    /** Thông tin game + danh sách thành tựu (JSON, rỗng nếu chưa nạp xong). */
    fun describeJson(): String = nativeDescribe()

    fun setHardcore(enabled: Boolean) = nativeSetHardcore(enabled)

    // ---- Native gọi ngược ----

    @JvmStatic
    fun httpRequest(id: Long, url: String, postData: String?, contentType: String?, userAgent: String) {
        network.execute {
            var status = RETRYABLE_CLIENT_ERROR
            var body = ByteArray(0)
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", userAgent)
                    if (postData != null) {
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded")
                        conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
                    }
                    status = conn.responseCode
                    val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                    body = stream?.use { it.readBytes() } ?: ByteArray(0)
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                status = RETRYABLE_CLIENT_ERROR
            }
            nativeOnHttpResponse(id, status, body)
        }
    }

    @JvmStatic
    fun onEvent(type: Int, id: Int, title: String?, description: String?, badgeUrl: String?, points: Int) {
        if (type != EVENT_ACHIEVEMENT_TRIGGERED) return
        val l = listener ?: return
        main.post { l.onUnlocked(id, title.orEmpty(), description.orEmpty(), badgeUrl.orEmpty(), points) }
    }

    @JvmStatic
    fun onState(what: Int, message: String?) {
        val l = listener ?: return
        val state = when (what) {
            1 -> State.LOGIN_FAILED
            2 -> State.GAME_LOADED
            3 -> State.GAME_NOT_FOUND
            4 -> State.MEMORY_UNSUPPORTED
            5 -> State.COMPLETED
            6 -> State.DISCONNECTED
            7 -> State.RECONNECTED
            else -> return
        }
        main.post { l.onState(state, message) }
    }

    private const val EVENT_ACHIEVEMENT_TRIGGERED = 1
    private const val RETRYABLE_CLIENT_ERROR = -2 // RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR

    @JvmStatic private external fun nativeConfigure(consoleId: Int, userAgent: String, hardcore: Boolean, user: String, token: String)
    @JvmStatic private external fun nativeOnHttpResponse(id: Long, status: Int, body: ByteArray)
    @JvmStatic private external fun nativeDescribe(): String
    @JvmStatic private external fun nativeSetHardcore(enabled: Boolean)
}
