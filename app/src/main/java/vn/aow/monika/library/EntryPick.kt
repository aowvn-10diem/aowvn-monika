package vn.aow.monika.library

import java.io.File
import java.io.RandomAccessFile
import vn.aow.monika.config.SystemDef

/**
 * Chọn lối vào game trong một thư mục có nhiều file cùng đuôi (V26: Kara no Shoujo có karanoshojo.xp3 + patch.xp3…patch5.xp3,
 * trước đây lấy file đầu tiên = một bản vá → Kirikiri báo "Cannot find storage startup.tjs"). Mọi quy tắc nằm trong config của hệ máy.
 */
object EntryPick {
    private val XP3_MAGIC = byteArrayOf(0x58, 0x50, 0x33, 0x0d)  // "XP3\r"

    fun excluded(file: File, system: SystemDef): Boolean =
        system.entryExclude.any { runCatching { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(file.name) }.getOrDefault(false) }

    /** File có đuôi hợp lệ cho [system] (kể cả đuôi cần kiểm loại thật) và không nằm trong danh sách loại trừ. */
    fun acceptable(file: File, system: SystemDef): Boolean {
        if (excluded(file, system)) return false
        val ext = file.extension.lowercase()
        if (system.extensions.any { it.equals(ext, true) }) return RomSniff.accepts(file, ext)
        if (system.extensionsSniffed.any { it.equals(ext, true) }) return isXp3Exe(file)
        return false
    }

    /** exe là game Kirikiri nếu có file .xp3 cùng thư mục, hoặc có XP3 gắn trong chính exe (quét tối đa [SNIFF_MAX] byte). */
    fun isXp3Exe(file: File): Boolean = runCatching {
        file.parentFile?.listFiles()?.any { it.isFile && it.extension.equals("xp3", true) } == true || hasEmbeddedXp3(file)
    }.getOrDefault(false)

    private const val SNIFF_MAX = 96L * 1024 * 1024

    private fun hasEmbeddedXp3(file: File): Boolean = RandomAccessFile(file, "r").use { raf ->
        val buf = ByteArray(1 shl 16)
        var pos = 0L
        val limit = minOf(raf.length(), SNIFF_MAX)
        var carry = 0
        while (pos < limit) {
            val n = raf.read(buf, carry, buf.size - carry)
            if (n <= 0) break
            val total = carry + n
            for (i in 0..total - XP3_MAGIC.size) {
                if (buf[i] == XP3_MAGIC[0] && buf[i + 1] == XP3_MAGIC[1] && buf[i + 2] == XP3_MAGIC[2] && buf[i + 3] == XP3_MAGIC[3]) return@use true
            }
            carry = minOf(XP3_MAGIC.size - 1, total)
            System.arraycopy(buf, total - carry, buf, 0, carry)
            pos += n
        }
        false
    }

    /** Chọn một lối vào trong [candidates] (cùng hệ máy, thường cùng thư mục). Trả null nếu không còn ứng viên sau khi loại trừ. */
    fun pick(candidates: List<File>, system: SystemDef): File? {
        val ok = candidates.filter { !excluded(it, system) }
        if (ok.isEmpty()) return null
        return when (system.entryPick.lowercase()) {
            "largest" -> ok.maxByOrNull { it.length() }
            "paired" -> {
                val pairBases = ok.filter { f -> system.entryPairExt.any { it.equals(f.extension, true) } }.map { it.nameWithoutExtension.lowercase() }.toSet()
                val primary = ok.filter { f -> system.extensions.any { it.equals(f.extension, true) } }.ifEmpty { ok }
                primary.filter { it.nameWithoutExtension.lowercase() in pairBases }.maxByOrNull { it.length() }
                    ?: primary.maxByOrNull { it.length() }
            }
            else -> ok.first()
        }
    }
}
