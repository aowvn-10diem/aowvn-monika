package vn.aow.monika.download

import android.app.DownloadManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.Prefs
import vn.aow.monika.ui.TestApp

/** [A85/V204-PR3] Chỉ xử lý lượt tải do chính app đặt, và dấu ID được dọn cùng lúc. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class DownloadReceiverGuardTest {
    private val complete = DownloadManager.ACTION_DOWNLOAD_COMPLETE

    @Test fun onlyOwnDownloadsAreHandled() {
        assertTrue(shouldHandleDownload(complete, 42, ownId = true))
        assertFalse("ID lạ do app khác gửi", shouldHandleDownload(complete, 42, ownId = false))
        assertFalse("sai action", shouldHandleDownload("android.intent.action.OTHER", 42, ownId = true))
        assertFalse("thiếu ID", shouldHandleDownload(complete, -1, ownId = true))
    }

    @Test fun prefsTracksOwnDownloadAndClearsIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = Prefs(app)
        assertFalse(prefs.isOwnDownload(7))
        prefs.markDownload(7)
        assertTrue(prefs.isOwnDownload(7))
        prefs.markToolDownload(7)
        prefs.setDownloadMeta(7, "{}")
        prefs.clearDownload(7)
        assertFalse(prefs.isOwnDownload(7))
        assertFalse(prefs.isToolDownload(7))
    }
}
