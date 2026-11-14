package vn.aow.monika.security

/**
 * [A85/A78-1] Hằng biên dịch của chứng thư phát hành, **không** đọc từ config JSON.
 *
 * Trước đây app đối chiếu với `cfg.security.officialCertSha256` — trường này nằm trong
 * `assets/monika-config.json` nên người đóng gói lại APK chỉ cần sửa JSON là tắt được cảnh báo
 * hoặc tráo chứng thư của họ vào màn "bản chính thức". Giá trị dưới đây nằm trong DEX đã ký.
 */
object OfficialCertPin {
    /** SHA-256 chứng thư ký APK phát hành trên GitHub (trùng `EXPECTED_SHA256` của verify-apk-cert.py). */
    const val OFFICIAL_APK_CERT_SHA256 = "c46902e97029a96593f43c38abd9871170a7519d90992a0b5466c752d45ab20c"
}
