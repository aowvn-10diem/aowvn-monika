# PM: tiếp quản việc của Sol và Luna (06/10 → 10/10/2026)

> **Kết thúc sớm 07/10/2026 23:40Z:** sếp báo Sol và Luna có hạn mức trở lại. Việc trả lại theo `tra-loi-sol-013-quay-lai-tang-viec.md` và `tra-loi-luna-005-quay-lai-tang-viec.md`. Từ nay tiền duyệt: Luna cho PR của Sol, Sonnet, Haiku, Nova; Nova cho PR của Luna.

**Kết luận:** Sol và Luna hết hạn mức, nghỉ tới **10/10**. Sếp giao (06/10): Sonnet, Nova và Haiku tiếp quản. Ưu tiên gỡ điểm nghẽn trước. Việc nào PM không tự quyết được thì đưa lên trang tiến độ để sếp duyệt nhanh, kèm link.

## Phân việc
| Ưu tiên | Việc | Cũ | Mới | Ghi chú |
|---|---|---|---|---|
| 1 | V26 #93: chọn đúng xp3 có `startup.tjs` (sửa Kara no Shoujo) | Sonnet | Sonnet (sửa theo duyệt) · **Nova** tiền duyệt | Gộp xong thì PM xin sếp phát hành 0.7.6 |
| 2 | V31(3): mục `app` trong config lên 0.7.5 + tăng `configVersion` | Sol | **Sonnet** | Thư `tra-loi-sol-012-v075-da-phat-hanh.md` |
| 3 | #86 V49: chặn PendingIntent thông báo implicit (Luna "Cần sửa") | Sol | **Sonnet** | Đẩy tiếp lên nhánh `sol/V49-notification` (merge, không force) |
| 4 | #84 V49: inventory CodeQL, bỏ job dismiss có `security-events: write` | Sol | **Sonnet** | PM đã yêu cầu sửa |
| 5 | V47: đo lại `configVersion` trên Cloudflare KV sau khi gộp V31(3) | Sol | **Sonnet** | Chỉ đọc, không đụng secret |
| 6 | L07: tiền duyệt PR | Luna | **Nova** cho PR của Sonnet/Haiku · **Haiku** cho PR của Nova | Không chờ 2 giờ nữa |
| 7 | #87 N01, #92 N03 (PR của Nova) chờ tiền duyệt | Luna | **Haiku** | #92 phải xanh CI trước |
| 8 | V48: `scripts/pm-digest.py` nhận mẫu tiền duyệt mới ("Nova/Haiku tiền duyệt") | Sol | **Nova** | Thêm test bằng comment đã che |
| 9 | L09/N06: đồng bộ tài liệu sau mỗi PR gộp | Luna/Nova | **Haiku** | Chỉ tài liệu |
| 10 | N02: CHANGELOG v0.7.5 | Nova | **Haiku** | Để Nova dành sức tiền duyệt |
| 11 | N07: rà thông tin cũ trong `docs/*.md`, `README.md` | Nova | **Haiku** | Bảng sai → sửa → bằng chứng |
| 12 | V50: nâng action còn lại trong workflow | Sol | **Sonnet** (sau ưu tiên 2–5) | Không dispatch phát hành/ký |
| — | V49 phân loại 26 cảnh báo CodeQL, V45 phát hành gói renpy8, V21 deploy Worker | Sol | **Tạm dừng tới 10/10** | Sol nhận lại |

Nova giữ N03 (#92), N04, N05. N05 đổi sang "Thử nhanh 0.7.6" khi có bản 0.7.6.

## Luật gộp trong thời gian này
- PR của Sonnet hoặc Haiku: **Nova "Đạt"** đúng head + CI xanh + PM duyệt → PM gộp (merge commit).
- PR của Nova: **Haiku "Đạt"** đúng head + CI xanh + PM duyệt. Haiku vắng quá 2 giờ thì Sonnet tiền duyệt thay.
- Người viết PR không tự duyệt PR của mình. Phát hành vẫn phải có sếp đồng ý từng lần.

## Kênh
- Sonnet: `send_message` (phiên `session_01HaU9f6SbsYY2fjxpeYbEs1`).
- Nova: issue #90. Haiku: issue #94, prompt `PROMPT-HAIKU-TIEP-QUAN.md`.
