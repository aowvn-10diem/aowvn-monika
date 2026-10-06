package vn.aow.monika.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V26: TimedCall cắt được lần đọc chặn (kể cả không phản hồi interrupt), lỗi → null. */
class TimedCallTest {
    @Test fun ketQuaNhanhDuocTraVe() {
        assertEquals("ok", TimedCall.run(1_000) { "ok" })
    }

    @Test fun quaHanThiTraNullDungHanKhongChoBlockXong() {
        val start = System.nanoTime()
        val r = TimedCall.run<String>(150) {
            // Chặn không thể interrupt (giả lập đọc đĩa treo): vòng bận, bỏ qua interrupt.
            val end = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < end) { /* busy */ }
            "late"
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertNull(r)
        assertTrue("phải ngừng chờ sớm, mất ${ms}ms", ms < 2_000)
    }

    @Test fun loiBatKyTraNull() {
        assertNull(TimedCall.run<String>(1_000) { error("boom") })
    }
}
