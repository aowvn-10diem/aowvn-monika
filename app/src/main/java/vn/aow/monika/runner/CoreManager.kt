package vn.aow.monika.runner

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.library.Importer
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
    private val abi: String = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS } ?: "arm64-v8a"

    fun systemDir() = File(context.filesDir, "system").apply { mkdirs() }
    private fun coreDir(id: String) = File(context.filesDir, "cores/$id")
    private fun versionFile(id: String) = File(coreDir(id), "version")

    fun installedVersion(id: String): String? = versionFile(id).takeIf { it.exists() }?.readText()
    fun delete(id: String) = coreDir(id).deleteRecursively()

    suspend fun ensureCore(id: String, onStatus: (String) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val def = configRepo.current.cores[id] ?: throw IOException("Cấu hình chưa có lõi '$id'")
        val so = File(coreDir(id), "${id}_libretro_android.so")
        if (!so.exists() || installedVersion(id) != def.version) {
            withContext(Dispatchers.Main) { onStatus("Đang tải lõi giả lập $id (chỉ lần đầu)…") }
            coreDir(id).mkdirs()
            val tmp = File(coreDir(id), "download.tmp")
            download(def.url.replace("{abi}", abi)) { zip ->
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
        }
        def.systemFiles?.let { url ->
            // Một số lõi (PPSSPP...) cần thêm file hệ thống: font, dữ liệu...
            val marker = File(systemDir(), ".$id-${def.version}")
            if (!marker.exists()) {
                withContext(Dispatchers.Main) { onStatus("Đang tải dữ liệu hệ thống cho $id…") }
                download(url) { Importer.unzip(it, systemDir()) }
                marker.writeText("ok")
            }
        }
        so
    }

    private fun download(url: String, consume: (ZipInputStream) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Tải thất bại (HTTP ${response.code}): $url")
            ZipInputStream(response.body!!.byteStream().buffered()).use(consume)
        }
    }

    companion object {
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}
