package vn.aow.monika.library

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.MultiReadOnlySeekableByteChannel
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Giải nén 7z có mật khẩu (AES-256) — libarchive không hỗ trợ. Dùng Apache Commons Compress (thuần Java).
 * Chậm hơn libarchive nên chỉ dùng khi libarchive báo lỗi với file .7z.
 */
object SevenZipExtractor {

    fun extract(parts: List<File>, target: File, passwords: List<String>, progress: ExtractProgress? = null) {
        // Thử không mật khẩu trước (file 7z thường mà libarchive đọc lỗi), rồi lần lượt từng mật khẩu.
        val candidates = listOf<String?>(null) + passwords.filter { it.isNotEmpty() }.distinct()
        var last: IOException? = null
        for (password in candidates) {
            try {
                extractOnce(parts, target, password, progress)
                return
            } catch (e: IOException) {
                last = e
                target.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
        throw if (candidates.size == 1 || last is org.apache.commons.compress.PasswordRequiredException) {
            ArchiveExtractor.PasswordException("File 7z có mật khẩu. Hãy nhập đúng mật khẩu (thường ghi trong bài viết).")
        } else {
            ArchiveExtractor.PasswordException("Sai mật khẩu file 7z. Hãy nhập đúng mật khẩu (thường ghi trong bài viết).")
        }
    }

    private fun extractOnce(parts: List<File>, target: File, password: String?, progress: ExtractProgress?) {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val channel = if (parts.size == 1) FileChannel.open(parts[0].toPath(), StandardOpenOption.READ)
        else MultiReadOnlySeekableByteChannel.forFiles(*parts.toTypedArray())
        val builder = SevenZFile.builder().setSeekableByteChannel(channel)
        if (password != null) builder.setPassword(password.toCharArray())
        builder.get().use { zip ->
            val total = zip.entries.sumOf { if (it.isDirectory) 0L else it.size }.coerceAtLeast(1)
            val tracker = ArchiveExtractor.Tracker(progress)
            var done = 0L
            val buf = ByteArray(256 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(target, entry.name).canonicalFile
                if (!out.path.startsWith(root)) throw IOException("File nén không hợp lệ: ${entry.name}")
                if (entry.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { os ->
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        tracker.set(done * 100 / total)
                    }
                }
            }
            tracker.set(100)
        }
    }
}
