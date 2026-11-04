package vn.aow.monika.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Điểm vào công khai của module :reader cho app: mở tệp PDF, không ném lỗi ra ngoài.
 * (Lớp [PdfRendererBook] để internal để app/test không phụ thuộc chi tiết Android.)
 */
object PdfBookOpener {
    fun open(file: File): Result<PdfBook> = PdfRendererBook.open(file)

    /**
     * Mở tệp có thời hạn [timeoutMs]; quá hạn thì trả `null`. Nếu tệp mở xong **muộn** thì sách
     * vừa mở bị **đóng ngay** — không giữ, không rò file descriptor.
     */
    suspend fun openWithin(timeoutMs: Long, file: File): Result<PdfBook>? =
        withDeadlineAndCloseOnLate(timeoutMs, close = { it.getOrNull()?.close() }) { open(file) }
}

/**
 * Đọc PDF bằng [PdfRenderer] có sẵn của Android (API 21+): không thêm thư viện ngoài.
 * Mọi lỗi đều đổi thành [ReaderException] có loại rõ ràng để màn đọc không bao giờ văng app;
 * lỗi đã xử lý được báo qua [ReaderDiag] (app gắn vào `Diagnostics`).
 */
internal class PdfRendererBook private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : PdfBook {

    /**
     * Số trang đọc **một lần** lúc mở, trên luồng PDF và trong khoá (xem [PdfRenderThread]).
     * Getter chỉ trả số đã nhớ nên `pageCount` không bao giờ chạm `PdfRenderer` từ luồng khác.
     */
    private val pages: Int = try {
        PdfRenderThread.call { renderer.pageCount }
    } catch (t: Throwable) {
        // Đọc số trang lỗi → coi như 0 trang (màn đọc sẽ báo "hỏng") và ghi nhận lại.
        ReaderDiag.report("doc-so-trang-loi")
        0
    }

    /** Đóng đúng một lần, dù gọi từ UI hay từ luồng PDF. */
    private val closed = AtomicBoolean(false)

    override val pageCount: Int get() = pages

    override fun openPage(index: Int): ReaderPage = PdfRenderThread.call {
        // PdfRenderer không thread-safe: mọi lời gọi renderer đi qua một luồng và một khoá.
        val safeIndex = index.coerceIn(0, (pages - 1).coerceAtLeast(0))
        val page = renderer.openPage(safeIndex)
        page.use {
            // PdfRenderer phóng/thu nội dung theo kích thước bitmap (transform = null).
            // Kích thước tính từ tỉ lệ trang: tối đa 2x cho chữ nét, và **luôn** chặn bề rộng
            // ở MAX_RENDER_WIDTH — kể cả trang nguồn rất rộng (> 16.000 px).
            val (width, height) = renderSize(it.width, it.height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ReaderPage(bitmap)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        PdfRenderThread.call { closeRendererAndFile() }
    }

    override fun closeLater() {
        if (!closed.compareAndSet(false, true)) return
        // Không chờ: nếu còn trang đang dựng thì close xếp sau nó, luồng UI không bị treo.
        PdfRenderThread.submit { closeRendererAndFile() }
    }

    /** Chỉ được gọi trên luồng PDF, trong khoá. */
    private fun closeRendererAndFile() {
        try {
            renderer.close()
        } catch (t: Throwable) {
            ReaderDiag.report("dong-renderer-loi")
        }
        try {
            fd.close()
        } catch (t: Throwable) {
            ReaderDiag.report("dong-tep-loi")
        }
    }

    companion object {
        /** Bề rộng bitmap tối đa khi dựng 1 trang (cap thật sự, kể cả trang lớn). */
        internal const val MAX_RENDER_WIDTH = 1600

        /** Chiều cao bitmap tối đa — chặn trang cực cao (vd. 1000×500000) khỏi cấp phát bitmap khổng lồ. */
        internal const val MAX_RENDER_HEIGHT = 2400

        /** Trần tổng điểm ảnh (≈ 15 MB ARGB_8888) — luôn suy ra từ 2 cap trên, kiểm cho chắc. */
        internal const val MAX_RENDER_PIXELS = MAX_RENDER_WIDTH * MAX_RENDER_HEIGHT

        /**
         * Kích thước bitmap khi dựng 1 trang: rộng ≤ [MAX_RENDER_WIDTH] **và** cao ≤ [MAX_RENDER_HEIGHT],
         * tổng điểm ảnh ≤ [MAX_RENDER_PIXELS]; tỉ lệ trang luôn giữ (không méo).
         * Trang rất rộng bị hạ theo bề rộng; trang rất cao bị hạ theo chiều cao (rộng thu nhỏ tương ứng).
         */
        internal fun renderSize(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
            val w = srcWidth.coerceAtLeast(1)
            val h = srcHeight.coerceAtLeast(1)
            var width = minOf(w.toLong() * 2, MAX_RENDER_WIDTH.toLong()).toInt().coerceAtLeast(1)
            var height = ((width.toDouble() * h) / w).toInt().coerceAtLeast(1)
            if (height > MAX_RENDER_HEIGHT) {
                // Hạ theo chiều cao rồi tính lại bề rộng để giữ đúng tỉ lệ.
                height = MAX_RENDER_HEIGHT
                width = ((height.toDouble() * w) / h).toInt().coerceIn(1, MAX_RENDER_WIDTH)
            }
            return width to height
        }

        /**
         * Mở tệp PDF. Không ném lỗi ra ngoài: trả [Result] với [ReaderException] đã phân loại
         * (thiếu tệp / có mật khẩu / hỏng / máy không dựng được) và ghi nhận lỗi qua [ReaderDiag].
         */
        fun open(file: File): Result<PdfBook> {
            if (!file.isFile || !file.canRead()) {
                return Result.failure(ReaderException(ReaderError.MISSING))
            }
            var opened: ParcelFileDescriptor? = null
            return try {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                opened = descriptor
                // Tạo renderer cũng là lời gọi PdfRenderer: đi qua luồng PDF như mọi lời gọi khác.
                val renderer = PdfRenderThread.call { PdfRenderer(descriptor) }
                val book = PdfRendererBook(descriptor, renderer)
                val pages = book.pageCount
                if (pages <= 0) {
                    // Tệp 0 trang = hỏng/giả PDF; không giữ handle.
                    ReaderDiag.report("pdf-khong-trang")
                    book.close()
                    Result.failure(ReaderException(ReaderError.CORRUPT))
                } else {
                    Result.success(book)
                }
            } catch (t: Throwable) {
                ReaderDiag.report("mo-pdf-loi")
                try {
                    opened?.close()
                } catch (c: Throwable) {
                    ReaderDiag.report("dong-tep-sau-loi-mo")
                }
                Result.failure(ReaderException(errorFor(t), t))
            }
        }

        /** Đổi lỗi của hệ thống thành loại lỗi người dùng hiểu được. */
        internal fun errorFor(t: Throwable): ReaderError = when (t) {
            is ReaderException -> t.error
            is FileNotFoundException -> ReaderError.MISSING
            // PdfRenderer ném SecurityException khi PDF có mật khẩu.
            is SecurityException -> ReaderError.ENCRYPTED
            is UnsatisfiedLinkError, is NoClassDefFoundError -> ReaderError.UNSUPPORTED
            else -> ReaderError.CORRUPT
        }
    }
}
