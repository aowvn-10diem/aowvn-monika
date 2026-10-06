package vn.aow.monika.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Đọc CHỈ MỤC của kho XP3 (Kirikiri) để biết kho có những tên mục nào, mà không đọc cả file (kho game có thể hàng trăm MB).
 * Chỉ seek tới chỉ mục; không đọc nội dung game. V26: Kirikiri báo "Cannot find storage startup.tjs" khi kho truyền vào
 * không có `startup.tjs` ở gốc, nên app cần tự kiểm trước khi gọi engine (xem docs/opus/ket-qua/V26.md).
 *
 * Định dạng (tóm tắt): "XP3\r\n \n\x1a\x8bg\x01" + u64 vị trí chỉ mục (v2: =0x17, vị trí thật ở u64 tại 0x20);
 * chỉ mục: cờ (0 = thô, 1 = zlib) rồi [u64 kích thước] hoặc [u64 nén, u64 gốc] + dữ liệu; dữ liệu là chuỗi chunk
 * "File"(u64 kích thước) chứa chunk con "info" (u32 cờ, u64 gốc, u64 nén, u16 độ dài tên, tên UTF-16LE).
 */
object Xp3Index {
    sealed class Result {
        /** [names] giữ nguyên hoa/thường, dùng '/' làm dấu phân cách; [complete] = false nếu bị cắt bởi giới hạn. */
        class Names(val names: List<String>, val complete: Boolean) : Result()
        /** Không đọc được (không phải XP3, định dạng lạ, hỏng, quá lớn…). [reason] là mã ngắn, không có nội dung game. */
        class Unreadable(val reason: String) : Result()
    }

    private val MAGIC = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
    /** Trần cho chỉ mục sau giải nén (và trước giải nén): chặn file lạ/hỏng làm tràn bộ nhớ. */
    const val MAX_INDEX_BYTES = 64L * 1024 * 1024
    private const val MAX_NAMES = 200_000

    fun read(file: File): Result = try {
        RandomAccessFile(file, "r").use { raf -> readIndex(raf) }
    } catch (e: Exception) {
        Result.Unreadable("lỗi đọc: ${e.javaClass.simpleName}")
    }

    /** true/false nếu đọc được chỉ mục; null nếu không biết (không đọc được chỉ mục). */
    fun hasRootStartup(file: File): Boolean? = (read(file) as? Result.Names)?.let { hasRootStartup(it.names) }

    fun hasRootStartup(names: List<String>): Boolean = names.any { it.equals("startup.tjs", ignoreCase = true) }

    private fun readIndex(raf: RandomAccessFile): Result {
        val size = raf.length()
        if (size < MAGIC.size + 8) return Result.Unreadable("quá ngắn")
        val head = ByteArray(0x28.coerceAtMost(size.toInt()))
        raf.seek(0); raf.readFully(head)
        if (!head.copyOf(MAGIC.size).contentEquals(MAGIC)) return Result.Unreadable("không phải XP3")
        val b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        var indexPos = b.getLong(MAGIC.size)
        if (indexPos == 0x17L) { // XP3 v2: vị trí thật nằm sau khối phụ (cờ 0x80, u64 kích thước bảng, u64 vị trí)
            if (head.size < 0x28) return Result.Unreadable("v2 hỏng")
            if ((head[0x17].toInt() and 0xFF) != 0x80) return Result.Unreadable("v2 lạ")
            indexPos = b.getLong(0x20)
        }
        if (indexPos <= 0 || indexPos >= size) return Result.Unreadable("vị trí chỉ mục ngoài file")
        raf.seek(indexPos)
        val flag = raf.readUnsignedByte() and 0x07
        val data: ByteArray = when (flag) {
            0 -> {
                val len = readLong(raf)
                if (len < 0 || len > MAX_INDEX_BYTES || indexPos + 9 + len > size) return Result.Unreadable("chỉ mục thô quá lớn/hỏng")
                ByteArray(len.toInt()).also { raf.readFully(it) }
            }
            1 -> {
                val packed = readLong(raf)
                val raw = readLong(raf)
                if (packed < 0 || raw < 0 || packed > MAX_INDEX_BYTES || raw > MAX_INDEX_BYTES || indexPos + 17 + packed > size) {
                    return Result.Unreadable("chỉ mục nén quá lớn/hỏng")
                }
                val z = ByteArray(packed.toInt()).also { raf.readFully(it) }
                inflate(z, raw.toInt()) ?: return Result.Unreadable("giải nén chỉ mục lỗi")
            }
            else -> return Result.Unreadable("cờ chỉ mục lạ")
        }
        return parseEntries(data)
    }

    private fun readLong(raf: RandomAccessFile): Long {
        val a = ByteArray(8); raf.readFully(a)
        return ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun inflate(z: ByteArray, rawSize: Int): ByteArray? {
        val inf = Inflater()
        return try {
            inf.setInput(z)
            val out = ByteArray(rawSize)
            var n = 0
            while (n < rawSize && !inf.finished()) {
                val k = inf.inflate(out, n, rawSize - n)
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                n += k
            }
            if (n == rawSize) out else null
        } catch (e: java.util.zip.DataFormatException) {
            null
        } finally {
            inf.end()
        }
    }

    private fun parseEntries(data: ByteArray): Result {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val names = ArrayList<String>()
        var pos = 0
        var complete = true
        while (pos + 12 <= data.size) {
            val tag = String(data, pos, 4, Charsets.ISO_8859_1)
            val len = b.getLong(pos + 4)
            val start = pos + 12
            if (len < 0 || start + len > data.size) { complete = false; break }
            val end = (start + len).toInt()
            if (tag == "File") {
                var p = start
                while (p + 12 <= end) {
                    val sub = String(data, p, 4, Charsets.ISO_8859_1)
                    val slen = b.getLong(p + 4)
                    val s = p + 12
                    if (slen < 0 || s + slen > end) break
                    if (sub == "info" && slen >= 22) {
                        val chars = b.getShort(s + 20).toInt() and 0xFFFF
                        if (s + 22 + chars * 2 <= s + slen) {
                            names.add(String(data, s + 22, chars * 2, Charsets.UTF_16LE).replace('\\', '/'))
                        }
                    }
                    p = (s + slen).toInt()
                }
                if (names.size >= MAX_NAMES) { complete = false; break }
            }
            pos = end
        }
        return Result.Names(names, complete)
    }
}
