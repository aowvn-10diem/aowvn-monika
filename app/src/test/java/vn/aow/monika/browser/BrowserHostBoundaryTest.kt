package vn.aow.monika.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V204 PR2: so khớp tên miền mạng xã hội phải đúng ranh giới — "evilfacebook.com"
 * hoặc "facebook.com.evil.io" không được coi là Facebook.
 */
class BrowserHostBoundaryTest {
    @Test fun facebookHostsMatchOnlyRealDomains() {
        assertTrue(InAppBrowserActivity.isFacebookHost("facebook.com"))
        assertTrue(InAppBrowserActivity.isFacebookHost("m.facebook.com"))
        assertTrue(InAppBrowserActivity.isFacebookHost("www.fb.com"))
        assertTrue(InAppBrowserActivity.isFacebookHost("messenger.com"))
        assertFalse(InAppBrowserActivity.isFacebookHost("evilfacebook.com"))
        assertFalse(InAppBrowserActivity.isFacebookHost("facebook.com.evil.io"))
        assertFalse(InAppBrowserActivity.isFacebookHost(""))
    }

    @Test fun discordHostsMatchOnlyRealDomains() {
        assertTrue(InAppBrowserActivity.isDiscordHost("discord.com"))
        assertTrue(InAppBrowserActivity.isDiscordHost("canary.discord.com"))
        assertTrue(InAppBrowserActivity.isDiscordHost("discordapp.com"))
        assertFalse(InAppBrowserActivity.isDiscordHost("notdiscord.com"))
        assertFalse(InAppBrowserActivity.isDiscordHost("discord.com.evil.io"))
    }
}
