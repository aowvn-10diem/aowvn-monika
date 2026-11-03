package vn.aow.monika.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import vn.aow.monika.config.*
import vn.aow.monika.official.*
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class OfficialDistributionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun manualClock() { rule.mainClock.autoAdvance = false }
    private val cfg get() = ConfigRepository.parse(rule.activity.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() })

    @Test fun sheetShowsConfigIdentityCopiesCertificateAndOpensReportTemplate() {
        val config = cfg
        rule.setContent { MonikaTheme { OfficialDistributionSheet(config.official, config.security, config.account.donate, {}) } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Bản chính thức").assertIsDisplayed()
        rule.onNodeWithText("GitHub Releases").assertIsDisplayed()
        rule.onNodeWithText(config.security.officialCertSha256.chunked(16).joinToString("\n")).assertIsDisplayed()
        val dialog = requireNotNull(ShadowDialog.getLatestDialog())
        val view = dialog.window!!.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(image)) }
        File("build/screenshots/v183-ban-chinh-thuc.png").apply { parentFile!!.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        rule.onNodeWithText("Sao chép chứng thư").performClick()
        val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        rule.runOnIdle { assertEquals(config.security.officialCertSha256, clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        repeat(2) {
            rule.onNodeWithTag("official-distribution").performTouchInput { swipeUp() }
            rule.mainClock.advanceTimeBy(1_000)
        }
        rule.onNodeWithText("Báo bản giả").assertIsDisplayed().performClick()
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(config.official.reportFakeUrl, intent.data.toString())
    }

    @Test fun linksComeFromConfigAndTextIsNotAnAutomaticAuthenticityClaim() {
        var opened: String? = null
        val config = cfg
        val custom = config.official.copy(downloadSources = listOf(OfficialSource("Nguồn kiểm thử", "https://example.test/releases")))
        rule.setContent { MonikaTheme { OfficialDistributionPanel(custom, "b".repeat(64), config.account.donate, {}, { opened = it }) } }
        rule.onNodeWithText("Nguồn kiểm thử").performClick()
        rule.runOnIdle { assertEquals("https://example.test/releases", opened) }
        rule.onNodeWithText("b".repeat(16) + "\n" + "b".repeat(16) + "\n" + "b".repeat(16) + "\n" + "b".repeat(16)).assertExists()
    }

    @Test fun reportIntentRejectsUnsafeSchemesAndSupportsConfiguredEmail() {
        assertNull(officialIntent("javascript:alert(1)"))
        assertNull(officialIntent("http://fake.test/"))
        assertNull(officialIntent("file:///app.apk"))
        assertNull(officialIntent("https://name@fake.test/"))
        assertEquals(Intent.ACTION_SENDTO, officialIntent("mailto:report@example.test")!!.action)
    }
}
