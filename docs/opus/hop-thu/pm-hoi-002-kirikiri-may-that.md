# PM-002 — Kirikiri không mở được game trên máy thật (G7) → việc V26, ưu tiên số 1

**Gửi:** Sonnet · **Ngày:** 03/10/2026 21:30 · **Trả lời ở:** `pm-tra-loi-002.md`

## Sự thật (sếp thử 0.7.3 trên điện thoại thật)
- Không văng. Kirikiri hiện hộp thoại: tiêu đề **"0.7.3 Kirikiri"**, nội dung **"Cannot find storage startup.tjs"**, nút "Đồng ý". Ảnh: `docs/opus/ket-qua/G7-kirikiri-073.png`.
- Vậy trên máy thật: gói engine tải được, native nạp được, TJS chạy tới bước tìm `startup.tjs`. Lỗi nằm ở **bước mở game**.
- Monika truyền `aow_game_path` = đường dẫn tuyệt đối tới **file** `data.xp3` (config: `markers`/`entry` = `data.xp3`; `EngineRoutes` chỉ nhận `isFile`). Native gọi `TVPMainScene::startupFrom(path)` (patch `0001-mo-game-tu-intent.patch`).

## Giả thuyết của PM [CHƯA KIỂM], theo thứ tự nên thử
1. `startupFrom` của Kirikiroid2 chờ **thư mục game** (hoặc hiểu file .xp3 khác cách ta nghĩ). Truyền file `data.xp3` thì không gắn được kho → không thấy `startup.tjs`. Trên máy ảo cùng đường này lại ra `malloc(-1)`, có thể cùng gốc.
2. Lớp file của krkr2 không đọc được đường dẫn đó (quyền, đường dẫn riêng của app, dấu cách: tiêu đề "0.7.3 Kirikiri" gợi ý tên thư mục có dấu cách).
3. xp3 mã hóa (game thương mại). Nếu đúng thì chỉ ghi nhận, không sửa ở mốc này.

## Việc V26 (trước V19, V25; V18 gộp vào đây)
1. Đọc `startupFrom` / `TVPCheckStartupPath` trong mã krkr2yuri đã ghim, ghi rõ nó nhận gì (thư mục, file xp3, `startup.tjs`).
2. Trên máy ảo, dùng game tự sinh, thử 3 biến thể: (a) file `data.xp3` như hiện tại; (b) thư mục cha; (c) thư mục cha **không dấu cách**, ASCII. Ghi kết quả từng biến thể.
3. Sửa theo kết quả (ví dụ `EngineRoutes` truyền thư mục cha, hoặc patch native chuẩn hóa đường dẫn). Thêm unit test cho đường dẫn truyền đi.
4. Emulator Test xanh, rồi ra bản vá theo quy trình thường lệ để sếp thử lại trên máy thật.
5. Trả lời `pm-tra-loi-002.md`, tối đa 15 dòng: nguyên nhân, cách sửa, bản để sếp thử.

Tên game và nơi sếp để game: PM đang hỏi sếp, sẽ ghi bổ sung vào đây. Không cần chờ.
