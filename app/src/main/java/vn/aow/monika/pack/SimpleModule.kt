package vn.aow.monika.pack

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.ModuleDef
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Gói thư viện native tải theo nhu cầu (khác [vn.aow.monika.azahar.AzaharModule] ở chỗ chung cho mọi gói đơn giản):
 * url có thể chứa "{abi}", tải ra file tạm → kiểm SHA-256 → giải nén (giữ cây thư mục, chặn ../) vào filesDir/packs/<id>/ → ghi version.
 * Gói sai/hỏng không bao giờ để lại thư mục dùng được.
 */
class SimpleModule(
    private val context: Context,
    private val http: OkHttpClient,
    private val configRepo: ConfigRepository,
) {
    /** Cùng cách chọn ABI với CoreManager: theo thư viện native của chính app (máy ảo x86 chạy app ARM vẫn đúng). */
    val abi: String = when (context.applicationInfo.nativeLibraryDir.substringAfterLast('/')) {
        "arm64" -> "arm64-v8a"
        "arm" -> "armeabi-v7a"
        else -> context.applicationInfo.nativeLibraryDir.substringAfterLast('/')
    }

    private fun def(id: String): ModuleDef? = configRepo.current.modules[id]
    fun dir(id: String) = File(context.filesDir, "packs/$id")

    fun supported(id: String): Boolean {
        val d = def(id) ?: return false
        return d.url.isNotBlank() && (d.abis.isEmpty() || abi in d.abis)
    }

    fun ready(id: String, mainFile: String): Boolean =
        supported(id) && File(dir(id), mainFile).isFile && File(dir(id), "version").takeIf { it.isFile }?.readText() == def(id)?.version

    fun sizeOf(id: String): Long = def(id)?.let { it.sizeByAbi[abi] ?: it.size } ?: 0L

    suspend fun ensure(id: String, mainFile: String, onStatus: (String) -> Unit = {}) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val d = def(id)?.takeIf { supported(id) } ?: throw IOException("Gói $id chưa có cho máy này ($abi)")
        if (ready(id, mainFile)) return@withContext
        val tmp = File(context.cacheDir, "pack-$id.zip")
        try {
            val md = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(d.url.replace("{abi}", abi)).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Tải gói $id thất bại (HTTP ${r.code})")
                val body = r.body!!
                val total = body.contentLength()
                var read = 0L; var last = -1
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); md.update(buf, 0, n); read += n
                            val pct = if (total > 0) (read * 100 / total).toInt() else -1
                            if (pct != last && pct >= 0) { last = pct; onStatus("Đang tải $id $pct%") }
                        }
                    }
                }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            val want = (d.sha256ByAbi[abi] ?: d.sha256).trim()
            if (want.isNotEmpty() && !got.equals(want, ignoreCase = true)) throw IOException("Gói $id sai SHA-256 (nhận $got)")
            val out = dir(id)
            out.deleteRecursively(); out.mkdirs()
            ZipInputStream(tmp.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    // Giữ cây thư mục trong zip (gói Kirikiri có assets/ui/...); chặn thoát khỏi thư mục gói (zip-slip).
                    val dest = File(out, e.name).canonicalFile
                    if (!dest.path.startsWith(out.canonicalPath + File.separator)) continue
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { z.copyTo(it) }
                }
            }
            if (!File(out, mainFile).isFile) { out.deleteRecursively(); throw IOException("Gói $id không có $mainFile") }
            File(out, "version").writeText(d.version)
        } finally { tmp.delete() }
    }
}
