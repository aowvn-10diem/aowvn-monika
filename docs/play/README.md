# Hồ sơ Google Play — nháp V156

**Trạng thái: nháp, chưa duyệt.** Soạn theo [A67] của sếp (26/10): tài khoản Play cá nhân, khóa ký hiện tại qua Play App Signing, bản Play ẩn các tính năng bị Play cấm. Các mục [CHƯA KIỂM] và [chờ sếp] phải được xử lý trước khi nộp hay công bố.

| File | Nội dung | Ngôn ngữ |
|---|---|---|
| `chinh-sach-rieng-tu.md` | Chính sách quyền riêng tư (sẽ công bố trên monika.aow.vn) | Tiếng Việt |
| `privacy-policy.md` | Privacy policy (bản tiếng Anh) | English |
| `data-safety.md` | Trả lời mục An toàn dữ liệu (Data safety) | Vi + En |
| `mo-ta-cua-hang.md` | Mô tả ngắn và mô tả dài trên cửa hàng | Vi + En |

## Quyết định cần sếp/PM trước khi nộp

1. **Đăng nhập Google (tùy chọn).** App lưu uid, tên, email, ảnh đại diện và token trên máy. Điều này **không khớp** với giả định "không thu thông tin cá nhân" trong [A67]. Cần chọn: giữ đăng nhập trong bản Play và khai đầy đủ, hay ẩn đăng nhập trong bản Play.
2. **Tự gửi báo lỗi đang bật mặc định.** `Prefs.autoSendCrash` mặc định `true`, và config `crash.autoSend` cũng `true`. Phải khai thu thập dữ liệu thiết bị và nhật ký. Cần chọn: giữ mặc định bật (có thông báo rõ), hay tắt mặc định trong bản Play.
3. **Ủng hộ qua chuyển khoản (VietQR).** Config có thông tin tài khoản nhận tiền. Play hạn chế thanh toán ngoài cho nội dung số. Kế hoạch đề xuất ẩn ở bản Play, chờ sếp quyết. Tài liệu này không ghi số tài khoản.
4. **Email hoặc kênh liên hệ chính thức** cho Play: [CHƯA KIỂM], chưa có trong repo.
5. **Ngày hiệu lực** và **thời hạn lưu báo lỗi trên máy chủ**: [chờ sếp].

## Đối chiếu mã đã làm (trên `origin/main` da1235dc)

- **Quyền trong manifest:** INTERNET, VIBRATE, POST_NOTIFICATIONS, REQUEST_INSTALL_PACKAGES, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, READ/WRITE_EXTERNAL_STORAGE. Không có quyền vị trí, danh bạ, máy ảnh hay micro.
- **SDK bên thứ ba:** không thấy SDK phân tích, quảng cáo hay báo lỗi trong `gradle/libs.versions.toml` và `app/build.gradle.kts`.
- **Báo lỗi:** gửi JSON POST tới `crash.endpoint` trong config (Cloudflare Worker của AowVN). Nội dung và bộ lọc xem `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt`. Bộ lọc thay đường dẫn riêng tư, token, khóa bí mật và email.
- **Đăng nhập:** `account/Account.kt`, dùng Firebase qua aow.vn và đổi token qua `securetoken.googleapis.com`.
- **Dịch màn hình:** `translate/ScreenTranslator.kt`. Chỉ chạy khi người dùng chọn nhà cung cấp và nhập khóa API. Dữ liệu không qua máy chủ AowVN.
- **Tải cấu hình:** `REMOTE_CONFIG_URL` trỏ tới Cloudflare Worker, không gửi thông tin cá nhân.

## Bản Play (V150) làm thay đổi gì

Bản Play bỏ tải game từ aow.vn, bỏ tự cập nhật APK, bỏ `REQUEST_INSTALL_PACKAGES` và có thể đổi quyền lưu trữ. Các tài liệu này viết cho cả hai bản. Khi V150 xong, cần đối chiếu lại mục quyền và danh sách hệ máy.
