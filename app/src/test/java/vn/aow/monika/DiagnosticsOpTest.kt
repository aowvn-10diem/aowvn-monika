package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.diag.Diagnostics.Op
import vn.aow.monika.library.Importer
import vn.aow.monika.ui.TestApp
import java.io.File
import java.io.IOException

/** V112: mọi thao tác người dùng lỗi → đúng 1 báo cáo `op-fail` (tên thao tác, bước, mã lỗi, vệt sự kiện), không lộ link, chống spam 10 phút. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class DiagnosticsOpTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private var now = 1_000_000L
    private val sent = java.util.Collections.synchronizedList(mutableListOf<Diagnostics.Report>())

    @Before fun setUp() {
        File(app.filesDir, "diag").deleteRecursively()
        Diagnostics.pending.value = null
        Diagnostics.resetOpThrottle()
        now = 1_000_000L
        Diagnostics.opClock = { now }
        sent.clear()
        Diagnostics.opSender = { _, r -> sent += r }
    }

    @After fun tearDown() {
        Diagnostics.opClock = { System.currentTimeMillis() }
        Diagnostics.opSender = null
        Diagnostics.resetOpThrottle()
    }

    private fun opFails() = Diagnostics.list(app).filter { it.kind == "op-fail" }
    /** Cùng một chỗ gọi → cùng chữ ký lỗi (như trong app, nơi mã gọi cố định). */
    private fun failInstall() = Diagnostics.op(app, Op.INSTALL_PACK) { step("tai"); throw IOException("hết dung lượng") }

    @Test fun everyNamedOperationProducesExactlyOneOpFailReport() {
        val names = listOf(Op.DOWNLOAD, Op.EXTRACT, Op.IMPORT, Op.PATCH_ROM, Op.INSTALL_PACK, Op.OPEN_GAME, Op.FETCH_CONFIG, Op.FETCH_FEED, Op.FETCH_FORUM)
        names.forEach { name ->
            val r = Diagnostics.op(app, name) { step("buoc-thu"); throw IOException("hỏng $name") }
            assertTrue(r.isFailure)
        }
        val reports = opFails()
        assertEquals(names.size, reports.size)
        assertEquals(names.map { "op:$it" }.toSet(), reports.map { it.component }.toSet())
        reports.forEach { assertEquals(1, it.count) }
        assertEquals(names.size, sent.size)
    }

    @Test fun reportCarriesOperationStepErrorCodeCrumbsAndDevice() {
        repeat(100) { Diagnostics.crumb(app, "t", "bước $it") }
        Diagnostics.op(app, Op.EXTRACT, "dir/Game (Việt hóa).zip") {
            step("doc-header"); throw IllegalStateException("không đọc được")
        }
        val r = opFails().single()
        assertEquals("op:giai-nen", r.component)
        assertTrue(r.title.contains("giai-nen") && r.title.contains("doc-header") && r.title.contains("IllegalStateException"))
        assertTrue(r.detail.contains("thao tác: giai-nen"))
        assertTrue(r.detail.contains("bước: doc-header"))
        assertTrue(r.detail.contains("mã lỗi: IllegalStateException"))
        assertTrue("tên file game được ghi, không kèm thư mục", r.detail.contains("đối tượng: Game (Việt hóa).zip") && !r.detail.contains("dir/"))
        assertEquals("đúng 30 breadcrumb gần nhất", 30, r.crumbs.size)
        assertTrue(r.crumbs.any { it.endsWith("bước 99") } && r.crumbs.none { it.endsWith("bước 10") })
        assertTrue("có bước đang làm trong vệt", r.crumbs.any { it.contains("giai-nen: doc-header") })
        assertTrue(r.env.contains("RAM"))
        assertTrue(r.app.isNotBlank() && r.device.isNotBlank())
    }

    @Test fun neverLeaksDownloadLinks() {
        Diagnostics.op(app, Op.DOWNLOAD, "game.zip") {
            throw IOException("Không tải được https://pixeldrain.com/api/file/AbCd1234?download và http://x.test/rom.nds")
        }
        val r = opFails().single()
        val text = r.toText() + r.detail + r.reason + r.title
        assertFalse(text.contains("pixeldrain") || text.contains("http://") || text.contains("https://"))
        assertTrue(r.detail.contains("<link>"))
    }

    @Test fun sameOperationAndSignatureWithinTenMinutesReportsOnlyOnce() {
        repeat(5) { failInstall() }
        assertEquals(1, opFails().size)
        assertEquals(1, sent.size)
        now += Diagnostics.OP_THROTTLE_MS + 1
        failInstall()
        assertEquals("sau 10 phút: gộp vào cùng báo cáo, tăng đếm", 1, opFails().size)
        assertEquals(2, opFails().single().count)
        assertEquals(2, sent.size)
    }

    @Test fun differentOperationOrErrorIsReportedSeparately() {
        Diagnostics.op(app, Op.DOWNLOAD) { throw IOException("a") }
        Diagnostics.op(app, Op.EXTRACT) { throw IOException("a") }
        Diagnostics.op(app, Op.EXTRACT) { throw IllegalStateException("a") }
        assertEquals(3, opFails().size)
    }

    @Test fun successAndExpectedAndCancellationLeaveNoReport() = runBlocking {
        assertEquals(42, Diagnostics.op(app, Op.OPEN_GAME) { 42 }.getOrThrow())
        val expected = Diagnostics.op(app, Op.EXTRACT, expected = { it is SecurityException }) { throw SecurityException("cần mật khẩu") }
        assertTrue(expected.isFailure)
        val offline = Diagnostics.op(app, Op.FETCH_FEED, expected = Diagnostics::isOffline) { throw java.net.UnknownHostException("aow.vn") }
        assertTrue(offline.isFailure)
        try {
            Diagnostics.opSuspend(app, Op.OPEN_GAME) { throw CancellationException("hủy") }
            fail("hủy coroutine phải được ném lại")
        } catch (_: CancellationException) {}
        assertTrue(opFails().isEmpty())
        assertTrue(sent.isEmpty())
    }

    @Test fun suspendVariantRecordsFailureWithStep() = runBlocking {
        val r = Diagnostics.opSuspend(app, Op.OPEN_GAME) { step("nap-native"); throw UnsatisfiedLinkError("libx.so") }
        assertTrue(r.isFailure)
        val rep = opFails().single()
        assertEquals("op:mo-game", rep.component)
        assertTrue(rep.detail.contains("bước: nap-native") && rep.detail.contains("UnsatisfiedLinkError"))
    }

    @Test fun debugAndRobolectricNeverAutoSendToTheWorker() {
        var calls = 0
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain -> calls++; chain.proceed(chain.request()) }.build()
        assertTrue(Diagnostics.isTestDevice())
        assertFalse(Diagnostics.autoSendAllowedHere())
        Diagnostics.opFail(app, Op.FETCH_FEED, "tai-bai", IOException("HTTP 500"))
        val r = opFails().single()
        Diagnostics.autoSend(app, http, r, "https://crash.invalid/report", true, true)
        Thread.sleep(200) // nếu có gửi thì luồng nền đã kịp gọi interceptor
        assertEquals("không được gọi mạng", 0, calls)
        Diagnostics.allowDebugSend = true
        try { assertTrue("cờ thủ công bật lại được", Diagnostics.autoSendAllowedHere()) } finally { Diagnostics.allowDebugSend = false }
    }

    @Test fun opFailIgnoresCancellationAndOfflineButKeepsACrumb() {
        Diagnostics.opFail(app, Op.INSTALL_PACK, "pack:x", CancellationException("Worker dừng"))
        Diagnostics.opFail(app, Op.DOWNLOAD, "tai-ve", IOException("Canceled"))
        Diagnostics.opFail(app, Op.DOWNLOAD, "tai-ve", java.net.UnknownHostException("host"))
        Diagnostics.opFail(app, Op.DOWNLOAD, "tai-ve", IOException("bọc", java.net.SocketTimeoutException("quá hạn")))
        assertTrue(opFails().isEmpty())
        assertTrue(sent.isEmpty())
        assertTrue(vn.aow.monika.diag.Breadcrumbs.read(app, android.os.Process.myPid()).any { it.contains("không báo") })
        Diagnostics.opFail(app, Op.DOWNLOAD, "tai-ve", IOException("HTTP 500")) // lỗi thật vẫn báo
        assertEquals(1, opFails().size)
    }

    @Test fun concurrentSameFailureWinsTheThrottleExactlyOnce() {
        val threads = 12
        val gate = java.util.concurrent.CyclicBarrier(threads)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        try {
            val futures = (1..threads).map {
                pool.submit {
                    gate.await()
                    Diagnostics.opFail(app, Op.DOWNLOAD, "tai-ve", IOException("HTTP 502"))
                }
            }
            futures.forEach { it.get() }
        } finally { pool.shutdown() }
        assertEquals("nhiều lượt tải cùng lỗi cùng lúc chỉ lập 1 báo cáo", 1, opFails().size)
        assertEquals(1, synchronized(sent) { sent.size })
    }

    @Test fun importUriOpenFailureLeavesOneImportOpFailAndStillThrows() {
        try {
            Importer.importUri(app, android.net.Uri.parse("content://vn.aow.khong-co/game.nds"), emptyList())
            fail("mở file lỗi phải ném lại để giao diện báo cho người dùng")
        } catch (_: Exception) {}
        val r = opFails().single()
        assertEquals("op:nhap-game", r.component)
        assertTrue(r.detail.contains("bước: mo-file"))
    }

    @Test fun errorCodeShowsRootCause() {
        val e = IOException("ngoài", java.io.FileNotFoundException("gốc"))
        assertEquals("IOException <- FileNotFoundException", Diagnostics.errorCode(e))
    }

    @Test fun importerExtractFailureLeavesOneOpFailButPasswordPromptDoesNot() {
        // Không chạy giải nén thật: SevenZipNative nạp thư viện native vào sandbox Robolectric và làm hỏng RarTest chạy sau trong cùng JVM.
        val err = Importer.extractOp(app, "Hong.zip") { throw IOException("file nén hỏng") }
        assertTrue(err is IOException)
        val r = opFails().single()
        assertEquals("op:giai-nen", r.component)
        assertTrue(r.detail.contains("đối tượng: Hong.zip") && r.detail.contains("bước: giai-nen"))
        val pw = Importer.extractOp(app, "Khoa.rar") { throw vn.aow.monika.library.ArchiveExtractor.PasswordException("cần mật khẩu") }
        assertTrue(pw is vn.aow.monika.library.ArchiveExtractor.PasswordException)
        assertEquals("cần mật khẩu không phải lỗi: không thêm báo cáo", 1, opFails().size)
    }
}
