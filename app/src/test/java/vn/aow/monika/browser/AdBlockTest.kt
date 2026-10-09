package vn.aow.monika.browser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AdBlockTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun adBlock() = AdBlock(app, OkHttpClient())

    @Test fun parseLineSkipsBlanksCommentsHeadersAndExceptions() {
        assertNull(AdBlock.parseLine(""))
        assertNull(AdBlock.parseLine("   "))
        assertNull(AdBlock.parseLine("# ghi chú"))
        assertNull(AdBlock.parseLine("! Title: danh sách"))
        assertNull(AdBlock.parseLine("[Adblock Plus 2.0]"))
        assertNull(AdBlock.parseLine("@@||ads.example.com^"))
    }

    @Test fun parseLineReadsDomainFromPlainHostsAndDomainRules() {
        assertEquals("ads.example.com", AdBlock.parseLine("ads.example.com"))
        assertEquals("ads.example.com", AdBlock.parseLine("  ADS.Example.COM  "))
        assertEquals("ads.example.com", AdBlock.parseLine("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", AdBlock.parseLine("127.0.0.1\tads.example.com"))
        assertEquals("ads.example.com", AdBlock.parseLine("||ads.example.com^"))
        assertEquals("ads.example.com", AdBlock.parseLine("||ads.example.com^\$third-party"))
        assertEquals("ads.example.com", AdBlock.parseLine("*.ads.example.com"))
        assertEquals("ads.example.com", AdBlock.parseLine("ads.example.com # ghi chú cuối dòng"))
    }

    @Test fun parseLineDropsRulesThatCannotBeBlockedByDomain() {
        assertNull(AdBlock.parseLine("||ads.example.com/banner.js"))
        assertNull(AdBlock.parseLine("||ads.example.com^\$image"))
        assertNull(AdBlock.parseLine("localhost"))
        assertNull(AdBlock.parseLine("0.0.0.0 localhost"))
        assertNull(AdBlock.parseLine("printer.local"))
        assertNull(AdBlock.parseLine("nodot"))
        assertNull(AdBlock.parseLine("example.com/path"))
        assertNull(AdBlock.parseLine("not a domain"))
    }

    @Test fun hashIsStableAndSeparatesDifferentNames() {
        assertEquals(AdBlock.hash("ads.example.com"), AdBlock.hash("ads.example.com"))
        assertTrue(AdBlock.hash("ads.example.com") != AdBlock.hash("ads.example.net"))
        assertTrue(AdBlock.hash("") != AdBlock.hash("a"))
    }

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

    @Test fun blocksDomainAndItsSubdomainsButNotSiblingsOrParents() {
        val ab = adBlock()
        ab.setDomains(listOf("ads.example.com"))
        assertTrue(ab.blocks("ads.example.com"))
        assertTrue(ab.blocks("cdn.ads.example.com"))
        assertTrue(ab.blocks("ADS.EXAMPLE.COM."))
        assertFalse(ab.blocks("example.com"))
        assertFalse(ab.blocks("news.example.com"))
        assertFalse(ab.blocks("ads.example.org"))
        assertFalse(ab.blocks(null))
    }

    @Test fun parentDomainBlocksAllItsSubdomains() {
        val ab = adBlock()
        ab.setDomains(listOf("example.com"))
        assertTrue(ab.blocks("example.com"))
        assertTrue(ab.blocks("a.b.example.com"))
        assertFalse(ab.blocks("example.org"))
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
