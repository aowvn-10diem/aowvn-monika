package vn.aow.monika.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
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
import vn.aow.monika.config.DonateConfig
import vn.aow.monika.donate.*
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.R
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class DonationSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun manualClock() { rule.mainClock.autoAdvance = false }
    private val config = DonateConfig()
    private var qrAmount = 0L

    private fun open() {
        rule.setContent {
            var visible by remember { mutableStateOf(true) }
            MonikaTheme {
                if (visible) DonationSheet(config, { visible = false }, loadBanks = { it.fallbackBanks },
                    qrContent = { amount -> qrAmount = amount; DonationStaticQr(config) })
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test fun nativeSheetCopiesAccountAndMemoThenClosesWithoutBrowser() {
        open()
        rule.onNodeWithTag("donation-sheet").assertExists()
        rule.onNodeWithText("Ủng hộ AowVN").assertIsDisplayed()
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNull(shadowOf(app).nextStartedActivity)
        screenshot("v182-bang-ung-ho")
        val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        rule.onNodeWithContentDescription("Sao chép số tài khoản").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(config.accountNo, clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        rule.onNodeWithContentDescription("Sao chép nội dung").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(config.memo, clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        // Vuốt như người dùng: performScrollTo lặp chờ frame trên ModalBottomSheet khi đồng hồ Compose thủ công.
        rule.onNodeWithTag("donation-sheet").performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Để sau, cảm ơn").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("donation-sheet").assertDoesNotExist()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun invalidAmountKeepsSelectedChipForQrAndBankLink() {
        open()
        rule.onNodeWithText("200.000đ").assertIsDisplayed().performClick()
        rule.onNodeWithTag("donation-custom-amount").assertIsDisplayed().performTextInput("30.000")
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals(30_000L, qrAmount) }
        rule.onNodeWithTag("donation-custom-amount").performTextReplacement("999")
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("donation-amount-hint").assertTextContains("đang dùng 200.000đ", substring = true)
        rule.runOnIdle { assertEquals(200_000L, qrAmount) }
        screenshot("v182-so-tien-khong-hop-le")
        rule.onNodeWithTag("donation-sheet").performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Vietcombank").assertIsDisplayed().performClick()
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertNotNull(intent); assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("dl.vietqr.io", intent.data!!.host)
        assertEquals("200000", intent.data!!.getQueryParameter("am"))
        assertEquals("vcb", intent.data!!.getQueryParameter("app"))
        assertEquals(config.memo, intent.data!!.getQueryParameter("tn"))
    }

    @Test fun fallbackNeverDisplaysQrForAChangedAccount() {
        rule.setContent { MonikaTheme { DonationStaticQr(config.copy(accountNo = "9999999999")) } }
        rule.onNodeWithContentDescription("QR dự phòng ủng hộ").assertDoesNotExist()
        rule.onNodeWithText("Chưa tải được QR. Hãy sao chép tài khoản hoặc chọn ngân hàng.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun libraryHeaderKeepsDonationAndMenuClickableOnNarrowScreen() {
        var donated = false
        var menu = false
        rule.setContent { MonikaTheme {
            MonikaHeader("Thư viện", left = { DonationAction({ donated = true }) },
                right = { CircleButton(R.drawable.ic_fluent_grid_24_regular, "Menu thư viện", { menu = true }) })
        } }
        rule.onNodeWithText("Ủng hộ").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Menu thư viện").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(donated); assertTrue(menu) }
        rule.shot("v182-thanh-dau-320dp")
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val view = requireNotNull(ShadowDialog.getLatestDialog()).window!!.decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val target = File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
