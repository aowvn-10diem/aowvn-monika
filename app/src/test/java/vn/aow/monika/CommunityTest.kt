package vn.aow.monika

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.aow.monika.community.Community
import vn.aow.monika.community.CommunityLink.Kind

class CommunityTest {
    @Test fun extractsTranslatorLinks() {
        val html = """
            <p>Nhóm dịch: <a href="https://www.facebook.com/HisekuTeam/">Hiseku</a></p>
            <a href="https://discord.com/invite/NkyD2Cm">Discord</a> và https://discord.gg/abc-XYZ
            <a href="https://www.facebook.com/sharer/sharer.php?u=x">Chia sẻ</a>
            <a href="https://www.facebook.com/profile.php?id=100078129614039&amp;ref=1">Trang</a>
            <a href="https://www.facebook.com/HisekuTeam">trùng</a>
        """
        val links = Community.extract(html)
        assertEquals(listOf(Kind.DISCORD, Kind.DISCORD, Kind.FACEBOOK, Kind.FACEBOOK), links.map { it.kind })
        assertEquals("https://discord.com/invite/NkyD2Cm", links[0].url)
        assertEquals("https://www.facebook.com/HisekuTeam", links[2].url)
        assertEquals("https://www.facebook.com/profile.php?id=100078129614039", links[3].url)
    }

    @Test fun noLinks() = assertEquals(0, Community.extract("<p>Không có gì</p>").size)
}

class CommunityKindTest {
    @Test fun kinds() {
        assertEquals(Kind.FACEBOOK, Community.kindOf("https://www.facebook.com/groups/aowvn"))
        assertEquals(Kind.DISCORD, Community.kindOf("https://discord.gg/abc"))
        assertEquals(null, Community.kindOf("https://www.aow.vn/"))
    }
}
