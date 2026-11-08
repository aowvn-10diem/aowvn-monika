# V201 — Nhận diện tác giả và người duyệt của PR cho dễ

- Người làm: Nova (PM phụ tạm, 07/11/2026). Mã V201 là mã tạm, PM gốc xác nhận.
- Thiết kế gốc: `docs/BAO-VE-MAIN.md` (mục 0 và 3)
- Trạng thái: Nháp

### 1. Mục tiêu
Nhìn một PR là biết ngay: ai là tác giả, lượt duyệt một và hai do ai làm, trên SHA nào, có phải head hiện tại không. Hiện mọi phiên (PM, Sol, Luna, DeepSeek, Nova) dùng chung tài khoản `aowvn-10diem`, nên phải đọc từng comment và dễ nhầm (cố vấn Opus 07/11 đã thấy: Đạt nằm trên head cũ, "Luna 4/5" bị tính nhầm là lượt một/hai, Đạt và Cần sửa lẫn nhau).

### 2. Ngoài phạm vi
- Không đổi luật duyệt (L3 → Ultra trên cùng SHA, tài liệu 1 Đạt).
- Không thay việc sếp xem PR đụng config, khóa, phát hành.
- Chưa tạo tài khoản/App mới (giai đoạn B cần sếp).

### 3. Tiêu chí đạt
- [ ] Script `scripts/review-ledger.py` đọc danh sách comment của PR (JSON) và in bảng: SHA head, vai tác giả, người đạt L3, người đạt Ultra, trạng thái "đủ cổng".
- [ ] Test offline bằng comment mẫu: Đạt trên head cũ không tính; "Cần sửa" mới hơn "Đạt" cùng SHA làm mất Đạt; tác giả tự duyệt không tính; Luna 4/5, "Đội Duyệt" không tính là L3/Ultra; L3 + Ultra Đạt trên cùng head từ hai vai khác nhau thì xanh.
- [ ] Workflow `reviews` ghi bảng đó vào Job Summary của PR và đặt check `reviews`; giai đoạn đầu chỉ báo, không bắt buộc.
- [ ] PR chỉ sửa tài liệu: đủ khi có 1 Đạt trên head.
- [ ] Vai tác giả suy ra từ tiền tố nhánh (`deepseekN/`, `sol2/`, `luna*/`, `nova/`).

### 4. Bản Play
không áp dụng (quy trình và CI).

### 5. Config
không đổi.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | Đặc tả này | — | (PR đặc tả) |
| 2 | `scripts/review-ledger.py` + test offline | 1 | |
| 3 | Workflow `reviews` (chỉ báo, đọc quyền hẹp, không secret) | 2, gate V200 đã chạy ổn | |
| 4 | Giai đoạn B: mỗi vai một danh tính (xem dưới); sếp tạo, Nova ghi hướng dẫn | 1, sếp quyết | |
| 5 | Bật bắt buộc `reviews` trong ruleset (sếp bấm) | 3 chạy ổn ≥ 2 tuần | |

### 7. Rủi ro
- **Giai đoạn A (PR 2–3) giả mạo được**, vì cùng tài khoản: ai cũng viết được "Luna Đạt". Chỉ chặn được lỗi vô tình (head cũ, thiếu lượt, tự duyệt), không chặn cố ý. Ghi rõ như vậy trong mô tả PR.
- **Giai đoạn B đúng gốc:** mỗi vai một danh tính (machine user hoặc GitHub App: Nova, DeepSeek Duyệt, DeepSeek Thi công, PM). Khi đó `user.login` là bằng chứng, có thể bật yêu cầu duyệt thật và CODEOWNERS cho `.github/workflows/**`, `config/**`. Token riêng mỗi vai, lưu ở nơi bí mật của từng phiên, **không bao giờ commit hay in ra**; quyền chỉ Write, không Admin. Việc tạo tài khoản và cấp token là của sếp.
- Phân tích comment sai có thể chặn nhầm: vì vậy `reviews` chỉ báo ít nhất 2 tuần trước khi bắt buộc, và có test với comment thật đã sao (ẩn tên).
- Không thêm lượt gọi Worker; chỉ gọi API đọc của GitHub.
- Không đụng save, phím, bố cục người chơi.
