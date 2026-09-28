package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import vn.aow.monika.ui.screens.DownloadsScreen
import vn.aow.monika.ui.screens.GamesScreen
import vn.aow.monika.ui.screens.HomeScreen
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.SettingsScreen
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * Mở từng màn hình trên JVM (Robolectric): bắt crash khi dựng giao diện + chụp ảnh để xem bằng mắt.
 * Không kiểm phần chạy game (thư viện native không chạy trên JVM).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // Tự điều khiển đồng hồ: hiệu ứng lặp vô hạn / việc nền không làm test chờ mãi (test ổn định khi chạy cả bộ).
    @org.junit.Before fun manualClock() { rule.mainClock.autoAdvance = false }

    private fun show(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { MonikaTheme { content() } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.shot(name)
    }

    @Test fun home() = show("1-trang-chu") { HomeScreen(onOpenPost = {}, onGo = {}) }

    @Test fun games() = show("2-game") { GamesScreen(onOpen = {}) }

    @Test fun library() {
        show("3-thu-vien") { LibraryScreen(onSettings = {}) }
        rule.onNodeWithText("Chưa có game").assertExists()
    }

    @Test fun downloads() = show("4-tai-xuong") { DownloadsScreen() }

    @Test fun settings() {
        show("5-cai-dat") { SettingsScreen(onBack = {}) }
        rule.onNodeWithText("Cài đặt").assertExists()
        // J2ME Loader đã nhúng sẵn → không được liệt kê là app ngoài nữa.
        rule.onNodeWithText("J2ME", substring = true).assertDoesNotExist()
    }
}
