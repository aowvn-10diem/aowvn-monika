# Hướng tới: app nhẹ nhất, dễ cài nhất — mọi thành phần tự tải khi chơi lần đầu

> Số đo lấy từ bản build release arm64 ngày 02/10/2026 (cột "nén" = dung lượng trong APK). **[ƯỚC TÍNH]** = suy ra từ phép trừ, chưa đo lại.

## 1. Hiện trạng (arm64, 0.6.2, còn đóng sẵn Azahar trong bản build local)

| Thành phần trong APK | Nén | Ghi chú |
|---|---:|---|
| Engine 3DS (Azahar) `assets/engines/azahar.zip` | 13,0 MB | **Đã chuyển sang tải từ repo phụ** (config v22): bản phát hành kế tiếp không còn |
| Thư viện native `lib/arm64-v8a` | 22,8 MB | Chia nhỏ ở dưới |
| Mã Java/Kotlin (dex) | 6,2 MB | App + lõi J2ME (JL-Mod) + thư viện |
| Còn lại (tài nguyên, phông, cheat…) | ~2 MB | |

Thư viện native lớn nhất (nén): `libc++_shared` 7,8 · `liboboe` 3,0 · `lib7-Zip-JBinding` 2,6 · `libjavam3g` 2,3 · `libarchive-jni` 2,0 · `liblibretrodroid` 1,5 · `libmmapi_eas` 1,4 · ffmpeg (avcodec/avformat/avutil/swscale/mobileffmpeg) ~1,5 · `libmmapi_tsf` 0,3.

**Đã tự tải sẵn (không nằm trong APK):** lõi libretro (tải lần đầu chơi từng hệ), Azahar (từ bản kế tiếp), OCR dịch màn hình (Google Play Services), mô hình dịch offline (kế hoạch riêng).

## 2. Mục tiêu và ước tính
- **APK nền arm64 ≈ 20–25 MB [ƯỚC TÍNH]** (hiện bản CI 0.6.2 ≈ 44,4 MB cho arm64 trước khi gỡ Azahar → ~31 MB sau khi gỡ; phần còn lại giảm bằng các bước dưới).
- Người dùng chỉ phải: cài 1 file → mở app → chọn game → app **tự tải đúng thứ cần** (có thanh tiến độ, báo dung lượng, tiếp tục được khi mất mạng).

## 3. Các bước (từ dễ đến khó, mỗi bước độc lập)

| # | Việc | Giảm APK | Độ khó | Ghi chú |
|---|---|---:|---|---|
| 0 | **Phát hành bản mới không đóng Azahar** | ~13 MB | Rất dễ | Đã sẵn (config v22 + release.yml); chỉ cần phát hành |
| 1 | **Phân phối APK riêng theo kiến trúc: tải bản arm64 làm mặc định** (universal chỉ là phương án dự phòng) | ~19–20 MB so với universal **[ƯỚC TÍNH]** | Dễ | Gần như mọi máy hiện nay là arm64; tỉ lệ máy 32-bit của người dùng AowVN **[CHƯA KIỂM]**. Workflow Release đã dựng sẵn `app-arm64-v8a`, `app-armeabi-v7a`, `app-universal`; chỉ cần chọn file đưa lên Pixeldrain/Release |
| 2 | **Gói "Giải nén" (7-Zip-JBinding + libarchive)** tải khi lần đầu nhập file nén | ~4,6 MB | Vừa | Nạp `.so` ngoài APK (như `AzaharModule`); hoặc thay phần zip bằng thư viện Java thuần, giữ native chỉ cho 7z/rar |
| 3 | **Gói "Game Java" (natives của JL-Mod: m3g, eas, tsf, ffmpeg, oboe riêng)** tải khi lần đầu mở game Java | ~7–9 MB **[ƯỚC TÍNH]** | Vừa–khó | Đổi `System.loadLibrary` → nạp từ thư mục gói; kiểm tên thư viện nạp trong J2ME |
| 4 | **Chuyển mã Java của J2ME ra khỏi dex chính** (DexClassLoader) | ~2–3 MB **[ƯỚC TÍNH]** | Khó | Chỉ làm nếu sau bước 1–3 vẫn muốn nhẹ hơn; rủi ro R8/classloader cao |
| 5 | Dọn tài nguyên (WebP đã làm; kiểm phông, cheat index 0,2 MB nén) | < 1 MB | Dễ | Gần đủ |

⇒ Thứ tự đề xuất: **0 → 1 → 2 → 3**. Sau đó APK nền arm64 ≈ 17–20 MB **[ƯỚC TÍNH]**.

## 4. Hạ tầng chung "gói tải theo nhu cầu" (làm một lần, dùng cho mọi gói)
Hiện có: `CoreManager` (lõi libretro), `AzaharModule` (engine 3DS). Tổng quát hóa thành `PackManager`:
1. **Danh mục gói trong config** (`modules.<tên>`: url, version, sha256, kích thước, abis, `minAppVersion`). Cùng nguồn: repo phụ công khai `aowvn-10diem/aowvn-monika-packs`.
2. **Tải an toàn:** kiểm **SHA-256** trước khi dùng (hiện `AzaharModule` chưa kiểm), ghi vào file tạm rồi đổi tên (không để gói dở), **tiếp tục tải khi mất mạng** (HTTP Range), thử lại.
3. **Trải nghiệm lần đầu:** khi bấm Chơi mà thiếu gói → hộp thoại "Cần tải X MB để chơi game này" (nút Tải / Để sau; gói > 15 MB: hỏi "Đợi Wi-Fi" hoặc "Tải luôn bằng 4G", xem mục 7), thanh tiến độ, tự vào game khi xong. Có tùy chọn **"Tải sẵn tất cả"** trong Cài đặt (đã có `CorePrefetchWorker` cho lõi).
4. **Quản lý dung lượng:** màn Cài đặt → Bộ nhớ liệt kê gói đã tải + dung lượng + nút xóa (đã có phần xóa lõi tự động ở `StorageSettings`).
5. **Chẩn đoán:** mọi lỗi tải/nạp gói đi qua `Diagnostics` với nhãn `pack:<tên>` (khớp kế hoạch crash log theo thành phần).
6. **Kiểm:** unit test (SHA sai bị từ chối, tải dở không để lại gói hỏng, tiếp tục tải), Emulator Test không tải gì khi chưa dùng tính năng.

## 5. Về "dễ cài"
- Cài vẫn là **1 file APK** (nguồn không rõ: Android bắt người dùng bật quyền cài ứng dụng ngoài Google Play; app không thay đổi được việc này).
- Trang tải nên có **một nút "Tải bản cho máy bạn"**: bản arm64 mặc định, kèm liên kết "Máy cũ 32-bit" (armeabi-v7a) và "Bản đầy đủ" (universal).
- Ký APK bằng **cùng một khóa** để người dùng cập nhật đè được (đã làm).

## 6. Rủi ro
| Rủi ro | Cách xử lý |
|---|---|
| Người dùng mất mạng lúc chơi lần đầu | Báo rõ cần mạng; tiếp tục tải; có "Tải sẵn" khi có Wi-Fi |
| Repo phụ bị GitHub giới hạn băng thông/tốc độ | Giữ tùy chọn chuyển sang Cloudflare R2 chỉ bằng đổi `url` trong config |
| Gói lỗi/độc hại bị thay | Kiểm SHA-256 lấy từ config (config đi qua Worker của AowVN) |
| Nhiều gói làm tăng độ phức tạp hỗ trợ | Một `PackManager` duy nhất, mọi gói cùng cách tải/báo lỗi |

---
## 7. Tải trước thông minh ("tải phần đằng sau ngay trong lúc đang tải")

**Hiện có:** `CorePrefetchWorker` tải lõi libretro (a) lần đầu mở app theo `prefetchCores` trong config, (b) **sau khi** giải nén xong game mới (`LibraryScreen` dòng ~139). Tức là bước tải phụ chỉ bắt đầu **khi game đã tải xong** — chậm hơn cần thiết.

**Mục tiêu:** ngay khi người dùng bắt đầu một việc, đoán được "phần đằng sau" cần gì và tải song song với việc đang làm.

### 7.1 Tín hiệu để đoán (rẻ → chắc)
| Thời điểm | Tín hiệu | Đoán ra |
|---|---|---|
| Mở bài viết game (`PostScreen`) | Nhãn bài (`labels`, vd. "Game NDS Việt Hóa") khớp `systems[].labels` | Hệ máy → lõi/engine của hệ đó |
| **Bấm tải** (`BrowserDownloads.start` / `Downloader.enqueue`) | `GameMeta.labels` đi kèm; đuôi/mime/tên file (`.zip .7z .rar` → cần giải nén; `.jar .jad` → game Java; `.apk .xapk` → trình cài APK; `.iso .cso`, `.nds`…) | Gói cần: **giải nén**, **game Java**, lõi đúng hệ, engine 3DS… |
| Đang tải (có `Content-Disposition` → tên thật) | Tên file chính xác hơn | Hiệu chỉnh đoán (vd. tên `.7z`) |
| Tải xong, trước giải nén | Nhìn vào bên trong file nén (danh sách entry) | Hệ máy thật → lõi |

### 7.2 Thành phần mới `PrefetchPlanner` (một chỗ duy nhất)
- `fun plan(signals): List<PackRef>` — hàm thuần (dễ test): nhận nhãn bài + tên/đuôi file + mime, trả danh sách gói cần (cores, `archive`, `java`, `azahar`…). Bảng ánh xạ đuôi/nhãn → gói nằm trong **config** (sửa không cần APK mới).
- `PackManager.prefetch(refs, reason)` — xếp hàng, **loại trùng** (đang tải/đã có thì bỏ qua), ưu tiên gói nhỏ trước, **không tranh băng thông** với file game đang tải (giới hạn song song 1–2, hạ ưu tiên khi game đang tải).
- Móc vào: `PostScreen` (mở bài), `BrowserDownloads.start`, `Downloader.enqueue`, `ImportWorker` (nhập file từ máy), `GameLauncher` (bấm Chơi mà vẫn thiếu → tải ngay, hiện hộp tiến độ).

### 7.3 Quy tắc để không phí / không gây khó chịu
1. **Mạng (đã chốt 02/10/2026):** gói ≤ 15 MB **tự tải ngay cả trên 4G**. Gói > 15 MB: hỏi người dùng 1 lần cho mỗi gói — **"Đợi Wi-Fi"** hoặc **"Tải luôn bằng 4G"**. Chọn "Đợi Wi-Fi" → gói vào hàng chờ; `ConnectivityManager.NetworkCallback` phát hiện máy chuyển sang Wi-Fi (mạng không tính phí) thì **tải ngay**, không cần mở lại app (WorkManager ràng `NetworkType.UNMETERED` làm dự phòng khi app bị tắt). Chọn "Tải luôn" → tải ngay trên 4G. Hỏi bằng thông báo/hộp thoại không chặn, ghi nhớ lựa chọn cho gói đó. Tôn trọng chế độ Tiết kiệm dữ liệu của Android (khi bật: gói ≤ 15 MB cũng hỏi). Cài đặt: "Tải trước thành phần: Tự động / Chỉ Wi-Fi / Tắt".
2. **Đoán sai thì rẻ:** gói đoán sai chỉ là tải thừa; ghi lại tỉ lệ đoán đúng (số liệu ẩn danh trong báo cáo lỗi nếu bật) để chỉnh bảng ánh xạ. Gói > 15 MB mới đoán từ nhãn bài (chưa có tín hiệu chắc như tên file) thì chưa hỏi, chỉ hỏi khi tín hiệu đủ chắc; trên Wi-Fi thì tải luôn.
3. **Hiển thị:** một dòng phụ trong màn Tải xuống ("Đang chuẩn bị bộ giải nén…", có thể hủy); không thông báo ồn ào; xong thì im lặng.
4. **Hủy gọn:** người dùng hủy tải game → hủy tải trước nếu chưa dùng tới.
5. **An toàn:** vẫn kiểm SHA-256 (mục 4); không thực thi gì từ gói trước khi kiểm xong.

### 7.4 Thứ tự làm
1. **Làm ngay được (không cần gói mới):** đưa việc tải lõi libretro lên **lúc bắt đầu tải/mở bài** thay vì sau khi giải nén; thêm hàm thuần `PrefetchPlanner.plan` + test. (Hiệu quả thấy ngay vì lõi nặng 2–20 MB.)
2. **Cùng `PackManager`:** móc cho Azahar (khi có game 3DS), "Giải nén", "Game Java" (các bước 2–3 mục 3).
3. Kiểm: unit test bảng ánh xạ (nhãn/đuôi → gói), test không tải trùng, Emulator Test không tải khi chưa có tín hiệu.

---
## 8. Tiến độ & phát hiện mới (02/10/2026, giờ GMT+7)

| Việc | Trạng thái |
|---|---|
| Bỏ Azahar khỏi APK, tải từ repo phụ + kiểm SHA-256 | ✅ 0.6.3 |
| `PackManager` + quy tắc mạng (≤15 MB tự tải kể cả 4G; lớn hơn hỏi Đợi Wi-Fi / Tải luôn; chờ Wi-Fi bằng WorkManager UNMETERED) | ✅ 0.6.4 |
| `PrefetchPlanner` + tải lõi/Azahar/7-Zip ngay lúc bấm tải | ✅ 0.6.4 |
| Gói "Giải nén" phần 7-Zip-JBinding (−2,6 MB/ABI) | ✅ 0.6.4 (`modules.sevenzip`, workflow **Publish pack**) |
| **Cắt ký hiệu debug thư viện native** (log build báo "Unable to strip"): app khai báo cùng NDK 22.1 với `:j2me` | ✅ commit 7e2a08e, chờ bản phát hành để đo |
| libarchive-jni (2,0 MB) | ⛔ không tách được bằng gói: lớp `Archive` tự `System.loadLibrary` lúc khởi tạo. Cần thay bằng bộ giải zip thuần Java hoặc vá thư viện |
| Gói "Game Java" | ❌ **bỏ**: sau khi cắt ký hiệu, toàn bộ native của J2ME chỉ còn ~1,3 MB (javam3g 0,38 · eas 0,60 · oboe 0,27 · tsf 0,05 · còn lại nhỏ) — không đáng độ phức tạp |

**Phát hiện:** các con số 7–9 MB của "Game Java" trong bảng mục 3 là số **chưa cắt ký hiệu**. Đo thử trên APK 0.6.2 arm64 bằng `llvm-strip --strip-unneeded`: tổng thư viện 22,8 MB → ~8,9 MB (**−13,9 MB**), riêng `libc++_shared` 7,8 → 1,0 MB, `liboboe` 3,0 → 0,27 MB, `libjavam3g` 2,3 → 0,38 MB. Đây là cách giảm lớn nhất và không đổi hành vi.
