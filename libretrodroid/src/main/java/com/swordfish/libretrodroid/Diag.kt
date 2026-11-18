package com.swordfish.libretrodroid

/**
 * Cầu nối chẩn đoán một chiều cho phép đo pause/destroy (#350/#318,
 * `docs/loi/melondsds-anr-1313.md` §5.2).
 *
 * Module `:libretrodroid` **không** thấy lớp app (`vn.aow.monika.diag.Diagnostics`) nên app gán
 * [sink] lúc tạo view, giống tiền lệ `GLRetroViewData.startupListener`. `sink == null` (app khác,
 * unit test) ⇒ mọi lời gọi là no-op.
 *
 * Chỉ để **đo**: không đổi luồng pause/resume, không giữ tham chiếu vòng đời, không đọc/ghi tệp,
 * và lỗi của sink bị nuốt để chẩn đoán không bao giờ làm hỏng luồng game.
 */
object Diag {
    /** Nơi nhận mốc đo. App gán; `null` = không ai nghe. */
    @Volatile
    var sink: ((String) -> Unit)? = null

    /**
     * Mốc `t0` (`onActivityPrePaused`, API 29+) do app đặt, tính bằng
     * `SystemClock.elapsedRealtime()` của **cùng tiến trình**. `0` = không có (API 26–28)
     * ⇒ `t1` tự là mốc gốc. Module đọc **một lần** rồi xoá về 0 để mốc cũ của lần pause
     * trước không bị dùng lại.
     */
    @Volatile
    var pauseOriginAt: Long = 0L

    /** Ghi một mốc đo. Không bao giờ ném (kể cả khi sink hỏng). */
    fun mark(msg: String) {
        val s = sink ?: return
        try {
            s(msg)
        } catch (_: Throwable) {
            // Chẩn đoán không được phép làm chết luồng game.
        }
    }
}

/**
 * Hàm **thuần** tính chênh lệch ms và định dạng crumb của một vệt mốc
 * (`t1`, `t2`, …). Không đụng Android nên test được bằng JUnit thường.
 *
 * Mỗi dòng có dạng `t2 +56ms (12503457)`: số ms so với mốc trước, rồi
 * `elapsedRealtime()` tuyệt đối để nối mốc giữa hai tiến trình.
 */
object DiagTimeline {
    /** Một mốc: nhãn (`t1`…) và thời điểm `SystemClock.elapsedRealtime()`. */
    data class Mark(val label: String, val at: Long)

    /** Chênh lệch so với mốc trước; mốc đầu hoặc mốc lùi thời gian trả 0. */
    fun deltaMs(prev: Mark?, cur: Mark): Long =
        if (prev == null) 0L else (cur.at - prev.at).coerceAtLeast(0L)

    fun format(prev: Mark?, cur: Mark): String = when {
        prev == null -> "${cur.label} gốc (${cur.at})"
        cur.at < prev.at -> "${cur.label} +0ms lùi ${cur.at - prev.at}ms (${cur.at})"
        else -> "${cur.label} +${cur.at - prev.at}ms (${cur.at})"
    }

    /** Định dạng cả vệt, mỗi mốc so với mốc ngay trước nó. */
    fun formatAll(marks: List<Mark>): List<String> =
        marks.mapIndexed { i, cur -> format(marks.getOrNull(i - 1), cur) }

    /**
     * Dòng cho **mốc đầu tiên** của vệt khi mốc `t0` nằm ở tiến trình khác (app) và được
     * chuyền qua `Diag.pauseOriginAt`. Không có `t0`, hoặc `t0` không dùng được (mốc lùi /
     * chưa đặt) ⇒ ghi rõ **t0 không đo được** thay vì lặng lẽ coi `t1` là gốc.
     */
    fun formatFirst(originAt: Long?, cur: Mark): String = when {
        originAt == null || originAt <= 0L -> "${cur.label} gốc (${cur.at}) t0 không đo được"
        originAt > cur.at -> "${cur.label} gốc (${cur.at}) t0 không đo được"
        else -> format(Mark("t0", originAt), cur)
    }
}
