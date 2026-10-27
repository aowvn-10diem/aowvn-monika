# Chính sách quyền riêng tư — Aow Monika

> **Nháp, chờ sếp duyệt.** Mục đánh dấu [CHƯA KIỂM] hoặc [chờ sếp] phải xử lý trước khi công bố.
> Ngày hiệu lực: [chờ sếp]

Aow Monika là ứng dụng Android của cộng đồng aow.vn. Trang này nói rõ ứng dụng thu thập dữ liệu gì, vì sao, và bạn kiểm soát được những gì.

## 1. Dữ liệu nào được xử lý

**Ở lại trên máy bạn, không gửi đi:** thư viện game, file save và bản sao lưu, cài đặt, lịch sử đọc bài. Các file game bạn chọn cũng chỉ được đọc trên máy.

**Báo lỗi tự động.** Khi lõi game hoặc ứng dụng gặp lỗi (văng, không lên hình, treo, hết bộ nhớ), ứng dụng gửi báo cáo về máy chủ của AowVN để sửa lỗi. Báo cáo gồm:

- phiên bản ứng dụng, thời điểm và loại lỗi;
- thông tin máy: hãng, model, phiên bản Android, kiến trúc CPU và phần cứng;
- tình trạng máy lúc lỗi: bộ nhớ, dung lượng trống, trạng thái mạng [CHƯA KIỂM: có gửi mức pin không];
- nhật ký kỹ thuật gần đây và vệt thao tác trước lỗi;
- tên game đang chạy, nếu lỗi xảy ra khi đang chơi.

Trước khi gửi, ứng dụng thay đường dẫn riêng tư, token, khóa bí mật và địa chỉ email bằng nội dung ẩn. Báo lỗi tự động **bật mặc định**. Bạn tắt được trong Cài đặt, mục "Tự gửi báo lỗi game". [chờ sếp: có giữ mặc định bật trong bản Play không]

**Báo lỗi do bạn gửi.** Khi bạn chủ động báo lỗi một game, bạn có thể viết mô tả và đính kèm ảnh chụp màn hình. Chỉ gửi khi bạn bấm gửi.

**Tài khoản Google (tùy chọn).** Nếu bạn đăng nhập bằng Google qua trang aow.vn, ứng dụng lưu trên máy: mã người dùng (uid), tên, email, ảnh đại diện và token đăng nhập. Token nằm trong vùng lưu riêng của ứng dụng. [CHƯA KIỂM: tính năng nào cần đăng nhập, và dữ liệu nào được gửi tới Firebase khi dùng tính năng đó]

**Dịch chữ trên màn hình (tùy chọn).** Bạn tự chọn nhà cung cấp dịch và nhập khóa API của họ. Chữ nhận diện được trên màn hình được gửi trực tiếp tới nhà cung cấp bạn chọn, không đi qua máy chủ AowVN. Nếu bạn bật đọc ảnh trực tiếp (AI đọc ảnh) và chọn nhà cung cấp AI (API kiểu OpenAI, ví dụ OpenAI, Gemini hoặc OpenRouter, do bạn nhập địa chỉ và khóa), ảnh khung màn hình hiện tại (định dạng JPEG) được gửi tới nhà cung cấp đó để đọc và dịch. [chờ sếp: cách khai theo biểu mẫu Play]

**Tải cấu hình.** Ứng dụng tải tệp cấu hình (danh sách hệ máy, liên kết, thông báo) từ máy chủ AowVN. Yêu cầu này không chứa thông tin cá nhân.

**Đọc bài viết và diễn đàn.** Ứng dụng đọc nội dung từ aow.vn. Như mọi kết nối internet, máy chủ nhận địa chỉ IP của bạn và chuỗi nhận diện ứng dụng (tên và phiên bản).

## 2. Ứng dụng không làm gì

- Không có quảng cáo, không có công cụ phân tích hay theo dõi của bên thứ ba trong mã.
- Không đọc vị trí, danh bạ, máy ảnh hay micro.
- Không bán dữ liệu.
- Theo đối chiếu mã đến thời điểm soạn, không tải file game, ROM hay save của bạn lên máy chủ AowVN.

## 3. Quyền ứng dụng dùng

Bảng dưới áp dụng cho bản GitHub. Bản Play có thể khác, xem ghi chú cuối bảng.

| Quyền | Mục đích |
|---|---|
| INTERNET | Đọc bài, tải cấu hình, tải game và gói engine do bạn chọn |
| POST_NOTIFICATIONS | Báo khi có bài mới |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC | Tải và giải nén game ở nền |
| QUERY_ALL_PACKAGES | Nhận diện ứng dụng đã cài (trình giả lập bên ngoài) để mở đúng |
| MANAGE_EXTERNAL_STORAGE, READ/WRITE_EXTERNAL_STORAGE | Đọc file game bạn chọn và ghi file save |
| REQUEST_INSTALL_PACKAGES | Cài APK do bạn chọn (bản Play sẽ bỏ) |
| VIBRATE | Phản hồi khi chạm |

Bản Play sẽ bỏ các quyền hạn chế và quyền cài APK. Danh sách này sẽ được cập nhật khi bản Play hoàn tất. [chờ V150]

## 4. Thời gian giữ dữ liệu

- Dữ liệu trên máy: xóa khi bạn gỡ ứng dụng hoặc xóa dữ liệu ứng dụng trong cài đặt Android.
- Báo lỗi đã gửi tới máy chủ AowVN: [chờ sếp: thời hạn lưu].

## 5. Quyền của bạn

- Tắt báo lỗi tự động trong Cài đặt.
- Không đăng nhập thì không có dữ liệu tài khoản trên máy. [CHƯA KIỂM: có nút đăng xuất để xóa token trên máy không]
- Yêu cầu xóa báo lỗi đã gửi: [chờ sếp: kênh liên hệ và cách xác minh]

## 6. Trẻ em

Ứng dụng không nhắm tới trẻ em dưới 13 tuổi. [chờ sếp: xác nhận theo chính sách Play]

## 7. Thay đổi chính sách

Khi tính năng thay đổi, chúng tôi cập nhật trang này và ghi ngày hiệu lực mới.

## 8. Liên hệ

[CHƯA KIỂM: email hoặc kênh liên hệ chính thức do sếp chọn]. Mã nguồn công khai: https://github.com/aowvn-10diem/aowvn-monika
