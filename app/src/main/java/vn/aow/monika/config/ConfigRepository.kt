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

    private fun loadLocal(): MonikaConfig {
        val bundled = parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        val cached = runCatching { parse(cacheFile.readText()) }.getOrNull()
        return if (cached != null && cached.configVersion >= bundled.configVersion) cached else bundled
    }

    suspend fun refresh(): Result<MonikaConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(BuildConfig.REMOTE_CONFIG_URL).build()
            val text = http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body!!.string()
            }
            val cfg = parse(text) // Lỗi cú pháp sẽ ném ra đây, trước khi ghi cache.
            cacheFile.writeText(text)
            _config.value = cfg
            cfg
        }.onFailure { Log.w(TAG, "Không tải được cấu hình từ xa, dùng bản hiện có", it) }
    }

    companion object {
        private const val TAG = "ConfigRepository"
        const val ASSET_NAME = "monika-config.json"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String): MonikaConfig = json.decodeFromString(MonikaConfig.serializer(), text)
    }
}
