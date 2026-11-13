package vn.aow.monika.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [A85/A78-2] Chỉ bản đúng chứng thư phát hành mới được vẽ QR/STK ủng hộ. */
class OfficialCertDonationTest {
    private val official = OfficialCertPin.OFFICIAL_APK_CERT_SHA256.ifBlank { "a".repeat(64) }

    @Test fun debugBuildAlwaysShowsDonation() {
        assertTrue(OfficialCert.donationAllowed("", "", true))
        assertTrue(OfficialCert.donationAllowed("b".repeat(64), official, true))
    }

    @Test fun releaseWithMatchingCertShowsDonation() {
        assertTrue(OfficialCert.donationAllowed(official.uppercase(), official, false))
    }

    @Test fun releaseWithForeignOrMissingCertHidesDonation() {
        assertFalse(OfficialCert.donationAllowed("b".repeat(64), official, false))
        // Không đọc được chứng thư (rỗng) trên bản release cũng không được vẽ QR.
        assertFalse(OfficialCert.donationAllowed("", official, false))
        // Chưa cấu hình chứng thư chính thức (bản Play) cũng không vẽ QR.
        assertFalse(OfficialCert.donationAllowed(official, "", false))
    }
}
