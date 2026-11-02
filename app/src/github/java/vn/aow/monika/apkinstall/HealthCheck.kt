package vn.aow.monika.apkinstall

import kotlinx.coroutines.delay

/** Kết quả tự kiểm tra game đã chỉnh (Cách 1). */
sealed class Health {
    /** Game hiện màn hình và còn sống sau [HealthCheck.aliveMs] → nghi là chạy được, cần người chơi xác nhận. */
    object LikelyOk : Health()
    data class Failed(val reason: String) : Health()
}

/**
 * Tự kiểm tra game có chạy được không, dựa vào tin bộ nạp gửi về ([LoaderEvents]):
 *  - không khởi động / không hiện màn hình trong [startMs] → hỏng;
 *  - crash Java → hỏng (kèm dòng lỗi đầu);
 *  - hiện màn hình rồi mất nhịp sống trước [aliveMs] → "thoát ngay" (thường do game tự kiểm tra chữ ký);
 *  - hiện màn hình và còn sống sau [aliveMs] → LikelyOk.
 */
object HealthCheck {
    const val START_MS = 15_000L
    const val ALIVE_MS = 8_000L
    /** Quá lâu không có nhịp sống (bộ nạp gửi mỗi giây) coi là tiến trình đã chết. */
    const val BEAT_STALE_MS = 3_500L

    suspend fun run(
        pkg: String,
        launch: () -> Unit,
        startMs: Long = START_MS,
        aliveMs: Long = ALIVE_MS,
        pollMs: Long = 200,
        now: () -> Long = LoaderEvents.clock,
    ): Health {
        LoaderEvents.reset(pkg)
        val t0 = now()
        launch()
        val s = LoaderEvents.state(pkg)
        while (true) {
            val t = now()
            if (s.crashAt > 0) return Health.Failed("Game bị lỗi và thoát: " + (s.crashSummary?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "không rõ"))
            val screen = s.screenAt
            if (screen == 0L) {
                if (t - t0 > startMs) return Health.Failed("Game không hiện màn hình trong ${startMs / 1000} giây (có thể không khởi động được).")
            } else {
                if (t - s.lastBeat > BEAT_STALE_MS) return Health.Failed("Game thoát ngay sau khi mở (thường do game tự kiểm tra chữ ký).")
                if (t - screen >= aliveMs) return Health.LikelyOk
            }
            delay(pollMs)
        }
    }
}
