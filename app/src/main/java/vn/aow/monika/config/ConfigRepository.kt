package vn.aow.monika.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.BuildConfig
import java.io.File

/**
 * Nguồn cấu hình theo thứ tự ưu tiên:
 * 1. Bản tải từ xa gần nhất (lưu cache) — nếu configVersion >= bản đóng gói.
 * 2. Bản đóng gói trong APK (assets/monika-config.json).
 *
 * Bản từ xa lỗi cú pháp sẽ bị bỏ qua, không bao giờ ghi đè bản đang chạy tốt.
 */
class ConfigRepository(
    private val context: Context,
    private val http: OkHttpClient,
) {
    private val cacheFile = File(context.filesDir, ASSET_NAME)
    private val _config = MutableStateFlow(loadLocal())
    val config: StateFlow<MonikaConfig> = _config
    val current: MonikaConfig get() = _config.value

    /** Bản đóng gói trong APK: không bao giờ dùng bản nào cũ hơn bản này. */
    private val bundledVersion by lazy { parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }).configVersion }

    private fun loadLocal(): MonikaConfig {
        val bundled = parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        val cached = runCatching { parse(cacheFile.readText()) }.getOrNull()
        return if (cached != null && cached.configVersion >= bundled.configVersion) cached else bundled
    }

    /** Lần hỏi máy chủ gần nhất: hỏi thưa ra (mặc định 6 giờ/lần) để tiết kiệm hạn mức Worker miễn phí. */
    private val checkedFile = File(context.filesDir, "config.checked")

    /** [force] = true (người dùng bấm "Cập nhật cấu hình") bỏ qua giới hạn tần suất. */
    suspend fun refresh(force: Boolean = false): Result<MonikaConfig> = withContext(Dispatchers.IO) {
        runCatching {
            if (!force && System.currentTimeMillis() - checkedFile.lastModified() < MIN_REFRESH_MS) return@runCatching current
            val request = Request.Builder().url(BuildConfig.REMOTE_CONFIG_URL).build()
            val text = http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body!!.string()
            }
            val cfg = parse(text) // Lỗi cú pháp sẽ ném ra đây, trước khi ghi cache.
            runCatching { checkedFile.writeText("ok") } // chỉ tính là "đã hỏi" khi tải được
            // Bản trên mạng cũ hơn bản trong APK (quên đồng bộ Cloudflare) → giữ bản trong APK.
            // Trước đây áp dụng luôn → config cũ đè mất hệ máy mới (vd. file .jar "Chưa nhận diện").
            if (cfg.configVersion < bundledVersion) return@runCatching current
            cacheFile.writeText(text)
            _config.value = cfg
            cfg
        }.onFailure { Log.w(TAG, "Không tải được cấu hình từ xa, dùng bản hiện có", it) }
    }

    companion object {
        private const val TAG = "ConfigRepository"
        const val MIN_REFRESH_MS = 6 * 3_600_000L
        const val ASSET_NAME = "monika-config.json"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String): MonikaConfig = json.decodeFromString(MonikaConfig.serializer(), text)
    }
}
