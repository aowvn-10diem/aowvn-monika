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
                    state = state, system = "Nintendo DS", title = "Pokemon Việt Hóa", layout = layout, showPad = true,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                    onOptionChange = onOptionChange,
                )
            }
        }.also { rule.mainClock.advanceTimeBy(1_000) }

    @Test fun padNds() { overlay(InGameState(), PadLayout.NDS); rule.shot("6-tay-cam-nds") }
    @Test fun padGba() { overlay(InGameState(), PadLayout.GBA); rule.shot("7-tay-cam-gba") }
    @Test fun padPs() { overlay(InGameState(), PadLayout.PS); rule.shot("8-tay-cam-ps") }

    @Test fun menu() {
        val s = InGameState().apply { menuOpen = true; filledSlots = setOf(1, 3) }
        overlay(s, PadLayout.NDS)
        rule.onNodeWithText("Tùy chọn giả lập").assertExists()
        rule.shot("9-menu-nhanh")
    }

    @Test fun optionsPanel() {
        val s = InGameState().apply {
            options = listOf(
                CoreOption("melonds_screen_layout", "Screen Layout", listOf("Top/Bottom", "Left/Right", "Hybrid"), "Top/Bottom"),
                CoreOption("melonds_threaded_renderer", "Threaded software renderer", listOf("disabled", "enabled"), "enabled"),
            )
        }
        var changed: Pair<String, String>? = null
        overlay(s, PadLayout.NDS) { o, v -> changed = o.key to v }
        rule.shot("10-tuy-chon-gia-lap")
        rule.onNodeWithText("Screen Layout").performClick()
        rule.waitForIdle()
        assertEquals("melonds_screen_layout" to "Left/Right", changed)
    }
}
