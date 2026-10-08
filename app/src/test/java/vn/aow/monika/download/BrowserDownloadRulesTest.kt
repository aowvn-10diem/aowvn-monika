package vn.aow.monika.download

import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.Prefs
import vn.aow.monika.library.GameStorage
import vn.aow.monika.ui.TestApp
import java.io.File

/** Nhánh lỗi, chọn nơi lưu mặc định, và các quy tắc đặt tên của trình quản lý tải. Máy chủ giả trong bộ nhớ, không mạng. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class BrowserDownloadRulesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val payload = ByteArray(50_000) { (it % 97).toByte() }

    private fun ok(disposition: String?) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .apply { if (disposition != null) header("Content-Disposition", disposition) }
            .body(payload.toResponseBody("application/octet-stream".toMediaType())).build()
    }.build()

    private fun status(code: Int) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("lỗi")
            .body("".toResponseBody("text/plain".toMediaType())).build()
    }.build()

    private fun waitFor(job: DlJob, state: DlState) {
        repeat(200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (job.state == state) return
            Thread.sleep(25)
        }
        throw AssertionError("Trạng thái ${job.state}, mong đợi $state (${job.error})")
    }

    @Test fun serverErrorMarksFailedAndDismissRemovesRow() {
        val mgr = BrowserDownloads(app, status(404), Prefs(app))
        val job = mgr.start("https://host.test/missing", null, null, null, null)
        waitFor(job, DlState.FAILED)
        assertEquals("Máy chủ trả lỗi 404", job.error)
        assertFalse(job.part.exists())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(mgr.jobs.contains(job))
        mgr.dismiss(job)
        assertTrue(mgr.jobs.isEmpty())
    }

    @Test fun defaultDestFollowsSavedChoice() {
        val prefs = Prefs(app)
        val mgr = BrowserDownloads(app, ok(null), prefs)
        assertEquals(SaveDest.Library, mgr.defaultDest())
        prefs.dlDest = "dl"
        assertEquals(SaveDest.Downloads, mgr.defaultDest())
        prefs.dlDest = "folder"
        assertEquals(SaveDest.Library, mgr.defaultDest()) // chưa có thư mục đã chọn → về Thư viện
        prefs.dlFolder = "content://tree/game"
        assertEquals(SaveDest.Folder("content://tree/game", "Thư mục đã chọn"), mgr.defaultDest())
        prefs.dlFolderLabel = "Game"
        assertEquals(SaveDest.Folder("content://tree/game", "Game"), mgr.defaultDest())
    }

    @Test fun autoConfirmSavesPendingJobToDefaultPlace() {
        // Dùng file APK: nhánh này không gọi WorkManager (ImportWorker), giống test APK đang xanh. File .zip đi qua ImportWorker; chưa xác minh đó có phải nguyên nhân đỏ trên CI.
        val mgr = BrowserDownloads(app, ok("attachment; filename*=UTF-8''tu-dong.apk"), Prefs(app))
        val job = mgr.start("https://host.test/auto", null, null, null, null)
        waitFor(job, DlState.READY)
        mgr.autoConfirmPending() // người dùng đóng trình duyệt khi chưa chọn nơi lưu
        assertTrue(job.confirmed)
        assertEquals(SaveDest.Library, job.dest)
        waitFor(job, DlState.SAVED)
        assertTrue(File(GameStorage.downloads(app), "tu-dong.apk").exists())
    }

    @Test fun apkConfirmedToLibraryIsSavedWithoutImport() {
        val mgr = BrowserDownloads(app, ok(null), Prefs(app))
        val job = mgr.start("https://host.test/game/app.apk", null, null, null, null)
        waitFor(job, DlState.READY)
        mgr.confirm(job, "app.apk", SaveDest.Library)
        waitFor(job, DlState.SAVED)
        assertTrue(File(GameStorage.downloads(app), "app.apk").exists())
    }

    @Test fun sanitizeReplacesReservedCharsAndTrimsDots() {
        assertEquals("a_b_c__", BrowserDownloads.sanitize("a/b:c*?"))
        assertEquals("x", BrowserDownloads.sanitize("..x.."))
        assertEquals("ok", BrowserDownloads.sanitize("  ok  "))
    }

    @Test fun suggestedNameDecodesUtf8AndStripsSeparators() {
        assertEquals("a_b.zip", BrowserDownloads.suggestName("https://h.test/x", "attachment; filename*=UTF-8''a%2Fb.zip", null))
    }

    @Test fun unknownOrMissingExtensionIsOctetStream() {
        assertEquals("application/octet-stream", BrowserDownloads.mimeOf("noext"))
        assertEquals("application/octet-stream", BrowserDownloads.mimeOf("file.qqqzzz"))
    }

    @Test fun uniqueAddsCounterBeforeExtension() {
        val f = File(tmp.root, "a.zip")
        assertEquals(f, BrowserDownloads.unique(f))
        f.writeText("x")
        assertEquals(File(tmp.root, "a (1).zip"), BrowserDownloads.unique(f))
        File(tmp.root, "a (1).zip").writeText("y")
        assertEquals(File(tmp.root, "a (2).zip"), BrowserDownloads.unique(f))
        val readme = File(tmp.root, "README")
        readme.writeText("z")
        assertEquals(File(tmp.root, "README (1)"), BrowserDownloads.unique(readme))
    }

    @Test fun progressClampsAndActiveFollowsState() {
        val j = DlJob(1, "https://h.test/x", null, null, null, null)
        assertEquals(-1f, j.progress, 0f) // chưa biết cỡ file
        j.total = 200
        j.done = 50
        assertEquals(0.25f, j.progress, 0.0001f)
        j.done = 300
        assertEquals(1f, j.progress, 0f)
        assertTrue(j.active) // đang tải
        j.state = DlState.READY
        assertFalse(j.active) // tải xong, chưa xác nhận
        j.confirmed = true
        assertTrue(j.active) // đã xác nhận, chờ chuyển file
        j.state = DlState.SAVED
        assertFalse(j.active)
    }
}
