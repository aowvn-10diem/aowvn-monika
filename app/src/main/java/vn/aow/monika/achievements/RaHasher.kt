package vn.aow.monika.achievements

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Băm file game theo quy tắc của RetroAchievements (nguồn: rcheevos `src/rhash`) để biết game nào trong Thư viện là game RA nào.
 * Giai đoạn 1 chỉ hỗ trợ hệ băm "cả file" + NES (bỏ header 16 byte) + SNES (bỏ header 512 byte). Hệ khác trả null.
 */
object RaHasher {
    /** Mã hệ RA theo hệ máy Monika; null = chưa băm được. `.gb` và `.gbc` khác mã. */
    fun consoleFor(systemId: String, file: File): Int? {
        val ext = file.extension.lowercase()
        return when (systemId) {
            "gba" -> 5
            "gbc" -> if (ext == "gbc") 6 else if (ext == "gb") 4 else null
            "nes" -> if (ext == "nes") 7 else null
            "snes" -> 3
            "genesis" -> 1
            "sms" -> 11
            "gg" -> 15
            "ngp" -> 14
            "ws" -> 53
            "a2600" -> 25
            else -> null
        }
    }

    /** Số byte đầu cần bỏ trước khi băm (theo header); [head] là tối đa 16 byte đầu file. */
    fun headerSize(consoleId: Int, size: Long, head: ByteArray): Int = when (consoleId) {
        7 -> if (head.size >= 4 && head[0] == 0x4E.toByte() && head[1] == 0x45.toByte() && head[2] == 0x53.toByte() && head[3] == 0x1A.toByte() && size > 16) 16 else 0
        3 -> if (size - (size / 0x2000) * 0x2000 == 512L) 512 else 0
        else -> 0
    }

    fun hash(file: File, consoleId: Int): String? = runCatching {
        val size = file.length()
        if (size <= 0) return null
        val head = file.inputStream().use { ins -> ByteArray(16).let { b -> val n = ins.read(b); if (n < 0) ByteArray(0) else b.copyOf(n) } }
        val skip = headerSize(consoleId, size, head)
        file.inputStream().use { md5(it, skip) }
    }.getOrNull()

    internal fun md5(ins: InputStream, skip: Int): String {
        var left = skip.toLong()
        while (left > 0) { val n = ins.skip(left); if (n <= 0) break; left -= n }
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(1 shl 16)
        while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
