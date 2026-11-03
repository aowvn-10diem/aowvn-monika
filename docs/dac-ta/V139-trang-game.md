# V139 — Trang chi tiết game trong Thư viện

- Người làm: DeepSeek 5 (phiên V139) · Thiết kế gốc: không có tệp riêng (mô tả ở PR #430)
- Trạng thái: Đang làm — mã ở **#430** (chưa gộp); đặc tả này viết bổ sung theo A79

### 1. Mục tiêu
Bấm một game trong Thư viện là mở **trang riêng của game**: ảnh bìa, tên, hệ máy, dung lượng, đường
dẫn và thanh **Tác vụ** Chơi · Save · Bản vá · Xóa. Chơi/Xóa chạy được ngay; Save/Bản vá là nút mờ
"Sắp có". **Phạm vi:** trang chi tiết + đường mở từ ô game và menu ⋯, cho cả flavor GitHub và Play.

### 2. Ngoài phạm vi
Nội dung Save (V140) và Bản vá Việt hóa (V143); đổi thiết kế Thư viện; sửa `GameLauncher`.

### 3. Tiêu chí đạt
- [ ] Bấm **ô game** hoặc mục **"Trang game"** trong menu ⋯ → mở đúng trang đó (chỉ game chơi được:
      có hệ máy, chưa bị dọn/khóa); giữ lâu ô vẫn ra menu như cũ.
- [ ] Trang hiện đúng ảnh bìa/icon/minh họa, tên, nhãn hệ máy (+ "Có sẵn trong máy", "Thử nghiệm"),
      3 dòng **Hệ máy / Dung lượng / Đường dẫn**; dung lượng tính cả cây (kể cả > 7 tầng), hiện
      "Chưa rõ"/"0 B" khi không đọc được, số dùng dấu phẩy (1,5 MB).
- [ ] **Chơi** đi đúng luồng Thư viện: hệ thử nghiệm hỏi xác nhận, thiếu app ngoài hiện "Cần cài …",
      chạy được thì ghi nhận lượt chơi.
- [ ] **Save** và **Bản vá** là nút mờ + nhãn **"Sắp có"**, bấm không có tác dụng.
- [ ] **Xóa** phải qua hộp thoại xác nhận nói rõ **"Sẽ xóa: thư mục game … (dung lượng)"** và
      **"Được giữ lại: …"**; chỉ xóa thư mục game do app quản lý (con cấp 1 của kho), game quét
      trong máy chỉ **ẩn**.
- [ ] Trước khi xóa: **sao lưu toàn bộ thư mục game** ra `save-backups/xoa-<tên>-<giờ>-<4hex>.zip`
      (tên duy nhất, giữ bằng `CREATE_NEW`); quét thiếu (symlink/mục lỗi/quá lớn/thiếu chỗ) hoặc
      sao lưu lỗi → **không xóa**, báo lý do.
- [ ] Xóa xong có bản sao lưu → hiện đường dẫn + nút **"Chia sẻ bản sao lưu"** (FileProvider +
      `ACTION_SEND`) để người chơi lấy zip ra ngoài.
- [ ] Không tìm thấy game → màn "Không tìm thấy game" có nút quay lại, không văng app.

### 4. Bản Play
`điều chỉnh: có` — trang game và đường mở dùng chung mã; khác biệt duy nhất là hộp "Cần cài app":
GitHub dùng `ExternalAppDetails` đầy đủ (nút cài + link), Play dùng
`app/src/play/.../screens/PlayAppDetails.kt` chỉ nêu mô tả/tên gói, **không kèm link tải ngoài** (A68).

### 5. Config
Không đổi `config/monika-config.json` (không thêm trường, không tăng `configVersion`); không thêm
thành phần `AppGraph`, không đổi ProGuard, không thêm Worker.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | Đặc tả này (chỉ docs) | — | PR này |
| 2 | Mã: trang chi tiết + route + ô/menu + thanh Tác vụ + xóa có sao lưu + test | 1 | #430 |
| 3 | Nội dung Save trên thanh Tác vụ | 2 | V140 |
| 4 | Nội dung Bản vá Việt hóa | 2 | V143 |

### 7. Rủi ro
- **Save:** xóa game là xóa cả thư mục → sao lưu toàn bộ thư mục trước (zip trong
  `filesDir/save-backups`, tên duy nhất theo mili-giây + 4 hex, giữ bằng `CREATE_NEW`); quét chưa
  trọn hoặc sao lưu lỗi thì từ chối xóa. Save giả lập nằm ngoài thư mục game nên không bị đụng;
  mỗi lần xóa tạo một zip đầy đủ (chưa có cơ chế tự dọn zip cũ).
- **Khác:** game ngoài kho chỉ ẩn, thư mục lạ trong kho không bị xóa; không thêm lượt gọi Worker/mạng.

### 8. Đối chiếu mã đã có (#430, head `e4f168f7`)
| Hạng mục | Trạng thái |
|---|---|
| Trang chi tiết + thanh Tác vụ (`GameDetailScreen.kt`) | **Xong** |
| Route `game/{key}`, `EXTRA_GAME_KEY`, mở từ ô/menu (cả 2 flavor) | **Xong** |
| Xóa: xác nhận, sao lưu toàn thư mục, chặn thư mục lạ, chia sẻ zip | **Xong** |
| Test | `GameDetailTest` 8/8, `GameDeleteBackupTest` 13/13 (github) |
| Save / Bản vá | **Chưa** — chỉ nút mờ "Sắp có" (V140/V143) |

**[CHƯA KIỂM]** Chưa chạy máy thật/máy ảo (chỉ Robolectric + `assembleDebug` hai flavor); ảnh giao
diện lấy từ test, chưa có ảnh máy thật; Play dùng chung mã nhưng chưa có test UI riêng cho route.
