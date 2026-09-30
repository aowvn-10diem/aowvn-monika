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
- Game Java: J2ME Loader nhúng ở module `j2me/` + `dexlib/` (Apache-2.0, bản gốc commit 9b0fa48). Chỉ sửa chỗ có chú thích `Aow Monika:`; chi tiết + cách cập nhật: `docs/J2ME-LOADER.md`. Module này giữ build.gradle riêng (Groovy), ngoại lệ của quy tắc version catalog. Cần NDK 22.1.7171670.
- Bản release bật R8 + build tách APK theo chip (arm64-v8a, armeabi-v7a, universal; bỏ x86) nhưng **mỗi phiên bản chỉ phát hành 1 APK: bản universal** (`AowVN-Monika-<tag>.apk`, quyết định của sếp). Thêm thư viện có JNI/reflection → thêm luật `-keep` vào `app/proguard-rules.pro`, rồi kiểm lớp còn trong dex bằng `build-tools/*/dexdump`.
- Giải nén: libarchive → lỗi thì 7-Zip native (`library/SevenZipNative.kt`, RAR/RAR5 mật khẩu, nhiều phần) → 7z thuần Java. Test chạy `.so` Linux cùng bản 16.02 (Gradle tự giải nén vào build/sevenzip-natives); file mẫu RAR tạo bằng `rar` với `LC_ALL=C.UTF-8` (không thì tên tiếng Việt hỏng).
- Config từ xa có `configVersion` thấp hơn bản trong APK bị bỏ qua. Nhớ tăng `configVersion` và đồng bộ Cloudflare khi sửa config.
- Không bao giờ commit keystore/mật khẩu ký, không in chúng ra log/chat. Cách ký hiện tại: xem mục "Phát hành APK".

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
- Test giao diện (Robolectric, trong `testDebugUnitTest`): `app/src/test/.../ui/` mở từng màn + lớp phủ trong game, ảnh ra `app/build/screenshots/` (CI: artifact `anh-chup-giao-dien`). Đồng hồ Compose chỉnh tay (`autoAdvance = false`) để không flaky. Máy thật: `docs/TEST-MAY-THAT.md`; cloud: `docs/TEST-LAB.md`.
- `PIXELDRAIN_API_KEY=... scripts/pixeldrain-upload.sh <apk>` — up APK lên Pixeldrain, in link `pixeldrain.com/u/{id}` (APK > 30 MB không gửi qua chat được). Key do chủ repo đưa, không ghi vào repo. Release CI tự up nếu có secret `PIXELDRAIN_API_KEY`.

## Phát hành APK — đọc hết trước khi báo "thiếu" bất cứ thứ gì

Repo **chưa có** GitHub Secrets ký app (`MONIKA_KEYSTORE_*`) và cũng không có `PIXELDRAIN_API_KEY`, nên workflow Release trên CI đỏ ở bước ký. Từ trước tới nay APK luôn được **build và ký ngay trên máy phiên**, không qua CI. Không có biến môi trường ký cũng là bình thường.

1. **Khóa ký** nằm trong thư mục scratchpad của phiên: `<scratchpad>/keystore/` gồm `aowvn-release.jks`, `aowvn-release.jks.base64` và `THONG-TIN-KHOA.txt` (các dòng `ALIAS:`, `STORE PASSWORD:`, `KEY PASSWORD:`). Nạp vào biến môi trường **mà không in ra**:
   ```bash
   K=<scratchpad>/keystore; f=$K/THONG-TIN-KHOA.txt; v(){ grep -m1 "^$1:" "$f" | sed -E "s/^$1:[[:space:]]*//"; }
   MONIKA_KEYSTORE=$K/aowvn-release.jks MONIKA_KEYSTORE_PASSWORD="$(v 'STORE PASSWORD')" \
   MONIKA_KEY_ALIAS="$(v ALIAS)" MONIKA_KEY_PASSWORD="$(v 'KEY PASSWORD')" ./gradlew assembleRelease
   ```
   Scratchpad nằm trong `/tmp`, sẽ **mất khi container bị thu hồi**. Mất khóa thì mọi bản sau phải gỡ app cài lại, nên đừng tạo khóa mới. Hỏi chủ repo, và nhắc chủ repo đưa khóa vào GitHub Secrets (`docs/CAP-NHAT.md`).
2. **Kiểm chữ ký** sau khi build: `$SDK/build-tools/*/apksigner verify --print-certs` (lấy SDK trong `local.properties`, vì `$ANDROID_HOME` trống). SHA-256 đúng là `c46902e9…d45ab20c`. Khác số này thì chủ repo phải gỡ app cũ, **không được gửi**.
3. **Engine 3DS (Azahar)**: repo private nên app không tải được asset trong Releases. Phải chép `azahar-android-arm64.zip` của release `engine-azahar-*` mới nhất vào `app/src/main/assets/engines/azahar.zip` trước khi build (thư mục này đã gitignore). Tải asset bằng API kèm `$GH_TOKEN` (`Accept: application/octet-stream`). Link `browser_download_url` không kèm token sẽ trả "Not Found".
4. **Pixeldrain**: key do chủ repo đưa và đồng ý dùng lại cho mọi lần upload. Key được lưu ở `<scratchpad>/keystore/pixeldrain.key`, dùng bằng `PIXELDRAIN_API_KEY=$(cat <scratchpad>/keystore/pixeldrain.key) scripts/pixeldrain-upload.sh <apk>`. Key cũng được chủ repo đưa vào GitHub Secret `PIXELDRAIN_API_KEY` để CI dùng. Không có file đó thì hỏi lại chủ repo. **Đừng lục lịch sử hội thoại** để lấy key vì bị chặn, và cũng không nên.
7. Token GitHub của phiên **không tạo được Secrets** (API trả 403). Secret phải do chủ repo tự thêm ở Settings → Secrets and variables → Actions.
5. **Đẩy tag bị proxy của phiên chặn** (`git push origin v…` báo "remote end hung up"). Push nhánh `main` vẫn bình thường. Release trên CI chạy tay bằng `workflow_dispatch` (tham số `tag`).
6. Lỗi "auto mode classifier gave no verdict" là **sự cố máy chủ kiểm duyệt**, không phải lỗi lệnh. Đừng suy ra thiếu công cụ hay quyền. Chuyển sang Read/Edit/Grep, rồi thử lại Bash sau.

## Việc còn lại (theo thứ tự ưu tiên)

1. Link chính thức cho app ngoài (chủ repo cung cấp) → điền `externalApps[].downloadUrl`, xác nhận `packageNames`.
2. ĐÃ CÓ: tay cầm theo hệ máy, kéo đổi chỗ + cỡ (lưu Prefs), save/load state 1 slot, ảnh bìa game từ bài viết (`library/GameMeta.kt`, file `.monika.json` trong thư mục game). ĐÃ CÓ thêm: 3 ô save, chọn lõi NDS, tùy chọn lõi (`runner/CoreOptions.kt`: đọc `GLRetroView.getVariables()` dạng "Tên; a|b|c", lưu theo lõi; mặc định trong config `cores.<id>.options` — chưa điền key nào, cần xác nhận key thật của từng lõi).
3. Chưa test trên máy thật — mọi thứ mới chỉ build + unit test.
4. Giải nén: zip/rar/rar5/7z (libarchive 1.1.6; 1.1.7 đòi compileSdk 37), file chia nhiều phần, % tiến độ (thông báo + màn Thư viện). 7z có mật khẩu: libarchive lỗi → `SevenZipExtractor` (Commons Compress, test `SevenZipTest` với file mẫu `app/src/test/resources/7z/`). Chưa test trên máy thật.
5. ĐÃ nhúng J2ME Loader (runner "j2me") + phím ảo kiểu Monika. Còn: ONScripter (native C++ + SDL, cần high).
6. Port Ren'Py, mkxp-z (RPG Maker XP/VX/Ace) — khó, làm sau.
