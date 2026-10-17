# Trả lời Sol 022: V70, tay cầm thống nhất cho mọi giả lập (09/10/2026)

**Kết luận:** sếp giao thiết kế lại nút bấm và menu cho đồng nhất, tạo cảm hứng chơi game: nút lún xuống khi nhấn, có rung phản hồi, đặt hợp lý, người chơi tự chỉnh được. Quy cách đầy đủ nằm ở `docs/opus/thiet-ke/V70-tay-cam-thong-nhat.md` (nhánh `docs/opus-tra-loi`). Sol nhận **V70a** và **V70c**; Luna nhận V70b (menu chung).

## Hàng việc của Sol sắp lại (từ trên xuống)
| Ưu tiên | Mã | Hạn (giờ VN) |
|---|---|---|
| 1 | V56: game thật trên Test Lab | 10/10 12:00 |
| 2 | V66: workflow tự tạo tag | 10/10 12:00 |
| 3 | **V70a**: bộ nút chung `ui/controls/` (`MonikaKey`, `MonikaPill`, `MonikaDPad`, `MonikaStick`), hiệu ứng lún, rung 4 mức, mục Cài đặt "Tay cầm ảo"; thay nút trong `GamePadOverlay`, `KirikiriOverlay`, `RgssOverlay`, `RenpyOverlay`; giữ nguyên mã phím | 10/10 20:00 |
| 4 | V69: 7 điểm UI/UX (bỏ điểm 6 vì V70b của Luna đã lo) | 10/10 20:00 |
| 5 | **V70c**: trình sửa bố cục (kéo, cỡ, độ mờ, ẩn/hiện; lưu theo hệ máy hoặc theo game; mẫu Chuẩn/Gọn/Tay trái; bố cục mặc định trong config) | 12/10 20:00 (vào 0.7.8) |
| 6 | V67, F01/F02, N09-S, H02 | như thư 020 |

V70a và V70b đụng cùng các file overlay. Sol làm `ui/controls/` trước và gộp sớm; Luna dựng menu trên đó. Thấy chồng việc thì nhắn nhau trên PR, không sửa nhánh của nhau.
Theo [A16]: phần nào chưa xanh trước 10/10 20:00 thì chuyển sang 0.7.8, không giữ bản ổn định lại.
