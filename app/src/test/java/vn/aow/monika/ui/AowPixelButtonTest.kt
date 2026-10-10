package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class AowPixelButtonTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    @Test fun normalSelectedPressedDisabledLabelsMeetFourPointFiveContrast() {
        val c = AowColors()
        for (style in AowButtonStyle.entries) for (state in AowButtonState.entries) {
            val p = c.buttonPalette(style, state)
            val a = p.face.luminance(); val b = p.label.luminance()
            val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
            assertTrue("$style/$state: $ratio", ratio >= 4.5f)
        }
        // Viền trắng ngoài / đen trong đọc được trên cả hai nền cực trị.
        assertTrue(c.outerRim.luminance() > .9f)
        assertEquals(0f, c.outline.luminance(), .0001f)
    }

    @Test fun fullLightFixture() = fixture(PerformanceTier.FULL, false)
    @Test fun fullDarkFixture() = fixture(PerformanceTier.FULL, true)
    @Test fun liteLightFixture() = fixture(PerformanceTier.LITE, false)
    @Test fun liteDarkFixture() = fixture(PerformanceTier.LITE, true)
    @Test fun offLightFixture() = fixture(PerformanceTier.OFF, false)
    @Test fun offDarkFixture() = fixture(PerformanceTier.OFF, true)

    private fun fixture(tier: PerformanceTier, dark: Boolean) {
        rule.setContent { MonikaTheme {
            CompositionLocalProvider(LocalMonikaMotion provides MonikaMotion(tier)) {
                val c = Monika.colors
                Column(Modifier.fillMaxSize().background(if (dark) c.surfaceDark else c.bgWarm)
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AowVN — $tier", color = if (dark) c.textOnDark else c.text, style = Monika.type.sectionTitle)
                    Text("Trước: thành phần hiện hành", color = if (dark) c.textOnDark else c.text, style = Monika.type.caption)
                    GradientButton("BẮT ĐẦU", {}, Modifier.fillMaxWidth())
                    DarkButton("TIẾP TỤC", {}, Modifier.fillMaxWidth())
                    Text("Sau: bốn trạng thái pixel", color = if (dark) c.textOnDark else c.text, style = Monika.type.caption)
                    AowButtonState.entries.forEach { state ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AowButtonStyle.entries.forEach { style ->
                                AowPixelSurface(state, Modifier.weight(1f), style) { label ->
                                    Text(when (state) {
                                        AowButtonState.NORMAL -> "THƯỜNG"
                                        AowButtonState.SELECTED -> "CHỌN"
                                        AowButtonState.PRESSED -> "NHẤN"
                                        AowButtonState.DISABLED -> "VÔ HIỆU"
                                    }, color = label, style = Monika.type.button,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
                                }
                            }
                        }
                    }
                    AowPixelButton("Tiếp tục", {}, Modifier.fillMaxWidth(), selected = true)
                    AowPixelButton("Không khả dụng", {}, Modifier.fillMaxWidth(), enabled = false)
                }
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onAllNodesWithText("TIẾP TỤC", substring = false).assertCountEquals(2)
        rule.onNodeWithText("KHÔNG KHẢ DỤNG").assertIsNotEnabled().assertIsDisplayed()
        repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.shot("v85a-${tier.name.lowercase()}-${if (dark) "toi" else "sang"}")
    }

    @Test fun pressCancelDisabledAndSelectionRetainClickContract() {
        var clicks = 0
        val enabled = mutableStateOf(true)
        val selected = mutableStateOf(false)
        rule.setContent { MonikaTheme {
            CompositionLocalProvider(LocalMonikaMotion provides MonikaMotion(PerformanceTier.OFF)) {
                AowPixelButton("Thử nút", { clicks++ }, Modifier.fillMaxWidth(),
                    enabled = enabled.value, selected = selected.value)
            }
        } }
        rule.mainClock.advanceTimeBy(1_000)
        val node = rule.onNodeWithText("THỬ NÚT")
        node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đang nhấn"))
        node.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(1_000)
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sẵn sàng"))
        assertEquals(0, clicks)
        node.performClick(); assertEquals(1, clicks)
        rule.runOnIdle { selected.value = true }
        rule.mainClock.advanceTimeBy(1_000)
        node.assertIsSelected()
        rule.runOnIdle { enabled.value = false }
        rule.mainClock.advanceTimeBy(1_000)
        node.assertIsNotEnabled().performTouchInput { click(center) }
        assertEquals(1, clicks)
    }
}
