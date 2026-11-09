package vn.aow.monika.download

import android.app.Application
import android.app.DownloadManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.Prefs
import vn.aow.monika.ui.TestApp
import java.io.IOException

/**
 * V196b: luồng tải qua trình quản lý tải của hệ thống không đi qua OkHttp, nên phải kiểm URL
 * **trước khi** giao cho hệ thống: HTTPS + host thuộc danh sách đã chốt; sai thì không enqueue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DownloaderGuardTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val dm get() = app.getSystemService(DownloadManager::class.java)

    /** Client không ra mạng: mọi request trả 200 rỗng (đủ cho bước hỏi tên tệp). */
    private fun downloader() = Downloader(
        app,
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("".toResponseBody(null)).build()
        }.build(),
        Prefs(app),
    )

    @Test fun urlSaiKhongGoiDownloadManager() = runBlocking {
        val d = downloader()
        val bad = listOf(
            "http://pixeldrain.com/api/file/abc",          // không phải HTTPS
            "https://evil.example/game.zip",               // host lạ
            "file:///data/data/vn.aow.monika/files/x.zip", // scheme lạ
            "https://pixeldrain.com.evil.example/x.zip",   // host na ná
            "khong-phai-url",
        )
        for (url in bad) {
            val error = runCatching { d.enqueue(url) }.exceptionOrNull()
            assertTrue("$url phải bị chặn: $error", error is IOException)
        }
        assertEquals("không được enqueue request nào", 0, shadowOf(dm).requestCount)
    }

    @Test fun urlHopLeDuocDuaChoDownloadManager() = runBlocking {
        val d = downloader()
        d.enqueue("https://pixeldrain.com/api/file/abc?download")      // host tệp người dùng
        d.enqueue("https://github.com/aowvn-10diem/aowvn-monika/releases/download/v0/x.apk", isTool = true) // host mã/gói
        assertEquals(2, shadowOf(dm).requestCount)
    }
}
