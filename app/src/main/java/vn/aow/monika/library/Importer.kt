package vn.aow.monika.library

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ImportResult(
    /** Thư mục game trong thư viện. */
    val dir: File,
    /** Null = thành công. Có lỗi thì file nén gốc vẫn nằm trong [dir] để giải nén lại sau. */
    val error: String? = null,
)

/**
 * Đưa 1 file (tải về hoặc chọn từ máy) vào thư viện.
 * - .zip/.rar/.7z: giải nén (ArchiveExtractor). Lỗi (sai mật khẩu...) thì giữ file nén để thử lại.
 * - File khác: chép vào thư mục riêng.
 */
object Importer {

    fun importFile(context: Context, src: File, deleteSource: Boolean, passwords: List<String>): ImportResult {
        val dir = newGameDir(context, src.name)
        if (!ArchiveExtractor.isArchive(src)) {
            val dest = File(dir, src.name)
            if (!(deleteSource && src.renameTo(dest))) {
                src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                if (deleteSource) src.delete()
            }
            return ImportResult(dir)
        }
        val error = runCatching {
            ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use {
                ArchiveExtractor.extract(it, dir, passwords)
            }
        }.exceptionOrNull()
        if (error == null) {
            if (deleteSource) src.delete()
            return ImportResult(dir)
        }
        // Giải nén hỏng: dọn phần dở, giữ lại file nén trong thư mục game để user thử lại (nhập mật khẩu...).
        dir.listFiles()?.forEach { it.deleteRecursively() }
        val kept = File(dir, src.name)
        if (!(deleteSource && src.renameTo(kept))) src.copyTo(kept, overwrite = true).also { if (deleteSource) src.delete() }
        return ImportResult(dir, error.message)
    }

    fun importUri(context: Context, uri: Uri, passwords: List<String>): ImportResult {
        val name = displayName(context, uri) ?: "game-${System.currentTimeMillis()}"
        val dir = newGameDir(context, name)
        val resolver = context.contentResolver
        val tmp = File(dir, name)
        (resolver.openInputStream(uri) ?: throw IOException("Không mở được file")).use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        if (!ArchiveExtractor.isArchive(tmp)) return ImportResult(dir)
        return extractInPlace(dir, passwords)
    }

    /** Giải nén file nén đang nằm trong thư mục game (lần đầu lỗi, hoặc user vừa nhập mật khẩu). */
    fun extractInPlace(dir: File, passwords: List<String>): ImportResult {
        val archive = dir.listFiles().orEmpty().firstOrNull { it.isFile && ArchiveExtractor.isArchive(it) }
            ?: return ImportResult(dir, "Không tìm thấy file nén trong thư mục game.")
        val work = File(dir, ".dang-giai-nen").apply { deleteRecursively(); mkdirs() }
        val error = runCatching {
            ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY).use {
                ArchiveExtractor.extract(it, work, passwords)
            }
        }.exceptionOrNull()
        if (error != null) {
            work.deleteRecursively()
            return ImportResult(dir, error.message)
        }
        archive.delete()
        work.listFiles().orEmpty().forEach { it.renameTo(File(dir, it.name)) }
        work.deleteRecursively()
        return ImportResult(dir)
    }

    /** Giải nén zip từ luồng mạng (dùng cho file hệ thống của lõi giả lập). */
    fun unzip(input: InputStream, target: File) {
        val root = target.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(target, entry.name).canonicalFile
                if (!out.path.startsWith(root)) throw IOException("File nén không hợp lệ: ${entry.name}")
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private fun newGameDir(context: Context, fileName: String): File {
        val base = fileName.substringBeforeLast('.').ifBlank { fileName }
            .replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
        val parent = GameStorage.games(context)
        var dir = File(parent, base)
        var i = 2
        while (dir.exists()) dir = File(parent, "$base ($i)").also { i++ }
        return dir.apply { mkdirs() }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
