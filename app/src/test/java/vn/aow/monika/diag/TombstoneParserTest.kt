package vn.aow.monika.diag

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TombstoneParserTest {
    private fun varint(o: ByteArrayOutputStream, v: Long) {
        var x = v
        while (x and 0x7fL.inv() != 0L) { o.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }
        o.write(x.toInt())
    }
    private fun tag(o: ByteArrayOutputStream, f: Int, w: Int) = varint(o, ((f shl 3) or w).toLong())
    private fun msg(f: Int, body: ByteArray, o: ByteArrayOutputStream) { tag(o, f, 2); varint(o, body.size.toLong()); o.write(body) }
    private fun str(f: Int, s: String, o: ByteArrayOutputStream) = msg(f, s.toByteArray(), o)
    private fun num(f: Int, v: Long, o: ByteArrayOutputStream) { tag(o, f, 0); varint(o, v) }

    private fun sample(): ByteArray {
        val frame = ByteArrayOutputStream().also { num(1, 0x3b6297, it); str(4, "SDL_main", it); num(5, 12, it); str(6, "/data/app/x/lib/libmkxp-z.so", it); str(8, "abc123", it) }
        val thread = ByteArrayOutputStream().also { num(1, 7303, it); str(2, "GLThread 48", it); msg(4, frame.toByteArray(), it) }
        val entry = ByteArrayOutputStream().also { num(1, 7303, it); msg(2, thread.toByteArray(), it) }
        val sig = ByteArrayOutputStream().also { num(1, 11, it); str(2, "SIGSEGV", it); num(3, 1, it); str(4, "SEGV_MAPERR", it); num(8, 1, it); num(9, 8, it) }
        val cause = ByteArrayOutputStream().also { str(1, "null pointer dereference", it) }
        return ByteArrayOutputStream().also {
            num(6, 7303, it); msg(10, sig.toByteArray(), it); msg(15, cause.toByteArray(), it); msg(16, entry.toByteArray(), it)
        }.toByteArray()
    }

    @Test fun docDuocTinHieuLuongVaNganXep() {
        val r = TombstoneParser.parse(sample())
        assertNotNull(r)
        assertEquals("SIGSEGV", r!!.signalName)
        assertEquals(8L, r.faultAddress)
        assertEquals("GLThread 48", r.threadName)
        assertEquals(1, r.frames.size)
        val lines = TombstoneParser.toLines(r)
        assertTrue(lines.any { it.startsWith("SIGSEGV (SEGV_MAPERR) địa chỉ lỗi 0x8") })
        assertTrue(lines.any { it.contains("libmkxp-z.so+0x3b6297 SDL_main+12 [build_id abc123]") })
        assertTrue(lines.any { it.contains("null pointer dereference") })
    }

    @Test fun duLieuHongThiTraNull() {
        assertNull(TombstoneParser.parse(byteArrayOf(0x52, 0x7f, 0x01)))
        assertNull(TombstoneParser.parse(ByteArray(0)))
    }
}
