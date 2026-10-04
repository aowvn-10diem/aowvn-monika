# Trả lời Sol 008: V43 bước 1, kiểm ký app trên CI (04/10 14:55)

**Kết luận:** khóa ký app **đã có** trong GitHub Secrets (`MONIKA_KEYSTORE_BASE64`, `MONIKA_KEYSTORE_PASSWORD`, `MONIKA_KEY_ALIAS`, `MONIKA_KEY_PASSWORD`, thêm khoảng ngày 29/09). Sếp xác nhận bằng ảnh chụp trang Settings. Câu "chưa có Secrets" trong CLAUDE.md và HANDOFF là sai; V46 và L09 sẽ sửa.

**Việc (V43 bước 1):** thêm input `dry_run` (mặc định `false`) vào `release.yml`. Khi `dry_run=true`:
1. checkout đúng tag hoặc ref, dựng `assembleRelease` và ký bằng Secrets, giữ nguyên các cửa chặn V30;
2. chạy `python3 scripts/verify-apk-cert.py <apk>`; SHA-256 khác `c46902e97029a96593f43c38abd9871170a7519d90992a0b5466c752d45ab20c` thì job đỏ;
3. **không** tạo release, không đẩy tag, không upload APK; chỉ in SHA-256 chứng thư và SHA-256 của APK ra log.
4. **Engine 3DS (đính chính 04/10 15:20, thay cho bản 15:05):** **không** cần chép engine 3DS vào APK. App tự tải engine từ `modules.azahar.url` trong config. Link này trỏ tới release `engine-azahar-1` của repo `aowvn-monika-packs`. PM đã kiểm qua API: repo công khai, release không phải bản nháp, kích thước file khớp config. Trong lượt chạy thử, thêm một bước `curl -sSIL` link đó **không kèm token** và ghi mã HTTP cuối vào log; phải ra 200. Đừng thêm bước tải `azahar.zip` vào assets.

**Đạt khi:** PR được PM duyệt và gộp; Sol chạy `release.yml` với `dry_run=true` trên `main` một lần; log in đúng `c46902e9…d45ab20c`; link engine 3DS tải không token ra 200; ghi link run vào `ket-qua/V43.md`.

Ngoại lệ được cấp: bấm chạy `release.yml` **chỉ với `dry_run=true`**. Phát hành thật vẫn cấm Sol; Sonnet làm khi PM báo. Không in, không lưu giá trị secret.
