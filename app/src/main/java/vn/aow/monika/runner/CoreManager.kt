package vn.aow.monika.runner

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.library.Importer
import vn.aow.monika.pack.PackTransaction
import vn.aow.monika.config.CoreDef
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Tải lõi libretro (.so) khi cần, theo link trong cấu hình.
 * Nâng cấp lõi = đổi "version" trong config, app tự tải lại. Không cần phát hành APK.
 */
class CoreManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val configRepo: ConfigRepository,
) {
    // Theo thư viện native của CHÍNH app (không theo máy): máy x86 chạy app ARM qua lớp dịch vẫn cần lõi ARM, nếu không dlopen thất bại.
    private val abi: String = when (context.applicationInfo.nativeLibraryDir.substringAfterLast('/')) {
        "arm64" -> "arm64-v8a"
        "arm" -> "armeabi-v7a"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> null
    } ?: Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS } ?: "arm64-v8a"

    fun systemDir() = File(context.filesDir, "system").apply { mkdirs() }
    private fun coreDir(id: String) = File(context.filesDir, "cores/$id")
    private fun versionFile(id: String) = File(coreDir(id), "version")

    fun installedVersion(id: String): String? = versionFile(id).takeIf { it.exists() }?.readText()

    /** Ghi chú về bản lõi đang cài (ngày tải, kích thước, Last-Modified của máy chủ) — đính vào báo lỗi. */
    fun info(id: String): String = runCatching { File(coreDir(id), "info.txt").readText() }.getOrDefault("")
    fun delete(id: String) = coreDir(id).deleteRecursively()

    private fun CoreDef.supportsAbi() = (abis.isEmpty() || abi in abis) && (artifacts.isEmpty() || abi in artifacts)
    private fun CoreDef.wantedVersion() = artifacts[abi]?.version ?: version

    /** Lõi có chạy được trên kiến trúc CPU của app này không (vd. melondsds chỉ có bản arm64). */
    fun supports(id: String): Boolean {
        val def = configRepo.current.cores[id] ?: return false
        return def.supportsAbi()
    }

    fun isReady(id: String): Boolean {
        val def = configRepo.current.cores[id] ?: return false
        return def.supportsAbi() && File(coreDir(id), "${id}_libretro_android.so").exists() && installedVersion(id) == def.wantedVersion() &&
            (def.systemFiles == null || File(systemDir(), ".$id-${def.wantedVersion()}").exists())
    }

    /** Tải sẵn lõi cho các hệ máy đang có game (chạy ngầm) → bấm Chơi vào game ngay, không chờ tải. */
    suspend fun prefetch(ids: Collection<String>) {
        for (id in ids.distinct()) if (!isReady(id)) runCatching { ensureCore(id) }
    }

    /** Các lõi trong [ids] còn thiếu/lỗi thời (bỏ lõi không tồn tại trong config hoặc không hợp ABI máy này). */
    fun missing(ids: Collection<String>): List<String> = ids.distinct().filter { id ->
        val def = configRepo.current.cores[id] ?: return@filter false
        def.supportsAbi() && !isReady(id)
    }

    /**
     * Tải lần lượt các lõi còn thiếu; [onProgress](tên lõi, thứ tự, tổng, %) để báo thông báo. Trả về số lõi tải được.
     * Lõi nào lỗi (mạng...) thì bỏ qua, bấm Chơi sẽ tự tải lại.
     */
    suspend fun prefetchWithProgress(ids: Collection<String>, onProgress: (String, Int, Int, Int) -> Unit): Int {
        val todo = missing(ids)
        var ok = 0
        todo.forEachIndexed { i, id ->
            onProgress(id, i + 1, todo.size, 0)
            val done = runCatching { ensureCore(id) { st -> st.substringAfterLast(' ').removeSuffix("%").toIntOrNull()?.let { onProgress(id, i + 1, todo.size, it) } } }
            if (done.isSuccess) ok++
        }
        return ok
    }

    /** Khóa theo tiến trình (Mutex) rồi khóa theo file (giữa tiến trình chính và tiến trình :game) → không ghi đè lõi của nhau. */
    suspend fun ensureCore(id: String, onStatus: (String) -> Unit = {}): File = withContext(Dispatchers.IO) {
        PackTransaction.locked(coreDir(id)) { ensureCoreLocked(id, onStatus) }
    }

    private suspend fun ensureCoreLocked(id: String, onStatus: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val def = configRepo.current.cores[id] ?: throw IOException("Cấu hình chưa có lõi '$id'")
        if (!def.supportsAbi()) {
            val allowed = if (def.artifacts.isEmpty()) def.abis else def.artifacts.keys.filter { def.abis.isEmpty() || it in def.abis }
            throw IOException("Lõi này chỉ chạy trên máy ${allowed.joinToString(" / ")} (máy bạn: $abi).")
        }
        val wantedVersion = def.wantedVersion()
        val artifact = def.artifacts[abi]
        val so = File(coreDir(id), "${id}_libretro_android.so")
        if (!so.exists() || installedVersion(id) != wantedVersion) {
            withContext(Dispatchers.Main) { onStatus("Đang tải lõi giả lập (chỉ lần đầu)…") }
            if (artifact == null) {
                coreDir(id).mkdirs()
                val tmp = File(coreDir(id), "download.tmp").apply { delete() }
                var lastPct = -1
                var serverDate = ""
                download(def.url.replace("{abi}", abi), { serverDate = it }, { pct ->
                    if (pct != lastPct) { lastPct = pct; kotlinx.coroutines.runBlocking(Dispatchers.Main) { onStatus("Đang tải lõi giả lập (chỉ lần đầu)… $pct%") } }
                }) { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: throw IOException("Gói lõi không chứa file .so")
                        if (entry.name.endsWith(".so")) {
                            tmp.outputStream().use { zip.copyTo(it) }
                            break
                        }
                    }
                }
                if (!tmp.renameTo(so)) throw IOException("Không lưu được lõi $id")
                versionFile(id).writeText(def.version)
                runCatching {
                    File(coreDir(id), "info.txt").writeText(
                        "cfg v${def.version} · $abi · ${so.length() / 1024}KB · tải ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(System.currentTimeMillis())}" +
                            (if (serverDate.isNotBlank()) " · bản dựng $serverDate" else "")
                    )
                }
            } else {
                val url = artifact.url
                val expectedHash = artifact.sha256
                if (!expectedHash.matches(Regex("[a-fA-F0-9]{64}")) || wantedVersion.isBlank())
                    throw IOException("Ảnh chụp lõi thiếu hash/version")
                PackTransaction.install(coreDir(id)) { archive, candidate ->
                    var serverDate = ""; var lastPct = -1
                    http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Tải lõi thất bại (HTTP ${response.code})")
                        serverDate = response.header("Last-Modified").orEmpty()
                        val body = response.body ?: throw IOException("Gói lõi không có nội dung")
                        val total = body.contentLength()
                        body.byteStream().use { input ->
                            PackTransaction.download(input, archive, expectedHash, total) { read ->
                                val pct = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else -1
                                if (pct >= 0 && pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) { onStatus("Đang tải lõi giả lập (chỉ lần đầu)… $pct%") }
                                }
                            }
                        }
                    }
                    PackTransaction.unzip(archive, candidate)
                    PackTransaction.validate(candidate, so.name, abi)
                    File(candidate, "version").writeText(wantedVersion)
                    File(candidate, "info.txt").writeText(
                        "cfg v$wantedVersion · $abi · ${File(candidate, so.name).length() / 1024}KB · tải ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(System.currentTimeMillis())}" +
                            (if (serverDate.isNotBlank()) " · bản dựng $serverDate" else "")
                    )
                }
            }
        }
        def.systemFiles?.let { url ->
            // Một số lõi (PPSSPP...) cần thêm file hệ thống: font, dữ liệu...
            val marker = File(systemDir(), ".$id-$wantedVersion")
            if (!marker.exists()) {
                withContext(Dispatchers.Main) { onStatus("Đang tải dữ liệu hệ thống cho $id…") }
                download(url) { Importer.unzip(it, systemDir()) }
                marker.writeText("ok")
            }
        }
        // Đánh dấu vừa dùng → bộ dọn bộ đệm không xóa lõi đang dùng thường xuyên.
        coreDir(id).setLastModified(System.currentTimeMillis())
        so
    }

    private fun download(url: String, onHeaders: ((String) -> Unit)? = null, progress: ((Int) -> Unit)? = null, consume: (ZipInputStream) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Tải thất bại (HTTP ${response.code}): $url")
            onHeaders?.invoke(response.header("Last-Modified").orEmpty())
            val body = response.body!!
            val total = body.contentLength()
            // Đếm byte đã tải để báo % (máy chậm mạng biết đang tải chứ không phải treo).
            val counting = object : java.io.FilterInputStream(body.byteStream()) {
                var read = 0L
                private fun tick(n: Long) { if (n > 0) { read += n; if (total > 0) progress?.invoke((read * 100 / total).toInt().coerceIn(0, 100)) } }
                override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { tick(it.toLong()) }
            }
            ZipInputStream(counting.buffered()).use(consume)
        }
    }

    companion object {
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}
