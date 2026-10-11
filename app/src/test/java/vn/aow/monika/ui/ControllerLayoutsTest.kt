package vn.aow.monika.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.Prefs
import vn.aow.monika.runner.*
import vn.aow.monika.ui.controls.*
import vn.aow.monika.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ControllerLayoutsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clockAndOptions() {
        rule.mainClock.autoAdvance = false
        Prefs(rule.activity).controllerOptions = ControllerOptions(haptic = HapticLevel.OFF)
    }

    private fun layout(layout: PadLayout, label: String, key: Int, orientation: String) {
        val events = mutableListOf<Pair<Int, Int>>()
        val aow = mutableStateOf(false)
        rule.setContent { MonikaTheme {
            Box(Modifier.fillMaxSize()) {
                VirtualPad(layout, InGameState(), { a, k -> events += a to k }, { _, _, _ -> }, Modifier.align(Alignment.BottomCenter), aowStyle = aow.value)
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.shot("v85b-truoc-${layout.name.lowercase()}-$orientation")
        rule.runOnIdle { aow.value = true }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Nút $label").performTouchInput { click(center) }
        // Clock tay: vẽ lại sau ACTION_UP trước khi lưu ảnh trạng thái đã nhả.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals(listOf(KeyEvent.ACTION_DOWN to key, KeyEvent.ACTION_UP to key), events)
        rule.onNodeWithContentDescription("Nút $label").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Sẵn sàng"))
        if (layout == PadLayout.N64) {
            val a = rule.onNodeWithContentDescription("Nút A").fetchSemanticsNode().boundsInWindow
            val b = rule.onNodeWithContentDescription("Nút B").fetchSemanticsNode().boundsInWindow
            listOf("C ▲", "C ◀", "C ▼", "C ▶").forEach {
                val node = rule.onNodeWithContentDescription("Nút $it").assertIsDisplayed()
                val c = node.fetchSemanticsNode().boundsInWindow
                assertFalse("Vùng chạm A/C chồng nhau", a.overlaps(c))
                assertFalse("Vùng chạm B/C chồng nhau", b.overlaps(c))
            }
            rule.onNodeWithContentDescription("Nút START").assertIsDisplayed()
        }
        rule.shot("v70a-${layout.name.lowercase()}-$orientation")
        rule.shot("v85b-sau-${layout.name.lowercase()}-$orientation")
    }

    @Test fun gbaPortrait() { layout(PadLayout.GBA, "A", KeyEvent.KEYCODE_BUTTON_B, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun gbaLandscape() { layout(PadLayout.GBA, "A", KeyEvent.KEYCODE_BUTTON_B, "ngang") }

    @Test fun ndsPortrait() { layout(PadLayout.NDS, "A", KeyEvent.KEYCODE_BUTTON_B, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun ndsLandscape() { layout(PadLayout.NDS, "A", KeyEvent.KEYCODE_BUTTON_B, "ngang") }

    @Test fun psPortrait() { layout(PadLayout.PS, "×", KeyEvent.KEYCODE_BUTTON_A, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun psLandscape() { layout(PadLayout.PS, "×", KeyEvent.KEYCODE_BUTTON_A, "ngang") }

    @Test fun snesPortrait() { layout(PadLayout.SNES, "A", KeyEvent.KEYCODE_BUTTON_B, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun snesLandscape() { layout(PadLayout.SNES, "A", KeyEvent.KEYCODE_BUTTON_B, "ngang") }

    @Test fun genPortrait() { layout(PadLayout.GEN, "C", KeyEvent.KEYCODE_BUTTON_B, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun genLandscape() { layout(PadLayout.GEN, "C", KeyEvent.KEYCODE_BUTTON_B, "ngang") }

    @Test fun n64Portrait() { layout(PadLayout.N64, "A", KeyEvent.KEYCODE_BUTTON_A, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun n64Landscape() { layout(PadLayout.N64, "A", KeyEvent.KEYCODE_BUTTON_A, "ngang") }

    @Test fun dcPortrait() { layout(PadLayout.DC, "A", KeyEvent.KEYCODE_BUTTON_A, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun dcLandscape() { layout(PadLayout.DC, "A", KeyEvent.KEYCODE_BUTTON_A, "ngang") }

    @Test fun pspPortrait() { layout(PadLayout.PSP, "×", KeyEvent.KEYCODE_BUTTON_A, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun pspLandscape() { layout(PadLayout.PSP, "×", KeyEvent.KEYCODE_BUTTON_A, "ngang") }

    @Test fun n3dsPortrait() { layout(PadLayout.N3DS, "A", KeyEvent.KEYCODE_BUTTON_B, "doc") }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun n3dsLandscape() { layout(PadLayout.N3DS, "A", KeyEvent.KEYCODE_BUTTON_B, "ngang") }
}
