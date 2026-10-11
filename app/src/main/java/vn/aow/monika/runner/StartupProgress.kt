package vn.aow.monika.runner

import java.util.Locale

/** Thời gian đơn điệu; cập nhật được từ IO/GL, không chứa đường dẫn game. */
internal class StartupProgress(private val now: () -> Long, private val breadcrumb: (String) -> Unit = {}) {
    private val started = now()
    private var since = started
    private var stage = "core-check"
    private var detail = "Đang kiểm tra lõi…"
    private var finished = false
    private val timings = mutableListOf<String>()

    @Synchronized fun update(next: String, text: String) {
        if (finished) return
        val at = now()
        if (next != stage) {
            val timing = "$stage=${at - since}ms"
            timings += timing
            breadcrumb(timing)
            stage = next; since = at
            breadcrumb("begin=$next")
        }
        detail = text
    }
    @Synchronized fun finish() {
        if (finished) return
        update("done", "Game đã lên hình")
        finished = true
    }
    @Synchronized fun isSlow(): Boolean = !finished && now() - started >= SOFT_TIMEOUT_MS
    suspend fun awaitSoftTimeout(): Boolean {
        kotlinx.coroutines.delay((SOFT_TIMEOUT_MS - (now() - started)).coerceAtLeast(0))
        return isSlow()
    }
    @Synchronized fun text(): String = detail
    @Synchronized fun describe(): String =
        (timings + "$stage=${now() - since}ms (hiện tại)" + "total=${now() - started}ms").joinToString("\n")

    companion object { const val SOFT_TIMEOUT_MS = 60_000L }
}

internal fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes.coerceAtLeast(0) / 1_000_000.0)

/** Tiến độ lõi dùng chung cho tải sẵn và màn chơi; total=-1 khi máy chủ không gửi kích thước. */
data class CoreProgress(val stage: String, val bytes: Long = 0, val total: Long = -1) {
    val percent: Int? get() = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else null
    val text: String get() = when (stage) {
        "core-download" -> "Đang tải lõi: ${megabytes(bytes)}" +
            (if (total > 0) " / ${megabytes(total)} · $percent%" else "")
        "core-unzip" -> "Đang giải nén lõi…"
        "core-cache" -> "Dùng lõi đã lưu trong bộ nhớ đệm"
        "system-download" -> "Đang tải dữ liệu hệ thống…"
        else -> "Đang kiểm tra lõi…"
    }
}
