package vn.aow.monika

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import ru.playsoftware.j2meloader.AowKeyPalette
import vn.aow.monika.ui.controls.controllerColors
import vn.aow.monika.ui.theme.AowColors
import kotlin.math.abs

/** V85c: bàn phím ảo J2ME (Java) dùng đúng token AowVN của app (`Monika.colors.aow`) và đủ tương phản. */
class AowKeyPaletteTest {
    private val aow = AowColors()

    @Test fun javaPaletteMatchesComposeTokens() {
        assertEquals(aow.orange.toArgb(), AowKeyPalette.ORANGE)
        assertEquals(aow.orangePressed.toArgb(), AowKeyPalette.ORANGE_PRESSED)
        assertEquals(aow.dark.toArgb(), AowKeyPalette.DARK)
        assertEquals(aow.darkPressed.toArgb(), AowKeyPalette.DARK_PRESSED)
        assertEquals(aow.padSurface.toArgb(), AowKeyPalette.PAD_SURFACE)
        assertEquals(aow.outline.toArgb(), AowKeyPalette.OUTLINE)
        assertEquals(aow.outerRim.toArgb(), AowKeyPalette.OUTER_RIM)
        assertEquals(aow.ink.toArgb(), AowKeyPalette.INK)
        assertEquals(aow.onDark.toArgb(), AowKeyPalette.ON_DARK)
        // Đỏ nhấn trộn nền tối: cùng công thức với tay cầm libretro (`controllerColors`), cho phép lệch làm tròn 1 đơn vị.
        val mixed = aow.controllerColors().darkPressed.toArgb()
        for (shift in intArrayOf(16, 8, 0)) {
            assertTrue("kênh $shift: compose=${Integer.toHexString(mixed)} java=${Integer.toHexString(AowKeyPalette.DARK_PRESSED_MIXED)}", abs(((mixed shr shift) and 0xFF) - ((AowKeyPalette.DARK_PRESSED_MIXED shr shift) and 0xFF)) <= 1)
        }
    }

    @Test fun everyKeyStateReadsAtFourPointFiveToOne() {
        for (primary in listOf(false, true)) for (pressed in listOf(false, true)) {
            val face = AowKeyPalette.face(primary, pressed)
            val label = AowKeyPalette.label(primary, pressed)
            val ratio = AowKeyPalette.contrast(face, label)
            assertTrue("primary=$primary pressed=$pressed: $ratio", ratio >= 4.5)
        }
    }

    @Test fun pressedLooksDifferentAndOkKeyIsOrange() {
        assertNotEquals(AowKeyPalette.face(false, false), AowKeyPalette.face(false, true))
        assertNotEquals(AowKeyPalette.face(true, false), AowKeyPalette.face(true, true))
        assertEquals(AowKeyPalette.ORANGE, AowKeyPalette.face(true, false))
        assertEquals(AowKeyPalette.PAD_SURFACE, AowKeyPalette.face(false, false))
    }

    /**
     * Tương phản THỰC TẾ sau khi vẽ: mặt phím phủ lên nền với đúng độ đục `FACE_ALPHA` mà `paintMonikaPhone` dùng, trên nền sáng, nền tối
     * và ba màu gradient vỏ máy (`MONIKA_BG`). Cả nhãn chính lẫn chữ phụ (ABC) phải ≥ 4.5:1 so với mặt phím đã trộn.
     */
    @Test fun renderedContrastHoldsOnLightDarkAndBrandBackgrounds() {
        val backgrounds = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(), 0xFFF08A4B.toInt(), 0xFFA0607A.toInt(), 0xFF2C3566.toInt())
        for (bg in backgrounds) for (primary in listOf(false, true)) for (pressed in listOf(false, true)) {
            val shown = AowKeyPalette.composite(AowKeyPalette.face(primary, pressed), bg, AowKeyPalette.FACE_ALPHA)
            val main = AowKeyPalette.contrast(shown, AowKeyPalette.label(primary, pressed))
            val sub = AowKeyPalette.contrast(shown, AowKeyPalette.subLabel(primary, pressed))
            val where = "nền=${Integer.toHexString(bg)} primary=$primary pressed=$pressed"
            assertTrue("nhãn $where: $main", main >= 4.5)
            assertTrue("chữ phụ $where: $sub", sub >= 4.5)
        }
    }

    /** Mặt phím đục hoàn toàn → màu hiện ra đúng bằng màu palette, không lẫn nền. */
    @Test fun faceIsFullyOpaqueSoBackgroundCannotBleedThrough() {
        assertEquals(0xFF, AowKeyPalette.FACE_ALPHA)
        for (primary in listOf(false, true)) for (pressed in listOf(false, true)) {
            val face = AowKeyPalette.face(primary, pressed)
            assertEquals(face, AowKeyPalette.composite(face, 0xFFF08A4B.toInt(), AowKeyPalette.FACE_ALPHA))
        }
    }

    /** Chứng minh lỗi cũ: với alpha 0xC0 (bản trước) OK thường chữ đen trên nền tối và OK nhấn chữ trắng trên nền sáng tụt dưới 4.5:1. */
    @Test fun oldTranslucentFaceWouldHaveFailed() {
        val darkBg = 0xFF000000.toInt(); val lightBg = 0xFFFFFFFF.toInt()
        val okNormalOnDark = AowKeyPalette.contrast(AowKeyPalette.composite(AowKeyPalette.face(true, false), darkBg, 0xC0), AowKeyPalette.label(true, false))
        val okPressedOnLight = AowKeyPalette.contrast(AowKeyPalette.composite(AowKeyPalette.face(true, true), lightBg, 0xC0), AowKeyPalette.label(true, true))
        assertTrue("OK thường trên nền tối: $okNormalOnDark", okNormalOnDark < 4.5)
        assertTrue("OK nhấn trên nền sáng: $okPressedOnLight", okPressedOnLight < 4.5)
    }
}
