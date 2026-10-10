package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.config.SystemDef
import vn.aow.monika.library.Game
import vn.aow.monika.ui.screens.*
import vn.aow.monika.ui.theme.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ExperimentalGameWarningTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }
    private val game = Game(File("/synthetic/v81"), "Game thử nghiệm", SystemDef("v81-test", "Kirikiri", "unsupported-test", experimental = true), null)

    @Test fun cancelNeverLaunchesAndContinueIsRequiredForEachAttempt() {
        var results = 0
        rule.setContent { MonikaTheme {
            val launch = rememberGameLaunch { _, _ -> results++ }
            DarkButton("Chơi bản thử", { launch(game) })
        } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Chơi bản thử").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        awaitText("Kirikiri đang thử nghiệm, có thể chưa chạy được game của bạn. Gặp lỗi hãy bấm Báo lỗi")
        rule.onNodeWithText("Kirikiri đang thử nghiệm, có thể chưa chạy được game của bạn. Gặp lỗi hãy bấm Báo lỗi").assertIsDisplayed()
        shotDialog("v81-canh-bao-kirikiri")
        assertEquals(0, results)
        rule.onNodeWithText("Hủy").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(0, results)
        rule.onNodeWithText("Chơi bản thử").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        awaitText("Tiếp tục")
        rule.onNodeWithText("Tiếp tục").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, results) // fake runner returns Failed only after confirmation; no native game launched
        rule.onNodeWithText("Chơi bản thử").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        awaitText("Tiếp tục")
        rule.onNodeWithText("Tiếp tục").assertIsDisplayed()
        assertEquals(1, results)
    }

    @Test fun experimentalCardHasBadgeAndStableSystemsDoNot() {
        rule.setContent { MonikaTheme { Column { ContinueCard(game) {}; ExperimentalTag(false) } } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Thử nghiệm").assertIsDisplayed()
        rule.onNodeWithText("Game thử nghiệm").assertIsDisplayed()
        rule.shot("v81-nhan-game-thu-nghiem")
    }
    private fun awaitText(text: String) {
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
    }
    private fun shotDialog(name: String) {
        val dialog = requireNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog())
        assertTrue(dialog.isShowing)
        val view = requireNotNull(dialog.window).decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        val target = java.io.File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

}
