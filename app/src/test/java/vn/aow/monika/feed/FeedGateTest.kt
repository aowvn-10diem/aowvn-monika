package vn.aow.monika.feed

import android.app.Application
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.Prefs
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.ui.TestApp

/**
 * V193 G1: bài viết mặc định tắt — tắt thì không có request feed nào; bật mới gọi; tắt lại dừng.
 * Test đếm request qua OkHttp interceptor, không phụ thuộc mạng thật.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class FeedGateTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private var calls = 0

    private fun http() = OkHttpClient.Builder().addInterceptor { chain ->
        calls++
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("""{"feed":{"entry":[]}}""".toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Before fun resetPrefs() {
        Prefs(app).feedEnabled = null
        calls = 0
    }

    @Test fun configMacDinhTatBaiViet() {
        // Trường mới có mặc định false ngay cả khi config thiếu khóa.
        assertFalse(ConfigRepository.parse("{}").feed.enabled)
        // Asset đóng trong APK (từ config/monika-config.json) cũng đang tắt.
        assertFalse(ConfigRepository(app, OkHttpClient()).current.feed.enabled)
    }

    @Test fun tatThiKhongGoiMangBatMoiGoiTatLaiDung() {
        var enabled = false
        val repo = FeedRepository(http(), ConfigRepository(app, http()), ctx = app) { enabled }

        assertThrowsDisabled { repo.fetchList("v193") }
        assertEquals("tắt: không request nào", 0, calls)

        enabled = true
        assertTrue(runBlocking { repo.fetchList("v193") }.isEmpty())
        assertEquals("bật: gọi đúng 1 lần", 1, calls)

        enabled = false
        assertThrowsDisabled { repo.fetchList("v193-b") }
        assertEquals("tắt lại: không gọi thêm", 1, calls)
    }

    private fun assertThrowsDisabled(block: suspend () -> Unit) {
        val error = runCatching { runBlocking { block() } }.exceptionOrNull()
        assertTrue("phải chặn trước khi gọi mạng, gặp $error", error is IllegalStateException)
    }

    @Test fun luaChonNguoiDungThangMacDinh() {
        val p = Prefs(app)
        assertNull("chưa chọn = theo config (đang tắt)", p.feedEnabled)
        p.feedEnabled = true
        assertTrue(Prefs(app).feedEnabled!!)
        p.feedEnabled = false
        assertFalse(Prefs(app).feedEnabled!!)
        p.feedEnabled = null
        assertNull(Prefs(app).feedEnabled)
    }
}
