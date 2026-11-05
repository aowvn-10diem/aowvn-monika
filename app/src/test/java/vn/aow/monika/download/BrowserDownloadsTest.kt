package vn.aow.monika.download

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        downloads().start("https://pixeldrain.com/api/file/abc123?download", null, null, null, null, confirmed = true)
        assertTrue("phải phát request sau khi xác nhận", firstCall.await(5, TimeUnit.SECONDS))
        assertEquals("đúng 1 request", 1L, calls.get().toLong())
    }
}
