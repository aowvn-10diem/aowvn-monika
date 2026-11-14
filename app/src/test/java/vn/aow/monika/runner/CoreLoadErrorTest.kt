package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ca kiểm cho [CoreLoadError]: mỗi mã lỗi libretrodroid phải ra câu riêng, có việc để làm. */
class CoreLoadErrorTest {
    private val codes = listOf(0, 1, 2, 3, 4, -1, 99)

    @Test fun everyCodeHasItsOwnText() {
        assertEquals(codes.size, codes.map { CoreLoadError.text(it, "pcsx_rearmed") }.distinct().size)
    }

    @Test fun textMentionsCoreAndStaysUserFacing() {
        codes.forEach { code ->
            val text = CoreLoadError.text(code, "melondsds")
            assertTrue("mã $code", text.contains("melondsds"))
            assertTrue("mã $code", text.contains("Quay lại"))
        }
    }

    @Test fun unknownCodeKeepsTheNumber() {
        assertTrue(CoreLoadError.text(42, "pcsx_rearmed").contains("42"))
        assertTrue(CoreLoadError.short(42, "pcsx_rearmed").contains("42"))
    }

    @Test fun libraryErrorPointsToCoreSettings() {
        val text = CoreLoadError.text(0, "pcsx_rearmed")
        assertTrue(text, text.contains("Lõi giả lập"))
        assertTrue(text, text.contains("Xóa"))
    }

    @Test fun shortMessageIsOneLine() {
        codes.forEach { code ->
            val short = CoreLoadError.short(code, "melondsds")
            assertTrue("mã $code: $short", short.isNotBlank() && !short.contains('\n'))
        }
    }
}
