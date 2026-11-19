package vn.aow.monika.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import vn.aow.monika.util.SafeZipPath

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

    /**
     * V112: giải nén là thao tác người dùng → lỗi thật (file hỏng, hết chỗ, lib 7-Zip lỗi…) để lại báo cáo `op-fail`.
     * Cần mật khẩu, và máy thiếu gói 7-Zip mà không có mạng để tải, là luồng bình thường → chỉ ghi vệt, không báo.
     * Tên bước kèm định dạng file (`giai-nen:7z`) để đọc báo cáo là biết hỏng ở bộ giải nào.
     */
    internal fun extractOp(context: Context?, name: String, block: () -> Unit): Throwable? {
        if (context == null) return runCatching(block).exceptionOrNull() // diag: bỏ qua — gọi từ nơi không có Context (test/tiện ích)
        val stepName = "giai-nen:" + archiveFormat(name)
        return vn.aow.monika.diag.Diagnostics.op(context, vn.aow.monika.diag.Diagnostics.Op.EXTRACT, name,
            expected = { it is ArchiveExtractor.PasswordException || it is ArchiveExtractor.NeedsExtractorPackException }) { step(stepName); block() }.exceptionOrNull()
    }

    /** Đuôi file nén cho tên bước: chỉ chữ/số, không lộ tên file. Bộ nhiều phần `game.7z.001` → `7z`. */
    private fun archiveFormat(name: String): String {
        val n = name.lowercase()
        val ext = if (Regex("""\.7z\.\d{3}$""").containsMatchIn(n)) "7z" else n.substringAfterLast('.', "")
        return ext.filter { it in 'a'..'z' || it in '0'..'9' }.take(8).ifBlank { "khac" }
    }

    fun importFile(context: Context, src: File, deleteSource: Boolean, passwords: List<String>, progress: ExtractProgress? = null): ImportResult {
        MultiPart.parse(src.name)?.let { return importPart(context, src, it, passwords, progress) }
        // Game.rar đầu bộ cũ (Game.rar + .r00...) nằm cùng chỗ các phần khác → gom chung.
        if (src.extension.equals("rar", true) && src.parentFile?.listFiles().orEmpty().any { MultiPart.parse(it.name)?.base == src.name.lowercase() }) {
            return importPart(context, src, MultiPart.Piece(src.name.lowercase(), 0), passwords, progress)
        }
        val archive = ArchiveExtractor.isArchive(src)
        val dir = gameDirFor(context, src.name, archive)
        if (!archive) {
            val dest = File(dir, src.name)
            // Tệp thường (không phải file nén): chép thẳng vào thư mục game. Trước đây nhánh này không có báo cáo
            // nên tệp không chép được (hết dung lượng, quyền, tệp nguồn biến mất) chỉ hiện lỗi chung chung.
            vn.aow.monika.diag.Diagnostics.op(context, vn.aow.monika.diag.Diagnostics.Op.IMPORT, src.name) {
                step("nhap-tep-thuong")
                if (!(deleteSource && src.renameTo(dest))) {
                    step("chep-file")
                    src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                    if (deleteSource) src.delete()
                }
            }.getOrThrow()
            return ImportResult(dir)
        }
        val error = extractOp(context, src.name) { ArchiveExtractor.extract(src, dir, passwords, progress) }
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
    private fun importPart(context: Context, src: File, piece: MultiPart.Piece, passwords: List<String>, progress: ExtractProgress?): ImportResult {
        val staging = File(GameStorage.downloads(context), "_phan/" + piece.base.replace(Regex("""[\\/:*?"<>|]"""), "_")).apply { mkdirs() }
        val moved = File(staging, src.name)
        if (src.canonicalPath != moved.canonicalPath && !src.renameTo(moved)) { src.copyTo(moved, overwrite = true); src.delete() }
        val parts = MultiPart.siblings(staging, piece)
        val pending = { msg: String -> ImportResult(staging, msg, pending = true) }
        if (!MultiPart.looksContiguous(parts)) return pending("Đã nhận ${parts.size} phần của \"${piece.base}\". Đang chờ các phần còn lại.")

        val dir = newGameDir(context, piece.base)
        val error = extractOp(context, piece.base) { ArchiveExtractor.extractParts(parts, dir, passwords, progress) }
        if (error == null) {
            // Giữ thông tin bài viết (ảnh bìa...) đã ghi lúc nhận các phần trước.
            File(staging, ".monika.json").takeIf { it.exists() }?.copyTo(File(dir, ".monika.json"), overwrite = true)
            staging.deleteRecursively()
            return ImportResult(dir)
        }
        dir.deleteRecursively()
        if (error is ArchiveExtractor.PasswordException) return ImportResult(staging, error.message, pending = true)
        // Thiếu gói 7-Zip + không có mạng: báo đúng việc cần làm, không nói "thiếu phần" gây hiểu nhầm.
        if (error is ArchiveExtractor.NeedsExtractorPackException) return ImportResult(staging, error.message, pending = true)
        return pending("Đã nhận ${parts.size} phần của \"${piece.base}\" nhưng chưa đủ bộ. Tải tiếp các phần còn lại.")
    }

    fun importUri(context: Context, uri: Uri, passwords: List<String>, progress: ExtractProgress? = null): ImportResult {
        val name = displayName(context, uri) ?: "game-${System.currentTimeMillis()}"
        if (MultiPart.parse(name) != null || name.endsWith(".rar", true)) {
            // Chép ra thư mục tải về trước, để các phần của cùng bộ nằm cạnh nhau.
            val tmp = File(GameStorage.downloads(context), name)
            copyUri(context, uri, tmp, name)
            return importFile(context, tmp, deleteSource = true, passwords = passwords, progress = progress)
        }
        val dir = gameDirFor(context, name, ArchiveExtractor.isArchiveName(name))
        val tmp = File(dir, name)
        copyUri(context, uri, tmp, name)
        if (!ArchiveExtractor.isArchive(tmp)) return ImportResult(dir)
        return extractInPlace(dir, passwords, progress, context)
    }

    /** V112: mở/chép file người dùng chọn là bước "nhập game" → lỗi để lại op-fail rồi ném lại như cũ cho giao diện hiện thông báo. */
    private fun copyUri(context: Context, uri: Uri, dest: File, name: String) {
        // V204: tên dùng cho báo cáo cũng phải là tên đơn đã làm sạch.
        val safeName = SafeZipPath.sanitizeSingleFileName(name) ?: "game-${System.currentTimeMillis()}"
        vn.aow.monika.diag.Diagnostics.op(context, vn.aow.monika.diag.Diagnostics.Op.IMPORT, safeName) {
            step("mo-file")
            (context.contentResolver.openInputStream(uri) ?: throw IOException("Không mở được file")).use { input ->
                step("chep-file")
                dest.outputStream().use { input.copyTo(it) }
            }
        }.getOrThrow()
    }

    /** Giải nén file nén đang nằm trong thư mục game (lần đầu lỗi, hoặc user vừa nhập mật khẩu). */
    fun extractInPlace(dir: File, passwords: List<String>, progress: ExtractProgress? = null, context: Context? = null): ImportResult {
        val archive = dir.listFiles().orEmpty().firstOrNull { it.isFile && ArchiveExtractor.isArchive(it) }
            ?: return ImportResult(dir, "Không tìm thấy file nén trong thư mục game.")
        val work = File(dir, ".dang-giai-nen").apply { deleteRecursively(); mkdirs() }
        val error = extractOp(context, archive.name) { ArchiveExtractor.extract(archive, work, passwords, progress) }
        if (error != null) {
            work.deleteRecursively()
            return ImportResult(dir, error.message)
        }
        // Chuyển ra thư mục game TRƯỚC khi xoá bất cứ thứ gì: `renameTo` hỏng (thiếu quyền, tên đích đã bị chiếm)
        // mà vẫn xoá thư mục tạm thì tệp vừa giải nén của người chơi mất luôn, không có đường lấy lại.
        val moveError = moveOut(context, work, dir, archive.name)
        if (moveError != null) {
            return ImportResult(dir, moveError.message ?: "Không chuyển được tệp giải nén ra thư mục game")
        }
        archive.delete()
        work.deleteRecursively()
        return ImportResult(dir)
    }

    /** Chuyển tệp đã giải nén từ thư mục tạm ra [dir]; dừng ở tệp lỗi đầu tiên và để nguyên phần còn lại trong tạm. */
    private fun moveOut(context: Context?, work: File, dir: File, archiveName: String): Throwable? {
        val move = {
            work.listFiles().orEmpty().forEach { f ->
                if (!f.renameTo(File(dir, f.name))) throw IOException("Không chuyển được \"${f.name}\" ra thư mục game")
            }
        }
        if (context == null) return runCatching { move() }.exceptionOrNull() // diag: gọi từ nơi không có Context (test/tiện ích)
        return vn.aow.monika.diag.Diagnostics.op(context, vn.aow.monika.diag.Diagnostics.Op.EXTRACT, archiveName) {
            step("chuyen-ra-thu-muc-game")
            move()
        }.exceptionOrNull()
    }

    /** Giải nén zip từ luồng mạng (dùng cho file hệ thống của lõi giả lập). */
    fun unzip(input: InputStream, target: File) {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = SafeZipPath.resolve(target, entry.name)
                    ?: throw IOException("File nén không hợp lệ: ${entry.name}")
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    /**
     * Thư mục đích cho một tệp rời. Nhập riêng `.cue` rồi `.bin` (chọn từng tệp, hoặc chọn nhiều tệp) thì tệp thứ
     * hai phải vào **cùng** thư mục với tệp danh sách — mỗi tệp một thư mục thì lõi PS1 không nạp được game
     * (issue #322, #578). Gói nén luôn có thư mục riêng: giải nén ra nhiều tệp nên không gộp vào bộ đĩa đang có.
     */
    private fun gameDirFor(context: Context, name: String, archive: Boolean): File =
        (if (archive) null else DiscSet.companionFolder(GameStorage.games(context), name)) ?: newGameDir(context, name)

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
            // V204: tên do hệ thống trả về có thể chứa đường dẫn/ký tự điều khiển — làm sạch trước khi đặt tên thư mục.
            if (c.moveToFirst()) SafeZipPath.sanitizeSingleFileName(c.getString(0).orEmpty()) else null
        }
}
