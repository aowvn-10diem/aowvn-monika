package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import vn.aow.monika.browser.FilterEngine
import java.io.File

class FilterEngineTest {
    private fun engine(vararg rules: String) = FilterEngine().also { e -> rules.forEach { e.addLine(it) } }

    @Test fun pathRulesAndAnchors() {
        val e = engine("/ads/*.gif", "||tracker.example.com/pixel^", "|https://evil.test/x|", ".vn/qc/", "/banner-ads.")
        assertTrue(e.blocks("https://site.com/ads/top.gif", "site.com", "site.com", 0))
        assertFalse(e.blocks("https://site.com/ads/top.png", "site.com", "site.com", 0))
        assertTrue(e.blocks("https://a.tracker.example.com/pixel?x=1", "a.tracker.example.com", "site.com", 0))
        assertFalse(e.blocks("https://a.tracker.example.com/pixels", "a.tracker.example.com", "site.com", 0))
        assertTrue(e.blocks("https://evil.test/x", "evil.test", "site.com", 0))
        assertFalse(e.blocks("https://evil.test/x/y", "evil.test", "site.com", 0))
        assertTrue(e.blocks("https://news.vn/qc/banner1", "news.vn", "news.vn", 0))
        assertTrue(e.blocks("https://x.com/js/banner-ads.min.js", "x.com", "x.com", 0))
    }

    @Test fun optionsThirdPartyTypeDomainAndException() {
        val e = engine(
            "||ads.example.com^\$third-party",
            "/cpc/\$script",
            "||promo.test/img/\$domain=news.vn|~vip.news.vn",
            "@@||ads.example.com/ok^",
        )
        // Bên thứ ba: chặn khi trang khác, không chặn khi cùng trang.
        assertTrue(e.blocks("https://ads.example.com/a", "ads.example.com", "other.com", 0))
        assertFalse(e.blocks("https://ads.example.com/a", "ads.example.com", "example.com", 0))
        // Ngoại lệ @@ thắng.
        assertFalse(e.blocks("https://ads.example.com/ok", "ads.example.com", "other.com", 0))
        // Ràng buộc loại tài nguyên: chỉ chặn script; không rõ loại thì không chặn (tránh chặn nhầm).
        assertTrue(e.blocks("https://x.com/cpc/a.js", "x.com", "y.com", FilterEngine.SCRIPT))
        assertFalse(e.blocks("https://x.com/cpc/a.js", "x.com", "y.com", FilterEngine.IMAGE))
        assertFalse(e.blocks("https://x.com/cpc/a.js", "x.com", "y.com", 0))
        // Giới hạn theo trang.
        assertTrue(e.blocks("https://promo.test/img/a.png", "promo.test", "news.vn", 0))
        assertFalse(e.blocks("https://promo.test/img/a.png", "promo.test", "vip.news.vn", 0))
        assertFalse(e.blocks("https://promo.test/img/a.png", "promo.test", "other.com", 0))
    }

    @Test fun unsupportedRulesAreSkippedNotMisapplied() {
        val e = FilterEngine()
        assertFalse(e.addLine("/^https:\\/\\/[a-z]{3,}\\.com\\/\$/\$xmlhttprequest,third-party")) // regex
        assertFalse(e.addLine("||x.com^\$popup"))
        assertFalse(e.addLine("||x.com^\$redirect=noopjs"))
        assertFalse(e.addLine("! chú thích"))
        assertEquals(0, e.networkRules)
    }

    @Test fun cosmeticSiteAndGeneric() {
        val e = engine("##.adsbygoogle", "mediafire.com##.ad-slot", "a.com,b.com###banner", "x.com##div:has-text(quảng cáo)", "x.com#@#.ok")
        val css = e.cosmeticCss("download.mediafire.com")
        assertTrue(".adsbygoogle{display:none!important}" in css)
        assertTrue(".ad-slot{display:none!important}" in css)
        assertFalse("#banner" in css) // chỉ áp cho a.com, b.com
        assertTrue("#banner{display:none!important}" in e.cosmeticCss("b.com"))
        assertFalse("has-text" in e.cosmeticCss("x.com")) // luật thủ tục không hỗ trợ bị bỏ
    }

    @Test fun thirdPartyRegistrable() {
        assertEquals("example.com", FilterEngine.registrable("a.b.example.com"))
        assertEquals("news.com.vn", FilterEngine.registrable("m.news.com.vn"))
    }

    /** Kiểm trên bộ lọc thật nếu máy dev có tải sẵn (không chạy trên CI). */
    @Test fun realListsSmoke() {
        val lists = listOf("/tmp/abpvn_ub.txt", "/tmp/easylist.txt").map(::File).filter { it.exists() }
        assumeTrue(lists.isNotEmpty())
        val e = FilterEngine()
        lists.forEach { f -> f.forEachLine { e.addLine(it) } }
        println("network=${e.networkRules} cosmetic=${e.cosmeticRules}")
        assertTrue(e.networkRules > 1000)
        val t0 = System.nanoTime()
        repeat(2000) { assertFalse(e.blocks("https://cdn.example.org/assets/app.bundle.js?v=$it", "cdn.example.org", "example.org", FilterEngine.SCRIPT)) }
        println("2000 tra cứu: ${(System.nanoTime() - t0) / 1_000_000} ms")
        assertTrue(e.blocks("https://securepubads.g.doubleclick.net/gampad/ads?iu=/1/x", "securepubads.g.doubleclick.net", "news.com", 0) ||
            e.blocks("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "pagead2.googlesyndication.com", "news.com", FilterEngine.SCRIPT))
    }
}
