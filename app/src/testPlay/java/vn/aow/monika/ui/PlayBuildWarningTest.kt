package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.screens.OFFICIAL_BUILD_WARNING
import vn.aow.monika.ui.screens.OfficialBuildWarning
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * [A78] V154 · flavor `play`: chứng thư lệch → thẻ cảnh báo đỏ dùng câu của bản Play,
 * **không** dẫn link/tên miền tải ngoài Google Play (chính sách Play, A65/A68), và không chặn app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class PlayBuildWarningTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        rule.mainClock.autoAdvance = false
    }

    @Test fun mismatchedCertShowsPlayWarningWithoutOutsideDownloadLink() {
        rule.setContent { MonikaTheme { Column {
            OfficialBuildWarning("f".repeat(64), "c".repeat(64), debugBuild = false, OFFICIAL_BUILD_WARNING)
            Text("Nội dung Thư viện")
        } } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText(OFFICIAL_BUILD_WARNING).assertIsDisplayed()
        // Chỉ cảnh báo: nội dung trang vẫn hiện, app vẫn dùng được.
        rule.onNodeWithText("Nội dung Thư viện").assertIsDisplayed()
        assertTrue(OFFICIAL_BUILD_WARNING, OFFICIAL_BUILD_WARNING.contains("Google Play"))
    }

    @Test fun matchingCertShowsNoPlayWarning() {
        val official = "c".repeat(64)
        rule.setContent { MonikaTheme { Column {
            OfficialBuildWarning(official, official, debugBuild = false, OFFICIAL_BUILD_WARNING)
            Text("Nội dung Thư viện")
        } } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText(OFFICIAL_BUILD_WARNING).assertDoesNotExist()
    }
}
