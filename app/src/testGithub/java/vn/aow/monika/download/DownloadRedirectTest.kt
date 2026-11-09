package vn.aow.monika.download

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * V196: redirect 302 **thật** chạy qua OkHttp (máy chủ HTTPS cục bộ, không ra mạng ngoài) để chứng minh
 * guard chạy ở hop kế: hop bị chặn thì server thứ hai nhận 0 request.
 * Không dùng Robolectric vì test này chỉ cần socket cục bộ.
 */
class DownloadRedirectTest {
    private lateinit var serverCerts: HandshakeCertificates
    private lateinit var clientCerts: HandshakeCertificates
    private lateinit var first: MockWebServer
    private lateinit var second: MockWebServer

    @Before fun setUp() {
        val held = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        serverCerts = HandshakeCertificates.Builder().heldCertificate(held).build()
        clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        first = MockWebServer().apply { useHttps(serverCerts.sslSocketFactory(), false); start() }
        second = MockWebServer().apply { useHttps(serverCerts.sslSocketFactory(), false); start() }
    }

    @After fun tearDown() {
        runCatching { first.shutdown() }
        runCatching { second.shutdown() }
    }

    private fun client(hosts: Set<String>) = OkHttpClient.Builder()
        .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
        .hostnameVerifier { _, _ -> true } // chứng thư tự ký cho localhost trong test
        .addNetworkInterceptor(DownloadGuard { it in hosts })
        .build()

    private fun call(client: OkHttpClient, url: String): Result<Int> = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.code }
    }

    @Test fun redirect302SangHostLaKhongGuiRequestNao() {
        // Host của hop 1 do máy chủ cục bộ quyết định (localhost hoặc 127.0.0.1) → chọn host còn lại làm "host lạ".
        val firstHost = first.url("/x.zip").host
        val foreignHost = if (firstHost == "localhost") "127.0.0.1" else "localhost"
        second.enqueue(MockResponse().setResponseCode(200).setBody("khong-duoc-toi-day"))
        first.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", "https://$foreignHost:${second.port}/x.zip")
        )
        val result = call(client(setOf(firstHost)), first.url("/x.zip").toString())

        assertTrue("phải bị chặn ở hop redirect: ${result.exceptionOrNull()}", result.exceptionOrNull() is IOException)
        assertEquals("hop đầu vẫn được gửi", 1, first.requestCount)
        assertEquals("hop redirect KHÔNG được gửi", 0, second.requestCount)
    }

    @Test fun redirect302SangHostChoPhepThiDiTiep() {
        val firstHost = first.url("/x.zip").host
        val secondHost = second.url("/x.zip").host
        second.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        first.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", "https://$secondHost:${second.port}/x.zip")
        )
        val result = call(client(setOf(firstHost, secondHost)), first.url("/x.zip").toString())

        assertEquals("request cuối phải thành công: ${result.exceptionOrNull()}", 200, result.getOrNull())
        assertEquals("hop redirect được gửi", 1, second.requestCount)
    }
}
