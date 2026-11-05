package vn.aow.monika.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.TestApp
import java.io.File
import java.util.zip.ZipFile

/**
 * V139 — xoá game phải sao lưu save trước (A54), không xoá đệ quy thư mục lạ,
 * và dừng khi không sao lưu được.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class GameDeleteBackupTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()
    private fun library() = GameLibrary(context(), AppGraph.config)

    private fun gameFolder(name: String): File = File(GameStorage.games(context()), name).apply { mkdirs() }

    private fun backups(): File = File(context().filesDir, GameSaveBackup.DIR)

    private fun gameWithSave(name: String): Pair<Game, File> {
        val dir = gameFolder(name)
        val rom = File(dir, "fixture.gba").apply { writeText("rom") }
        val save = File(dir, "save/tien-do.sav").apply {
            parentFile?.mkdirs(); writeText("du lieu save quan trong")
        }
        return Game(dir, name, AppGraph.config.current.system("gba"), rom) to save
    }

    @Test fun deletingAGameBacksUpItsSavesAndTheBackupRestoresThem() {
        val (game, save) = gameWithSave("v139-save-game")
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val outcome = library().deleteWithBackup(game)

            assertTrue("Phải xoá được game", outcome.deleted)
            assertFalse("Thư mục game đã xoá", game.dir.exists())
            assertFalse("Tệp save gốc nằm trong thư mục game nên đã xoá", save.exists())
            val backup = outcome.backup
            assertNotNull("Phải có bản sao lưu save", backup)
            assertTrue(backup!!.isFile)
            assertTrue(backup.name.startsWith("xoa-v139-save-game"))
            assertTrue("Câu báo phải ghi vị trí bản sao lưu", outcome.message.contains("Bản sao lưu thư mục game"))

            // Bản sao lưu giữ đúng nội dung và khôi phục được vào thư mục mới.
            val restore = File(context().cacheDir, "v139-khoi-phuc").apply { deleteRecursively(); mkdirs() }
            ZipFile(backup).use { zip ->
                val entry = zip.getEntry("game/save/tien-do.sav")
                assertNotNull("Bản sao lưu phải có tệp save", entry)
                val out = File(restore, "save/tien-do.sav").apply { parentFile?.mkdirs() }
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                assertNotNull("Bản sao lưu phải có tệp mô tả", zip.getEntry("monika-xoa-game.txt"))
            }
            assertEquals("du lieu save quan trong", File(restore, "save/tien-do.sav").readText())
            restore.deleteRecursively()
        } finally {
            game.dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun refusingWhenBackupFailsKeepsTheGameAndItsSave() {
        val (game, save) = gameWithSave("v139-khong-sao-luu")
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val outcome = library().deleteWithBackup(game) { error("hết chỗ") }

            assertFalse("Chưa sao lưu được thì không được xoá", outcome.deleted)
            assertTrue("Thư mục game phải còn", game.dir.exists())
            assertTrue("Save phải còn", save.exists())
            assertTrue(outcome.message.contains("Chưa sao lưu được thư mục game"))
            assertEquals("Không được tạo bản sao lưu dở dang", 0, backups().listFiles().orEmpty().count { it !in known })
        } finally {
            game.dir.deleteRecursively()
        }
    }

    @Test fun savesWithUnknownExtensionsAreBackedUpBeforeDeleting() {
        val dir = gameFolder("v139-save-la")
        val rom = File(dir, "fixture.gba").apply { writeText("rom") }
        // Đuôi/tên lạ và tệp không đuôi: whitelist cũ bỏ qua hết nhưng xoá thư mục là mất luôn.
        val bin = File(dir, "save.bin").apply { writeText("save dang bin") }
        val dat = File(dir, "profile/slot1.dat").apply { parentFile?.mkdirs(); writeText("save dang dat") }
        val bare = File(dir, "progress").apply { writeText("save khong duoi") }
        val game = Game(dir, "v139-save-la", AppGraph.config.current.system("gba"), rom)
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val outcome = library().deleteWithBackup(game)

            assertTrue("Phải xoá được game", outcome.deleted)
            assertFalse(dir.exists())
            val zipFile = outcome.backup
            assertNotNull("Phải có bản sao lưu", zipFile)
            ZipFile(zipFile!!).use { zip ->
                assertNotNull("save.bin phải có trong zip", zip.getEntry("game/save.bin"))
                assertNotNull("profile/slot1.dat phải có trong zip", zip.getEntry("game/profile/slot1.dat"))
                assertNotNull("progress (không đuôi) phải có trong zip", zip.getEntry("game/progress"))
                assertNotNull("ROM cũng phải có (backup toàn thư mục)", zip.getEntry("game/fixture.gba"))
            }
            assertFalse(bin.exists()); assertFalse(dat.exists()); assertFalse(bare.exists())
        } finally {
            dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun deepAndManyFilesAreAllInTheBackup() {
        val dir = gameFolder("v139-save-sau")
        val rom = File(dir, "fixture.gba").apply { writeText("rom") }
        val deep = File(dir, "a/b/c/d/e/f/sau-tang.sav").apply { parentFile?.mkdirs(); writeText("save sau") }
        // 300 tệp: bản cũ cắt âm thầm ở 256.
        (1..300).forEach { i -> File(dir, "save/s$i.srm").apply { parentFile?.mkdirs(); writeText("s$i") } }
        val game = Game(dir, "v139-save-sau", AppGraph.config.current.system("gba"), rom)
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val scan = GameSaveBackup.scan(context(), dir)
            assertTrue("Quét phải trọn vẹn", scan.complete)
            assertTrue("Phải thấy save sâu 7 tầng", scan.files.any { it == deep })
            assertEquals("Không được cắt bớt tệp nào", 302, scan.files.size) // 301 save + ROM

            val outcome = library().deleteWithBackup(game)
            assertTrue(outcome.deleted)
            val zipFile = outcome.backup
            assertNotNull("Phải có bản sao lưu", zipFile)
            ZipFile(zipFile!!).use { zip ->
                assertNotNull("Save sâu phải có trong zip", zip.getEntry("game/a/b/c/d/e/f/sau-tang.sav"))
                assertNotNull("Save thứ 300 phải có trong zip", zip.getEntry("game/save/s300.srm"))
                assertEquals("301 save + ROM + tệp mô tả", 303, zip.size())
            }
        } finally {
            dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    /**
     * Luna 4 + Luna 5 (#434): quét thư mục chưa trọn thì **không phát ZIP một phần** — không tạo tệp,
     * không trả đường dẫn để chia sẻ, và **không xoá** game.
     */
    @Test fun incompleteScanEmitsNoArchiveAndRefusesToDelete() {
        val (game, save) = gameWithSave("v141-quet-thieu")
        val rom = game.entry!!
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val warning = "Bỏ qua 1 liên kết tượng trưng (không đi theo để tránh lặp vô hạn)."
            val outcome = library().deleteWithBackup(game) { g ->
                // Quét thật có file đọc được nhưng thiếu mục → complete = false.
                GameSaveBackup.backupFolder(context(), g, scanFolder = { _, _ ->
                    GameFolderScan(listOf(rom), listOf(warning), complete = false)
                })
            }

            assertFalse("Quét thiếu thì không được xoá", outcome.deleted)
            assertNull("Không được trả tệp zip để chia sẻ", outcome.backup)
            assertTrue(
                "Phải báo rõ không tạo tệp: ${outcome.message}",
                outcome.message.contains("Bản sao lưu chưa đầy đủ nên không tạo tệp"),
            )
            assertTrue("Phải nêu lý do quét thiếu", outcome.message.contains("liên kết tượng trưng"))
            assertTrue("Thư mục game phải còn", game.dir.exists())
            assertTrue("Save phải còn", save.exists())
            assertTrue(
                "Không được để lại zip nào khi quét thiếu",
                backups().listFiles().orEmpty().filter { it !in known }.isEmpty(),
            )
        } finally {
            game.dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    /** Quét đủ → đúng 1 zip và zip đó chia sẻ được (ACTION_SEND trỏ đúng tệp). */
    @Test fun completeScanEmitsOneArchiveThatCanBeShared() {
        val (game, _) = gameWithSave("v141-du-zip")
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val outcome = library().deleteWithBackup(game)
            assertTrue("Phải xoá được", outcome.deleted)
            val created = backups().listFiles().orEmpty().filter { it !in known }
            assertEquals("Đúng 1 bản sao lưu", 1, created.size)
            assertEquals(created.single(), outcome.backup)
            val uri = android.net.Uri.parse("content://vn.aow.monika.files/app_internal/save-backups/x.zip")
            val intent = GameSaveBackup.shareIntent(context(), outcome.backup!!) { uri }
            assertEquals(android.content.Intent.ACTION_SEND, intent.action)
            assertEquals(uri, intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM))
        } finally {
            game.dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun backupsInTheSameSecondDoNotOverwriteEachOther() {        val (game, _) = gameWithSave("v139-ghi-de")
        val known = backups().listFiles().orEmpty().toSet()
        try {
            // Gọi liên tiếp (cùng giây) — tên cũ chỉ có độ phân giải giây nên sẽ đè nhau.
            val first = GameSaveBackup.backupFolder(context(), game).file
            val second = GameSaveBackup.backupFolder(context(), game).file

            assertNotNull("Lần 1 phải có zip", first)
            assertNotNull("Lần 2 phải có zip", second)
            assertNotEquals("Hai lần sao lưu phải ra hai tệp khác nhau", first, second)
            assertTrue("Zip lần 1 phải còn", first!!.isFile)
            assertTrue("Zip lần 2 phải còn", second!!.isFile)
            assertTrue(
                "Tên phải có mili-giây và hậu tố 4 hex",
                first.name.matches(Regex("""xoa-v139-ghi-de-\d{8}-\d{6}-\d{3}-[0-9a-f]{4}\.zip""")),
            )
            ZipFile(first).use { zip -> assertNotNull("Zip lần 1 đọc được", zip.getEntry("game/fixture.gba")) }
            ZipFile(second).use { zip -> assertNotNull("Zip lần 2 đọc được", zip.getEntry("game/fixture.gba")) }
        } finally {
            game.dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun backupsForGamesWithTheSameNameInDifferentFoldersDoNotOverwrite() {
        val dirA = gameFolder("v139-ten-a")
        val dirB = gameFolder("v139-ten-b")
        val romA = File(dirA, "a.gba").apply { writeText("a") }
        val romB = File(dirB, "b.gba").apply { writeText("b") }
        val system = AppGraph.config.current.system("gba")
        val gameA = Game(dirA, "Cùng tên", system, romA)
        val gameB = Game(dirB, "Cùng tên", system, romB)
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val first = GameSaveBackup.backupFolder(context(), gameA).file
            val second = GameSaveBackup.backupFolder(context(), gameB).file

            assertNotNull(first)
            assertNotNull(second)
            assertNotEquals("Hai game cùng tên không được ghi đè bản sao lưu của nhau", first, second)
            ZipFile(first!!).use { zip -> assertNotNull("Zip game A đọc được", zip.getEntry("game/a.gba")) }
            ZipFile(second!!).use { zip -> assertNotNull("Zip game B đọc được", zip.getEntry("game/b.gba")) }
        } finally {
            dirA.deleteRecursively(); dirB.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun backupNamesAreReservedAtomically() {
        val dir = backups().apply { mkdirs() }
        val known = dir.listFiles().orEmpty().toSet()
        try {
            // Giữ tên xong là tệp tồn tại ngay; kiểu `if (!exists())` sẽ trả cùng một tên 2 lần.
            val first = GameSaveBackup.createUniqueBackupFile(context(), dir, "xoa-atomic")
            val second = GameSaveBackup.createUniqueBackupFile(context(), dir, "xoa-atomic")
            assertNotEquals("Hai lần giữ tên phải khác nhau", first, second)
            assertTrue("Tệp giữ tên phải tồn tại ngay", first.isFile)
            assertTrue("Tệp giữ tên phải tồn tại ngay", second.isFile)
        } finally {
            dir.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun concurrentBackupsDoNotOverwriteEachOther() {
        val (game, _) = gameWithSave("v139-song-song")
        val known = backups().listFiles().orEmpty().toSet()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val futures = (1..2).map {
                pool.submit<java.io.File?> {
                    barrier.await(30, java.util.concurrent.TimeUnit.SECONDS) // cả hai vào cùng lúc
                    GameSaveBackup.backupFolder(context(), game).file
                }
            }
            val files = futures.map { it.get(120, java.util.concurrent.TimeUnit.SECONDS) }
            assertNotNull("Luồng 1 phải có zip", files[0])
            assertNotNull("Luồng 2 phải có zip", files[1])
            assertNotEquals("Hai luồng không được dùng chung một zip", files[0], files[1])
            for (file in files) {
                assertTrue("Zip phải còn trên đĩa", file!!.isFile)
                assertTrue("Zip phải có nội dung (không bị ghi đè/truncate)", file.length() > 0)
                ZipFile(file).use { zip -> assertNotNull("Zip đọc được", zip.getEntry("game/fixture.gba")) }
            }
        } finally {
            pool.shutdownNow()
            game.dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun deleteIsRefusedWhenTheScanIsNotComplete() {
        val (game, save) = gameWithSave("v139-quet-thieu")
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val warning = "Bỏ qua 1 liên kết tượng trưng (không đi theo để tránh lặp vô hạn)."
            val outcome = library().deleteWithBackup(game) {
                GameFolderBackup(null, listOf(warning), complete = false)
            }
            assertFalse("Chưa chứng minh sao lưu đủ thì không được xoá", outcome.deleted)
            assertTrue("Thư mục game phải còn", game.dir.exists())
            assertTrue("Save phải còn", save.exists())
            assertNull("Không được trả tệp để chia sẻ", outcome.backup)
            assertTrue(outcome.message.contains("Bản sao lưu chưa đầy đủ nên không tạo tệp"))
            assertTrue("Phải nêu lý do", outcome.message.contains("liên kết tượng trưng"))
            assertTrue("Không được có zip nào", backups().listFiles().orEmpty().filter { it !in known }.isEmpty())
        } finally {
            game.dir.deleteRecursively()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun backupShareIntentHandsTheZipToOtherApps() {
        val backup = File(backups(), "xoa-demo.zip").apply { parentFile?.mkdirs(); writeText("zip") }
        val shared = android.net.Uri.parse("content://vn.aow.monika.files/app_internal/save-backups/xoa-demo.zip")
        try {
            val intent = GameSaveBackup.shareIntent(context(), backup) { shared }
            assertEquals(android.content.Intent.ACTION_SEND, intent.action)
            assertEquals("application/zip", intent.type)
            assertEquals(shared, intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM))
            assertTrue(
                "Phải cấp quyền đọc URI cho ứng dụng nhận",
                intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            )
            assertEquals(shared, intent.clipData?.getItemAt(0)?.uri)
        } finally {
            backup.delete()
        }
    }

    @Test fun deleteOnlyTouchesTheGameFolderAndKeepsUnknownFoldersAndOutsideSaves() {
        val (game, save) = gameWithSave("v139-xoa-game")
        // Thư mục lạ trong kho (không phải game) + save nằm ngoài thư mục game.
        val stranger = File(GameStorage.games(context()), "v139-kho-la").apply { mkdirs() }
        val strangerSave = File(stranger, "save/quan-trong.sav").apply {
            parentFile?.mkdirs(); writeText("save nguoi choi")
        }
        val outsideSave = File(context().filesDir, "saves/mgba/v139-xoa-game.srm").apply {
            parentFile?.mkdirs(); writeText("save grap")
        }
        val known = backups().listFiles().orEmpty().toSet()
        try {
            val outcome = library().deleteWithBackup(game)

            assertTrue(outcome.deleted)
            assertFalse(game.dir.exists())
            assertFalse(save.exists())
            assertTrue("Thư mục lạ phải còn nguyên", stranger.exists())
            assertTrue("Save trong thư mục lạ phải còn", strangerSave.exists())
            assertTrue("Save của giả lập ngoài thư mục game phải còn", outsideSave.exists())
        } finally {
            game.dir.deleteRecursively(); stranger.deleteRecursively(); outsideSave.delete()
            backups().listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }

    @Test fun neverDeletesFoldersOutsideTheManagedKho() {
        val nested = File(gameFolder("v139-kho-la"), "save").apply { mkdirs() }
        val nestedSave = File(nested, "quan-trong.sav").apply { writeText("x") }
        val outside = File(context().cacheDir, "v139-ngoai-kho").apply { mkdirs() }
        val outsideFile = File(outside, "du-lieu.bin").apply { writeText("x") }
        try {
            for (dir in listOf(nested, outside)) {
                val outcome = library().deleteWithBackup(Game(dir, "la", null, null))
                assertFalse("App không được xoá thư mục ngoài phạm vi quản lý: $dir", outcome.deleted)
                assertTrue("Thư mục phải còn nguyên: $dir", dir.exists())
                assertTrue(outcome.message.contains("không nằm trong kho game"))
            }
            assertTrue(nestedSave.exists())
            assertTrue(outsideFile.exists())
        } finally {
            File(GameStorage.games(context()), "v139-kho-la").deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun externalGameIsOnlyHiddenAndFilesStay() {
        val dir = File(context().cacheDir, "v139-game-ngoai").apply { mkdirs() }
        val rom = File(dir, "ngoai.gba").apply { writeText("rom") }
        val save = File(dir, "save/ngoai.sav").apply { parentFile?.mkdirs(); writeText("save") }
        try {
            val outcome = library().deleteWithBackup(Game(dir, "ngoai", AppGraph.config.current.system("gba"), rom, external = true))
            assertTrue(outcome.deleted)
            assertTrue("Game ngoài kho chỉ ẩn, không xoá tệp", rom.exists())
            assertTrue("Save của game ngoài kho phải còn", save.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
