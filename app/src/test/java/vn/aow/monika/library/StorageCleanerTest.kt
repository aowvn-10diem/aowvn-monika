package vn.aow.monika.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * Dọn bộ nhớ đệm: chỉ đụng thứ tải lại được; không bao giờ đụng game ghim, game user tự thêm, save.
 * Dữ liệu tự sinh trong thư mục tạm của Robolectric; tuổi tính tương đối với "bây giờ" bằng bước nhảy hàng ngày
 * nên không phụ thuộc đồng hồ chính xác.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class StorageCleanerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = AppGraph.prefs
    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    @Before fun cleanSlate() {
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        File(context.filesDir, "cores").deleteRecursively()
        GameStorage.games(context).listFiles().orEmpty().forEach { it.deleteRecursively() }
        GameStorage.downloads(context).listFiles().orEmpty().forEach { it.deleteRecursively() }
        prefs.cacheLimitBytes = -1
        prefs.autoDeleteGameDays = 0
        prefs.autoDeleteCoreDays = 0
        prefs.autoDeleteMediaDays = 0
        prefs.pinnedGames = emptySet()
    }

    private fun bytes(file: File, size: Int, ageDays: Int): File {
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(size) { 7 })
        file.setLastModified(now - ageDays * day)
        return file
    }

    private fun media(name: String, size: Int, ageDays: Int) = bytes(File(context.cacheDir, name), size, ageDays)

    private fun core(name: String, size: Int, ageDays: Int): File {
        val dir = File(context.filesDir, "cores/$name").apply { mkdirs() }
        bytes(File(dir, "core.so"), size, ageDays)
        dir.setLastModified(now - ageDays * day)
        return dir
    }

    /** Game tải từ bài viết (có link bài => nằm trong bộ đệm). */
    private fun game(name: String, size: Int, ageDays: Int, postUrl: String? = "https://aow.vn/p/$name", title: String = name): File {
        val dir = File(GameStorage.games(context), name).apply { mkdirs() }
        bytes(File(dir, "data.bin"), size, ageDays)
        GameMeta.write(dir, GameMeta(title = title, postUrl = postUrl))
        dir.setLastModified(now - ageDays * day)
        return dir
    }

    private fun payload(dir: File) = File(dir, "data.bin")

    @Test fun usageGroupsMediaCoresAndOnlyDownloadedUnpinnedGames() {
        media("a.jpg", 100, 1)
        core("mgba", 300, 1)
        game("pinned", 700, 1).also { prefs.pinnedGames = setOf(it.path) }
        game("user-added", 900, 1, postUrl = null)
        game("downloaded", 500, 1)

        val usage = StorageCleaner.usage(context)

        assertEquals(500L, usage.games) // bỏ .monika.json, bỏ game ghim, bỏ game tự thêm
        assertEquals(300L, usage.cores)
        assertEquals(100L, usage.media)
        assertEquals(900L, usage.total)
    }

    @Test fun ageRulesDeleteOldItemsAndKeepGameMetaForRedownload() {
        prefs.autoDeleteMediaDays = 7
        prefs.autoDeleteCoreDays = 30
        prefs.autoDeleteGameDays = 10
        val oldMedia = media("old.jpg", 100, 10)
        val newMedia = media("new.jpg", 50, 1)
        val oldCore = core("old-core", 300, 40)
        val newCore = core("new-core", 200, 5)
        val oldGame = game("old-game", 500, 20, title = "Game cũ")
        val newGame = game("new-game", 400, 2, title = "Game mới")

        val report = StorageCleaner.clean(context)

        assertFalse(oldMedia.exists())
        assertTrue(newMedia.exists())
        assertFalse(oldCore.exists())
        assertTrue(newCore.exists())
        assertFalse(payload(oldGame).exists())
        assertTrue(payload(newGame).exists())
        // Game bị dọn chỉ còn .monika.json để Thư viện hiện "Tải lại".
        assertEquals(listOf(".monika.json"), oldGame.listFiles().orEmpty().map { it.name })
        assertEquals("Game cũ", GameMeta.read(oldGame)?.title)
        assertEquals(listOf("Game cũ"), report.removedGames)
        assertEquals(100L + 300L + 500L, report.freed)
    }

    @Test fun zeroDaysMeansNeverDeleteByAge() {
        val m = media("old.jpg", 100, 400)
        val c = core("old-core", 100, 400)
        val g = game("old-game", 100, 400)

        val report = StorageCleaner.clean(context)

        assertTrue(m.exists() && c.exists() && payload(g).exists())
        assertEquals(0L, report.freed)
        assertTrue(report.removedGames.isEmpty())
    }

    @Test fun recentlyPlayedGameSurvivesEvenWhenFolderIsOld() {
        prefs.autoDeleteGameDays = 10
        val dir = game("played", 500, 60)
        prefs.markPlayed(dir.path) // lần chơi gần nhất = bây giờ

        val report = StorageCleaner.clean(context)

        assertTrue(payload(dir).exists())
        assertTrue(report.removedGames.isEmpty())
    }

    @Test fun pinnedAndUserAddedGamesAreNeverCleaned() {
        prefs.autoDeleteGameDays = 1
        prefs.cacheLimitBytes = 1
        val pinned = game("pinned", 500, 90)
        prefs.pinnedGames = setOf(pinned.path)
        val userAdded = game("user-added", 500, 90, postUrl = null)

        val report = StorageCleaner.clean(context)

        assertTrue(payload(pinned).exists())
        assertTrue(payload(userAdded).exists())
        assertTrue(report.removedGames.isEmpty())
    }

    @Test fun overLimitDeletesMediaThenCoresThenOldestGameUntilUnderLimit() {
        val m = media("m.jpg", 200, 1)
        val c = core("c", 300, 1)
        val olderGame = game("older", 400, 20, title = "Cũ hơn")
        val newerGame = game("newer", 400, 5, title = "Mới hơn")
        prefs.cacheLimitBytes = 600

        val report = StorageCleaner.clean(context)

        assertFalse(m.exists())
        assertFalse(c.exists())
        assertFalse(payload(olderGame).exists())
        assertTrue(payload(newerGame).exists())
        assertEquals(listOf("Cũ hơn"), report.removedGames)
        assertEquals(200L + 300L + 400L, report.freed)
    }

    @Test fun underLimitDeletesNothing() {
        val m = media("m.jpg", 200, 1)
        val g = game("g", 400, 1)
        prefs.cacheLimitBytes = 10_000

        val report = StorageCleaner.clean(context)

        assertTrue(m.exists() && payload(g).exists())
        assertEquals(0L, report.freed)
    }

    @Test fun protectedGameSurvivesLimitCleaning() {
        val protected = game("just-downloaded", 400, 20)
        val other = game("other", 400, 30)
        prefs.cacheLimitBytes = 100

        val report = StorageCleaner.clean(context, protect = protected)

        assertTrue(payload(protected).exists())
        assertFalse(payload(other).exists())
        assertEquals(listOf("other"), report.removedGames)
    }

    @Test fun mediaOnlyClearsMediaRegardlessOfAgeAndLimitButNotCoresOrGames() {
        prefs.autoDeleteCoreDays = 1
        prefs.autoDeleteGameDays = 1
        prefs.cacheLimitBytes = 1
        val fresh = media("fresh.jpg", 80, 0)
        val c = core("old-core", 300, 90)
        val g = game("old-game", 500, 90)

        val report = StorageCleaner.clean(context, mediaOnly = true)

        assertFalse(fresh.exists())
        assertTrue(c.exists())
        assertTrue(payload(g).exists())
        assertEquals(80L, report.freed)
        assertTrue(report.removedGames.isEmpty())
    }

    @Test fun leftoverDownloadsOlderThanADayAreRemovedAndCounted() {
        val stale = bytes(File(GameStorage.downloads(context), "stale.part"), 250, 3)
        val fresh = bytes(File(GameStorage.downloads(context), "fresh.part"), 120, 0)

        val report = StorageCleaner.clean(context)

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertEquals(250L, report.freed)
    }

    @Test fun alreadyCleanedGameIsSkippedAndNeverReportedTwice() {
        val dir = game("cleaned", 500, 20)
        prefs.autoDeleteGameDays = 10
        StorageCleaner.clean(context)
        assertNotNull(GameMeta.read(dir))
        assertEquals(0L, StorageCleaner.usage(context).games)

        val second = StorageCleaner.clean(context)

        assertTrue(second.removedGames.isEmpty())
        assertEquals(0L, second.freed)
        assertTrue(File(dir, ".monika.json").isFile)
    }
}
