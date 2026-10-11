package vn.aow.monika.library

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/** Dựng XP3 tự sinh cho test (không dùng file game thật). */
object Xp3Fixture {
    enum class IndexDamage { SHORT_SUBCHUNK, OVERLONG_NAME, SHORT_INFO, MISSING_INFO }

    /** Mục main.ks đọc được trước mục startup.tjs bị hỏng; kích thước chỉ mục ngoài vẫn hợp lệ. */
    fun exeWithBrokenEntry(dir: File, damage: IndexDamage): File {
        val file = exe(dir, listOf("main.ks", "startup.tjs"), compressed = false, name = "$damage.exe")
        val bytes = file.readBytes()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val index = 64 + b.getLong(64 + 11).toInt()
        val first = index + 9
        val second = first + 12 + b.getLong(first + 4).toInt()
        var end = bytes.size
        when (damage) {
            IndexDamage.SHORT_SUBCHUNK -> {
                b.putLong(second + 4, 2)
                end = second + 12 + 2
            }
            IndexDamage.OVERLONG_NAME -> b.putShort(second + 12 + 12 + 20, 0x7fff.toShort())
            IndexDamage.SHORT_INFO -> {
                b.putLong(second + 12 + 4, 20)
                b.putLong(second + 4, 32L)
                end = second + 12 + 12 + 20
            }
            IndexDamage.MISSING_INFO -> "junk".toByteArray().copyInto(bytes, second + 12)
        }
        b.putLong(index + 1, (end - index - 9).toLong())
        file.writeBytes(bytes.copyOf(end))
        return file
    }

    fun le(n: Long, bytes: Int): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array().copyOf(bytes)

    private fun entry(name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        val info = le(0, 4) + le(5, 8) + le(5, 8) + le(name.length.toLong(), 2) + nameBytes
        val fileBody = "info".toByteArray() + le(info.size.toLong(), 8) + info
        return "File".toByteArray() + le(fileBody.size.toLong(), 8) + fileBody
    }

    /** Dựng XP3: [phần giả] + chỉ mục; compressed = chỉ mục zlib; v2 = header 0x17. */
    fun xp3(dir: File, names: List<String>, compressed: Boolean, v2: Boolean = false, filler: Int = 100,
        continuation: Boolean = false): File {
        val magic = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
        val headerLen = if (v2) 0x28 else 0x13
        val raw = ByteArrayOutputStream().also { out -> names.forEach { out.write(entry(it)) } }.toByteArray()
        val index = ByteArrayOutputStream()
        if (compressed) {
            val d = Deflater(); d.setInput(raw); d.finish()
            val buf = ByteArray(raw.size + 64); val n = d.deflate(buf); d.end()
            index.write(if (continuation) 0x81 else 1); index.write(le(n.toLong(), 8)); index.write(le(raw.size.toLong(), 8)); index.write(buf, 0, n)
        } else {
            index.write(if (continuation) 0x80 else 0); index.write(le(raw.size.toLong(), 8)); index.write(raw)
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

    /** MZ giả + XP3: [continuation] đánh dấu còn khối chỉ mục chưa đọc, offset vẫn tương đối với chữ ký. */
    fun exe(dir: File, names: List<String>, compressed: Boolean = true, v2: Boolean = false,
        offset: Int = 64, name: String = "game.exe", continuation: Boolean = false): File {
        val archive = xp3(dir, names, compressed, v2, continuation = continuation)
        val prefix = ByteArray(offset).apply { this[0] = 0x4d; this[1] = 0x5a }
        return File(dir, name).also { it.writeBytes(prefix + archive.readBytes()); archive.delete() }
    }
}
