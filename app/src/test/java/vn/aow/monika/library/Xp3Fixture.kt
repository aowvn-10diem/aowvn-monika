package vn.aow.monika.library

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/** Dựng XP3 tự sinh cho test (không dùng file game thật). */
object Xp3Fixture {
    fun le(n: Long, bytes: Int): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array().copyOf(bytes)

    private fun entry(name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        val info = le(0, 4) + le(5, 8) + le(5, 8) + le(name.length.toLong(), 2) + nameBytes
        val fileBody = "info".toByteArray() + le(info.size.toLong(), 8) + info
        return "File".toByteArray() + le(fileBody.size.toLong(), 8) + fileBody
    }

    /** Dựng XP3: [phần giả] + chỉ mục; compressed = chỉ mục zlib; v2 = header 0x17. */
    fun xp3(dir: File, names: List<String>, compressed: Boolean, v2: Boolean = false, filler: Int = 100): File {
        val magic = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
        val headerLen = if (v2) 0x28 else 0x13
        val raw = names.fold(ByteArray(0)) { a, n -> a + entry(n) }
        val index = ByteArrayOutputStream()
        if (compressed) {
            val d = Deflater(); d.setInput(raw); d.finish()
            val buf = ByteArray(raw.size + 64); val n = d.deflate(buf); d.end()
            index.write(1); index.write(le(n.toLong(), 8)); index.write(le(raw.size.toLong(), 8)); index.write(buf, 0, n)
        } else {
            index.write(0); index.write(le(raw.size.toLong(), 8)); index.write(raw)
        }
        val indexPos = (headerLen + filler).toLong()
        val out = ByteArrayOutputStream()
        out.write(magic)
        if (v2) {
            out.write(le(0x17, 8)); out.write(le(1, 4)); out.write(byteArrayOf(0x80.toByte())); out.write(le(0, 8)); out.write(le(indexPos, 8))
        } else {
            out.write(le(indexPos, 8))
        }
        out.write(ByteArray(filler) { 9 })
        out.write(index.toByteArray())
        return File(dir, "t${System.nanoTime()}.xp3").also { it.writeBytes(out.toByteArray()) }
    }

}
