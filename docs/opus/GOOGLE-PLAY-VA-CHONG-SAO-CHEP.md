# Google Play và chống sao chép — kế hoạch (PM, 26/10/2026)

Sếp giao 26/10 06:50 UTC, hẹn 18h00 giờ VN. Số liệu chưa kiểm ghi [CHƯA KIỂM]. Kế hoạch đánh số [A65], [A66].

## Kết luận

| | Kết luận |
|---|---|
| [A65] Google Play | Đưa lên được, nhưng **không thể dùng nguyên bản APK hiện tại**. Cần một bản riêng cho Play ("bản Play"): bỏ tải game từ aow.vn trong app, bỏ tự cập nhật, đóng gói sẵn lõi/engine thay vì tải về. Bản GitHub giữ nguyên đầy đủ tính năng. |
| [A66] Chống sao chép | Mã app là GPLv3 công khai, nên làm rối mã không bảo vệ được gì đáng kể. Thứ cần bảo vệ là **máy chủ (Worker), link tải, khóa ký và thương hiệu**. Bảo vệ ở phía máy chủ, không nhét bí mật vào APK. |

---

## 1. Đưa app lên Google Play [A65]

### 1.1 Ba điểm chặn chính (bản hiện tại chắc chắn bị từ chối)

| # | Điểm chặn | Vì sao | Cách xử lý trong bản Play |
|---|---|---|---|
| 1 | App tải lõi/engine (.so) từ GitHub Releases lúc chạy | Chính sách Device and Network Abuse cấm app trên Play tải mã thực thi (dex, JAR, .so) từ nguồn khác Google Play | Đóng gói sẵn lõi phổ biến trong AAB, hoặc dùng Play Feature Delivery / Play Asset Delivery cho lõi lớn (Azahar, PPSSPP…) |
| 2 | App tự cập nhật APK qua `apkUrl` + quyền `REQUEST_INSTALL_PACKAGES` | Play cấm tự cập nhật ngoài Play; quyền này chỉ cho một số loại app (trình duyệt, quản lý tệp, sao lưu…), không cho tự cập nhật | Bản Play tắt tự cập nhật và bỏ quyền này. Tính năng cài game Android (.apk) phải tắt hoặc chứng minh là "quản lý tệp" [CHƯA KIỂM có được chấp nhận không] |
| 3 | Tải và chạy game Việt hóa (ROM, bản dịch game thương mại) từ aow.vn ngay trong app | Rủi ro vi phạm chính sách sở hữu trí tuệ, dễ bị gỡ và khóa tài khoản | Bản Play chỉ mở file **người dùng tự có** (chọn tệp), đọc bài viết vẫn giữ. Không có nút tải game trong bản Play |

Các app giả lập đang có trên Play (RetroArch, PPSSPP, J2ME Loader…) đều theo mô hình "không kèm game, người dùng tự đưa file vào". Bản Play phải theo đúng mô hình đó.

### 1.2 Các quyền cần xử lý (đã đọc AndroidManifest trên main)

| Quyền | Việc cần làm |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` (truy cập mọi tệp) | Bị hạn chế, phải khai báo lý do trong Play Console. Nên chuyển sang trình chọn tệp Android (SAF) trong bản Play; phần chuyển save đã dùng SAF |
| `REQUEST_INSTALL_PACKAGES` | Bỏ khỏi bản Play (xem điểm chặn 2) |
| `QUERY_ALL_PACKAGES` | Bị hạn chế, phải khai báo; nên bỏ hoặc thay bằng `<queries>` cụ thể |
| `READ/WRITE_EXTERNAL_STORAGE` | Giữ cho Android cũ, giới hạn `maxSdkVersion` |

### 1.3 Yêu cầu kỹ thuật

| Việc | Hiện trạng | Cần làm |
|---|---|---|
| Định dạng | APK universal | Play bắt buộc AAB cho app mới. Thêm task build AAB cho bản Play; bản GitHub vẫn 1 APK universal |
| Target API | targetSdk 35 | Từ 31/08/2026, app mới phải target Android 16 (API 36), có thể xin gia hạn tới 01/11/2026. **Đã quá hạn, phải lên 36** ([nguồn](https://developer.android.com/google/play/requirements/target-sdk)) |
| Ký app | Khóa phát hành riêng (chứng thư c46902e9…) | Dùng Play App Signing. **Quyết định quan trọng:** tải khóa ký hiện tại lên Play (để người dùng bản GitHub cập nhật được qua Play và ngược lại), hay để Google tạo khóa mới (hai bản không cập nhật chéo được). Đề xuất: khóa mới cho Play, đổi `applicationId` bản Play (ví dụ thêm `.play`), để không bao giờ phải đưa khóa gốc ra ngoài |
| Làm rối mã R8 | Đã bật `isMinifyEnabled` + `isShrinkResources` | Giữ nguyên, tải file mapping lên Play để đọc báo lỗi |

### 1.4 Hồ sơ trên Play Console

| Việc | Ai làm | Ghi chú |
|---|---|---|
| Tạo tài khoản nhà phát triển, phí 25 USD một lần, xác minh danh tính | **Sếp** (đụng giấy tờ, thanh toán) | Tài khoản **tổ chức** cần mã D-U-N-S nhưng được miễn bước thử nghiệm kín; tài khoản **cá nhân** phải thử nghiệm kín |
| Thử nghiệm kín (chỉ tài khoản cá nhân mới) | Sếp mời người thử | Ít nhất 12 người thử đăng ký liên tục 14 ngày, tụt dưới 12 là đếm lại từ đầu ([nguồn](https://www.testerscommunity.com/guides/how-many-testers-do-you-need-google-play)) [CHƯA KIỂM trên trang chính thức của Google]. Cộng đồng AowVN dễ gom đủ 12 người |
| Chính sách quyền riêng tư (link công khai) | Đội viết, sếp duyệt | App gửi báo lỗi lên Worker → phải khai. Đặt trên monika.aow.vn |
| Biểu mẫu An toàn dữ liệu (Data safety) | Đội soạn, sếp nộp | Khai: báo lỗi (thông tin thiết bị), không bán dữ liệu |
| Phân loại nội dung (bảng câu hỏi IARC) | Sếp nộp | |
| Khai báo quyền hạn chế | Sếp nộp | Chỉ những quyền còn giữ ở mục 1.2 |
| Trang giới thiệu: tên, mô tả, ảnh, biểu tượng 512px, ảnh bìa 1024×500 | Đội soạn | Không dùng ảnh hay tên game thương mại trong ảnh chụp |
| Ủng hộ / link ngân hàng | Cần sếp quyết | Play hạn chế thanh toán ngoài cho nội dung số. Bản Play nên ẩn link ủng hộ [CHƯA KIỂM chính sách quyên góp hiện hành] |

### 1.5 Lộ trình đề xuất (song song với Thư viện)

| Bước | Việc | Người |
|---|---|---|
| 1 | Sếp chọn loại tài khoản (cá nhân hay tổ chức) và đăng ký | Sếp |
| 2 | V150: tách bản build "play" (Gradle productFlavor): tắt tải game, tắt tự cập nhật, bỏ 3 quyền hạn chế, build AAB, `applicationId` riêng | Sol 2 |
| 3 | V151: đóng gói sẵn lõi cho bản Play (bắt đầu với GBA/NES/NDS/Java; lõi lớn dùng Play Asset Delivery sau) | Nova (sau V138) |
| 4 | V152: nâng targetSdk lên 36, chạy lại test giao diện | Sol |
| 5 | Chính sách quyền riêng tư + Data safety + trang giới thiệu | Haiku (soạn), sếp duyệt |
| 6 | Thử nghiệm kín 14 ngày (nếu tài khoản cá nhân) | Sếp + cộng đồng |
| 7 | Nộp duyệt bản chính thức | Sếp |

Ước lượng: phần kỹ thuật (bước 2–4) khoảng 1–2 tuần làm việc của đội [CHƯA KIỂM, phụ thuộc lõi nào phải đóng gói], cộng 14 ngày thử nghiệm kín nếu là tài khoản cá nhân.

---

## 2. Chống trộm, crack, sao chép trái phép [A66]

### 2.1 REA là gì, vì sao liên quan

[REA (morluto/rea)](https://apidog.com/de/blog/what-is-rea-reverse-engineer-anything/) là công cụ mã nguồn mở (MIT) cho phép AI (Claude Code, Codex, Cursor…) dịch ngược app: chỉ vào một tệp APK, native binary hoặc bản ghi lưu lượng mạng là ra mã giả, sơ đồ module, nhật ký request. Nó nổi trên GitHub Trending đầu tháng 10/2026 ([nguồn](https://aitoolly.com/ai-news/article/2026-10-08-rea-surfaces-on-github-trending-leveraging-ai-agents-to-reverse-engineer-software-from-application-b)). Có bản nhái độc hại mang tên tương tự bị Defender/VirusTotal gắn cờ; đội **không cài** REA, chỉ dùng hiểu biết về cách nó làm việc để phòng thủ.

Hệ quả cho mình: kẻ xấu giờ chỉ cần chỉ AI vào APK Monika là có ngay mọi URL Worker, endpoint, chuỗi bí mật nếu có, cách app xác thực. **Làm rối mã (R8) chỉ làm chậm vài phút.** Bất cứ thứ gì nằm trong APK phải coi như công khai.

Phiên này **không đọc mã nguồn repo REA** (ngoài phạm vi repo được phép); phần trên dựa trên các bài giới thiệu công khai.

### 2.2 Cần bảo vệ cái gì

| Tài sản | Rủi ro | Hiện trạng |
|---|---|---|
| Mã app | Đã công khai (GPLv3), người khác được phép sửa nếu giữ GPL | Không cần giấu; cần **giữ tên và thương hiệu** |
| Worker Cloudflare (config, bản dịch thành tựu, báo lỗi) | Bị gọi dồn làm cạn hạn mức miễn phí; bị giả báo lỗi rác | URL nằm trong APK, không có giới hạn theo IP [CHƯA KIỂM] |
| Link tải game, gói engine | Bị gom link hàng loạt, đăng lại ở nơi khác | Link công khai trong config |
| Khóa ký phát hành | Mất khóa = người khác phát bản "chính chủ" | Đang an toàn (chỉ trong secret GitHub) |
| App nhái (đổi tên, gắn quảng cáo, ký lại) | Người dùng tải nhầm, mất uy tín | Chưa có cách phát hiện |

### 2.3 Kế hoạch (ưu tiên từ trên xuống)

| # | Việc | Cách làm | Ai | Chi phí Worker |
|---|---|---|---|---|
| 1 | Không có bí mật trong APK | Rà APK bằng chính kiểu công cụ dịch ngược (apktool/jadx) xem có token, khóa API, URL quản trị nào lọt không. Có thì chuyển ra Worker | Sol 2 (V145 đang làm, mở rộng) | 0 |
| 2 | Giới hạn tần suất ở Worker | Mỗi IP tối đa N lượt/phút cho config và báo lỗi (Cloudflare Rate Limiting rule miễn phí hoặc đếm trong Worker), trả 429 khi vượt. Giữ được 1000 người dùng thật vì mỗi máy chỉ gọi 1 lần/6 giờ | Sol (Worker ở noibo) | Giảm |
| 3 | App tự kiểm chữ ký | Khi chạy, app so chứng thư ký với chứng thư thật (c46902e9…). Khác thì hiện cảnh báo "Đây không phải bản chính thức của AowVN, tải bản thật tại monika.aow.vn" (không khóa app, vì GPL cho phép bản sửa). Chống app nhái giả danh | Nova | 0 |
| 4 | Báo lỗi có ký tên | Báo lỗi gửi kèm chữ ký chứng thư; Worker bỏ báo cáo từ bản không chính thức, để thống kê lỗi không bị bơm rác | Sol | 0 |
| 5 | Play Integrity API (chỉ bản Play, sau khi lên Play) | Bản Play xin token Integrity khi mở app (tối đa 1 lần/ngày, lưu trên máy). Worker chỉ cấp link tải gói lớn cho token hợp lệ | Sau [A65] | Thêm 1 lượt/ngày/máy |
| 6 | Link tải có thời hạn | Worker cấp URL ký số sống 10–30 phút thay vì link cố định trong config | Để sau, cân nhắc chi phí | +1 lượt mỗi lần tải |
| 7 | Bảo vệ thương hiệu | Ghi rõ trong README và màn Giới thiệu: tên "Aow Monika" và logo không thuộc phạm vi GPL; bản sửa phải đổi tên. Cần sếp hỏi luật sư nếu muốn chặt [CHƯA KIỂM] | Haiku (V136 README) | 0 |
| 8 | Phần nhạy đã ở noibo [A60] | Tiếp tục V144 chuyển script, Worker, quy trình nội bộ sang repo riêng tư | Sol 2 | 0 |

Không làm: tự viết lớp mã hóa/đóng gói chống dịch ngược cho mã app. Tốn công, dễ gây lỗi trên máy yếu, mà mã đã công khai nên không có giá trị.

### 2.4 Cần sếp quyết

1. **Tài khoản Play:** cá nhân (nhanh, nhưng phải thử nghiệm kín 14 ngày với 12 người) hay tổ chức (cần D-U-N-S, miễn thử nghiệm). Đề xuất: **cá nhân**, vì cộng đồng AowVN gom đủ 12 người thử dễ.
2. **Khóa ký bản Play:** khóa mới do Google giữ và `applicationId` riêng (đề xuất), hay tải khóa hiện tại lên Google.
3. **Duyệt lộ trình:** V150–V152 cho Play, việc 1–4 của mục 2.3 làm trước.

Nguồn: [Google target API](https://developer.android.com/google/play/requirements/target-sdk) · [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379?hl=en) · [REQUEST_INSTALL_PACKAGES](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en) · [12 người thử, 14 ngày](https://www.testerscommunity.com/guides/how-many-testers-do-you-need-google-play) · [REA (Apidog)](https://apidog.com/de/blog/what-is-rea-reverse-engineer-anything/) · [REA trending](https://aitoolly.com/ai-news/article/2026-10-08-rea-surfaces-on-github-trending-leveraging-ai-agents-to-reverse-engineer-software-from-application-b)

## 3. Sếp chốt 26/10 11:16 UTC [A67]
- Tài khoản Play **cá nhân**, sếp đã mua. Cần thử kín 12 người × 14 ngày trước khi lên chính thức.
- Khóa ký: **dùng khóa hiện tại** (chứng thư c46902e9…) qua Play App Signing. Bản Play và bản GitHub cùng chữ ký nên việc tự kiểm chữ ký (V154) chỉ cần một chứng thư. Đưa khóa lên Google bằng công cụ PEPK, chỉ sếp làm hoặc duyệt (agent không đụng khóa). Mặc định của PM: `applicationId` riêng `com.aow.monika.play`, để hai bản cài song song không đè nhau.
- Duyệt lộ trình. Thêm yêu cầu: bản Play **ẩn các thành phần đã có** mà Play cấm (tải game, tự cập nhật, link tải ngoài). Cách làm: loại khỏi mã bản Play bằng productFlavor/sourceSet chứ không chỉ ẩn bằng config, vì Play quét mã. Bản GitHub giữ nguyên.
- Dung lượng: V151 đổi từ "đóng gói sẵn lõi" sang **Play Feature Delivery, module tải khi cần**. Mỗi lõi/engine là một module do Play phát (được chứa mã native), người dùng mở hệ máy nào thì Play tải lõi đó, gỡ được khi không dùng. AAB tự tách theo chip (arm64/armv7) nên máy chỉ tải phần của mình. Cỡ module hiện có: azahar 13,1 MB, kirikiri 17,2 MB, rgss 7,9 MB, onsyuri 1,8 MB (config `modules`); giới hạn Play mỗi module 200 MB nén [CHƯA KIỂM bản 2026: trang trợ giúp Google có bản ghi 500 MB].

| Mã | Việc | Ai |
|---|---|---|
| V150 | Tách flavor `play`: loại tải game, tự cập nhật, link ngoài; bỏ 3 quyền hạn chế; AAB; applicationId `.play` | Sol 2 |
| V151 | Lõi/engine bản Play thành module tải khi cần (Play Feature Delivery) | Nova (sau V138) |
| V152 | targetSdk 36 + chạy lại test giao diện | Sol |
| V145 | Rà APK không còn bí mật (đang có) | Sol 2 |
| V153 | Worker giới hạn lượt theo IP/thiết bị (luật 1000 người) | Sol |
| V154 | App tự kiểm chữ ký, cảnh báo bản nhái | Nova |
| V155 | Báo lỗi kèm chữ ký, Worker lọc báo cáo rác | Sol |
| V156 | Nháp chính sách riêng tư, Data safety, mô tả cửa hàng | Haiku |
