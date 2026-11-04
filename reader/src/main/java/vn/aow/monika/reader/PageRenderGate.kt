package vn.aow.monika.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Chạy [block] có thời hạn [timeoutMs].
 *
 * - Xong trong hạn → trả kết quả.
 * - Quá hạn → trả `null`, huỷ coroutine đang chạy; nếu lần chạy **vẫn xong muộn** thì gọi
 *   [close] ngay trên kết quả đó để **không rò tài nguyên** (page/bitmap/file descriptor).
 * - [block] ném lỗi → ném tiếp cho nơi gọi (không nuốt).
 */
internal suspend fun <T : Any> withDeadlineAndCloseOnLate(
    timeoutMs: Long,
    close: (T) -> Unit,
    block: () -> T,
): T? = coroutineScope {
    val result = CompletableDeferred<T>()
    val settled = AtomicBoolean(false)
    fun closeOnce(value: T) {
        if (settled.compareAndSet(false, true)) close(value)
    }
    val worker = launch(Dispatchers.IO) {
        try {
            result.complete(block())
        } catch (t: Throwable) {
            result.completeExceptionally(t)
        }
    }
    val value = withTimeoutOrNull(timeoutMs) { result.await() }
    if (value == null) {
        worker.cancel()
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
    value
}

/**
 * Cổng dựng trang dùng chung cho màn đọc:
 *
 * 1. **Tuần tự** — `PdfRenderer` không thread-safe, nên mọi lần mở/dựng trang đi qua [mutex]:
 *    không bao giờ có 2 trang cùng mở trên một renderer.
 * 2. **Có hạn** — quá [timeoutMs] thì trả `null` và đóng kết quả nếu nó về muộn.
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
