package vn.aow.monika.runner

import com.swordfish.libretrodroid.DiagTimeline

/**
 * Vệt mốc cho **một chu kỳ** pause/destroy của [RetroActivity] (#350/#318,
 * `docs/loi/melondsds-anr-1313.md` §5.2).
 *
 * Nguyên tắc: ghi crumb **trước** lời gọi có thể chặn (nếu tiến trình bị ANR-kill giữa chặng,
 * crumb mở cuối cùng cho biết đang đứng ở đâu) và **sau khi** nó trả về (mất bao nhiêu ms).
 *
 * Chu kỳ được mở bằng [origin] (`t0`) hoặc [begin] (`t4` khi không có `t0`); **không** giữ mốc
 * của chu kỳ trước, nên lần ra nền thứ hai không cộng dồn thời gian của lần thứ nhất.
 *
 * Không chứa API Android: thời điểm do chỗ gọi truyền vào, nội dung do [DiagTimeline] định dạng
 * ⇒ test JUnit thuần. Lỗi của [emit] bị nuốt: đo lường không được đổi hành vi thoát game.
 *
 * Mốc `t0` (`onActivityPrePaused`) và `t1`–`t3` sinh ở phía app/`:libretrodroid`
 * ([DiagTimeline] qua `Diag.sink`); vệt này giữ `t4`, `t4s`, `t5`–`t9` trong tiến trình app.
 * Crumb có `elapsedRealtime()` tuyệt đối nên vẫn tính được `t3→t4`, và `t4` (khi có `t0`)
 * tự mang luôn hiệu `t0→t4`.
 */
internal class PauseProbe(private val emit: (String) -> Unit) {
    private var prev: DiagTimeline.Mark? = null

    /** Chu kỳ đã có `t0` (API 29+) hay chưa; quyết định `t4` nối tiếp hay tự làm gốc. */
    private var originSeen = false

    /** Mốc gốc của chu kỳ (`t0`): luôn mở chu kỳ mới, không nối mốc cũ. */
    fun origin(label: String, at: Long) {
        prev = null
        originSeen = true
        emitMark(label, at)
    }

    /** Mốc đầu chu kỳ khi vào `onPause` (`t4`): nối `t0` của **cùng** chu kỳ, ngược lại tự là gốc. */
    fun begin(label: String, at: Long) {
        if (!originSeen) prev = null
        originSeen = false
        emitMark(label, at)
    }

    /** Mốc kế tiếp trong chu kỳ hiện tại. */
    fun mark(label: String, at: Long) = emitMark(label, at)

    private fun emitMark(label: String, at: Long) {
        val cur = DiagTimeline.Mark(label, at)
        val line = DiagTimeline.format(prev, cur)
        prev = cur
        try {
            emit(line)
        } catch (_: Throwable) {
            // Đo lường không được phép làm chết luồng vòng đời.
        }
    }
}
