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
import java.io.File

/**
 * Nguồn cấu hình theo thứ tự ưu tiên:
 * 1. Bản tải từ xa gần nhất (lưu cache) — nếu hợp lệ và configVersion >= bản đóng gói.
 * 2. Bản đóng gói trong APK (assets/monika-config.json).
 *
 * V181: khi tải mới, hỏi GitHub Pages trước (`ConfigEndpoints.PAGES_CONFIG_URL`); Pages lỗi
 * (mạng/HTTP/JSON/phiên bản) mới rơi về Worker (`BuildConfig.REMOTE_CONFIG_URL`).
 *
 * Bản từ xa lỗi cú pháp, URL/hash/runner hoặc lùi phiên bản sẽ bị bỏ qua, không bao giờ ghi đè bản đang chạy tốt.
 */
class ConfigRepository(
    private val context: Context,
    private val http: OkHttpClient,
    /** V206: khoá công khai Ed25519 của kênh config; rỗng = đọc bản không ký như trước. */
    private val configPublicKeyHex: String = ConfigSignature.OFFICIAL_CONFIG_ED25519_PUBKEY_HEX,
) {
    private val cacheFile = File(context.filesDir, ASSET_NAME)
    private val atomicCache = AtomicFile(cacheFile)
    private val refreshLock = Mutex()
    /** true khi cấu hình đang chạy đến từ máy chủ (bản cache đã tải hoặc vừa tải), false khi chỉ có bản đóng trong APK. */
    private val _remoteApplied = MutableStateFlow(false)
    val remoteApplied: StateFlow<Boolean> = _remoteApplied
    private val _config = MutableStateFlow(loadLocal())
    val config: StateFlow<MonikaConfig> = _config
    val current: MonikaConfig get() = _config.value

    private fun loadLocal(): MonikaConfig {
        val bundled = parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        if (!cacheFile.exists() && !File(cacheFile.path + ".bak").exists()) return bundled
        val remote = runCatching {
            ConfigValidation.parse(String(atomicCache.readFully(), Charsets.UTF_8), bundled.configVersion)
        }.onFailure { reportInvalid(it) }.getOrNull() ?: return bundled
        _remoteApplied.value = true
        return remote
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
        refreshLock.withLock {
            // V112: tải cấu hình từ xa là thao tác → lỗi thật thành op-fail (mất mạng/hủy chỉ ghi vệt); hủy coroutine được ném lại.
            Diagnostics.opSuspend(context, Diagnostics.Op.FETCH_CONFIG) {
                if (!force && System.currentTimeMillis() - checkedFile.lastModified() < MIN_REFRESH_MS) return@opSuspend current
                step("tai-pages")
                // V181: Pages trước (giảm lượt gọi Worker); lỗi mạng/HTTP/JSON/phiên bản mới thử Worker.
                val loaded = try {
                    val text = fetchConfig(ConfigEndpoints.PAGES_CONFIG_URL, ConfigEndpoints.PAGES_SIGNED_CONFIG_URL)
                    step("kiem-tra")
                    text to ConfigValidation.parse(text, current.configVersion)
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Log.w(TAG, "Cấu hình Pages không dùng được, chuyển sang Worker", error)
                    step("tai-worker")
                    val text = fetchConfig(ConfigEndpoints.WORKER_CONFIG_URL, ConfigEndpoints.WORKER_SIGNED_CONFIG_URL)
                    step("kiem-tra")
                    text to ConfigValidation.parse(text, current.configVersion)
                }
                val (text, cfg) = loaded
                step("ghi-cache")
                writeCache(text) // Đổi nguyên khối trước khi thay StateFlow; ghi lỗi giữ bản cũ.
                _config.value = cfg
                _remoteApplied.value = true
                runCatching { checkedFile.writeText("ok") }
                cfg
            }.onFailure {
                Log.w(TAG, "Không tải được cấu hình từ xa, dùng bản hiện có", it)
            }
        }
    }

    /**
     * V206: ưu tiên bản đã ký nếu đã có khoá công khai (1 lượt GET). Chữ ký sai/thiếu tệp thì:
     * - bản chính thức ([ConfigSignature.signatureRequired]) ⇒ ném lỗi để rơi sang nguồn dự phòng;
     * - bản debug/tự build ⇒ đọc `config.json` như cũ.
     * Chưa có khoá công khai ⇒ luôn đọc `config.json` (hành vi trước V206).
     */
    private fun fetchConfig(plainUrl: String, signedUrl: String): String {
        if (!ConfigSignature.hasPublicKey(configPublicKeyHex)) return fetchText(plainUrl)
        // diag: bỏ qua lỗi tải bản đã ký, ngay sau đây rơi về config.json
        val payload = runCatching { fetchText(signedUrl) }
            .getOrNull()
            ?.let { ConfigSignature.verifyAndExtract(it, configPublicKeyHex) }
        if (payload != null) return payload
        Log.w(TAG, "Không có config.signed.json hợp lệ, dùng config.json chưa ký")
        Diagnostics.crumb(context, "config", "config.signed.json không hợp lệ, dùng bản chưa ký")
        if (ConfigSignature.signatureRequired(context, configPublicKeyHex)) {
            error("Cấu hình tải về không có chữ ký hợp lệ")
        }
        return fetchText(plainUrl)
    }


    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            // V166: đọc body qua mạng → đứt luồng ở đây là chuyện mạng (đánh dấu nguồn).
            Diagnostics.networkRead { response.body!!.string() }
        }
    }

    companion object {
        private const val TAG = "ConfigRepository"
        const val MIN_REFRESH_MS = 6 * 3_600_000L
        const val ASSET_NAME = "monika-config.json"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String): MonikaConfig = json.decodeFromString(MonikaConfig.serializer(), text)
    }
}
