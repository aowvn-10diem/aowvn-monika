package vn.aow.monika.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            assertTrue("Câu báo phải ghi vị trí bản sao lưu", outcome.message.contains("Bản sao lưu save"))

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
            assertTrue(outcome.message.contains("Chưa sao lưu được save"))
            assertEquals("Không được tạo bản sao lưu dở dang", 0, backups().listFiles().orEmpty().count { it !in known })
        } finally {
            game.dir.deleteRecursively()
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
