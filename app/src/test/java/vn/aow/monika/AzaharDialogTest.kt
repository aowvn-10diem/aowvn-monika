package vn.aow.monika

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.azahar.AzDialog
import vn.aow.monika.azahar.AzaharBridge
import vn.aow.monika.ui.TestApp
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Hộp thoại engine 3DS được native gọi ở luồng giả lập và **chờ** câu trả lời. Nếu không còn ai vẽ/trả lời
 * (màn chơi đang đóng, người chơi đã rời màn), lời chờ phải có hạn: luồng giả lập không được treo vĩnh viễn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AzaharDialogTest {
    private val main get() = shadowOf(Looper.getMainLooper())

    @After fun traHanChoVeMacDinh() {
        main.idle()
        AzaharBridge.dialog = null
        AzaharBridge.dialogTimeoutMs = 60_000
    }

    @Test fun khongCoAiTraLoiThiKhongTreoLuongGiaLap() {
        AzaharBridge.dialogTimeoutMs = 50
        val traLoi = AtomicBoolean(true)
        val luong = Thread({ traLoi.set(AzaharBridge.alert("Lỗi", "thử", false)) }, "test-dialog")
        luong.isDaemon = true
        luong.start()
        luong.join(5_000)
        assertFalse("luồng giả lập vẫn treo sau hạn chờ", luong.isAlive)
        assertFalse("hết hạn chờ phải trả câu trả lời dự phòng", traLoi.get())
    }

    @Test fun hopThoaiHetHanKhongHienMoCoi() {
        AzaharBridge.dialogTimeoutMs = 50
        val luong = Thread({ AzaharBridge.alert("Lỗi", "thử", false) }, "test-dialog")
        luong.isDaemon = true
        luong.start()
        luong.join(5_000)
        main.idle() // chạy các việc đã xếp lên luồng chính sau khi hết hạn
        assertNull("không được hiện hộp thoại đã hết hạn chờ", AzaharBridge.dialog)
    }

    @Test fun traLoiBinhThuongTraVeDungKetQua() {
        AzaharBridge.dialogTimeoutMs = 5_000
        val ketQua = AtomicReference<Boolean?>(null)
        val luong = Thread({ ketQua.set(AzaharBridge.alert("Hỏi", "chọn", true)) }, "test-dialog")
        luong.isDaemon = true
        luong.start()
        val han = System.currentTimeMillis() + 3_000
        while (AzaharBridge.dialog == null && System.currentTimeMillis() < han) {
            main.idle()
            Thread.sleep(10)
        }
        val hopThoai = AzaharBridge.dialog as? AzDialog.Message
        assertNotNull("không hiện hộp thoại của engine", hopThoai)
        hopThoai!!.reply.complete(true)
        luong.join(3_000)
        assertEquals(true, ketQua.get())
        main.idle()
        assertNull("hộp thoại phải được dọn sau khi trả lời", AzaharBridge.dialog)
    }

    /** Lỗi nghiêm trọng của lõi (`canContinue = false`): không ai trả lời thì phải trả `false` = dừng game. */
    @Test fun loiNghiemTrongHetHanChoTiepTucDungGame() {
        AzaharBridge.dialogTimeoutMs = 50
        val tiepTuc = AtomicBoolean(true)
        val luong = Thread({
            tiepTuc.set(AzaharBridge.onCoreError(org.citra.citra_emu.NativeLibrary.CoreError.ErrorCoreExceptionRaised, "thử"))
        }, "test-dialog")
        luong.isDaemon = true
        luong.start()
        luong.join(5_000)
        assertFalse("lỗi nghiêm trọng phải dừng game khi không ai trả lời", tiepTuc.get())
    }
}
