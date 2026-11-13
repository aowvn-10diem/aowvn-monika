package vn.aow.monika.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phạm vi chạy việc PDF: **không** là con của coroutine gọi, nên hết hạn là nơi gọi được trả
 * về ngay — kể cả khi lệnh `PdfRenderer` bên dưới còn đang chạy đồng bộ (không cắt được).
 * [PdfRenderThread] bảo đảm các việc này xếp hàng trên một luồng duy nhất.
 */
private val renderScope = CoroutineScope(SupervisorJob() + PdfRenderThread.dispatcher)

/**
 * Chạy [block] có thời hạn [timeoutMs] trên luồng PDF.
 *
 * - Xong trong hạn → trả kết quả.
 * - Quá hạn → trả `null` **ngay** (không chờ worker đồng bộ kết thúc), huỷ job; nếu kết quả
 *   về muộn thì gọi [close] đúng một lần trên chính kết quả đó (không rò bitmap/page/descriptor).
 * - Nơi gọi **bị huỷ** (đổi trang, rời màn) → vẫn dọn kết quả về muộn như trên, rồi ném tiếp
 *   `CancellationException` (không nuốt huỷ, không rò tài nguyên).
 * - [block] ném lỗi → ném tiếp cho nơi gọi (không nuốt). Lỗi chỉ về **muộn** (sau hạn hoặc sau
 *   khi caller bị huỷ) thì báo qua [onLateError] thay vì im lặng.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Deferred.getCompleted()
internal suspend fun <T : Any> withDeadlineAndCloseOnLate(
    timeoutMs: Long,
    close: (T) -> Unit,
    onLateError: (Throwable) -> Unit,
    block: () -> T,
): T? {
    val result = CompletableDeferred<T>()
    // true = kết quả đã có chủ (được giữ để dùng, hoặc đã đóng) → không dọn lần hai.
    val claimed = AtomicBoolean(false)
    fun closeOnce(value: T) {
        if (!claimed.compareAndSet(false, true)) return
        try {
            close(value)
        } catch (t: Throwable) {
            onLateError(t)
        }
    }

    /** Dọn kết quả/lỗi về muộn — không suspend nên chạy được cả trong `finally` khi bị huỷ. */
    fun settleLate() {
        result.invokeOnCompletion { cause ->
            if (cause == null) {
                // Đã xong (kể cả trước lúc gắn handler): đóng ngay, không giữ.
                try {
                    closeOnce(result.getCompleted())
                } catch (t: Throwable) {
                    onLateError(t)
                }
            } else {
                onLateError(cause) // lỗi về muộn: báo, không im lặng
            }
        }
    }

    // Việc PDF chạy trên luồng riêng, không phải con của coroutine này — nhờ vậy deadline
    // không bị "níu" bởi một lệnh đồng bộ đang chạy dở.
    val job = renderScope.launch {
        try {
            result.complete(block())
        } catch (t: Throwable) {
            result.completeExceptionally(t)
        }
    }

    // false = kết quả/lỗi CHƯA tới tay caller (huỷ / quá hạn) → còn phải dọn ở finally.
    var callerOwnsOutcome = false
    try {
        val value = withTimeoutOrNull(timeoutMs) { result.await() }
        if (value != null) {
            claimed.set(true) // giữ để dùng, không đóng
            return value
        }
        job.cancel()
        return null // quá hạn: caller không giữ gì, phần dọn nằm ở finally
    } catch (c: CancellationException) {
        // Caller bị huỷ: worker vẫn có thể trả kết quả/lỗi muộn → dọn ở finally, rồi ném tiếp.
        job.cancel()
        throw c
    } catch (t: Throwable) {
        callerOwnsOutcome = true // lỗi trong hạn đã tới caller → không báo trùng
        throw t
    } finally {
        if (!callerOwnsOutcome) settleLate()
    }
}

/**
 * Cổng dựng trang dùng chung cho màn đọc:
 *
 * 1. **Tuần tự** — mọi lần dựng trang đi qua [mutex] và đều chạy trên [PdfRenderThread]; quá hạn
 *    thì cổng nhả ra, nhưng lần dựng kế tiếp vẫn chỉ chạy **sau** lần đang dở (cùng một luồng).
 * 2. **Có hạn thật** — quá [timeoutMs] là trả `null` ngay, không chờ worker đồng bộ; kết quả về
 *    muộn bị đóng qua [withDeadlineAndCloseOnLate] — kể cả khi caller đã bị huỷ.
 */
internal class PageRenderGate(
    private val mutex: Mutex = Mutex(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    suspend fun <T : Any> render(close: (T) -> Unit, block: () -> T): T? =
        mutex.withLock {
            withDeadlineAndCloseOnLate(
                timeoutMs = timeoutMs,
                close = close,
                // Lỗi chỉ về muộn (sau hạn / sau khi caller bị huỷ): vẫn phải vào Diagnostics.
                onLateError = { ReaderDiag.report("dung-trang-loi", it) },
                block = block,
            )
        }

    companion object {
        /** Dựng 1 trang quá lâu thì bỏ, không treo màn. */
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
