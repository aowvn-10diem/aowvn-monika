package vn.aow.monika.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.library.GameStorage
import vn.aow.monika.ui.screens.LibraryFileEntry
import vn.aow.monika.ui.screens.LibraryFileKind
import vn.aow.monika.ui.screens.LibraryFilesPage
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.formatFileDate
import vn.aow.monika.ui.screens.formatFileSize
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.dockContentClearance
import java.io.File

/** V141: mục Tệp — danh sách tệp trong kho, trạng thái rỗng, xoá có xác nhận, bấm mở đúng hành động. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class LibraryFilesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    /** AppGraph là singleton dùng chung giữa các lớp test → trả mục đang chọn về Chơi để không lây lớp khác. */
    @After fun restoreSection() { AppGraph.prefs.librarySection = "play" }

    private fun settle(text: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.awaitComposeLayout { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun settleGone(text: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.awaitComposeLayout { rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    /** Mở thẳng mục Tệp (mục đang chọn nhớ trong Prefs). */
    private fun openFiles() {
        AppGraph.prefs.librarySection = "files"
        rule.setContent { MonikaTheme { LibraryScreen(onSettings = {}) } }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun fixture(name: String): File = File(GameStorage.games(rule.activity), name).apply { mkdirs() }

    @Test fun filesSectionListsGamesDocumentsAndOtherFilesWithSizeAndDate() {
        val game = fixture("v141-game-fixture")
        val docs = fixture("v141-doc-fixture")
        val other = fixture("v141-khac-fixture")
        try {
            File(game, "fixture.gba").writeText("synthetic fixture")
            // Dung lượng khác nhau để mỗi dòng có chuỗi "dung lượng · ngày" riêng.
            val manual = File(docs, "Sách hướng dẫn.pdf").apply { writeText("synthetic fixture document") }
            File(other, "ghi-chu.bin").writeText("synthetic fixture")

            openFiles()
            // Danh sách dựng từ game + tài liệu; tài liệu về sau một nhịp nên chờ đúng nó.
            settle("Sách hướng dẫn.pdf")
            rule.onNodeWithText("v141-game-fixture").assertExists()
            rule.onNodeWithText("Sách hướng dẫn.pdf").assertExists()
            rule.onNodeWithText("v141-khac-fixture").assertExists()
            rule.onNodeWithText("Game").assertExists()
            rule.onNodeWithText("Tài liệu").assertExists()
            rule.onNodeWithText("Khác").assertExists()
            rule.onNodeWithText("3 tệp", substring = true).assertIsDisplayed()
            // Dung lượng + ngày thêm lấy từ chính tệp trong kho, không phải số bịa.
            rule.onNodeWithText("${formatFileSize(manual.length())} · ${formatFileDate(manual.lastModified())}").assertExists()
            repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
            rule.shot("v141-tep-danh-sach")
        } finally {
            game.deleteRecursively(); docs.deleteRecursively(); other.deleteRecursively(); AppGraph.library.list()
        }
    }

    @Test fun emptyFilesSectionExplainsAndOpensTheExistingAddFlow() {
        openFiles()
        settle("Chưa có tệp nào")
        // Một "+ Thêm" ở đầu Thư viện, một ở trạng thái rỗng — cả hai mở cùng luồng nhập.
        assertEquals(2, rule.onAllNodesWithText("+ Thêm").fetchSemanticsNodes().size)
        rule.onAllNodesWithText("+ Thêm").onLast().performClick()
        settle("Thêm vào Thư viện")
        rule.onNodeWithText("Thêm file từ máy").assertExists()
        repeat(8) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.shot("v141-tep-rong")
    }

    @Test fun deletingAnOtherFileAsksFirstAndOnlyThenRemovesItFromDisk() {
        val other = fixture("v141-xoa-fixture")
        val inner = File(other, "tam.bin").apply { writeText("synthetic fixture") }
        try {
            openFiles()
            settle("v141-xoa-fixture")
            rule.onNodeWithContentDescription("Xoá tệp").performClick()
            settle("Xoá tệp?")
            rule.onNodeWithText("Không khôi phục được", substring = true).assertExists()

            rule.onNodeWithText("Hủy").performClick()
            settleGone("Xoá tệp?")
            assertTrue("Huỷ thì phải giữ nguyên tệp", inner.exists())

            rule.onNodeWithContentDescription("Xoá tệp").performClick()
            settle("Xoá tệp?")
            rule.onNodeWithText("Xoá").performClick()
            settle("Chưa có tệp nào")
            assertFalse(other.exists())
            assertFalse(inner.exists())
        } finally {
            other.deleteRecursively(); AppGraph.library.list()
        }
    }

    @Test fun deletingAGameOnlyHidesItFromTheFilesListAndKeepsTheData() {
        val game = fixture("v141-an-fixture")
        val rom = File(game, "fixture.gba").apply { writeText("synthetic fixture") }
        try {
            openFiles()
            settle("v141-an-fixture")
            rule.onNodeWithContentDescription("Xoá tệp").performClick()
            settle("Ẩn khỏi mục Tệp?")
            rule.onNodeWithText("Chỉ ẩn khỏi danh sách Tệp", substring = true).assertExists()
            rule.onNodeWithText("Ẩn").performClick()
            settle("Chưa có tệp nào")
            assertTrue("Không được xoá thư mục game", game.exists())
            assertTrue("Không được xoá tệp game", rom.exists())

            // Game vẫn còn nguyên ở mục Chơi.
            rule.onNodeWithText("Chơi").performClick()
            settle("v141-an-fixture")
            rule.onNodeWithText("v141-an-fixture").assertExists()
            rule.shot("v141-tep-an-khoi-danh-sach")
        } finally {
            game.deleteRecursively(); AppGraph.library.list()
        }
    }

    @Test fun tappingARowOpensItWhileTheDeleteButtonOnlyReportsTheChoice() {
        var opened = 0
        var deleted = 0
        val entry = LibraryFileEntry("k", "Hướng dẫn.pdf", LibraryFileKind.DOCUMENT, 2_048, 0L, File("Hướng dẫn.pdf"))
        rule.setContent {
            MonikaTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    LibraryFilesPage(listOf(entry), onOpen = { opened++ }, onDelete = { deleted++ }, onAdd = {})
                }
            }
        }
        settle("Hướng dẫn.pdf")
        rule.onNodeWithText("2 KB · —").assertExists()
        rule.onNodeWithText("Hướng dẫn.pdf").performClick()
        assertEquals(1, opened)
        assertEquals(0, deleted)
        rule.onNodeWithContentDescription("Xoá tệp").performClick()
        assertEquals(1, opened)
        assertEquals(1, deleted)
        rule.shot("v141-tep-mot-dong")
    }

    @Test fun tappingADocumentRowStartsTheReaderIntent() {
        val docs = fixture("v141-mo-fixture")
        try {
            File(docs, "Sách hướng dẫn.pdf").writeText("synthetic fixture")
            openFiles()
            settle("Sách hướng dẫn.pdf")
            rule.onNodeWithText("Sách hướng dẫn.pdf").performClick()
            rule.mainClock.advanceTimeBy(1_000)
            val started = shadowOf(rule.activity).nextStartedActivity
            // Robolectric trên Windows không dựng được URI FileProvider cho thẻ nhớ giả lập, khi đó app
            // phải báo lỗi rõ ràng thay vì im lặng — cả hai đều chứng minh cú bấm đã vào đúng hành động mở.
            val explained = rule.onAllNodesWithText("Chưa mở được tài liệu", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
            assertTrue("Bấm tài liệu phải gọi hành động mở", started?.action == Intent.ACTION_VIEW || explained)
            started?.let { assertEquals("application/pdf", it.type) }
        } finally {
            docs.deleteRecursively(); AppGraph.library.list()
        }
    }

    @Test fun sizeAndDateTextStayReadableForVietnameseUsers() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1 KB", formatFileSize(1_024))
        assertEquals("1,5 MB", formatFileSize((1.5 * 1_024 * 1_024).toLong()))
        assertEquals("2,0 GB", formatFileSize(2L * 1_024 * 1_024 * 1_024))
        assertEquals("—", formatFileDate(0))
        val day = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 2, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("02/11/2026", formatFileDate(day))
    }

    /** Tìm AndroidComposeView trong cây view (Robolectric không có cửa sổ thật để tự đẩy inset). */
    private fun findComposeView(view: android.view.View): android.view.View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /**
     * Edge-to-edge: thanh điều hướng 48dp nằm trong cửa sổ. Đáy danh sách phải chừa
     * DockClearance + inset (không dùng mốc 110dp cố định), nếu không hàng cuối bị che.
     */
    @Test fun lastRowStaysAboveTheDockWhenNavigationInsetIs48dp() {
        val insetDp = 48
        val density = rule.activity.resources.displayMetrics.density
        val entries = (0 until 20).map { i ->
            LibraryFileEntry(
                key = "k$i", name = "Tệp mẫu $i.pdf", kind = LibraryFileKind.DOCUMENT,
                sizeBytes = 1_024, addedAt = 0L, file = File("Tệp mẫu $i.pdf"),
            )
        }
        val measured = arrayOfNulls<Dp>(1)
        rule.setContent {
            MonikaTheme {
                measured[0] = dockContentClearance()
                LibraryFilesPage(entries, onOpen = {}, onDelete = {}, onAdd = {})
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        // Máy thật bật edge-to-edge: đẩy inset thanh điều hướng 48dp vào cửa sổ.
        // DecorView nuốt inset trước khi tới Compose nên đẩy thẳng vào AndroidComposeView.
        val insetPx = (insetDp * density).toInt()
        val composeView = findComposeView(rule.activity.window.decorView)
        assertNotNull("Không tìm thấy AndroidComposeView", composeView)
        ViewCompat.dispatchApplyWindowInsets(
            composeView!!,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, insetPx))
                .build(),
        )
        rule.mainClock.advanceTimeBy(1_000)

        // Item 0 là tiêu đề "N tệp · …", 20 tệp nằm ở index 1..20.
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(entries.size)
        rule.awaitComposeLayout {
            rule.onAllNodesWithText("Tệp mẫu ${entries.lastIndex}.pdf").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(
            "Khoảng đệm đáy phải cộng inset hệ thống",
            DockClearance + insetDp.dp, measured[0],
        )
        val rootBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val lastBottom = rule.onNodeWithText("Tệp mẫu ${entries.lastIndex}.pdf")
            .fetchSemanticsNode().boundsInRoot.bottom
        val safe = (DockClearance.value + insetDp) * density
        assertTrue(
            "Hàng cuối (đáy ${lastBottom}px) bị vùng dock/thanh điều hướng che: phải cách đáy ít nhất ${safe}px",
            rootBottom - lastBottom >= safe - 1f,
        )
        rule.shot("v141-tep-inset-48dp")
    }
}
