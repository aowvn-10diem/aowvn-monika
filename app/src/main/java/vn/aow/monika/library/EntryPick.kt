package vn.aow.monika.library

import java.io.File
import java.io.RandomAccessFile
import vn.aow.monika.config.SystemDef

/**
 * Chọn lối vào game trong một thư mục có nhiều file cùng đuôi (V26: Kara no Shoujo có karanoshojo.xp3 + patch.xp3…patch5.xp3,
 * trước đây lấy file đầu tiên = một bản vá → Kirikiri báo "Cannot find storage startup.tjs"). Mọi quy tắc nằm trong config của hệ máy.
 */
object EntryPick {
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

    /** exe là game Kirikiri nếu có file .xp3 cùng thư mục, hoặc có XP3 gắn trong chính exe (đọc overlay PE + 4 MB đầu, không quét cả file). */
    fun isXp3Exe(file: File): Boolean = runCatching {
        file.parentFile?.listFiles()?.any { it.isFile && it.extension.equals("xp3", true) } == true || hasEmbeddedXp3(file)
    }.getOrDefault(false)

    // Chữ ký đầy đủ của file XP3: "XP3\r\n \n\x1a\x8bg\x01" (11 byte).
    private val XP3_SIG = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
    private const val SNIFF_HEAD = 4L * 1024 * 1024

    /**
     * exe Kirikiri có XP3 nối sau phần PE ("overlay"): đọc bảng section lấy điểm kết thúc của PE, kiểm chữ ký tại đó (rẻ, vài KB).
     * Phòng khi tool đóng gói đặt kho ở chỗ khác thì quét thêm 4 MB đầu. Không đọc cả file nên quét thư mục nhiều exe vẫn nhanh.
     */
    private fun hasEmbeddedXp3(file: File): Boolean = RandomAccessFile(file, "r").use { raf ->
        val len = raf.length()
        fun sigAt(off: Long): Boolean {
            if (off < 0 || off + XP3_SIG.size > len) return false
            val b = ByteArray(XP3_SIG.size); raf.seek(off); raf.readFully(b)
            return b.contentEquals(XP3_SIG)
        }
        peOverlayOffset(raf, len)?.let { if (sigAt(it)) return@use true }
        val n = minOf(len, SNIFF_HEAD).toInt()
        val buf = ByteArray(n); raf.seek(0); raf.readFully(buf)
        for (i in 0..n - XP3_SIG.size) {
            var k = 0
            while (k < XP3_SIG.size && buf[i + k] == XP3_SIG[k]) k++
            if (k == XP3_SIG.size) return@use true
        }
        false
    }

    private fun peOverlayOffset(raf: RandomAccessFile, len: Long): Long? = runCatching {
        fun u16(o: Long): Int { raf.seek(o); return raf.read() or (raf.read() shl 8) }
        fun u32(o: Long): Long { raf.seek(o); return (raf.read().toLong()) or (raf.read().toLong() shl 8) or (raf.read().toLong() shl 16) or (raf.read().toLong() shl 24) }
        if (len < 0x40 || u16(0) != 0x5A4D) return null           // "MZ"
        val pe = u32(0x3C)
        if (pe <= 0 || pe + 24 > len || u32(pe) != 0x00004550L) return null // "PE\0\0"
        val nSections = u16(pe + 6)
        val optSize = u16(pe + 20)
        var end = 0L
        for (i in 0 until nSections) {
            val s = pe + 24 + optSize + 40L * i
            val rawSize = u32(s + 16); val rawPtr = u32(s + 20)
            end = maxOf(end, rawPtr + rawSize)
        }
        end.takeIf { it in 1 until len }
    }.getOrNull()

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
            // pairedexe: như paired nhưng trả về .exe đi kèm kho chính (nếu có) — để đổi cách mở chỉ bằng config khi cần thử hướng khác.
            "pairedexe" -> {
                val xp3 = pick(ok, system.copy(entryPick = "paired"))
                val exe = xp3?.let { x -> ok.firstOrNull { it.extension.equals("exe", true) && it.nameWithoutExtension.equals(x.nameWithoutExtension, true) } }
                exe ?: xp3
            }
            else -> ok.first()
        }
    }
}
