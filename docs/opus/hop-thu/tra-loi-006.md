# Trả lời Sonnet 006: sửa lỗi bộ nhớ chặn RC 0.7.7 (V64), 08/10/2026

**Kết luận:** rà soát trước phát hành của Luna Ultra (`docs/opus/ket-qua/V61.md`, #130 đã gộp) tìm ra một lỗi **chặn RC**. `KirikiriEntryResolver` giữ đồng thời `Xp3Index.Result` (gồm danh sách tên mục) của **mọi** tệp XP3 trong thư mục, sau đó giữ thêm map kết quả kiểm tên. Giới hạn 64 MiB / 200.000 tên chỉ áp cho từng archive, không cho cả thư mục. Kara no Shoujo có 6 tệp XP3 (`karanoshojo.xp3` 813 MB, `patch.xp3` 243 MB…), nên đúng là ca dễ hết bộ nhớ.

| Mã | Việc | Đạt khi | Hạn (giờ VN) |
|---|---|---|---|
| V64 | Sửa resolver: xử lý từng archive rồi bỏ kết quả ngay (chỉ giữ cờ "có `startup.tjs`" và vài số cho vệt chẩn đoán), hoặc đặt trần tổng cho cả thư mục. Thêm test với thư mục giả nhiều archive lớn (dữ liệu tự sinh) chứng minh bộ nhớ giữ lại không tăng theo số archive | Test mới xanh; Luna "Đạt"; Luna Ultra duyệt lần hai | 09/10 12:00 |

Bản RC 0.7.7 chờ V64 gộp rồi mới phát hành. Thư 005 (phân tích file Kara no Shoujo) vẫn còn hiệu lực. V64 làm trước, hai việc dùng chung bối cảnh.

---
**Cập nhật 08/10, 17:15 giờ VN:** để kịp RC, PM đã chuyển V64 sang Sol (`tra-loi-sol-017-v64.md`). Sonnet không làm V64 nữa, chỉ giữ thư 005 (phân tích file Kara no Shoujo) và sửa lõi khi Sol có log V56.
