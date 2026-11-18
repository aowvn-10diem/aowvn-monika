package vn.aow.monika.library

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Đưa file vào thư viện: chép/di chuyển, đặt tên thư mục, giải nén zip, file nhiều phần, chống zip-slip. Dữ liệu tự sinh. */
@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class ImporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun cleanLibrary() {
        GameStorage.games(context).listFiles().orEmpty().forEach { it.deleteRecursively() }
        GameStorage.downloads(context).listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    private fun source(name: String, size: Int = 20_000, fill: Int = 3): File =
        File(tmp.newFolder(), name).apply { writeBytes(ByteArray(size) { (it + fill).toByte() }) }

    private fun zip(name: String, entries: Map<String, ByteArray>): File {
        val file = File(tmp.newFolder(), name)
        ZipOutputStream(file.outputStream()).use { z ->
            for ((entryName, bytes) in entries) { z.putNextEntry(ZipEntry(entryName)); z.write(bytes); z.closeEntry() }
        }
        return file
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((entryName, bytes) in entries) { z.putNextEntry(ZipEntry(entryName)); z.write(bytes); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun plainFileIsCopiedIntoItsOwnFolderAndSourceIsKept() {
        val src = source("Game One.nds")

        val result = Importer.importFile(context, src, deleteSource = false, passwords = emptyList())

        assertNull(result.error)
        assertFalse(result.pending)
        assertEquals("Game One", result.dir.name)
        assertEquals(GameStorage.games(context).canonicalPath, result.dir.parentFile!!.canonicalPath)
        assertArrayEquals(src.readBytes(), File(result.dir, "Game One.nds").readBytes())
        assertTrue(src.exists())
    }

    @Test fun deleteSourceMovesThePlainFile() {
        val src = source("Rom.gba")
        val bytes = src.readBytes()

        val result = Importer.importFile(context, src, deleteSource = true, passwords = emptyList())

        assertFalse(src.exists())
        assertArrayEquals(bytes, File(result.dir, "Rom.gba").readBytes())
    }

    @Test fun sameNameGetsNumberedFolders() {
        val first = Importer.importFile(context, source("Same.nds"), deleteSource = false, passwords = emptyList())
        val second = Importer.importFile(context, source("Same.nds", fill = 9), deleteSource = false, passwords = emptyList())
        val third = Importer.importFile(context, source("Same.nds", fill = 11), deleteSource = false, passwords = emptyList())

        assertEquals(listOf("Same", "Same (2)", "Same (3)"), listOf(first.dir.name, second.dir.name, third.dir.name))
    }

    @Test fun illegalCharactersBecomeUnderscoresAndLongNamesAreTrimmed() {
        val weird = Importer.importFile(context, source("a:b?c.nds"), deleteSource = false, passwords = emptyList())
        assertEquals("a_b_c", weird.dir.name)

        val longName = "x".repeat(120) + ".nds"
        val long = Importer.importFile(context, source(longName), deleteSource = false, passwords = emptyList())
        assertEquals(80, long.dir.name.length)
    }

    @Test fun zipIsExtractedProgressReachesHundredAndSourceDeletedOnRequest() {
        val src = zip("Pack.zip", mapOf("rom.gba" to ByteArray(2_000) { 5 }, "docs/readme.txt" to "xin chào".toByteArray()))
        val progress = mutableListOf<Int>()

        val result = Importer.importFile(context, src, deleteSource = true, passwords = emptyList(), progress = ExtractProgress { progress += it })

        assertNull(result.error)
        assertEquals(2_000L, File(result.dir, "rom.gba").length())
        assertEquals("xin chào", File(result.dir, "docs/readme.txt").readText())
        assertFalse(src.exists())
        assertEquals(100, progress.last())
    }

    @Test fun zipSourceIsKeptWhenNotAskedToDelete() {
        val src = zip("Keep.zip", mapOf("a.nds" to ByteArray(100)))

        val result = Importer.importFile(context, src, deleteSource = false, passwords = emptyList())

        assertNull(result.error)
        assertTrue(src.exists())
        assertTrue(File(result.dir, "a.nds").isFile)
    }

    @Test fun incompleteMultiPartSetStaysPendingInStagingFolder() {
        val part2 = source("Big.part2.rar")

        val result = Importer.importFile(context, part2, deleteSource = true, passwords = emptyList())

        assertTrue(result.pending)
        assertTrue(result.error!!.contains("Đang chờ các phần còn lại"))
        assertEquals("big.rar", result.dir.name)
        assertEquals(GameStorage.downloads(context).canonicalPath + "/_phan", result.dir.parentFile!!.canonicalPath)
        assertTrue(File(result.dir, "Big.part2.rar").isFile)
        assertFalse(part2.exists())
        // Chưa có thư mục game nào trong thư viện.
        assertTrue(GameStorage.games(context).listFiles().orEmpty().isEmpty())
    }

    @Test fun extractInPlaceReportsMissingArchive() {
        val dir = tmp.newFolder("empty-game")
        File(dir, "notes.txt").writeText("không phải file nén")

        val result = Importer.extractInPlace(dir, emptyList())

        assertEquals("Không tìm thấy file nén trong thư mục game.", result.error)
        assertEquals(dir, result.dir)
    }

    @Test fun extractInPlaceUnpacksIntoTheFolderAndRemovesArchiveAndWorkDir() {
        val dir = tmp.newFolder("retry-game")
        zip("inner.zip", mapOf("game.exe" to ByteArray(300) { 1 }, "data/a.bin" to ByteArray(10)))
            .copyTo(File(dir, "inner.zip"))

        val result = Importer.extractInPlace(dir, emptyList())

        assertNull(result.error)
        assertTrue(File(dir, "game.exe").isFile)
        assertTrue(File(dir, "data/a.bin").isFile)
        assertFalse(File(dir, "inner.zip").exists())
        assertFalse(File(dir, ".dang-giai-nen").exists())
    }

    @Test fun unzipExtractsNestedEntriesUnderTarget() {
        val target = tmp.newFolder("sys")
        val data = zipBytes(mapOf("bios/gba_bios.bin" to ByteArray(16) { 2 }, "readme.txt" to "ok".toByteArray()))

        Importer.unzip(ByteArrayInputStream(data), target)

        assertEquals(16L, File(target, "bios/gba_bios.bin").length())
        assertEquals("ok", File(target, "readme.txt").readText())
    }

    @Test fun unzipRejectsZipSlipEntriesAndWritesNothingOutside() {
        val parent = tmp.newFolder("outer")
        val target = File(parent, "sys").apply { mkdirs() }
        val data = zipBytes(mapOf("../evil.txt" to "x".toByteArray()))

        try {
            Importer.unzip(ByteArrayInputStream(data), target)
            fail("phải từ chối entry thoát khỏi thư mục đích")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("File nén không hợp lệ"))
        }
        assertFalse(File(parent, "evil.txt").exists())
    }

    @Test fun importUriCopiesPlainFileFromFileUri() {
        val src = source("whatever.bin", size = 4_000, fill = 21)

        val result = Importer.importUri(context, Uri.fromFile(src), emptyList())

        assertNull(result.error)
        val copied = result.dir.listFiles().orEmpty().single()
        assertTrue(copied.name.startsWith("game-"))
        assertArrayEquals(src.readBytes(), copied.readBytes())
        assertNotNull(result.dir.parentFile)
    }

    // ---- Bộ đĩa PS1: nhập .cue và .bin rời phải vào CÙNG thư mục (issue #322, #578) ----

    private fun discard(): File = File(tmp.root, "nguon").apply { mkdirs() }

    @Test fun cueThenBinFromPickerEndUpInSameFolderAndSetIsComplete() {
        val cue = File(discard(), "Game.cue").apply { writeText("FILE \"Game.bin\" BINARY\n  TRACK 01 MODE1/2352\n") }
        val bin = File(discard(), "Game.bin").apply { writeBytes(ByteArray(2_048) { 7 }) }

        val first = Importer.importFile(context, cue, deleteSource = false, passwords = emptyList())
        val second = Importer.importFile(context, bin, deleteSource = false, passwords = emptyList())

        assertEquals(first.dir, second.dir)
        assertEquals(setOf("Game.cue", "Game.bin"), second.dir.listFiles().orEmpty().map { it.name }.toSet())
        assertNull(DiscSet.inspect(File(second.dir, "Game.cue"))) // đủ tệp → lõi nạp được
    }

    @Test fun binThenCueEndUpInSameFolder() {
        val bin = File(discard(), "Game (Disc 1).bin").apply { writeBytes(ByteArray(1_024) { 1 }) }
        val cue = File(discard(), "Game (Disc 1).cue").apply { writeText("FILE \"Game (Disc 1).bin\" BINARY\n") }

        Importer.importFile(context, bin, deleteSource = false, passwords = emptyList())
        val second = Importer.importFile(context, cue, deleteSource = false, passwords = emptyList())

        assertEquals(setOf("Game (Disc 1).bin", "Game (Disc 1).cue"), second.dir.listFiles().orEmpty().map { it.name }.toSet())
        assertNull(DiscSet.inspect(File(second.dir, "Game (Disc 1).cue")))
    }

    @Test fun trackNameDifferentFromCueStemStillJoinsTheSet() {
        val cue = File(discard(), "Game.cue").apply { writeText("FILE \"Game (Track 1).bin\" BINARY\n") }
        val track = File(discard(), "Game (Track 1).bin").apply { writeBytes(ByteArray(512) { 2 }) }

        val first = Importer.importFile(context, cue, deleteSource = false, passwords = emptyList())
        val second = Importer.importFile(context, track, deleteSource = false, passwords = emptyList())

        assertEquals(first.dir, second.dir)
        assertNull(DiscSet.inspect(File(second.dir, "Game.cue")))
    }

    @Test fun unrelatedFilesKeepTheirOwnFolders() {
        val notes = File(discard(), "notes.txt").apply { writeText("ghi chú") }
        val data = File(discard(), "notes.bin").apply { writeBytes(ByteArray(64) { 3 }) }

        val first = Importer.importFile(context, notes, deleteSource = false, passwords = emptyList())
        val second = Importer.importFile(context, data, deleteSource = false, passwords = emptyList())

        assertTrue(first.dir != second.dir) // không gom oan: chỉ gom khi có tệp danh sách đĩa
    }

    @Test fun sameFileNameIsNotOverwrittenInAnExistingSet() {
        val cue = File(discard(), "Game.cue").apply { writeText("FILE \"Game.bin\" BINARY\n") }
        val first = Importer.importFile(context, cue, deleteSource = false, passwords = emptyList())
        val bin1 = File(first.dir, "Game.bin").apply { writeBytes(ByteArray(32) { 9 }) }

        val again = File(discard(), "Game.bin").apply { writeBytes(ByteArray(48) { 5 }) }
        val second = Importer.importFile(context, again, deleteSource = false, passwords = emptyList())

        assertTrue(second.dir != first.dir) // tệp trùng tên → thư mục mới, không ghi đè
        assertEquals(32, bin1.length().toInt()) // tệp cũ còn nguyên
    }
}
