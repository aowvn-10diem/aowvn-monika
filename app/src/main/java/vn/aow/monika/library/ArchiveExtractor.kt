package vn.aow.monika.library

import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Báo tiến độ giải nén 0..100 (chỉ gọi khi % đổi). */
fun interface ExtractProgress {
    fun update(percent: Int)
}

/**
 * Giải nén zip / rar (cả RAR5) / 7z bằng libarchive (thư viện C, chạy native nên nhanh).
 * Mật khẩu: libarchive giải được zip và rar có mật khẩu. 7z có mật khẩu → [SevenZipExtractor].
 */
object ArchiveExtractor {
    val EXTENSIONS = setOf("zip", "rar", "7z")

    class PasswordException(message: String) : IOException(message)

    fun isArchive(file: File) = file.extension.lowercase() in EXTENSIONS || MultiPart.parse(file.name) != null

    fun extract(source: File, target: File, passwords: List<String>, progress: ExtractProgress? = null) =
        extractParts(listOf(source), target, passwords, progress)

    /** 1 file, hoặc file nén chia nhiều phần (part1.rar, part2.rar… / .7z.001, .002…) theo đúng thứ tự. */
    fun extractParts(parts: List<File>, target: File, passwords: List<String>, progress: ExtractProgress? = null) {
        try {
            readAll(parts, target, passwords, progress)
        } catch (e: ArchiveException) {
            val error = translate(e)
            // libarchive không giải được 7z có mật khẩu → thử bộ giải 7z thuần Java.
            if (!is7z(parts.first())) throw error
            target.listFiles()?.forEach { it.deleteRecursively() }
            try {
                SevenZipExtractor.extract(parts, target, passwords, progress)
            } catch (e7: IOException) {
                target.listFiles()?.forEach { it.deleteRecursively() }
                throw if (e7 is PasswordException) e7 else error
            }
        }
    }

    fun is7z(file: File): Boolean {
        val n = file.name.lowercase()
        return n.endsWith(".7z") || Regex("""\.7z\.\d{3}$""").containsMatchIn(n)
    }

    private fun readAll(parts: List<File>, target: File, passwords: List<String>, progress: ExtractProgress?) {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val total = parts.sumOf { it.length() }.coerceAtLeast(1)
        val tracker = Tracker(progress)
        val buffer = ByteBuffer.allocateDirect(CHUNK)
        val archive = Archive.readNew()
        try {
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            passwords.filter { it.isNotEmpty() }.forEach { Archive.readAddPassphrase(archive, it.toByteArray()) }
            Archive.readOpenFileNames(archive, parts.map { it.absolutePath.toByteArray() }.toTypedArray(), BLOCK_SIZE)
            // Tiến độ = số byte nén đã đọc / tổng dung lượng file nén.
            val report = { tracker.set(Archive.filterBytes(archive, -1) * 100 / total) }
            while (true) {
                val entry = try {
                    Archive.readNextHeader(archive)
                } catch (e: ArchiveException) {
                    if (e.code == ARCHIVE_EOF) 0L else throw e
                }
                if (entry == 0L) break // Hết file.
                val name = entryName(entry)
                if (name.isBlank()) { Archive.readDataSkip(archive); continue }
                val out = File(target, name).canonicalFile
                // Chặn file nén độc hại ghi ra ngoài thư mục game.
                if (!out.path.startsWith(root)) throw IOException("File nén không hợp lệ: $name")
                val type = ArchiveEntry.filetype(entry)
                when {
                    type == ArchiveEntry.AE_IFDIR || name.endsWith("/") -> {
                        out.mkdirs()
                        Archive.readDataSkip(archive)
                    }
                    type == ArchiveEntry.AE_IFREG || type == 0 -> {
                        out.parentFile?.mkdirs()
                        // Đọc từng khúc (thay vì readDataIntoFd) để cập nhật % cả khi 1 file nặng vài GB (ISO PSP).
                        FileOutputStream(out).channel.use { ch ->
                            while (true) {
                                buffer.clear()
                                Archive.readData(archive, buffer)
                                if (buffer.position() == 0) break // Hết file này.
                                buffer.flip()
                                while (buffer.hasRemaining()) ch.write(buffer)
                                report()
                            }
                        }
                    }
                    else -> Archive.readDataSkip(archive) // Symlink, file đặc biệt: bỏ qua.
                }
                report()
            }
            tracker.set(100)
        } finally {
            runCatching { Archive.readFree(archive) }
        }
    }

    /** Tên file trong file nén: ưu tiên UTF-8, không phải thì thử bảng mã Windows tiếng Việt. */
    private fun entryName(entry: Long): String {
        ArchiveEntry.pathnameUtf8(entry)?.let { return it }
        val bytes = ArchiveEntry.pathname(entry) ?: return ""
        return decodeName(bytes)
    }

    internal fun decodeName(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, runCatching { Charset.forName("windows-1258") }.getOrDefault(Charsets.ISO_8859_1))
    }

    private fun translate(e: ArchiveException): IOException {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("passphrase", ignoreCase = true) ->
                PasswordException("File nén có mật khẩu. Hãy nhập đúng mật khẩu (thường ghi trong bài viết).")
            msg.contains("ncrypt", ignoreCase = true) ->
                PasswordException("File nén dùng kiểu mã hóa chưa hỗ trợ. Hãy giải nén bằng ZArchiver rồi thêm lại.")
            msg.contains("volume", ignoreCase = true) ->
                IOException("Thiếu phần của file nén nhiều phần (part1, part2...). Hãy tải đủ các phần rồi thêm lại.")
            else -> IOException("Không giải nén được: $msg", e)
        }
    }

    /** Gọi [ExtractProgress] chỉ khi % tăng, tránh cập nhật thông báo quá dày. */
    internal class Tracker(private val progress: ExtractProgress?) {
        private var last = -1
        fun set(percent: Long) {
            val p = percent.coerceIn(0, 100).toInt()
            if (p > last) { last = p; progress?.update(p) }
        }
    }

    private const val BLOCK_SIZE = 64L * 1024
    private const val CHUNK = 256 * 1024
    private const val ARCHIVE_EOF = 1
}
