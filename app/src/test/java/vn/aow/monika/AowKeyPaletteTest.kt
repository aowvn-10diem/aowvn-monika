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

    /** PM 11/10: vkAlpha = 0 không được làm bàn phím Monika biến mất; kiểu khác vẫn ẩn khi độ mờ 0 như bản gốc. */
    @Test fun monikaKeysStayVisibleEvenWhenOpacityIsZero() {
        assertTrue(AowKeyPalette.keysVisible(false, 0, true))      // Monika, vkAlpha = 0 → vẫn vẽ
        assertTrue(AowKeyPalette.keysVisible(false, 140, true))
        assertFalse(AowKeyPalette.keysVisible(false, 0, false))    // kiểu khác, vkAlpha = 0 → ẩn như cũ
        assertTrue(AowKeyPalette.keysVisible(false, 1, false))
        assertTrue(AowKeyPalette.keysVisible(true, 0, false))      // đang sửa bố cục luôn hiện
        // Mã vẽ thật phải đi qua đúng hàm này (không còn so vkAlpha trực tiếp).
        val src = java.io.File("../j2me/src/main/java/javax/microedition/lcdui/keyboard/VirtualKeyboard.java").readText()
        assertTrue(src.contains("AowKeyPalette.keysVisible(layoutEditMode != LAYOUT_EOF, settings.vkAlpha, paintsAowKeys())"))
        assertFalse("không còn điều kiện vkAlpha > 0 cũ trong paint()", src.contains("layoutEditMode != LAYOUT_EOF || settings.vkAlpha > 0"))
        assertTrue(src.contains("AowKeyPalette.opaqueKeys(MONIKA_STYLE, layoutVariant == TYPE_MONIKA, settings.vkButtonShape == SHAPE_RECT)"))
    }

    /**
     * Hồi quy (Luna Ultra / Haiku-2): Monika + hình chữ nhật + vkAlpha = 0. Khóa "Độ mờ" và bộ vẽ dùng CÙNG `opaqueKeys`, nên
     * không bao giờ xảy ra "phím bị ẩn mà thanh Độ mờ lại bị khóa" (người dùng không có cách bật lại).
     */
    @Test fun lockAndRendererShareOneConditionSoKeysNeverHideWhileOpacityIsLocked() {
        // Hình chữ nhật dùng nét vẽ gốc (theo vkAlpha) → không đục, không khóa; vkAlpha = 0 ẩn phím nhưng thanh Độ mờ còn dùng được.
        val rect = AowKeyPalette.opaqueKeys(true, true, true)
        assertFalse(rect)
        assertFalse(AowKeyPalette.keysVisible(false, 0, rect))
        // Hình tròn/bo góc (mặc định): đục, khóa Độ mờ, phím luôn hiện kể cả vkAlpha = 0.
        val round = AowKeyPalette.opaqueKeys(true, true, false)
        assertTrue(round)
        assertTrue(AowKeyPalette.keysVisible(false, 0, round))
        // Kiểu bàn phím khác Monika, hoặc tắt MONIKA_STYLE: không khóa.
        assertFalse(AowKeyPalette.opaqueKeys(true, false, false))
        assertFalse(AowKeyPalette.opaqueKeys(false, true, false))
        // Bất biến trên mọi tổ hợp: phím bị ẩn ⇒ thanh Độ mờ KHÔNG bị khóa.
        for (style in listOf(false, true)) for (monika in listOf(false, true)) for (rectShape in listOf(false, true)) for (alpha in listOf(0, 1, 140, 255)) {
            val locked = AowKeyPalette.opaqueKeys(style, monika, rectShape)
            val shown = AowKeyPalette.keysVisible(false, alpha, locked)
            assertTrue("style=$style monika=$monika rect=$rectShape alpha=$alpha: phím ẩn mà Độ mờ bị khóa", shown || !locked)
        }
        // Mã thật dùng đúng hàm này ở cả hai chỗ.
        val config = java.io.File("../j2me/src/main/java/ru/playsoftware/j2meloader/config/ConfigActivity.java").readText()
        val lock = config.substringAfter("private void applyMonikaOpacityLock()").substringBefore("\n\t}\n")
        assertTrue(lock.contains("AowKeyPalette.opaqueKeys(VirtualKeyboard.MONIKA_STYLE,"))
        assertTrue(lock.contains("spButtonsShape.getSelectedItemPosition() == VirtualKeyboard.SHAPE_RECT"))
        assertTrue("đổi hình phím phải cập nhật khóa ngay", config.contains("binding.spButtonsShape.setOnItemSelectedListener"))
    }
}
