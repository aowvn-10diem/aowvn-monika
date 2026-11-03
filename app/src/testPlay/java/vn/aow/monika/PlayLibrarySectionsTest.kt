package vn.aow.monika.ui

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.File

/**
 * V192 — Thư viện bản Play: đủ 3 mục Chơi / Đọc / Tệp như bản web, nhớ mục đang chọn,
 * nhưng chỉ nhập tệp cục bộ (không tải game, không link ngoài, không ủng hộ).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class PlayLibrarySectionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
        AppGraph.prefs.librarySection = "play"
    }

    private fun settle(text: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openLibrary() {
        rule.setContent { MonikaTheme { LibraryScreen(onSettings = {}) } }
        settle("Chơi")
    }

    @Test fun threeSectionsSwitchAndRememberTheChosenOne() {
        openLibrary()
        rule.onNodeWithText("Chơi").assertIsSelected()

        rule.onNodeWithText("Đọc").performClick()
        settle("Chưa có tài liệu")
        rule.onNodeWithText("Đọc").assertIsSelected()
        rule.onNodeWithText("Chưa có game").assertDoesNotExist()
        assertEquals("read", AppGraph.prefs.librarySection)

        rule.onNodeWithText("Tệp").performClick()
        settle("Chưa có tệp")
        rule.onNodeWithText("Tệp").assertIsSelected()
        assertEquals("files", AppGraph.prefs.librarySection)

        rule.onNodeWithText("Chơi").performClick()
        settle("Chưa có game")
        rule.onNodeWithText("Chơi").assertIsSelected()
        assertEquals("play", AppGraph.prefs.librarySection)
    }

    @Test fun reopensOnTheRememberedSection() {
        AppGraph.prefs.librarySection = "files"
        openLibrary()
        settle("Chưa có tệp")
        rule.onNodeWithText("Tệp").assertIsSelected()
        rule.onNodeWithText("Chưa có game").assertDoesNotExist()
        // Giá trị lạ vẫn quay về mục Chơi, không làm mất màn.
        assertEquals(LibrarySection.PLAY, LibrarySection.of("khong-co-muc-nay"))
    }

    @Test fun addSheetOnlyOffersLocalFilesInPlayBuild() {
        openLibrary()
        rule.onNodeWithText("+ Thêm").performClick()
        settle("Thêm vào Thư viện")
        rule.onNodeWithText("Thêm file từ máy").assertIsDisplayed()
        // Không quét cả máy, không xin quyền đọc toàn bộ bộ nhớ ở bản Play.
        rule.onNodeWithText("Quét cả máy").assertDoesNotExist()
        rule.onNodeWithText("Cho phép đọc mọi tệp").assertDoesNotExist()
        rule.onNodeWithText("Chọn tệp có sẵn trong máy").assertExists()
    }

    @Test fun libraryShowsNoDownloadOrDonationEntry() {
        openLibrary()
        for (text in listOf("Ủng hộ", "VietQR", "Tải xuống", "Tải game", "Cập nhật APK")) {
            rule.onNodeWithText(text, substring = true).assertDoesNotExist()
        }
    }

    /** Luna #469: menu thư viện Play chỉ được nói đúng việc nó làm — mở SAF chọn tệp, không "quét cả máy". */
    @Test fun libraryMenuOffersOnlyFilePicker() {
        openLibrary()
        rule.onNodeWithContentDescription("Menu thư viện").performClick()
        settle("Chọn tệp từ máy")
        rule.onNodeWithText("Quét cả máy").assertDoesNotExist()
        rule.onNodeWithText("Chọn tệp từ máy").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(
            Intent.ACTION_OPEN_DOCUMENT,
            shadowOf(rule.activity).nextStartedActivityForResult?.intent?.action,
        )
    }

    /** Luna #469: bản Play không xin quyền bộ nhớ nên không được còn nhãn "Cấp quyền" ở bất kỳ thẻ/menu nào. */
    @Test fun playLibraryHasNoPermissionRequestLabels() {
        for (name in listOf("LibraryScreen.kt", "LibraryGameMenu.kt", "LibraryGameCards.kt")) {
            val src = File("src/play/java/vn/aow/monika/ui/screens/$name").readText()
            assertFalse("$name còn nhãn cấp quyền", src.contains("\"Cấp quyền") || src.contains("\"cấp quyền"))
            assertFalse("$name còn mục quét cả máy", src.contains("SheetAction(\"Quét cả máy") || src.contains("DarkButton(\"Quét cả máy"))
        }
    }
}
