package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.DialogKeyAction
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.MonikaDialog
import vn.aow.monika.ui.theme.MonikaTextField
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.dialogKeyAction

/** V193 UX: hộp thoại dùng wrapper token; nút huỷ đúng chữ "Hủy" và bấm được. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class MonikaDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun nutHuyDungChinhTaVaGoiOnDismiss() {
        var dismissed = false
        var confirmed = false
        var request = 0
        rule.setContent {
            MonikaTheme {
                MonikaDialog(
                    onDismissRequest = { dismissed = true },
                    title = "Tải từ link",
                    confirm = { GradientButton("Tiếp tục tải", { confirmed = true; request++ }) },
                    dismiss = { DarkButton("Hủy", { dismissed = true }) },
                ) { Text("Nội dung") }
            }
        }
        rule.onNodeWithText("Hủy").assertIsDisplayed()
        rule.onNodeWithText("Hủy").performClick()
        rule.waitForIdle()
        assertTrue(dismissed)
        assertTrue("không được tự xác nhận", !confirmed)
        assertEquals("hủy không được phát request", 0, request)
    }

    /** Quyết định phím thuần: Enter (kể cả NumPadEnter) lúc nhấn xuống = Hủy, không bao giờ xác nhận.
     *  Robolectric mở dialog bằng cửa sổ riêng (2 semantics root) nên không inject Enter ổn định;
     *  test khóa đúng hàm `dialogKeyAction` mà `MonikaDialog` dùng trong `onPreviewKeyEvent`. */
    @Test fun enterChonHuyKhongXacNhan() {
        assertEquals(DialogKeyAction.DISMISS, dialogKeyAction(Key.Enter, KeyEventType.KeyDown))
        assertEquals(DialogKeyAction.DISMISS, dialogKeyAction(Key.NumPadEnter, KeyEventType.KeyDown))
        assertEquals(DialogKeyAction.IGNORE, dialogKeyAction(Key.Enter, KeyEventType.KeyUp))
        assertEquals(DialogKeyAction.IGNORE, dialogKeyAction(Key.Escape, KeyEventType.KeyDown))
    }

    /** Harness một semantics root (tránh Robolectric treo ở cửa sổ dialog): đúng ô nhập + nút Hủy/Tiếp tục
     *  của luồng "Tải từ link"; Enter/NumPadEnter khi ô focus chỉ được gọi Hủy, không xác nhận/tải. */
    private fun assertEnterCancels(key: Key) {
        var cancel = 0
        var confirm = 0
        var request = 0
        rule.setContent {
            MonikaTheme {
                androidx.compose.foundation.layout.Column {
                    MonikaTextField("", {}, "Dán link tải", Modifier.testTag("link-field"), onEnter = { cancel++ })
                    DarkButton("Hủy", { cancel++ })
                    GradientButton("Tiếp tục tải", { confirm++; request++ })
                }
            }
        }
        rule.onNodeWithTag("link-field").performClick()
        rule.onNodeWithTag("link-field").performKeyInput { pressKey(key) }
        rule.waitForIdle()
        assertEquals("Hủy phải gọi đúng 1 lần", 1, cancel)
        assertEquals("không được xác nhận", 0, confirm)
        assertEquals("không được phát request", 0, request)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun enterKhiOFocusGoiHuyKhongXacNhan() = assertEnterCancels(Key.Enter)

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun numPadEnterKhiOFocusGoiHuyKhongXacNhan() = assertEnterCancels(Key.NumPadEnter)
}
