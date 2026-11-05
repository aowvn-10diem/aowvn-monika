package vn.aow.monika.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * V142 — Luna 4 + Luna Ultra + Luna 5 (#457):
 * 1. Mọi lời gọi renderer (dựng trang, đọc số trang, đóng) chạy **tuần tự trên một luồng**.
 * 2. Hết hạn là trả về **ngay**, không chờ lệnh `PdfRenderer` đồng bộ đang chạy dở; kết quả về
 *    muộn vẫn bị đóng (không rò bitmap/page).
 * 3. Caller **bị huỷ** (đổi trang/rời màn) vẫn dọn kết quả muộn và báo lỗi muộn qua [ReaderDiag].
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

    /** Trang dựng trên luồng PDF riêng, không phải luồng gọi. */
    @Test fun renderRunsOnTheDedicatedPdfThread() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 5_000)
        val (name, onPdfThread) = gate.render<Pair<String, Boolean>>(close = { }) {
            Thread.currentThread().name to PdfRenderThread.onPdfThread()
        }!!
        assertTrue("Phải dựng trên luồng nền riêng, nhận: $name", name.startsWith("pdf-render"))
        assertTrue("Phải đang ở luồng PDF", onPdfThread)
    }

    /**
     * Luna Ultra: deadline phải **thật** — worker đồng bộ còn chạy (1,2 s) mà hàm phải trả về
     * quanh mốc 100 ms, không chờ worker xong.
     */
    @Test fun timeoutReturnsImmediatelyEvenWhileBlockingWorkStillRuns() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 100)
        val closed = AtomicInteger(0)
        val finished = AtomicInteger(0)

        val start = System.nanoTime()
        val result = gate.render(close = { closed.incrementAndGet() }) {
            Thread.sleep(1_200) // lệnh PDF đồng bộ, không cắt được
            finished.incrementAndGet()
            "late-page"
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertNull("Quá hạn thì trả null", result)
        assertTrue("Phải trả về gần hạn 100 ms, thực tế $elapsedMs ms", elapsedMs < 600)
        assertEquals("Lúc trả về worker còn đang chạy", 0, finished.get())

        // Worker hoàn tất muộn: đóng đúng 1 lần, không làm hỏng trạng thái.
        val deadline = System.currentTimeMillis() + 5_000
        while (finished.get() == 0 && System.currentTimeMillis() < deadline) delay(20)
        delay(100)
        assertEquals(1, finished.get())
        assertEquals("Kết quả về muộn phải bị đóng đúng 1 lần", 1, closed.get())
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

    /**
     * Luna Ultra: `pageCount`/`close` cũng đi qua [PdfRenderThread] nên không được chen vào lúc
     * trang đang dựng — đo bằng bộ đếm "đang bận" lớn nhất.
     */
    @Test fun pageCountAndCloseNeverOverlapPageRendering() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 10_000)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val calls = AtomicInteger(0)

        fun enter() {
            val now = active.incrementAndGet()
            maxActive.updateAndGet { prev -> maxOf(prev, now) }
        }

        fun leave() {
            active.decrementAndGet()
        }

        // Trang đang dựng (chậm) trên luồng PDF...
        val rendering = async {
            gate.render(close = { _: String -> }) {
                PdfRenderThread.call {
                    enter()
                    Thread.sleep(200)
                    leave()
                    "page"
                }
            }
        }
        delay(50) // để trang bắt đầu dựng

        // ...trong lúc đó nhiều luồng khác đọc số trang / đóng sách.
        val readers = (1..4).map {
            thread {
                PdfRenderThread.call {
                    enter()
                    Thread.sleep(20)
                    leave()
                    calls.incrementAndGet()
                }
            }
        }
        readers.forEach { it.join() }

        assertSame("page", rendering.await())
        assertEquals("Mọi lượt đọc/đóng phải chạy xong", 4, calls.get())
        assertEquals("Không được chồng lên lúc dựng trang", 1, maxActive.get())
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

    /**
     * Luna 5 (#457): caller bị huỷ khi đang chờ (đổi trang) mà worker trả **kết quả** muộn thì
     * kết quả đó vẫn phải được đóng đúng 1 lần — không rò bitmap, không crash.
     * Dùng latch, không `Thread.sleep`.
     */
    @Test fun canceledCallerStillClosesLateResult() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 10_000)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)

        val caller = launch {
            gate.render<String>(close = { closed.countDown() }) {
                started.countDown()
                release.await() // worker còn chạy khi caller bị huỷ
                "late-page"
            }
        }

        assertTrue("Worker phải bắt đầu dựng", started.await(5, TimeUnit.SECONDS))
        caller.cancelAndJoin() // đổi trang → produceState bị huỷ
        assertTrue("Caller phải đã bị huỷ", caller.isCancelled)

        release.countDown() // worker xong muộn
        assertTrue("Kết quả về muộn phải bị đóng", closed.await(5, TimeUnit.SECONDS))
    }

    /**
     * Luna 5 (#457): caller bị huỷ rồi worker ném **lỗi** muộn → lỗi phải vào [ReaderDiag],
     * không được im lặng. Dùng latch, không `Thread.sleep`.
     */
    @Test fun canceledCallerStillReportsLateFailure() = runBlocking {
        val gate = PageRenderGate(timeoutMs = 10_000)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reported = CompletableDeferred<Pair<String, Throwable?>>()
        val previous = ReaderDiag.onError
        ReaderDiag.onError = { what, t -> reported.complete(what to t) }
        try {
            val caller = launch {
                gate.render<String>(close = { }) {
                    started.countDown()
                    release.await() // worker còn chạy khi caller bị huỷ
                    throw IllegalStateException("hỏng trang muộn")
                }
            }

            assertTrue("Worker phải bắt đầu dựng", started.await(5, TimeUnit.SECONDS))
            caller.cancelAndJoin()
            assertTrue("Caller phải đã bị huỷ", caller.isCancelled)

            release.countDown() // lỗi về muộn
            val (what, failure) = withTimeout(5_000) { reported.await() }
            assertEquals("Lỗi muộn phải báo bằng mã, không kèm tên tệp", "dung-trang-loi", what)
            assertTrue("Phải kèm lỗi gốc, nhận: $failure", failure is IllegalStateException)
        } finally {
            ReaderDiag.onError = previous
        }
    }
}
