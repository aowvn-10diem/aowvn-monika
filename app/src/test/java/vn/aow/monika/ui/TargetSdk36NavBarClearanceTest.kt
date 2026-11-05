package vn.aow.monika.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.screens.DownloadsScreen
import vn.aow.monika.ui.screens.FeedViewModel
import vn.aow.monika.ui.screens.GamesScreen
import vn.aow.monika.ui.screens.SearchScreen
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * V152 — targetSdk 36 (edge-to-edge bắt buộc) phải sống với thanh điều hướng hệ thống.
 *
 * Test này dựng **chính ba màn của app** ([DownloadsScreen], [GamesScreen], [SearchScreen]) trong
 * khung cao [VIEWPORT_DP]dp, đẩy vào composition một thanh điều hướng giả cao 48dp rồi kiểm hai
 * điều trên màn thật:
 *  1. danh sách của màn dài thêm đúng 48dp khi thanh điều hướng xuất hiện — tức đệm đáy của màn
 *     cộng phần thanh hệ thống qua `dockContentClearance()`, không quay lại `DockClearance` trần;
 *  2. cuộn tới cuối, không một điểm ảnh nội dung nào rơi vào dải an toàn dưới cùng
 *     ([DockClearance] + 48dp) — hàng cuối không bị thanh điều hướng che.
 *
 * Cặp đôi với `TargetSdk36ScreensTest.dockClearanceAddsSystemBarInset` (kiểm công thức); ở đây kiểm
 * việc ba màn thật sự dùng công thức đó.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class TargetSdk36NavBarClearanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun manualClock() { rule.mainClock.autoAdvance = false }

    /** Tab "Tải xuống": hàng cuối (trạng thái chưa tải gì) vẫn nằm trên thanh điều hướng. */
    @Test fun downloadsKeepsLastRowAboveNavigationBar() =
        assertScreenReservesNavigationBar("Tải xuống") { DownloadsScreen() }

    /**
     * Tab "Game & bài viết": hàng cuối của danh sách bài vẫn nằm trên thanh điều hướng.
     * Dựng màn với [FixtureFeed] để không gọi feed thật — test không phụ thuộc mạng.
     */
    @Test fun gamesKeepsLastRowAboveNavigationBar() {
        val vm = FeedViewModel(FixtureFeed())
        assertScreenReservesNavigationBar("Game & bài viết") { GamesScreen(onOpen = {}, vm = vm) }
    }

    /** Tab "Tìm kiếm": hàng cuối (gợi ý) vẫn nằm trên thanh điều hướng. */
    @Test fun searchKeepsLastRowAboveNavigationBar() =
        assertScreenReservesNavigationBar("Tìm kiếm") { SearchScreen(onOpenPost = {}, onOpenLibrary = {}) }

    /**
     * Dựng [content] trong khung ngắn, so chiều dài cuộn trước/sau khi có thanh điều hướng 48dp, rồi
     * soi dải an toàn dưới cùng trên ảnh chụp màn hình thật.
     */
    private fun assertScreenReservesNavigationBar(screen: String, content: @Composable () -> Unit) {
        var bg = 0
        rule.setContent {
            MonikaTheme {
                bg = Monika.colors.bg.toArgb()
                Box(Modifier.height(VIEWPORT_DP.dp)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        // Màn phải dựng đúng trước khi đo, nếu không phép đo bên dưới vô nghĩa.
        rule.onNodeWithText(screen).assertIsDisplayed()

        // Chờ nội dung ổn định (màn Game có thể nạp thêm bài — ở đây là fixture tĩnh) rồi mới đo
        // chiều dài cuộn khi chưa có thanh.
        val maxWithoutBarPx = awaitStableScroll(outermostScrollable())

        applyNavigationBarInset(NAV_BAR_DP)
        val maxWithBarPx = awaitStableScroll(outermostScrollable())
        val density = rule.density.density

        // 1) Đệm cuộn của màn dài thêm đúng phần thanh điều hướng.
        assertTrue(
            "$screen: danh sách phải dài hơn khung để phép đo có nghĩa (maxScroll=$maxWithBarPx px)",
            maxWithBarPx > 0f,
        )
        assertEquals(
            "$screen: đệm đáy phải cộng thêm thanh điều hướng ${NAV_BAR_DP}dp",
            NAV_BAR_DP.toFloat(),
            (maxWithBarPx - maxWithoutBarPx) / density,
            0.5f,
        )

        // 2) Cuộn tới cuối rồi soi ảnh chụp: dải dưới cùng (menu nổi + thanh điều hướng) phải trống.
        settleLayout()
        val shot = capture()
        try {
            val clearancePx = ((DockClearance.value + NAV_BAR_DP) * density).roundToInt()
            val bandTopPx = (VIEWPORT_DP * density).roundToInt() - clearancePx
            val bandBottomPx = (VIEWPORT_DP * density).roundToInt()
            assertEquals(
                "$screen: không được có điểm ảnh nội dung trong dải an toàn " +
                    "${DockClearance.value.toInt() + NAV_BAR_DP}dp dưới cùng",
                0,
                countNonBackground(shot, bg, bandTopPx, bandBottomPx),
            )
            // 3) Phép soi dải chỉ có giá trị khi nội dung thật sự lấp gần tới dải; nội dung ngắn hơn
            //    khung sẽ để dải trống một cách vô nghĩa. Cho hụt tối đa đúng bề dày thanh điều hướng,
            //    vì hồi quy về 110dp đẩy nội dung xuống 48dp là lọt vào dải.
            val paintedBottomPx = lastPaintedRow(shot, bg, bandBottomPx) + 1
            assertTrue(
                "$screen: nội dung phải chạm gần dải an toàn (đáy nội dung ${paintedBottomPx / density}dp, " +
                    "dải bắt đầu ${bandTopPx / density}dp) — nếu không ca test không chứng minh được gì",
                paintedBottomPx >= bandTopPx - NAV_BAR_DP * density,
            )
        } finally {
            shot.recycle()
        }
    }

    /** Thanh điều hướng 3 nút (Android 16 vẫn cho phép) cao 48dp, dính đáy cửa sổ. */
    private fun applyNavigationBarInset(dp: Int) {
        val composeView = findComposeView(rule.activity.window.decorView)
            ?: error("Không tìm thấy AndroidComposeView trong cửa sổ test")
        val px = (dp * rule.activity.resources.displayMetrics.density).roundToInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px))
            .build()
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(composeView, insets) }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun findComposeView(view: View): View? {
        if (view.javaClass.name.contains("AndroidComposeView")) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    /** Danh sách dọc ngoài cùng của màn (màn có thể lồng LazyRow bên trong). */
    private fun outermostScrollable(): SemanticsNodeInteraction {
        val all = rule.onAllNodes(hasScrollAction())
        val count = all.fetchSemanticsNodes().size
        check(count > 0) { "Màn không có danh sách cuộn để đo" }
        return (0 until count).map { all[it] }.maxBy { it.fetchSemanticsNode().size.height }
    }

    private fun maxScrollPx(list: SemanticsNodeInteraction): Float =
        list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /**
     * Chiều dài cuộn chỉ đúng sau khi LazyList đã đo nội dung và việc nạp bài (nếu có) đã xong.
     * Cuộn hết + dựng lại layout cho tới khi số đo đứng yên ở hai lần liên tiếp.
     */
    private fun awaitStableScroll(list: SemanticsNodeInteraction): Float {
        var previous = -1f
        repeat(20) {
            scrollToEnd(list)
            settleLayout()
            val value = maxScrollPx(list)
            if (value == previous) return value
            previous = value
            rule.mainClock.advanceTimeBy(500)
        }
        return previous
    }

    /** Cuộn hết danh sách: scrollBy rất lớn bị kẹp ở cuối nên không phụ thuộc số phần tử. */
    private fun scrollToEnd(list: SemanticsNodeInteraction) {
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
        rule.mainClock.advanceTimeBy(1_000)
    }

    /** Đồng bộ layout/draw của cửa sổ trước khi đọc số đo hoặc chụp ảnh (Robolectric không tự vẽ). */
    private fun settleLayout() {
        val decor = rule.activity.window.decorView
        val width = decor.width.coerceAtLeast(1)
        val height = decor.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            rule.runOnUiThread {
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                decor.layout(0, 0, width, height)
                decor.draw(Canvas(bitmap))
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Chụp cửa sổ thật (Robolectric NATIVE) để soi điểm ảnh. */
    private fun capture(): Bitmap {
        val decor = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { decor.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** Hàng cuối cùng (tính từ đỉnh khung) còn điểm ảnh khác màu nền, -1 nếu trống. */
    private fun lastPaintedRow(bitmap: Bitmap, bg: Int, bottomLimitPx: Int): Int {
        val width = bitmap.width
        val pixels = IntArray(width)
        for (y in bottomLimitPx - 1 downTo 0) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            if (pixels.any { it != bg }) return y
        }
        return -1
    }

    /** Số điểm ảnh khác màu nền trong dải [topPx, bottomPx). */
    private fun countNonBackground(bitmap: Bitmap, bg: Int, topPx: Int, bottomPx: Int): Int {
        val width = bitmap.width
        val height = bottomPx - topPx
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, topPx, width, height)
        return pixels.count { it != bg }
    }

    private companion object {
        /** Thanh điều hướng 3 nút cao 48dp. */
        const val NAV_BAR_DP = 48

        /** Khung ngắn để danh sách luôn dài hơn màn → đo được phần chừa đáy. */
        const val VIEWPORT_DP = 240
    }
}
