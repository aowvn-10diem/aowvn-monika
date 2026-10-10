package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.controls.*
import vn.aow.monika.ui.theme.*
import vn.aow.monika.runner.InGameState
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class MonikaControlsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    @Test fun controlsOnDarkGameFrame() = contrastFixture("toi")
    @Test fun controlsOnLightGameFrame() = contrastFixture("sang")
    @Test fun controlsOnMixedGameFrame() = contrastFixture("game")

    private fun contrastFixture(backdrop: String) {
        val held = mutableStateOf(false)
        rule.setContent { MonikaTheme {
            val c = Monika.colors
            Box(Modifier.fillMaxSize().background(if (backdrop == "sang") c.bgWarm else c.surfaceDark)) {
                if (backdrop == "game") Canvas(Modifier.fillMaxSize()) {
                    val tile = 64.dp.toPx()
                    for (x in 0..(size.width / tile).toInt()) for (y in 0..(size.height / tile).toInt()) {
                        drawRect(if ((x + y) % 2 == 0) c.accentBlue else c.surfaceDark,
                            Offset(x * tile, y * tile), Size(tile, tile))
                    }
                }
                CompositionLocalProvider(LocalMonikaMotion provides MonikaMotion(PerformanceTier.OFF)) {
                    val state = if (held.value) ControlVisualState.HELD else ControlVisualState.RELEASED
                    Column(Modifier.padding(24.dp).alpha(InGameState().opacity), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MonikaPill("L", {}, visualState = state)
                            MonikaPill("R", {}, visualState = state)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonikaDPad({ _, _ -> })
                            Column { MonikaKey("A", {}, primary = true, visualState = state); MonikaKey("B", {}, visualState = state) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MonikaPill("SELECT", {}, visualState = state)
                            MonikaPill("START", {}, visualState = state)
                        }
                        MonikaStick({ _, _ -> })
                    }
                }
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        listOf("L", "R", "A", "B", "SELECT", "START").forEach { rule.onNodeWithContentDescription("Nút $it").assertIsDisplayed() }
        rule.onNodeWithContentDescription("Phím hướng, hỗ trợ đi chéo").assertIsDisplayed()
        rule.shot("v75-$backdrop-tha")
        rule.runOnIdle { held.value = true }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Nút B").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Đang giữ"))
        rule.shot("v75-$backdrop-giu")
    }

    @Test fun primaryOutlineHasThreeToOneContrastAtDefaultOverlayOpacity() {
        rule.setContent { MonikaTheme {
            Box(Modifier.fillMaxSize().background(Monika.colors.bg)) {
                Column(Modifier.padding(32.dp).alpha(InGameState().opacity)) {
                    MonikaKey("A", {}, primary = true)
                }
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.shot("v70a-tuong-phan-nen-sang")
        val bounds = rule.onNodeWithContentDescription("Nút A").fetchSemanticsNode().boundsInWindow
        val density = rule.activity.resources.displayMetrics.density
        val image = BitmapFactory.decodeFile(File("build/screenshots/v70a-tuong-phan-nen-sang.png").absolutePath)
        // Điểm giữa viền trên, cách mép tròn 1 dp; nền bên phải cách vùng chạm 16 dp.
        val rimY = bounds.top + (bounds.height - 64f * density) / 2f + density
        val rim = Color(image.getPixel(bounds.center.x.toInt(), rimY.toInt())).luminance()
        val background = Color(image.getPixel((bounds.right + 16f * density).toInt(), bounds.center.y.toInt())).luminance()
        val contrast = (maxOf(rim, background) + .05f) / (minOf(rim, background) + .05f)
        println("V70a primary outline/light background contrast=$contrast:1 at default overlay opacity")
        assertTrue("Đường bao nút chính cần tương phản >=3:1, đo được $contrast", contrast >= 3f)
        image.recycle()
    }

    @Test fun pillHasSeventyTwoDpTouchTargetOutsideItsFortyDpFace() {
        val edges = mutableListOf<Boolean>()
        rule.setContent { MonikaTheme {
            CompositionLocalProvider(LocalControllerOptions provides ControllerOptions(size = .7f, haptic = HapticLevel.OFF)) {
                MonikaPill("START", { edges += it })
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        val pill = rule.onNodeWithContentDescription("Nút START")
        pill.assertHeightIsAtLeast(72.dp).assertWidthIsAtLeast(72.dp)
        rule.shot("v70a-pill-vung-cham72")
        pill.performTouchInput { click(Offset(center.x, 1f)) }
        rule.waitForIdle()
        assertEquals(listOf(true, false), edges)
    }

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
