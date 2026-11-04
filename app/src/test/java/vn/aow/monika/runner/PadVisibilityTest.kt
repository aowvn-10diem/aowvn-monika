package vn.aow.monika.runner

import android.app.Application
import android.view.InputDevice
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.playsoftware.j2meloader.AowPad
import ru.playsoftware.j2meloader.AowPadVisibility
import vn.aow.monika.Prefs
import vn.aow.monika.ui.TestApp

/**
 * V162: nút ẩn/hiện phím ảo + tự ẩn khi cắm tay cầm, tự hiện khi rút.
 * Quy tắc dùng chung cho libretro, Azahar (3DS) và J2ME ([AowPadVisibility]); sự kiện cắm/rút được
 * giả lập đúng bằng API mà ba activity gọi (`setPadConnected`), không dựng lại bộ nhận diện thiết bị.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class PadVisibilityTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun nutBatTatAnRoiHienPhimAo() {
        val p = AowPadVisibility()
        assertTrue("mặc định: phím hiện", p.isVisible)
        assertFalse("bấm lần 1: phím ẩn", p.toggle())
        assertTrue("nhớ là người chơi tự ẩn", p.isManualHidden)
        assertTrue("bấm lần 2: phím hiện lại", p.toggle())
        assertFalse(p.isManualHidden)
    }

    @Test fun camTayCamTuAnRutTayCamTuHien() {
        val p = AowPadVisibility()
        p.setPadConnected(true) // sự kiện onInputDeviceAdded
        assertFalse("cắm tay cầm: tự ẩn phím ảo", p.isVisible)
        assertFalse("ẩn do tay cầm, không phải người chơi tự ẩn", p.isManualHidden)
        p.setPadConnected(false) // sự kiện onInputDeviceRemoved
        assertTrue("rút tay cầm: tự hiện lại", p.isVisible)
    }

    @Test fun nguoiChoiDaTuAnThiRutTayCamKhongTuHien() {
        val p = AowPadVisibility()
        p.toggle()
        assertFalse(p.isVisible)
        p.setPadConnected(true)
        assertFalse(p.isVisible)
        p.setPadConnected(false)
        assertFalse("tôn trọng lựa chọn tự ẩn: rút tay cầm không tự hiện", p.isVisible)
        assertTrue(p.isManualHidden)
    }

    @Test fun bamHienKhiDangCamTayCamThiPhimHienNgay() {
        val p = AowPadVisibility()
        p.setPadConnected(true)
        assertFalse(p.isVisible)
        assertTrue("người chơi chủ động hiện: phím hiện dù đang cắm tay cầm", p.toggle())
    }

    /** Luna #433: onResume gọi lại setPadConnected(true) cùng trạng thái không được ẩn lại phím vừa hiện. */
    @Test fun hienPhimKhiDangCamTayCamGiuQuaLanDongBoOnResume() {
        val p = AowPadVisibility()
        p.setPadConnected(true)
        assertFalse(p.isVisible)
        p.toggle() // người chơi bấm "Hiện phím" khi tay cầm vẫn cắm
        assertTrue(p.isVisible)
        p.setPadConnected(true) // onResume: cùng trạng thái kết nối, không phải sự kiện cắm mới
        assertTrue("đồng bộ onResume cùng trạng thái không được ẩn lại", p.isVisible)
    }

    /** Rút rồi cắm lại là sự kiện kết nối thật → auto-hide phải hoạt động lại bình thường. */
    @Test fun rutRoiCamLaiThiTuAnHoatDongLai() {
        val p = AowPadVisibility()
        p.setPadConnected(true)
        p.toggle()
        assertTrue(p.isVisible)
        p.setPadConnected(false) // rút
        assertTrue(p.isVisible)
        p.setPadConnected(true) // cắm lại
        assertFalse("cắm lại phải tự ẩn phím", p.isVisible)
    }

    @Test fun nhanDienTayCamBangNguonThietBiCoSan() {
        assertTrue(GamepadInput.hasPadSource(listOf(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)))
        assertTrue(GamepadInput.hasPadSource(listOf(InputDevice.SOURCE_JOYSTICK)))
        // 0.8.9: DPAD một mình từng bị coi là tay cầm → máy không cắm gì vẫn tự ẩn phím.
        assertFalse("DPAD một mình không phải tay cầm", GamepadInput.hasPadSource(listOf(InputDevice.SOURCE_KEYBOARD, InputDevice.SOURCE_DPAD)))
        assertFalse(GamepadInput.hasPadSource(listOf(InputDevice.SOURCE_KEYBOARD, InputDevice.SOURCE_TOUCHSCREEN)))
    }

    /** V184: danh sách thiết bị của điện thoại thường (cảm ứng + phím cứng + DPAD) không được coi là tay cầm. */
    @Test fun dienThoaiKhongCoTayCamKhongBiCoiLaCoTayCam() {
        val phone = listOf(
            InputDevice.SOURCE_TOUCHSCREEN,
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD,
            InputDevice.SOURCE_MOUSE,
        )
        assertFalse(GamepadInput.hasPadSource(phone))
        assertFalse(AowPad.isPadDevice(InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD))
        assertTrue(AowPad.isPadDevice(InputDevice.SOURCE_GAMEPAD))
        assertTrue(AowPad.isPadDevice(InputDevice.SOURCE_JOYSTICK))
    }

    @Test fun trangThaiTuAnNhoTrongPrefs() {
        val prefs = Prefs(app)
        assertFalse("mặc định: phím hiện", prefs.padHidden)
        prefs.padHidden = true
        assertTrue("đọc lại được từ Prefs (file tiến trình game)", Prefs(app).padHidden)
        prefs.padHidden = false
        assertFalse(Prefs(app).padHidden)
    }
}
