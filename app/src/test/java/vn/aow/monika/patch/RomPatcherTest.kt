package vn.aow.monika.patch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class RomPatcherTest {
    private fun crc(b: ByteArray) = CRC32().also { it.update(b) }.value
    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    /** Mã hóa varint kiểu BPS/UPS (ngược với bộ giải). */
    private fun varint(v0: Long): ByteArray {
        var v = v0; val o = ByteArrayOutputStream()
        while (true) {
            val x = (v and 0x7F).toInt(); v = v shr 7
            if (v == 0L) { o.write(x or 0x80); break }
            o.write(x); v--
        }
        return o.toByteArray()
    }
    private fun withFooter(body: ByteArray, src: ByteArray, dst: ByteArray): ByteArray {
        val noPatchCrc = body + le32(crc(src)) + le32(crc(dst))
        return noPatchCrc + le32(crc(noPatchCrc))
    }

    private val src = "hello world".toByteArray()
    private val dst = "hello brave world".toByteArray()

    @Test fun ipsPatchAndRle() {
        val p = ByteArrayOutputStream().apply {
            write("PATCH".toByteArray())
            write(byteArrayOf(0, 0, 0, 0, 2, 'J'.code.toByte(), 'Y'.code.toByte())) // ghi đè "he"→"JY"
            write(byteArrayOf(0, 0, 11, 0, 0, 0, 3, '!'.code.toByte()))             // RLE "!!!" ngay sau ROM (mở rộng)
            write("EOF".toByteArray())
        }.toByteArray()
        assertEquals("JYllo world!!!", String(RomPatcher.apply(src, p)))
    }

    @Test fun ipsTruncate() {
        val p = "PATCH".toByteArray() + "EOF".toByteArray() + byteArrayOf(0, 0, 5)
        assertEquals("hello", String(RomPatcher.apply(src, p)))
    }

    @Test fun bpsAllActions() {
        val body = ByteArrayOutputStream().apply {
            write("BPS1".toByteArray()); write(varint(src.size.toLong())); write(varint(dst.size.toLong())); write(varint(0))
            write(varint(((6 - 1) shl 2 or 0).toLong()))                      // SourceRead "hello "
            write(varint(((6 - 1) shl 2 or 1).toLong())); write("brave ".toByteArray()) // TargetRead
            write(varint(((5 - 1) shl 2 or 2).toLong())); write(varint(6L shl 1))       // SourceCopy "world" từ offset 6
        }.toByteArray()
        val out = RomPatcher.apply(src, withFooter(body, src, dst))
        assertArrayEquals(dst, out)
    }

    @Test fun bpsTargetCopy() {
        val t = "abababab".toByteArray(); val s = "x".toByteArray()
        val body = ByteArrayOutputStream().apply {
            write("BPS1".toByteArray()); write(varint(1)); write(varint(t.size.toLong())); write(varint(0))
            write(varint(((2 - 1) shl 2 or 1).toLong())); write("ab".toByteArray())  // TargetRead "ab"
            write(varint(((6 - 1) shl 2 or 3).toLong())); write(varint(0))           // TargetCopy 6 byte từ đầu (chồng lên vùng đang ghi)
        }.toByteArray()
        assertArrayEquals(t, RomPatcher.apply(s, withFooter(body, s, t)))
    }

    @Test fun bpsWrongRomRejected() {
        val body = ByteArrayOutputStream().apply {
            write("BPS1".toByteArray()); write(varint(src.size.toLong())); write(varint(dst.size.toLong())); write(varint(0))
            write(varint(((6 - 1) shl 2 or 0).toLong()))
            write(varint(((6 - 1) shl 2 or 1).toLong())); write("brave ".toByteArray())
            write(varint(((5 - 1) shl 2 or 2).toLong())); write(varint(6L shl 1))
        }.toByteArray()
        val patch = withFooter(body, src, dst)
        try { RomPatcher.apply("HELLO WORLD".toByteArray(), patch); fail() }
        catch (e: PatchException) { assertTrue(e.message, e.message!!.contains("ROM không đúng")) }
    }

    @Test fun bpsCorruptPatchRejected() {
        val body = "BPS1".toByteArray() + varint(1) + varint(1) + varint(0)
        val patch = withFooter(body, src, src).also { it[5] = (it[5] + 1).toByte() }
        try { RomPatcher.apply(src, patch); fail() } catch (e: PatchException) { assertTrue(e.message!!.contains("CRC")) }
    }

    @Test fun upsXor() {
        val s = byteArrayOf(1, 2, 3, 4); val t = byteArrayOf(1, 9, 3, 4, 7)
        // Khối 1: offset 1 (xor 2→9) kết thúc bằng byte 0 → pos=3. Khối 2: +1 → pos=4, ghi byte thứ 5 (xor với 0 = 7).
        val fixed = ByteArrayOutputStream().apply {
            write("UPS1".toByteArray()); write(varint(4)); write(varint(5))
            write(varint(1)); write(byteArrayOf((2 xor 9).toByte(), 0))
            write(varint(1)); write(byteArrayOf(7, 0))
        }.toByteArray()
        assertArrayEquals(t, RomPatcher.apply(s, withFooter(fixed, s, t)))
    }

    @Test fun detectByMagicNotExtension() {
        assertEquals(RomPatcher.Format.IPS, RomPatcher.detect("PATCHEOF".toByteArray()))
        assertEquals(RomPatcher.Format.BPS, RomPatcher.detect("BPS1xx".toByteArray()))
        assertEquals(RomPatcher.Format.UPS, RomPatcher.detect("UPS1xx".toByteArray()))
        assertNull(RomPatcher.detect("zip".toByteArray()))
    }
}
