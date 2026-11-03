# Chính sách bảo mật

> **English summary:** Please report security vulnerabilities **privately** through GitHub's **Report a vulnerability** button (Security tab of this repository). Do **not** open a public issue. In scope: the official AowVN Monika app, its Worker, and the monika.aow.vn site. Out of scope: forks and fake builds, games, third-party emulator cores. Only the latest stable release is supported. Response time: [CHƯA KIỂM — to be set by the project owner].

## 1. Báo lỗ hổng bảo mật như thế nào

- Vào tab **Security** của repo này và bấm **Report a vulnerability** (GitHub sẽ mở một báo cáo **riêng tư**, chỉ chủ dự án thấy). Đường dẫn trực tiếp: <https://github.com/aowvn-10diem/aowvn-monika/security/advisories/new>.
  - [CHƯA KIỂM] Nút này chỉ hiện khi chủ repo đã bật *Private vulnerability reporting* ở Settings → Code security. Nếu bạn không thấy nút, hãy nhắn qua cộng đồng tại <https://www.aow.vn> rằng bạn có báo cáo bảo mật (**không** đăng chi tiết lỗi) để chủ dự án bật hoặc cho kênh khác.
- **Không mở issue công khai** và không đăng chi tiết lỗ hổng lên diễn đàn, nhóm chat hay mạng xã hội trước khi lỗi được sửa.
- Hiện dự án **chưa công bố email** nhận báo cáo bảo mật.

Nội dung báo cáo nên có: mô tả lỗi, các bước tái hiện, phiên bản app bị ảnh hưởng (xem trong Cài đặt), thiết bị và Android, và ảnh hưởng bạn cho là có. Đừng gửi khóa, mật khẩu hay dữ liệu cá nhân của người khác; không cần gửi game hay ROM.

## 2. Phạm vi

**Trong phạm vi:**

- App AowVN Monika **bản chính thức**. Thế nào là bản chính thức: xem [TRADEMARK.md](TRADEMARK.md) và trang giới thiệu <https://monika.aow.vn> (sẽ có trang "Bản chính thức" riêng, đang làm).
- Worker của dự án (dịch vụ cấu hình và nhận báo lỗi).
- Trang <https://monika.aow.vn>.

**Ngoài phạm vi:**

- Bản fork và bản giả mạo (báo bản giả theo [docs/BAO-BAN-GIA.md](docs/BAO-BAN-GIA.md), không phải báo lỗ hổng).
- Game, ROM và nội dung do người dùng tải về hay nhập vào.
- Lõi giả lập và thư viện bên thứ ba (hãy báo cho dự án gốc của chúng; nếu app dùng sai cách an toàn thì vẫn báo cho chúng tôi).
- Các dịch vụ bên ngoài như aow.vn (Blogger), diễn đàn, Pixeldrain và các nơi lưu trữ tệp khác.

## 3. Phiên bản được hỗ trợ

Chỉ **bản ổn định mới nhất** ở trang [Releases](https://github.com/aowvn-10diem/aowvn-monika/releases) được vá lỗi bảo mật. Bản thử nghiệm (prerelease) chỉ để thử, và các bản cũ hơn không được vá riêng: hãy cập nhật lên bản mới.

## 4. Thời gian phản hồi

Thời gian dự kiến phản hồi và sửa lỗi: [CHƯA KIỂM — chủ dự án chốt]. Cho tới khi có cam kết này, dự án sẽ cố gắng phản hồi sớm nhưng chưa hứa thời hạn.

## 5. Mong đợi từ người báo

- Chỉ kiểm thử trên thiết bị và tài khoản của chính bạn.
- Không truy cập, sửa hay xóa dữ liệu của người dùng khác; không làm sập dịch vụ.
- Cho dự án một khoảng thời gian hợp lý để sửa trước khi công khai.

[CHƯA KIỂM pháp lý] Văn bản này là hướng dẫn của dự án, không phải cam kết pháp lý hay chương trình thưởng lỗi.
