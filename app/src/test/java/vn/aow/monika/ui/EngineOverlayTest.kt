package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.KirikiriOverlay
import vn.aow.monika.runner.PadLayout
import vn.aow.monika.runner.RgssOverlay

/** Ảnh chụp hai trạng thái menu của lớp phủ Kirikiri và RPG Maker. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class EngineOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun manualClock() { rule.mainClock.autoAdvance = false }

    private fun kirikiri(open: Boolean) {
        rule.setContent {
            KirikiriOverlay(
                title = "Game Kirikiri",
                open = open,
                fastForward = false,
                onOpen = {},
                onGameMenu = {},
                onFastForward = {},
                onExit = {},
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun rgss(open: Boolean) {
        val state = InGameState().apply { menuOpen = open }
        rule.setContent {
            RgssOverlay(
                state = state,
                title = "Game RPG Maker",
                shiftHeld = false,
                onSend = { _, _ -> },
                onShift = {},
                onOpacity = {},
                onExit = {},
            )
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test fun kirikiriMenuClosed() {
        kirikiri(open = false)
        rule.shot("20-menu-kirikiri-dong")
    }

    @Test fun kirikiriMenuOpen() {
        kirikiri(open = true)
        rule.shot("21-menu-kirikiri-mo")
    }

    @Test fun rgssMenuClosed() {
        rgss(open = false)
        rule.shot("22-menu-rgss-dong")
    }

    @Test fun rgssMenuOpen() {
        rgss(open = true)
        rule.shot("23-menu-rgss-mo")
    }
}
