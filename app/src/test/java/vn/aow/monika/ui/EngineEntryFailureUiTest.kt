package vn.aow.monika.ui

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.R
import vn.aow.monika.runner.copyEngineEntryInfo
import vn.aow.monika.runner.engineEntryFailureActions
import vn.aow.monika.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class EngineEntryFailureUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }
    @Test fun reportAndCopyKeepEntryFailurePopupAndCopyExactMetadata() {
        val info = "xp3 karanoshojo.xp3 3KB startup.tjs@gốc=false\nexe karanoshojo.exe 2KB"
        var reports = 0; var dismissals = 0; var copied = false
        val actions = engineEntryFailureActions(info,
            SheetAction("Báo lỗi", R.drawable.ic_fluent_document_24_regular) { reports++ }) {
            copied = copyEngineEntryInfo(rule.activity, it)
        }
        assertTrue(actions.all { it.keepOpen && it.enabled })
        rule.setContent { MonikaTheme { Box(Modifier.fillMaxSize().background(Monika.colors.surfaceDark)) {
            MonikaMenuSheet(true, { dismissals++ }, actions, title = "Kara no Shoujo",
                subtitle = "Không tìm thấy startup.tjs để mở game")
        } } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Sao chép thông tin lỗi").assertIsDisplayed()
        rule.shot("v82-entry-failure-actions")
        rule.onNodeWithText("Sao chép thông tin lỗi").performClick()
        assertTrue(copied)
        val clipboard = rule.activity.getSystemService(ClipboardManager::class.java)
        assertEquals(info, clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(0, dismissals)
        rule.onNodeWithText("Báo lỗi").performClick()
        assertEquals(1, reports); assertEquals(0, dismissals)
        rule.onNodeWithText("Sao chép thông tin lỗi").assertIsDisplayed()
    }
    @Test fun pendingMetadataDisablesBothActionsUntilReady() {
        val actions = engineEntryFailureActions(null,
            SheetAction("Báo lỗi", R.drawable.ic_fluent_document_24_regular) {}) { fail("No metadata") }
        assertEquals(2, actions.size)
        assertTrue(actions.none { it.enabled })
        actions.last().onClick()
    }
}
