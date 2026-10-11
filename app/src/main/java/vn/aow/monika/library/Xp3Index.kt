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
        /** Offset tuyệt đối chữ ký XP3; null khi không tìm thấy chữ ký. */
        abstract val archiveOffset: Long?
        /** [names] giữ nguyên hoa/thường, dùng '/' làm dấu phân cách; [complete] = false nếu bị cắt bởi giới hạn. */
        class Names(val names: List<String>, val complete: Boolean, override val archiveOffset: Long = 0) : Result()
        /** Không đọc được (không phải XP3, định dạng lạ, hỏng, quá lớn…). [reason] là mã ngắn, không có nội dung game. */
        class Unreadable(val reason: String, override val archiveOffset: Long? = null) : Result()
    }

    private val MAGIC = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
    /** Trần cho chỉ mục sau giải nén (và trước giải nén): chặn file lạ/hỏng làm tràn bộ nhớ. */
    const val MAX_INDEX_BYTES = 64L * 1024 * 1024
    private const val MAX_NAMES = 200_000

    fun read(file: File): Result {
        var offset: Long? = null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                offset = archiveOffset(raf)
                val base = offset ?: return@use Result.Unreadable("không có chữ ký XP3")
                when (val result = readIndex(raf, base)) {
                    is Result.Names -> Result.Names(result.names, result.complete, base)
                    is Result.Unreadable -> Result.Unreadable(result.reason, base)
                }
            }
        } catch (e: Exception) {
            Result.Unreadable("lỗi đọc: ${e.javaClass.simpleName}", offset)
        }
    }

    /** Quét MZ theo khối nhỏ, có overlap; ưu tiên chữ ký căn 16 byte giống engine hiện tại.
     * Chữ ký lệch căn chỉ để chẩn đoán, resolver không truyền nó cho engine.
     */
    private fun archiveOffset(raf: RandomAccessFile): Long? {
        if (raf.length() < MAGIC.size) return null
        val head = ByteArray(MAGIC.size); raf.seek(0); raf.readFully(head)
        if (head.contentEquals(MAGIC)) return 0
        if (head[0] != 0x4d.toByte() || head[1] != 0x5a.toByte()) return null
        val buffer = ByteArray(64 * 1024 + MAGIC.size - 1)
        var position = 16L
        var unaligned: Long? = null
        while (position < raf.length()) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val count = minOf(buffer.size.toLong(), raf.length() - position).toInt()
            raf.seek(position); raf.readFully(buffer, 0, count)
            for (i in 0..count - MAGIC.size) {
                if (buffer[i] != MAGIC[0]) continue
                var k = 1
                while (k < MAGIC.size && buffer[i + k] == MAGIC[k]) k++
                if (k == MAGIC.size) {
                    val found = position + i
                    if (found % 16 == 0L) return found
                    if (unaligned == null) unaligned = found
                }
            }
            position += 64 * 1024
        }
        return unaligned
    }

    /** Chỉ trả false khi đọc hết chỉ mục; phần chưa đọc/hỏng không chứng minh thiếu startup. */
    fun hasRootStartup(file: File): Boolean? = hasRootStartup(read(file))

    internal fun hasRootStartup(result: Result?): Boolean? = (result as? Result.Names)?.let {
        if (hasRootStartup(it.names)) true else if (it.complete) false else null
    }

    fun hasRootStartup(names: List<String>): Boolean = names.any { it.equals("startup.tjs", ignoreCase = true) }

    private fun readIndex(raf: RandomAccessFile, archiveOffset: Long): Result {
        val size = raf.length()
        if (size - archiveOffset < MAGIC.size + 8) return Result.Unreadable("quá ngắn")
        val head = ByteArray(minOf(0x28L, size - archiveOffset).toInt())
        raf.seek(archiveOffset); raf.readFully(head)
        if (!head.copyOf(MAGIC.size).contentEquals(MAGIC)) return Result.Unreadable("không phải XP3")
        val b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        var indexPos = b.getLong(MAGIC.size)
        if (indexPos == 0x17L) { // XP3 v2: vị trí thật nằm sau khối phụ (cờ 0x80, u64 kích thước bảng, u64 vị trí)
            if (head.size < 0x28) return Result.Unreadable("v2 hỏng")
            if ((head[0x17].toInt() and 0xFF) != 0x80) return Result.Unreadable("v2 lạ")
            indexPos = b.getLong(0x20)
        }
        if (indexPos <= 0 || indexPos >= size - archiveOffset) return Result.Unreadable("vị trí chỉ mục ngoài file")
        indexPos += archiveOffset
        raf.seek(indexPos)
        val indexFlag = raf.readUnsignedByte()
        val flag = indexFlag and 0x07
        val data: ByteArray = when (flag) {
            0 -> {
                val len = readLong(raf)
                if (len < 0 || len > MAX_INDEX_BYTES || len > size - indexPos - 9) return Result.Unreadable("chỉ mục thô quá lớn/hỏng")
                ByteArray(len.toInt()).also { raf.readFully(it) }
            }
            1 -> {
                val packed = readLong(raf)
                val raw = readLong(raf)
                if (packed < 0 || raw < 0 || packed > MAX_INDEX_BYTES || raw > MAX_INDEX_BYTES || packed > size - indexPos - 17) {
                    return Result.Unreadable("chỉ mục nén quá lớn/hỏng")
                }
                val z = ByteArray(packed.toInt()).also { raf.readFully(it) }
                inflate(z, raw.toInt()) ?: return Result.Unreadable("giải nén chỉ mục lỗi")
            }
            else -> return Result.Unreadable("cờ chỉ mục lạ")
        }
        val entries = parseEntries(data)
        // Có khối chỉ mục tiếp theo chưa đọc: không được kết luận không có startup.
        return if (entries is Result.Names && indexFlag and 0x80 != 0)
            Result.Names(entries.names, false) else entries
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
            if (len < 0 || len > data.size - start) { complete = false; break }
            val end = (start + len).toInt()
            if (tag == "File") {
                var p = start
                var hasInfo = false
                while (p + 12 <= end) {
                    val sub = String(data, p, 4, Charsets.ISO_8859_1)
                    val slen = b.getLong(p + 4)
                    val s = p + 12
                    if (slen < 0 || slen > end - s) { complete = false; break }
                    if (sub == "info") {
                        if (slen < 22) complete = false
                        else {
                            val chars = b.getShort(s + 20).toInt() and 0xFFFF
                            if (22 + chars * 2 <= slen) {
                                names.add(String(data, s + 22, chars * 2, Charsets.UTF_16LE).replace('\\', '/'))
                                hasInfo = true
                            } else complete = false
                        }
                    }
                    p = (s + slen).toInt()
                }
                // File phải có info hợp lệ; đuôi subchunk bị cắt không chứng minh thiếu startup.
                if (p != end || !hasInfo) complete = false
                if (names.size >= MAX_NAMES) { complete = false; break }
            }
            pos = end
        }
        if (pos != data.size) complete = false
        return Result.Names(names, complete)
    }
}
