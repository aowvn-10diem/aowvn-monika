package vn.aow.monika.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.config.SystemDef
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameTask
import vn.aow.monika.ui.screens.*
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class LibraryPartsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }
    private fun game(name: String = "V137 fixture") = Game(File("/synthetic/$name"), name,
        SystemDef("v137", "Máy thử", "test"), null)
    private fun settle(text: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.awaitComposeLayout { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun gameCardKeepsActionPriorityAndRoutesOnlyTheChosenCallback() {
        var current by mutableStateOf(game())
        val calls = IntArray(5)
        rule.setContent { MonikaTheme { Column(Modifier.width(180.dp).padding(8.dp)) {
            GameTile(current, { calls[0]++ }, { calls[1]++ }, { calls[2]++ }, { calls[3]++ }, { calls[4]++ })
        } } }
        settle("Chơi")
        rule.onNodeWithText("Chơi").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Menu game").performClick()
        assertArrayEquals(intArrayOf(1,0,1,0,0),calls)
        rule.shot("v137-game-card")
        rule.runOnUiThread { current = game().copy(evicted=true,locked=true,needsExtract=true) }
        settle("Tải lại")
        rule.onNodeWithText("Chơi").assertDoesNotExist()
        rule.onNodeWithText("Cấp quyền để mở").assertDoesNotExist()
        rule.onNodeWithText("Tải lại").performClick()
        rule.runOnUiThread { current = game().copy(locked=true,needsExtract=true) }
        settle("Cấp quyền để mở")
        rule.onNodeWithText("Giải nén").assertDoesNotExist()
        rule.onNodeWithText("Cấp quyền để mở").performClick()
        rule.runOnUiThread { current = game().copy(system=null,needsExtract=true) }
        settle("Giải nén")
        rule.onNodeWithText("Chơi").assertDoesNotExist()
        rule.onNodeWithText("Giải nén").performClick()
        assertArrayEquals(intArrayOf(1,1,1,1,1),calls)
    }

    @Test fun continueCardStillOpensTheSameGame() {
        var played = 0
        rule.setContent { MonikaTheme { Column { ContinueCard(game()) { played++ } } } }
        settle("V137 fixture")
        rule.onNodeWithContentDescription("Chơi").assertExists()
        rule.onNodeWithText("V137 fixture").performClick()
        assertEquals(1,played)
    }

    @Test fun taskCardKeepsDeterminateAndUnknownProgressWithoutPlayAction() {
        var task by mutableStateOf(GameTask("v137", "Đang nhập fixture", null, "Đang giải nén", 42))
        rule.setContent { MonikaTheme { Column(Modifier.width(180.dp).padding(8.dp)) { TaskTile(task) } } }
        settle("42%")
        rule.onNodeWithText("Đang giải nén · 42%").assertExists()
        rule.onNodeWithText("Chơi").assertDoesNotExist()
        rule.shot("v137-task-card")
        rule.runOnUiThread { task=task.copy(phase="Chờ mạng",percent=null) }
        settle("Chờ mạng")
        rule.onNodeWithText("…").assertExists()
        rule.onNodeWithText("42%").assertDoesNotExist()
    }

    @Test fun gameMenuKeepsOwnedGamePlayAndDeleteCallbacks() {
        val g=game()
        var played: Game?=null;var deleted: Game?=null
        rule.setContent { MonikaTheme { Box(Modifier.fillMaxSize()) {
            GameMenuSheet(g, {}, emptySet(), {}, { played=it }, {}, { deleted=it }, {}, {})
        } } }
        settle("Xóa game")
        rule.onNodeWithText("Chơi").assertExists().performClick()
        assertSame(g,played)
        rule.onNodeWithText("Xóa game").assertExists().performClick()
        assertSame(g,deleted)
        rule.onNodeWithText("Ẩn khỏi Thư viện").assertDoesNotExist()
        rule.shot("v137-game-menu")
    }

    @Test fun lockedExternalGameMenuOffersAccessAndHideButNeverPlayOrDelete() {
        val g=game().copy(external=true,locked=true)
        var access=0;var played=0;var removed: Game?=null
        rule.setContent { MonikaTheme { Box(Modifier.fillMaxSize()) {
            GameMenuSheet(g, {}, emptySet(), {}, { played++ }, {}, { removed=it }, {}, { access++ })
        } } }
        settle("Ẩn khỏi Thư viện")
        rule.onNodeWithText("Chơi").assertDoesNotExist()
        rule.onNodeWithText("Xóa game").assertDoesNotExist()
        rule.onNodeWithText("Cấp quyền").performClick()
        rule.onNodeWithText("Ẩn khỏi Thư viện").performClick()
        assertEquals(0,played);assertEquals(1,access);assertSame(g,removed)
    }

    @Test fun extractedPresentationHelpersKeepTimeBoundariesAndPermissionIntents() {
        assertEquals("dưới 1 phút",formatPlayTime(59_999))
        assertEquals("1 phút",formatPlayTime(60_000))
        assertEquals("59 phút",formatPlayTime(59*60_000L))
        assertEquals("1 giờ",formatPlayTime(60*60_000L))
        assertEquals("2 giờ 5 phút",formatPlayTime(125*60_000L))
        val intent=allFilesAccessIntent(rule.activity)
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,intent.action)
        assertEquals("package:${rule.activity.packageName}",intent.data.toString())
        assertEquals(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,allFilesAccessIntent(rule.activity,false).action)
        assertNull(allFilesAccessIntent(rule.activity,false).data)
        val gba=game().copy(system=AppGraph.config.current.system("gba"))
        assertEquals(listOf(gba.system!!.core),coreIdsOf(listOf(gba,gba,game(),game().copy(system=null))))
    }
}
