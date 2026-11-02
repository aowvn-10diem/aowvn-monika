package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.runner.DsFrame
import vn.aow.monika.runner.DsScreen
import vn.aow.monika.runner.ScreenLayoutSwitch
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

    /**
     * V160 (Luna 3): xoay ngang→dọc phải trả lại bố cục người chơi đã lưu, không ghi đè bằng mặc định config.
     */
    @Test fun rotateBackToPortraitKeepsSavedLayout() {
        val s = screen("melondsds", "layoutKey", "defaultLayout")
        val saved = mapOf(s.layoutKey to "hybrid-bottom")
        assertEquals("left-right", DsScreen.rotationLayout(s, saved, landscape = true))
        assertEquals("hybrid-bottom", DsScreen.rotationLayout(s, saved, landscape = false))
        val configDefault = s.layouts.single { it.id == s.defaultLayout }.value
        assertNotEquals(configDefault, DsScreen.rotationLayout(s, saved, landscape = false))
    }

    /** Chưa từng chọn bố cục: về dọc dùng mặc định của config (config là mặc định, không phải ghi đè). */
    @Test fun portraitWithoutSavedChoiceUsesConfigDefault() {
        val s = screen("melondsds", "layoutKey", "defaultLayout")
        val configDefault = s.layouts.single { it.id == s.defaultLayout }.value
        assertEquals(configDefault, DsScreen.rotationLayout(s, emptyMap(), landscape = false))
    }

    /**
     * V160 (Luna 3): xoay ngang lúc `retroView` chưa tạo không được đánh dấu "đã đổi chiều".
     * Nếu đánh dấu sớm, lời gọi khi view tạo xong bị điều kiện "đã đúng chiều" chặn → bố cục ngang
     * không bao giờ được áp.
     */
    @Test fun earlyRotationBeforeViewReadyIsAppliedWhenViewArrives() {
        val s = screen("melondsds", "layoutKey", "landscapeLayout")
        val switch = ScreenLayoutSwitch()
        val writes = mutableListOf<Pair<String, String>>()
        // onConfigurationChanged chạy trước khi GLRetroView có: không ghi, không đánh dấu chiều.
        assertFalse(switch.apply(s, emptyMap(), landscape = true, write = null))
        assertFalse(switch.appliedLandscape)
        assertTrue(writes.isEmpty())
        // View tạo xong → lời gọi tại chỗ tạo view phải ghi bố cục ngang, không bị lần xoay sớm chặn.
        assertTrue(switch.apply(s, emptyMap(), landscape = true) { writes += it })
        assertEquals(listOf(s.layoutKey to "left-right"), writes)
        assertTrue(switch.appliedLandscape)
        // Xoay tiếp về dọc → trả lại bố cục người chơi đã lưu, ghi đúng một lần.
        assertTrue(switch.apply(s, mapOf(s.layoutKey to "hybrid-bottom"), landscape = false) { writes += it })
        assertEquals("hybrid-bottom", writes.last().second)
        assertFalse(switch.appliedLandscape)
    }

    /** Xoay ngang rồi về dọc khi view còn chưa tạo: không ghi gì, lúc view xong (đang dọc) cũng không ghi thừa. */
    @Test fun rotationBackBeforeViewReadyWritesNothing() {
        val s = screen("melondsds", "layoutKey", "landscapeLayout")
        val switch = ScreenLayoutSwitch()
        assertFalse(switch.apply(s, emptyMap(), landscape = true, write = null))
        assertFalse(switch.apply(s, emptyMap(), landscape = false, write = null))
        val writes = mutableListOf<Pair<String, String>>()
        // Bố cục dọc đã có sẵn từ lúc nạp game (CoreOptions.initial) → không ghi lại.
        assertFalse(switch.apply(s, emptyMap(), landscape = false) { writes += it })
        assertTrue(writes.isEmpty())
    }

    /**
     * V160: màn ngang xếp hai màn cạnh nhau (2 × 256×192 = 512×192) phải vừa khít 3 cỡ máy, không cắt,
     * và dùng bố cục ngang của config; xếp chồng như màn dọc thì hụt chiều ngang rất nhiều.
     */
    @Test fun landscapeSideBySideFitsThreeScreenSizes() {
        val s = screen("melondsds", "landscapeLayout", "layoutKey")
        for ((size, _, _) in phones) {
            val landscapeW = maxOf(size.first, size.second)
            val landscapeH = minOf(size.first, size.second)
            val sideScale = minOf(landscapeW / 512f, landscapeH / 192f)
            val sideW = 512 * sideScale
            val sideH = 192 * sideScale
            assertTrue("${landscapeW}x$landscapeH: cặp màn chỉ $sideW/$landscapeW px", sideW >= landscapeW * 0.99f)
            assertTrue("${landscapeW}x$landscapeH: cặp màn cao $sideH/$landscapeH px", sideH <= landscapeH + 0.5f)
            // Bố cục chồng dọc (như màn dọc) trên màn ngang chỉ dùng được ~1/3 chiều rộng.
            val stackedW = 256 * minOf(landscapeW / 256f, landscapeH / 384f)
            assertTrue("${landscapeW}x$landscapeH: cạnh nhau $sideW ≤ chồng dọc $stackedW", sideW >= stackedW * 1.5f)
            assertEquals("left-right", DsScreen.rotationLayout(s, emptyMap(), landscape = true))
        }
    }
}
