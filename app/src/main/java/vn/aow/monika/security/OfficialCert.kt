package vn.aow.monika.security

/**
 * [A78] V154: so chứng thư đang ký APK với chứng thư chính thức để phát hiện bản bị đóng gói lại.
 *
 * **Chỉ cảnh báo, không chặn chạy**: GPLv3 cho phép người chơi tự build và dùng bản của mình.
 * Việc đọc chứng thư nằm ở [vn.aow.monika.diag.Diagnostics.certSha256]; ở đây chỉ có luật so sánh
 * (thuần Kotlin) để kiểm được bằng test, không cần máy thật.
 */
object OfficialCert {
    /**
     * true = cần hiện cảnh báo "không phải bản Monika chính thức".
     *
     * Bỏ qua kiểm (false) khi:
     * - bản debug ([debugBuild]): máy dev ký bằng khóa debug, không phải khóa phát hành;
     * - chưa có giá trị chính thức ([officialSha256] rỗng): vd. bản Play chưa cấu hình chứng thư Play App Signing;
     * - không đọc được chứng thư đang chạy ([actualSha256] rỗng): thiếu dữ kiện thì không buộc tội sai.
     */
    fun isForeignBuild(actualSha256: String, officialSha256: String, debugBuild: Boolean): Boolean {
        if (debugBuild) return false
        val official = officialSha256.trim()
        val actual = actualSha256.trim()
        if (official.isEmpty() || actual.isEmpty()) return false
        return !official.equals(actual, ignoreCase = true)
    }

    /**
     * [A85/A78-2] Màn Ủng hộ chỉ được vẽ VietQR/STK khi APK đúng chứng thư phát hành.
     *
     * Khác [isForeignBuild]: bản release mà **không đọc được** chứng thư (rỗng) hoặc chưa cấu hình
     * chứng thư chính thức đều **không** được vẽ thông tin nhận tiền — đây là bề mặt bị giả mạo nhiều nhất.
     * Bản debug vẫn hiện để dev kiểm giao diện.
     */
    fun donationAllowed(actualSha256: String, officialSha256: String, debugBuild: Boolean): Boolean {
        if (debugBuild) return true
        val official = officialSha256.trim()
        val actual = actualSha256.trim()
        if (official.isEmpty() || actual.isEmpty()) return false
        return official.equals(actual, ignoreCase = true)
    }

    /** Kết luận về bản đang chạy; `UNKNOWN` khi flavor chưa có chứng thư phát hành (bản Play). */
    enum class BuildTrust { OFFICIAL, FOREIGN, UNKNOWN }

    /**
     * [A85/A78-3] So chứng thư do `PackageManager` trả với chứng thư đọc trực tiếp từ khối ký của tệp APK.
     * Lệch nhau = dấu hiệu `PackageManager` bị hook (Signature Killer) → coi là bản ngoại lai.
     * Bản release không đọc được chứng thư cũng coi là ngoại lai (không có bằng chứng là bản chính thức).
     */
    fun trustOf(
        pmSha256: String,
        apkBlockSha256: String?,
        officialSha256: String,
        debugBuild: Boolean,
    ): BuildTrust {
        if (debugBuild) return BuildTrust.OFFICIAL
        val official = officialSha256.trim()
        if (official.isEmpty()) return BuildTrust.UNKNOWN
        val pm = pmSha256.trim()
        if (pm.isEmpty()) return BuildTrust.FOREIGN
        if (apkBlockSha256 != null && !apkBlockSha256.equals(pm, ignoreCase = true)) return BuildTrust.FOREIGN
        return if (pm.equals(official, ignoreCase = true)) BuildTrust.OFFICIAL else BuildTrust.FOREIGN
    }

    /** Đọc chứng thư từ `PackageManager` và từ khối chữ ký của APK trên đĩa rồi kết luận. */
    fun verifyOfficialApk(
        context: android.content.Context,
        officialSha256: String = OfficialCertPin.OFFICIAL_APK_CERT_SHA256,
        debugBuild: Boolean = vn.aow.monika.BuildConfig.DEBUG,
    ): BuildTrust {
        val pm = vn.aow.monika.diag.Diagnostics.certSha256(context)
        val block = context.applicationInfo?.sourceDir
            ?.let { ApkSigningBlockReader.signingCertSha256(java.io.File(it)) }
        return trustOf(pm, block, officialSha256, debugBuild)
    }
}
