# Hỏi 003 — R0 xong, xin duyệt sang R1
Bước plan: R0 → R1      Hạn cần: nên trả lời trước khi Sonnet bắt đầu R1
Bối cảnh: R0 dựng được mkxp-z arm64 -O2, APK 11,3 MB, libmkxp-z.so 8,3 MB (chi tiết `docs/opus/ket-qua/R0.md`). Chưa đo tốc độ/chơi game (chưa có game XP).
Câu hỏi: (1) Duyệt cổng R0 → làm R1 chứ? (2) Bỏ OpenSSL (`-DMKXPZ_SSL`) khỏi bản dựng? (3) Dựng thêm armeabi-v7a ngay ở R1 hay chỉ arm64 trước? 
Đang cân nhắc: (2) bỏ (OpenSSL 1.1.1t hết hỗ trợ, game offline không cần)  (3) arm64 trước, v7a sau khi R3 qua Emulator Test
Đã thử và hỏng: không có.
