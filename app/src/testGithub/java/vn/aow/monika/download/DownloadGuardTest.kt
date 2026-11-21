package vn.aow.monika.download

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
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

/**
 * V196: guard phải chạy ở **network interceptor** (mỗi hop) và chặn hop redirect sang host lạ
 * trước khi request đó được gửi (proceed không được gọi).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DownloadGuardTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun guardFor(vararg hosts: String) = DownloadGuard({ it in hosts.toSet() })

    private fun url(s: String): String = s

    @Test fun hopHopLeDuocGui() {
        val chain = FakeChain(url("https://github.com/aowvn-10diem/pack.zip"))
        assertNotNull(guardFor("github.com").intercept(chain))
        assertNotNull(chain.proceeded)
    }

    @Test fun chanDuongDanNgoaiTienToTrenHostDungChung() {
        // V206: host đúng chưa đủ — đường dẫn phải nằm trong tiền tố chủ sở hữu/gói đã chốt.
        val guard = guardFor("github.com", "cdn.jsdelivr.net")
        for (ok in listOf(
            "https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/v1/x.zip",
            "https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle/ruffle.js",
            "https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle@0.1.0/dist/ruffle.js",
        )) {
            val chain = FakeChain(ok)
            assertNotNull(ok, guard.intercept(chain))
            assertNotNull(ok, chain.proceeded)
        }
        for (bad in listOf(
            "https://github.com/evil/repo/releases/download/v1/core.zip",
            "https://github.com/aowvn-10diem-khac/repo/x.zip",
            "https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle.evil/x.js",
            "https://cdn.jsdelivr.net/gh/abpvn/abpvn@master/filter/x.txt",
        )) {
            val chain = FakeChain(bad)
            assertTrue(bad, runCatching { guard.intercept(chain) }.exceptionOrNull() is IOException)
            assertNull(bad, chain.proceeded)
        }
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
        for (ok in listOf("https://github.com/aowvn-10diem/x.zip", "https://monika.aow.vn/config.json")) {
            val chain = FakeChain(url(ok))
            assertNotNull(ok, guard.intercept(chain))
            assertNotNull(ok, chain.proceeded)
        }
        for (bad in listOf(
            "https://evil.example/x.zip",
            "http://github.com/aowvn-10diem/x.zip",
            // V206: host đúng nhưng đường dẫn ngoài tiền tố chủ sở hữu.
            "https://github.com/evil/repo/releases/download/v1/core.zip",
        )) {
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
        val officialOrigin = "https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/v1/x.zip"
        val hop = FakeChain(
            url("https://release-assets.githubusercontent.com/github-production-release-asset/1/x.zip?sig=abc&se=2026-11-05"),
            origin = officialOrigin,
        )
        assertNotNull(guard.intercept(hop))
        assertNotNull(hop.proceeded)

        // V206: host CDN GitHub không được là URL gốc, kể cả khi hop trước đó trỏ repo người khác.
        val direct = FakeChain(url("https://release-assets.githubusercontent.com/github-production-release-asset/1/x.zip"))
        assertTrue(runCatching { guard.intercept(direct) }.exceptionOrNull() is IOException)
        assertNull(direct.proceeded)
        val foreignOrigin = FakeChain(
            url("https://release-assets.githubusercontent.com/github-production-release-asset/1/x.zip"),
            origin = "https://github.com/evil/repo/releases/download/v1/x.zip",
        )
        assertTrue(runCatching { guard.intercept(foreignOrigin) }.exceptionOrNull() is IOException)
        assertNull(foreignOrigin.proceeded)

        val evil = FakeChain(url("https://evil.example/x.zip"))
        assertTrue(runCatching { guard.intercept(evil) }.exceptionOrNull() is IOException)
        assertNull(evil.proceeded)
    }

    // ---- V196: luồng config -> kiểm dữ liệu -> guard (không ra mạng) ----

    @Test fun configChuaHostLaBiTuChoiTruocKhiCoRequest() {
        val hash = "a".repeat(64)
        val json = """{"configVersion":1,"modules":{"x":{"version":"1","url":"https://evil.example/x.zip","sha256":"$hash"}}}"""
        val error = runCatching { vn.aow.monika.config.ConfigValidation.parse(json, 0) }.exceptionOrNull()
        assertTrue("config phải bị từ chối: $error", error is IllegalArgumentException)

        val chain = FakeChain("https://evil.example/x.zip")
        val guard = AppGraph.downloadHttp.networkInterceptors.filterIsInstance<DownloadGuard>().single()
        assertTrue(runCatching { guard.intercept(chain) }.exceptionOrNull() is IOException)
        assertNull("không được gửi request", chain.proceeded)
    }

    @Test fun configHopLeThiUrlDiDungHost() {
        val hash = "b".repeat(64)
        val json = """{"configVersion":1,"modules":{"x":{"version":"1","url":"https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/e/x.zip","sha256":"$hash"}}}"""
        val cfg = vn.aow.monika.config.ConfigValidation.parse(json, 0)
        val chain = FakeChain(cfg.modules.getValue("x").url)
        val guard = AppGraph.downloadHttp.networkInterceptors.filterIsInstance<DownloadGuard>().single()
        guard.intercept(chain)
        org.junit.Assert.assertEquals("github.com", chain.proceeded?.host)
    }

    @Test fun choPhepCongG3Redirect1HopSangR2SigV4() {
        val r2Host = "0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"
        assertTrue(vn.aow.monika.config.isAllowedDownloadHost("api.monika.aow.vn"))
        assertTrue(vn.aow.monika.config.isAllowedDownloadHost(r2Host))
        assertTrue(vn.aow.monika.config.isRedirectOnlyDownloadHost(r2Host))
        assertFalse(vn.aow.monika.config.isAllowedDownloadHost("evil.r2.cloudflarestorage.com"))

        // Config chứa URL cổng G3 `/pack/<tên>` phải parse thành công.
        val hash = "c".repeat(64)
        val json = """{"configVersion":1,"modules":{"x":{"version":"1","url":"https://api.monika.aow.vn/pack/ppsspp-1-arm64-v8a.zip","sha256":"$hash"}}}"""
        val cfg = vn.aow.monika.config.ConfigValidation.parse(json, 0)
        val packOrigin = cfg.modules.getValue("x").url

        val guard = AppGraph.downloadHttp.networkInterceptors.filterIsInstance<DownloadGuard>().single()
        // Hop 0: gọi vào cổng /pack/
        val firstHop = FakeChain(packOrigin)
        assertNotNull(guard.intercept(firstHop))
        assertEquals("api.monika.aow.vn", firstHop.proceeded?.host)

        // Hop 1 (302 từ cổng /pack/ sang R2 SigV4 hợp lệ trên cùng Call):
        val r2SignedUrl = "https://$r2Host/monika-packs/ppsspp-1-arm64-v8a.zip?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=deadbeef"
        val redirectHop = FakeChain(r2SignedUrl, origin = packOrigin, sharedCall = firstHop.call())
        assertNotNull(guard.intercept(redirectHop))
        assertEquals(r2Host, redirectHop.proceeded?.host)
    }

    @Test fun chanMoiTruongHopG3VaR2SaiQuyTacVaRedirect2Hop() {
        val r2Host = "0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"
        val r2SignedUrl = "https://$r2Host/monika-packs/core.zip?X-Amz-Signature=deadbeef"
        val hash = "d".repeat(64)
        val guard = AppGraph.downloadHttp.networkInterceptors.filterIsInstance<DownloadGuard>().single()

        // 1) Đặt trực tiếp URL R2 hoặc đường dẫn ngoài /pack/ trên api.monika.aow.vn trong config → bị ConfigValidation bác bỏ.
        for (badConfigUrl in listOf(
            r2SignedUrl,
            "https://api.monika.aow.vn/report",
            "https://api.monika.aow.vn/pack/",
            "https://api.monika.aow.vn/pack/../secret.zip",
        )) {
            val badJson = """{"configVersion":1,"modules":{"x":{"version":"1","url":"$badConfigUrl","sha256":"$hash"}}}"""
            assertTrue(
                badConfigUrl,
                runCatching { vn.aow.monika.config.ConfigValidation.parse(badJson, 0) }.exceptionOrNull() is IllegalArgumentException,
            )
        }

        // 2) Gọi trực tiếp R2 (không phải redirect từ /pack/), hoặc redirect chéo nguồn (github -> R2, /pack/ -> github CDN),
        //    hoặc sai bucket, hoặc thiếu X-Amz-Signature → bị DownloadGuard chặn trước khi gửi.
        for ((target, origin) in listOf(
            r2SignedUrl to r2SignedUrl, // gọi thẳng R2
            r2SignedUrl to "https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/v1/x.zip", // github -> R2
            r2SignedUrl to "https://monika.aow.vn/config.json", // không phải /pack/
            "https://release-assets.githubusercontent.com/github-production-release-asset/1/x.zip" to "https://api.monika.aow.vn/pack/x.zip", // /pack/ -> github CDN
            "https://$r2Host/other-bucket/core.zip?X-Amz-Signature=deadbeef" to "https://api.monika.aow.vn/pack/core.zip", // sai bucket R2
            "https://$r2Host/monika-packs/core.zip" to "https://api.monika.aow.vn/pack/core.zip", // thiếu X-Amz-Signature
            "https://api.monika.aow.vn/report" to "https://api.monika.aow.vn/report", // ngoài /pack/
        )) {
            val chain = FakeChain(target, origin = origin)
            assertTrue("$origin -> $target", runCatching { guard.intercept(chain) }.exceptionOrNull() is IOException)
            assertNull("$origin -> $target", chain.proceeded)
        }

        // 3) Chặn redirect 2 hop trên cùng một Call (hop 0 -> hop 1 hợp lệ -> hop 2 bị chặn cứng).
        val packOrigin = "https://api.monika.aow.vn/pack/core.zip"
        val hop0 = FakeChain(packOrigin)
        assertNotNull(guard.intercept(hop0))
        val hop1 = FakeChain(r2SignedUrl, origin = packOrigin, sharedCall = hop0.call())
        assertNotNull(guard.intercept(hop1))
        val hop2 = FakeChain(r2SignedUrl, origin = packOrigin, sharedCall = hop0.call())
        assertTrue("phải chặn hop redirect thứ 2", runCatching { guard.intercept(hop2) }.exceptionOrNull() is IOException)
        assertNull("không được gửi hop thứ 2", hop2.proceeded)
    }
}

