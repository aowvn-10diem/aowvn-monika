package vn.aow.monika.reader

import android.graphics.Bitmap

/** Sách giả cho test giao diện: đếm số lần dựng trang, có thể cố tình lỗi ở 1 trang. */
internal class FakeBook(
    override val pageCount: Int,
    private val failOnPage: Int? = null,
) : PdfBook {
    var openedPages = mutableListOf<Int>()
    var closed = false

    override fun openPage(index: Int): ReaderPage {
        if (index == failOnPage) throw IllegalStateException("trang hỏng")
        openedPages += index
        return ReaderPage(Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888))
    }

    override fun close() { closed = true }
}
