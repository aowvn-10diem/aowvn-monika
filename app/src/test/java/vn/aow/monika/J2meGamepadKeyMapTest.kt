package vn.aow.monika

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.microedition.lcdui.Canvas
import javax.microedition.lcdui.keyboard.KeyMapper

/** V120: tay cầm thật trong game Java. Bảng phím mặc định có nút tay cầm; hat/cần analog quy về 4 hướng. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class J2meGamepadKeyMapTest {
    private val map = KeyMapper.getDefaultKeyMap()

    @Test fun nutTayCamCoTrongBangMacDinh() {
        assertEquals(Canvas.KEY_FIRE, map.get(KeyEvent.KEYCODE_BUTTON_A, 0))
        assertEquals(Canvas.KEY_FIRE, map.get(KeyEvent.KEYCODE_BUTTON_START, 0))
        assertEquals(Canvas.KEY_FIRE, map.get(KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertEquals(Canvas.KEY_SOFT_RIGHT, map.get(KeyEvent.KEYCODE_BUTTON_B, 0))
        assertEquals(Canvas.KEY_SOFT_LEFT, map.get(KeyEvent.KEYCODE_BUTTON_X, 0))
        assertEquals(Canvas.KEY_NUM0, map.get(KeyEvent.KEYCODE_BUTTON_Y, 0))
        assertEquals(Canvas.KEY_STAR, map.get(KeyEvent.KEYCODE_BUTTON_SELECT, 0))
        assertEquals(Canvas.KEY_NUM1, map.get(KeyEvent.KEYCODE_BUTTON_L1, 0))
        assertEquals(Canvas.KEY_NUM3, map.get(KeyEvent.KEYCODE_BUTTON_R1, 0))
    }

    @Test fun dpadVanLaBonHuong() {
        assertEquals(Canvas.KEY_UP, map.get(KeyEvent.KEYCODE_DPAD_UP, 0))
        assertEquals(Canvas.KEY_DOWN, map.get(KeyEvent.KEYCODE_DPAD_DOWN, 0))
        assertEquals(Canvas.KEY_LEFT, map.get(KeyEvent.KEYCODE_DPAD_LEFT, 0))
        assertEquals(Canvas.KEY_RIGHT, map.get(KeyEvent.KEYCODE_DPAD_RIGHT, 0))
    }

    @Test fun hatUuTienHonCanVaCanCoVungChet() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, KeyMapper.gamepadDirection(-1f, 0f, 0.9f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, KeyMapper.gamepadDirection(0f, 1f, 0f, 0f))
        assertEquals(0, KeyMapper.gamepadDirection(0f, 0f, 0.3f, -0.4f))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, KeyMapper.gamepadDirection(0f, 0f, 0.2f, -0.8f))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, KeyMapper.gamepadDirection(0f, 0f, 0.9f, 0.5f))
    }
}
