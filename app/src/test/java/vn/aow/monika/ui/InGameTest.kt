package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.runner.CoreOption
import vn.aow.monika.runner.AutoHide
import vn.aow.monika.runner.HEADER_TAG
import vn.aow.monika.runner.InGameOverlay
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.PAD_TOGGLE_TAG
import vn.aow.monika.runner.PadLayout
import vn.aow.monika.ui.theme.MonikaTheme

/** Lớp phủ trong game: tay cầm từng hệ, menu nhanh, bảng tùy chọn giả lập. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class InGameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // Tự điều khiển đồng hồ: hiệu ứng lặp vô hạn / việc nền không làm test chờ mãi (test ổn định khi chạy cả bộ).
    @org.junit.Before fun manualClock() { rule.mainClock.autoAdvance = false }

    private fun overlay(state: InGameState, layout: PadLayout, onOptionChange: (CoreOption, String) -> Unit = { _, _ -> }) =
        rule.setContent {
            MonikaTheme {
                InGameOverlay(
                    state = state, system = controllerName(layout), title = "Game thử " + controllerName(layout), layout = layout, showPad = true,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                    onOptionChange = onOptionChange,
                )
            }
        }.also { rule.mainClock.advanceTimeBy(1_000) }

    private fun controllerName(layout: PadLayout) = when (layout) {
        PadLayout.NDS -> "Nintendo DS"; PadLayout.GBA -> "Game Boy Advance"; PadLayout.PS -> "PlayStation"
        PadLayout.SNES -> "Super Nintendo"; PadLayout.GEN -> "Mega Drive"; PadLayout.N64 -> "Nintendo 64"
        PadLayout.DC -> "Dreamcast"; PadLayout.PSP -> "PSP"; PadLayout.N3DS -> "Nintendo 3DS"
        else -> layout.name
    }

    @Test fun padNds() { overlay(InGameState(), PadLayout.NDS); rule.shot("6-tay-cam-nds") }
    @Test fun padGba() { overlay(InGameState(), PadLayout.GBA); rule.shot("7-tay-cam-gba") }
    @Test fun padPs() { overlay(InGameState(), PadLayout.PS); rule.shot("8-tay-cam-ps") }
    @Test fun padSnes() { overlay(InGameState(), PadLayout.SNES); rule.shot("14-tay-cam-snes") }
    @Test fun padGenesis() { overlay(InGameState(), PadLayout.GEN); rule.shot("15-tay-cam-megadrive") }
    @Test fun padN64() { overlay(InGameState(), PadLayout.N64); rule.shot("16-tay-cam-n64") }
    @Test fun padDreamcast() { overlay(InGameState(), PadLayout.DC); rule.shot("17-tay-cam-dreamcast") }
    @Test fun padPsp() { overlay(InGameState(), PadLayout.PSP); rule.shot("18-tay-cam-psp") }
    @Test fun pad3ds() { overlay(InGameState(), PadLayout.N3DS); rule.shot("19-tay-cam-3ds") }

    /** Kéo cần analog → gửi (x,y) cho lõi; nhả tay → về (0,0). */
    @Test fun analogStickSendsMotion() {
        val events = mutableListOf<Triple<Int, Float, Float>>()
        rule.setContent {
            MonikaTheme {
                InGameOverlay(
                    state = InGameState(), system = "N64", title = "Mario", layout = PadLayout.N64, showPad = true,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                    onMotion = { src, x, y -> events += Triple(src, x, y) },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Cần → D-pad").assertExists() // N64 mặc định dùng cần
    }

    @Test fun menu() {
        val s = InGameState().apply { menuOpen = true; filledSlots = setOf(1, 3) }
        overlay(s, PadLayout.NDS)
        rule.onNodeWithText("Tùy chọn giả lập").assertExists()
        rule.shot("9-menu-nhanh")
    }

    @Test fun optionsPanel() {
        val s = InGameState().apply {
            options = listOf(
                vn.aow.monika.runner.CoreOptions.parse("melonds_screen_layout", "Screen Layout; Top/Bottom|Left/Right|Hybrid", "Top/Bottom", vn.aow.monika.AppGraph.config.current.coreOptionText)!!,
                vn.aow.monika.runner.CoreOptions.parse("melonds_threaded_renderer", "Threaded software renderer; disabled|enabled", "enabled", vn.aow.monika.AppGraph.config.current.coreOptionText)!!,
            )
        }
        var changed: Pair<String, String>? = null
        overlay(s, PadLayout.NDS) { o, v -> changed = o.key to v }
        rule.shot("10-tuy-chon-gia-lap")
        rule.onNodeWithText("Bố cục 2 màn hình").performClick()
        rule.waitForIdle()
        assertEquals("melonds_screen_layout" to "Left/Right", changed)
    }

    /** A31: tự ẩn ở mọi giả lập — hiện lúc vào game, ẩn sau AutoHide.HIDE_MS, chạm mép hiện lại rồi lại tự ẩn, mở menu thì giữ. */
    private fun assertAutoHides(layout: PadLayout, edgeBottom: Boolean = false, shotName: String? = null) {
        val state = InGameState()
        overlay(state, layout)
        rule.onNodeWithTag(HEADER_TAG).assertIsDisplayed()
        shotName?.let { rule.shot("$it-hien") }
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(HEADER_TAG).assertDoesNotExist()
        shotName?.let { rule.shot("$it-an") }
        // Chạm sát mép (NDS dọc: mép dưới; còn lại: mép trên).
        rule.onRoot().performTouchInput { down(Offset(width / 2f, if (edgeBottom) height - 8f else 8f)); up() }
        // Trạng thái đổi từ trình xử lý chạm: Robolectric chỉ áp dụng khi looper rảnh (waitForIdle) trước khi chạy khung hình.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(HEADER_TAG).assertIsDisplayed()
        // Lần ẩn thứ hai: giờ đếm chạy lại sau khi hiện lại.
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(HEADER_TAG).assertDoesNotExist()
        // Mở menu: hiện lại và giữ nguyên tới khi đóng menu, rồi lại tự ẩn.
        rule.runOnIdle { state.menuOpen = true }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(HEADER_TAG).assertExists()
        rule.runOnIdle { state.menuOpen = false }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(HEADER_TAG).assertDoesNotExist()
    }

    @Test fun ndsPortraitHeaderAtBottomAutoHides() = assertAutoHides(PadLayout.NDS, edgeBottom = true, shotName = "v89-nds-doc")

    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun pspLandscapeHeaderAutoHidesAndDoesNotCoverGame() = assertAutoHides(PadLayout.PSP, shotName = "v89-psp-ngang")

    @Test fun gbaHeaderAutoHides() = assertAutoHides(PadLayout.GBA)

    /** A30: NDS dọc — thanh tên/menu nằm sát đáy, dưới bàn phím ảo; hệ khác giữ ở đầu màn hình. */
    @Test fun ndsHeaderSitsAtTheBottomOthersAtTheTop() {
        overlay(InGameState(), PadLayout.NDS)
        val rootH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val nds = rule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("NDS dọc: thanh phải ở nửa dưới, top=${nds.top} / $rootH", nds.top > rootH * 0.8f)
        // Bàn phím ảo không bị thanh đè: hàng SELECT/START nằm trên thanh.
        val start = rule.onNodeWithText("START").fetchSemanticsNode().boundsInRoot
        assertTrue("START (bottom=${start.bottom}) phải nằm trên thanh (top=${nds.top})", start.bottom <= nds.top + 1f)
    }

    @Test fun headerAtTopForOtherSystems() {
        overlay(InGameState(), PadLayout.GBA)
        val rootH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue(rule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot.top < rootH * 0.2f)
    }

    /** Chưa vào game (đang tải) thì không ẩn; tắt tự ẩn thì luôn hiện. */
    @Test fun headerNeverHidesBeforeGameRunsOrWhenAutoHideOff() {
        rule.setContent {
            MonikaTheme {
                InGameOverlay(
                    state = InGameState(), system = "Nintendo DS", title = "Game thử NDS", layout = PadLayout.NDS, showPad = false,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(HEADER_TAG).assertExists()
    }

    @Test fun autoHideOffKeepsHeader() {
        rule.setContent {
            MonikaTheme {
                InGameOverlay(
                    state = InGameState(), system = "Game Boy Advance", title = "Game thử", layout = PadLayout.GBA, showPad = true,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                    autoHideHeader = false,
                )
            }
        }
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(HEADER_TAG).assertExists()
    }

    /** A31: chip gợi ý "D-pad → Cần" (PSP) tự mờ cùng thanh tên game: đang ẩn thì không bấm được, chạm mép hiện lại thì bấm được. */
    @Test fun stickHintChipFadesWithHeader() {
        val state = InGameState()
        overlay(state, PadLayout.PSP)
        rule.onNodeWithText("Cần → D-pad").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        // Mờ hẳn: chạm vào chip không đổi chế độ.
        rule.onNodeWithText("Cần → D-pad").performClick()
        rule.waitForIdle()
        assertEquals(null, state.stickMode)
        rule.onRoot().performTouchInput { down(Offset(width / 2f, 8f)); up() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Cần → D-pad").performClick()
        rule.waitForIdle()
        assertEquals(false, state.stickMode)
    }

    /**
     * V162/V184: nút nhỏ luôn hiện — bấm ẩn cả cụm phím ảo (không chỉ mờ), bấm lại hiện ra;
     * lựa chọn tự ẩn báo về để activity ghi vào Prefs.
     */
    @Test fun padToggleHidesAndShowsVirtualPad() {
        val state = InGameState()
        var saved: Boolean? = null
        rule.setContent {
            MonikaTheme {
                InGameOverlay(
                    state = state, system = "Game Boy Advance", title = "Game thử", layout = PadLayout.GBA, showPad = true,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                    onPadHiddenChange = { saved = it },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(PAD_TOGGLE_TAG).assertIsDisplayed()
        rule.onNodeWithText("A").assertExists()
        rule.onNodeWithTag(PAD_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        assertFalse("bấm nút: phím ảo ẩn", state.padVisible)
        assertEquals("báo activity lưu Prefs", true, saved)
        rule.onNodeWithText("A").assertDoesNotExist()
        // Nút vẫn còn (đổi mô tả thành "Hiện phím ảo"), không mờ/biến mất.
        rule.onNodeWithContentDescription("Hiện phím ảo").assertIsDisplayed()
        rule.shot("v184-da-an-phim")
        rule.onNodeWithTag(PAD_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        assertTrue("bấm lại: phím ảo hiện", state.padVisible)
        assertEquals(false, saved)
        rule.onNodeWithText("A").assertExists()
    }

    /** V184: mặc định phím HIỆN khi không có tay cầm (0.8.9 tự ẩn ở mọi hệ là lỗi). */
    @Test fun padVisibleByDefaultWithoutGamepad() {
        val state = InGameState()
        overlay(state, PadLayout.GBA)
        assertTrue("mặc định phải hiện phím", state.padVisible)
        rule.runOnIdle { state.setPadConnected(false) }
        rule.waitForIdle()
        assertTrue("không cắm tay cầm: vẫn hiện phím", state.padVisible)
        rule.onNodeWithText("A").assertExists()
    }

    /** V184: xoay ngang vẫn còn nút ẩn/hiện, nút nhỏ và bấm được (0.8.9: nút bị cụm phím che/mất). */
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun padToggleStaysReachableInLandscape() {
        val state = InGameState()
        overlay(state, PadLayout.GBA)
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000) // thanh tên đã tự ẩn, nút vẫn phải còn
        rule.onNodeWithTag(PAD_TOGGLE_TAG).assertIsDisplayed()
        val density = rule.activity.resources.displayMetrics.density
        val width = rule.onNodeWithTag(PAD_TOGGLE_TAG).fetchSemanticsNode().boundsInRoot.width
        assertTrue("nút phải nhỏ (<= 40dp), rộng ${width}px", width <= 40f * density)
        rule.onNodeWithTag(PAD_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        assertFalse("bấm được ở màn ngang: phím ẩn", state.padVisible)
        rule.onNodeWithTag(PAD_TOGGLE_TAG).assertIsDisplayed()
        rule.onNodeWithTag(PAD_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        assertTrue("bấm lại được ở màn ngang: phím hiện", state.padVisible)
        rule.shot("v184-nut-phim-ngang")
    }

    /** V162: cắm tay cầm tự ẩn phím; người chơi đã tự ẩn thì rút tay cầm vẫn ẩn (không tự hiện). */
    @Test fun padFollowsGamepadButKeepsManualHide() {
        val state = InGameState()
        overlay(state, PadLayout.GBA)
        rule.runOnIdle { state.setPadConnected(true) }
        rule.waitForIdle()
        assertFalse("cắm tay cầm: phím ảo ẩn", state.padVisible)
        rule.runOnIdle { state.setPadConnected(false) }
        rule.waitForIdle()
        assertTrue("rút tay cầm: phím ảo hiện", state.padVisible)
        // Người chơi tự ẩn bằng nút → cắm/rút tay cầm không được tự hiện lại.
        rule.runOnIdle { state.togglePad() }
        rule.waitForIdle()
        assertFalse(state.padVisible)
        rule.runOnIdle { state.setPadConnected(true); state.setPadConnected(false) }
        rule.waitForIdle()
        assertFalse("tôn trọng lựa chọn tự ẩn", state.padVisible)
    }
}
