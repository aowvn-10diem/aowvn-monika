package vn.aow.monika.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.Inflater

/** Đọc metadata có giới hạn; không nạp lõi, giải mã hay sao chép ROM. */
internal object ConsoleSaveFormats {
    private fun check(ok: Boolean, message: String) = SaveFormats.checked(ok, message)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL

    fun ps1(bytes: ByteArray): ByteArray {
        val gme = bytes.size >= 11 && bytes.copyOfRange(0, 11).toString(Charsets.US_ASCII) == "123-456-STD"
        val raw = if (gme) {
            check(bytes.size == 131072 + 3904, "Thẻ DexDrive GME không đúng dung lượng.")
            bytes.copyOfRange(3904, bytes.size)
        } else bytes.copyOf()
        check(raw.size == 131072 && raw[0] == 'M'.code.toByte() && raw[1] == 'C'.code.toByte(),
            "Thẻ PS1 phải là MCR/MCD/SRM 128 KiB hoặc GME có header DexDrive.")
        check(raw.take(127).fold(0) { sum, b -> sum xor (b.toInt() and 255) } == (raw[127].toInt() and 255),
            "Header thẻ PS1 bị hỏng.")
        return raw
    }

    fun title3ds(file: File): String = RandomAccessFile(file, "r").use { input ->
        fun read(offset: Long, size: Int): ByteArray {
            check(offset >= 0 && offset + size <= input.length(), "Header game 3DS bị thiếu.")
            input.seek(offset); return ByteArray(size).also { input.readFully(it) }
        }
        val top = read(0, 512)
        val magic = top.copyOfRange(256, 260).toString(Charsets.US_ASCII)
        val ncch = when (magic) {
            "NCCH" -> top
            "NCSD" -> read(u32(top, 0x120) * 512, 512)
            else -> throw SaveTransferException("Chưa đọc được title ID từ CIA/3DSX này. Chọn bản NCSD/NCCH đã kiểm; không đoán thư mục save.")
        }
        check(ncch.copyOfRange(256, 260).toString(Charsets.US_ASCII) == "NCCH", "Phân vùng 3DS không phải NCCH đã kiểm.")
        val id = ByteBuffer.wrap(ncch, 0x118, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val text = "%016x".format(Locale.ROOT, id)
        check(text.startsWith("00040000") && id != 0L, "Title ID không phải game 3DS được hỗ trợ.")
        text
    }

    fun sfo(bytes: ByteArray, key: String): String {
        check(bytes.size in 20..65536 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0, 80, 83, 70)), "PARAM.SFO không hợp lệ.")
        check(u32(bytes, 4) == 0x101L, "Phiên bản PARAM.SFO chưa được hỗ trợ.")
        val keys = u32(bytes, 8); val data = u32(bytes, 12); val count = u32(bytes, 16)
        check(count in 1..128 && 20 + count * 16 <= keys && keys <= data && data <= bytes.size, "Bảng PARAM.SFO bị hỏng.")
        var result: String? = null
        repeat(count.toInt()) { i ->
            val at = 20 + i * 16
            val k = keys + ((bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8))
            check(k in keys until data, "Khóa PARAM.SFO bị hỏng.")
            val end = (k.toInt() until data.toInt()).firstOrNull { bytes[it] == 0.toByte() }
                ?: throw SaveTransferException("Khóa PARAM.SFO thiếu kết thúc.")
            if (bytes.copyOfRange(k.toInt(), end).toString(Charsets.US_ASCII) == key) {
                val size = u32(bytes, at + 4); val max = u32(bytes, at + 8); val offset = data + u32(bytes, at + 12)
                check(result == null && bytes[at + 2] == 4.toByte() && bytes[at + 3] == 2.toByte() &&
                    size in 1..128 && size <= max && offset >= data && offset + max <= bytes.size, "Giá trị PARAM.SFO bị hỏng hoặc trùng.")
                val value = bytes.copyOfRange(offset.toInt(), (offset + size).toInt())
                check(value.last() == 0.toByte() && value.dropLast(1).none { it == 0.toByte() }, "Chuỗi PARAM.SFO không hợp lệ.")
                result = value.dropLast(1).toByteArray().toString(Charsets.US_ASCII)
            }
        }
        return result ?: throw SaveTransferException("PARAM.SFO thiếu mã game/thư mục save.")
    }

    fun pspDisc(file: File): String = RandomAccessFile(file, "r").use { input ->
        var budget = 2 * 1024 * 1024
        fun read(offset: Long, size: Int): ByteArray {
            check(size in 1..65536 && offset >= 0 && offset + size <= input.length() && size <= budget,
                "Metadata ISO/CSO thiếu hoặc vượt giới hạn.")
            budget -= size; input.seek(offset); return ByteArray(size).also { input.readFully(it) }
        }
        val header = read(0, 24)
        val compressed = header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "CISO"
        val total = if (compressed) ByteBuffer.wrap(header, 8, 8).order(ByteOrder.LITTLE_ENDIAN).long else input.length()
        val align = header[21].toInt() and 255
        if (compressed) check(header[20] == 1.toByte() && u32(header, 16) == 2048L && align <= 12 &&
            total in 32768..(4L * 1024 * 1024 * 1024) && total % 2048 == 0L && 24L + (total / 2048 + 1) * 4 <= input.length(),
            "Chỉ đọc metadata CSO v1, block 2048 đã kiểm; ZSO/CSO khác chưa hỗ trợ.")
        fun block(sector: Long): ByteArray {
            check(sector >= 0 && (sector + 1) * 2048 <= total, "Sector ISO không hợp lệ.")
            if (!compressed) return read(sector * 2048, 2048)
            val index = read(24 + sector * 4, 8)
            val first = u32(index, 0); val next = u32(index, 4)
            val offset = (first and 0x7fffffffL) shl align
            val finish = (next and 0x7fffffffL) shl align
            check(offset >= 24 + (total / 2048 + 1) * 4 && finish > offset && finish <= input.length(), "Chỉ mục CSO không hợp lệ.")
            if (first and 0x80000000L != 0L) { check(finish - offset >= 2048, "Block CSO bị thiếu."); return read(offset, 2048) }
            val data = read(offset, (finish - offset).also { check(it <= 65536, "Block CSO quá lớn.") }.toInt())
            val inflater = Inflater(true)
            try {
                inflater.setInput(data); val output = ByteArray(2048)
                check(inflater.inflate(output) == 2048 && inflater.finished(), "Block CSO bị hỏng.")
                return output
            } finally { inflater.end() }
        }
        fun extent(sector: Long, size: Int): ByteArray {
            check(size in 1..65536, "Metadata ISO quá lớn.")
            return ByteArray(size).also { out ->
                var copied = 0
                while (copied < size) { val b = block(sector + copied / 2048); val n = minOf(2048, size - copied); b.copyInto(out, copied, 0, n); copied += n }
            }
        }
        val pvd = block(16)
        check(pvd[0] == 1.toByte() && pvd.copyOfRange(1, 6).toString(Charsets.US_ASCII) == "CD001" && pvd[6] == 1.toByte(), "Game PSP không có ISO9660 đã kiểm.")
        fun find(dir: ByteArray, wanted: String, directory: Boolean): ByteArray {
            var at = 0
            while (at < dir.size) {
                val len = dir[at].toInt() and 255
                if (len == 0) { at = (at / 2048 + 1) * 2048; continue }
                check(len >= 34 && at + len <= dir.size, "Thư mục ISO bị hỏng.")
                val nameSize = dir[at + 32].toInt() and 255
                check(33 + nameSize <= len, "Tên trong ISO bị hỏng.")
                val name = dir.copyOfRange(at + 33, at + 33 + nameSize).toString(Charsets.US_ASCII).substringBefore(';')
                if (name == wanted) {
                    val flags = dir[at + 25].toInt() and 255
                    check((flags and 2 != 0) == directory && flags and 128 == 0, "Kiểu mục ISO chưa được hỗ trợ.")
                    val size = u32(dir, at + 10); check(size in 1..65536, "Mục metadata ISO quá lớn.")
                    return extent(u32(dir, at + 2), size.toInt())
                }
                at += len
            }
            throw SaveTransferException("ISO PSP thiếu PSP_GAME/PARAM.SFO.")
        }
        val rootSize = u32(pvd, 166); check(rootSize in 1..65536, "Thư mục gốc ISO quá lớn.")
        val root = extent(u32(pvd, 158), rootSize.toInt())
        val disc = sfo(find(find(root, "PSP_GAME", true), "PARAM.SFO", false), "DISC_ID")
        check(disc.matches(Regex("[A-Z]{4}[0-9]{5}")), "DISC_ID PSP không hợp lệ.")
        disc
    }
}
