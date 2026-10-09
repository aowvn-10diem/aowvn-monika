package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.Prefs
import vn.aow.monika.ui.screens.*
import vn.aow.monika.ui.theme.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class V69UxTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun setup() { rule.mainClock.autoAdvance = false; AppGraph.prefs.supportHiddenUntil = 0L }
    @After fun reset() { AppGraph.prefs.supportHiddenUntil = 0L }

    @Test fun loadingUsesSkeletonNotAnEndlessBlankSpinner() {
        rule.setContent { MonikaTheme { FeedStatus(true) {} } }
        rule.onNodeWithTag("v69-feed-loading").assertExists()
        rule.onNodeWithText("Thử lại").assertDoesNotExist()
        rule.shot("v69-feed-skeleton")
    }

    @Test fun errorOffersRetry() {
        var retries = 0
        rule.setContent { MonikaTheme { FeedStatus(false) { retries++ } } }
        rule.onNodeWithText("Không tải được bài viết").assertIsDisplayed()
        rule.shot("v69-feed-error")
        rule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test fun hideSupportPersistsSevenDaysAndExpiredHideIsVisible() {
        AppGraph.prefs.supportHiddenUntil = System.currentTimeMillis() - 1
        rule.setContent { MonikaTheme { SupportStrip() } }
        rule.onNodeWithText("Ẩn 7 ngày").assertExists()
        rule.shot("v69-support-visible")
        val before = System.currentTimeMillis()
        rule.onNodeWithText("Ẩn 7 ngày").performClick()
        rule.onNodeWithText("Ẩn 7 ngày").assertDoesNotExist()
        val until = Prefs(rule.activity).supportHiddenUntil
        assertTrue(until >= before + SUPPORT_HIDE_MILLIS)
        assertTrue(until <= System.currentTimeMillis() + SUPPORT_HIDE_MILLIS)
        rule.shot("v69-support-hidden")
    }

    @Test fun shortFolderLabelCopiesFullPathOnHold() {
        val full = "/storage/emulated/0/Download/AowVN Monika/Game"
        var clipboard: androidx.compose.ui.platform.ClipboardManager? = null
        rule.setContent { MonikaTheme { clipboard = LocalClipboardManager.current; LibraryFolderLabel(full) } }
        assertEquals("Download/AowVN Monika/Game", compactLibraryPath(full))
        val node = rule.onNodeWithText("Thư mục: Download/AowVN Monika/Game · Giữ để sao chép")
        node.assertIsDisplayed(); rule.shot("v69-library-short-path")
        node.performTouchInput { longClick() }
        rule.runOnIdle { assertEquals(full, clipboard!!.getText()!!.text) }
    }

    @Test fun chipFadeDisappearsAtTheEnd() {
        val labels = (1..20).map { "Hệ máy " + it }
        lateinit var scroll: androidx.compose.foundation.lazy.LazyListState
        rule.setContent { MonikaTheme { scroll = rememberLazyListState(); ChipBar(labels, labels.first(), { it }, {}, scroll = scroll) } }
        rule.onNodeWithContentDescription("Còn hệ máy bên phải").assertExists()
        rule.shot("v69-chip-more")
        rule.runOnIdle { kotlinx.coroutines.runBlocking { scroll.scrollToItem(labels.lastIndex) } }
        rule.onNodeWithText(labels.last()).assertIsDisplayed()
        rule.onNodeWithContentDescription("Còn hệ máy bên phải").assertDoesNotExist()
        rule.shot("v69-chip-end")
    }

    @Test fun dockPaddingAddsNavigationInset() {
        var clearance = 0.dp
        rule.setContent {
            MonikaTheme { CompositionLocalProvider(LocalDensity provides Density(1f)) {
                clearance = dockContentClearance(WindowInsets(bottom = 40))
                Column { Box(Modifier.height(clearance)); androidx.compose.material3.Text("Nội dung sau dock") }
            } }
        }
        rule.runOnIdle { assertEquals(150.dp, clearance) }
        rule.shot("v69-dock-inset")
    }
}
