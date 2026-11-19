# TV-A — Thư viện bản A, co giãn dọc và ngang

- Người làm: đội thi công OpenCode (PM giao sau khi duyệt đặc tả) · Số V do PM gán, tạm gọi **TV-A**
- Thiết kế gốc: `docs/dac-ta/mockup/thu-vien-A-chot-ngang-doc.html` (mở bằng trình duyệt); thiết kế cũ ở [A56]–[A59]
- Quyết định: sếp chốt 19/11/2026 — nền là mockup A (Cửa hàng), ghép chế độ tay cầm của B và bộ lọc/sắp xếp của C. Người chơi dọc đông hơn, nhưng app nhắm cả handheld Android: **dọc là chính, ngang rộng tối ưu cho handheld**.
- Trạng thái: Nháp

### 1. Mục tiêu
Thư viện nhìn như một cửa hàng game: có bìa "Tiếp tục chơi" to, kệ game, chơi 1–2 chạm. Xoay dọc hay ngang đều gọn, không phải cuộn thừa; cắm tay cầm thì chọn được mọi thứ bằng D-pad.
Ba kiểu màn: **dọc** (rộng < 600dp), **ngang thấp** (cao < 480dp, điện thoại xoay), **ngang rộng** (rộng ≥ 840dp, handheld/tablet).

### 2. Ngoài phạm vi
Nội dung Save (V140), Bản vá (V143), đọc PDF (V142), logic `GameLibrary`/`Importer`/`GameLauncher`; kho bản vá theo game; chạm tay cầm trong màn chơi (đã có `GamepadInput`).

### 3. Tiêu chí đạt
Test Robolectric theo kích cỡ (`w360dp-h780dp`, `w800dp-h360dp`, `w960dp-h540dp`) + ảnh chụp 3 cỡ.
- [ ] **Dọc:** tab Chơi/Đọc/Tệp trên, dock nổi dưới, bìa "Tiếp tục chơi" (chỉ khi đã có game từng chơi), kệ ngang Gần đây / Việt hóa.
- [ ] **Ngang rộng:** thanh trái thay dock; bìa cột trái; lưới ≥ 4 cột; trang game 2 cột (Save cạnh Bản sao lưu).
- [ ] **Ngang thấp:** thanh trái 56dp, "Tiếp tục chơi" thu thành 1 dải, 1 kệ ngang; không có phần nào bị cắt.
- [ ] **Xoay máy:** mục đang mở, bộ lọc, vị trí cuộn, game chọn, sheet đang mở và khung focus giữ nguyên; không tải lại danh sách.
- [ ] Mọi vùng bấm ≥ 48dp (dọc), ≥ 44dp (ngang thấp); tên game tiếng Việt dài cắt 2 dòng, không tràn.
- [ ] Không đè tai thỏ, camera, thanh cử chỉ khi xoay (`WindowInsets`).
- [ ] **Tay cầm** (có gamepad hoặc bật tay ở Cài đặt): khung focus, thanh gợi ý nút, D-pad đi tới mọi vùng bấm và không kẹt; B quay lại, LB/RB đổi mục. Test duyệt focus hết các vùng ở 3 cỡ.
- [ ] Sheet "+ Thêm": đáy ở dọc, bảng trượt bên phải ở ngang; thẻ cấp quyền đầu trang bỏ (trạng thái quyền chỉ ở sheet này và mục Tệp).
- [ ] Thanh Tác vụ gom tải/giải nén/nhập; ô `TaskTile` trong lưới bỏ.
- [ ] Chơi, nhập, xóa, ẩn game chạy như cũ; test cũ vẫn xanh.

### 4. Bản Play
`có` — dùng chung mã. Hiện `LibraryScreen.kt` có hai bản (`app/src/github/…` 614 dòng, `app/src/play/…` 574 dòng): phần mới đặt trong `src/main` (composable + token), mỗi flavor chỉ gọi lại. Không thêm tải game, tự cập nhật, link tải ngoài, VietQR hay link ủng hộ (A68, A95).

### 5. Config
Không đổi `config/monika-config.json`, không tăng `configVersion`. Ngưỡng dp (600 / 840 / 480) là hằng trong token giao diện `ui/theme`, tránh thêm thư viện `window-size-class`; dựng bằng `BoxWithConstraints`. Bật tay cầm tay trong Cài đặt dùng `Prefs` sẵn có. Nếu PM muốn ngưỡng nằm trong config thì tách một PR riêng.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | Đặc tả này (chỉ docs + mockup) | — | PR này |
| 2 | Khung co giãn: kiểu bố cục theo cỡ màn, thanh trái thay dock ở ngang, giữ trạng thái khi xoay, test 3 cỡ | 1 | — |
| 3 | Màn Chơi: bìa "Tiếp tục chơi", kệ, lưới thích ứng, thanh Tác vụ, sheet "+ Thêm" | 2 | — |
| 4 | Đọc và Tệp theo bố cục ngang/dọc mới | 2 | — |
| 5 | Trang game 2 cột ở ngang (`GameDetailScreen`) | 2, V139 (#430) | — |
| 6 | Chế độ tay cầm: focus, thanh gợi ý nút, luật D-pad, mục Cài đặt | 3 | — |
| 7 | Lọc và sắp xếp (sheet Lọc, A–Z) | 3 | — |

### 7. Rủi ro
- **Bố cục/phím:** chỉ đổi lớp giao diện Thư viện; không đụng save, `GameLauncher`, phím ảo trong game. Dock hiện có 5 mục (menu "Thêm" cuối), mockup minh họa 4 mục; giữ đúng danh sách `dockItems` của `MainActivity`.
- **Xoay:** `MainActivity` đã xử lý `configChanges` nên không tạo lại; cần `rememberSaveable` cho trạng thái mới.
- **Focus tay cầm:** kẹt focus là lỗi nặng, nên có test duyệt focus trước khi gộp. Hai flavor dễ lệch nhau nếu sửa riêng.
- **Chưa kiểm:** ngưỡng dp, tai thỏ/thanh cử chỉ và hiệu năng trên máy yếu **[CHƯA KIỂM]** trên máy thật. Không thêm lượt gọi Worker/mạng.
