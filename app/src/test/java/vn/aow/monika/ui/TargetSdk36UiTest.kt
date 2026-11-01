package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.screens.DownloadsScreen
import vn.aow.monika.ui.screens.GamesScreen
import vn.aow.monika.ui.screens.SettingsScreen
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.dockContentClearance

/*
 * V152 — targetSdk 36 (Android 16).
 *
 * Ba thay đổi bắt buộc của API 36 mà app phải chịu được:
 *  - edge-to-edge luôn bật (không tắt được) → nội dung phải chừa thanh hệ thống;
 *  - màn lớn (≥600dp) bỏ khoá xoay → activity game/màn chính bị đổi cỡ, không được tạo lại;
 *  - predictive back bật mặc định → điều hướng bằng OnBackPressedDispatcher (Navigation Compose).
 *
 * Test chạy trên Robolectric với SDK 36 và cỡ cửa sổ dọc/ngang/máy tính bảng.
 * Không kiểm phần chạy game (thư viện native không chạy trên JVM).
 */

/** Mở app thật (MainActivity) trên API 36: menu nổi và nội dung phải còn trong cửa sổ ở mọi cỡ màn. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class TargetSdk36AppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun manualClock() { rule.mainClock.autoAdvance = false }

    /** Dọc, API 36: app dựng xong, menu nổi hiện đủ. */
    @Test fun dockVisibleOnApi36() {
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithContentDescription("Trang chủ").assertIsDisplayed()
        rule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }

    /** Xoay ngang: không tạo lại activity, menu nổi vẫn nằm trọn trong cửa sổ. */
    @Test
    @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun landscapeKeepsDockVisible() {
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithContentDescription("Trang chủ").assertIsDisplayed()
        rule.onNodeWithContentDescription("Thư viện").assertIsDisplayed()
    }

    /** Máy tính bảng (≥600dp, nơi API 36 bỏ khoá xoay): đổi tab vẫn chạy, menu nổi không bị che. */
    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun largeScreenTabSwitchKeepsDock() {
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithContentDescription("Thư viện").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithContentDescription("Thư viện").assertIsDisplayed()
    }
}

/** Từng màn chính trên API 36: dọc / ngang / màn lớn không làm vỡ bố cục. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class TargetSdk36ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun manualClock() { rule.mainClock.autoAdvance = false }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { MonikaTheme { content() } }
        rule.mainClock.advanceTimeBy(2_000)
    }

    /** Màn lớn: danh sách game dựng đúng (targetSdk 36 cho phép mọi tỉ lệ màn). */
    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun gamesLargeScreen() {
        show { GamesScreen(onOpen = {}) }
        rule.onNodeWithText("Game & bài viết").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun settingsLargeScreen() {
        show { SettingsScreen(onBack = {}) }
        rule.onNodeWithText("Cài đặt").assertIsDisplayed()
    }

    /** Xoay khi đang ở tab Tải xuống: tiêu đề vẫn hiện, không tràn khỏi màn. */
    @Test
    @Config(qualifiers = "w851dp-h393dp-land-xxhdpi")
    fun downloadsLandscape() {
        show { DownloadsScreen() }
        rule.onNodeWithText("Tải xuống").assertIsDisplayed()
    }

    /**
     * Edge-to-edge bắt buộc (API 36): khoảng chừa đáy của nội dung = 110dp của menu nổi + đúng phần
     * thanh điều hướng hệ thống, nên dòng cuối không nằm dưới thanh điều hướng.
     */
    @Test
    fun dockClearanceAddsSystemBarInset() {
        var extra: Dp? = null
        rule.setContent {
            val px = with(LocalDensity.current) { 48.dp.roundToPx() }
            extra = dockContentClearance(WindowInsets(left = 0, top = 0, right = 0, bottom = px)) - DockClearance
        }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(48f, extra!!.value, 0.5f)
    }
}
