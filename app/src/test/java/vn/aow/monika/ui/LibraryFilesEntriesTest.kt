package vn.aow.monika.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.library.Game
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.ui.screens.libraryFileEntries
import java.io.File

/**
 * V207: mục Tệp văng vì LazyColumn trùng khoá khi nhiều game quét ngoài máy dùng chung
 * một thư mục (vd. Download). Khoá phải duy nhất cho mọi tổ hợp kho.
 */
class LibraryFilesEntriesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun external(dir: File, entry: File, name: String = entry.name) =
        Game(dir = dir, name = name, system = null, entry = entry, external = true)

    @Test fun externalGamesSharingFolderGetDistinctKeys() {
        val download = tmp.newFolder("Download")
        val a = File(download, "a.gba").apply { writeText("a") }
        val b = File(download, "b.gba").apply { writeText("b") }
        val entries = libraryFileEntries(
            listOf(external(download, a, "Game A"), external(download, b, "Game B")),
            emptyList(),
        )
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.key }.toSet().size)
        assertTrue(entries.all { it.key.startsWith("game:") })
        assertTrue(entries.none { it.key == "game:${download.absolutePath}" })
    }

    @Test fun internalGameKeepsFolderKey() {
        val dir = tmp.newFolder("Pokemon")
        val game = Game(dir = dir, name = "Pokemon", system = null, entry = File(dir, "game.nds"))
        val entries = libraryFileEntries(listOf(game), emptyList())
        assertEquals(listOf("game:${dir.absolutePath}"), entries.map { it.key })
    }

    @Test fun duplicateGamesAndDocumentsAreCollapsed() {
        val dir = tmp.newFolder("Shared")
        val rom = File(dir, "game.gba").apply { writeText("x") }
        val game = external(dir, rom, "Trùng")
        val doc = File(dir, "huong-dan.pdf").apply { writeText("pdf") }
        val entries = libraryFileEntries(
            listOf(game, game),
            listOf(LibraryDocument(doc), LibraryDocument(doc)),
        )
        assertEquals(2, entries.size)
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
    }

    @Test fun externalWithoutEntryStillProducesAKey() {
        val dir = tmp.newFolder("NoEntry")
        val entries = libraryFileEntries(
            listOf(Game(dir = dir, name = "Không rõ", system = null, entry = null, external = true)),
            emptyList(),
        )
        assertEquals(listOf("game:${dir.absolutePath}"), entries.map { it.key })
    }
}
