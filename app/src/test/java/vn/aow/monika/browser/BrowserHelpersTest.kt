package vn.aow.monika.browser

import android.content.Intent
import android.net.Uri
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
class BrowserHelpersTest {
    @Test fun searchUrlUsesFacebookOnFacebookPages() {
        assertEquals("https://m.facebook.com/search/top/?q=nh%C3%B3m%20game", InAppBrowserActivity.searchUrl("m.facebook.com", "  nhóm game "))
    }

    @Test fun searchUrlFallsBackToGoogleAndSkipsBlankQuery() {
        assertEquals("https://www.google.com/search?q=mario%20bros", InAppBrowserActivity.searchUrl("news.vn", "mario bros"))
        assertNull(InAppBrowserActivity.searchUrl("news.vn", "   "))
    }

    @Test fun facebookHostsAndApps() {
        assertTrue(InAppBrowserActivity.isFacebookHost("m.facebook.com"))
        assertTrue(InAppBrowserActivity.isFacebookHost("fb.com"))
        assertTrue(InAppBrowserActivity.isFacebookHost("www.messenger.com"))
        assertFalse(InAppBrowserActivity.isFacebookHost("news.vn"))

        assertTrue(InAppBrowserActivity.isFacebookApp(Uri.parse("fb://profile/1"), Intent()))
        assertTrue(InAppBrowserActivity.isFacebookApp(Uri.parse("messenger://x"), Intent()))
        assertTrue(InAppBrowserActivity.isFacebookApp(Uri.parse("https://example.com"), Intent().setPackage("com.facebook.katana")))
        assertTrue(InAppBrowserActivity.isFacebookApp(Uri.parse("https://example.com"), Intent(Intent.ACTION_VIEW, Uri.parse("https://m.fb.com/x"))))
        assertTrue(InAppBrowserActivity.isFacebookApp(Uri.parse("https://go.example.com/?to=com.facebook.orca"), Intent()))
        assertFalse(InAppBrowserActivity.isFacebookApp(Uri.parse("https://example.com/x"), Intent()))
    }

    @Test fun allowlistMatchesHostAndSubdomainsOnly() {
        val allow = listOf("news.vn", "cdn.test")
        assertTrue(InAppBrowserActivity.isAllowedHost("news.vn", allow))
        assertTrue(InAppBrowserActivity.isAllowedHost("m.news.vn", allow))
        assertFalse(InAppBrowserActivity.isAllowedHost("fakenews.vn", allow))
        assertFalse(InAppBrowserActivity.isAllowedHost("other.com", allow))
        assertFalse(InAppBrowserActivity.isAllowedHost("news.vn", emptyList()))
    }

    @Test fun resourceTypeFromAcceptAndPath() {
        assertEquals(FilterEngine.STYLE, InAppBrowserActivity.resourceType("/a/b.css", "", false))
        assertEquals(FilterEngine.STYLE, InAppBrowserActivity.resourceType("/x.css", "image/png", false))
        assertEquals(FilterEngine.SCRIPT, InAppBrowserActivity.resourceType("/app.mjs", "", false))
        assertEquals(0, InAppBrowserActivity.resourceType("/", "text/html", true))
        assertEquals(FilterEngine.SUBDOC, InAppBrowserActivity.resourceType("/frame", "text/html", false))
        assertEquals(FilterEngine.IMAGE, InAppBrowserActivity.resourceType("/logo.png", "", false))
        assertEquals(FilterEngine.IMAGE, InAppBrowserActivity.resourceType("/img", "image/webp", false))
        assertEquals(FilterEngine.FONT, InAppBrowserActivity.resourceType("/f/icon.woff2", "", false))
        assertEquals(FilterEngine.MEDIA, InAppBrowserActivity.resourceType("/v.m3u8", "", false))
        assertEquals(FilterEngine.MEDIA, InAppBrowserActivity.resourceType("/stream", "audio/mpeg", false))
        assertEquals(0, InAppBrowserActivity.resourceType("/api/data", "application/json", false))
    }
}
