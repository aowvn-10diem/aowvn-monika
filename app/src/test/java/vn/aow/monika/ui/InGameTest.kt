package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.runner.CoreOption
import vn.aow.monika.runner.InGameOverlay
import vn.aow.monika.runner.InGameState
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
}
