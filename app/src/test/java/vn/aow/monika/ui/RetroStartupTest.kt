package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.runner.RetroStartup
import vn.aow.monika.ui.theme.MonikaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class RetroStartupTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun timeoutShowsCopyAndRetainsProgress() {
        rule.mainClock.autoAdvance = false
        val slow = mutableStateOf(false)
        val status = mutableStateOf("Đang tải lõi: 2.0 MB / 4.0 MB · 50%")
        var copies = 0
        rule.setContent { MonikaTheme { RetroStartup("Game thử", status.value, slow.value, { copies++ }) } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText(status.value).assertIsDisplayed()
        rule.onNodeWithText("Sao chép chẩn đoán").assertDoesNotExist()
        rule.runOnIdle { slow.value = true; status.value = "Đang nạp ROM: 512.0 MB" }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText(status.value).assertIsDisplayed()
        rule.onNodeWithText("Sao chép chẩn đoán").performClick()
        assertEquals(1, copies)
        rule.onNodeWithText("Game thử").assertIsDisplayed()
        rule.shot("retro-startup-soft-timeout")
    }
}
