package vn.aow.monika.browser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.DataOutputStream
import java.io.File

// Nạp bộ lọc đã lưu trong filesDir. Danh sách URL rỗng nên không bao giờ gọi download (không mạng).
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AdBlockStoreTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun clearSavedLists() {
        File(app.filesDir, "adblock").deleteRecursively()
    }

    private fun writeSavedLists() {
        val dir = File(app.filesDir, "adblock").apply { mkdirs() }
        val hashes = listOf(AdBlock.hash("ads.example.com"), AdBlock.hash("track.example.net")).sorted()
        DataOutputStream(File(dir, "domains.bin").outputStream().buffered()).use { out ->
            out.writeInt(hashes.size)
            hashes.forEach(out::writeLong)
        }
        File(dir, "rules.txt").writeText("||tracker.example.org^\n")
    }

    @Test fun ensureLoadsSavedDomainsAndRulesWithoutDownloading() {
        writeSavedLists()
        val ab = AdBlock(app, OkHttpClient())
        assertTrue(runBlocking { ab.ensure(emptyList(), 24) })
        assertTrue(ab.ready)
        assertTrue(ab.blocks("cdn.ads.example.com"))
        assertFalse(ab.blocks("news.vn"))
        assertTrue(ab.blocksRequest("https://cdn.tracker.example.org/x.js", "cdn.tracker.example.org", "news.vn", FilterEngine.SCRIPT))
    }

    @Test fun ensureReportsNotReadyWhenNothingIsSavedAndNoListsGiven() {
        val ab = AdBlock(app, OkHttpClient())
        assertFalse(runBlocking { ab.ensure(emptyList(), 24) })
        assertFalse(ab.ready)
    }
}
