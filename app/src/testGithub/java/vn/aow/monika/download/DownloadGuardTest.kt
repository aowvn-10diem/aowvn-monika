package vn.aow.monika.download

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.Call
import okhttp3.Connection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.TestApp
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * V196: guard phải chạy ở **network interceptor** (mỗi hop) và chặn hop redirect sang host lạ
 * trước khi request đó được gửi (proceed không được gọi).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DownloadGuardTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** Chain giả: ghi lại URL thực sự được gửi đi, không chạm mạng. */
    private class FakeChain(private val urlText: String) : Interceptor.Chain {
        var proceeded: HttpUrl? = null
        override fun request(): Request = Request.Builder().url(urlText.toHttpUrl()).build()
        override fun proceed(request: Request): Response {
            proceeded = request.url
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("".toResponseBody(null)).build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = OkHttpClient().newCall(request())
        override fun connectTimeoutMillis(): Int = 0
        override fun readTimeoutMillis(): Int = 0
        override fun writeTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun guardFor(vararg hosts: String) = DownloadGuard { it in hosts.toSet() }

    private fun url(s: String): String = s

    @Test fun hopHopLeDuocGui() {
        val chain = FakeChain(url("https://github.com/aowvn-10diem/pack.zip"))
        assertNotNull(guardFor("github.com").intercept(chain))
        assertNotNull(chain.proceeded)
    }

    @Test fun hopRedirectSangHostLaBiChanTruocKhiGui() {
        val guard = guardFor("github.com")
        val evil = FakeChain(url("https://evil.example/pack.zip"))
        val error = runCatching { guard.intercept(evil) }.exceptionOrNull()
        assertTrue("phải ném IOException", error is IOException)
        assertNull("không được gửi request tới host lạ", evil.proceeded)
    }

    @Test fun chiNhanHttps() {
        val httpHop = FakeChain(url("http://github.com/pack.zip"))
        assertTrue(runCatching { guardFor("github.com").intercept(httpHop) }.exceptionOrNull() is IOException)
        assertNull(httpHop.proceeded)
    }

    @Test fun clientTaiCoGuardONetworkInterceptorConClientKhacThiKhong() {
        // Đọc AppGraph để chắc guard thật sự được gắn (network = chạy lại mỗi hop).
        assertTrue(AppGraph.downloadHttp.networkInterceptors.any { it is DownloadGuard })
        assertTrue(AppGraph.browserHttp.networkInterceptors.any { it is DownloadGuard })
        assertFalse(AppGraph.http.networkInterceptors.any { it is DownloadGuard })
    }

    @Test fun guardCuaAppGraphChoPhepHostTaiVaHostConfigChanHostKhac() {
        val guard = AppGraph.downloadHttp.networkInterceptors.filterIsInstance<DownloadGuard>().single()
        for (ok in listOf("https://github.com/x.zip", "https://monika.aow.vn/config.json")) {
            val chain = FakeChain(url(ok))
            assertNotNull(ok, guard.intercept(chain))
            assertNotNull(ok, chain.proceeded)
        }
        for (bad in listOf("https://evil.example/x.zip", "http://github.com/x.zip")) {
            val chain = FakeChain(url(bad))
            assertTrue(bad, runCatching { guard.intercept(chain) }.exceptionOrNull() is IOException)
            assertNull(bad, chain.proceeded)
        }
    }

    @Test fun choPhepDich302ThatCuaGithubReleases() {
        // Chuỗi thật (kiểm 05/11/2026): github.com → 302 → release-assets.githubusercontent.com (200).
        assertTrue(vn.aow.monika.config.isAllowedDownloadHost("release-assets.githubusercontent.com"))
        assertTrue(vn.aow.monika.config.isAllowedDownloadHost("objects.githubusercontent.com"))
        assertFalse(vn.aow.monika.config.isAllowedDownloadHost("evil.example"))

        val guard = AppGraph.downloadHttp.networkInterceptors.filterIsInstance<DownloadGuard>().single()
        val hop = FakeChain(url("https://release-assets.githubusercontent.com/github-production-release-asset/1/x.zip?sig=abc&se=2026-11-05"))
        assertNotNull(guard.intercept(hop))
        assertNotNull(hop.proceeded)

        val evil = FakeChain(url("https://evil.example/x.zip"))
        assertTrue(runCatching { guard.intercept(evil) }.exceptionOrNull() is IOException)
        assertNull(evil.proceeded)
    }
}
