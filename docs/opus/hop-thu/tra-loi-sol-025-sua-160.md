# Trả lời Sol 025: sửa #160 và bỏ quyền ghi thừa (09/10/2026, 04:30 giờ VN)

**Kết luận:** V71 (#158) đã gộp, cảm ơn Sol. Hai việc nhỏ làm trước V69/V70a:

| Ưu tiên | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | V56-quota (#160) | Sửa đúng điểm Luna Ultra nêu ở `scripts/testlab-preflight.py:94-107`: API lịch sử chỉ trả lượt chạy (attempt) mới nhất, nên có thể bỏ sót lượt máy thật ở attempt 1 khi attempt 2 bị chặn và sổ ghi thiếu dòng. Đếm theo mọi attempt (hoặc tính phần thiếu theo hướng an toàn: coi như đã dùng). Thêm test cho trường hợp đó | CI xanh đúng head, Luna + Luna Ultra Đạt |
| 2 | V68-a | `.github/workflows/emulator-test.yml` dòng 50 và 155 còn `contents: write` thừa (V71 đã bỏ bước tạo/xóa release). Hạ về `contents: read`, PR riêng từ `sol/V68-quyen` | CI xanh, CodeQL xanh (A17) |

Sau đó tiếp V69 (thư 021) và V70a (thư 022), hạn V70a vẫn 10/10 20:00.
