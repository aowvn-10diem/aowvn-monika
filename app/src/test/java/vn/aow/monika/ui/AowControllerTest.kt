package vn.aow.monika.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
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
class AowControllerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    @Test fun fullLight() = fixture(PerformanceTier.FULL, false)
    @Test fun fullDark() = fixture(PerformanceTier.FULL, true)
    @Test fun liteLight() = fixture(PerformanceTier.LITE, false)
    @Test fun liteDark() = fixture(PerformanceTier.LITE, true)
    @Test fun offLight() = fixture(PerformanceTier.OFF, false)
    @Test fun offDark() = fixture(PerformanceTier.OFF, true)

    private fun fixture(tier: PerformanceTier, dark: Boolean) {
        rule.setContent { MonikaTheme {
            Box(Modifier.fillMaxSize().background(if (dark) Monika.colors.surfaceDark else Monika.colors.bgWarm)) {
                CompositionLocalProvider(LocalAowControllerStyle provides true,
                    LocalControllerOptions provides ControllerOptions(haptic = HapticLevel.OFF),
                    LocalMonikaMotion provides MonikaMotion(tier)) {
                    Column(Modifier.padding(24.dp).alpha(.65f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row { MonikaKey("A", {}); MonikaKey("B", {}, selected = true)
                            MonikaKey("X", {}, visualState = ControlVisualState.PRESSED)
                            MonikaKey("Y", {}, enabled = false) }
                        Row { MonikaPill("L", {}); MonikaPill("R", {}, selected = true)
                            MonikaPill("ZL", {}, visualState = ControlVisualState.HELD)
                            MonikaPill("ZR", {}, enabled = false) }
                        Row { MonikaDPad({ _, _ -> }); MonikaStick({ _, _ -> }) }
                        Row { MonikaPill("SELECT", {}); MonikaPill("START", {}) }
                    }
                }
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        listOf("A", "B", "X", "Y", "L", "R", "ZL", "ZR", "SELECT", "START")
            .forEach { rule.onNodeWithContentDescription("Nút $it").assertIsDisplayed() }
        rule.onNodeWithContentDescription("Nút B").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Đang chọn"))
        rule.onNodeWithContentDescription("Nút Y").assertIsNotEnabled()
        rule.shot("v85b-${tier.name.lowercase()}-${if (dark) "toi" else "sang"}")
    }

    @Test fun whiteLabelsMeetContrastAtDefaultOpacityOnBothGameFrames() {
        rule.setContent { MonikaTheme {
            val c = Monika.colors
            for (frame in listOf(c.bgWarm, c.surfaceDark)) {
                val palette = c.aow.controllerColors()
                for (face in AowButtonState.entries.map { palette.buttonPalette(AowButtonStyle.DARK, it).face } + c.aow.padSurface) {
                    val a = c.aow.onDark.copy(alpha = .65f).compositeOver(frame).luminance()
                    val b = face.copy(alpha = .65f).compositeOver(frame).luminance()
                    assertTrue("Chữ trắng phải >=4.5:1 ở độ mờ 65%", (maxOf(a,b)+.05f)/(minOf(a,b)+.05f) >= 4.5f)
                }
            }
        } }
    }

    @Test fun pressCancelAndDisabledKeepOriginalEdgesAndTouchBounds() {
        val edges = mutableListOf<Boolean>()
        val enabled = mutableStateOf(true)
        rule.setContent { MonikaTheme {
            CompositionLocalProvider(LocalAowControllerStyle provides true,
                LocalControllerOptions provides ControllerOptions(haptic = HapticLevel.OFF)) {
                MonikaKey("A", { edges += it }, enabled = enabled.value)
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        val key = rule.onNodeWithContentDescription("Nút A")
        key.assertWidthIsAtLeast(72.dp).assertHeightIsAtLeast(72.dp)
        key.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(true), edges)
        key.performTouchInput { cancel() }
        rule.waitForIdle(); assertEquals(listOf(true, false), edges)
        rule.runOnIdle { enabled.value = false }; rule.mainClock.advanceTimeByFrame()
        rule.waitUntil(2_000) { rule.onAllNodes(isNotEnabled()).fetchSemanticsNodes().isNotEmpty() }
        key.assertIsNotEnabled().performTouchInput { click(center) }
        assertEquals(listOf(true, false), edges)
    }

    @Test fun diagonalAndAnalogCancelReleaseImmediately() {
        val keys = mutableListOf<Pair<Int, Int>>()
        val moves = mutableListOf<Pair<Float, Float>>()
        rule.setContent { MonikaTheme {
            CompositionLocalProvider(LocalAowControllerStyle provides true,
                LocalControllerOptions provides ControllerOptions(haptic = HapticLevel.OFF)) {
                Row { MonikaDPad({ a,k -> keys += a to k }); MonikaStick({ x,y -> moves += x to y }) }
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        val pad = rule.onNodeWithContentDescription("Phím hướng, hỗ trợ đi chéo")
        pad.performTouchInput { down(Offset(width*.85f, height*.15f)) }
        rule.waitForIdle()
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT), keys.filter { it.first==KeyEvent.ACTION_DOWN }.map { it.second }.toSet())
        pad.performTouchInput { cancel() }; rule.waitForIdle()
        assertEquals(keys.filter { it.first==KeyEvent.ACTION_DOWN }.map { it.second }.toSet(), keys.filter { it.first==KeyEvent.ACTION_UP }.map { it.second }.toSet())
        val stick = rule.onNodeWithContentDescription("Cần analog")
        stick.performTouchInput { down(Offset(width*.9f,height*.5f)) }
        rule.waitForIdle(); assertTrue(moves.last().first > 0f)
        stick.performTouchInput { cancel() }; rule.waitForIdle()
        assertEquals(0f to 0f, moves.last())
    }
}
