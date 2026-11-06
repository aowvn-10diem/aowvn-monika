package vn.aow.monika.reader

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * V142 — đội duyệt (#457): trang dựng lỗi từng bỏ bitmap ARGB_8888 (~15 MB) cho GC.
 * [renderPageInto] phải giải phóng bitmap **đúng một lần** ở nhánh lỗi, và **không** đụng vào
 * bitmap đã giao cho caller ở nhánh thành công.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfPageBitmapTest {

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)

    /** Bọc [Bitmap.recycle] thật + đếm số lần gọi (để bắt cả trường hợp recycle 2 lần). */
    private fun countingRecycle(counter: AtomicInteger): (Bitmap) -> Unit = { bitmap ->
        bitmap.recycle()
        counter.incrementAndGet()
    }

    @Test fun renderErrorRecyclesTheBitmapExactlyOnceAndRethrows() {
        val bitmap = newBitmap()
        val recycles = AtomicInteger(0)
        val failure = IllegalStateException("hỏng trang")

        val thrown = runCatching {
            renderPageInto(bitmap, recycle = countingRecycle(recycles)) { throw failure }
        }.exceptionOrNull()

        assertSame("Phải ném lại đúng lỗi gốc, nhận: $thrown", failure, thrown)
        assertEquals("Bitmap trang lỗi phải được giải phóng đúng 1 lần", 1, recycles.get())
        assertTrue("Bitmap phải ở trạng thái đã recycle", bitmap.isRecycled)
    }

    @Test fun successfulRenderHandsTheBitmapToTheCallerWithoutRecycling() {
        val bitmap = newBitmap()
        val recycles = AtomicInteger(0)
        val drawn = AtomicInteger(0)

        val returned = renderPageInto(bitmap, recycle = countingRecycle(recycles)) { target ->
            assertSame("Phải vẽ vào đúng bitmap đã cấp phát", bitmap, target)
            drawn.incrementAndGet()
        }

        assertSame("Thành công thì trả đúng bitmap cho caller", bitmap, returned)
        assertEquals("Bitmap phải được vẽ đúng 1 lần", 1, drawn.get())
        assertEquals("Bitmap đã giao cho caller thì không được recycle", 0, recycles.get())
        assertFalse("Bitmap còn dùng được sau khi dựng xong", bitmap.isRecycled)
    }

    /** Lỗi khi chính việc giải phóng (hiếm) không được che lỗi gốc, nhưng phải vào [ReaderDiag]. */
    @Test fun recycleFailureDoesNotHideTheOriginalRenderError() {
        val bitmap = newBitmap()
        val failure = IllegalArgumentException("render hỏng")
        val reports = mutableListOf<String>()
        val previous = ReaderDiag.onError
        ReaderDiag.onError = { what, _ -> reports += what }
        try {
            val thrown = runCatching {
                renderPageInto(bitmap, recycle = { throw IllegalStateException("recycle hỏng") }) {
                    throw failure
                }
            }.exceptionOrNull()

            assertSame("Lỗi gốc phải được giữ nguyên", failure, thrown)
            assertTrue(
                "Lỗi giải phóng phải vào ReaderDiag, có: $reports",
                reports.any { it == "giai-phong-trang-loi" },
            )
        } finally {
            ReaderDiag.onError = previous
        }
    }
}
