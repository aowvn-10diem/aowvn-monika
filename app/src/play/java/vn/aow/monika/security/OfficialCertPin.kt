package vn.aow.monika.security

/**
 * [A85/A78-1] Hằng biên dịch của chứng thư phát hành cho bản Play.
 *
 * Bản Play dùng Play App Signing: chứng thư thật do Google giữ và chỉ lấy được trong Play Console
 * → App integrity. Để rỗng cho tới khi chủ dự án cấp giá trị; khi rỗng thì màn cảnh báo không kết luận
 * (tránh báo sai trên bản Play hợp lệ), nhưng màn hỗ trợ dự án không tồn tại ở flavor này.
 */
object OfficialCertPin {
    const val OFFICIAL_APK_CERT_SHA256 = ""
}
