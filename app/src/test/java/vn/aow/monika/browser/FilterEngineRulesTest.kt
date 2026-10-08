package vn.aow.monika.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineRulesTest {
    @Test fun registrableKeepsTwoLabelsAndCountrySuffixes() {
        assertEquals("news.vn", FilterEngine.registrable("news.vn"))
        assertEquals("news.vn", FilterEngine.registrable("m.news.vn"))
        assertEquals("bbc.co.uk", FilterEngine.registrable("a.b.bbc.co.uk"))
        assertEquals("example.com", FilterEngine.registrable("WWW.Example.COM."))
        assertEquals("localhost", FilterEngine.registrable("localhost"))
    }

    @Test fun skipsCommentsHeadersBlanksRegexAndUnknownOptions() {
        val fe = FilterEngine()
        listOf("! Title: x", "[Adblock Plus 2.0]", "   ", "/ads[0-9]+/", "||x.com^\$popup").forEach { assertFalse(it, fe.addLine(it)) }
        assertEquals(0, fe.networkRules)
        assertEquals(0, fe.cosmeticRules)
    }

    @Test fun noRulesBlocksNothing() {
        assertFalse(FilterEngine().blocks("https://ads.example.com/x", "ads.example.com", "news.vn", 0))
    }

    @Test fun hostRuleBlocksThatHostAndSubdomainsOnly() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("||ads.example.com^"))
        assertTrue(fe.blocks("https://ads.example.com/banner.js", "ads.example.com", "news.vn", FilterEngine.SCRIPT))
        assertFalse(fe.blocks("https://example.com/", "example.com", "example.com", FilterEngine.SCRIPT))
    }

    @Test fun thirdPartyOptionOnlyAppliesAcrossSites() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("||tracker.io^\$third-party"))
        assertTrue(fe.blocks("https://tracker.io/p.js", "tracker.io", "news.vn", FilterEngine.SCRIPT))
        assertFalse(fe.blocks("https://tracker.io/p.js", "tracker.io", "tracker.io", FilterEngine.SCRIPT))
    }

    @Test fun typeOptionNeedsKnownMatchingType() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("/banner.gif\$image"))
        val url = "https://cdn.x.com/banner.gif"
        assertTrue(fe.blocks(url, "cdn.x.com", "news.vn", FilterEngine.IMAGE))
        assertFalse(fe.blocks(url, "cdn.x.com", "news.vn", FilterEngine.STYLE))
        assertFalse(fe.blocks(url, "cdn.x.com", "news.vn", 0))
    }

    @Test fun domainOptionIncludesAndExcludesPages() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("/ads/*\$domain=news.vn|~sub.news.vn"))
        val url = "https://img.cdn.net/ads/1.png"
        assertTrue(fe.blocks(url, "img.cdn.net", "news.vn", 0))
        assertTrue(fe.blocks(url, "img.cdn.net", "m.news.vn", 0))
        assertFalse(fe.blocks(url, "img.cdn.net", "sub.news.vn", 0))
        assertFalse(fe.blocks(url, "img.cdn.net", "other.com", 0))
    }

    @Test fun exceptionRuleWinsOverBlockRule() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("||ads.example.com^"))
        assertTrue(fe.addLine("@@||ads.example.com/ok^"))
        assertFalse(fe.blocks("https://ads.example.com/ok/x", "ads.example.com", "news.vn", 0))
        assertTrue(fe.blocks("https://ads.example.com/bad", "ads.example.com", "news.vn", 0))
    }

    @Test fun plainPathRuleIsNotMistakenForRegex() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("/ads/"))
        assertTrue(fe.blocks("https://x.com/ads/a.js", "x.com", "news.vn", 0))
        assertFalse(fe.blocks("https://x.com/news/a.js", "x.com", "news.vn", 0))
    }

    @Test fun cosmeticRulesApplyPerSiteAndGenerically() {
        val fe = FilterEngine()
        assertTrue(fe.addLine("##.ad-box"))
        assertTrue(fe.addLine("news.vn##.banner"))
        assertFalse(fe.addLine("~foo.com##.x"))
        assertFalse(fe.addLine("example.com#@#.ad"))
        assertFalse(fe.addLine("##div:has-text(Ad)"))
        assertFalse(fe.addLine("##.x{color:red}"))
        assertEquals(2, fe.cosmeticRules)
        val css = fe.cosmeticCss("m.news.vn", listOf(".extra"))
        assertTrue(css.contains(".banner{display:none!important}"))
        assertTrue(css.contains(".extra{display:none!important}"))
        assertTrue(css.contains(".ad-box{display:none!important}"))
        assertFalse(fe.cosmeticCss("other.com").contains(".banner"))
    }
}
