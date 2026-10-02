# Trả lời 003
Kết luận (1 dòng): **Duyệt cổng R0 → R1** (R1 đã làm, đúng hướng). **Bỏ OpenSSL.** Dựng **arm64 trước**; armeabi-v7a dựng sau khi R3 qua Emulator Test, và **bắt buộc có trước R7** (bước tắt app ngoài cho `rgss`).

Lý do + đánh đổi:
- R0 đạt điều kiện cổng: dựng được, có bảng kích thước, mới gặp 2 nguyên nhân hỏng (ngưỡng là 5). Tốc độ chưa đo vì chưa có game; việc đo dời sang R3 (khi sếp đưa game XP) và R6.
- OpenSSL 1.1.1t đã hết hỗ trợ. Nó chỉ phục vụ HTTPS của module mạng mkxp-z (`src/net/net.cpp:10–11`, binding `HTTPLite` ở `binding/http-binding.cpp`), mà khi có SSL thì mkxp-z vẫn **tắt kiểm chứng chỉ** (`net.cpp:131–133`). Vậy nó không thêm an toàn mà chỉ thêm dung lượng và rủi ro. Game RPG Maker XP/VX/Ace gốc không dùng mạng.
- v7a không đổi mã, chỉ tốn thêm CI. Rủi ro chính nằm ở R3 (nạp `.so` + SDL), nên kiểm arm64 qua R3 trước rồi mới nhân đôi ABI. Nhưng khi `allowExternalApp: false` cho `rgss` mà chưa có v7a, máy 32-bit mất đường chơi RPG Maker. Vì vậy v7a là điều kiện của R7.

Các bước (mỗi bước có cách kiểm):
1. Trong `build-rgss.yml`: bỏ `-DMKXPZ_SSL`, bỏ `openssl` khỏi `LOCAL_STATIC_LIBRARIES` và khỏi bước dựng phụ thuộc. Ruby thêm `openssl` vào `--with-out-ext=` (bản port đang để `readline,dbm,gdbm,win32,win32ole,fiddle`, `Makefile:130`). Kiểm: `readelf -d` các `.so` không có `libssl`/`libcrypto`; `strings libmkxp-z.so libruby.so | grep -c "OpenSSL 1.1.1"` bằng 0; ghi kích thước trước và sau vào `ket-qua/R0.md` hoặc `R1.md`.
2. `libSDL2_ttf.so` 17,9 MB là số **chưa cắt ký hiệu**. Đo lại sau `llvm-strip`; vẫn trên 5 MB thì ghi nguyên nhân (FreeType/HarfBuzz tĩnh) để PM quyết. Mục tiêu: gói `rgss` arm64 ≤ 15 MB để tự tải cả khi dùng 4G.
3. **Hỏi sếp G1 ngay** (1 game RPG Maker XP, Q5), vì R3 cần game để chạy thử.
4. v7a: PM sẽ mở việc thêm `armeabi-v7a` vào ma trận `build-rgss.yml` khi R3 xanh trên Emulator Test.

Điều chưa chắc [CHƯA KIỂM]:
- Game RPG Maker dùng `HTTPLite` qua HTTPS: tỉ lệ chưa biết. Gặp game như vậy (R6) thì báo lỗi rõ "game cần mạng HTTPS, chưa hỗ trợ".
- Ruby bỏ ext `openssl` thì ext `digest` có dựng được bản dựng sẵn không. Bình thường là được; kiểm bằng log `configure` của Ruby.

Cần sếp quyết: G1 (1 game XP), Sonnet hỏi sếp.
