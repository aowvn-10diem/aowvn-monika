package vn.aow.monika.config

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.aow.monika.diag.Diagnostics
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.BuildConfig
import java.io.File

/**
 * Nguồn cấu hình theo thứ tự ưu tiên:
 * 1. Bản tải từ xa gần nhất (lưu cache) — nếu hợp lệ và configVersion >= bản đóng gói.
 * 2. Bản đóng gói trong APK (assets/monika-config.json).
 *
 * Bản từ xa lỗi cú pháp, URL/hash/runner hoặc lùi phiên bản sẽ bị bỏ qua, không bao giờ ghi đè bản đang chạy tốt.
 */
class ConfigRepository(
    private val context: Context,
    private val http: OkHttpClient,
) {
    private val cacheFile = File(context.filesDir, ASSET_NAME)
    private val atomicCache = AtomicFile(cacheFile)
    private val refreshLock = Mutex()
    private val _config = MutableStateFlow(loadLocal())
    val config: StateFlow<MonikaConfig> = _config
    val current: MonikaConfig get() = _config.value

    private fun loadLocal(): MonikaConfig {
        val bundled = parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        if (!cacheFile.exists() && !File(cacheFile.path + ".bak").exists()) return bundled
        return runCatching {
            ConfigValidation.parse(String(atomicCache.readFully(), Charsets.UTF_8), bundled.configVersion)
        }.onFailure { reportInvalid(it) }.getOrDefault(bundled)
    }

    private fun reportInvalid(error: Throwable) {
        runCatching { Diagnostics.recordHandled(context, "app:config", "Không áp dụng cấu hình từ xa", error) }
    }

    private fun writeCache(text: String) {
        val output = atomicCache.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomicCache.finishWrite(output)
        } catch (error: Throwable) {
            atomicCache.failWrite(output)
            throw error
        }
    }

    /** Lần hỏi máy chủ gần nhất: hỏi thưa ra (mặc định 6 giờ/lần) để tiết kiệm hạn mức Worker miễn phí. */
    private val checkedFile = File(context.filesDir, "config.checked")

    /** [force] = true (người dùng bấm "Cập nhật cấu hình") bỏ qua giới hạn tần suất. */
    suspend fun refresh(force: Boolean = false): Result<MonikaConfig> = withContext(Dispatchers.IO) {
        refreshLock.withLock { runCatching {
            if (!force && System.currentTimeMillis() - checkedFile.lastModified() < MIN_REFRESH_MS) return@runCatching current
            val request = Request.Builder().url(BuildConfig.REMOTE_CONFIG_URL).build()
            val text = http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body!!.string()
            }
            val cfg = ConfigValidation.parse(text, current.configVersion)
            writeCache(text) // Đổi nguyên khối trước khi thay StateFlow; ghi lỗi giữ bản cũ.
            _config.value = cfg
            runCatching { checkedFile.writeText("ok") }
            cfg
        }.onFailure {
            reportInvalid(it)
            Log.w(TAG, "Không tải được cấu hình từ xa, dùng bản hiện có", it)
        } }

    }

    companion object {
        private const val TAG = "ConfigRepository"
        const val MIN_REFRESH_MS = 6 * 3_600_000L
        const val ASSET_NAME = "monika-config.json"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String): MonikaConfig = json.decodeFromString(MonikaConfig.serializer(), text)
    }
}
