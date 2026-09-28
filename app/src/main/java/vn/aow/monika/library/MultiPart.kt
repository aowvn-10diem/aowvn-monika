package vn.aow.monika.library

import java.io.File

/**
 * Nhận diện file nén chia nhiều phần:
 * - Game.part1.rar, Game.part2.rar … (WinRAR mới)
 * - Game.rar + Game.r00, Game.r01 … (WinRAR cũ)
 * - Game.7z.001, Game.zip.001 … (7-Zip)
 */
object MultiPart {
    data class Piece(val base: String, val index: Int)

    private val partRar = Regex("""(?i)^(.*)\.part0*(\d+)\.rar$""")
    private val oldRar = Regex("""(?i)^(.*)\.r(\d{2})$""")
    private val numbered = Regex("""(?i)^(.*\.(?:7z|zip|rar))\.(\d{3})$""")

    fun parse(name: String): Piece? {
        partRar.find(name)?.let { return Piece(it.groupValues[1].lowercase() + ".rar", it.groupValues[2].toInt()) }
        numbered.find(name)?.let { return Piece(it.groupValues[1].lowercase(), it.groupValues[2].toInt()) }
        // .rNN là phần 2 trở đi của bộ cũ; phần đầu là Game.rar (index 0).
        oldRar.find(name)?.let { return Piece(it.groupValues[1].lowercase() + ".rar", it.groupValues[2].toInt() + 1) }
        return null
    }

    /** Các phần cùng bộ trong thư mục, sắp đúng thứ tự (kể cả Game.rar đầu bộ cũ). */
    fun siblings(dir: File, piece: Piece): List<File> {
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val parts = files.mapNotNull { f -> parse(f.name)?.takeIf { it.base == piece.base }?.let { f to it.index } }.toMutableList()
        if (parts.any { oldRar.containsMatchIn(it.first.name) }) {
            files.firstOrNull { it.name.lowercase() == piece.base }?.let { parts += it to 0 }
        }
        return parts.sortedBy { it.second }.map { it.first }
    }

    /** Đủ bộ chưa: chỉ số liên tục từ phần đầu (1, hoặc 0 với bộ .rNN). Không biết tổng số phần → thử giải nén để chắc. */
    fun looksContiguous(parts: List<File>): Boolean {
        val idx = parts.mapNotNull { f -> parse(f.name)?.index ?: 0 }
        if (idx.isEmpty()) return false
        return idx.zipWithNext().all { (a, b) -> b == a + 1 } && idx.first() <= 1
    }
}
