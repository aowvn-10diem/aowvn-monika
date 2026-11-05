package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.config.SystemDef
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.library.Game
import vn.aow.monika.runner.LaunchResult
import vn.aow.monika.ui.screens.GameDetailScreen
import vn.aow.monika.ui.screens.GameMenuSheet
import vn.aow.monika.ui.screens.GameSizeMeter
import vn.aow.monika.ui.screens.GameTile
import vn.aow.monika.ui.screens.formatGameSize
import vn.aow.monika.ui.screens.fileSizeBytes
import vn.aow.monika.ui.screens.rememberGameLaunch
import vn.aow.monika.ui.screens.sizeLabel
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

/**
 * V139 — trang chi tiết game + thanh Tác vụ (Chơi · Save · Bản vá · Xóa).
 * Kiểm: mở trang từ danh sách, hộp thoại xác nhận xóa, nút Chơi đi đúng luồng chạy của Thư viện,
 * Save/Bản vá chỉ là nút "Sắp có".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class GameDetailTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    private fun game() = Game(File("/synthetic/v139"), "V139 fixture", SystemDef("v139", "Máy thử", "test"), null)

    private fun settle(text: String) = settleUntil {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun settleUntil(condition: () -> Boolean) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.awaitComposeLayout(condition)
    }

    @Test fun tappingTheGameCardOpensItsDetailPage() {
        val g = game()
        rule.setContent { MonikaTheme { Column(Modifier.width(180.dp).padding(8.dp)) {
            GameTile(g, {}, {}, {}, {}, {})
        } } }
        settleUntil { rule.onAllNodesWithContentDescription("Mở trang game ${g.name}").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Mở trang game ${g.name}").performTouchInput { click() }
        val started = shadowOf(rule.activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(g.key, started.getStringExtra(MainActivity.EXTRA_GAME_KEY))
    }

    @Test fun gameMenuAlsoOpensTheDetailPage() {
        val g = game()
        rule.setContent { MonikaTheme { Box(Modifier.fillMaxSize()) {
            GameMenuSheet(g, {}, emptySet(), {}, {}, {}, {}, {}, {})
        } } }
        settle("Trang game")
        rule.onNodeWithText("Trang game").performClick()
        val started = shadowOf(rule.activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(g.key, started.getStringExtra(MainActivity.EXTRA_GAME_KEY))
    }

    @Test fun detailPageShowsSystemSizeAndPathThenPlaysThroughTheLibraryLaunchFlow() {
        val g = game()
        var played: Game? = null
        var result: LaunchResult? = null
        rule.setContent { MonikaTheme {
            // Đúng luồng chạy của Thư viện (ExperimentalGameWarning.kt).
            val play = rememberGameLaunch { game, outcome -> played = game; result = outcome }
            GameDetailScreen(g, play, {})
        } }
        // Thư mục không có thật → dung lượng chưa đọc được, vẫn phải hiện dòng thay vì treo.
        settle("Chưa rõ")
        rule.onNodeWithText(g.name).assertExists()
        assertTrue(rule.onAllNodesWithText("Máy thử").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText(File("/synthetic/v139").path).assertExists()
        rule.shot("v139-trang-game")
        rule.onNodeWithText("Chơi").performClick()
        assertEquals(g, played)
        // Hệ "test" không có trình chạy → launcher hiện hành trả lỗi; chứng tỏ đã gọi đúng luồng.
        assertTrue(result is LaunchResult.Failed)
    }

    @Test fun deleteNeedsConfirmationBeforeRemovingTheGame() {
        val g = game()
        var deleted: Game? = null
        rule.setContent { MonikaTheme { GameDetailScreen(g, {}, {}, onDelete = { deleted = it }) } }
        settle("Xóa")
        rule.onNodeWithText("Xóa game?").assertDoesNotExist()
        rule.onNodeWithText("Xóa").performClick()
        settle("Xóa game?")
        rule.onNodeWithText("Xóa game?").assertExists()
        // Hộp thoại phải nói rõ xoá gì và giữ gì (Luna 4: trước đây hứa giữ save nhưng lại xoá đệ quy).
        rule.onNodeWithText("Sẽ xóa: thư mục game", substring = true).assertExists()
        rule.onNodeWithText("Được giữ lại", substring = true).assertExists()
        rule.onNodeWithText("save-backups", substring = true).assertExists()
        assertNull("Chưa xác nhận thì không được xóa", deleted)
        rule.onNodeWithText("Hủy").performClick()
        settle("Chơi")
        rule.onNodeWithText("Xóa game?").assertDoesNotExist()
        assertNull(deleted)
        rule.onNodeWithText("Xóa").performClick()
        settle("Xóa game?")
        rule.onNodeWithText("Xóa game").performClick()
        assertEquals(g, deleted)
    }

    /** V140: Save đã có nội dung thật (mục Save ngay dưới thanh Tác vụ); chỉ Bản vá còn "Sắp có". */
    @Test fun saveSectionIsRealAndOnlyPatchStaysComingSoon() {
        val g = game()
        var played = 0
        var deleted: Game? = null
        rule.setContent { MonikaTheme { GameDetailScreen(g, { played++ }, {}, onDelete = { deleted = it }) } }
        settle("Sắp có")
        rule.onAllNodesWithText("Sắp có").assertCountEquals(1)
        rule.onNodeWithText("Bản vá").assertExists().assertHasNoClickAction()
        // Hệ "test" chưa hỗ trợ chuyển save → mục Save nói rõ thay vì để trống.
        rule.onNodeWithText("Hệ này chưa hỗ trợ chuyển save", substring = true).assertExists()
        assertEquals(0, played)
        assertNull(deleted)
    }

    @Test fun sizeIsMeasuredFromTheGameFolderAndShownWithComma() {
        val dir = File(rule.activity.cacheDir, "v139-size").apply { mkdirs() }
        File(dir, "game.bin").writeBytes(ByteArray(2048))
        File(dir, "readme.txt").writeText("xin chào")
        assertEquals(2048L + "xin chào".toByteArray().size, meter().size(Game(dir, "V139 size", SystemDef("v139", "Máy thử", "test"), null)))

        assertEquals("Chưa rõ", formatGameSize(0))
        assertEquals("512 B", formatGameSize(512))
        assertEquals("1,0 KB", formatGameSize(1024))
        assertEquals("1,5 KB", formatGameSize(1536))
        assertEquals("300 MB", formatGameSize(300L * 1024 * 1024))
        assertEquals("1,2 GB", formatGameSize(1288490188L))
    }

    /** Luna 3 (PR #430): tệp sâu hơn 6 tầng vẫn phải được cộng vào dung lượng, không chỉ đếm tầng nông. */
    @Test fun deepFilesBelowSevenLevelsAreCountedInTheGameSize() {
        val dir = File(rule.activity.cacheDir, "v139-deep").apply { deleteRecursively(); mkdirs() }
        File(dir, "shallow.bin").writeBytes(ByteArray(1024))
        // Tệp nằm ở tầng 8 — bản cũ (maxDepth(6)) bỏ sót đúng tệp này.
        val deep = File(dir, "a/b/c/d/e/f/g").apply { mkdirs() }
        File(deep, "deep.bin").writeBytes(ByteArray(4096))
        File(deep, "deeper.bin").writeBytes(ByteArray(2048))
        val game = Game(dir, "V139 deep", SystemDef("v139", "Máy thử", "test"), null)
        assertEquals(1024L + 4096L + 2048L, meter().size(game))

        // Chỉ còn tệp sâu: tổng phải khác 0, chứng tỏ không giới hạn độ sâu.
        File(dir, "shallow.bin").delete()
        assertEquals(4096L + 2048L, meter().size(game))
        assertEquals("6,0 KB", sizeLabel(meter().size(game)))
    }

    /** Luna 4 (PR #430/#476): đọc được **một phần** cây thì không được trả tổng thiếu như số chắc chắn. */
    @Test fun partiallyUnreadableFolderReportsUnknownSizeInsteadOfAShortTotal() {
        val dir = File(rule.activity.cacheDir, "v139-partial").apply { deleteRecursively(); mkdirs() }
        File(dir, "readable.bin").writeBytes(ByteArray(1024))
        val broken = File(dir, "broken").apply { mkdirs() }
        File(broken, "hidden.bin").writeBytes(ByteArray(5000))
        val game = Game(dir, "V139 partial", SystemDef("v139", "Máy thử", "test"), null)

        // Đọc được trọn cây → tổng chính xác.
        assertEquals(6024L, meter().size(game))

        // Thư mục con bị chặn quyền/không liệt kê được → "Chưa rõ", không phải 1024 B.
        val list = { f: File -> if (f.name == "broken") null else f.listFiles() }
        val partial = meter(list = list).folder(game)
        assertFalse("Phải đánh dấu chưa đọc đủ cây", partial!!.complete)
        assertEquals("Tổng đo được chỉ là mức tối thiểu", 1024L, partial.bytes)
        assertNull(meter(list = list).size(game))
        assertEquals("Chưa rõ", sizeLabel(meter(list = list).size(game)))
    }

    /** Tệp lỗi (không đọc được dung lượng) giữa cây cũng làm tổng thành "Chưa rõ". */
    @Test fun fileLengthErrorReportsUnknownSize() {
        val dir = File(rule.activity.cacheDir, "v139-len-error").apply { deleteRecursively(); mkdirs() }
        File(dir, "ok.bin").writeBytes(ByteArray(2048))
        val bad = File(dir, "bad.bin").apply { writeBytes(ByteArray(777)) }
        val game = Game(dir, "V139 len error", SystemDef("v139", "Máy thử", "test"), null)

        val length = { f: File -> if (f.name == "bad.bin") null else f.length() }
        val size = meter(length = length).folder(game)
        assertFalse("Có tệp không đọc được thì chưa đủ", size!!.complete)
        assertEquals(2048L, size.bytes)
        assertNull(meter(length = length).size(game))
        assertEquals("Chưa rõ", sizeLabel(meter(length = length).size(game)))
        assertTrue("Tệp lỗi vẫn tồn tại trên đĩa", bad.exists())
    }

    /**
     * Luna 5 (#430): `File.length()` trả 0 khi không đọc được metadata nên tổng bị thiếu mà không
     * biết. Đường chạy thật: tệp biến mất sau khi liệt kê → `Files.size` ném lỗi → "Chưa rõ".
     */
    @Test fun deletedFileDuringScanReportsUnknownSizeInsteadOfCountingZero() {
        val dir = File(rule.activity.cacheDir, "v139-race").apply { deleteRecursively(); mkdirs() }
        File(dir, "a.bin").writeBytes(ByteArray(1024))
        val gone = File(dir, "b.bin").apply { writeBytes(ByteArray(2048)) }
        val game = Game(dir, "V139 race", SystemDef("v139", "Máy thử", "test"), null)

        assertEquals(3072L, meter().size(game))

        // Liệt kê xong rồi tệp b.bin biến mất (race thật trên đĩa); danh sách vẫn còn tên tệp đó.
        val listing = dir.listFiles()!!.also { gone.delete() }
        val size = meter(list = { listing }).folder(game)!!
        assertFalse("Tệp mất khi đọc kích thước thì chưa đủ cây", size.complete)
        assertEquals("Chỉ cộng tệp còn đọc được", 1024L, size.bytes)
        assertNull(meter(list = { listing }).size(game))
        assertEquals("Chưa rõ", sizeLabel(meter(list = { listing }).size(game)))
        assertTrue(
            "Phải ghi Diagnostics khi không đọc được kích thước",
            Diagnostics.list(rule.activity).any { it.component == "thu-vien" },
        )
    }

    /** `fileSizeBytes` (đường thật): tệp không tồn tại phải trả null + Diagnostics, không phải 0. */
    @Test fun fileSizeOfMissingFileIsUnknownNotZero() {
        val missing = File(rule.activity.cacheDir, "v139-missing-size.bin")
        assertNull("File.length() sẽ trả 0; API có báo lỗi phải trả null", fileSizeBytes(rule.activity, missing))
        assertEquals("Chưa rõ", sizeLabel(fileSizeBytes(rule.activity, missing)))
        assertTrue(
            "Phải ghi Diagnostics khi tệp không đọc được",
            Diagnostics.list(rule.activity).any { it.component == "thu-vien" },
        )

        // Tệp rỗng thật vẫn là 0 (không phải lỗi).
        val empty = File(rule.activity.cacheDir, "v139-empty-size.bin").apply { writeBytes(ByteArray(0)) }
        assertEquals(0L, fileSizeBytes(rule.activity, empty))
    }

    /**
     * Luna 5 (PR #430): **không xác định được** một mục có phải symlink (lỗi I/O/quyền) thì phải coi
     * là chưa đọc đủ cây → "Chưa rõ", và phải ghi Diagnostics; không được trả tổng thiếu.
     */
    @Test fun unknownSymlinkStateReportsUnknownSizeAndRecordsDiagnostics() {
        val dir = File(rule.activity.cacheDir, "v139-symlink").apply { deleteRecursively(); mkdirs() }
        File(dir, "readable.bin").writeBytes(ByteArray(1024))
        File(dir, "maybe-link.bin").writeBytes(ByteArray(4096))
        val game = Game(dir, "V139 symlink", SystemDef("v139", "Máy thử", "test"), null)
        val before = Diagnostics.list(rule.activity).size

        // Đọc bình thường: không có symlink thật → tổng chính xác.
        assertEquals(5120L, meter().size(game))

        // Không kiểm được symlink (mô phỏng lỗi I/O/quyền) → chưa đọc đủ, "Chưa rõ".
        val unknown = meter(isSymlink = { null })
        assertFalse("Không xác định được symlink thì chưa đủ cây", unknown.folder(game)!!.complete)
        assertNull(unknown.size(game))
        assertEquals("Chưa rõ", sizeLabel(unknown.size(game)))

        // Gặp symlink thật (mô phỏng) → bỏ qua, vẫn tính là chưa đủ.
        val linked = meter(isSymlink = { it.name == "maybe-link.bin" })
        val linkedSize = linked.folder(game)!!
        assertFalse(linkedSize.complete)
        assertEquals(1024L, linkedSize.bytes)

        // Mọi lần không đọc đủ đều ghi Diagnostics (component "thu-vien").
        val reports = Diagnostics.list(rule.activity)
        assertTrue("Phải ghi Diagnostics khi không xác định được symlink", reports.size > before)
        assertTrue("Báo cáo phải thuộc thành phần thu-vien", reports.any { it.component == "thu-vien" })
    }

    /** Thư mục rỗng hoặc không đọc được không được làm crash và phải hiện nhãn hợp lý. */
    @Test fun emptyOrUnreadableFolderGivesASensibleSizeLabel() {
        val empty = File(rule.activity.cacheDir, "v139-empty").apply { deleteRecursively(); mkdirs() }
        assertEquals(0L, meter().size(Game(empty, "V139 empty", SystemDef("v139", "Máy thử", "test"), null)))
        assertEquals("0 B", sizeLabel(0L))

        val missing = File(rule.activity.cacheDir, "v139-missing")
        assertNull(meter().size(Game(missing, "V139 missing", SystemDef("v139", "Máy thử", "test"), null)))
        assertEquals("Chưa rõ", sizeLabel(null))

        // Trỏ vào một tệp thay vì thư mục: không phải thư mục game → chưa rõ, không ném lỗi.
        val file = File(rule.activity.cacheDir, "v139-file.bin").apply { writeBytes(ByteArray(16)) }
        assertNull(meter().size(Game(file, "V139 file", SystemDef("v139", "Máy thử", "test"), null)))
        assertEquals("1,5 KB", sizeLabel(1536L))
    }

    /** Dựng bộ đo dung lượng với seam giả lập lỗi đọc (chạy thật để mặc định). */
    private fun meter(
        list: ((File) -> Array<File>?)? = null,
        length: ((File) -> Long?)? = null,
        isSymlink: ((File) -> Boolean?)? = null,
    ) = GameSizeMeter(rule.activity, list, length, isSymlink)
}
