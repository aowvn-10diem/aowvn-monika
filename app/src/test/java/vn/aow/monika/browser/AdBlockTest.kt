package vn.aow.monika.browser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

// Chỉ phần chưa được AdBlockParseTest và AdBlockMatchTest (vn.aow.monika) phủ.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AdBlockTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun adBlock() = AdBlock(app, OkHttpClient())

    @Test fun nothingIsBlockedBeforeListsAreLoaded() {
        val ab = adBlock()
        assertFalse(ab.ready)
        assertEquals(0, ab.size)
        assertFalse(ab.blocks("ads.example.com"))
    }

    @Test fun setDomainsKeepsOneEntryPerNameAndMarksListReady() {
        val ab = adBlock()
        ab.setDomains(listOf("ads.example.com", "ads.example.com", "track.example.net"))
        assertTrue(ab.ready)
        assertEquals(2, ab.size)
    }

    @Test fun topLevelDomainAloneNeverBlocksWholeSites() {
        val ab = adBlock()
        ab.setDomains(listOf("com"))
        assertFalse(ab.blocks("example.com"))
    }

    @Test fun blocksRequestUsesDomainListForNetworkRequests() {
        val ab = adBlock()
        ab.setDomains(listOf("ads.example.com"))
        assertTrue(ab.blocksRequest("https://cdn.ads.example.com/x.js", "cdn.ads.example.com", "news.vn", FilterEngine.SCRIPT))
        assertFalse(ab.blocksRequest("https://news.vn/a.js", "news.vn", "news.vn", FilterEngine.SCRIPT))
    }
}
