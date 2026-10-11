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
}
