package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.KirikiriOverlay
import vn.aow.monika.runner.RgssOverlay
import vn.aow.monika.ui.theme.MonikaTheme

/** Lớp phủ nhúng sâu: RPG Maker (RgssOverlay) và Kirikiri (KirikiriOverlay). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class RgssKirikiriOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun manualClock() { rule.mainClock.autoAdvance = false }

    private fun rgssOverlay(state: InGameState = InGameState()) {
        rule.setContent {
            MonikaTheme {
                RgssOverlay(
                    state = state, title = "Game XP", shiftHeld = false,
                    onSend = { _, _ -> }, onShift = {}, onOpacity = {}, onExit = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun kirikiriOverlay(state: InGameState = InGameState()) {
        rule.setContent {
            MonikaTheme {
                KirikiriOverlay(
                    state = state, title = "Visual Novel", onSend = { _, _ -> },
                    onExit = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun rgssOverlayClosed() {
        rgssOverlay()
        rule.shot("rgss-overlay-menu-dong")
    }

    @Test
    fun rgssOverlayOpen() {
        val s = InGameState().apply { menuOpen = true }
        rgssOverlay(s)
        rule.onNodeWithText("Chơi tiếp").assertExists()
        rule.onNodeWithText("Chạy nhanh (giữ Shift): tắt").assertExists()
        rule.onNodeWithText("Thoát game").assertExists()
        rule.shot("rgss-overlay-menu-mo")
    }

    @Test
    fun kirikiriOverlayClosed() {
        kirikiriOverlay()
        rule.shot("kirikiri-overlay-menu-dong")
    }

    @Test
    fun kirikiriOverlayOpen() {
        val s = InGameState().apply { menuOpen = true }
        kirikiriOverlay(s)
        rule.onNodeWithText("Menu game").assertExists()
        rule.shot("kirikiri-overlay-menu-mo")
    }
}
