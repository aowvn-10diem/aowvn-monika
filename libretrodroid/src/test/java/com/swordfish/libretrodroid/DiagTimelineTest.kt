/*
 * Aow Monika: test JUnit thuần (không Android/Robolectric) cho phép đo pause/destroy.
 * Mục tiêu: số ms giữa hai mốc và dòng crumb phải tất định để đọc được trong báo cáo ANR
 * (#350/#318, docs/loi/melondsds-anr-1313.md §5.2).
 */
package com.swordfish.libretrodroid

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagTimelineTest {

    private fun mark(label: String, at: Long) = DiagTimeline.Mark(label, at)

    /** Ca 1: chỉ có MỘT mốc (ví dụ pause() chết trước khi onPause() trả về) → không có mốc trước. */
    @Test
    fun singleMarkHasNoDeltaAndKeepsAbsoluteTime() {
        assertEquals("t1 gốc (123456)", DiagTimeline.format(null, mark("t1", 123456)))
        assertEquals(0L, DiagTimeline.deltaMs(null, mark("t1", 123456)))
    }

    /** Ca 2: hai mốc bình thường → hiệu dương, phần tuyệt đối giữ nguyên. */
    @Test
    fun twoMarksReportDeltaInMs() {
        val t1 = mark("t1", 1_000)
        val t2 = mark("t2", 1_056)

        assertEquals(56L, DiagTimeline.deltaMs(t1, t2))
        assertEquals("t2 +56ms (1056)", DiagTimeline.format(t1, t2))
    }

    /** Ca 3: mốc LÙI thời gian (onDestroy trước onPause của lần sau, hoặc đồng hồ nhảy) → delta kẹp 0, âm được ghi rõ. */
    @Test
    fun backwardsMarkIsClampedToZeroAndReported() {
        val t7 = mark("t7", 5_000)
        val t8 = mark("t8", 4_200)

        assertEquals(0L, DiagTimeline.deltaMs(t7, t8))
        assertEquals("t8 +0ms lùi -800ms (4200)", DiagTimeline.format(t7, t8))
    }

    /** Ca 4: cả vệt — mỗi dòng so với mốc NGAY TRƯỚC, gồm chặng chờ SRAM 1 500 ms. */
    @Test
    fun fullTraceChainsEachMarkToThePreviousOne() {
        val lines = DiagTimeline.formatAll(
            listOf(mark("t7", 10_000), mark("t8", 11_500), mark("t9", 11_520))
        )

        assertEquals(
            listOf("t7 gốc (10000)", "t8 +1500ms (11500)", "t9 +20ms (11520)"),
            lines
        )
    }

    /** Ca 5: vệt một mốc qua formatAll (nhánh sramJob == null) không sinh dòng rỗng. */
    @Test
    fun singleMarkTraceFormatsOneLine() {
        assertEquals(listOf("t4 gốc (77)"), DiagTimeline.formatAll(listOf(mark("t4", 77))))
        assertEquals(emptyList<String>(), DiagTimeline.formatAll(emptyList()))
    }

    /** Ca 6: có mốc `t0` (onActivityPrePaused) → t1 mang ms so với t0, không phải "gốc". */
    @Test
    fun firstMarkUsesOriginFromPrePaused() {
        assertEquals("t1 +12ms (1012)", DiagTimeline.formatFirst(1_000, mark("t1", 1_012)))
        assertEquals("t1 +0ms (1012)", DiagTimeline.formatFirst(1_012, mark("t1", 1_012)))
    }

    /** Ca 7: API 26–28 không có t0 → ghi rõ "t0 không đo được" thay vì coi t1 là gốc. */
    @Test
    fun missingOriginIsReportedAsNotMeasurable() {
        assertEquals("t1 gốc (1012) t0 không đo được", DiagTimeline.formatFirst(null, mark("t1", 1_012)))
        assertEquals("t1 gốc (1012) t0 không đo được", DiagTimeline.formatFirst(0L, mark("t1", 1_012)))
    }

    /** Ca 8: t0 "lùi" so với t1 (đồng hồ nhảy/giá trị cũ) bị loại, không sinh hiệu âm. */
    @Test
    fun originAfterMarkIsTreatedAsNotMeasurable() {
        assertEquals("t1 gốc (900) t0 không đo được", DiagTimeline.formatFirst(1_000, mark("t1", 900)))
    }
}
