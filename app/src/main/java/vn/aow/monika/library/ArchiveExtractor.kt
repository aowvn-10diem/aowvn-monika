package vn.aow.monika.library

import java.io.File
import java.io.IOException

/** Báo tiến độ giải nén 0..100 (chỉ gọi khi % đổi). */
fun interface ExtractProgress {
    fun update(percent: Int)
}

/**
 * Giải nén zip / rar (cả RAR5) / 7z. Không còn libarchive (tiết kiệm ~2 MB APK):
 * - .zip → zip4j (Java thuần; ZipCrypto + AES, mật khẩu thử lần lượt).
 * - rar / 7z / zip lạ → 7-Zip native (gói tải khi cần, xem SevenZipNative) → lỗi thì 7z có bộ giải thuần Java.
 */
object ArchiveExtractor {
    val EXTENSIONS = setOf("zip", "rar", "7z")

    class PasswordException(message: String) : IOException(message)

    fun isArchive(file: File) = file.extension.lowercase() in EXTENSIONS || MultiPart.parse(file.name) != null

    fun extract(source: File, target: File, passwords: List<String>, progress: ExtractProgress? = null) =
        extractParts(listOf(source), target, passwords, progress)

    /** 1 file, hoặc file nén chia nhiều phần (part1.rar, part2.rar… / .7z.001, .002…) theo đúng thứ tự. */
    fun extractParts(parts: List<File>, target: File, passwords: List<String>, progress: ExtractProgress? = null) {
        target.mkdirs()
        // zip thường: bộ giải Java nhanh, không cần tải thêm gì.
        if (parts.size == 1 && parts[0].extension.equals("zip", ignoreCase = true)) {
            val e = runCatching { ZipExtractor.extract(parts[0], target, passwords, progress) }.exceptionOrNull() ?: return
            target.listFiles()?.forEach { it.deleteRecursively() }
            if (e is PasswordException) throw e
            // zip lạ mà zip4j không đọc được (nén kiểu hiếm, hỏng nhẹ…) → thử 7-Zip.
        }
        val e7 = runCatching { SevenZipNative.extract(parts, target, passwords, progress) }.exceptionOrNull() ?: return
        target.listFiles()?.forEach { it.deleteRecursively() }
        if (e7 is PasswordException) throw e7
        // 7-Zip native không nạp/tải được (máy lạ, mất mạng) và là 7z → bộ giải 7z thuần Java.
        if (e7 is UnsatisfiedLinkError || e7 is net.sf.sevenzipjbinding.SevenZipNativeInitializationException) {
            if (is7z(parts.first())) {
                try {
                    SevenZipExtractor.extract(parts, target, passwords, progress)
                    return
                } catch (e3: IOException) {
                    target.listFiles()?.forEach { it.deleteRecursively() }
                    if (e3 is PasswordException) throw e3
                }
            }
            throw IOException("Chưa giải nén được: cần tải bộ giải nén (kiểm tra mạng) rồi thử lại.", e7)
        }
        throw if (e7 is IOException) e7 else IOException("Không giải nén được: ${e7.message}", e7)
    }

    fun is7z(file: File): Boolean {
        val n = file.name.lowercase()
        return n.endsWith(".7z") || Regex("""\.7z\.\d{3}$""").containsMatchIn(n)
    }

    /** Gọi [ExtractProgress] chỉ khi % tăng, tránh cập nhật thông báo quá dày. */
    internal class Tracker(private val progress: ExtractProgress?) {
        private var last = -1
        fun set(percent: Long) {
            val p = percent.coerceIn(0, 100).toInt()
            if (p > last) { last = p; progress?.update(p) }
        }
    }

}
