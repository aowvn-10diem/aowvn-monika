package vn.aow.monika.library

import android.os.ParcelFileDescriptor
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Giải nén zip / rar (cả RAR5) / 7z bằng libarchive (thư viện C, chạy native nên nhanh).
 * Mật khẩu: libarchive hỗ trợ zip và rar có mật khẩu; 7z có mật khẩu chưa hỗ trợ.
 */
object ArchiveExtractor {
    val EXTENSIONS = setOf("zip", "rar", "7z")

    class PasswordException(message: String) : IOException(message)

    fun isArchive(file: File) = file.extension.lowercase() in EXTENSIONS

    fun extract(source: ParcelFileDescriptor, target: File, passwords: List<String>) {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val archive = Archive.readNew()
        try {
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            passwords.filter { it.isNotEmpty() }.forEach { Archive.readAddPassphrase(archive, it.toByteArray()) }
            Archive.readOpenFd(archive, source.fd, BLOCK_SIZE)
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
                        ParcelFileDescriptor.open(
                            out,
                            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                        ).use { Archive.readDataIntoFd(archive, it.fd) }
                    }
                    else -> Archive.readDataSkip(archive) // Symlink, file đặc biệt: bỏ qua.
                }
            }
        } catch (e: ArchiveException) {
            throw translate(e)
        } finally {
            runCatching { Archive.readFree(archive) }
        }
    }

    /** Tên file trong file nén: ưu tiên UTF-8, không phải thì thử bảng mã Windows tiếng Việt. */
    private fun entryName(entry: Long): String {
        ArchiveEntry.pathnameUtf8(entry)?.let { return it }
        val bytes = ArchiveEntry.pathname(entry) ?: return ""
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, runCatching { Charset.forName("windows-1258") }.getOrDefault(Charsets.ISO_8859_1))
        }
    }

    private fun translate(e: ArchiveException): IOException {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("passphrase", ignoreCase = true) ->
                PasswordException("File nén có mật khẩu. Hãy nhập đúng mật khẩu (thường ghi trong bài viết).")
            msg.contains("ncrypt", ignoreCase = true) ->
                PasswordException("File nén dùng kiểu mã hóa chưa hỗ trợ. Hãy giải nén bằng ZArchiver rồi thêm lại.")
            msg.contains("multivolume", ignoreCase = true) || msg.contains("volume", ignoreCase = true) ->
                IOException("File nén chia nhiều phần (part1, part2...) chưa hỗ trợ. Hãy giải nén bằng ZArchiver.")
            else -> IOException("Không giải nén được: $msg", e)
        }
    }

    private const val BLOCK_SIZE = 64L * 1024
    private const val ARCHIVE_EOF = 1
}
