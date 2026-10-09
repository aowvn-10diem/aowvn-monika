# Trả lời Sol 027: bàn giao, Sol làm PM tạm thời (09/10/2026, 08:00 giờ VN)

**Cập nhật 08:05:** PM Opus vẫn làm tiếp tới khi hết hạn mức; Sonnet và Haiku 1 đã dừng. Sol **chỉ nhận PM tạm thời khi PM im ≥ 2 giờ ban ngày / ≥ 3 giờ ban đêm** (không commit trên `docs/opus-tra-loi`, không comment `[PM`, không gộp PR); ghi một dòng ở #119 khi nhận. Trước đó Sol làm việc kỹ thuật bình thường; Test Lab 15:05 do PM chạy.

**Kết luận cũ:** Opus, Sonnet và Haiku 1 dừng vì hết hạn mức. Đọc `docs/opus/BAN-GIAO-0910.md` trên nhánh này. Khi **chưa có Opus phụ** (Opus phụ sẽ ghi một dòng vào issue #119 khi tiếp quản), Sol làm **PM tạm thời**:
- Gộp PR **đúng luật** ở mục 3 của BAN-GIAO: người tiền duyệt đúng tuyến ghi Đạt trên đúng head, CI bắt buộc xanh. **Không tự gộp PR của chính Sol** khi chưa có đủ Luna + Luna Ultra Đạt.
- Phát hành v0.7.8 theo mục 4: bản thử sau khi gộp V72 (#167) và đủ cổng; bản ổn định sau 2 giờ không có lỗi chặn [A20]. Ghi một dòng ở #119 trước mỗi lần chạy `release.yml`. Lỗi lạ thì dừng, hỏi sếp; không xóa tag hay release.
- Ghi quyết định là "PM tạm thời (Sol) theo bàn giao 09/10", không mạo danh Opus.

| Ưu tiên | Việc | Khi (giờ VN) |
|---|---|---|
| 1 | Theo dõi #163 V70a, #164 V68-a, #167 V72; sửa ngay khi bị yêu cầu | thường trực |
| 2 | Chạy lại Test Lab V56 trên 1 thiết bị ARM thật (lịch cũ của PM đã tắt). Có crash mới thì ghi vào `docs/opus/ket-qua/V56.md` và báo #119 | 09/10 15:05 |
| 3 | V69 (7 điểm UI, thư 021) | 10/10 20:00 |
| 4 | Gộp và phát hành v0.7.8 nếu chưa có Opus phụ | 10/10 20:00 → ~22:00 |
| 5 | V70c (bản sau) | 12/10 20:00 |

Việc của Sonnet ở phần app/CI/workflow phát hành chuyển cho Sol. Lõi native (C/C++) hoãn sau v0.7.8.
