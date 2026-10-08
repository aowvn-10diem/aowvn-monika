# Thử nhanh 0.7.6 (bản thử, pre-release)

Dành cho sếp thử trên điện thoại, khoảng 10 phút. Chỉ ghi kết quả thật bạn thấy; ô trống nghĩa là chưa thử. Bản 0.7.6 là pre-release và chưa có bằng chứng chạy game thật **[CHƯA KIỂM]**.

| # | Bài thử | Làm | Đạt khi | KQ |
|---|---|---|---|---|
| 1 | Kirikiri: Kara no Shoujo (cổng G7) | 1. **Thư viện** → **Thêm game từ máy** → chọn thư mục hoặc tệp `.xp3` của game.<br>2. Chờ gói Kirikiri tải, rồi mở game.<br>3. Chạm một cảnh. Nếu hiện hộp thoại không tìm thấy `startup.tjs`, bấm **Báo lỗi game này**. | Vào được game. Nếu lỗi: chụp màn hình hộp thoại. | |
| 2 | RPG Maker XP/VX/Ace | 1. **Thêm game từ máy** → chọn game RPG Maker.<br>2. Mở game, dùng phím ảo di chuyển.<br>3. Mở menu Monika → **Chơi tiếp**. | Vào màn chơi, nhân vật đi được, **Chơi tiếp** quay lại game. | |
| 3 | Một game GB, GBA hoặc NES | 1. **Thêm game từ máy** → chọn ROM.<br>2. Mở game, bấm vài phím ảo.<br>3. Mở menu Monika → **Thoát game**. | Có hình, có tiếng, phím ảo ăn, thoát về Thư viện không văng. | |
| 4 | Nút báo lỗi | 1. Trong một game bất kỳ, mở menu Monika → **Báo lỗi game này**.<br>2. Điền mô tả ngắn.<br>3. Bấm **Gửi báo lỗi**. | Hiện thông báo “Đã gửi báo lỗi. Cảm ơn bạn!”. Nếu gửi thất bại, app báo lỗi và đã lưu sẵn nội dung để chép gửi tay. | |

**Gửi lại:** các dòng KQ ❌ kèm ảnh chụp màn hình. Không gửi file game.

Nguồn tên nút: `LibraryScreen.kt` (Thêm game từ máy), `GameReportUi.kt` (Báo lỗi game này, Gửi báo lỗi), `GamePadOverlay.kt` / `RgssOverlay.kt` (Chơi tiếp, Thoát game), `strings.xml` (`engine_entry_not_found`). Bài chi tiết hơn: [TEST-MAY-THAT.md](TEST-MAY-THAT.md).
