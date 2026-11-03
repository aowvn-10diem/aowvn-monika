package vn.aow.monika.ui

import androidx.activity.ComponentActivity
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
import vn.aow.monika.AppGraph
import vn.aow.monika.runner.CoreOption
import vn.aow.monika.runner.CoreOptions
import vn.aow.monika.runner.InGameOverlay
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.PadLayout
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * V188: bảng "Tùy chọn giả lập" của NDS chia 3 nhóm cố định, nhãn tiếng Việt, bố cục chọn bằng chip
 * lấy từ config (3 bố cục), khoá lạ vẫn hiện.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class NdsOptionsPanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val text get() = AppGraph.config.current.coreOptionText
    private val screen get() = AppGraph.config.current.cores.getValue("melondsds").screen!!

    @Before fun setup() { rule.mainClock.autoAdvance = false }

    private fun parse(key: String, description: String, current: String): CoreOption =
        CoreOptions.parse(key, description, current, text) ?: error("không đọc được $key")

    /** Danh sách giống lõi melonDS DS khai báo, đủ cả 3 nhóm + 1 khoá lạ. */
    private fun options() = listOf(
        parse("melonds_screen_layout1", "Screen Layout; Top/Bottom|Left/Right|Hybrid Bottom", "Top/Bottom"),
        parse("melonds_screen_gap", "Screen Gap; 0|4|8", "0"),
        // Khoá lạ nhưng tên gốc đã biết → vẫn dịch; nhóm theo từ khoá "layout".
        parse("melonds_screen_layout_count", "Number of Screen Layouts; 1|2|3", "1"),
        parse("melonds_internal_resolution", "Internal Resolution; 1|2|3", "1"),
        parse("melonds_render_mode", "Render Mode; software|opengl", "software"),
        parse("melonds_jit_enable", "JIT Enable (Restart); enabled|disabled", "enabled"),
        parse("melonds_threaded_renderer", "Threaded software renderer; disabled|enabled", "enabled"),
        parse("melonds_mystery_switch", "Mystery Switch; a|b", "a"),
    )

    private fun show(options: List<CoreOption>, onChange: (CoreOption, String) -> Unit = { _, _ -> }) {
        rule.setContent {
            MonikaTheme {
                InGameOverlay(
                    state = InGameState().apply { this.options = options; coreId = "melondsds" },
                    system = "Nintendo DS", title = "Game thử NDS", layout = PadLayout.NDS, showPad = true,
                    send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {},
                    onEditDone = {}, onOptionChange = onChange, screen = screen,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun top(label: String) = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test fun optionsAreGroupedInFixedOrderWithVietnameseTitles() {
        show(options())
        listOf("Màn hình", "Bố cục", "Hiệu năng", "Khác").forEach { rule.onNodeWithText(it).assertExists() }
        val tops = listOf("Màn hình", "Bố cục", "Hiệu năng").associateWith { top(it) }
        assertTrue("Thứ tự nhóm sai: $tops", tops.getValue("Màn hình") < tops.getValue("Bố cục"))
        assertTrue("Thứ tự nhóm sai: $tops", tops.getValue("Bố cục") < tops.getValue("Hiệu năng"))
        // Tùy chọn chưa xếp nhóm vẫn hiện (nhóm "Khác" nằm cuối — thứ tự kiểm ở NdsSettingsTest vì phần dưới bị cuộn).
        // Nhãn đã Việt hoá (khoá lạ giữ nguyên tên gốc).
        rule.onNodeWithText("Bố cục 2 màn hình").assertExists()
        rule.onNodeWithText("Số bố cục màn hình đang dùng").assertExists()
        rule.onNodeWithText("Bật JIT – tăng tốc (mở lại game)").assertExists()
        // "Cách vẽ 3D" (render mode) nằm trong nhóm Màn hình, trên tiêu đề "Bố cục".
        rule.onNodeWithText("Cách vẽ 3D (phần mềm / OpenGL)").assertExists()
        assertTrue("Render Mode phải ở nhóm Màn hình",
            top("Cách vẽ 3D (phần mềm / OpenGL)") < top("Bố cục"))
        rule.onNodeWithText("Mystery Switch").assertExists()
        rule.shot("v188-tuy-chon-nds-nhom")
    }

    @Test fun layoutIsChosenWithThreeChipsFromConfig() {
        var changed: Pair<String, String>? = null
        show(options()) { o, v -> changed = o.key to v }
        // Đúng 3 bố cục của config, nhãn lấy từ config.
        listOf("Trên – dưới", "Cạnh nhau", "Một màn lớn").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("Cạnh nhau").performClick()
        rule.waitForIdle()
        assertEquals("melonds_screen_layout1" to "left-right", changed)
    }

    @Test fun savedLayoutShownWhenReopeningPanel() {
        // Người chơi đã chọn "Cạnh nhau" trước đó → mở lại bảng vẫn thấy đúng bố cục ấy.
        val saved = options().map {
            if (it.key == "melonds_screen_layout1") it.copy(value = "Left/Right") else it
        }
        show(saved)
        rule.onNodeWithText("Cạnh nhau").assertExists()
        rule.onNodeWithText("Bố cục 2 màn hình").assertExists()
    }
}
