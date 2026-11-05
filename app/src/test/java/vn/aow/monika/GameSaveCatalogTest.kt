package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.defaultSaveProfiles
import vn.aow.monika.config.SystemDef
import vn.aow.monika.library.Game
import vn.aow.monika.library.GameSaveCatalog
import vn.aow.monika.library.GameSaveEntry
import vn.aow.monika.library.GameSaveTransfer
import vn.aow.monika.library.SaveFormats
import vn.aow.monika.library.SaveTransferException
import vn.aow.monika.ui.TestApp
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * V140 — mục Save ở trang game: liệt kê save hiện có (tên, thời điểm, dung lượng) và **sao lưu
 * trước khi ghi đè** (A54), cùng lỗi có loại khi tệp không phải save.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class GameSaveCatalogTest {
    @Before fun nativePathFixture() {
        // CoreManager đọc ABI của app; Robolectric mặc định không có nativeLibraryDir.
        ApplicationProvider.getApplicationContext<android.app.Application>().applicationInfo.nativeLibraryDir = "/fake/arm64"
    }

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun fixture(dir: File): Game {
        val rom = File(dir, "Fixture.gba").apply { writeText("synthetic ROM identity fixture, not a playable ROM") }
        return Game(dir, "Fixture", AppGraph.config.current.system("gba"), rom)
    }

    /** Danh sách save: tên dễ đọc, thời điểm ghi, dung lượng thật; mới ghi xếp trước. */
    @Test fun listsExistingSavesWithReadableNameTimeAndSize() {
        val dir = Files.createTempDirectory("save-list").toFile()
        try {
            // Dùng đúng target thật (dưới filesDir) để danh sách đọc đúng chỗ bộ chuyển save ghi.
            val t = GameSaveTransfer.target(app, fixture(dir))
            val ram = t.ram!!.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(8192) { 1 }) }
            val state = t.states.getValue("state/slot1.state").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(300)) }
            ram.setLastModified(1_700_000_000_000)
            state.setLastModified(1_800_000_000_000)
            try {
                val saves = GameSaveCatalog.list(app, fixture(dir))
                assertEquals(2, saves.size)
                // Mới ghi trước: state (1.800…) đứng trước RAM (1.700…).
                assertEquals("State: lưu nhanh 1", saves[0].name)
                assertEquals(300L, saves[0].size)
                assertEquals(1_800_000_000_000L, saves[0].savedAt)
                assertEquals("Save trong game (SRAM)", saves[1].name)
                assertEquals(8192L, saves[1].size)
                assertEquals(1_700_000_000_000L, saves[1].savedAt)
            } finally {
                ram.delete()
                t.states.values.forEach { it.delete() }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Chưa có save → danh sách rỗng (UI hiện trạng thái rỗng, không phải lỗi). */
    @Test fun gameWithoutSavesReturnsEmptyList() {
        val dir = Files.createTempDirectory("save-empty").toFile()
        try {
            assertTrue(GameSaveCatalog.list(app, fixture(dir)).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Placeholder 0 byte của lõi (RAM/state rỗng) không phải save để hiện. */
    @Test fun emptyPlaceholderFilesAreNotListedAsSaves() {
        val dir = Files.createTempDirectory("save-zero").toFile()
        try {
            val t = GameSaveTransfer.target(app, fixture(dir))
            val ram = t.ram!!.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0)) }
            val state = t.states.getValue("state/slot1.state").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0)) }
            try {
                val saves = GameSaveCatalog.list(app, fixture(dir))
                assertTrue("Tệp 0 byte không phải save: $saves", saves.isEmpty())
            } finally {
                ram.delete()
                t.states.values.forEach { it.delete() }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Hệ chưa hỗ trợ chuyển save → báo rõ, không ném lỗi. */
    @Test fun unsupportedSystemIsReportedInsteadOfCrashing() {
        val game = Game(File("/synthetic/v140"), "Máy thử", SystemDef("v140", "Máy thử", "test"), null)
        assertFalse(GameSaveCatalog.supported(app, game))
    }

    /**
     * A54: sao lưu **trước** khi ghi đè. Bản sao lưu phải chứa đúng save cũ, còn save trên đĩa là
     * bản mới; nếu không có bước sao lưu thì nội dung cũ mất hẳn.
     */
    @Test fun importBacksUpTheOldSaveBeforeOverwriting() {
        val dir = Files.createTempDirectory("save-backup").toFile()
        val game = fixture(dir)
        val t = GameSaveTransfer.target(app, game)
        val ram = t.ram!!
        val backupRoot = File(app.filesDir, "save-backups")
        val known = backupRoot.listFiles().orEmpty().toSet()
        try {
            ram.parentFile!!.mkdirs()
            ram.writeBytes(ByteArray(8192) { 8 }) // save muốn nhập lại

            val zip = ByteArrayOutputStream()
            GameSaveTransfer.export(app, game, zip)

            // Người chơi chơi tiếp → save hiện tại thành bản khác (3).
            ram.writeBytes(ByteArray(8192) { 3 })
            val plan = GameSaveTransfer.prepare(app, game, "save.zip", zip.toByteArray().inputStream())
            val imported = GameSaveTransfer.commit(app, plan)

            assertTrue("Save trên đĩa phải là bản vừa nhập", ram.readBytes().all { it == 8.toByte() })
            assertTrue("Phải tạo bản sao lưu", imported.backup.isFile)
            val backup = imported.backup.inputStream().use { SaveFormats.archive(it) }
            assertTrue(
                "Bản sao lưu phải giữ save TRƯỚC khi ghi đè (byte 3), không phải bản mới",
                backup.getValue("ram.srm").all { it == 3.toByte() },
            )
        } finally {
            ram.delete()
            t.states.values.forEach { it.delete() }
            dir.deleteRecursively()
            backupRoot.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    /** Tệp không phải save → lỗi có loại (câu tiếng Việt), không ghi gì, không văng app. */
    @Test fun importingAFileThatIsNotASaveFailsWithATypedMessageAndWritesNothing() {
        val dir = Files.createTempDirectory("save-bad").toFile()
        val game = fixture(dir)
        val t = GameSaveTransfer.target(app, game)
        val ram = t.ram!!
        try {
            ram.parentFile!!.mkdirs()
            ram.writeBytes(ByteArray(8192) { 5 })

            val error = assertThrows(SaveTransferException::class.java) {
                GameSaveTransfer.prepare(app, game, "khong-phai-save.txt", "day khong phai save".byteInputStream())
            }
            assertTrue("Lỗi phải có câu tiếng Việt cho người dùng: ${error.message}",
                error.message.orEmpty().isNotBlank())
            assertTrue("Không được ghi đè save khi tệp sai", ram.readBytes().all { it == 5.toByte() })
        } finally {
            ram.delete()
            t.states.values.forEach { it.delete() }
            dir.deleteRecursively()
        }
    }

    /** Nút "Chia sẻ save" xuất đúng những mục đang liệt kê (cùng một nguồn save). */
    @Test fun shareExportContainsEveryListedSave() {
        val dir = Files.createTempDirectory("save-share").toFile()
        val game = fixture(dir)
        val t = GameSaveTransfer.target(app, game)
        val ram = t.ram!!
        val state = t.states.getValue("state/slot1.state")
        try {
            ram.parentFile!!.mkdirs(); ram.writeBytes(ByteArray(8192) { 2 })
            state.parentFile!!.mkdirs(); state.writeBytes(ByteArray(64) { 3 })
            val saves = GameSaveCatalog.list(app, game)
            assertEquals(2, saves.size)

            val zip = ByteArrayOutputStream()
            assertEquals(2, GameSaveTransfer.export(app, game, zip))
            val archive = SaveFormats.archive(zip.toByteArray().inputStream())
            assertTrue("Gói chia sẻ phải có save RAM", archive.containsKey("ram.srm"))
            assertTrue("Gói chia sẻ phải có state", archive.containsKey("state/slot1.state"))
        } finally {
            ram.delete()
            t.states.values.forEach { it.delete() }
            dir.deleteRecursively()
        }
    }

    /** Nhãn state đọc được; mới ghi xếp trước. */
    @Test fun entriesAreSortedNewestFirstAndLabelsAreVietnamese() {
        val dir = Files.createTempDirectory("save-sort").toFile()
        try {
            val a = File(dir, "a.srm").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }
            val b = File(dir, "b.state").apply { writeBytes(ByteArray(20)) }
            a.setLastModified(1_000)
            b.setLastModified(2_000)
            val entries: List<GameSaveEntry> = GameSaveCatalog.entries(mapOf("ram.srm" to a, "state/auto.state" to b))
            assertEquals(listOf("State: tự động", "Save trong game (SRAM)"), entries.map { it.name })
            assertEquals(20L, entries[0].size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
