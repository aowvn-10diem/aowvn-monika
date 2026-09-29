package vn.aow.monika.library

import java.io.File
import java.io.RandomAccessFile

/**
 * Kiểm nhanh header ROM với các đuôi file dễ trùng với file thường (".md" = Markdown!).
 * Chỉ áp cho đuôi mơ hồ; các đuôi riêng (.nds .gba .sfc…) nhận thẳng.
 */
object RomSniff {
    private val AMBIGUOUS = setOf("md", "gen")

    fun accepts(file: File, ext: String = file.extension.lowercase()): Boolean {
        if (ext !in AMBIGUOUS) return true
        // Mega Drive: "SEGA" tại 0x100 ("SEGA MEGA DRIVE" / "SEGA GENESIS").
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 0x104) return false
                val b = ByteArray(4).also { raf.seek(0x100); raf.readFully(it) }
                String(b, Charsets.US_ASCII) == "SEGA"
            }
        }.getOrDefault(false)
    }
}
