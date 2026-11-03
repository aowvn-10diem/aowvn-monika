package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.Config
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * V194: hạn mức ghi KV của Worker chỉ 1000/ngày và mỗi báo cáo là 1 lần ghi.
 * Cùng dấu vân tay chỉ gửi 1 lần/giờ (nhớ qua lần mở app); `lowmem` chặt hơn: 1 lần mỗi phiên.
 * Bản gộp vẫn tăng `count` ở máy nhưng không gửi lại.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DiagnosticsSendDedupeTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val requests = AtomicInteger()
    private var now = 1_000_000_000L

    @Before fun setUp() {
        File(app.filesDir, "diag").deleteRecursively()
        app.getSharedPreferences("diag-send", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Diagnostics.allowDebugSend = true
        Diagnostics.resetSendDedupe()
        Diagnostics.sendClock = { now }
        requests.set(0)
    }

    @After fun tearDown() {
        Diagnostics.allowDebugSend = false
        Diagnostics.sendClock = { System.currentTimeMillis() }
        Diagnostics.resetSendDedupe()
    }

    /** OkHttp giả: đếm request, trả 200; mỗi request nhả 1 permit để test chờ được. */
    private val latch = CountDownLatch(16)
    private fun http(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        latch.countDown()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("{}".toResponseBody(null)).build()
    }.build()

    private fun report(kind: String, fingerprint: String) = Diagnostics.Report(
        id = now, time = now, kind = kind, title = "thử $kind", app = "0.8.7 (120)", device = "máy thử",
        fingerprint = fingerprint, count = 1,
    )

    private fun send(report: Diagnostics.Report) {
        Diagnostics.autoSend(app, http(), report, "https://crash.invalid/report", true, true)
    }

    /** Chờ tối đa [ms]; trả về số request đã thấy. */
    private fun settle(ms: Long = 400): Int {
        latch.await(ms, TimeUnit.MILLISECONDS)
        Thread.sleep(120) // chờ markSent() chạy xong sau khi có phản hồi
        return requests.get()
    }

    @Test fun lowmemSameFingerprintIsSentOncePerSession() {
        send(report("lowmem", "363ea01f"))
        assertEquals(1, settle())
        now += 5 * 60_000L // 5 phút sau, vẫn cùng phiên
        send(report("lowmem", "363ea01f"))
        assertEquals("lần thứ hai trong phiên không gửi", 1, settle())
    }

    @Test fun lowmemIsAlsoBlockedAcrossSessionsWithinAnHour() {
        send(report("lowmem", "363ea01f"))
        assertEquals(1, settle())
        Diagnostics.resetSendDedupe() // như mở lại app: mất bộ nhớ phiên, còn tệp trên đĩa
        now += 10 * 60_000L
        send(report("lowmem", "363ea01f"))
        assertEquals("mở lại app trong 1 giờ vẫn không gửi lại", 1, settle())
    }

    @Test fun otherKindsAreBlockedForOneHourThenSentAgain() {
        send(report("anr", "aaaa1111"))
        assertEquals(1, settle())
        now += Diagnostics.SEND_DEDUPE_MS - 1
        send(report("anr", "aaaa1111"))
        assertEquals("chưa đủ 1 giờ thì không gửi", 1, settle())
        now += 2
        send(report("anr", "aaaa1111"))
        assertEquals("qua 1 giờ thì gửi lại", 2, settle())
    }

    @Test fun differentFingerprintsAreNotBlocked() {
        send(report("anr", "aaaa1111"))
        assertEquals(1, settle())
        send(report("anr", "bbbb2222"))
        assertEquals(2, settle())
    }

    @Test fun afterSendingTheFingerprintIsRememberedOnDiskAcrossRelaunch() {
        send(report("lowmem", "363ea01f"))
        assertEquals(1, settle())
        assertTrue(Diagnostics.sentWithin(app, "363ea01f", Diagnostics.SEND_DEDUPE_MS, now))
        Diagnostics.resetSendDedupe() // như mở lại app: mất bộ nhớ phiên
        assertTrue(
            "phải nhớ qua lần mở app nhờ tệp, không chỉ RAM",
            Diagnostics.sentWithin(app, "363ea01f", Diagnostics.SEND_DEDUPE_MS, now),
        )
        assertTrue("báo cáo đã gửi được đánh dấu sent", Diagnostics.list(app).single().sent)
    }
}
