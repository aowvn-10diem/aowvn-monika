package vn.aow.monika.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * V142: kích thước bitmap khi dựng trang — rộng ≤ 1600 px **và** cao ≤ 2400 px (kể cả trang nguồn
 * rất rộng/rất cao), tổng điểm ảnh ≤ 1600×2400, và giữ đúng tỉ lệ trang (không méo).
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

    /**
     * Luna 3 (PR #457): trang 1000×500000 từng tạo bitmap 1600×800000 (~5,1 GB ARGB_8888) vì chỉ
     * chặn bề rộng. Nay chiều cao bị chặn 2400 px và tổng điểm ảnh ≤ 1600×2400.
     */
    @Test fun extremelyTallPageIsCappedByHeightAndTotalPixels() {
        val (width, height) = PdfRendererBook.renderSize(1_000, 500_000)
        assertEquals("Cao phải chặn đúng ở cap", PdfRendererBook.MAX_RENDER_HEIGHT, height)
        assertTrue("Rộng phải ≥ 1", width >= 1)
        assertTrue("Rộng phải ≤ cap", width <= PdfRendererBook.MAX_RENDER_WIDTH)
        assertTrue(
            "Tổng điểm ảnh phải ≤ ${PdfRendererBook.MAX_RENDER_PIXELS} (thực tế ${width.toLong() * height})",
            width.toLong() * height <= PdfRendererBook.MAX_RENDER_PIXELS,
        )
    }

    /** Trang cao nhưng tỉ lệ còn hợp lý: hạ theo chiều cao và vẫn giữ đúng tỉ lệ. */
    @Test fun tallPageKeepsItsAspectRatio() {
        val (width, height) = PdfRendererBook.renderSize(1_000, 20_000)
        assertEquals(PdfRendererBook.MAX_RENDER_HEIGHT, height)
        assertEquals("Rộng tính lại theo tỉ lệ", 120, width)
        assertEquals(0.05, width.toDouble() / height, 0.001)
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
            assertTrue(
                "Mọi cỡ trang đều phải ≤ tổng điểm ảnh cho phép",
                width.toLong() * height <= PdfRendererBook.MAX_RENDER_PIXELS,
            )
        }
    }

    @Test fun zeroDimensionsDoNotCrash() {
        val (width, height) = PdfRendererBook.renderSize(0, 0)
        assertTrue("Rộng phải ≥ 1", width >= 1)
        assertTrue("Cao phải ≥ 1", height >= 1)
    }
}
