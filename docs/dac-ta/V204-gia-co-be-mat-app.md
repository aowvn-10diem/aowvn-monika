## V204 — Gia cố biên dữ liệu, WebView và tệp gói trên ứng dụng

- Người làm: Argon (đặc tả) → DeepSeek (thi công)
- Thiết kế gốc (nếu có): Báo cáo rà soát nội bộ A85 (kho riêng tư)
- Trạng thái: Nháp

### 1. Mục tiêu
Đảm bảo mọi tệp người dùng đưa vào ứng dụng (qua trình chọn tệp, chia sẻ, trình tải về), mọi màn hình WebView nhúng và các gói thư viện tải thêm đều đi qua bộ lọc biên giới nghiêm ngặt, tương thích quy định tải mã chỉ-đọc của Android 14+ (`targetSdk` 35+) và giữ nguyên trải nghiệm chơi game hiện tại.

### 2. Ngoài phạm vi
- Không thay đổi định dạng kho lưu save (`SaveVault`), không đổi khóa ký APK phát hành.
- Việc đăng ký HTTPS App Links (`assetlinks.json`) trên tên miền web làm ở đợt hạ tầng riêng.

### 3. Tiêu chí đạt
- [ ] `SafeZipPath.sanitizeSingleFileName` làm sạch tên tệp đầu vào (bỏ dấu tách thư mục, ký tự điều khiển, `.`/`..`, giới hạn ≤ 180 byte UTF-8 giữ nguyên phần mở rộng) và được áp cho `Importer.displayName`, `Importer.copyUri`, `Downloader.fileNameFrom`, kèm unit test ca biên.
- [ ] `ApkInspector` từ chối sớm mục dữ liệu không qua `SafeZipPath.isSafeName` hoặc chứa ký tự xuống dòng/tab; `reopenEntry` khớp đúng ranh giới tên mục; `j2me` `AppInstaller` kiểm đường dẫn `.jar` cục bộ nằm trong thư mục cha và không mở kết nối `http://` không mã hóa.
- [ ] Mọi tệp thư viện/mã trong `PackTransaction`, `CoreManager` và `j2me` được đặt quyền chỉ-đọc (`setReadOnly`) sau khi xác thực SHA-256 (bật lại quyền ghi trên thư mục tạm trước khi dọn bản cũ); bỏ nhánh tải lõi không kiểm tra SHA-256 trong `CoreManager`.
- [ ] `InAppBrowserActivity` tắt `allowFileAccess`/`allowContentAccess`, làm sạch `Intent.URI_INTENT_SCHEME` (xóa `component`, `selector`, `clipData`, cờ cấp quyền URI, chặn tự gọi gói nội bộ), chỉ nạp `browser_fallback_url` có scheme `https`, khớp đúng ranh giới tên miền xã hội và hỏi xác nhận trước khi cấp quyền Camera/Micro.
- [ ] `WebGameActivity` tách origin ảo theo từng thư mục game (không dùng chung `localStorage` giữa các game), tắt `allowFileAccess`/`allowContentAccess` và chặn request mạng ra ngoài trừ script `webPlayers` hợp lệ; `PostScreen` tắt truy cập tệp, dùng `baseUrl` trung lập kèm thẻ Content-Security-Policy.
- [ ] Thu hẹp `res/xml/file_paths.xml` về đúng các thư mục con cần chia sẻ; bổ sung `backup_rules.xml` & `data_extraction_rules.xml` loại trừ tệp phiên đăng nhập; ghim host `account.loginUrl` về `aow.vn`/`monika.aow.vn` trong `ConfigValidation`; `DownloadReceiver` kiểm đúng `ACTION_DOWNLOAD_COMPLETE` và danh sách ID tải của chính ứng dụng.

### 4. Bản Play
`có` — các chốt làm sạch đường dẫn (`Importer`, `SafeZipPath`, `j2me`), quyền chỉ-đọc gói (`PackTransaction`, `CoreManager`), `WebGameActivity`, `FileProvider` và `dataExtractionRules` áp cho cả hai flavor (`InAppBrowserActivity`, `PostScreen`, `Downloader`, `ApkInspector` chỉ nằm ở `github`).

### 5. Config
Không thêm trường bắt buộc mới; đảm bảo mọi mục trong `cores` đều khai báo đủ `artifacts.<abi>.sha256` trước khi tắt nhánh tải lõi cũ không hash.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | Làm sạch tên tệp đầu vào (`SafeZipPath`, `Importer`, `Downloader`, `ApkInspector`, `j2me`) + khóa `setReadOnly` & bắt buộc SHA-256 cho gói/lõi | — | #... |
| 2 | Gia cố 3 mặt phẳng `WebView` (`InAppBrowserActivity`, `WebGameActivity`, `PostScreen`) + unit test bộ lọc URI/host | — | #... |
| 3 | Thu hẹp `FileProvider`, cấu hình `backup_rules`/`data_extraction_rules`, ghim host `account.loginUrl`, lọc `DownloadReceiver` & `MainActivity` extras | 1 | #... |

### 7. Rủi ro
- Không đụng thư mục save (`saves`, `states`, `azahar`). Khi đặt `setReadOnly` cho gói/lõi, bắt buộc bật lại `setWritable(true)` trước khi xóa thư mục `.previous` lúc nâng cấp gói để tránh lỗi `IOException` trên một số ROM Android; kiểm bằng unit test nâng cấp gói 2 lần liên tiếp.
- Không phát sinh thêm lượt gọi Worker.
