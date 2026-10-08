package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.diag.Breadcrumbs
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File

/** Bắt lỗi game: phiên chơi dang dở = game đã chết bất thường → báo cáo; thoát bình thường → không báo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class DiagnosticsTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() { File(app.filesDir, "diag").deleteRecursively(); Diagnostics.pending.value = null }

    /** Giả lập tiến trình game khác (pid khác) bắt đầu phiên rồi chết. */
    private fun fakeDeadSession(stage: String) {
        val dir = File(app.filesDir, "diag").apply { mkdirs() }
        File(dir, "active-session.json").writeText(
            """{"pid":424242,"startedAt":1000,"kind":"libretro","core":"desmume","coreInfo":"cfg v1 · arm64-v8a","game":"Pokemon.nds","system":"Nintendo DS","stage":"$stage","stageAt":5000,"lastAlive":9000}"""
        )
    }

    @Test fun deadSessionBecomesReport() {
        fakeDeadSession("first-frame")
        val r = Diagnostics.collect(app)
        assertNotNull(r)
        assertEquals("Pokemon.nds", r!!.session!!.game)
        assertTrue(r.title.contains("khi đang chơi"))
        assertTrue(r.toText().contains("desmume"))
        assertEquals(r.id, Diagnostics.pending.value?.id)
        assertEquals(1, Diagnostics.list(app).size)
        // Phiên đã xử lý → lần mở sau không báo lại.
        assertNull(Diagnostics.collect(app))
    }

    @Test fun crashWhileLoadingIsSaidSo() {
        fakeDeadSession("loading-game")
        assertTrue(Diagnostics.collect(app)!!.title.contains("khởi động"))
    }

    @Test fun normalExitMakesNoReport() {
        Diagnostics.begin(app, "libretro", "mgba", "", "a.gba", "GBA")
        Diagnostics.end(app)
        assertNull(Diagnostics.collect(app))
        assertEquals(0, Diagnostics.list(app).size)
    }

    @Test fun ownLiveSessionIsNotACrash() {
        Diagnostics.begin(app, "libretro", "mgba", "", "a.gba", "GBA")
        assertNull(Diagnostics.collect(app)) // cùng pid = đang chạy
    }

    @Test fun javaCrashKeepsSessionContext() {
        Diagnostics.begin(app, "libretro", "mgba", "cfg v1", "a.gba", "GBA")
        Diagnostics.stage(app, "first-frame")
        val r = Diagnostics.recordJavaCrash(app, "main", IllegalStateException("boom"))
        assertEquals("java", r.kind)
        assertTrue(r.fromGame)
        assertTrue(r.detail.contains("boom"))
        assertNull(Diagnostics.collect(app)) // đã ghi rồi → không báo trùng
        assertEquals(1, Diagnostics.list(app).size)
    }

    @Test fun postsToEndpointWhenConfigured() {
        fakeDeadSession("first-frame")
        val r = Diagnostics.collect(app)!!
        var url = ""; var body = ""
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            url = chain.request().url.toString()
            val buf = okio.Buffer(); chain.request().body!!.writeTo(buf); body = buf.readUtf8()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(null, "{\"ok\":true}")).build()
        }.build()
        assertEquals(true, Diagnostics.send(app, http, r, "https://example.test/report"))
        assertEquals("https://example.test/report", url)
        assertTrue(body.contains("\"kind\"") && body.contains("Pokemon.nds") && body.contains("desmume"))
        assertEquals(true, Diagnostics.list(app).first().sent) // đánh dấu đã gửi
    }

    private fun httpReturning(code: Int) = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
        okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("x")
            .body(okhttp3.ResponseBody.create(null, "{}")).build()
    }.build()

    @Test fun sendResultNamesHttpCodeNetworkErrorAndMissingEndpoint() {
        fakeDeadSession("first-frame")
        val r = Diagnostics.collect(app)!!
        assertEquals(Diagnostics.SendResult(true), Diagnostics.sendResult(app, httpReturning(200), r, "https://example.test/report"))
        val tooLarge = Diagnostics.sendResult(app, httpReturning(413), r, "https://example.test/report")
        assertEquals(Diagnostics.SendResult(false, "HTTP 413"), tooLarge)
        val broken = okhttp3.OkHttpClient.Builder().addInterceptor { throw java.io.IOException("mạng sập") }.build()
        val net = Diagnostics.sendResult(app, broken, r, "https://example.test/report")
        assertEquals(false, net.ok); assertEquals("IOException", net.error)
        assertEquals("chưa cấu hình địa chỉ nhận", Diagnostics.sendResult(app, httpReturning(200), r, "").error)
        // Thất bại vẫn chép phần chữ vào clipboard và ghi vệt report-send.
        assertTrue(Breadcrumbs.read(app, android.os.Process.myPid()).any { it.contains("report-send") && it.contains("HTTP 413") })
    }

    @Test fun clipboardFallbackWhenNoEndpoint() {
        fakeDeadSession("first-frame")
        val r = Diagnostics.collect(app)!!
        val sent = Diagnostics.send(app, okhttp3.OkHttpClient(), r, "")
        assertEquals(false, sent)
        val clip = app.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip.contains("Pokemon.nds"))
    }

    @Test fun handledErrorCarriesComponentCrumbsAndEnv() {
        Diagnostics.crumb(app, "pack", "yêu cầu sevenzip")
        val r = Diagnostics.recordHandled(app, "pack:sevenzip", "tải lỗi", java.io.IOException("hết dung lượng"))
        assertEquals("pack:sevenzip", r.component)
        assertTrue(r.crumbs.any { it.contains("yêu cầu sevenzip") })
        assertTrue(r.env.contains("RAM") && r.env.contains("đĩa"))
        assertTrue(r.toText().contains("Thành phần: pack:sevenzip") && r.toText().contains("vệt sự kiện"))
    }

    @Test fun sameErrorIsMergedWithCount() {
        repeat(3) { Diagnostics.recordHandled(app, "pack:onsyuri", "tải lỗi", java.io.IOException("timeout 1$it")) }
        val l = Diagnostics.list(app)
        assertEquals(1, l.size)
        assertEquals(3, l[0].count)
        Diagnostics.recordHandled(app, "pack:azahar", "tải lỗi", java.io.IOException("x"))
        assertEquals(2, Diagnostics.list(app).size) // thành phần khác → báo cáo riêng
    }

    @Test fun javaCrashGetsComponentFromStack() {
        val e = RuntimeException("boom").apply { stackTrace = arrayOf(StackTraceElement("vn.aow.monika.pack.SimpleModule", "ensure", "SimpleModule.kt", 42)) }
        val r = Diagnostics.recordJavaCrash(app, "main", e)
        assertEquals("pack", r.component)
        assertTrue(r.title.contains("pack"))
    }

    @Test fun deadGameSessionKeepsCrumbsAndLibretroComponent() {
        fakeDeadSession("loading-game")
        Breadcrumbs.add(app, "stage", "loading-game", pid = 424242)
        val r = Diagnostics.collect(app)!!
        assertEquals("engine:libretro:desmume", r.component)
        assertTrue(r.crumbs.single().contains("loading-game"))
    }

    @Test fun scrubHidesPathsEmailsAndTokens() {
        val t = Diagnostics.scrub(app, "open /storage/emulated/0/Download/Vy.nds from /data/user/0/vn.aow.monika/files/x.so mail a.b@c.vn token=abc123&k=1 Authorization: Bearer zzz")
        assertTrue(!t.contains("/storage") && !t.contains("/data/user") && !t.contains("a.b@c.vn") && !t.contains("abc123"))
        assertTrue(!t.contains("Vy.nds") && !t.contains("zzz"))
    }
}
