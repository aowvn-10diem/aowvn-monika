package vn.aow.monika.library

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File
import java.io.IOException

/** Giải nén .zip bằng zip4j (Java thuần): ZipCrypto + AES-256, tên file tiếng Việt, chặn đường dẫn thoát khỏi thư mục đích. */
object ZipExtractor {
    fun extract(source: File, target: File, passwords: List<String>, progress: ExtractProgress? = null) {
        val root = target.canonicalPath + File.separator
        val candidates = listOf<String?>(null) + passwords.filter { it.isNotEmpty() }.distinct()
        var sawPassword = false
        for (pw in candidates) {
            val zip = ZipFile(source, pw?.toCharArray())
            // Tên file: ưu tiên UTF-8; nếu giải mã ra ký tự lỗi (zip tạo trên Windows tiếng Việt, không có cờ UTF-8) → Windows-1258.
            zip.charset = Charsets.UTF_8
            if (zip.fileHeaders.any { '\uFFFD' in it.fileName }) {
                zip.charset = runCatching { java.nio.charset.Charset.forName("windows-1258") }.getOrDefault(Charsets.ISO_8859_1)
            }
            try {
                val headers = zip.fileHeaders
                if (pw == null && headers.any { it.isEncrypted }) { sawPassword = true; continue }
                val total = headers.filter { !it.isDirectory }.sumOf { it.uncompressedSize }.coerceAtLeast(1)
                val tracker = ArchiveExtractor.Tracker(progress)
                var done = 0L
                for (h in headers) {
                    val out = File(target, h.fileName).canonicalFile
                    if (!out.path.startsWith(root) && out.path + File.separator != root) throw IOException("File nén không hợp lệ: ${h.fileName}")
                    if (h.isDirectory) { out.mkdirs(); continue }
                    zip.extractFile(h, target.absolutePath)
                    done += h.uncompressedSize
                    tracker.set(done * 100 / total)
                }
                tracker.set(100)
                return
            } catch (e: ZipException) {
                if (e.type == ZipException.Type.WRONG_PASSWORD || e.message.orEmpty().contains("password", ignoreCase = true)) {
                    sawPassword = true
                    target.listFiles()?.forEach { it.deleteRecursively() }
                    continue
                }
                throw IOException("Không giải nén được zip: ${e.message}", e)
            }
        }
        throw ArchiveExtractor.PasswordException(
            if (sawPassword && candidates.size > 1) "Sai mật khẩu file nén. Hãy nhập đúng mật khẩu (thường ghi cuối bài viết)."
            else "File nén có mật khẩu. Hãy nhập mật khẩu (thường ghi cuối bài viết)."
        )
    }
}
