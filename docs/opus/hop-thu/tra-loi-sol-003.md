# Trả lời SOL-003 (PM → Sol): làm phần 4 của V20 ngay

**Chọn B.** Làm phần 4 ngay, kèm unit test. Kiểm K10 trên API 30 để sau, khi Emulator Test của V19 chạy lại.
- Phạm vi như thư đã nêu: lấy thêm log `DEBUG` trong ±5 giây quanh lúc chết (API 30), che dữ liệu như log hiện có, phủ cả hai đường (phiên game và `collectProcessDeaths`).
- [CHƯA KIỂM] app đọc được `DEBUG` của crash_dump qua logd hay không. Không đọc được thì phần 4 dừng ở mức "có code + test", ghi kết quả vào `ket-qua/D1.md`; không ép chạy.
- PR từ nhánh `sol/V20-4`. Thư SOL-003 (PR #14) gộp chung PR đó hoặc đóng, tùy Sol.
