package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.Prefs
import vn.aow.monika.ui.screens.LibraryStorageAccess
import vn.aow.monika.ui.screens.allFilesAccessIntent
import vn.aow.monika.ui.theme.MonikaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class LibraryStorageAccessTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun setup() { rule.mainClock.autoAdvance = false; Prefs(rule.activity).libraryStorageExplained = false }

    @Test fun firstVisitExplainsAndAllowsManualWithoutAnyPermissionRequest() {
        val prefs = Prefs(rule.activity)
        var requests = 0; var manual = 0
        rule.setContent { MonikaTheme { LibraryStorageAccess(false, prefs, { requests++ }, { manual++ }) } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Tự tìm game trong máy").assertIsDisplayed()
        settleDraw()
        val dialog = requireNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog())
        assertTrue(dialog.isShowing)
        val view = requireNotNull(dialog.window).decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        val target = java.io.File("build/screenshots/v79-lan-dau.png").apply { parentFile!!.mkdirs() }
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue(Prefs(rule.activity).libraryStorageExplained)
        assertEquals(0, requests)
        rule.onNodeWithText("Thêm game từ máy").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Tự tìm game trong máy").assertDoesNotExist()
        rule.onNodeWithText("Cấp quyền quét game").assertIsDisplayed()
        assertEquals(1, manual); assertEquals(0, requests)
        settleDraw()
        rule.shot("v79-tu-choi-them-thu-cong")
    }

    @Test fun revisitDoesNotExplainAgainButExplicitButtonRequestsAndGrantRemovesCard() {
        Prefs(rule.activity).libraryStorageExplained = true
        val reader = Prefs(rule.activity)
        val granted = mutableStateOf(false)
        var requests = 0
        rule.setContent { MonikaTheme { androidx.compose.foundation.layout.Column {
            androidx.compose.material3.Text("Thư viện", style = vn.aow.monika.ui.theme.Monika.type.sectionTitle)
            LibraryStorageAccess(granted.value, reader, { requests++ }, {})
            androidx.compose.material3.Text("Game tổng hợp để kiểm UI")
        } } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Tự tìm game trong máy").assertDoesNotExist()
        rule.onNodeWithText("Cấp quyền quét game").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, requests)
        // Returning without granting retains the explicit button and does not re-open the dialog.
        rule.onNodeWithText("Cấp quyền quét game").assertIsDisplayed()
        rule.onNodeWithText("Tự tìm game trong máy").assertDoesNotExist()
        rule.runOnIdle { granted.value = true }
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.onAllNodesWithText("Cấp quyền quét game").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText("Cấp quyền quét game").assertDoesNotExist()
        assertEquals(1, requests)
        rule.onNodeWithText("Thư viện").assertIsDisplayed()
        rule.onNodeWithText("Game tổng hợp để kiểm UI").assertIsDisplayed()
        settleDraw()
        rule.shot("v79-da-cap-quyen")
    }

    @Test fun existingGrantNeverRequestsOrExplainsAndSettingsIntentTargetsThisApp() {
        var requests = 0
        rule.setContent { MonikaTheme { LibraryStorageAccess(true, Prefs(rule.activity), { requests++ }, {}) } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Tự tìm game trong máy").assertDoesNotExist()
        rule.onNodeWithText("Cấp quyền quét game").assertDoesNotExist()
        assertEquals(0, requests)
        val intent = allFilesAccessIntent(rule.activity)
        assertEquals(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertEquals("package:${rule.activity.packageName}", intent.data.toString())
        assertEquals(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
            allFilesAccessIntent(rule.activity, appSpecific = false).action)
    }
    private fun settleDraw() {
        repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
    }

}
