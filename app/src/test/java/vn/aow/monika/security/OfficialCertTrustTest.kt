package vn.aow.monika.security

import org.junit.Assert.assertEquals
import org.junit.Test

/** [A85/A78-3] Kết luận bản chính thức khi PackageManager và khối chữ ký APK lệch nhau. */
class OfficialCertTrustTest {
    private val official = "a".repeat(64)
    private val other = "b".repeat(64)

    @Test fun debugIsAlwaysOfficial() {
        assertEquals(OfficialCert.BuildTrust.OFFICIAL, OfficialCert.trustOf("", null, "", true))
    }

    @Test fun missingOfficialPinIsUnknown() {
        assertEquals(OfficialCert.BuildTrust.UNKNOWN, OfficialCert.trustOf(official, official, "", false))
    }

    @Test fun releaseWithoutReadableCertIsUnknown() {
        // Thiếu dữ liệu thì không buộc tội (màn Ủng hộ vẫn bị chặn riêng theo A78-2).
        assertEquals(OfficialCert.BuildTrust.UNKNOWN, OfficialCert.trustOf("", null, official, false))
    }

    @Test fun hookingDetectionComparesPackageManagerWithApkBlock() {
        // PackageManager trả chứng thư gốc nhưng APK trên đĩa ký bằng khoá khác → bản ngoại lai.
        assertEquals(OfficialCert.BuildTrust.FOREIGN, OfficialCert.trustOf(official, other, official, false))
        // Hai nguồn khớp nhau và khớp hằng phát hành.
        assertEquals(OfficialCert.BuildTrust.OFFICIAL, OfficialCert.trustOf(official.uppercase(), official, official, false))
        // Không đọc được khối chữ ký (null) thì chỉ so PackageManager.
        assertEquals(OfficialCert.BuildTrust.OFFICIAL, OfficialCert.trustOf(official, null, official, false))
        assertEquals(OfficialCert.BuildTrust.FOREIGN, OfficialCert.trustOf(other, null, official, false))
    }
}
