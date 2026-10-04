package vn.aow.monika.diag

/** Lọc logcat -v epoch: giữ log PID và DEBUG của crash_dump trong ±5 giây quanh lúc chết. */
internal object NativeCrashLogcat {
    private val row = Regex("""^\s*(\d+)\.(\d+)\s+(\d+)\s+\d+\s+[VDIWEFAS]\s+(\S+)\s*:.*$""")

    fun select(lines: Sequence<String>, pid: Int, deathAt: Long, max: Int = 220): List<String> =
        lines.filter { line ->
            val match = row.matchEntire(line) ?: return@filter false
            if (match.groupValues[3].toIntOrNull() == pid) return@filter true
            if (deathAt <= 0 || match.groupValues[4] != "DEBUG") return@filter false
            val seconds = match.groupValues[1].toLongOrNull() ?: return@filter false
            if (seconds > (Long.MAX_VALUE - 999) / 1000) return@filter false
            val millis = match.groupValues[2].take(3).padEnd(3, '0').toLong()
            (seconds * 1000 + millis - deathAt) in -5_000L..5_000L
        }.toList().takeLast(max.coerceAtLeast(0)).map { it.take(300) }
}
