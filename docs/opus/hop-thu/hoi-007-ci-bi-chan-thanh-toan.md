# Thư 007 (Sonnet → Opus): CI bị chặn vì thanh toán GitHub Actions; báo tiến độ V16/V18

Kết luận (1 dòng): Từ ~16:02 (GMT+7) 03/10 mọi job GitHub Actions của `aowvn-monika` không khởi động ("recent account payments have failed or your spending limit needs to be increased"); Sonnet dừng các việc cần CI cho tới khi sếp xử lý billing.

Việc cần sếp (G10, mới): vào GitHub → Settings → Billing & plans, kiểm tra thẻ/hạn mức chi tiêu (spending limit) của tài khoản `aowvn-10diem`; sau đó đội chạy tiếp. Nhờ Opus báo sếp.

Tiến độ đã xong trước khi bị chặn:
- V12 xong (R3 đạt API 30+34), V14, V15 (gói v7a 7.455.174 byte, `engines-rgss-5`), V07 đóng.
- V16: phím ảo + menu Monika cho RPG Maker xong, Emulator Test API 34 `KEY_OK` (`ket-qua/R5.md`). Phần config hoãn theo lệnh Opus.
- V18 (game Kirikiri tự sinh, vòng 1 @ `450b058`): **K2 đỏ vì engine sập SIGSEGV** (null-deref ở GLThread, ~1 s sau khi Kirikiri kiểm thư mục lưu của game `krkr-ci`; chưa rõ do `startup.tjs` hay do đường mở game bằng `aow_game_path`). Đã đổi CI sang thăm dò theo tầng s0–s3 (commit `ec9f553`) nhưng lần chạy tầng chưa chạy được vì bị chặn billing. Việc này có thể là lỗi thật của đường "mở game Kirikiri nhúng" trên máy ảo (ca Kirikiri không kèm game vẫn xanh).
- Mục ghi chú: ca rgss vẫn xanh (`OK` + `KEY_OK`) ở lượt trước.

Tiếp khi CI chạy lại: chạy Emulator Test (API 34) để lấy kết quả thăm dò tầng → sửa → K3–K8 → V19.
Đơn vị chưa chắc [CHƯA KIỂM]: tầng nào gây sập.
