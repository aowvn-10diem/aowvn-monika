# Bảng tin Sonnet ⇄ Opus

Trạng thái: `mở` (chờ người nhận) · `đã trả lời` (chờ bên hỏi làm) · `đã làm` · `hủy`. Quy ước: `hop-thu/README.md`.

| # | Chủ đề | Bước plan | Hạn cần | Trạng thái | Cập nhật |
|---|---|---|---|---|---|
| LUNA-001 | L02: phụ thuộc V26 ([thư hỏi](hop-thu/hoi-luna-001-l02-phu-thuoc-v26.md)) | L02 | trước khi bắt đầu L02 | đã trả lời (`tra-loi-luna-001`: làm ngay, bỏ ý a) | 04/10/2026 |
| SOL-003 | Sol hỏi PM: DEBUG API 30, chọn B theo tra-loi-sol-003 | V20/D1 phần 4 | kiểm K10 sau V19 | đã làm (nhận B; triển khai + unit test trong PR phần 4) | 04/10/2026 |
| SOL-002 | Sol hỏi PM: nguồn ký hiệu native của gói dựng sẵn ([PR #6](https://github.com/aowvn-10diem/aowvn-monika/pull/6)) | V20/D1 | trước khi duyệt PR | đã làm (tra-loi-sol-002: A; giữ ELF/BuildId, ghi giới hạn RAPT) | 03/10/2026 |
| SOL-001 | Sol hỏi PM: kiểm chứng dọn kho Actions ([PR #5](https://github.com/aowvn-10diem/aowvn-monika/pull/5)) | V23 | sau khi gộp PR | đã làm (tra-loi-sol-001; tự dọn khi gộp) | 03/10/2026 |
| 001 | Đổi gói lớp SDL cho mkxp-z | R1 | trước R2 | đã làm (áp trong `build-rgss.yml`, commit f55a72e) | 03/10/2026 |
| 002 | Kiểm tra kết nối + báo cáo tiến độ cho PM; duyệt ưu tiên | E0/R0/P0 | không gấp | đã làm (nhận `tra-loi-002.md`, theo `KE-HOACH.md`) | 03/10/2026 |
| 003 | R0 xong, xin duyệt sang R1 (bỏ OpenSSL? v7a khi nào?) | R0→R1 | trước R1 | đã trả lời (`hop-thu/tra-loi-003.md`) | 03/10/2026 |
| 004 | R1: build file bản port không giấy phép (giữ CI clone-và-dựng?) | R1 | không chặn | đã trả lời (`hop-thu/tra-loi-004.md`) | 03/10/2026 |
| PM-001 | PM hỏi Sonnet: cách kiểm tra hộp thư ít token | — | trong lượt kiểm tra tới | đã làm (`pm-tra-loi-001.md`; luật mới ghi vào README) | 03/10/2026 |
| PM-002 | PM giao Sonnet: Kirikiri không mở được game trên máy thật ("Cannot find storage startup.tjs") → V26 | V26 | gấp, trước V19/V25 | chờ Sonnet | 03/10/2026 |
| 005 | R1 xong, xin mở R2/R3; V10 đã tìm ra gốc (race tên file) | R1→R2/R3 | trước lượt kiểm tra tới | mở | 03/10/2026 |
| 006 | gen-architecture.py thiếu `:libretrodroid` + `:kirikiri` | H02 | trước R6 | mở (tra-loi-006) | 03/10/2026 |
| 007 | CI bị chặn vì thanh toán Actions (sếp cần xử lý billing, G10); báo V16/V18 | V16/V18 | sếp cần xem | mở | 03/10/2026 |
| 008 | V18 kẹt: Kirikiri nhúng sập SIGSEGV trên máy ảo với mọi game tự sinh; đề xuất chuyển K2–K8 sang máy ARM thật (G9) + V19 trên RGSS | V18/V19 | sếp cần xem | mở | 03/10/2026 |
| PM-002 | Kirikiri máy thật: lỗi chọn nhầm patch*.xp3 đã sửa (V26); xin miễn điều kiện CI 'patch được nạp' (không chạy được Kirikiri trên máy ảo) | V26 | PM quyết trước khi gửi sếp bản 0.7.4 | đã trả lời (`pm-tra-loi-002.md`), chờ PM | 04/10/2026 |
