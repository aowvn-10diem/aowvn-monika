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
import vn.aow.monika.library.Game
import vn.aow.monika.runner.LaunchResult
import vn.aow.monika.ui.screens.GameDetailScreen
import vn.aow.monika.ui.screens.GameMenuSheet
import vn.aow.monika.ui.screens.GameTile
import vn.aow.monika.ui.screens.formatGameSize
import vn.aow.monika.ui.screens.gameSizeBytes
import vn.aow.monika.ui.screens.rememberGameLaunch
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

    @Test fun saveAndPatchArePlaceholdersOnly() {
        val g = game()
        var played = 0
        var deleted: Game? = null
        rule.setContent { MonikaTheme { GameDetailScreen(g, { played++ }, {}, onDelete = { deleted = it }) } }
        settle("Sắp có")
        rule.onAllNodesWithText("Sắp có").assertCountEquals(2)
        rule.onNodeWithText("Save").assertExists().assertHasNoClickAction()
        rule.onNodeWithText("Bản vá").assertExists().assertHasNoClickAction()
        assertEquals(0, played)
        assertNull(deleted)
    }

    @Test fun sizeIsMeasuredFromTheGameFolderAndShownWithComma() {
        val dir = File(rule.activity.cacheDir, "v139-size").apply { mkdirs() }
        File(dir, "game.bin").writeBytes(ByteArray(2048))
        File(dir, "readme.txt").writeText("xin chào")
        assertEquals(2048L + "xin chào".toByteArray().size, gameSizeBytes(Game(dir, "V139 size", SystemDef("v139", "Máy thử", "test"), null)))

        assertEquals("Chưa rõ", formatGameSize(0))
        assertEquals("512 B", formatGameSize(512))
        assertEquals("1,0 KB", formatGameSize(1024))
        assertEquals("1,5 KB", formatGameSize(1536))
        assertEquals("300 MB", formatGameSize(300L * 1024 * 1024))
        assertEquals("1,2 GB", formatGameSize(1288490188L))
    }
}
