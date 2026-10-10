package vn.aow.monika.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.aow.monika.config.DownloadHost

class LinkResolverTest {
    private val hosts = listOf(
        DownloadHost("Drive", "drive.google.com", "browser"),
        DownloadHost("Pixeldrain", "pixeldrain.com", "direct", pattern = "pixeldrain\\.com/u/(\\w+)", directUrl = "https://pixeldrain.com/api/file/$1"),
    )

    @Test fun directHostBuildsDirectUrlFromCapturedGroup() {
        assertEquals(
            DownloadLink("Pixeldrain", "https://pixeldrain.com/u/AbC12", "https://pixeldrain.com/api/file/AbC12"),
            LinkResolver.resolve("https://pixeldrain.com/u/AbC12", hosts),
        )
    }

    @Test fun browserHostHasNoDirectUrl() {
        assertEquals(DownloadLink("Drive", "https://drive.google.com/file/d/1x", null), LinkResolver.resolve("https://drive.google.com/file/d/1x", hosts))
    }

    @Test fun wwwPrefixAndSubdomainStillMatch() {
        assertEquals(DownloadLink("Drive", "https://www.drive.google.com/file/d/1x", null), LinkResolver.resolve("https://www.drive.google.com/file/d/1x", hosts))
        assertEquals(
            DownloadLink("Pixeldrain", "https://cdn.pixeldrain.com/u/Zz9", "https://pixeldrain.com/api/file/Zz9"),
            LinkResolver.resolve("https://cdn.pixeldrain.com/u/Zz9", hosts),
        )
    }

    @Test fun lookalikeHostsAreNotMatched() {
        assertNull(LinkResolver.resolve("https://fakedrive.google.com/x", hosts))
        assertNull(LinkResolver.resolve("https://drive.google.com.evil.io/x", hosts))
    }

    @Test fun directHostWithoutMatchingPatternIsNull() {
        assertNull(LinkResolver.resolve("https://pixeldrain.com/l/AbC12", hosts))
    }

    @Test fun unknownOrInvalidUrlIsNull() {
        assertNull(LinkResolver.resolve("https://mega.nz/file/x", hosts))
        assertNull(LinkResolver.resolve("not a url", hosts))
    }

    @Test fun extractCleansLabelsDecodesEntitiesAndDropsDuplicatesAndUnknownHosts() {
        val html = """
            <a href="https://drive.google.com/file/d/1x">TẢI <b>VỀ</b>&nbsp;ngay &amp; nhanh</a>
            <a href="https://pixeldrain.com/u/AbC?a=1&amp;b=2">DỰ PHÒNG</a>
            <a href="https://drive.google.com/file/d/1x">Trùng</a>
            <a href="https://mega.nz/x">Mega</a>
        """.trimIndent()
        assertEquals(
            listOf(
                DownloadLink("Drive", "https://drive.google.com/file/d/1x", null, "TẢI VỀ ngay & nhanh"),
                DownloadLink("Pixeldrain", "https://pixeldrain.com/u/AbC?a=1&b=2", "https://pixeldrain.com/api/file/AbC", "DỰ PHÒNG"),
            ),
            LinkResolver.extract(html, hosts),
        )
    }

    @Test fun blogPageDetection() {
        assertTrue(LinkResolver.isBlogPage("https://aow.vn/p/x.html"))
        assertTrue(LinkResolver.isBlogPage("https://www.aow.vn/p/x.html"))
        assertFalse(LinkResolver.isBlogPage("https://www.aow.vn/2026/02/game.html"))
        assertFalse(LinkResolver.isBlogPage("https://aow.vn.evil.com/p/x"))
        assertFalse(LinkResolver.isBlogPage("not a url"))
    }
}
