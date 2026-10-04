---
name: them-he-may
description: Thêm hệ máy / trình chạy / lõi libretro / link tải / host / mật khẩu vào Aow Monika theo kiểu config-first (config/monika-config.json + GameLauncher + ConfigTest). Dùng khi thêm hoặc sửa hệ, lõi, app ngoài, hướng dẫn, tay cầm ảo, tùy chọn lõi.
---

# Thêm / sửa hệ máy (config-first)

Mọi thứ thay đổi được (link, lõi, hệ máy, host, hướng dẫn) nằm trong `config/monika-config.json`, không hard-code. Trường mới trong `MonikaConfig.kt` luôn có giá trị mặc định (tương thích config cũ). `config/` là nguồn duy nhất (Gradle đóng gói chính file này vào assets: `sourceSets ... srcDirs("../config")`). Sửa file → **tăng `configVersion`** (config từ xa thấp hơn bản trong APK bị bỏ qua) → push `main` (`sync-config.yml` đẩy lên Cloudflare KV, cần secret `CLOUDFLARE_API_TOKEN`; app đọc `https://aowvn-monika.aowvn-system.workers.dev/config.json`; mã Worker `cloudflare/worker.js`). Không cần phát hành APK.

## Thêm hệ dùng libretro
1. Thêm lõi vào `cores` (link `https://buildbot.libretro.com/nightly/android/latest/{abi}/<core>_libretro_android.so.zip`, `{abi}` thay cho `arm64-v8a`…).
2. Thêm hệ vào `systems` với `"runner": "libretro"`, `core`, `extensions`, `labels` (nhãn trên blog); `altCores` là lõi thay thế khi lõi mặc định không chạy trên kiến trúc máy.
3. Nâng cấp lõi: đổi `version` của lõi (app tải lại lần chơi tới); đổi lõi của 1 hệ: sửa `core` trong `systems`.
4. Kiểm: `python3 scripts/audit-cores.py [abi…]` (link sống, ABI, phụ thuộc, khóa/giá trị tùy chọn có thật trong lõi), `python3 scripts/check-config-links.py`.

## Thêm trình chạy mới (runner)
Runner hiện có: `libretro`, `web`, `apk`, `j2me`, `external` (nhánh trong `runner/GameLauncher.kt`, hàm `launch`). Runner mới → thêm 1 nhánh trong `when (system.runner)` **và** cập nhật `ConfigTest` (`runner config nam trong nhanh cua GameLauncher`). Engine nhúng kiểu "module + gói tải thêm" (Kirikiri, Ren'Py, RGSS): đăng ký 1 dòng ở `EngineRoutes.all` + gói trong `PackManager` + `modules.<id>` (xem `dung-engine`). Tất cả thành phần tạo trong `AppGraph.kt` (DI thủ công, không thêm Hilt/Koin). Phiên bản thư viện chỉ sửa trong `gradle/libs.versions.toml`.

## Các mục config khác (`docs/CAP-NHAT.md` mục A)
- **App ngoài** (`externalApps[]`): `downloadUrl` nên là trang aow.vn (app đọc trang, hiện nút tải theo chữ trên nút; Pixeldrain → tải + cài trong app; `.apk` → tự tải; link web → mở trình duyệt); `guide` = mỗi dòng 1 bước; `packageNames` để nhận biết đã cài. Việc còn lại: chủ repo đưa link chính thức → điền `downloadUrl`, xác nhận `packageNames`.
- **Host tải** (`downloadHosts`): chỉ mở trình duyệt `{name,host,mode:"browser"}`; tải thẳng `{host,mode:"direct",pattern,directUrl}` (Pixeldrain: `pattern "/u/([A-Za-z0-9]+)"`, `directUrl "https://pixeldrain.com/api/file/$1?download"`).
- **Nhận diện game dạng thư mục**: thêm quy tắc vào `engines`: `markers` (file đặc trưng) + `entry` (file mở để chạy).
- **Mật khẩu file nén**: `archivePasswords` (vd. `["aowvn.org"]`).
- **Báo bản app mới**: khối `app` (`latestVersionCode`, `latestVersionName`, `apkUrl`, `changelog`).
- Hệ `external` có `allowExternalApp` (mặc định true); `false` thì máy không chạy được engine nhúng sẽ báo lỗi chứ không mở app ngoài.

## Màn chơi game libretro
`runner/RetroActivity.kt` (GLRetroView) + `runner/GamePadOverlay.kt` (Compose nổi). Phím theo vị trí Android: dưới=BUTTON_A, phải=BUTTON_B, trái=X, trên=Y. Tay cầm theo hệ máy (`system.pad`), kéo đổi chỗ + cỡ (lưu Prefs), save/load state 3 ô, chọn lõi NDS, tùy chọn lõi (`runner/CoreOptions.kt`: đọc `GLRetroView.getVariables()` dạng "Tên; a|b|c", lưu theo lõi; mặc định trong config `cores.<id>.options` — chưa điền key nào, cần xác nhận key thật của từng lõi). LibretroDroid 0.14.0 (JitPack): `GLRetroView(context, GLRetroViewData)`, `serializeSRAM()`, `getGLRetroErrors()`; chi tiết `docs/LIBRETRODROID.md`. 3DS: engine nhúng Azahar (`azahar/`), `system.engine`.
