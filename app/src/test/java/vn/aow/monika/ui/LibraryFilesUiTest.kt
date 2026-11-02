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
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameStorage
import vn.aow.monika.ui.screens.LibraryFileEntry
import vn.aow.monika.ui.screens.LibraryFileKind
import vn.aow.monika.ui.screens.LibraryFilesPage
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.SAVE_BACKUP_DIR
import vn.aow.monika.ui.screens.backupGameSaves
import vn.aow.monika.ui.screens.deleteGameWithSaveBackup
import vn.aow.monika.ui.screens.formatFileDate
import vn.aow.monika.ui.screens.formatFileSize
import vn.aow.monika.ui.screens.saveFilesInside
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.dockContentClearance
import java.io.File
import java.util.zip.ZipFile

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

    @Test fun filesSectionListsOnlyManagedGamesAndDocuments() {
        val game = fixture("v141-game-fixture")
        val docs = fixture("v141-doc-fixture")
        // Thư mục app KHÔNG nhận diện (vd. save của người chơi) — không được thành mục trong Tệp.
        val unknown = fixture("v141-kho-la")
        try {
            File(game, "fixture.gba").writeText("synthetic fixture")
            // Dung lượng khác nhau để mỗi dòng có chuỗi "dung lượng · ngày" riêng.
            val manual = File(docs, "Sách hướng dẫn.pdf").apply { writeText("synthetic fixture document") }
            val saveInUnknown = File(unknown, "save/quan-trong.sav").apply {
                parentFile?.mkdirs(); writeText("save nguoi choi")
            }

            openFiles()
            // Danh sách dựng từ game + tài liệu; tài liệu về sau một nhịp nên chờ đúng nó.
            settle("Sách hướng dẫn.pdf")
            rule.onNodeWithText("v141-game-fixture").assertExists()
            rule.onNodeWithText("Sách hướng dẫn.pdf").assertExists()
            rule.onNodeWithText("Game").assertExists()
            rule.onNodeWithText("Tài liệu").assertExists()
            rule.onNodeWithText("2 tệp", substring = true).assertIsDisplayed()
            // Dung lượng + ngày thêm lấy từ chính tệp trong kho, không phải số bịa.
            rule.onNodeWithText("${formatFileSize(manual.length())} · ${formatFileDate(manual.lastModified())}").assertExists()
            // Chỉ 2 mục app quản lý; thư mục lạ (và save bên trong) không xuất hiện để xoá.
            assertEquals(2, rule.onAllNodesWithContentDescription("Xoá tệp").fetchSemanticsNodes().size)
            rule.onNodeWithText("v141-kho-la").assertDoesNotExist()
            rule.onNodeWithText("quan-trong.sav").assertDoesNotExist()
            assertTrue(saveInUnknown.exists())
            repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
            rule.shot("v141-tep-danh-sach")
        } finally {
            game.deleteRecursively(); docs.deleteRecursively(); unknown.deleteRecursively(); AppGraph.library.list()
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

    /** Nút xoá của đúng một dòng (danh sách có thể có nhiều dòng). */
    private fun deleteButtonFor(name: String) = rule.onNode(
        hasContentDescription("Xoá tệp") and hasAnyAncestor(hasText(name, substring = true)),
    )

    @Test fun deletingADocumentRemovesOnlyThatFileAndKeepsTheFolder() {
        val docs = fixture("v141-doc-xoa")
        val manual = File(docs, "Sách hướng dẫn.pdf").apply { writeText("noi dung tai lieu") }
        val kept = File(docs, "Ghi chú.txt").apply { writeText("giu lai") }
        try {
            openFiles()
            settle("Sách hướng dẫn.pdf")
            deleteButtonFor("Sách hướng dẫn.pdf").performClick()
            settle("Xoá tài liệu?")
            rule.onNodeWithText("Sẽ xoá: tệp", substring = true).assertExists()
            rule.onNodeWithText("Được giữ lại: các tệp khác", substring = true).assertExists()

            rule.onNodeWithText("Hủy").performClick()
            settleGone("Xoá tài liệu?")
            assertTrue("Huỷ thì phải giữ nguyên tệp", manual.exists())

            deleteButtonFor("Sách hướng dẫn.pdf").performClick()
            settle("Xoá tài liệu?")
            rule.onNodeWithText("Xoá").performClick()
            rule.awaitComposeLayout { !manual.exists() }
            assertFalse(manual.exists())
            assertTrue("Tệp khác trong thư mục phải còn", kept.exists())
            assertTrue("Thư mục tài liệu phải còn", docs.exists())
        } finally {
            docs.deleteRecursively(); AppGraph.library.list()
        }
    }

    @Test fun deletingAGameRemovesOnlyItsFolderAndKeepsSavesAndUnknownFolders() {
        val game = fixture("v141-xoa-game")
        val unknown = fixture("v141-kho-la")
        File(game, "fixture.gba").writeText("synthetic fixture")
        val engineSave = File(game, "save/tien-do.sav").apply { parentFile?.mkdirs(); writeText("save trong thu muc game") }
        // Save của giả lập nằm ngoài thư mục game — không bao giờ bị xoá.
        val outsideSave = File(rule.activity.filesDir, "saves/mgba/v141-xoa-game.srm").apply {
            parentFile?.mkdirs(); writeText("save grap")
        }
        val unknownSave = File(unknown, "save/quan-trong.sav").apply { parentFile?.mkdirs(); writeText("save nguoi choi") }
        val backups = File(rule.activity.filesDir, SAVE_BACKUP_DIR)
        val knownBackups = backups.listFiles().orEmpty().toSet()
        try {
            openFiles()
            settle("v141-xoa-game")
            // Thư mục lạ không thành mục xoá được.
            rule.onNodeWithText("v141-kho-la").assertDoesNotExist()

            rule.onNodeWithContentDescription("Xoá tệp").performClick()
            settle("Xoá game?")
            rule.onNodeWithText("Sẽ xoá: thư mục game", substring = true).assertExists()
            rule.onNodeWithText("Được giữ lại", substring = true).assertExists()
            rule.onNodeWithText("Hủy").performClick()
            settleGone("Xoá game?")
            assertTrue("Huỷ thì phải giữ game", game.exists())

            rule.onNodeWithContentDescription("Xoá tệp").performClick()
            settle("Xoá game?")
            rule.onNodeWithText("Xoá").performClick()
            rule.awaitComposeLayout { !game.exists() }

            assertFalse("Chỉ thư mục game bị xoá", game.exists())
            assertFalse("Save trong thư mục game đã được sao lưu rồi mới xoá", engineSave.exists())
            assertTrue("Save giả lập ngoài thư mục game phải còn", outsideSave.exists())
            assertTrue("Thư mục lạ phải còn", unknown.exists())
            assertTrue("Save trong thư mục lạ phải còn", unknownSave.exists())
            // Bản sao lưu save được tạo trước khi xoá và báo rõ vị trí.
            val created = backups.listFiles().orEmpty().filter { it !in knownBackups }
            assertEquals("Phải có đúng 1 bản sao lưu save", 1, created.size)
            assertTrue(created.single().name.startsWith("xoa-v141-xoa-game"))
            rule.awaitComposeLayout {
                rule.onAllNodesWithText("Bản sao lưu save", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            // Danh sách tự làm mới: không còn dòng nào để xoá.
            rule.awaitComposeLayout { rule.onAllNodesWithText("Chưa có tệp nào").fetchSemanticsNodes().isNotEmpty() }
            rule.shot("v141-tep-xoa-game")
        } finally {
            game.deleteRecursively(); unknown.deleteRecursively(); outsideSave.delete()
            backups.listFiles().orEmpty().filter { it !in knownBackups }.forEach { it.delete() }
            AppGraph.library.list()
        }
    }

    @Test fun deletingAGameBacksUpSavesFirstAndTheBackupRestoresThem() {
        val game = fixture("v141-save-game")
        val rom = File(game, "fixture.gba").apply { writeText("synthetic fixture") }
        val save = File(game, "save/tien-do.sav").apply {
            parentFile?.mkdirs(); writeText("du lieu save quan trong")
        }
        val target = Game(game, "v141-save-game", AppGraph.config.current.system("gba"), rom)
        val backups = File(rule.activity.filesDir, SAVE_BACKUP_DIR)
        val knownBackups = backups.listFiles().orEmpty().toSet()
        try {
            assertEquals(listOf("tien-do.sav"), saveFilesInside(game).map { it.name })
            val backup = backupGameSaves(rule.activity, target)
            assertNotNull("Phải tạo được bản sao lưu save", backup)
            assertTrue(backup!!.isFile)
            assertTrue(backup.name.startsWith("xoa-v141-save-game"))

            // Bản sao lưu giữ đúng đường dẫn + nội dung và khôi phục được vào thư mục mới.
            val restoreDir = File(rule.activity.cacheDir, "v141-khoi-phuc").apply { deleteRecursively(); mkdirs() }
            ZipFile(backup).use { zip ->
                val entry = zip.getEntry("game/save/tien-do.sav")
                assertNotNull("Bản sao lưu phải có tệp save", entry)
                val out = File(restoreDir, "save/tien-do.sav").apply { parentFile?.mkdirs() }
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                assertNotNull("Bản sao lưu phải có tệp mô tả", zip.getEntry("monika-xoa-game.txt"))
            }
            assertEquals("du lieu save quan trong", File(restoreDir, "save/tien-do.sav").readText())
            restoreDir.deleteRecursively()

            // Sao lưu lỗi → KHÔNG xoá gì (sao lưu luôn đi trước).
            val refused = deleteGameWithSaveBackup(rule.activity, target, backup = { _, _ -> error("hết chỗ") })
            assertFalse(refused.deleted)
            assertTrue("Chưa sao lưu được thì không được xoá", game.exists())
            assertTrue(save.exists())
            assertTrue(refused.message.contains("Chưa sao lưu được save"))
        } finally {
            game.deleteRecursively()
            backups.listFiles().orEmpty().filter { it !in knownBackups }.forEach { it.delete() }
            AppGraph.library.list()
        }
    }

    @Test fun unmanagedFoldersAreNeverDeletedEvenWhenAsked() {
        val nested = File(fixture("v141-kho-la"), "save").apply { mkdirs() }
        val save = File(nested, "quan-trong.sav").apply { writeText("save nguoi choi") }
        val outside = File(rule.activity.cacheDir, "v141-ngoai-kho").apply { mkdirs() }
        val outsideFile = File(outside, "du-lieu.bin").apply { writeText("x") }
        try {
            // Thư mục con trong kho và thư mục ngoài kho đều không phải game app quản lý.
            for (dir in listOf(nested, outside)) {
                val result = deleteGameWithSaveBackup(rule.activity, Game(dir, "la", null, null))
                assertFalse("App không được xoá thư mục ngoài phạm vi quản lý: $dir", result.deleted)
                assertTrue("Thư mục phải còn nguyên: $dir", dir.exists())
                assertTrue(result.message.contains("không nằm trong kho game"))
            }
            assertTrue(save.exists())
            assertTrue(outsideFile.exists())
        } finally {
            File(GameStorage.games(rule.activity), "v141-kho-la").deleteRecursively()
            outside.deleteRecursively()
            AppGraph.library.list()
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
