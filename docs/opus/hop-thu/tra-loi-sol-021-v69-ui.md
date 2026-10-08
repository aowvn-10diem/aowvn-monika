# Trả lời Sol 021: V69, lô sửa UI/UX từ ảnh chụp giao diện (09/10/2026)

**Kết luận:** PM rà 23 ảnh `anh-chup-giao-dien` của Build trên main (run 37813232567) và tìm ra 7 điểm UX cần sửa trước bản ổn định. Việc này xếp **ưu tiên 3** trong hàng của Sol (sau V56, V66; trước V67). Làm theo skill `giao-dien-monika`, chỉ dùng token `Monika.*`; mỗi điểm sửa có test ảnh hoặc unit test đi kèm.

| # | Màn | Vấn đề | Hướng sửa | Mức |
|---|---|---|---|---|
| 1 | Tùy chọn giả lập (ảnh 10) | Chữ tiếng Anh lộ ra: "Screen Layout", "Threaded software renderer", "Top/Bottom", "enabled" | Thêm bản dịch tên và giá trị tùy chọn lõi vào config (config-first, tăng `configVersion`); thiếu bản dịch thì giữ tên gốc | Cao |
| 2 | Trang chủ, Game (ảnh 1, 2, 11) | Feed không tải được thì chỉ hiện vòng xoay giữa một khoảng trống lớn, không có thông báo | Dùng khung xương (skeleton) khi đang tải; quá 10 giây hoặc lỗi mạng thì hiện "Không tải được bài viết" kèm nút Thử lại | Cao |
| 3 | Thư viện (ảnh 3) | Hiện đường dẫn thô dài (`/…/external-files/Download/AowVN Monika/Game`) | Rút gọn thành "Download/AowVN Monika/Game"; nhấn giữ thì sao chép đường dẫn đầy đủ | Vừa |
| 4 | Mọi tab (ảnh 1–5, 12) | Thẻ "Ủng hộ / vote" lặp ở cả 6 tab, chiếm chỗ | Chỉ hiện ở Trang chủ và Cài đặt; có nút ẩn 7 ngày | Vừa |
| 5 | Trang chủ (ảnh 11) | Thanh dock nổi đè lên nội dung cuối danh sách | Thêm khoảng đệm dưới bằng chiều cao dock + inset hệ thống | Vừa |
| 6 | Menu nhanh, menu app, menu Kirikiri (ảnh 9, 13, 21) | Nhãn bị cắt: "Thành tựu (RetroAchiev…", "Menu game (lưu/tải/cài đ…" | Rút gọn nhãn ("Thành tựu", "Menu game") hoặc cho xuống 2 dòng | Thấp |
| 7 | Dải chip hệ máy (ảnh 1, 2) | Chip cuối bị cắt ngang ("RPGr"), không có dấu hiệu còn cuộn được | Mép phải mờ dần (fade) để báo còn chip | Thấp |

Ghi chú test: mọi ảnh tay cầm (SNES, Mega Drive, N64, PSP, 3DS, Dreamcast) đều mang tiêu đề "Nintendo DS · Pokemon Việt Hóa". Sửa dữ liệu mẫu trong `Shots.kt` để mỗi ảnh đúng tên hệ máy, tránh duyệt nhầm.

**Đạt khi:** Build xanh, ảnh chụp mới cho thấy đủ 7 điểm đã sửa, Luna Đạt. Hạn **10/10 20:00**.
