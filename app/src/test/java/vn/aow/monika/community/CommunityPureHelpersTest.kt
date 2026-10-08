package vn.aow.monika.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CommunityPureHelpersTest {
    @Test fun shortNameHandlesGroupInviteAndPageIdLinks() {
        assertEquals(
            "monika-translators",
            Community.shortName(CommunityLink(CommunityLink.Kind.FACEBOOK, "https://www.facebook.com/groups/monika-translators")),
        )
        assertEquals(
            "abc-XYZ",
            Community.shortName(CommunityLink(CommunityLink.Kind.DISCORD, "https://discord.gg/abc-XYZ")),
        )
        assertEquals(
            "Trang Facebook",
            Community.shortName(CommunityLink(CommunityLink.Kind.FACEBOOK, "https://facebook.com/profile.php?id=12345")),
        )
        assertEquals("Facebook", Community.shortName(CommunityLink(CommunityLink.Kind.FACEBOOK, "https://facebook.com/")))
    }

    @Test fun extractsDistinctLinksAndRejectsShareAndNonCommunityUrls() {
        val html = """
            https://m.facebook.com/team-a/, https://fb.com/team-b
            https://discord.com/invite/fixture-1 https://DISCORD.COM/invite/fixture-1
            https://facebook.com/share.php?x=1 https://example.invalid/community
        """.trimIndent()
        val links = Community.extract(html)
        assertEquals(3, links.size)
        assertEquals(CommunityLink.Kind.DISCORD, links.first().kind)
        assertEquals(CommunityLink.Kind.FACEBOOK, links[1].kind)
        assertEquals("team-a", Community.shortName(links[1]))
        assertNull(Community.kindOf("https://example.invalid/"))
    }
}
