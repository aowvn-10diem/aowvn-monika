package vn.aow.monika.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.screens.LinkDownloadDialog
import vn.aow.monika.ui.theme.DialogKeyAction
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.dialogKeyAction

/**
 * V193 G2: test qua `LinkDownloadDialog` — chính composable production của màn Tải xuống.
 * `useDialog = false` chỉ bỏ cửa sổ dialog Robolectric (2 semantics root); body/nút/logic vẫn là production.
 * `startDownload` là seam duy nhất thay chỗ gọi BrowserDownloads, để đếm request.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class DownloadsLinkFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val calls = mutableListOf<Pair<String, Boolean>>()
    private var dismissed = false

    @Before fun setup() {
        AppGraph.init(app)
        calls.clear()
        dismissed = false
    }

    private fun openDialog() {
        rule.setContent {
            MonikaTheme {
                LinkDownloadDialog(
                    startDownload = { url, pixeldrainConfirmed -> calls += url to pixeldrainConfirmed },
                    onDismiss = { dismissed = true },
                    useDialog = false,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun typeLink(url: String) {
        rule.onNodeWithTag("link-field").performClick()
        rule.onNodeWithTag("link-field").performTextInput(url)
        rule.waitForIdle()
    }

    @Test fun linkThuongXacNhanTruyenPixeldrainFalse() {
        openDialog(); typeLink("https://aow.vn/p/tai-game.html")
        rule.onNodeWithText("Tiếp tục tải").performClick()
        rule.waitForIdle()
        assertEquals(1, calls.size)
        assertFalse(calls.single().second)
        assertTrue(dismissed)
    }

    @Test fun linkPixeldrainHienCanhBaoVaXacNhanTruyenTrue() {
        openDialog(); typeLink("https://pixeldrain.com/u/AbC12")
        rule.onNodeWithText("Tệp do bên thứ ba (Pixeldrain), AowVN không kiểm soát.").assertIsDisplayed()
        rule.onNodeWithText("Chỉ tải nếu bạn tin nguồn.").assertIsDisplayed()
        rule.onNodeWithText("Tiếp tục tải").performClick()
        rule.waitForIdle()
        assertEquals(1, calls.size)
        assertTrue(calls.single().second)
    }

    @Test fun huyOCanhBaoPixeldrainKhongTai() {
        openDialog(); typeLink("https://pixeldrain.com/u/AbC12")
        rule.onNodeWithText("Hủy").performClick()
        rule.waitForIdle()
        assertTrue("Hủy không được phát request", calls.isEmpty())
        assertTrue(dismissed)
    }

    @Test fun enterKhiOFocusChonHuyKhongTai() {
        openDialog(); typeLink("https://pixeldrain.com/u/AbC12")
        rule.onNodeWithTag("link-field").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertTrue("Enter không được phát request", calls.isEmpty())
        assertTrue(dismissed)
    }

    @Test fun numPadEnterKhiOFocusChonHuyKhongTai() {
        openDialog(); typeLink("https://pixeldrain.com/u/AbC12")
        rule.onNodeWithTag("link-field").performKeyInput { pressKey(Key.NumPadEnter) }
        rule.waitForIdle()
        assertTrue("NumPadEnter không được phát request", calls.isEmpty())
        assertTrue(dismissed)
    }

    /** Hàm quyết định phím production dùng trong MonikaTextField/MonikaDialog. */
    @Test fun dialogKeyActionKhoaEnterVaNumPadEnter() {
        assertEquals(DialogKeyAction.DISMISS, dialogKeyAction(Key.Enter, KeyEventType.KeyDown))
        assertEquals(DialogKeyAction.DISMISS, dialogKeyAction(Key.NumPadEnter, KeyEventType.KeyDown))
        assertEquals(DialogKeyAction.IGNORE, dialogKeyAction(Key.Enter, KeyEventType.KeyUp))
    }
}
