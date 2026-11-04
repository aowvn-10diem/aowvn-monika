# Thương hiệu AowVN / Monika

> **[CHƯA KIỂM pháp lý]** Văn bản này là chính sách của dự án, chưa qua luật sư. Chủ dự án đọc và chỉnh trước khi coi là chính thức.

## Tóm tắt một dòng

Mã nguồn thì **ai cũng được dùng, sửa, phát hành lại** theo GPL-3.0. Nhưng **tên, logo, icon và danh nghĩa "bản chính thức" thì không**: bản của bạn phải đổi tên và đổi icon.

## 1. Bạn được làm gì (theo GPL-3.0)

- Tải mã nguồn, đọc, sửa, tự build, chia sẻ lại, miễn là giữ giấy phép GPL-3.0 và công khai mã nguồn bản sửa (xem [LICENSE](LICENSE)).
- Ghi công trung thực, ví dụ: *"Dựa trên mã nguồn mở AowVN Monika"*, kèm đường dẫn tới repo này. Câu này chỉ để ghi nguồn, không được hiểu là bản của bạn do AowVN làm hay bảo trợ.

## 2. Bạn không được làm gì

- Dùng tên **"AowVN"**, **"Monika"**, **"Aow Monika"** (hoặc tên na ná dễ nhầm) làm tên app, tên cửa hàng, tên kênh phân phối của bản của bạn.
- Dùng **logo và icon** của AowVN Monika (kể cả ảnh chụp màn hình có logo, banner, ảnh nền trang chủ do dự án làm) cho bản của bạn.
- Tự nhận là **bản chính thức**, bản "được AowVN chấp thuận", hoặc gắn thông tin liên hệ, link tải, link ủng hộ để người dùng tưởng là của dự án.
- Dùng chung tên gói `com.aow.monika` cho bản của bạn. Hãy đặt tên gói riêng để máy người dùng không nhầm hai app là một.

GPL-3.0 không cấp quyền dùng nhãn hiệu, tên và logo; phần này dự án giữ lại. [CHƯA KIỂM pháp lý: căn cứ điều khoản về nhãn hiệu trong GPL-3.0 mục 7.]

## 3. Bản của bạn cần đổi gì

| Việc | Ghi chú |
|---|---|
| Đổi tên hiển thị của app | Không chứa "AowVN", "Monika". |
| Đổi icon và ảnh giới thiệu | Tự vẽ hoặc dùng ảnh bạn có quyền. |
| Đổi tên gói | Khác `com.aow.monika`. |
| Ký bằng khóa của bạn | Không dùng khóa ký của bản chính thức; bản tự build sẽ ký bằng khóa khác nên không cài đè được bản chính thức. |
| Không trỏ link tải, link ủng hộ, link báo lỗi về nơi của dự án | Dùng địa chỉ của bạn. |
| Giữ nguyên dòng ghi nguồn và giấy phép | Cả `LICENSE` và thông báo bản quyền. |

## 4. Kênh chính thức

Bản chính thức (APK) **chỉ** tải được ở hai nơi:

- Trang Releases của repo này: <https://github.com/aowvn-10diem/aowvn-monika/releases>
- Trang giới thiệu: <https://monika.aow.vn>

Nơi nào khác (kênh chat, trang tải, cửa hàng ứng dụng khác) cho tải "AowVN Monika" đều **không** phải kênh chính thức, trừ khi hai trang trên ghi rõ.

**Kênh cộng đồng (không phải nơi tải APK):** <https://www.aow.vn> là nơi đọc tin, hướng dẫn và trao đổi. Đừng tải APK từ bài đăng hay đường dẫn ở đó nếu nó không dẫn về hai trang chính thức ở trên.

## 5. Cách nhận biết bản chính thức

- Tải từ trang Releases. Mỗi bản có tệp `SHA256SUMS-vX.Y.Z.txt`; so SHA-256 của APK với tệp này.
- Ai rành kỹ thuật có thể xem chứng thư ký APK: mọi bản chính thức dùng cùng một khóa, bản giả sẽ khác. Dấu vân tay chứng thư chính thức do chủ dự án công bố (dự kiến ở trang "Bản chính thức" trên monika.aow.vn, đang làm: V183); **không** tin dấu vân tay do chỗ tải lạ đưa ra.
- Bản chính thức không bao giờ hỏi mật khẩu tài khoản ngân hàng hay mã OTP.

## 6. Thấy bản giả thì làm gì

1. Đừng cài; nếu lỡ cài thì gỡ ra và đổi mật khẩu các tài khoản đã đăng nhập trong đó.
2. Lưu lại link, ảnh chụp màn hình, tên gói, SHA-256 của APK.
3. Báo cho cộng đồng tại <https://www.aow.vn>. Chủ dự án dùng các mẫu trong [docs/BAO-BAN-GIA.md](docs/BAO-BAN-GIA.md) để yêu cầu gỡ.
