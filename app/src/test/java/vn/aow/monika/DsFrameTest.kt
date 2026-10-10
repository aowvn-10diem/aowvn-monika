package vn.aow.monika

import org.junit.Assert.*
import org.junit.Test
import vn.aow.monika.runner.DsFrame
import vn.aow.monika.runner.PadLayout
import vn.aow.monika.runner.padFor

/** V78a: khung NDS ở màn dọc lấy tối đa chiều rộng; lõi `melondsds` (lõi NDS mặc định) dùng tay cầm NDS. */
class DsFrameTest {
    // Máy 393×851 dp ở 2,75× (xxhdp): rộng 1081 px, cao 2340 px.
    private val w = 1081
    private val h = 2340
    private val reserve = (DsFrame.PAD_RESERVE_DP * 2.75f).toInt()

    @Test fun twoScreensFillWidthWhenThereIsRoom() {
        // Màn cao: đủ chỗ cho khung cao 1,5× chiều rộng.
        val tall = DsFrame.portraitHeight(w, 3200, top = 80, padReservePx = reserve)
        assertEquals((w / DsFrame.ASPECT).toInt(), tall)
    }

    @Test fun framePictureNearlyFillsWidthOnATypicalPhone() {
        val top = (DsFrame.TOP_GAP_DP * 2.75f).toInt() + 90
        val frameH = DsFrame.portraitHeight(w, h, top, reserve)
        val pictureWidth = frameH * DsFrame.ASPECT // lõi giữ tỉ lệ 2:3 trong khung
        // Trước V78: lõi melondsds rơi về tỉ lệ 4:3 → khung cao w/(4/3) → hình chỉ ~ w/2.
        val before = (w / (4f / 3f)) * DsFrame.ASPECT
        assertTrue("sau $pictureWidth / trước $before", pictureWidth > before * 1.6f)
        assertTrue(pictureWidth >= w * 0.8f)
    }

    @Test fun leavesRoomForThePadOnShortScreens() {
        val top = 100
        val frameH = DsFrame.portraitHeight(1080, 1920, top, reserve)
        assertTrue(frameH <= 1920 - top - reserve)
        assertTrue(frameH >= 1920 * 4 / 10)
    }

    @Test fun neverCollapsesBelowFortyPercent() {
        assertEquals(800 * 4 / 10, DsFrame.portraitHeight(1080, 800, 300, 600))
    }

    @Test fun defaultNdsCoreUsesNdsPad() {
        assertEquals(PadLayout.NDS, padFor("melondsds", null))
        assertEquals(PadLayout.NDS, padFor("melonds", null))
        assertEquals(PadLayout.NDS, padFor("desmume", null))
    }
}
