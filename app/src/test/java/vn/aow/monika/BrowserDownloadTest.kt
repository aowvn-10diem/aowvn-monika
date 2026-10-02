package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.download.BrowserDownloads
import vn.aow.monika.download.DlJob
import vn.aow.monika.download.DlState
import vn.aow.monika.download.SaveDest
import vn.aow.monika.library.GameStorage
import vn.aow.monika.ui.TestApp
import java.io.File

/** Trình quản lý tải của trình duyệt nhúng: tải ngay → chọn tên + nơi lưu → chuyển file đúng chỗ. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class BrowserDownloadTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val payload = ByteArray(300_000) { (it % 251).toByte() }

    private fun http() = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Disposition", "attachment; filename*=UTF-8''Game%20Vi%E1%BB%87t%20H%C3%B3a.zip")
            .body(payload.toResponseBody("application/zip".toMediaType())).build()
    }.build()

    private fun waitFor(job: DlJob, state: DlState) {
        repeat(200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (job.state == state) return
            Thread.sleep(25)
        }
        throw AssertionError("Trạng thái ${job.state}, mong đợi $state (${job.error})")
    }

    @Test fun downloadsImmediatelyThenMovesOnConfirm() {
        val mgr = BrowserDownloads(app, http(), Prefs(app))
        val job = mgr.start("https://host.test/f?id=1", "UA", null, null, "https://host.test/")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(mgr.sheetFor === job) // popup hiện ngay khi bắt đầu tải
        waitFor(job, DlState.READY)
        // File đã tải xong TRƯỚC khi người dùng chọn gì; tên lấy từ tiêu đề máy chủ (tiếng Việt).
        assertEquals("Game Việt Hóa.zip", job.name)
        assertEquals(payload.size.toLong(), job.part.length())
        // Đặt tên + chọn nơi lưu → chuyển sang thư mục Thư viện (đúng tên mới).
        mgr.confirm(job, "Tên mới: bản 1.zip", SaveDest.Library)
        assertTrue(job.confirmed)
        assertEquals("Ổn: tên bị làm sạch", "Tên mới_ bản 1.zip", job.name)
        val target = File(GameStorage.downloads(app), job.name)
        repeat(200) { shadowOf(android.os.Looper.getMainLooper()).idle(); if (target.exists() && !job.part.exists()) return@repeat; Thread.sleep(25) }
        assertTrue(target.exists())
        assertEquals(payload.size.toLong(), target.length())
        assertFalse(job.part.exists())
        assertEquals(SaveDest.Library, mgr.defaultDest())
    }

    @Test fun confirmBeforeFinishStillMovesWhenDone() {
        val mgr = BrowserDownloads(app, http(), Prefs(app))
        val job = mgr.start("https://host.test/g", null, null, null, null)
        mgr.confirm(job, "chon-truoc.bin", SaveDest.Library) // chọn trước khi tải xong
        val target = File(GameStorage.downloads(app), "chon-truoc.bin")
        repeat(800) { if (target.exists()) return@repeat; shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(25) } // chờ tối đa ~20 giây: toàn bộ bộ test chạy song song nên có lúc chậm
        assertTrue("state=${job.state} confirmed=${job.confirmed} err=${job.error} part=${job.part.exists()} dir=${target.parentFile?.list()?.toList()}", target.exists())
    }

    @Test fun cancelDeletesPartial() {
        val mgr = BrowserDownloads(app, http(), Prefs(app))
        val job = mgr.start("https://host.test/h", null, null, null, null)
        mgr.cancel(job)
        Thread.sleep(200)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(DlState.CANCELED, job.state)
        assertFalse(job.part.exists())
        assertTrue(mgr.jobs.isEmpty())
    }
}
