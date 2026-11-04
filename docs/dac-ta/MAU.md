# Mẫu đặc tả việc lớn

> Bắt buộc với việc dự kiến **từ 3 PR trở lên** (sếp duyệt 03/11/2026, A79). Việc nhỏ hơn không cần.
> Chép tệp này thành `docs/dac-ta/V<số>-<ten-ngan>.md`. Ghi ngắn, tối đa khoảng 60 dòng.
> PR đặc tả (chỉ tệp này) mở **trước** PR mã đầu tiên. Mỗi PR mã sau đó dẫn link tới tệp đặc tả trong mô tả.

## V<số> — <tên việc>

- Người làm: <tên phiên>
- Thiết kế gốc (nếu có): <đường dẫn>
- Trạng thái: Nháp / Đã duyệt / Đang làm / Xong

### 1. Mục tiêu
Người dùng thấy gì sau khi xong, 1–3 câu, lời người thường.

### 2. Ngoài phạm vi
Những việc **không** làm trong V này (để làm sau hoặc giao V khác).

### 3. Tiêu chí đạt
Mỗi dòng kiểm được bằng test hoặc ảnh chụp. Luna duyệt dựa vào danh sách này.

- [ ] ...
- [ ] ...

### 4. Bản Play
`có` / `không` / `điều chỉnh: ...` (bản Play không có tải game, tự cập nhật APK, link tải ngoài, VietQR, link ủng hộ).

### 5. Config
Trường mới trong `config/monika-config.json` / `MonikaConfig.kt` (kèm giá trị mặc định), hoặc ghi "không đổi".

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | ... | — | #... |
| 2 | ... | 1 | #... |

### 7. Rủi ro
Dữ liệu người chơi (save, phím, bố cục) có bị đụng không, cách sao lưu. Có thêm lượt gọi Worker không.
