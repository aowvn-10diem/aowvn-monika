package vn.aow.monika.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * V142: kích thước bitmap khi dựng trang — rộng luôn ≤ cap 1600 px (kể cả trang nguồn rất rộng)
 * và giữ đúng tỉ lệ trang. Đây là chỗ từng cho trang > 16.000 px tạo bitmap vượt cap vì ép
 * scale tối thiểu 0.1.
 */
class PdfRenderSizeTest {

    @Test fun normalPageIsUpscaledTwiceForCrispText() {
        val (width, height) = PdfRendererBook.renderSize(300, 400)
        assertEquals(600, width)
        assertEquals(800, height)
    }

    @Test fun widePageNeverExceedsTheWidthCap() {
        // Trang nguồn 20.000 px: cách cũ (scale >= 0.1) cho bitmap 2.000 px → vượt cap 1.600.
        val (width, height) = PdfRendererBook.renderSize(20_000, 30_000)
        assertEquals("Phải chặn đúng ở cap", PdfRendererBook.MAX_RENDER_WIDTH, width)
        assertTrue("Rộng phải ≤ cap", width <= PdfRendererBook.MAX_RENDER_WIDTH)
        assertEquals("Cao phải theo đúng tỉ lệ 20000×30000", 2_400, height)
    }

    @Test fun extremeWidthIsStillCapped() {
        val (width, height) = PdfRendererBook.renderSize(Int.MAX_VALUE, 1_000)
        assertEquals(PdfRendererBook.MAX_RENDER_WIDTH, width)
        assertTrue("Cao phải dương", height >= 1)
    }

    @Test fun aspectRatioIsKept() {
        for ((srcW, srcH) in listOf(300 to 400, 1_600 to 900, 12_345 to 777, 20_000 to 30_000)) {
            val (width, height) = PdfRendererBook.renderSize(srcW, srcH)
            val expected = srcW.toDouble() / srcH
            val actual = width.toDouble() / height
            assertTrue(
                "Tỉ lệ phải giữ: $srcW×$srcH → $width×$height (lệch ${abs(expected - actual)})",
                abs(expected - actual) < 0.01 * expected,
            )
        }
    }

    @Test fun zeroDimensionsDoNotCrash() {
        val (width, height) = PdfRendererBook.renderSize(0, 0)
        assertTrue("Rộng phải ≥ 1", width >= 1)
        assertTrue("Cao phải ≥ 1", height >= 1)
    }
}
