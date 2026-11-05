package vn.aow.monika.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.LibrarySection
import vn.aow.monika.ui.theme.MonikaTheme

/** V138: khung 3 mục Chơi / Đọc / Tệp, nhớ mục đang chọn, nút "+ Thêm" mở luồng nhập sẵn có. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class LibrarySectionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    private fun settle(text: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.awaitComposeLayout { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openLibrary() {
        rule.setContent { MonikaTheme { LibraryScreen(onSettings = {}) } }
        settle("Chơi")
    }

    @Test fun threeTabsSwitchAndKeepTheChosenOne() {
        AppGraph.prefs.librarySection = "play"
        openLibrary()
        rule.onNodeWithText("Chơi").assertIsSelected()

        rule.onNodeWithText("Đọc").performClick()
        settle("Chưa có tài liệu")
        rule.onNodeWithText("Đọc").assertIsSelected()
        rule.onNodeWithText("Chưa có game").assertDoesNotExist()
        assertEquals("read", AppGraph.prefs.librarySection)
        rule.shot("v138-thu-vien-doc")

        rule.onNodeWithText("Tệp").performClick()
        settle("Chưa có tệp nào")
        rule.onNodeWithText("Tệp").assertIsSelected()
        assertEquals("files", AppGraph.prefs.librarySection)
        rule.shot("v138-thu-vien-tep")

        rule.onNodeWithText("Chơi").performClick()
        settle("Chưa có game")
        rule.onNodeWithText("Chơi").assertIsSelected()
        assertEquals("play", AppGraph.prefs.librarySection)
        rule.shot("v138-thu-vien-choi")
    }

    @Test fun reopensOnTheRememberedSectionAndFallsBackOnUnknownValue() {
        AppGraph.prefs.librarySection = "files"
        openLibrary()
        settle("Chưa có tệp nào")
        rule.onNodeWithText("Tệp").assertIsSelected()
        rule.onNodeWithText("Chưa có game").assertDoesNotExist()

        // Giá trị lạ (bản cũ / sửa tay) không làm mất màn: quay về mục Chơi.
        assertEquals(LibrarySection.FILES, LibrarySection.of("files"))
        assertEquals(LibrarySection.READ, LibrarySection.of("read"))
        assertEquals(LibrarySection.PLAY, LibrarySection.of("khong-co-muc-nay"))
        assertEquals(LibrarySection.PLAY, LibrarySection.of(null))
    }

    @Test fun addButtonOpensTheExistingImportFlow() {
        AppGraph.prefs.librarySection = "play"
        openLibrary()
        rule.onNodeWithText("+ Thêm").performClick()
        settle("Thêm vào Thư viện")
        rule.onNodeWithText("Thêm file từ máy").assertExists()
        rule.onNodeWithText("Quét cả máy").assertExists()
        rule.shot("v138-them-vao-thu-vien")

        rule.onNodeWithText("Thêm file từ máy").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        val started = shadowOf(rule.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started?.intent?.action)
    }
}
