package vn.aow.monika.azahar

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Engine 3DS (Azahar) dưới dạng module tải khi cần: gói .zip do workflow "Build engines" dựng,
 * chứa libcitra-android.so + thư viện phụ + needed.txt (thứ tự nạp) + jni-symbols.txt.
 * Nâng cấp = đổi modules.azahar.version trong config → app tải lại.
 */
class AzaharModule(
    private val context: Context,
    private val http: OkHttpClient,
    private val configRepo: ConfigRepository,
) {
    private val abi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    private val dir get() = File(context.filesDir, "engines/azahar")
    private val versionFile get() = File(dir, "version")

    private val def get() = configRepo.current.modules[ID]

    /** Config đã có link module và máy đúng kiến trúc → có thể dùng engine nhúng. */
    fun available(): Boolean {
        val d = def ?: return false
        return d.url.isNotBlank() && (d.abis.isEmpty() || abi in d.abis)
    }

    fun ready(): Boolean = available() && File(dir, MAIN).exists() && versionFile.takeIf { it.exists() }?.readText() == def?.version

    fun info(): String = runCatching { File(dir, "manifest.json").readText() }.getOrDefault("")
    fun delete() = dir.deleteRecursively()

    suspend fun ensure(onStatus: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        val d = def?.takeIf { available() } ?: throw IOException("Engine 3DS chưa có cho máy này")
        if (ready()) return@withContext
        withContext(Dispatchers.Main) { onStatus("Đang tải engine 3DS (chỉ lần đầu)…") }
        dir.deleteRecursively(); dir.mkdirs()
        var lastPct = -1
        http.newCall(Request.Builder().url(d.url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Tải engine thất bại (HTTP ${r.code})")
            val body = r.body!!
            val total = body.contentLength()
            var read = 0L
            val counting = object : java.io.FilterInputStream(body.byteStream()) {
                private fun tick(n: Long) {
                    if (n <= 0) return
                    read += n
                    val pct = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else -1
                    if (pct != lastPct && pct >= 0) { lastPct = pct; kotlinx.coroutines.runBlocking(Dispatchers.Main) { onStatus("Đang tải engine 3DS (chỉ lần đầu)… $pct%") } }
                }
                override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { tick(it.toLong()) }
            }
            ZipInputStream(counting.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val out = File(dir, File(e.name).name) // phẳng, chặn ../
                    out.outputStream().use { z.copyTo(it) }
                }
            }
        }
        if (!File(dir, MAIN).exists()) { dir.deleteRecursively(); throw IOException("Gói engine không có $MAIN") }
        versionFile.writeText(d.version)
    }

    private var loaded = false

    /** Nạp thư viện phụ theo needed.txt rồi thư viện chính. Gọi ở tiến trình game, sau [ensure]. */
    @Synchronized
    fun load() {
        if (loaded) return
        // Thư viện phụ (không kể main) có thể phụ thuộc lẫn nhau: thử nạp lặp tới khi hết tiến triển, rồi mới nạp main.
        var pending = dir.listFiles().orEmpty().filter { it.name.endsWith(".so") && it.name != MAIN }
        while (pending.isNotEmpty()) {
            val failed = pending.filter { runCatching { System.load(it.absolutePath) }.isFailure }
            if (failed.size == pending.size) break
            pending = failed
        }
        System.load(File(dir, MAIN).absolutePath)
        loaded = true
    }

    companion object {
        const val ID = "azahar"
        const val MAIN = "libcitra-android.so"
    }
}
