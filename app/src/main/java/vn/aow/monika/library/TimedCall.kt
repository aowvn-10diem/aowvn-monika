package vn.aow.monika.library

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Chạy [block] chặn (đọc đĩa...) ở luồng nền riêng và chờ tối đa [timeoutMs]. Quá hạn thì ngừng chờ và trả null
 * (V26: `withTimeoutOrNull` của coroutine không cắt được một lần đọc đĩa treo). Luồng nền là daemon nên không giữ tiến trình.
 * Mọi lỗi → null. Hàm này CHẶN luồng gọi tới tối đa [timeoutMs]: gọi ở luồng nền (Dispatchers.IO).
 */
object TimedCall {
    fun <T : Any> run(timeoutMs: Long, name: String = "timed-call", block: () -> T?): T? {
        val task = FutureTask<T?> { block() }
        Thread(task, name).apply { isDaemon = true }.start()
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true); null
        } catch (e: Exception) {
            null
        }
    }
}
