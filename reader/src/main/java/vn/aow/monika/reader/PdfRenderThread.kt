package vn.aow.monika.reader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Một luồng nền duy nhất cho **mọi** lời gọi `PdfRenderer`: mở/dựng trang, đọc số trang, đóng.
 *
 * `PdfRenderer` không thread-safe, nên chỉ khoá là chưa đủ: `pageCount`/`close` có thể chen vào
 * giữa lúc trang đang dựng. Ở đây mọi lời gọi đều xếp vào **cùng một luồng** (single-thread
 * executor) và cùng một [lock], nên không bao giờ có hai lời gọi renderer chạy chồng nhau.
 *
 * Luồng là daemon và dùng chung cho cả tiến trình: tại một thời điểm chỉ có một renderer hoạt
 * động, đúng như `PdfRenderer` yêu cầu.
 */
internal object PdfRenderThread {

    /** Khoá chung cho mọi lời gọi renderer (giữ cùng executor để không chồng lên nhau). */
    val lock = Any()

    private val worker = AtomicReference<Thread?>(null)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, THREAD_NAME).apply {
            isDaemon = true
            worker.set(this)
        }
    }

    /** Dispatcher của đúng luồng trên — dựng trang chạy ở đây. */
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /** Đang chạy trên luồng PDF? (Gọi lồng nhau thì chạy luôn, không tự chờ mình.) */
    fun onPdfThread(): Boolean = Thread.currentThread() === worker.get()

    /**
     * Chạy [block] trên luồng PDF và **chờ** kết quả — dùng cho việc ngắn như đọc số trang/đóng.
     * Nếu đang ở luồng PDF thì chạy ngay tại chỗ (tránh tự khoá); nếu không thì xếp hàng sau
     * việc đang chạy nên không bao giờ chồng lên lần dựng trang.
     */
    fun <T> call(block: () -> T): T {
        if (onPdfThread()) return synchronized(lock) { block() }
        return executor.submit(Callable { synchronized(lock) { block() } }).get()
    }

    /**
     * Xếp [block] vào luồng PDF **không chờ** — dùng khi rời màn: `close` phải xếp sau lần dựng
     * trang đang chạy chứ không được làm treo luồng UI.
     */
    fun submit(block: () -> Unit) {
        if (onPdfThread()) {
            synchronized(lock) { block() }
        } else {
            executor.execute { synchronized(lock) { block() } }
        }
    }

    private const val THREAD_NAME = "pdf-render"
}
