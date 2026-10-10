package vn.aow.monika.cheats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.IOException

/** Tải kho cheat: thử raw GitHub rồi jsDelivr, lưu bộ nhớ đệm, đọc tệp .cht. Máy chủ giả trong bộ nhớ, không mạng thật. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CheatDbFetchTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val candidate = CheatDb.Candidate("Nintendo - Game Boy Advance", "Pokemon Emerald (USA).cht")
    private val cht = """
        cheats = 2

        cheat0_desc = "Vô hạn tiền"
        cheat0_code = "94000130 00000001"
        cheat0_enable = false

        cheat1_desc = "Bất tử"
        cheat1_code = "9400010C 0000FFFF"
        cheat1_enable = true
    """.trimIndent()

    /** Trả lời theo [reply]; mọi yêu cầu được ghi vào [seen]. */
    private class Fake(val reply: (Request) -> Pair<Int, String>) {
        val seen = ArrayList<Request>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            seen += req
            val (code, body) = reply(req)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    @Test fun supportsAndReposFollowSystemMap() {
        val db = CheatDb(app, OkHttpClient())
        assertTrue(db.supports("gbc"))
        assertEquals(listOf("Nintendo - Game Boy Color", "Nintendo - Game Boy"), db.repos("gbc"))
        assertFalse(db.supports("xyz"))
        assertEquals(emptyList<String>(), db.repos("xyz"))
        assertEquals(emptyList<CheatDb.Candidate>(), db.match("xyz", listOf("Pokemon Emerald")))
    }

    @Test fun fetchDownloadsOnceThenReadsFromCache() {
        val first = Fake { _ -> (200 to cht) }
        val got = CheatDb(app, first.client).fetch(candidate)!!
        assertEquals(listOf("Vô hạn tiền", "Bất tử"), got.map { it.name })
        assertEquals(listOf(false, true), got.map { it.enabled })
        assertEquals(listOf("auto", "auto"), got.map { it.source })
        assertEquals("raw.githubusercontent.com", first.seen.single().url.host)
        // Lần sau không có mạng vẫn đọc được từ bộ nhớ đệm.
        val offline = Fake { throw IOException("không có mạng") }
        assertEquals(got, CheatDb(app, offline.client).fetch(candidate))
        assertTrue(offline.seen.isEmpty())
    }

    @Test fun rawFailureFallsBackToJsDelivr() {
        val fake = Fake { req ->
            if (req.url.host == "raw.githubusercontent.com") throw IOException("raw bị chặn")
            (200 to cht)
        }
        val got = CheatDb(app, fake.client).fetch(candidate)
        assertEquals(2, got!!.size)
        assertEquals(listOf("raw.githubusercontent.com", "cdn.jsdelivr.net"), fake.seen.map { it.url.host })
    }

    @Test fun bothSourcesFailingGivesNullAndCachesNothing() {
        val failing = Fake { _ -> (404 to "") }
        assertNull(CheatDb(app, failing.client).fetch(candidate))
        assertEquals(2, failing.seen.size)
        // Lỗi không được lưu: lần sau vẫn tải lại từ mạng.
        val retry = Fake { _ -> (200 to cht) }
        assertEquals(2, CheatDb(app, retry.client).fetch(candidate)!!.size)
        assertEquals(1, retry.seen.size)
    }

    @Test fun chtParseDropsEmptyCodesNamesBlankAndReadsEnableCaseInsensitively() {
        val text = """
            cheat2_code = "AAAAAAAA BBBBBBBB"
            cheat0_desc = "   "
            cheat0_code = "11111111 22222222"
            cheat1_desc = "Không mã"
            cheat1_enable = true
            cheat2_enable = TRUE
            note = bỏ qua
        """.trimIndent()
        assertEquals(
            listOf(
                CheatEntry("Mã 1", "11111111 22222222", enabled = false, source = "user"),
                CheatEntry("Mã 3", "AAAAAAAA BBBBBBBB", enabled = true, source = "user"),
            ),
            ChtFormat.parse(text),
        )
    }
}
