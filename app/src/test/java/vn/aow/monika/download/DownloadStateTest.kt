package vn.aow.monika.download

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStateTest {
    @Test fun pendingRunningAndPausedAreActive() {
        listOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED).forEach {
            assertTrue("status $it", DownloadState(1, it, 0, 0, 0).running)
        }
    }

    @Test fun successfulAndFailedAreNotActive() {
        listOf(DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED).forEach {
            assertFalse("status $it", DownloadState(1, it, 0, 0, 0).running)
        }
    }

    @Test fun progressIsFractionOrZeroWhenSizeUnknown() {
        assertEquals(0.5f, DownloadState(1, DownloadManager.STATUS_RUNNING, 50, 100, 0).progress, 0.0001f)
        assertEquals(0f, DownloadState(1, DownloadManager.STATUS_RUNNING, 50, 0, 0).progress, 0f)
    }
}
