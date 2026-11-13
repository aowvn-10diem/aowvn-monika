/*
 * Aow Monika: test JUnit thuần cho vệt mốc pause/destroy của RetroActivity
 * (#350/#318, docs/loi/melondsds-anr-1313.md §5.2). Không cần Robolectric:
 * thời điểm truyền vào, chỉ kiểm thứ tự + dòng crumb + không ném khi sink hỏng.
 */
package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseProbeTest {

    /** Mốc đầu tiên là gốc, các mốc sau nối tiếp mốc ngay trước (t4→t5→t6). */
    @Test
    fun marksChainInOrder() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.begin("t4", 1_000)
        probe.mark("t5", 1_300)
        probe.mark("t6", 1_301)

        assertEquals(listOf("t4 gốc (1000)", "t5 +300ms (1300)", "t6 +1ms (1301)"), lines)
    }

    /** Chặng chờ SRAM 1 500 ms phải hiện đúng số, không làm tròn. */
    @Test
    fun sramWaitIsReportedExactly() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.begin("t7", 2_000)
        probe.mark("t8", 3_500)

        assertEquals("t8 +1500ms (3500)", lines.last())
    }

    /** Chỉ một mốc (ANR-kill giữa chặng) vẫn ghi được dòng gốc. */
    @Test
    fun singleMarkIsStillEmitted() {
        val lines = mutableListOf<String>()
        PauseProbe { lines += it }.begin("t7", 42)

        assertEquals(listOf("t7 gốc (42)"), lines)
    }

    /** Mốc lùi thời gian không làm delta âm và không ném. */
    @Test
    fun backwardsMarkDoesNotThrow() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.begin("t9", 900)
        probe.mark("t6", 800)

        assertEquals("t6 +0ms lùi -100ms (800)", lines.last())
    }

    /** Khi có t0, t4 mang luôn hiệu t0→t4 (toàn bộ chặng trước thân onPause()). */
    @Test
    fun t4ChainsFromOriginWhenPrePausedFired() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.origin("t0", 1_000)
        probe.begin("t4", 2_400)

        assertEquals(listOf("t0 gốc (1000)", "t4 +1400ms (2400)"), lines)
    }

    /**
     * Ca khoá lỗi tiền duyệt: HAI chu kỳ liên tiếp. Chu kỳ 2 phải có `t0…t4` riêng (10 ms),
     * không cộng dồn 45 s đứng ở nền của chu kỳ 1.
     */
    @Test
    fun secondCycleDoesNotAccumulateFirstCycleTime() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.origin("t0", 1_000)
        probe.begin("t4", 1_010)
        probe.mark("t5", 1_300)
        probe.mark("t6", 1_305)
        // … ở nền 45 s, quay lại rồi ra nền lần hai …
        probe.origin("t0", 46_000)
        probe.begin("t4", 46_010)
        probe.mark("t5", 46_300)

        assertEquals(
            listOf(
                "t0 gốc (1000)", "t4 +10ms (1010)", "t5 +290ms (1300)", "t6 +5ms (1305)",
                "t0 gốc (46000)", "t4 +10ms (46010)", "t5 +290ms (46300)",
            ),
            lines
        )
    }

    /** API < 29 (không có t0): chu kỳ 2 mở bằng `t4` cũng phải là gốc, không nối `t9` của chu kỳ 1. */
    @Test
    fun secondCycleWithoutOriginStartsFreshAtT4() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.begin("t4", 1_000)
        probe.mark("t5", 1_050)
        probe.mark("t9", 2_000) // kết thúc chu kỳ 1 (destroy)
        probe.begin("t4", 47_000) // chu kỳ 2
        probe.mark("t5", 47_050)

        assertEquals(
            listOf("t4 gốc (1000)", "t5 +50ms (1050)", "t9 +950ms (2000)", "t4 gốc (47000)", "t5 +50ms (47050)"),
            lines
        )
    }

    /** t4s tách chặng releasePad/clock.pause khỏi saveSram: t4→t4s và t4s→t5 đo riêng. */
    @Test
    fun t4sSeparatesSaveSramFromPreWork() {
        val lines = mutableListOf<String>()
        val probe = PauseProbe { lines += it }

        probe.begin("t4", 1_000)
        probe.mark("t4s", 1_120)
        probe.mark("t5", 1_400)

        assertEquals(listOf("t4 gốc (1000)", "t4s +120ms (1120)", "t5 +280ms (1400)"), lines)
    }

    /** Sink hỏng (ví dụ crumb lỗi ghi tệp) bị nuốt: vòng đời game không được đổi vì đo lường. */
    @Test
    fun failingSinkDoesNotBreakTheChain() {
        val lines = mutableListOf<String>()
        var calls = 0
        val probe = PauseProbe {
            calls++
            if (calls == 1) throw IllegalStateException("crumb lỗi")
            lines += it
        }

        probe.begin("t4", 10)
        probe.mark("t5", 20)

        assertEquals(listOf("t5 +10ms (20)"), lines)
    }
}
