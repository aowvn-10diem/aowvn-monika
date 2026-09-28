package vn.aow.monika.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Đưa 1 file (tải về hoặc chọn từ máy) vào thư viện: giải nén .zip, còn lại chép vào thư mục riêng. */
object Importer {
    /** Định dạng nén chưa hỗ trợ: giữ nguyên file, thư viện sẽ nhắc user giải nén bằng app khác. */
    val UNSUPPORTED_ARCHIVES = setOf("rar", "7z")

    fun importFile(context: Context, src: File, deleteSource: Boolean): File {
        val dir = src.inputStream().use { importStream(context, src.name, it) }
        if (deleteSource) src.delete()
        return dir
    }

    fun importUri(context: Context, uri: Uri): File {
        val name = displayName(context, uri) ?: "game-${System.currentTimeMillis()}"
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Không mở được file")
        return input.use { importStream(context, name, it) }
    }

    private fun importStream(context: Context, fileName: String, input: InputStream): File {
        val dir = uniqueDir(GameStorage.games(context), fileName.substringBeforeLast('.').ifBlank { fileName })
        dir.mkdirs()
        try {
            if (fileName.endsWith(".zip", ignoreCase = true)) unzip(input, dir)
            else File(dir, fileName).outputStream().use { input.copyTo(it) }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return dir
    }

    fun unzip(input: InputStream, target: File) {
        val root = target.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(target, entry.name).canonicalFile
                // Chặn file zip độc hại ghi ra ngoài thư mục game (zip slip).
                if (!out.path.startsWith(root)) throw IOException("File nén không hợp lệ: ${entry.name}")
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private fun uniqueDir(parent: File, base: String): File {
        val clean = base.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
        var dir = File(parent, clean)
        var i = 2
        while (dir.exists()) dir = File(parent, "$clean ($i)").also { i++ }
        return dir
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
