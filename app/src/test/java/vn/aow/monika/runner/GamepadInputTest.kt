package vn.aow.monika.runner

import android.view.InputDevice
import android.view.KeyEvent
import org.citra.citra_emu.NativeLibrary.ButtonType
import org.junit.Assert.*
import org.junit.Test
import vn.aow.monika.azahar.AzaharPadMap
import vn.aow.monika.runner.GamepadInput.Axes
import vn.aow.monika.runner.GamepadInput.Change

/** V120: nhận tay cầm thật. Thuần JVM, chưa thử tay cầm thật [CHƯA KIỂM]. */
class GamepadInputTest {
    private val padSource = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD
    private val keyboard = InputDevice.SOURCE_KEYBOARD

    @Test fun nutTayCamNhanDuocKeCaKhiBaoNguonBanPhim() {
        for (k in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R2)) {
            assertTrue("pad $k", GamepadInput.isPadKey(padSource, k))
            assertTrue("bàn phím $k", GamepadInput.isPadKey(keyboard, k))
        }
    }

    @Test fun dpadChiNhanKhiNguonLaTayCam() {
        assertTrue(GamepadInput.isPadKey(padSource, KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(GamepadInput.isPadKey(InputDevice.SOURCE_DPAD or keyboard, KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(GamepadInput.isPadKey(InputDevice.SOURCE_JOYSTICK, KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse("mũi tên bàn phím thường không bị chặn", GamepadInput.isPadKey(keyboard, KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test fun backLuonDeChoHeThong() {
        assertFalse(GamepadInput.isPadKey(padSource, KeyEvent.KEYCODE_BACK))
    }

    @Test fun chuyenDongChiTuCanJoystick() {
        assertTrue(GamepadInput.isPadMotion(InputDevice.SOURCE_JOYSTICK))
        assertTrue(GamepadInput.isPadMotion(InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD))
        assertFalse(GamepadInput.isPadMotion(InputDevice.SOURCE_TOUCHSCREEN))
        assertFalse(GamepadInput.isPadMotion(InputDevice.SOURCE_MOUSE))
    }

    @Test fun hatPhatKhiDoiVaNhaKhiVeGiua() {
        val t = GamepadInput.Tracker()
        assertEquals(listOf(Change.Hat(1, 0, 0, 0)), t.update(Axes(hatX = 1f)))
        assertTrue("không đổi thì không phát", t.update(Axes(hatX = 1f)).isEmpty())
        assertEquals(listOf(Change.Hat(1, -1, 1, 0)), t.update(Axes(hatX = 1f, hatY = -1f)))
        assertEquals(listOf(Change.Hat(0, 0, 1, -1)), t.update(Axes()))
    }

    @Test fun canAnalogCoVungChet() {
        val t = GamepadInput.Tracker()
        assertTrue("trôi nhẹ bị bỏ", t.update(Axes(leftX = 0.1f, leftY = -0.1f)).isEmpty())
        assertEquals(listOf(Change.Stick(false, 0.8f, 0f)), t.update(Axes(leftX = 0.8f)))
        assertEquals(listOf(Change.Stick(true, 0f, -0.9f)), t.update(Axes(leftX = 0.8f, rightY = -0.9f)))
        assertEquals(listOf(Change.Stick(false, 0f, 0f)), t.update(Axes(leftX = 0.05f, rightY = -0.9f)))
    }

    @Test fun coQuyVeNutCoLechNguong() {
        val t = GamepadInput.Tracker()
        assertTrue("0,4 chưa tới ngưỡng nhấn", t.update(Axes(triggerL = 0.4f)).isEmpty())
        assertEquals(listOf(Change.Trigger(false, true)), t.update(Axes(triggerL = 0.8f)))
        assertTrue("đang nhấn, 0,4 vẫn giữ (trễ)", t.update(Axes(triggerL = 0.4f)).isEmpty())
        assertEquals(listOf(Change.Trigger(false, false)), t.update(Axes(triggerL = 0.2f)))
        assertEquals(listOf(Change.Trigger(true, true)), t.update(Axes(triggerR = 1f)))
    }

    @Test fun rutTayCamNhaHetTrucVaCo() {
        val t = GamepadInput.Tracker()
        t.update(Axes(hatY = 1f, leftX = 1f, triggerR = 1f))
        val r = t.reset()
        assertEquals(setOf<Change>(Change.Hat(0, 0, 0, 1), Change.Stick(false, 0f, 0f), Change.Trigger(true, false)), r.toSet())
        assertTrue(t.reset().isEmpty())
    }

    // ---- Azahar (3DS) ----

    @Test fun azaharNutTheoViTri() {
        assertEquals(ButtonType.BUTTON_B, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_A)) // dưới = B
        assertEquals(ButtonType.BUTTON_A, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_B)) // phải = A
        assertEquals(ButtonType.BUTTON_Y, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_X)) // trái = Y
        assertEquals(ButtonType.BUTTON_X, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_Y)) // trên = X
        assertEquals(ButtonType.TRIGGER_L, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(ButtonType.TRIGGER_R, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(ButtonType.BUTTON_ZL, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(ButtonType.BUTTON_ZR, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(ButtonType.BUTTON_START, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(ButtonType.BUTTON_SELECT, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(ButtonType.DPAD_UP, AzaharPadMap.button(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(ButtonType.DPAD_RIGHT, AzaharPadMap.button(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(-1, AzaharPadMap.button(KeyEvent.KEYCODE_BUTTON_THUMBL))
    }

    @Test fun azaharHatDoiHuongNhaCuTruocNhanMoiSau() {
        val out = AzaharPadMap.out(Change.Hat(1, 0, -1, 0))
        assertEquals(listOf(AzaharPadMap.Out.Press(ButtonType.DPAD_LEFT, false), AzaharPadMap.Out.Press(ButtonType.DPAD_RIGHT, true)), out)
        assertEquals(
            listOf(AzaharPadMap.Out.Press(ButtonType.DPAD_UP, false), AzaharPadMap.Out.Press(ButtonType.DPAD_DOWN, true)),
            AzaharPadMap.out(Change.Hat(0, 1, 0, -1)),
        )
    }

    @Test fun azaharCanVaCo() {
        assertEquals(listOf(AzaharPadMap.Out.Stick(ButtonType.STICK_LEFT, 0.5f, 0.1f)), AzaharPadMap.out(Change.Stick(false, 0.5f, 0.1f)))
        assertEquals(listOf(AzaharPadMap.Out.Stick(ButtonType.STICK_C, 0f, 1f)), AzaharPadMap.out(Change.Stick(true, 0f, 1f)))
        assertEquals(listOf(AzaharPadMap.Out.Press(ButtonType.BUTTON_ZL, true)), AzaharPadMap.out(Change.Trigger(false, true)))
        assertEquals(listOf(AzaharPadMap.Out.Press(ButtonType.BUTTON_ZR, false)), AzaharPadMap.out(Change.Trigger(true, false)))
    }
}
