package vn.aow.monika.diag

/** Không regex từ config: chuỗi literal có giới hạn tránh chạy biểu thức không kiểm soát. */
internal object EngineSignals {
    fun matched(lines: List<String>, patterns: List<String>): List<String> = lines.filter { line ->
        patterns.take(32).any { it.isNotBlank() && line.contains(it.take(160), ignoreCase = true) }
    }.takeLast(40)

    fun isBlack(pixels: IntArray): Boolean = pixels.isNotEmpty() && pixels.all { p ->
        (p ushr 24) != 0 && ((p ushr 16) and 255) <= 12 && ((p ushr 8) and 255) <= 12 && (p and 255) <= 12
    }
}

/** Null (copy thất bại) hay một điểm sáng phá chuỗi; pause/resume phải reset. */
internal class BlackFrameTracker {
    private var since: Long? = null
    private var reported = false
    fun reset() { since = null; reported = false }
    fun observe(black: Boolean?, elapsed: Long): Boolean {
        if (black != true) { since = null; return false }
        if (since == null) since = elapsed
        if (!reported && elapsed - since!! >= 30_000) { reported = true; return true }
        return false
    }
}
