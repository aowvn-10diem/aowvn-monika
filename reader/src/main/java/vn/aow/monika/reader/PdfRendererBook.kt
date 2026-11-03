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
 * Mọi lỗi đều đổi thành [ReaderException] có loại rõ ràng để màn đọc không bao giờ văng app;
 * lỗi đã xử lý được báo qua [ReaderDiag] (app gắn vào `Diagnostics`).
 */
internal class PdfRendererBook private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : PdfBook {

    override val pageCount: Int
        get() = try {
            renderer.pageCount
        } catch (t: Throwable) {
            // Đọc số trang lỗi → coi như 0 trang (màn đọc sẽ báo "hỏng") và ghi nhận lại.
            ReaderDiag.report("đọc số trang PDF", t)
            0
        }

    override fun openPage(index: Int): ReaderPage {
        val safeIndex = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val page = renderer.openPage(safeIndex)
        return page.use {
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
        try {
            renderer.close()
        } catch (t: Throwable) {
            ReaderDiag.report("đóng PdfRenderer", t)
        }
        try {
            fd.close()
        } catch (t: Throwable) {
            ReaderDiag.report("đóng tệp PDF", t)
        }
    }

    companion object {
        /** Bề rộng bitmap tối đa khi dựng 1 trang (cap thật sự, kể cả trang lớn). */
        internal const val MAX_RENDER_WIDTH = 1600

        /**
         * Kích thước bitmap khi dựng 1 trang: rộng = min(nguồn × 2, [MAX_RENDER_WIDTH]) — **luôn**
         * ≤ cap, kể cả trang nguồn rất rộng; cao tính theo đúng tỉ lệ trang (không méo).
         */
        internal fun renderSize(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
            val w = srcWidth.coerceAtLeast(1)
            val h = srcHeight.coerceAtLeast(1)
            val width = minOf(w.toLong() * 2, MAX_RENDER_WIDTH.toLong()).toInt().coerceAtLeast(1)
            val height = ((width.toDouble() * h) / w).toInt().coerceAtLeast(1)
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
                opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(opened)
                val book = PdfRendererBook(opened, renderer)
                val pages = book.pageCount
                if (pages <= 0) {
                    // Tệp 0 trang = hỏng/giả PDF; không giữ handle.
                    ReaderDiag.report("PDF không có trang nào: ${file.name}")
                    book.close()
                    Result.failure(ReaderException(ReaderError.CORRUPT))
                } else {
                    Result.success(book)
                }
            } catch (t: Throwable) {
                ReaderDiag.report("mở PDF: ${file.name}", t)
                try {
                    opened?.close()
                } catch (c: Throwable) {
                    ReaderDiag.report("đóng tệp PDF sau lỗi mở", c)
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
