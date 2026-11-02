package vn.aow.monika.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.Prefs
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.CoreScreen
import vn.aow.monika.runner.AutoHide
import vn.aow.monika.runner.DsFrame
import vn.aow.monika.runner.DsScreen
import vn.aow.monika.runner.InGameOverlay
import vn.aow.monika.runner.InGameState
import vn.aow.monika.runner.PadLayout
import vn.aow.monika.ui.controls.ControllerOptions
import vn.aow.monika.ui.controls.HapticLevel
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * V160: ảnh trước/sau ở **thế ngang** cho NDS — hai màn + phím ảo phủ lên vùng game.
 *
 * - Ảnh "trước" = bố cục lõi đang dùng nếu không ai đổi khi xoay (mặc định config `top-bottom`): trong màn ngang
 *   khung chỉ dùng ~1/3 bề rộng (bị bóp, thừa viền đen hai bên).
 * - Ảnh "sau" = `DsScreen.rotationLayout(screen, saved, landscape = true)` trả `left-right` (config
 *   `cores.melondsds.screen.landscapeLayout`): hai màn cạnh nhau rộng hết màn.
 *
 * Phần **thật**: phím ảo là `InGameOverlay` (production), bố cục lấy từ `DsScreen` + `DsFrame`.
 * Phần **thay thế**: vùng màn NDS là bề mặt có nhãn "Màn trên"/"Màn dưới" — khung hình GL của lõi không dựng được
 * trong Robolectric; ảnh này **không phải** ảnh máy ảo/emulator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w851dp-h393dp-land-xxhdpi")
class NdsLandscapeShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setup() {
        rule.mainClock.autoAdvance = false
        Prefs(rule.activity).controllerOptions = ControllerOptions(haptic = HapticLevel.OFF)
    }

    @Test fun truocNdsNgangKhiXoayKhongDoiBoCuc() = ndsLandscapeShot(sau = false, name = "v160-truoc-nds-ngang")

    @Test fun sauNdsNgangTheoBoCucConfig() = ndsLandscapeShot(sau = true, name = "v160-sau-nds-ngang")

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun ndsScreen(): CoreScreen =
        ConfigRepository.parse(app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() })
            .cores["melondsds"]?.screen ?: error("thiếu config screen melondsds")

    /**
     * Khung hình lõi vừa trong vùng game, giữ tỉ lệ: chồng dọc = [DsFrame.ASPECT] (256×384),
     * cạnh nhau = 4× (hai màn 256×192 → 512×192). Lõi tự giữ tỉ lệ nên phần thừa là viền đen.
     */
    private fun pictureRect(w: Int, h: Int, aspect: Float): Rect {
        val pw = minOf(w.toFloat(), h * aspect)
        val ph = pw / aspect
        return Rect((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
    }

    private fun ndsLandscapeShot(sau: Boolean, name: String) {
        val screen = ndsScreen()
        val saved = emptyMap<String, String>()
        val rotated = DsScreen.rotationLayout(screen, saved, landscape = true)
        val untouched = DsScreen.current(screen, saved).layout.value
        assertEquals("sau: xoay ngang phải theo landscapeLayout của config", "left-right", rotated)
        assertEquals("trước: lõi giữ bố cục mặc định (chồng dọc)", "top-bottom", untouched)
        val sideBySide = (if (sau) rotated else untouched) == screen.landscapeLayout
        assertEquals("hai ảnh phải khác bố cục", sau, sideBySide)
        // "Sau" dùng overlayPad của config (phím che lên khung); "trước" là độ mờ mặc định của app.
        val padOpacity = if (sau) screen.padOpacity.takeIf { it in 0.2f..1f } ?: InGameState().opacity else InGameState().opacity
        val state = InGameState().apply { opacity = padOpacity }

        val dm = rule.activity.resources.displayMetrics
        val wPx = dm.widthPixels
        val hPx = dm.heightPixels
        val rect = pictureRect(wPx, hPx, if (sideBySide) DsFrame.ASPECT * 4f else DsFrame.ASPECT)

        rule.setContent {
            MonikaTheme {
                val density = LocalDensity.current
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    NdsSurface(
                        sideBySide,
                        Modifier.align(Alignment.Center)
                            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
                    )
                    InGameOverlay(
                        state = state, system = "Nintendo DS", title = "NDS · " + screen.title,
                        layout = PadLayout.NDS, showPad = true,
                        send = { _, _ -> }, onBack = {}, onSave = {}, onLoad = {}, onTurbo = {}, onOpacity = {}, onEditDone = {},
                    )
                }
            }
        }
        // Thanh tên tự ẩn sau 3 s: ảnh chỉ còn vùng màn + phím ảo.
        rule.mainClock.advanceTimeBy(AutoHide.HIDE_MS + 1_000)
        rule.waitForIdle()

        // Hai màn phải có trong ảnh (nhãn của bề mặt thay thế).
        rule.onNodeWithText("Màn trên").assertExists()
        rule.onNodeWithText("Màn dưới").assertExists()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val game = Rect(root.left + rect.left, root.top + rect.top, root.left + rect.right, root.top + rect.bottom)
        val widthShare = rect.width / wPx
        if (sau) assertTrue("sau: khung phải rộng ≥99% màn ngang, đo ${(widthShare * 100).toInt()}%", widthShare >= 0.99f)
        else assertTrue("trước: khung bị bóp còn ${(widthShare * 100).toInt()}% bề rộng", widthShare < 0.4f)
        // Phím ảo thật phủ lên vùng game: nút giữa bàn phím nằm trên khung ở cả hai ảnh.
        val start = rule.onNodeWithContentDescription("Nút START").fetchSemanticsNode().boundsInRoot
        assertTrue("START phải phủ lên vùng màn game", game.overlaps(start))
        // Ảnh "sau": khung rộng hết màn nên cả cụm nút nằm trên khung; "trước": khung hẹp, nút hai bên rơi ra viền đen.
        listOf("Nút A", "Nút B").forEach { label ->
            val pad = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            if (sau) assertTrue("$label phải phủ lên vùng màn game", game.overlaps(pad))
            else assertTrue("trước: khung hẹp nên $label nằm ngoài khung", !game.overlaps(pad))
        }
        rule.shot(name)
    }
}

/**
 * Bề mặt thay thế cho khung hình lõi: hai khối có nhãn "Màn trên"/"Màn dưới" (chồng dọc hoặc cạnh nhau).
 * Không phải hình vẽ giả lập khung game — chỉ đánh dấu vùng màn để thấy bố cục và chỗ phím ảo phủ lên.
 */
@Composable
private fun NdsSurface(sideBySide: Boolean, modifier: Modifier) {
    Box(modifier.background(Color(0xFF0B1016)).border(2.dp, Color(0xFF7FA8D8))) {
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                ScreenPane("Màn trên", Color(0xFF1D3F63), Modifier.weight(1f).fillMaxHeight())
                ScreenPane("Màn dưới", Color(0xFF245A46), Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ScreenPane("Màn trên", Color(0xFF1D3F63), Modifier.weight(1f).fillMaxWidth())
                ScreenPane("Màn dưới", Color(0xFF245A46), Modifier.weight(1f).fillMaxWidth())
            }
        }
        Text(
            "Bề mặt NDS thay thế — không phải khung hình lõi",
            color = Color(0xFFB9C6D4), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
    }
}

@Composable
private fun ScreenPane(label: String, fill: Color, modifier: Modifier) {
    Box(modifier.background(fill).border(1.dp, Color(0xFF4C6B8A))) {
        // Nhãn ở đỉnh mỗi màn để phím ảo (phủ nửa dưới) không che mất.
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
    }
}
