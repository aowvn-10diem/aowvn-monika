package vn.aow.monika.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Điểm vào công khai của module :reader cho app: mở tệp PDF, không ném lỗi ra ngoài.
 * (Lớp [PdfRendererBook] để internal để app/test không phụ thuộc chi tiết Android.)
 */
object PdfBookOpener {
    fun open(file: File): Result<PdfBook> = PdfRendererBook.open(file)
}

/**
 * Đọc PDF bằng [PdfRenderer] có sẵn của Android (API 21+): không thêm thư viện ngoài.
 * Mọi lỗi đều đổi thành [ReaderException] có loại rõ ràng để màn đọc không bao giờ văng app.
 */
internal class PdfRendererBook private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : PdfBook {

    override val pageCount: Int get() = runCatching { renderer.pageCount }.getOrDefault(0)

    override fun openPage(index: Int): ReaderPage {
        val safeIndex = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val page = renderer.openPage(safeIndex)
        return page.use {
            // PdfRenderer phóng to/thu nhỏ nội dung theo kích thước bitmap (transform = null).
            // Dựng ở 2x bề rộng trang để chữ nét trên màn hình điện thoại, chặn trên 1600px.
            val scale = (MAX_RENDER_WIDTH / it.width.coerceAtLeast(1).toFloat()).coerceIn(1f, 2f)
            val width = (it.width * scale).toInt().coerceAtLeast(1)
            val height = (it.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ReaderPage(bitmap)
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    companion object {
        /** Bề rộng bitmap tối đa khi dựng 1 trang. */
        private const val MAX_RENDER_WIDTH = 1600f

        /**
         * Mở tệp PDF. Không ném lỗi ra ngoài: trả [Result] với [ReaderException] đã phân loại
         * (thiếu tệp / có mật khẩu / hỏng / máy không dựng được).
         */
        fun open(file: File): Result<PdfBook> {
            if (!file.isFile || !file.canRead()) {
                return Result.failure(ReaderException(ReaderError.MISSING))
            }
            var opened: ParcelFileDescriptor? = null
            return try {
                opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(opened)
                val book = PdfRendererBook(opened, renderer)
                if (book.pageCount <= 0) {
                    // Tệp 0 trang = hỏng/giả PDF; không giữ handle.
                    book.close()
                    Result.failure(ReaderException(ReaderError.CORRUPT))
                } else {
                    Result.success(book)
                }
            } catch (t: Throwable) {
                runCatching { opened?.close() }
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
