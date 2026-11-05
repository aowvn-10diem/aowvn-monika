package vn.aow.monika.ui

import androidx.activity.ComponentActivity
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
import vn.aow.monika.ui.screens.FeedOff
import vn.aow.monika.ui.theme.MonikaTheme

/** V193 G1: màn bài viết khi tắt phải nói rõ đang tắt và có nút bật. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class FeedOffTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun hienTrangThaiTatVaNutBat() {
        var enabled = false
        rule.setContent { MonikaTheme { FeedOff { enabled = true } } }
        rule.onNodeWithText("Bài viết đang tắt").assertIsDisplayed()
        rule.onNodeWithText("Bật bài viết").performClick()
        assertTrue("nút bật phải gọi callback", enabled)
    }
}
