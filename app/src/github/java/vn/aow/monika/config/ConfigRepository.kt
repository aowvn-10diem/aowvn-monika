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
    /** V206: chính sách "bắt buộc chữ ký"; tách ra để test được nhánh fail-closed của bản chính thức. */
    private val requireSignature: (Context, String) -> Boolean = { c, key ->
        ConfigSignature.signatureRequired(c, key)
    },
) {
    private val cacheFile = File(context.filesDir, ASSET_NAME)
    private val atomicCache = AtomicFile(cacheFile)
    /** V206: dấu sha256 của cache hợp lệ gần nhất; thiếu dấu ⇒ không tin cache khi đã có khoá công khai. */
    private val markerFile = File(context.filesDir, VERIFIED_MARKER)
    private val refreshLock = Mutex()
    /** true khi cấu hình đang chạy đến từ máy chủ (bản cache đã tải hoặc vừa tải), false khi chỉ có bản đóng trong APK. */
    private val _remoteApplied = MutableStateFlow(false)
    val remoteApplied: StateFlow<Boolean> = _remoteApplied
    private val _config = MutableStateFlow(loadLocal())
    val config: StateFlow<MonikaConfig> = _config
    val current: MonikaConfig get() = _config.value

    private fun loadLocal(): MonikaConfig {
        // `assets/monika-config.json` nằm trong APK: tin theo kênh phân phối (chữ ký APK/chứng thư đã kiểm),
        // không verify chữ ký config ở đây — kẻ tấn công đổi được asset là đã đổi được cả APK.
        val bundled = parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        if (!cacheFile.exists() && !File(cacheFile.path + ".bak").exists()) return bundled
        val bytes = runCatching { atomicCache.readFully() }.onFailure { reportInvalid(it) }.getOrNull()
            ?: return bundled
        // V206: đã có khoá công khai ⇒ chỉ tin cache do chính app ghi sau một lượt tải được chấp nhận
        // (dấu sha256 ở [VERIFIED_MARKER]); cache tạo trước V206 hoặc tệp bị sửa thì bỏ, dùng bản trong APK.
        if (!cacheTrusted(bytes)) {
            Diagnostics.crumb(context, "config", "Bỏ cache cấu hình không có dấu xác nhận")
            return bundled
        }
        val remote = runCatching {
            ConfigValidation.parse(String(bytes, Charsets.UTF_8), bundled.configVersion)
        }.onFailure { reportInvalid(it) }.getOrNull() ?: return bundled
        _remoteApplied.value = true
        return remote
    }

    /**
     * true khi được dùng cache trên đĩa: chưa có khoá công khai (hành vi trước V206), hoặc cache khớp
     * dấu sha256 của đúng bytes đã ghi ở lượt tải hợp lệ gần nhất.
     */
    private fun cacheTrusted(bytes: ByteArray): Boolean {
        if (!ConfigSignature.hasPublicKey(configPublicKeyHex)) return true
        // diag: bỏ qua lỗi đọc dấu — thiếu/hỏng dấu thì coi như cache không đáng tin
        val marker = runCatching { markerFile.readText().trim() }.getOrNull()
        return marker != null && marker == Diagnostics.sha256Hex(bytes)
    }

    private fun reportInvalid(error: Throwable) {
        runCatching { Diagnostics.recordHandled(context, "app:config", "Không áp dụng cấu hình từ xa", error) }
    }

    private fun writeCache(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val output = atomicCache.startWrite()
        try {
            output.write(bytes)
            atomicCache.finishWrite(output)
        } catch (error: Throwable) {
            atomicCache.failWrite(output)
            throw error
        }
        // V206: dấu sha256 của đúng bytes vừa ghi — lần đọc sau chỉ dùng cache khi khớp dấu
        // (bản này chỉ ghi sau khi lượt tải đã được chấp nhận: verify chữ ký hoặc chính sách cho phép).
        // diag: bỏ qua lỗi ghi dấu — lần sau bỏ cache, dùng bản trong APK
        runCatching { markerFile.writeText(Diagnostics.sha256Hex(bytes)) }
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
        // diag: bỏ qua lỗi tải bản đã ký, ngay sau đây quyết định theo chính sách
        val payload = runCatching { fetchText(signedUrl) }
            .getOrNull()
            ?.let { ConfigSignature.verifyAndExtract(it, configPublicKeyHex) }
        if (payload != null) return payload
        if (requireSignature(context, configPublicKeyHex)) {
            // Bản chính thức: không có chữ ký hợp lệ thì KHÔNG rơi về config.json chưa ký (fail-closed);
            // ném lỗi để tầng gọi thử nguồn dự phòng khác, cuối cùng giữ cấu hình đang chạy.
            Diagnostics.crumb(context, "config", "config.signed.json không hợp lệ, từ chối bản chưa ký")
            error("Cấu hình tải về không có chữ ký hợp lệ")
        }
        Log.w(TAG, "Không có config.signed.json hợp lệ, dùng config.json chưa ký")
        Diagnostics.crumb(context, "config", "config.signed.json không hợp lệ, dùng bản chưa ký")
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
        /** V206: tệp dấu xác nhận cache (sha256) — chỉ dùng nội bộ, không phải config. */
        const val VERIFIED_MARKER = "config.verified"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String): MonikaConfig = json.decodeFromString(MonikaConfig.serializer(), text)
    }
}
