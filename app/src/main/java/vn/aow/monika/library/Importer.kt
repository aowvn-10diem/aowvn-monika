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
    /** File nén nhiều phần chưa đủ bộ → đang chờ phần tiếp theo (dir = thư mục chờ, không phải thư viện). */
    val pending: Boolean = false,
)

/**
 * Đưa 1 file (tải về hoặc chọn từ máy) vào thư viện.
 * - .zip/.rar/.7z: giải nén (ArchiveExtractor). Lỗi (sai mật khẩu...) thì giữ file nén để thử lại.
 * - File khác: chép vào thư mục riêng.
 */
object Importer {

    fun importFile(context: Context, src: File, deleteSource: Boolean, passwords: List<String>): ImportResult {
        MultiPart.parse(src.name)?.let { return importPart(context, src, it, passwords) }
        // Game.rar đầu bộ cũ (Game.rar + .r00...) nằm cùng chỗ các phần khác → gom chung.
        if (src.extension.equals("rar", true) && src.parentFile?.listFiles().orEmpty().any { MultiPart.parse(it.name)?.base == src.name.lowercase() }) {
            return importPart(context, src, MultiPart.Piece(src.name.lowercase(), 0), passwords)
        }
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

    /**
     * 1 phần của file nén nhiều phần: gom vào thư mục chờ `_TaiVe/_phan/<tên bộ>/`.
     * Đủ bộ liên tục → thử giải nén; thiếu phần → trả về pending, chờ phần tiếp theo.
     */
    private fun importPart(context: Context, src: File, piece: MultiPart.Piece, passwords: List<String>): ImportResult {
        val staging = File(GameStorage.downloads(context), "_phan/" + piece.base.replace(Regex("""[\\/:*?"<>|]"""), "_")).apply { mkdirs() }
        val moved = File(staging, src.name)
        if (src.canonicalPath != moved.canonicalPath && !src.renameTo(moved)) { src.copyTo(moved, overwrite = true); src.delete() }
        val parts = MultiPart.siblings(staging, piece)
        val pending = { msg: String -> ImportResult(staging, msg, pending = true) }
        if (!MultiPart.looksContiguous(parts)) return pending("Đã nhận ${parts.size} phần của \"${piece.base}\". Đang chờ các phần còn lại.")

        val dir = newGameDir(context, piece.base)
        val error = runCatching { ArchiveExtractor.extractParts(parts, dir, passwords) }.exceptionOrNull()
        if (error == null) {
            // Giữ thông tin bài viết (ảnh bìa...) đã ghi lúc nhận các phần trước.
            File(staging, ".monika.json").takeIf { it.exists() }?.copyTo(File(dir, ".monika.json"), overwrite = true)
            staging.deleteRecursively()
            return ImportResult(dir)
        }
        dir.deleteRecursively()
        if (error is ArchiveExtractor.PasswordException) return ImportResult(staging, error.message, pending = true)
        return pending("Đã nhận ${parts.size} phần của \"${piece.base}\" nhưng chưa đủ bộ. Tải tiếp các phần còn lại.")
    }

    fun importUri(context: Context, uri: Uri, passwords: List<String>): ImportResult {
        val name = displayName(context, uri) ?: "game-${System.currentTimeMillis()}"
        if (MultiPart.parse(name) != null || name.endsWith(".rar", true)) {
            // Chép ra thư mục tải về trước, để các phần của cùng bộ nằm cạnh nhau.
            val tmp = File(GameStorage.downloads(context), name)
            (context.contentResolver.openInputStream(uri) ?: throw IOException("Không mở được file")).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            return importFile(context, tmp, deleteSource = true, passwords = passwords)
        }
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
