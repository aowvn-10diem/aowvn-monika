package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.runner.CoreOptions
import vn.aow.monika.ui.controls.ScreenSettings
import vn.aow.monika.ui.theme.MonikaTheme

/** V78c: Cài đặt → Màn hình (NDS): chọn bố cục / tỉ lệ / khoảng cách → ghi đúng khóa lõi melonDS DS. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenSettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val core get() = AppGraph.config.current.cores.getValue("melondsds")
    private fun saved() = CoreOptions.saved(rule.activity, "melondsds")

    @Before fun reset() {
        rule.mainClock.autoAdvance = false
        CoreOptions.reset(rule.activity, "melondsds")
    }

    private fun show() {
        rule.setContent { MonikaTheme { Column(Modifier.padding(16.dp)) { ScreenSettings("melondsds", core.screen!!, core.options) } } }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test fun layoutRatioAndGapAreSavedAsCoreOptions() {
        show()
        rule.onNodeWithText("Trên – dưới").assertExists()
        rule.onNodeWithText("Cạnh nhau").assertExists()
        rule.onNodeWithText("2 : 1").assertDoesNotExist() // chỉ "Một màn lớn" dùng tỉ lệ
        rule.shot("v78c-man-hinh-xep-doc")

        rule.onNodeWithText("Cạnh nhau").performClick()
        assertEquals("left-right", saved()["melonds_screen_layout1"])
        rule.onNodeWithText("2 : 1").assertDoesNotExist()

        rule.onNodeWithText("Một màn lớn").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals("hybrid-bottom", saved()["melonds_screen_layout1"])
        rule.onNodeWithText("3 : 1").assertExists()
        rule.onNodeWithText("3 : 1").performClick()
        assertEquals("3", saved()["melonds_hybrid_ratio"])
        rule.shot("v78c-man-hinh-mot-man-lon")

        rule.onNodeWithContentDescription("Khoảng cách giữa hai màn")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals("12", saved()["melonds_screen_gap"])
        rule.onNodeWithText("Khoảng cách giữa hai màn: 12 px").assertExists()
    }

    @Test fun resetDropsOnlyManagedKeys() {
        CoreOptions.save(rule.activity, "melondsds", "melonds_screen_layout1", "left-right")
        CoreOptions.save(rule.activity, "melondsds", "melonds_screen_gap", "10")
        CoreOptions.save(rule.activity, "melondsds", "melonds_jit_enable", "disabled")
        show()
        rule.onNodeWithText("Về mặc định").performClick()
        assertNull(saved()["melonds_screen_layout1"])
        assertNull(saved()["melonds_screen_gap"])
        assertEquals("disabled", saved()["melonds_jit_enable"]) // tùy chọn khác của lõi giữ nguyên
    }

    @Test fun showsSavedChoiceOnOpen() {
        CoreOptions.save(rule.activity, "melondsds", "melonds_screen_layout1", "hybrid-bottom")
        show()
        rule.onNodeWithText("3 : 1").assertExists() // bố cục "Một màn lớn" đang chọn → hiện tỉ lệ
    }
}
