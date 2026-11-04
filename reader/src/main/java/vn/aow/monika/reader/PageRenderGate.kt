package vn.aow.monika.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
 * - [block] ném lỗi → ném tiếp cho nơi gọi (không nuốt).
 */
internal suspend fun <T : Any> withDeadlineAndCloseOnLate(
    timeoutMs: Long,
    close: (T) -> Unit,
    block: () -> T,
): T? {
    val result = CompletableDeferred<T>()
    val settled = AtomicBoolean(false)
    fun closeOnce(value: T) {
        if (settled.compareAndSet(false, true)) close(value)
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
    val value = withTimeoutOrNull(timeoutMs) { result.await() }
    if (value == null) {
        job.cancel()
        if (result.isCompleted) {
            // Xong đúng lúc quá hạn: đóng luôn, không giữ.
            runCatching { result.getCompleted() }.getOrNull()?.let { closeOnce(it) }
        } else {
            // Xong muộn hơn nữa: đóng ngay khi có kết quả.
            result.invokeOnCompletion { cause ->
                if (cause == null) {
                    runCatching { result.getCompleted() }.getOrNull()?.let { closeOnce(it) }
                }
            }
        }
    } else {
        settled.set(true) // kết quả được giữ để dùng, không đóng
    }
    return value
}

/**
 * Cổng dựng trang dùng chung cho màn đọc:
 *
 * 1. **Tuần tự** — mọi lần dựng trang đi qua [mutex] và đều chạy trên [PdfRenderThread]; quá hạn
 *    thì cổng nhả ra, nhưng lần dựng kế tiếp vẫn chỉ chạy **sau** lần đang dở (cùng một luồng).
 * 2. **Có hạn thật** — quá [timeoutMs] là trả `null` ngay, không chờ worker đồng bộ; kết quả về
 *    muộn bị đóng qua [withDeadlineAndCloseOnLate].
 */
internal class PageRenderGate(
    private val mutex: Mutex = Mutex(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    suspend fun <T : Any> render(close: (T) -> Unit, block: () -> T): T? =
        mutex.withLock { withDeadlineAndCloseOnLate(timeoutMs, close, block) }

    companion object {
        /** Dựng 1 trang quá lâu thì bỏ, không treo màn. */
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
