package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.runner.AutoHide
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.MENU_BUTTON_TAG
import vn.aow.monika.runner.REVEAL_EDGE_TAG
import vn.aow.monika.runner.RenpyOverlay
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

    /** A31: nút menu của Kirikiri / RPG Maker / Ren'Py tự ẩn khi đang chơi; chạm góc phải trên hiện lại; đang mở menu thì luôn hiện. */
    private fun assertMenuButtonAutoHides(name: String, show: (open: Boolean) -> Unit) {
        show(false)
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertIsDisplayed()
        rule.shot("$name-nut-hien")
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertDoesNotExist()
        // V92: TalkBack/Android accessibility có action hiện lại, không chỉ tag Compose.
        rule.onNodeWithContentDescription("Hiện tên game và menu")
            .assertIsDisplayed().assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertDoesNotExist()
        rule.shot("$name-nut-an")
        // V92: cùng selector với Maestro → chạm đúng vùng A31 để hiện lại, rồi lại tự ẩn.
        rule.onNodeWithTag(REVEAL_EDGE_TAG).assertIsDisplayed().performTouchInput { click() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertDoesNotExist()
    }

    @Test fun kirikiriMenuButtonAutoHides() = assertMenuButtonAutoHides("v89-kirikiri") { kirikiri(it) }

    @Test fun rgssMenuButtonAutoHides() = assertMenuButtonAutoHides("v89-rgss") { rgss(it) }

    @Test fun renpyMenuButtonAutoHides() = assertMenuButtonAutoHides("v89-renpy") { open ->
        rule.setContent { RenpyOverlay(title = "Game Ren'Py", open = open, onOpen = {}, onExit = {}) }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test fun menuButtonStaysWhileMenuOpen() {
        kirikiri(open = true)
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.onNodeWithTag(MENU_BUTTON_TAG).assertIsDisplayed()
    }
}
