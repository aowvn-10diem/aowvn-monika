package vn.aow.monika.diag

/**
 * Phần trace ANR cần cho việc tìm chặng bị treo, nhưng [Diagnostics.traceStrings] đang bỏ mất.
 *
 * `INTERESTING` (`Diagnostics.kt`) chỉ giữ dòng có `.so|libretro|libc|…`; vì vậy trace ANR hiện
 * chỉ còn các dòng `native: #NN pc …`:
 * - mất dòng tiêu đề luồng (`"main" prio=5 tid=1 Native`) ⇒ không biết khung native thuộc luồng nào;
 * - mất khung Java (`at android.opengl.GLSurfaceView$GLThread.guardedRun(…)`) ⇒ không biết Main Thread
 *   đang chờ ở đâu (chặng pause/destroy nào).
 *
 * [appendMainThread] nối lại đúng khối `main`, nhưng **chỉ nối sau** các dòng đã có và **chỉ khi**
 * đã có đủ [BOT_FRAMES] dòng — bot gộp issue lấy tối đa 3 khung đầu theo thứ tự
 * (`scripts/crash-issue-bot.py:39-53`), nên chữ ký của 9 issue đang mở không đổi.
 */
internal object AnrTrace {
    /** Số khung bot lấy để băm chữ ký; đủ rồi thì phần nối thêm không ảnh hưởng chữ ký. */
    const val BOT_FRAMES = 3

    /** Trần số dòng giữ từ khối `main` (một trace ANR thật có thể vài trăm dòng). */
    const val MAX_MAIN_LINES = 16

    /** Trần ký tự mỗi dòng, cùng mức với bộ lọc hiện tại (`Diagnostics.traceStrings`). */
    const val MAX_LINE_CHARS = 200

    /**
     * Ghép thêm khối `main` vào cuối [existing] để báo cáo có ngữ cảnh luồng.
     *
     * Không làm gì khi: chưa đủ [BOT_FRAMES] dòng (chữ ký bot sẽ đổi), hoặc trace không có khối `main`.
     * Dòng trùng với [existing] bị bỏ để không phình `detail`.
     */
    fun appendMainThread(existing: List<String>, rawLines: List<String>): List<String> {
        if (existing.size < BOT_FRAMES) return existing
        val main = mainSection(rawLines)
        if (main.isEmpty()) return existing
        val seen = existing.toHashSet()
        val out = ArrayList<String>(existing.size + main.size)
        out += existing
        for (line in main) if (seen.add(line)) out += line
        return out
    }

    /**
     * Trích khối luồng `main` trong trace ANR dạng văn bản: dòng tiêu đề, các dòng `native:`/`at `
     * (khung), và `| state=` / `| held` (trạng thái + mutex đang giữ). Dừng ở luồng kế tiếp
     * (dòng bắt đầu bằng `"`) hoặc dòng trống.
     */
    fun mainSection(rawLines: List<String>, maxLines: Int = MAX_MAIN_LINES): List<String> {
        val start = rawLines.indexOfFirst { it.trimStart().startsWith("\"main\"") }
        if (start < 0) return emptyList()
        val out = ArrayList<String>(maxLines)
        for (i in start until rawLines.size) {
            val line = rawLines[i].trimEnd()
            if (i > start && (line.isBlank() || line.trimStart().startsWith("\""))) break
            val body = line.trim()
            val keep = i == start || body.startsWith("native:") || body.startsWith("at ") ||
                body.startsWith("kernel:") || body.startsWith("| state=") || body.startsWith("| held")
            if (!keep) continue
            out += line.take(MAX_LINE_CHARS)
            if (out.size >= maxLines) break
        }
        return out
    }
}
