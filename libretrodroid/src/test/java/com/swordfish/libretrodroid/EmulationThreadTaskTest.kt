/*
 * Aow Monika (V205): test JUnit thường (không cần Android/Robolectric) cho cầu nối chờ GLThread.
 * Mục tiêu: lời gọi đồng bộ sang GLThread phải có hạn — hết hạn thì nơi gọi nhận giá trị dự phòng,
 * Main Thread không bị treo tới mức ANR (ffdfa861, #318, #529, #533, #535).
 */
package com.swordfish.libretrodroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulationThreadTaskTest {

    /** Lời gọi đã xong trước khi chờ thì phải thấy ngay kết quả, không chờ thêm. */
    @Test
    fun awaitReturnsResultWhenCallAlreadyFinished() {
        val task = EmulationThreadTask<Int>()
        task.complete(42)

        assertTrue("phải báo đã xong", task.await(EmulationThread.CALL_TIMEOUT_MS))
        assertEquals("phải trả đúng kết quả của GLThread", 42, task.result())
    }

    /** GLThread chưa chạy xong trong hạn thì await trả false để nơi gọi dùng giá trị dự phòng. */
    @Test
    fun awaitReturnsFalseWhenCallIsTooSlow() {
        val task = EmulationThreadTask<ByteArray>()

        val startedAt = System.nanoTime()
        val done = task.await(50L)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertFalse("quá hạn thì phải trả false", done)
        assertTrue("không được chờ quá hạn đã đặt ($elapsedMs ms)", elapsedMs < 2_000L)
    }

    /** Hết hạn rồi GLThread mới xong: lần chờ sau vẫn thấy kết quả, không mất dữ liệu. */
    @Test
    fun resultIsAvailableAfterLateCompletion() {
        val task = EmulationThreadTask<Boolean>()

        assertFalse("lần đầu phải quá hạn", task.await(20L))

        task.complete(false)

        assertTrue("lần chờ sau phải thấy lời gọi đã xong", task.await(EmulationThread.CALL_TIMEOUT_MS))
        assertFalse("phải trả đúng kết quả của GLThread", task.result())
    }

    /** Kết quả do thread khác ghi phải được thread đang chờ nhìn thấy (mô phỏng GLThread). */
    @Test
    fun awaitSeesResultFromAnotherThread() {
        val task = EmulationThreadTask<String>()
        val worker = Thread({
            Thread.sleep(20L)
            task.complete("xong")
        }, "GLThread-test")

        worker.start()
        try {
            assertTrue("phải thấy kết quả từ GLThread", task.await(EmulationThread.CALL_TIMEOUT_MS))
            assertEquals("xong", task.result())
        } finally {
            worker.join(EmulationThread.CALL_TIMEOUT_MS)
        }
    }

    /** Bị ngắt giữa lúc chờ: không ném ra ngoài, chờ đủ hạn rồi trả lại cờ ngắt cho thread gọi. */
    @Test
    fun interruptIsRestoredAfterWaiting() {
        val task = EmulationThreadTask<Unit>()

        Thread.currentThread().interrupt()
        val completed = task.await(20L)
        val stillInterrupted = Thread.interrupted() // đọc và xoá cờ để không ảnh hưởng test khác

        assertFalse("quá hạn thì phải trả false", completed)
        assertTrue("phải giữ cờ ngắt cho code phía trên", stillInterrupted)
    }

    /** Nhận đúng thread GL của GLSurfaceView (tên "GLThread <id>") và không nhận Main Thread. */
    @Test
    fun emulationThreadIsDetectedByName() {
        assertTrue(EmulationThread.isEmulationThread("GLThread 1234"))
        assertFalse(EmulationThread.isEmulationThread("main"))
        assertFalse(EmulationThread.isEmulationThread("RenderThread"))
    }

    /** Hạn chờ mặc định không được vượt 2 giây (yêu cầu ANR của V205). */
    @Test
    fun callTimeoutIsWithinAnrBudget() {
        assertTrue("hạn chờ phải ≤ 2000 ms", EmulationThread.CALL_TIMEOUT_MS <= 2_000L)
    }

    /** L1-anr: việc không cần kết quả (đổi biến lõi/shader/tốc độ) chỉ được xếp hàng, không chờ GLThread. */
    @Test
    fun postOnlyQueuesAndNeverWaitsForGlThread() {
        val queued = mutableListOf<() -> Unit>()
        var ran = false

        val startedAt = System.nanoTime()
        EmulationCall.post({ body -> queued += body }) { ran = true }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertFalse("post không được chạy việc trên thread gọi", ran)
        assertEquals("post phải xếp đúng 1 việc", 1, queued.size)
        assertTrue("post không được chờ GLThread ($elapsedMs ms)", elapsedMs < 100L)

        queued.single()()
        assertTrue("GLThread xử lý hàng đợi thì việc vẫn chạy", ran)
    }

    /** Việc cần kết quả mà GLThread trả lời ngay thì lấy đúng kết quả. */
    @Test
    fun runBoundedReturnsResultWhenGlThreadAnswers() {
        val value = EmulationCall.runBounded({ body -> body() }, 500L, { "dự phòng" }) { "kết quả" }
        assertEquals("kết quả", value)
    }

    /** L1-anr: GLThread kẹt (lõi đang giữ coreLock) thì hết hạn trả dự phòng, không chờ tới ANR. */
    @Test
    fun runBoundedFallsBackWhenGlThreadNeverRuns() {
        val queued = mutableListOf<() -> Unit>()
        var ran = false

        val startedAt = System.nanoTime()
        val value = EmulationCall.runBounded({ body -> queued += body }, 50L, { "dự phòng" }) {
            ran = true
            "kết quả"
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals("dự phòng", value)
        assertFalse("việc không chạy trên thread gọi", ran)
        assertTrue("không được chờ quá hạn ($elapsedMs ms)", elapsedMs < 1_000L)
        assertEquals("việc vẫn nằm trong hàng đợi GLThread", 1, queued.size)
        queued.single()()
        assertTrue("GLThread xử lý muộn thì việc vẫn chạy", ran)
    }
}
