package vn.aow.monika

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import ru.playsoftware.j2meloader.AowPad
import javax.microedition.lcdui.Canvas

/** V120: tay cầm thật trong game Java. Bảng nút tay cầm (nạp vào `KeyMapper.getDefaultKeyMap`); hat/cần analog quy về 4 hướng. */
class J2meGamepadKeyMapTest {
    private val map = AowPad.KEYS.associate { it[0] to it[1] }

    @Test fun nutTayCamCoTrongBangMacDinh() {
        assertEquals(Canvas.KEY_FIRE, map[KeyEvent.KEYCODE_BUTTON_A])
        assertEquals(Canvas.KEY_FIRE, map[KeyEvent.KEYCODE_BUTTON_START])
        assertEquals(Canvas.KEY_FIRE, map[KeyEvent.KEYCODE_DPAD_CENTER])
        assertEquals(Canvas.KEY_SOFT_RIGHT, map[KeyEvent.KEYCODE_BUTTON_B])
        assertEquals(Canvas.KEY_SOFT_LEFT, map[KeyEvent.KEYCODE_BUTTON_X])
        assertEquals(Canvas.KEY_NUM0, map[KeyEvent.KEYCODE_BUTTON_Y])
        assertEquals(Canvas.KEY_STAR, map[KeyEvent.KEYCODE_BUTTON_SELECT])
        assertEquals(Canvas.KEY_NUM1, map[KeyEvent.KEYCODE_BUTTON_L1])
        assertEquals(Canvas.KEY_NUM3, map[KeyEvent.KEYCODE_BUTTON_R1])
    }

    @Test fun bangKhongTrungKhoaVaKhongDeLenBonHuongDpad() {
        val keys = AowPad.KEYS.map { it[0] }
        assertEquals("trùng mã Android", keys.size, keys.toSet().size)
        val dpad = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("bốn hướng D-pad vẫn do KeyMapper xử lý", keys.none { it in dpad })
    }

    @Test fun hatUuTienHonCanVaCanCoVungChet() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, AowPad.direction(-1f, 0f, 0.9f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, AowPad.direction(0f, 1f, 0f, 0f))
        assertEquals(0, AowPad.direction(0f, 0f, 0.3f, -0.4f))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, AowPad.direction(0f, 0f, 0.2f, -0.8f))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, AowPad.direction(0f, 0f, 0.9f, 0.5f))
    }
}
