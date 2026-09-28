package vn.aow.monika.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Mở app thật (MainActivity), bấm tab Tìm kiếm trên menu nổi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class MainNavTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun manualClock() { rule.mainClock.autoAdvance = false }

    @Test fun searchTab() {
        rule.mainClock.advanceTimeBy(1_000)
        rule.shot("11-menu-noi-5-tab")
        rule.onNodeWithContentDescription("Tìm kiếm").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.shot("12-tab-tim-kiem")
    }
}
