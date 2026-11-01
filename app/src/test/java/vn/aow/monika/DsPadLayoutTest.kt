package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.runner.DsFrame
import vn.aow.monika.ui.TestApp

/**
 * V160: màn dọc NDS phải rộng hết chiều ngang (tay cầm ảo che lên nửa dưới) và bố cục màn ngang
 * lấy từ config, không hard-code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DsPadLayoutTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun screen(id: String, key: String, fallback: String) =
        ConfigRepository.parse(app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() })
            .cores[id]?.screen ?: error("thiếu config screen cho $id ($key/$fallback)")

    /** (rộng px, cao px, mật độ, khoảng chừa trên px) của 3 máy thật phổ biến. */
    private val phones = listOf(
        Triple(1080 to 2340, 2.75f, 90),
        Triple(720 to 1600, 2.0f, 80),
        Triple(1080 to 1920, 3.0f, 100),
    )

    @Test fun portraitFrameFillsWidthOnThreeScreenSizes() {
        for ((size, density, top) in phones) {
            val (w, h) = size
            val reserve = (DsFrame.padReserveDp(overlayPad = true) * density).toInt()
            val frameH = DsFrame.portraitHeight(w, h, top, reserve)
            val pictureW = frameH * DsFrame.ASPECT
            assertTrue("${w}x$h: hình chỉ $pictureW/$w", pictureW >= w * 0.99f)
        }
    }

    @Test fun padOverlayReservesMuchLessThanASeparatePadRow() {
        assertTrue(DsFrame.padReserveDp(true) < DsFrame.padReserveDp(false))
        // Máy thấp (1080×1920, 3x): kiểu chừa chỗ riêng làm hình hụt còn dưới 70 % chiều ngang.
        val reserve = (DsFrame.padReserveDp(false) * 3f).toInt()
        val frameH = DsFrame.portraitHeight(1080, 1920, 100, reserve)
        assertTrue(frameH * DsFrame.ASPECT < 1080 * 0.7f)
    }

    @Test fun ndsConfigUsesPadOverlayAndLandscapeSideBySide() {
        val s = screen("melondsds", "overlayPad", "landscapeLayout")
        assertTrue(s.overlayPad)
        assertTrue(s.padOpacity in 0.2f..1f)
        assertEquals("left-right", s.landscapeLayout)
        // Giá trị bố cục màn ngang phải là một lựa chọn có thật của lõi.
        assertEquals(1, s.layouts.count { it.value == s.landscapeLayout })
    }
}
