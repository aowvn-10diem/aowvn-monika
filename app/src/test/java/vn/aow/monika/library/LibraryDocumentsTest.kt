package vn.aow.monika.library

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.MonikaConfig
import vn.aow.monika.config.SystemDef
import vn.aow.monika.ui.TestApp
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class LibraryDocumentsTest {
    private lateinit var root: File
    private val cfg = MonikaConfig(systems = listOf(SystemDef("gba", "GBA", "libretro", extensions = listOf("gba"))))
    @Before fun setup() { root = Files.createTempDirectory("v84-fixture").toFile() }
    @After fun clean() { root.deleteRecursively() }
    private fun dir(name: String, vararg files: String): File = File(root, name).apply {
        mkdirs(); files.forEach { File(this, it).apply { parentFile!!.mkdirs(); writeText("fixture") } }
    }
    private fun classify(d: File, config: MonikaConfig = cfg) = partitionLibrary(listOf(GameDetector.detect(d, config)), config)

    @Test fun pdfOnlyAndNestedDocumentsAreNotGamesAndUseConfig() {
        val folder = dir("Tai lieu", "Hướng dẫn.PDF", "chuong/Tóm tắt.txt")
        val result = classify(folder)
        assertTrue(result.games.isEmpty())
        assertEquals(setOf("Hướng dẫn.PDF", "Tóm tắt.txt"), result.documents.map { it.name }.toSet())
        assertTrue(classify(folder, cfg.copy(documentExtensions = listOf("md"))).documents.isEmpty())
        assertTrue(classify(dir("Tuy chon", "Đọc.md"), cfg.copy(documentExtensions = listOf("MD"))).documents.isNotEmpty())
    }

    @Test fun mixedGameAndManualRemainOneGameAndUnknownFilesAreExcluded() {
        val result = classify(dir("Game va huong dan", "fixture.gba", "Hướng dẫn.pdf"))
        assertEquals("gba", result.games.single().system!!.id)
        assertTrue(result.documents.isEmpty())
        val unknown = classify(dir("Khong phai game", "image.png", "unknown.bin"))
        assertTrue(unknown.games.isEmpty()); assertTrue(unknown.documents.isEmpty())
    }

    @Test fun lockedEvictedAndPendingArchivesKeepRecoveryActions() {
        val folder = dir("Cu", "Hướng dẫn.pdf")
        for (game in listOf(Game(folder, "Khóa", null, null, locked = true),
            Game(folder, "Tải lại", null, null, evicted = true), Game(folder, "Giải nén", null, null, needsExtract = true))) {
            val result = partitionLibrary(listOf(game), cfg)
            assertEquals(listOf(game), result.games); assertTrue(result.documents.isEmpty())
        }
    }

    @Test fun externalViewerGetsContentUriReadGrantAndCorrectMime() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val folder = File(app.filesDir, "v84-view").apply { mkdirs() }
        val formats = mapOf("pdf" to "application/pdf", "txt" to "text/plain", "epub" to "application/epub+zip", "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        try {
            formats.forEach { (ext, mime) ->
                val intent = documentViewIntent(app, LibraryDocument(File(folder, "fixture.$ext").apply { writeText("fixture") }))
                assertEquals(Intent.ACTION_VIEW, intent.action); assertEquals(mime, intent.type)
                assertEquals("content", intent.data!!.scheme)
                assertEquals("${app.packageName}.files", intent.data!!.authority)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                assertEquals(intent.data, intent.clipData!!.getItemAt(0).uri)
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun deletingOneDocumentPreservesSiblingsAndGame() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val library = GameLibrary(app, vn.aow.monika.AppGraph.config)
        val folder = dir("Suppression", "Guide.pdf", "other.txt", "fixture.gba")
        val document = LibraryDocument(File(folder, "Guide.pdf"))
        assertTrue(library.deleteDocument(document)); assertFalse(document.file.exists())
        assertTrue(File(folder, "other.txt").exists()); assertTrue(File(folder, "fixture.gba").exists())
    }
}
