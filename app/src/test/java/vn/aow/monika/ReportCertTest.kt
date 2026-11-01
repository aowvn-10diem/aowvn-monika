package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp

/**
 * V155: báo cáo gửi kèm SHA-256 chứng thư ký APK để phân biệt bản chính thức với bản tự build.
 * Không đọc được chứng thư thì báo cáo vẫn giữ định dạng cũ và vẫn gửi được.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ReportCertTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun report() = Diagnostics.Report(
        id = 1L, time = 1L, kind = "java", title = "thử", app = "0.8.7 (52)", device = "máy thử",
    )

    @Test fun sha256HexMatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Diagnostics.sha256Hex("abc".toByteArray()),
        )
    }

    @Test fun certSha256IsEmptyOrSixtyFourLowercaseHex() {
        val value = Diagnostics.certSha256(app)
        assertTrue("giá trị: $value", value.isEmpty() || Regex("^[0-9a-f]{64}$").matches(value))
    }

    @Test @Config(sdk = [27]) fun readsLegacySignatureFromPackageManager() {
        val bytes = "chứng-thư-thử".toByteArray()
        val info = android.content.pm.PackageInfo().apply {
            packageName = app.packageName
            signatures = arrayOf(android.content.pm.Signature(bytes))
        }
        org.robolectric.Shadows.shadowOf(app.packageManager).installPackage(info)
        assertEquals(Diagnostics.sha256Hex(bytes), Diagnostics.certSha256(app))
    }

    @Test fun reportJsonCarriesCertWhenReadable() {
        val cert = Diagnostics.certSha256(app)
        val text = Diagnostics.reportJson(app, report())
        if (cert.isEmpty()) {
            assertFalse(text, text.contains("certSha256"))
        } else {
            assertTrue(text, text.contains("\"certSha256\":\"$cert\""))
        }
    }

    @Test fun reportWithoutContextKeepsOldJsonShape() {
        val text = Diagnostics.reportJson(null, report())
        assertFalse(text, text.contains("certSha256"))
    }

    @Test fun presetCertIsNotOverwritten() {
        val other = "f".repeat(64)
        val text = Diagnostics.reportJson(app, report().copy(certSha256 = other))
        assertTrue(text, text.contains("\"certSha256\":\"$other\""))
    }
}
