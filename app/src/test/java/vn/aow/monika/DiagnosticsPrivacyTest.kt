package vn.aow.monika

import android.app.Application
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.*
import okhttp3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.diag.Breadcrumbs
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [30])
class DiagnosticsPrivacyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val fake = listOf(
        "Authorization: Bearer fakeBearer", "Authorization: Basic fakeBasic",
        "https://example.test/?y=fakeRa&key=fakeKey&token=fakeToken",
        "password=\"fake password có dấu\"", "a.b@example.test",
        "content://docs/tree/Thư mục riêng/file.txt",
        "content://docs/tree/x?key=fakeKey&name=Tên riêng/file.txt",
        "token=\"fakeToken", "file:///sdcard/Tên riêng/file.txt",
        "/storage/emulated/0/Thư mục riêng/game.nds", "/data/user/0/vn.aow.monika/files/Tên riêng"
    ).joinToString("\n")
    private val secrets = listOf("fakeBearer", "fakeBasic", "fakeRa", "fakeKey", "fakeToken",
        "fake password", "a.b@example.test", "content://", "file://", "Thư mục riêng", "Tên riêng", "/storage/", "/data/user/")

    @Before fun clean() { File(app.filesDir, "diag").deleteRecursively() }

    private fun report() = Diagnostics.Report(1, 2, fake, fake, fake, fake,
        session = Diagnostics.Session(123, 10, fake, fake, fake, fake, fake, fake, 20, 30),
        reason = fake, detail = fake, log = listOf("123.000 456 457 F DEBUG : $fake"),
        component = fake, fingerprint = fake, env = fake, crumbs = listOf(fake))

    private fun assertSafe(text: String) { secrets.forEach { assertFalse("Lộ $it", text.contains(it)) } }

    @Test fun everyStringIsScrubbedAtSaveIncludingDebugAndSession() {
        Diagnostics.save(app, report())
        val disk = File(app.filesDir, "diag/reports/1.json").readText()
        assertSafe(disk)
        val root = Json.parseToJsonElement(disk).jsonObject
        assertEquals(123, root["session"]!!.jsonObject["pid"]!!.jsonPrimitive.int)
        assertEquals(2, root["time"]!!.jsonPrimitive.int)
        assertSafe(Diagnostics.list(app).single().toText())
        assertEquals(Diagnostics.scrub(app, fake), Diagnostics.scrub(app, Diagnostics.scrub(app, fake)))
    }

    @Test fun manualAndAutoSendScrubEvenReportsNotFromStore() {
        val posted = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val b = Buffer(); chain.request().body!!.writeTo(b)
            synchronized(posted) { posted += b.readUtf8() }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(null, "{}")).build()
        }.build()
        assertTrue(Diagnostics.send(app, http, report(), "https://example.test/report"))
        assertSafe(posted.single())
        synchronized(posted) { posted.clear() }
        val autoDone = CountDownLatch(1)
        val autoHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val b = Buffer(); chain.request().body!!.writeTo(b)
            synchronized(posted) { posted += b.readUtf8() }
            autoDone.countDown()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(500).message("Retry")
                .body(ResponseBody.create(null, "{}")).build()
        }.build()
        Diagnostics.autoSend(app, autoHttp, report(), "https://example.test/report", true, true)
        assertTrue(autoDone.await(5, TimeUnit.SECONDS))
        synchronized(posted) { assertSafe(posted.single()) }
    }

    @Test fun clipboardAndOldFilesCannotExposeSecrets() {
        assertFalse(Diagnostics.send(app, OkHttpClient(), report(), ""))
        assertSafe(app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
        val old = File(app.filesDir, "diag/reports/9.json").apply { parentFile!!.mkdirs() }
        old.writeText("""{"id":9,"time":9,"kind":"java","title":"Authorization: Bearer fakeBearer","app":"old","device":"old"}""")
        assertSafe(Diagnostics.list(app).single().toText())
    }

    @Test fun breadcrumbAndSessionAreScrubbedBeforeWriting() {
        Breadcrumbs.add(app, fake, fake)
        assertSafe(File(app.filesDir, "diag/crumbs").listFiles()!!.single().readText())
        Diagnostics.begin(app, fake, fake, fake, fake, fake)
        assertSafe(File(app.filesDir, "diag/active-session.json").readText())
        val r = Diagnostics.recordHandled(app, "pack:test", fake, IllegalStateException(fake))
        assertSafe(Diagnostics.reportJson(app, r))
        assertSafe(r.toText())
    }
}
