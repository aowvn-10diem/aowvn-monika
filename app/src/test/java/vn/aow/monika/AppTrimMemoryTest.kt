package vn.aow.monika

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.browser.AdBlock
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.feed.FeedRepository
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * V202 PR4: khi app ra nền/thiếu RAM, bộ đệm tiến trình chính phải nhả được.
 * Kiểm phần đo được offline: cache bài viết có giới hạn + mất nội dung HTML,
 * bảng chặn nhả bảng băm, và AppGraph.trimMemory không ném lỗi khi bộ đệm rỗng.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AppTrimMemoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun feed(posts: Int = 70): FeedRepository {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val http = OkHttpClient()
        val dir = tmp.newFolder("feed")
        val repo = FeedRepository(http, ConfigRepository(ctx, http), dir)
        val list = (1..posts).joinToString(",") { i ->
            """{"id":"p$i","title":"Bài $i","published":"2026-11-01","url":"https://aow.vn/p$i","labels":[],"thumbnail":null,"contentHtml":"<p>nội dung $i</p>"}"""
        }
        val name = "feed-" + "hom".hashCode().toUInt().toString(16) + ".json"
        File(dir, name).writeText("[$list]")
        return repo
    }

    @Test fun trimMemoryClearsHtmlAndBoundsPostCache() {
        val repo = feed()
        assertEquals(70, repo.cachedList("hom")!!.size)
        assertEquals("<p>nội dung 70</p>", repo.cached("p70")!!.contentHtml)

        repo.trimMemory()

        assertTrue(repo.cachedList("hom")!!.all { it.contentHtml.isEmpty() })
        // LruCache(60): giữ bài mới, bài đầu bị đẩy ra.
        assertNotNull(repo.cached("p70"))
        assertNull(repo.cached("p1"))
    }

    @Test fun adBlockTrimReleasesTables() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val adblock = AdBlock(ctx, OkHttpClient())
        adblock.trimMemory()
        assertFalse(adblock.ready)
        assertEquals(0, adblock.size)
    }

    @Test fun appGraphTrimDoesNotThrowOnEmptyCaches() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        AppGraph.init(ctx.applicationContext as Application)
        AppGraph.trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        AppGraph.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE - 1)
    }
}
