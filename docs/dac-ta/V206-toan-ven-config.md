## V206 — Xác thực toàn vẹn cấu hình từ xa bằng chữ ký Ed25519

- Người làm: Argon (đặc tả) → DeepSeek (thi công)
- Thiết kế gốc (nếu có): Tiếp nối `V181` (`ConfigRepository.kt`), `V196` (`ConfigValidation.kt`)
- Trạng thái: Nháp

### 1. Mục tiêu
Bảo đảm mọi bản cấu hình tải từ xa trên bản phân phối chính thức đều do dự án phát hành nguyên vẹn tới từng byte trước khi ứng dụng áp dụng và lưu vào bộ nhớ đệm, đồng thời giữ nguyên hiệu năng đọc CDN của `V181` và quyền tự cấu hình của các bản tự biên dịch GPLv3.

### 2. Ngoài phạm vi
- Không đổi thứ tự ưu tiên nguồn tải của `V181` (`GitHub Pages` trước → `Cloudflare Worker` dự phòng → bản đệm trên đĩa → `assets` đóng gói).
- Không thêm thư viện mật mã mới vào APK (dùng trực tiếp `org.bouncycastle.crypto.signers.Ed25519Signer` đã có sẵn tại `app/build.gradle.kts:16`).

### 3. Tiêu chí đạt
- [ ] **Ràng buộc tiền tố đường dẫn tải gói (`ConfigValidation.kt:63-66`, `DownloadGuard.kt:18-22`):** Với các tên miền dùng chung trong `ALLOWED_DOWNLOAD_HOSTS` (`github.com`, `raw.githubusercontent.com`, `codeload.github.com`), URL tải mã/gói bắt buộc mang tiền tố đường dẫn của tổ chức chính thức (`/aowvn-10diem/`); với `cdn.jsdelivr.net`, chỉ chấp nhận tiền tố gói trình phát web đã chốt. *Test:* unit test kiểm tra URL cùng tên miền nhưng khác chủ sở hữu bị từ chối ở cả bước nạp cấu hình và bước tải.
- [ ] **Định dạng cấu hình có chữ ký `config.signed.json` (1 lượt GET):** Dòng đầu có dạng `v1:<128-hex-chữ-ký-Ed25519>`, phần sau `\n` đầu tiên là nguyên vẹn mảng byte UTF-8 của `config/monika-config.json`. Giữ phát hành song song `config.json` không đổi định dạng để tương thích ngược các bản APK cũ (`<= v0.8.10`).
- [ ] **Xác thực chữ ký trong `ConfigSignature.kt` & `ConfigRepository.kt`:** Ghim khóa công khai `Ed25519` (32 byte hex) vào mã nguồn. Khi chạy trên bản phát hành chính thức (`OfficialCert.isOfficialBuild(context)` và khóa công khai đã cấu hình), `ConfigRepository` xác thực chữ ký trước khi gọi `ConfigValidation.parse` ở cả luồng tải mạng (`Pages` → `Worker`, dòng `78-92`) và luồng đọc lại bộ nhớ đệm (`loadLocal()`, dòng `45-49`); nếu bộ đệm cũ không có chữ ký hợp lệ khi nâng cấp ứng dụng thì tự động dùng bản `assets` đóng gói rồi tải mới. *Test:* unit test với vector chuẩn RFC 8032 xác nhận sửa 1 ký tự bất kỳ trong phần thân JSON làm bước kiểm chữ ký thất bại và chuyển sang nguồn dự phòng.
- [ ] **Tương thích GPLv3:** Bản tự biên dịch không mang chứng thư phát hành chính thức (hoặc để trống khóa công khai riêng) vẫn đọc được cấu hình của người tự dựng mà không bị chặn.
- [ ] **Công cụ ký `scripts/sign-config.py`:** Script nhận đường dẫn `monika-config.json` và khóa bí mật qua biến môi trường, xuất `config.signed.json` và tự kiểm tra lại chữ ký bằng khóa công khai trước khi thoát mã `0`.

### 4. Bản Play
`không` (bản `play` tại `app/src/play/java/vn/aow/monika/config/ConfigRepository.kt:8-13` chỉ đọc `assets/monika-config.json` cục bộ, không tải cấu hình từ xa).

### 5. Config
Không thêm trường mới vào `config/monika-config.json` (chữ ký bọc bên ngoài mảng byte gốc trong `config.signed.json` để tránh sai lệch chuẩn hóa JSON).

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | Ràng buộc tiền tố đường dẫn cho host dùng chung trong `ConfigValidation.kt` & `DownloadGuard.kt` + unit test | — | #... |
| 2 | `ConfigSignature.kt` (`Ed25519Signer`), tích hợp vào `ConfigRepository.kt` (`config.signed.json` + fallback tương thích) & `scripts/sign-config.py` | 1 | #... |
| 3 | Điền khóa công khai chính thức vào `ConfigSignature.kt` và bật đồng bộ `config.signed.json` sau khi sếp cấp khóa | 2 | #... |

### 7. Rủi ro
- Không đụng dữ liệu save, phím hay bố cục của người chơi.
- Số lượt gọi mạng giữ nguyên **1 HTTP GET / 6 giờ** lên GitHub Pages như `V181`, không tăng tải lên Cloudflare Worker.
