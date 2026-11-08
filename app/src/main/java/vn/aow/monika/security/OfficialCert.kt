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
}
