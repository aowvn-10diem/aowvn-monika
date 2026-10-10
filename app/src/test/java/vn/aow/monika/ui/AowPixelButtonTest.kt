package vn.aow.monika.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
                                AowPixelSurface(state, Modifier.weight(1f).testTag("$state-$style"), style) { label ->
                                    Text(when (state) {
                                        AowButtonState.NORMAL -> "THƯỜNG"
                                        AowButtonState.SELECTED -> "CHỌN"
                                        AowButtonState.PRESSED -> "NHẤN"
                                        AowButtonState.DISABLED -> "VÔ HIỆU"
                                    }, color = label, style = Monika.type.button,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                                            .testTag("label-$state-$style"))
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
        // Lưu chính frame đã kiểm glyph: lần draw thứ hai có thể dùng display-list
        // chưa đầy đủ trong Robolectric native graphics.
        var captured: Bitmap? = null
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.invalidate(); view.draw(Canvas(bmp)) }
            val ready = try {
                AowButtonState.entries.all { state ->
                    AowButtonStyle.entries.all { style ->
                        val box = rule.onNodeWithTag("label-$state-$style", useUnmergedTree = true)
                            .fetchSemanticsNode().boundsInWindow
                        val white = style == AowButtonStyle.DARK || state == AowButtonState.PRESSED
                        var ink = 0
                        for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(bmp.height))
                            for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(bmp.width)) {
                                val light = Color(bmp.getPixel(x, y)).luminance()
                                if (if (white) light > .9f else light < .01f) ink++
                            }
                        ink >= 10
                    }
                }
            } catch (e: Throwable) { bmp.recycle(); throw e }
            if (ready) captured = bmp else bmp.recycle()
            ready
        }
        val name = "v85a-${tier.name.lowercase()}-${if (dark) "toi" else "sang"}"
        val bitmap = requireNotNull(captured)
        try {
            File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        val bounds = rule.onNodeWithTag("SELECTED-ORANGE").fetchSemanticsNode().boundsInWindow
        val density = rule.activity.resources.displayMetrics.density
        val image = BitmapFactory.decodeFile(File("build/screenshots/$name.png").absolutePath)
        try {
            val inner = Color(image.getPixel(bounds.center.x.toInt(), (bounds.top + 4f*density).toInt()))
            val outer = Color(image.getPixel(bounds.center.x.toInt(), (bounds.top + 2f*density).toInt()))
            assertTrue("Viền đen phải còn bên trong viền chọn vàng", inner.luminance() < .01f)
            assertTrue("Viền chọn vàng phải sáng", outer.luminance() > .7f)
        } finally { image.recycle() }
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
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            val semantics = node.fetchSemanticsNode().config
            semantics.contains(SemanticsProperties.Disabled) &&
                semantics.getOrNull(SemanticsProperties.StateDescription) == "Vô hiệu"
        }
        try {
            node.assertIsNotEnabled()
            node.performTouchInput { click(center) }
        } catch (e: AssertionError) {
            println("V85a transition: ${e.message}\n${node.printToString()}")
            throw e
        }
        assertEquals(1, clicks)
    }
}
