# AowVN Monika — ghi chú cho Claude

App Android (Kotlin, Jetpack Compose) của aow.vn: đọc bài (Blogger feed), thông báo bài mới, tải và chạy game.
Chủ repo giao tiếp tiếng Việt; tài liệu và chuỗi giao diện viết tiếng Việt.

## Nguyên tắc thiết kế (bắt buộc giữ)

- **Config-first**: thứ gì có thể thay đổi (link, lõi, hệ máy, host, hướng dẫn) phải nằm trong `config/monika-config.json`, không hard-code. Trường mới trong `MonikaConfig.kt` luôn có giá trị mặc định (tương thích config cũ).
- `config/` là nguồn duy nhất: Gradle đóng gói chính file này vào assets (`sourceSets ... srcDirs("../config")`).
- Thêm hệ/trình chạy: sửa config + (nếu runner mới) thêm nhánh trong `runner/GameLauncher.kt`. Cập nhật `ConfigTest` nếu thêm loại runner.
- Tất cả thành phần tạo trong `AppGraph.kt` (DI thủ công, không thêm Hilt/Koin).
- Phiên bản thư viện chỉ sửa trong `gradle/libs.versions.toml`.
- Giao diện theo design spec "Aow Monika" (nền cream, charcoal, gradient cam–hồng–tím, bo lớn, menu nổi `ui/theme/FloatingDock.kt`). Màn hình chỉ dùng token `Monika.colors/type/motion`, `Radius`, `primaryGradient()` trong `ui/theme/Theme.kt` và thành phần trong `ui/theme/Components.kt`. Font Manrope (OFL) `res/font/manrope.ttf`. Icon: Fluent System Icons (MIT) chép lẻ `res/drawable/ic_fluent_*`; minh họa 3D Fluent Emoji `res/drawable-nodpi/fluent3d_*`.
- Chuyển động tự điều chỉnh: `PerformanceTier` FULL/LITE/OFF (`detectTier`: RAM < 3GB hoặc < 6 nhân → LITE; tỉ lệ hoạt ảnh Android = 0 → OFF; user chọn được trong Cài đặt). Mọi animation phải lấy thời lượng từ `Monika.motion`.
- Màn chơi game: `runner/RetroActivity.kt` (GLRetroView) + `runner/GamePadOverlay.kt` (Compose nổi). Phím theo vị trí Android: dưới=BUTTON_A, phải=BUTTON_B, trái=X, trên=Y.
- Không bao giờ commit keystore/mật khẩu ký; chỉ dùng GitHub Secrets.

## Sự thật đã xác minh

- Feed: `https://www.aow.vn/feeds/posts/default?alt=json&orderby=published`, bài lẻ: `/feeds/posts/default/{postId}?alt=json`. Blog ID `4482370512868492154`.
- aow.vn đứng sau Cloudflare với rule chặn IP ngoài VN/LA/CU. Máy chủ Claude chỉ được mở riêng đường dẫn `/feeds/` (rule skip theo IP). Vì vậy thông báo chạy **trong app** (WorkManager, IP user VN), không dùng server/GitHub Action nước ngoài.
- LibretroDroid 0.14.0 (JitPack): `GLRetroView(context, GLRetroViewData)`, `serializeSRAM()`, `getGLRetroErrors()`.
- Feed trả NGUYÊN bài (không bị cắt). Trang tĩnh đọc qua `/feeds/pages/default?alt=json` (dùng cho trang tải giả lập aow.vn/p/...).
- Pixeldrain: `/u/{id}` → `/api/file/{id}?download`; `/d/{id}` → `/api/filesystem/{id}?attach` (đã thử, trả file + tên tiếng Việt qua filename*=UTF-8).
- Mật khẩu file nén aow.vn: `aowvn.org` (ghi cuối bài).
- libarchive (me.zhanghai.android.libarchive): giải nén bằng `readOpenFd` + `readDataIntoFd`; `readNextHeader` trả 0 khi hết file.
- Lõi libretro Android: `https://buildbot.libretro.com/nightly/android/latest/{abi}/<core>_libretro_android.so.zip`.

## Lệnh

- `./gradlew testDebugUnitTest` — test config/logic (chạy trước mọi push).
- `./gradlew assembleDebug` — build APK.

## Việc còn lại (theo thứ tự ưu tiên)

1. Link chính thức cho app ngoài (chủ repo cung cấp) → điền `externalApps[].downloadUrl`, xác nhận `packageNames`.
2. Tay cầm ảo đẹp hơn (RadialGamePad), layout riêng cho NDS 2 màn hình.
3. Save state, cài đặt lõi (core options), chọn lõi melonDS/desmume.
4. Giải nén: đã có zip/rar/rar5/7z (libarchive 1.1.6; 1.1.7 đòi compileSdk 37). Còn thiếu: file chia nhiều phần, 7z có mật khẩu, thanh % tiến độ. Chưa test trên máy thật.
5. Nhúng J2ME Loader, ONScripter (hiện chạy qua app ngoài).
6. Port Ren'Py, mkxp-z (RPG Maker XP/VX/Ace) — khó, làm sau.
