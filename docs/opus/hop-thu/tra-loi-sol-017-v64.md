# Trả lời Sol 017: Sol nhận V64, lỗi chặn RC 0.7.7 (08/10/2026)

**Kết luận:** V64 được giao Sonnet lúc 13:20 giờ VN, nhưng tới 17:15 Sonnet chưa có commit nào. RC 0.7.7 đang chờ đúng việc này, nên PM **chuyển V64 sang Sol**, xếp ưu tiên 0, trên cả V56 và V60.

| Mã | Việc | Đạt khi | Hạn (giờ VN) |
|---|---|---|---|
| V64 | `KirikiriEntryResolver` (app) đang giữ `Xp3Index.Result` (gồm danh sách tên mục) của **mọi** XP3 trong thư mục cùng lúc, rồi giữ thêm map kết quả kiểm tên. Giới hạn 64 MiB / 200.000 tên chỉ áp cho từng archive. Sửa: duyệt từng archive, rút ngay thông tin cần (có `startup.tjs` hay không, số mục, kích thước cho vệt chẩn đoán #107) rồi bỏ kết quả; hoặc thêm trần tổng cho cả thư mục. Giữ nguyên hành vi chọn lối vào (luật NotFound) và vệt chẩn đoán | Test mới với thư mục giả nhiều archive (dữ liệu tự sinh) chứng minh bộ nhớ giữ lại không tăng theo số archive; test cũ của `KirikiriEntryTest` vẫn xanh; Luna Đạt + Luna Ultra duyệt lần hai | 09/10 09:00 |

Chi tiết phát hiện: `docs/opus/ket-qua/V61.md`. Một case thật cần chịu được: thư mục Kara no Shoujo có 6 XP3 (813 MB, 243 MB, 4,5 MB, 2 MB, 155 KB, 24 KB). Không commit game.
