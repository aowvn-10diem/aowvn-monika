package vn.aow.monika.patch

import java.io.IOException
import java.util.zip.CRC32

/** Lỗi khi vá: thông điệp tiếng Việt, hiển thị thẳng cho người chơi. */
class PatchException(message: String) : IOException(message)

/**
 * Áp bản Việt hóa (patch) lên ROM: IPS, BPS, UPS. Hàm thuần trên mảng byte (ROM game nhỏ hơn vài chục MB; ISO lớn nên dùng xdelta/PPF – chưa hỗ trợ).
 * BPS/UPS có kiểm CRC32 nên báo được "sai bản ROM" thay vì tạo ra file hỏng; IPS không có checksum.
 */
object RomPatcher {
    enum class Format(val label: String, val extensions: Set<String>) {
        IPS("IPS", setOf("ips")), BPS("BPS", setOf("bps")), UPS("UPS", setOf("ups"))
    }

    /** Nhận diện theo chữ ký đầu file (đáng tin hơn đuôi file, vì file tải về hay bị đổi tên). */
    fun detect(patch: ByteArray): Format? = when {
        patch.startsWith("PATCH") -> Format.IPS
        patch.startsWith("BPS1") -> Format.BPS
        patch.startsWith("UPS1") -> Format.UPS
        else -> null
    }

    fun apply(rom: ByteArray, patch: ByteArray): ByteArray = when (detect(patch) ?: throw PatchException("Không nhận ra định dạng bản vá (hỗ trợ IPS, BPS, UPS).")) {
        Format.IPS -> applyIps(rom, patch)
        Format.BPS -> applyBps(rom, patch)
        Format.UPS -> applyUps(rom, patch)
    }

    // ---------- IPS ----------
    private fun applyIps(rom: ByteArray, p: ByteArray): ByteArray {
        var out = rom.copyOf()
        fun ensure(size: Int) { if (size > out.size) out = out.copyOf(size) }
        var i = 5
        while (true) {
            if (i + 3 > p.size) throw PatchException("Bản vá IPS bị cắt cụt (thiếu EOF).")
            if (p[i] == 'E'.code.toByte() && p[i + 1] == 'O'.code.toByte() && p[i + 2] == 'F'.code.toByte()) { i += 3; break }
            val offset = (p[i].u() shl 16) or (p[i + 1].u() shl 8) or p[i + 2].u(); i += 3
            if (i + 2 > p.size) throw PatchException("Bản vá IPS bị hỏng.")
            val size = (p[i].u() shl 8) or p[i + 1].u(); i += 2
            if (size == 0) { // RLE
                if (i + 3 > p.size) throw PatchException("Bản vá IPS bị hỏng.")
                val len = (p[i].u() shl 8) or p[i + 1].u(); val v = p[i + 2]; i += 3
                ensure(offset + len)
                java.util.Arrays.fill(out, offset, offset + len, v)
            } else {
                if (i + size > p.size) throw PatchException("Bản vá IPS bị hỏng.")
                ensure(offset + size)
                System.arraycopy(p, i, out, offset, size); i += size
            }
        }
        if (i + 3 <= p.size) { // một số bản vá có thêm độ dài cắt ngắn
            val trunc = (p[i].u() shl 16) or (p[i + 1].u() shl 8) or p[i + 2].u()
            if (trunc in 1 until out.size) out = out.copyOf(trunc)
        }
        return out
    }

    // ---------- BPS ----------
    private fun applyBps(rom: ByteArray, p: ByteArray): ByteArray {
        if (p.size < 4 + 3 + 12) throw PatchException("Bản vá BPS bị hỏng.")
        val footer = p.size - 12
        val srcCrc = le32(p, footer); val dstCrc = le32(p, footer + 4); val patchCrc = le32(p, footer + 8)
        if (crc(p, 0, p.size - 4) != patchCrc) throw PatchException("Bản vá BPS bị hỏng (sai CRC). Hãy tải lại bản vá.")
        val r = Reader(p, 4, footer)
        val srcSize = r.varint(); val dstSize = r.varint(); val metaSize = r.varint()
        r.skip(metaSize.toInt())
        if (srcSize != rom.size.toLong() || crc(rom, 0, rom.size) != srcCrc)
            throw PatchException("ROM không đúng bản mà bản Việt hóa này yêu cầu (sai kích thước hoặc CRC32). Hãy dùng đúng ROM gốc (thường ghi trong bài viết).")
        if (dstSize > Int.MAX_VALUE) throw PatchException("Kết quả quá lớn.")
        val out = ByteArray(dstSize.toInt())
        var o = 0; var srcRel = 0L; var dstRel = 0L
        while (r.hasMore()) {
            val data = r.varint(); val len = (data shr 2).toInt() + 1
            if (o + len > out.size) throw PatchException("Bản vá BPS không khớp kích thước.")
            when ((data and 3).toInt()) {
                0 -> { if (o + len > rom.size) throw PatchException("Bản vá BPS không khớp ROM."); System.arraycopy(rom, o, out, o, len); o += len }
                1 -> { r.copyTo(out, o, len); o += len }
                2 -> {
                    val d = r.varint(); srcRel += if (d and 1L != 0L) -(d shr 1) else (d shr 1)
                    if (srcRel < 0 || srcRel + len > rom.size) throw PatchException("Bản vá BPS không khớp ROM.")
                    System.arraycopy(rom, srcRel.toInt(), out, o, len); o += len; srcRel += len
                }
                else -> {
                    val d = r.varint(); dstRel += if (d and 1L != 0L) -(d shr 1) else (d shr 1)
                    if (dstRel < 0) throw PatchException("Bản vá BPS bị hỏng.")
                    repeat(len) { out[o++] = out[dstRel.toInt()]; dstRel++ } // chép từng byte: được phép đè lên vùng vừa ghi
                }
            }
        }
        if (o != out.size) throw PatchException("Bản vá BPS không khớp kích thước.")
        if (crc(out, 0, out.size) != dstCrc) throw PatchException("Vá xong nhưng kết quả sai CRC32. Có thể ROM không đúng bản.")
        return out
    }

    // ---------- UPS ----------
    private fun applyUps(rom: ByteArray, p: ByteArray): ByteArray {
        if (p.size < 4 + 2 + 12) throw PatchException("Bản vá UPS bị hỏng.")
        val footer = p.size - 12
        val srcCrc = le32(p, footer); val dstCrc = le32(p, footer + 4); val patchCrc = le32(p, footer + 8)
        if (crc(p, 0, p.size - 4) != patchCrc) throw PatchException("Bản vá UPS bị hỏng (sai CRC). Hãy tải lại bản vá.")
        val r = Reader(p, 4, footer)
        val inSize = r.varint(); val outSize = r.varint()
        if (inSize != rom.size.toLong() || crc(rom, 0, rom.size) != srcCrc)
            throw PatchException("ROM không đúng bản mà bản Việt hóa này yêu cầu (sai kích thước hoặc CRC32). Hãy dùng đúng ROM gốc (thường ghi trong bài viết).")
        if (outSize > Int.MAX_VALUE) throw PatchException("Kết quả quá lớn.")
        val out = rom.copyOf(outSize.toInt())
        var pos = 0L
        while (r.hasMore()) {
            pos += r.varint()
            while (true) {
                val x = r.byte()
                if (pos < out.size) out[pos.toInt()] = (out[pos.toInt()].toInt() xor x).toByte()
                pos++
                if (x == 0) break
            }
        }
        if (crc(out, 0, out.size) != dstCrc) throw PatchException("Vá xong nhưng kết quả sai CRC32. Có thể ROM không đúng bản.")
        return out
    }

    // ---------- tiện ích ----------
    private fun ByteArray.startsWith(s: String) = size >= s.length && s.indices.all { this[it] == s[it].code.toByte() }
    private fun Byte.u() = toInt() and 0xFF
    private fun le32(b: ByteArray, o: Int) = (b[o].u().toLong()) or (b[o + 1].u().toLong() shl 8) or (b[o + 2].u().toLong() shl 16) or (b[o + 3].u().toLong() shl 24)
    private fun crc(b: ByteArray, off: Int, len: Int): Long = CRC32().also { it.update(b, off, len) }.value

    /** Đọc varint kiểu BPS/UPS và byte thô trong khoảng [pos, end). */
    private class Reader(val b: ByteArray, var pos: Int, val end: Int) {
        fun hasMore() = pos < end
        fun byte(): Int { if (pos >= end) throw PatchException("Bản vá bị cắt cụt."); return b[pos++].toInt() and 0xFF }
        fun skip(n: Int) { if (n < 0 || pos + n > end) throw PatchException("Bản vá bị hỏng."); pos += n }
        fun copyTo(dst: ByteArray, off: Int, n: Int) { if (pos + n > end) throw PatchException("Bản vá bị cắt cụt."); System.arraycopy(b, pos, dst, off, n); pos += n }
        fun varint(): Long {
            var data = 0L; var shift = 1L
            while (true) {
                val x = byte()
                data += (x and 0x7F) * shift
                if (x and 0x80 != 0) return data
                shift = shift shl 7
                data += shift
                if (shift > (1L shl 56)) throw PatchException("Bản vá bị hỏng.")
            }
        }
    }
}
