package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.library.GameStorage
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.screens.LibraryScreen
import vn.aow.monika.ui.screens.LibraryDocumentCard
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class LibraryDocumentUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun setup() { rule.mainClock.autoAdvance = false }
    @Test fun documentCardOpensExternalActionAndDeletesWithoutAnyPlayAction() {
        var opened = 0; var deleted = 0
        rule.setContent { MonikaTheme { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Tài liệu", style = Monika.type.sectionTitle, color = Monika.colors.text)
            LibraryDocumentCard(LibraryDocument(File("Hướng dẫn tiếng Việt.pdf")), { opened++ }, { deleted++ })
        } } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Hướng dẫn tiếng Việt.pdf").assertIsDisplayed()
        rule.onNodeWithText("Mở tài liệu").assertIsDisplayed().performClick()
        rule.onNodeWithText("Xóa").assertIsDisplayed().performClick()
        assertEquals(1, opened); assertEquals(1, deleted)
        rule.onNodeWithText("Chơi").assertDoesNotExist()
        repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.shot("v84-tai-lieu-khong-choi")
    }

    @Test fun realLibrarySeparatesDocumentFolderAndKeepsMixedGame() {
        val storage = GameStorage.games(rule.activity)
        val docs = File(storage, "v84-tai-lieu-fixture").apply { mkdirs() }
        val game = File(storage, "v84-game-fixture").apply { mkdirs() }
        val unknown = File(storage, "v84-unknown-fixture").apply { mkdirs() }
        try {
            File(docs, "Sách hướng dẫn.pdf").writeText("synthetic fixture")
            File(game, "fixture.gba").writeText("synthetic fixture")
            File(game, "Manual.pdf").writeText("synthetic fixture")
            File(unknown, "unknown.bin").writeText("synthetic fixture")
            AppGraph.prefs.libraryStorageExplained = true
            val listed = AppGraph.library.list()
            assertTrue(listed.any { it.dir == game && it.system?.id == "gba" })
            assertFalse(listed.any { it.dir == docs || it.dir == unknown })
            assertTrue(AppGraph.library.cachedDocuments.any { it.file.parentFile == docs })
            assertFalse(AppGraph.library.cachedDocuments.any { it.file.parentFile == game })
            rule.setContent { MonikaTheme { LibraryScreen {} } }
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitUntil(timeoutMillis = 2_000) {
                rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
                rule.onAllNodesWithText("v84-game-fixture").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("v84-game-fixture").assertExists()
            rule.onNodeWithText("v84-tai-lieu-fixture").assertDoesNotExist()
            rule.onNodeWithText("v84-unknown-fixture").assertDoesNotExist()
            rule.onNodeWithText("Sách hướng dẫn.pdf").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Tài liệu").assertExists()
            repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
            rule.shot("v84-thu-vien-phan-nhom")
        } finally { docs.deleteRecursively(); game.deleteRecursively(); unknown.deleteRecursively(); AppGraph.library.list() }
    }
}
