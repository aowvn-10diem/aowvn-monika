package vn.aow.monika.reader

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * V142 — Luna 4 (#457):
 * 1. Renderer chỉ dựng **một trang tại một thời điểm** (mutex), không mở 2 trang song song.
 * 2. Quá hạn thì bỏ; kết quả về **muộn** phải bị đóng ngay (không rò bitmap/page).
 */
class PageRenderGateTest {

    @Test fun rendersAreSerializedEvenWhenCalledConcurrently() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 10_000)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val done = AtomicInteger(0)

        val jobs = (1..2).map {
            async {
                gate.render(close = { _: String -> }) {
                    val now = active.incrementAndGet()
                    maxActive.updateAndGet { prev -> maxOf(prev, now) }
                    Thread.sleep(120) // giả lập dựng trang chậm
                    active.decrementAndGet()
                    done.incrementAndGet()
                    "page-$it"
                }
            }
        }
        val results = jobs.awaitAll()

        assertEquals("Cả hai phải dựng xong", 2, done.get())
        assertEquals("Không được dựng 2 trang cùng lúc", 1, maxActive.get())
        assertEquals(setOf("page-1", "page-2"), results.toSet())
    }

    @Test fun resultFinishingAfterTimeoutIsClosed() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 50)
        val closed = AtomicInteger(0)
        val finished = AtomicInteger(0)

        val result = gate.render(close = { closed.incrementAndGet() }) {
            Thread.sleep(250) // xong sau khi đã quá hạn
            finished.incrementAndGet()
            "late-page"
        }

        assertNull("Quá hạn thì trả null", result)
        // Chờ lần dựng muộn kết thúc rồi mới kiểm: phải đóng đúng 1 lần.
        val deadline = System.currentTimeMillis() + 3_000
        while (finished.get() == 0 && System.currentTimeMillis() < deadline) delay(20)
        delay(100)
        assertEquals(1, finished.get())
        assertEquals("Kết quả về muộn phải bị đóng đúng 1 lần", 1, closed.get())
    }

    @Test fun resultWithinTimeoutIsReturnedAndNotClosed() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 5_000)
        val closed = AtomicInteger(0)
        val page = "page"
        val result = gate.render(close = { closed.incrementAndGet() }) { page }

        assertSame(page, result)
        assertEquals("Kết quả hiển thị thì không bị đóng", 0, closed.get())
    }

    @Test fun blockFailureIsPropagatedAndNotSwallowed() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 5_000)
        val failure = IllegalStateException("hỏng trang")
        val thrown = runCatching {
            gate.render<String>(close = { }) { throw failure }
        }.exceptionOrNull()
        assertTrue("Lỗi dựng phải ném lại cho màn đọc, nhận: $thrown", thrown is IllegalStateException)
        assertEquals("hỏng trang", thrown?.message)
    }
}
