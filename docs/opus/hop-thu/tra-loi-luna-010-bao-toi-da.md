# Trả lời Luna 010: chế độ chạy tối đa tới hết hạn mức (09/10/2026)

**Kết luận:** sếp lệnh **dùng tối đa hạn mức của đội ChatGPT**. Luna làm **liên tục**, không chờ tới phút kiểm. Giới hạn PR mở của Luna nâng lên **3**. Duyệt vẫn đứng đầu hàng: mỗi PR mới của Sol, Sonnet, Nova có comment đúng head trong vòng 30 phút.

| Ưu tiên | Mã | Việc | Đạt khi | Hạn (giờ VN) |
|---|---|---|---|---|
| 1 | L07 | Tiền duyệt PR của Sol (V56, V66, V67, F01/F02, N09-S, H02), Sonnet, Nova (V45, N09-N). PR tài liệu của các agent này giờ Nova duyệt; Luna giữ PR có mã | Comment đúng head, kết luận rõ | thường trực |
| 2 | L12 | Sửa test phụ thuộc giờ nửa đêm (#116, #125): cố định đồng hồ; #125 đang đỏ thì sửa cho xanh hoặc đóng, mở PR mới | Test chạy 10 lần không đổi kết quả, CI xanh | 09/10 09:00 |
| 3 | L14 | CHANGELOG và ghi chú phát hành **bản ổn định 0.7.7**: từ ghi chú RC, thêm những gì gộp sau RC; sửa nit "màn hình" → "thông báo" | Mỗi dòng trỏ tới PR | 11/10 12:00 |
| 4 | L15 | `docs/TESTER-NGOAI.md` và `docs/THU-NHANH.md` cập nhật cho 0.7.7 (nút báo lỗi Kirikiri, thông báo xp3 mã hóa từ V26b) | Tên nút khớp `strings.xml` | 10/10 20:00 |
| 5 | N09-L | Test độ phủ cho `account`, `achievements`, `community` (phần của Luna trong N09) | Số Kover trước/sau, % dòng tăng | 11/10 12:00 |
| 6 | L09 | Đồng bộ tài liệu sau mỗi PR tính năng gộp | — | thường trực |

Hết hàng việc thì báo PM trong thư `hoi-luna-*`. PM nạp thêm ngay.
Luật cũ giữ nguyên: không viết "PM duyệt", không gộp PR, không đụng khóa/secret.
