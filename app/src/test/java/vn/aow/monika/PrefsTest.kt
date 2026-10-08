package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.feed.Post
import vn.aow.monika.ui.TestApp
import java.io.File

/** Cài đặt nhỏ trên máy: giá trị mặc định, lịch sử, bài đã xem, cộng giờ chơi, sự kiện từ tiến trình game. Không dùng đồng hồ giả. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class PrefsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = Prefs(app)

    private fun post(id: String, html: String = "<p>nội dung</p>") =
        Post(id, "Bài $id", "2026-10-08", "https://aow.vn/p/$id.html", listOf("rpg"), null, html)

    @Test fun defaultsWhenNothingSaved() {
        val p = prefs
        assertTrue(p.seenPostIds.isEmpty())
        assertEquals("", p.inboxSeen)
        assertTrue(p.searchHistory.isEmpty())
        assertEquals("lib", p.dlDest)
        assertNull(p.dlFolder)
        assertEquals("auto", p.motionMode)
        assertEquals("auto", p.emuPerf)
        assertTrue(p.preloadGame)
        assertTrue(p.autoSendCrash)
        assertEquals(2L * 1024 * 1024 * 1024, p.cacheLimitBytes)
        assertEquals(0, p.autoDeleteGameDays)
        assertEquals(30, p.autoDeleteCoreDays)
        assertEquals(7, p.autoDeleteMediaDays)
        assertEquals(1f, p.padScale, 0f)
        assertEquals(0.65f, p.padOpacity, 0.0001f)
        assertEquals(0f to 0f, p.padOffset("dpad"))
    }

    @Test fun searchHistoryNewestFirstIgnoresCaseAndShortQueries() {
        val p = prefs
        p.addSearch("Zelda")
        p.addSearch("  mario  ")
        p.addSearch("zelda")
        assertEquals(listOf("zelda", "mario"), p.searchHistory)
        p.addSearch("a")
        assertEquals(listOf("zelda", "mario"), p.searchHistory)
    }

    @Test fun searchHistoryKeepsTwelve() {
        val p = prefs
        repeat(15) { p.addSearch("q$it") }
        assertEquals(12, p.searchHistory.size)
        assertEquals("q14", p.searchHistory.first())
    }

    @Test fun recentPostsDropBodyDedupeByIdAndKeepTwelve() {
        val p = prefs
        p.addRecentPost(post("1"))
        p.addRecentPost(post("2"))
        p.addRecentPost(post("1"))
        assertEquals(listOf("1", "2"), p.recentPosts.map { it.id })
        assertTrue(p.recentPosts.all { it.contentHtml.isEmpty() })
        repeat(15) { p.addRecentPost(post("n$it")) }
        assertEquals(12, p.recentPosts.size)
        assertEquals("n14", p.recentPosts.first().id)
    }

    @Test fun playTimeIgnoresShortSessionsAndAccumulates() {
        val p = prefs
        p.addPlayTime("games/a", 2_000)
        assertEquals(0L, p.playTime("games/a"))
        p.addPlayTime("games/a", 5_000)
        p.addPlayTime("games/a", 7_000)
        assertEquals(12_000L, p.playTime("games/a"))
    }

    @Test fun markPlayedSessionTimeRulesUseInjectedClock() {
        val p = prefs
        var now = 1_000_000L
        p.nowMillis = { now } // đồng hồ giả: không phụ thuộc tốc độ CI
        p.markPlayed("games/a")
        p.markPlayed("games/a")
        assertEquals(2, p.playCount("games/a"))
        assertEquals(now, p.lastPlayed("games/a"))
        now += 5_000
        p.endSession() // phiên 5 giây, dưới 10 giây → không cộng giờ
        assertEquals(0L, p.playTime("games/a"))
        p.markPlayed("games/a")
        now += 15_000
        p.endSession() // phiên 15 giây → cộng đúng 15 giây
        assertEquals(15_000L, p.playTime("games/a"))
        p.markPlayed("games/b")
        p.cancelSession()
        p.endSession()
        assertEquals(0L, p.playTime("games/b"))
    }

    @Test fun gameEventsAreMergedThenFileCleared() {
        val p = prefs
        p.postGameEvent("play\tgames/a\t60000")
        p.postGameEvent("play\tgames/a\t2000") // dưới 3 giây → bỏ
        p.postGameEvent("khong-biet\tx")
        val f = File(app.filesDir, "game-events.log")
        assertTrue(f.isFile)
        p.mergeGameEvents()
        assertEquals(60_000L, p.playTime("games/a"))
        assertFalse(f.exists())
    }

    @Test fun padLayoutRoundTrips() {
        val p = prefs
        p.setPadOffset("dpad", 3f, -4f)
        assertEquals(3f to -4f, p.padOffset("dpad"))
        p.padScale = 1.2f
        assertEquals(1.2f, p.padScale, 0.0001f)
        p.padOpacity = 0.4f
        assertEquals(0.4f, p.padOpacity, 0.0001f)
    }

    @Test fun coreOverrideAndDisplayStyle() {
        val p = prefs
        assertNull(p.coreOverride("3ds"))
        p.setCoreOverride("3ds", "azahar")
        assertEquals("azahar", p.coreOverride("3ds"))
        p.setCoreOverride("3ds", null)
        assertNull(p.coreOverride("3ds"))
        assertNull(p.displayStyle("core1"))
        p.setDisplayStyle("core1", "lcd")
        assertEquals("lcd", p.displayStyle("core1"))
    }

    @Test fun downloadFlagAndMetaClearedTogether() {
        val p = prefs
        p.markToolDownload(7L)
        p.setDownloadMeta(7L, "{\"postId\":\"42\"}")
        assertTrue(p.isToolDownload(7L))
        assertEquals("{\"postId\":\"42\"}", p.downloadMeta(7L))
        assertFalse(p.isToolDownload(8L))
        p.clearDownload(7L)
        assertFalse(p.isToolDownload(7L))
        assertNull(p.downloadMeta(7L))
    }

    @Test fun seenIdsSubscriptionsAndDownloadFolderAreSaved() {
        val p = prefs
        p.seenPostIds = setOf("1", "2")
        p.subscribedLabels = setOf("rpg")
        p.dlDest = "folder"
        p.dlFolder = "content://tree/x"
        p.dlFolderLabel = "Game"
        p.inboxSeen = "2026-10-08"
        assertEquals(setOf("1", "2"), p.seenPostIds)
        assertEquals(setOf("rpg"), p.subscribedLabels)
        assertEquals("folder", p.dlDest)
        assertEquals("content://tree/x", p.dlFolder)
        assertEquals("Game", p.dlFolderLabel)
        assertEquals("2026-10-08", p.inboxSeen)
    }
}
