package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.MonikaDialog
import vn.aow.monika.ui.theme.MonikaTheme

/** V193 UX: hộp thoại dùng wrapper token; nút huỷ đúng chữ "Hủy" và bấm được. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class MonikaDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun nutHuyDungChinhTaVaGoiOnDismiss() {
        var dismissed = false
        var confirmed = false
        rule.setContent {
            MonikaTheme {
                MonikaDialog(
                    onDismissRequest = { dismissed = true },
                    title = "Tải từ link",
                    confirm = { GradientButton("Tiếp tục tải", { confirmed = true }) },
                    dismiss = { DarkButton("Hủy", { dismissed = true }) },
                ) { Text("Nội dung") }
            }
        }
        rule.onNodeWithText("Hủy").assertIsDisplayed()
        rule.onNodeWithText("Hủy").performClick()
        rule.waitForIdle()
        assertTrue(dismissed)
        assertTrue("không được tự xác nhận", !confirmed)
    }
}
