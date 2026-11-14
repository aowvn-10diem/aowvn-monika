package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.config.DonateConfig
import vn.aow.monika.donate.DonationSheet
import vn.aow.monika.donate.DonationStaticQr
import vn.aow.monika.ui.theme.MonikaTheme

/** [A85/A78-2] Bản lạ chứng thư không được vẽ QR/STK ủng hộ. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class DonationSheetGatingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun manualClock() { rule.mainClock.autoAdvance = false }

    private val config = DonateConfig()

    private fun open(official: String, actual: String) {
        rule.setContent {
            MonikaTheme {
                val cfg = remember { config }
                DonationSheet(
                    cfg, {},
                    loadBanks = { it.fallbackBanks },
                    qrContent = { DonationStaticQr(cfg) },
                    officialSha256 = official,
                    actualSha256 = actual,
                    debugBuild = false,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test fun foreignCertShowsNeutralNoticeInsteadOfQr() {
        open(official = "a".repeat(64), actual = "b".repeat(64))
        rule.onNodeWithTag("donation-unavailable").assertExists()
        rule.onNodeWithTag("donation-sheet").assertDoesNotExist()
    }

    @Test fun officialCertKeepsDonationPanel() {
        open(official = "a".repeat(64), actual = "a".repeat(64))
        rule.onNodeWithTag("donation-sheet").assertExists()
        assertTrue(true)
    }
}
