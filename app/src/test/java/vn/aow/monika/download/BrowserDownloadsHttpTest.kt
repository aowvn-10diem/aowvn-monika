package vn.aow.monika.download

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
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
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * #608: một lần tải nhận 4xx của máy chủ (link chết, tệp đã bị xoá) từng thành báo cáo `op:tai-xuong`.
 * Nay 4xx chỉ để lại vệt sự kiện; 5xx vẫn phải thành đúng một báo cáo để phát hiện sự cố thật.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class BrowserDownloadsHttpTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setUp() {
        AppGraph.init(app)
        File(app.filesDir, "diag").deleteRecursively()
        Diagnostics.resetOpThrottle()
        Diagnostics.opSender = { _, _ -> }
    }

    @After fun tearDown() {
        Diagnostics.opSender = null
        Diagnostics.resetOpThrottle()
    }

    /** Một lượt tải với máy chủ luôn trả [code]; trả về job sau khi luồng tải kết thúc. */
    private fun downloadWithStatus(code: Int): DlJob {
        val done = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body("".toResponseBody("text/plain".toMediaType())).build()
        }.build()
        val manager = BrowserDownloads(app, client, Prefs(app))
        manager.onJobFinished = { done.countDown() }
        val job = manager.start("https://example.test/tai/game.zip", null, null, null, null, confirmed = true)
        assertTrue("job phải kết thúc", done.await(5, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        return job
    }

    private fun downloadReports() =
        Diagnostics.list(app).filter { it.kind == "op-fail" && it.component == "op:tai-xuong" }

    @Test fun loi4xxChiGhiVetKhongLapBaoCao() {
        val job = downloadWithStatus(404)

        assertEquals("người chơi phải thấy tải thất bại", DlState.FAILED, job.state)
        assertTrue("câu báo phải nêu mã HTTP", job.error.orEmpty().contains("404"))
        assertEquals("link chết không được thành báo cáo lỗi app", 0, downloadReports().size)
    }

    @Test fun loi5xxVanLapMotBaoCao() {
        val job = downloadWithStatus(503)

        assertEquals(DlState.FAILED, job.state)
        assertEquals("sự cố máy chủ phải có đúng 1 báo cáo", 1, downloadReports().size)
    }
}
