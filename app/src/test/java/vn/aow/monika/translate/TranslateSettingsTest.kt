package vn.aow.monika.translate

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.apkinstall.repack.KeyWrap
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class TranslateSettingsTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    /** Mã hóa giả để kiểm: khóa lưu trên máy không được là nguyên văn. */
    private val reversing = object : KeyWrap {
        override fun wrap(plain: ByteArray) = plain.reversedArray()
        override fun unwrap(cipher: ByteArray) = cipher.reversedArray()
    }

    private fun rawPrefs() = app.getSharedPreferences("translate", Context.MODE_PRIVATE)

    @Test fun defaultsWhenNothingSaved() {
        val s = TranslateSettings(app, reversing)
        assertEquals("ai", s.provider)
        assertEquals("en", s.sourceLang)
        assertEquals(TranslateSettings.DEFAULT_BASE, s.baseUrl)
        assertEquals("gpt-4o-mini", s.model)
        assertFalse(s.vision)
        assertEquals("", s.apiKey)
    }

    @Test fun legacyMlkitProviderReadsAsAi() {
        val s = TranslateSettings(app, reversing)
        s.provider = "mlkit"
        assertEquals("ai", s.provider)
        s.provider = "google"
        assertEquals("google", s.provider)
    }

    @Test fun baseUrlAndModelAreTrimmed() {
        val s = TranslateSettings(app, reversing)
        s.baseUrl = "  https://api.test/v1/  "
        s.model = "  m-1  "
        assertEquals("https://api.test/v1", s.baseUrl)
        assertEquals("m-1", s.model)
    }

    @Test fun apiKeyIsStoredWrappedAndReadsBack() {
        val s = TranslateSettings(app, reversing)
        s.apiKey = "  SK-123  "
        assertEquals("SK-123", s.apiKey)
        // Lưu trong prefs là Base64 của bản đã bọc (đảo ngược), không phải nguyên văn.
        assertEquals(Base64.encodeToString("321-KS".toByteArray(), Base64.NO_WRAP), rawPrefs().getString("key", null))
        assertEquals("SK-123", TranslateSettings(app, reversing).apiKey)
    }

    @Test fun blankKeyClearsStoredValue() {
        val s = TranslateSettings(app, reversing)
        s.apiKey = "SK"
        s.apiKey = "   "
        assertEquals("", s.apiKey)
        assertNull(rawPrefs().getString("key", null))
    }
}
