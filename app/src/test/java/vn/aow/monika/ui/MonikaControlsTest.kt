package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.controls.*
import vn.aow.monika.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class MonikaControlsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    @Test fun holdHasOneDownAndCancelAlwaysReleases() {
        val edges = mutableListOf<Boolean>()
        rule.setContent { MonikaTheme { MonikaKey("A", { edges += it }) } }
        rule.mainClock.advanceTimeBy(1_000)
        val key = rule.onNodeWithContentDescription("Nút A")
        key.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(true), edges)
        rule.shot("v70a-giu-phim")
        key.performTouchInput { cancel() }
        rule.waitForIdle()
        assertEquals(listOf(true, false), edges)
    }

    @Test fun replacingCallbackWhileHoldingReleasesOriginalTarget() {
        val old = mutableListOf<Boolean>(); val new = mutableListOf<Boolean>()
        val replacement = mutableStateOf(false)
        val first: (Boolean) -> Unit = { old += it }
        val second: (Boolean) -> Unit = { new += it }
        rule.setContent { MonikaTheme { MonikaKey("A", if (replacement.value) second else first) } }
        rule.mainClock.advanceTimeBy(1_000)
        val key = rule.onNodeWithContentDescription("Nút A")
        key.performTouchInput { down(center) }
        rule.runOnIdle { replacement.value = true }
        rule.mainClock.advanceTimeByFrame()
        key.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(true, false), old)
        assertEquals(emptyList<Boolean>(), new)
    }

    @Test fun disabledAndOffVisualStates() {
        val edges = mutableListOf<Boolean>()
        rule.setContent {
            MonikaTheme {
                CompositionLocalProvider(LocalMonikaMotion provides MonikaMotion(PerformanceTier.OFF)) {
                    Column {
                        ControlVisualState.entries.forEachIndexed { index, state ->
                            MonikaKey(listOf("A", "B", "X", "Y", "Z")[index], { edges += it }, primary = true, visualState = state)
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Nút Z").assertIsNotEnabled().performTouchInput { click(center) }
        assertEquals(emptyList<Boolean>(), edges)
        rule.shot("v70a-trang-thai-off")
    }

    @Test fun fullMotionPressHeldTurboDisabledScreens() {
        rule.setContent {
            MonikaTheme {
                CompositionLocalProvider(LocalMonikaMotion provides MonikaMotion(PerformanceTier.FULL)) {
                    Column {
                        ControlVisualState.entries.forEachIndexed { index, state ->
                            MonikaKey(listOf("A", "B", "X", "Y", "Z")[index], {}, primary = true, visualState = state)
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Nút Z").assertIsNotEnabled()
        rule.shot("v70a-trang-thai-full")
    }
}
