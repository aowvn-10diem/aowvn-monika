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
import vn.aow.monika.pack.PackTransaction

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
        return (d.url.isNotBlank() || bundled()) && (d.abis.isEmpty() || abi in d.abis)
    }

    /** Bản thử có thể đóng sẵn gói engine trong APK (assets/engines/azahar.zip): không cần link tải. */
    private fun bundled() = runCatching { context.assets.open(ASSET).close(); true }.getOrDefault(false)

    fun ready(): Boolean = runCatching { available() && File(dir, MAIN).isFile && versionFile.takeIf { it.isFile }?.readText() == def?.version }.getOrDefault(false)

    fun info(): String = runCatching { File(dir, "manifest.json").readText() }.getOrDefault("")
    fun delete() = dir.deleteRecursively()

    suspend fun ensure(onStatus: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        PackTransaction.locked(dir) {
            val d = def?.takeIf { (it.url.isNotBlank() || bundled()) && (it.abis.isEmpty() || abi in it.abis) }
                ?: throw IOException("Engine 3DS chưa có cho máy này")
            if (File(dir, MAIN).isFile && versionFile.takeIf { it.isFile }?.readText() == d.version) return@locked
            withContext(Dispatchers.Main) { onStatus("Đang tải engine 3DS (chỉ lần đầu)…") }
            PackTransaction.install(dir) { archive, candidate ->
                if (bundled()) {
                    context.assets.open(ASSET).use { PackTransaction.download(it, archive, d.sha256.trim()) }
                } else {
                    var last = -1
                    http.newCall(Request.Builder().url(d.url.replace("{abi}", abi)).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Tải engine thất bại (HTTP ${response.code})")
                        val body = response.body ?: throw IOException("Gói không có nội dung")
                        val total = body.contentLength()
                        body.byteStream().use { input ->
                            PackTransaction.download(input, archive, (d.sha256ByAbi[abi] ?: d.sha256).trim(), total) { read ->
                                val pct = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else -1
                                if (pct >= 0 && pct != last) { last = pct; withContext(Dispatchers.Main) { onStatus("Đang tải engine 3DS (chỉ lần đầu)… $pct%") } }
                            }
                        }
                    }
                }
                PackTransaction.unzip(archive, candidate, flatten = true)
                PackTransaction.validate(candidate, MAIN, abi)
                File(candidate, "version").writeText(d.version)
            }
        }
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
        const val ASSET = "engines/azahar.zip"
    }
}
