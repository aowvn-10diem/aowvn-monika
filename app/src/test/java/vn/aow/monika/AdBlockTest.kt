package vn.aow.monika

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import vn.aow.monika.browser.AdBlock
import vn.aow.monika.ui.TestApp

class AdBlockParseTest {
    @Test fun formats() {
        assertEquals("ads.example.com", AdBlock.parseLine("ads.example.com"))
        assertEquals("eclick.vn", AdBlock.parseLine("0.0.0.0 eclick.vn"))
        assertEquals("admicro.vn", AdBlock.parseLine("127.0.0.1   admicro.vn"))
        assertEquals("track.x.io", AdBlock.parseLine("||track.x.io^"))
        assertEquals("track.x.io", AdBlock.parseLine("||track.x.io^\$third-party"))
        assertEquals("ad.x.com", AdBlock.parseLine("*.ad.x.com"))
        assertEquals("upper.com", AdBlock.parseLine("  UPPER.com  "))
    }

    @Test fun skips() {
        listOf("# chú thích", "! chú thích", "[Adblock Plus 2.0]", "@@||good.com^", "||x.com/path^", "||x.com^\$script",
            "0.0.0.0 localhost", "com", "", "example.com##.banner").forEach { assertNull(it, AdBlock.parseLine(it)) }
    }

    @Test fun moreFormats() {
        assertEquals("ads.x.com", AdBlock.parseLine("ads.x.com # quảng cáo"))
        assertEquals("ad.x.com", AdBlock.parseLine(":: ad.x.com"))
        assertEquals("ad.x.com", AdBlock.parseLine("0.0.0.0\tad.x.com"))
    }

    @Test fun rejectsLocalAndMultiTokenLines() {
        listOf("printer.local", "ads.x.com tracker.y.com", "||ads.x.com/banner^", "||ads.x.com^\$image", "::1 ip6-localhost", "ads..com")
            .forEach { assertNull(it, AdBlock.parseLine(it)) }
    }

    @Test fun hashIsFnv1a64() {
        assertEquals(0xcbf29ce484222325UL.toLong(), AdBlock.hash(""))
        assertNotEquals(AdBlock.hash("a"), AdBlock.hash("b"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AdBlockMatchTest {
    @Test fun matchesSubdomains() {
        val ab = AdBlock(RuntimeEnvironment.getApplication(), OkHttpClient())
        ab.setDomains(listOf("doubleclick.net", "eclick.vn"))
        assertTrue(ab.blocks("doubleclick.net"))
        assertTrue(ab.blocks("stats.g.doubleclick.net"))
        assertTrue(ab.blocks("EClick.VN."))
        assertFalse(ab.blocks("notdoubleclick.net"))
        assertFalse(ab.blocks("discord.com"))
        assertFalse(ab.blocks(null))
    }
}
