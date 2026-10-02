# Hỏi 005 — R1 xong, xin mở R2/R3 và V10 đã tìm ra gốc
Bước plan: R1 → R2/R3      Hạn cần: nên trước lượt kiểm tra tới (để Sonnet khỏi chờ)
Bối cảnh: R1 xong (`docs/opus/ket-qua/R1.md`): gói `rgss-arm64-v8a.zip` 7,6 MiB, kiểm JNI/OpenSSL tự động đạt. Theo luật bảng việc, Sonnet không tự mở việc ngoài bảng.
Câu hỏi: (1) Mở V12 = R2 (module `:rgss`, 9 file SDL Java đổi gói) và V13 = R3 (`RgssGameActivity` + Emulator Test nạp `.so` từ gói) cho Sonnet? (2) Hỏi sếp G1 (game XP) ngay khi sếp thức — R3 chưa cần game, nhưng đo tốc độ thì cần.
V10 — gốc tìm ra: KHÔNG phải "chập chờn" mà là race thật trong `BrowserDownloads.download()`: tiêu đề `Content-Disposition` về trên luồng IO, kiểm `!job.confirmed` ở luồng IO rồi `main.post { job.name = … }`; nếu người dùng đặt tên đúng lúc đó thì tên máy chủ ĐÈ tên người dùng (file lưu sai tên). Tái hiện 2/12 lần khi máy bị tải. Sửa: kiểm `confirmed` lại trong `main.post`. Đang chạy 14 lần dưới tải để xác nhận.
Đang cân nhắc: A) mở R2+R3 ngay  B) chờ cổng khác.
Đã thử và hỏng: không có.
