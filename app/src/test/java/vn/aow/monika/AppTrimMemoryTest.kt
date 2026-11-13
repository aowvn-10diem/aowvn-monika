package vn.aow.monika

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Kiểm phần đo được offline: cache bài viết có giới hạn + mất nội dung HTML (và đường đọc sau trim
 * phải tải lại), bảng chặn nhả bảng băm, và AppGraph.trimMemory không ném lỗi khi bộ đệm rỗng.
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

        // Danh sách vẫn còn tiêu đề/ảnh để mở lại tức thì, nhưng không còn HTML.
        assertTrue(repo.cachedList("hom")!!.all { it.contentHtml.isEmpty() })
        assertEquals("Bài 70", repo.cachedList("hom")!!.first { it.id == "p70" }.title)
        // V202 PR4: cache bài bị xoá hẳn (không giữ bản rỗng) → không còn cache hit rỗng cho fetchPost.
        assertNull(repo.cached("p70"))
        assertNull(repo.cached("p1"))
    }

    /** V202 PR4: sau trim, đường đọc duy nhất của màn bài (PostScreen → fetchPost) phải tải lại, không trả bản rỗng. */
    @Test fun fetchPostReloadsAfterTrim() = runBlocking {
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            attempts++
            val body = """
                {"entry":{
                  "id":{"${'$'}t":"tag:blogger.com,1999:blog-1.post-p1"},
                  "title":{"${'$'}t":"Bài 1"},
                  "published":{"${'$'}t":"2026-11-01"},
                  "link":[{"rel":"alternate","href":"https://aow.vn/p1"}],
                  "content":{"${'$'}t":"<p>nội dung tải lại</p>"}
                }}
            """.trimIndent()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = FeedRepository(client, ConfigRepository(ctx, client), tmp.newFolder("feed-reload"))

        assertEquals("<p>nội dung tải lại</p>", repo.fetchPost("p1").contentHtml)
        repo.trimMemory()
        assertNull(repo.cached("p1"))
        assertEquals("<p>nội dung tải lại</p>", repo.fetchPost("p1").contentHtml)
        assertEquals(2, attempts)
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
