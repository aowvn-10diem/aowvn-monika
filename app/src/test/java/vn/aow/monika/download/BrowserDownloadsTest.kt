package vn.aow.monika.download

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.AppGraph
import vn.aow.monika.Prefs
import vn.aow.monika.ui.TestApp
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * V193 G2: chốt cuối của luồng tải. URL Pixeldrain không có xác nhận thủ công thì
 * BrowserDownloads không được phát request nào; có xác nhận mới đúng 1 request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class BrowserDownloadsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val calls = AtomicInteger()
    private val firstCall = CountDownLatch(1)

    @Before fun init() {
        AppGraph.init(app)
        calls.set(0)
    }

    private fun downloads() = BrowserDownloads(
        app,
        OkHttpClient.Builder().addInterceptor { _ ->
            calls.incrementAndGet()
            firstCall.countDown()
            throw IOException("dừng test — không tải thật")
        }.build(),
        Prefs(app),
    )

    @Test fun pixeldrainThieuXacNhanThiKhongTai() {
        val job = downloads().start("https://pixeldrain.com/api/file/abc123?download", null, null, null, null)
        assertEquals("thiếu confirmed: job phải báo lỗi, không chạy", DlState.FAILED, job.state)
        assertEquals("không được có request nào", 0L, calls.get().toLong())
    }

    /** V193: host có dấu chấm cuối vẫn phải vào chốt chặn. */
    @Test fun pixeldrainDauChamThieuXacNhanCungKhongTai() {
        val job = downloads().start("https://pixeldrain.com./api/file/abc123?download", null, null, null, null)
        assertEquals(DlState.FAILED, job.state)
        assertEquals("không được có request nào", 0L, calls.get().toLong())
    }

    @Test fun pixeldrainCoXacNhanThiGoiDungMotLan() {
        downloads().start("https://pixeldrain.com/api/file/abc123?download", null, null, null, null, confirmed = true, pixeldrainConfirmed = true)
        assertTrue("phải phát request sau khi xác nhận", firstCall.await(5, TimeUnit.SECONDS))
        assertEquals("đúng 1 request", 1L, calls.get().toLong())
    }

    /** Link thường đã xác nhận chung nhưng 302 sang Pixeldrain: không được gọi Pixeldrain khi chưa cảnh báo riêng. */
    @Test fun redirectSangPixeldrainThieuDongYCanhBaoKhongGoiPixeldrain() {
        val hosts = java.util.Collections.synchronizedList(mutableListOf<String>())
        val hit = CountDownLatch(1)
        val done = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val host = chain.request().url.host
            hosts += host
            hit.countDown()
            if (host == "example.test") {
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Found")
                    .header("Location", "https://pixeldrain.com/api/file/abc123?download")
                    .body("".toResponseBody("text/plain".toMediaType())).build()
            } else {
                throw IOException("không được gọi $host")
            }
        }.build()
        val manager = BrowserDownloads(app, client, Prefs(app))
        manager.onJobFinished = { done.countDown() }
        val job = manager.start("https://example.test/start", null, null, null, null, confirmed = true, pixeldrainConfirmed = false)
        assertTrue(hit.await(5, TimeUnit.SECONDS))
        assertTrue("job phải kết thúc", done.await(5, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DlState.FAILED, job.state)
        assertTrue("phải hiện cảnh báo Pixeldrain", job.error.orEmpty().contains("Pixeldrain"))
        assertEquals("không có request Pixeldrain", 0, hosts.count { it == "pixeldrain.com" })
        assertEquals(1, hosts.size)
    }
}
