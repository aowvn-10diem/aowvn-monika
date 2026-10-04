package vn.aow.monika.pack

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.ModuleDef
import java.io.File
import java.io.IOException

/**
 * Gói thư viện native tải theo nhu cầu (khác [vn.aow.monika.azahar.AzaharModule] ở chỗ chung cho mọi gói đơn giản):
 * url có thể chứa "{abi}", tải ra file tạm → kiểm SHA-256 → giải nén (giữ cây thư mục, chặn ../) vào filesDir/packs/<id>/ → ghi version.
 * Gói sai/hỏng giữ nguyên bản cũ; khóa theo gói, chỉ thay sau khi ứng viên hợp lệ.
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
        runCatching { supported(id) && File(dir(id), mainFile).isFile && File(dir(id), "version").takeIf { it.isFile }?.readText() == def(id)?.version }.getOrDefault(false)

    fun sizeOf(id: String): Long = def(id)?.let { it.sizeByAbi[abi] ?: it.size } ?: 0L

    suspend fun ensure(id: String, mainFile: String, onStatus: (String) -> Unit = {}) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        PackTransaction.locked(dir(id)) {
            val d = def(id)?.takeIf { it.url.isNotBlank() && (it.abis.isEmpty() || abi in it.abis) }
                ?: throw IOException("Gói $id chưa có cho máy này ($abi)")
            if (File(dir(id), mainFile).isFile && File(dir(id), "version").takeIf { it.isFile }?.readText() == d.version) return@locked
            PackTransaction.install(dir(id)) { archive, candidate ->
                val want = (d.sha256ByAbi[abi] ?: d.sha256).trim()
                http.newCall(Request.Builder().url(d.url.replace("{abi}", abi)).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Tải gói $id thất bại (HTTP ${response.code})")
                    val body = response.body ?: throw IOException("Gói không có nội dung")
                    val total = body.contentLength(); var last = -1
                    body.byteStream().use { input ->
                        PackTransaction.download(input, archive, want, total) { read ->
                            val pct = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else -1
                            if (pct >= 0 && pct != last) { last = pct; onStatus("Đang tải $id $pct%") }
                        }
                    }
                }
                PackTransaction.unzip(archive, candidate, mainFile = mainFile)
                PackTransaction.validate(candidate, mainFile, abi)
                File(candidate, "version").writeText(d.version)
            }
        }
    }
}
