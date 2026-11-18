# V203 — Lộ trình nâng `targetSdk 36`, AGP `9.4.1` và Kotlin `2.4.21` (Cập nhật `main@4c4757b1` — 18/11/2026)

> **Loại:** kỹ thuật — không đổi giao diện (bù inset thanh điều hướng cho Android 16)

## Mục tiêu

1. Đưa Aow Monika lên **Android 16 (`targetSdk 36`, `compileSdk 36`)** kịp yêu cầu phát hành Google Play `[A65]` mà không làm vỡ đường phát hành APK cầu nối của chiến dịch `[A85]`.
2. Nâng tuần tự **Gradle `9.6.0` + AGP `9.4.1` (Built-in Kotlin)** để gỡ sạch **48 cảnh báo Dependabot** ở classpath plugin build (`netty`, `protobuf-java`, `commons-io`, `guava` — `docs/bao-mat/PHU-THUOC-0811.md` §13), tiếp theo là **Kotlin `2.4.21`** và **NDK `r28` căn lề 16 KB** (trước hạn Play `01/02/2027`) mà không làm gãy bất kỳ module giả lập/đọc sách nhúng nào (`:kirikiri`, `:rgss`, `:renpy`, `:j2me`, `:libretrodroid`, `:loader`, `:reader`).

---

## Phán quyết của Argon (18/11/2026)

### 1. Làm ngay hay chờ (sau A85 hay song song)?

| Giai đoạn | Nội dung | Phán quyết thời điểm | Lý do & Bằng chứng |
|---|---|---|---|
| **Giai đoạn A (`targetSdk 36` trên AGP `8.7.3`)** | PR `#643` + PR 2 (vá phụ `loader` & `BrowserDownloadService` + dọn `kotlinOptions`) | **LÀM NGAY (Song song A85)** | `#643` (`head 5d8b210`) đã xanh **11/11 check CI** (`build`, `coverage`, `gate`, `merged-manifest`, `CodeQL`), không đụng AGP/Kotlin nên rủi ro vỡ build bằng `0`, giải quyết ngay yêu cầu `targetSdk 36` cho bản Play `[A65]`. |
| **Giai đoạn B (`Gradle 9.6.0` + `AGP 9.4.1` + `Kotlin 2.4.21` + `NDK r28`)** | PR 3 → PR 4 → PR 5 bên dưới | **CHỜ SAU KHI ĐÓNG A85** *(sau khi phát hành APK cầu nối G3 `v0.8.15` và đóng `aowvn-monika-packs`)* | 48 cảnh báo Dependabot còn lại **100% nằm ở công cụ build trên máy CI, 0 cảnh báo trong APK** (`docs/bao-mat/PHU-THUOC-0811.md` §13). Trong khi đó, AGP 9 xóa plugin `kotlin-android` và đổi DSL trên cả 9 module; nếu làm ngay lúc đang cắt APK cầu nối A85 sẽ khóa đường phát hành khẩn cấp. |

### 2. Kết luận dứt khoát về 3 PR đang treo (`#643`, `#216`, `#217`)

| PR | Tác giả / Nhánh | Kết luận | Hành động cụ thể cho PM & Đội OpenCode |
|---|---|---|---|
| **`#643`** (`Bản rebase của PR 431 - targetSdk 36`) | `rebound/V152-target36` (`head 5d8b210`) | **GỘP NGAY** | Đã rebase sạch, `mergeable: MERGEABLE`, xanh 11/11 check CI, đã đổi `app/build.gradle.kts:187` sang `android-36/android.jar`, phủ `dockContentClearance()` và thêm 5 bộ test API 36. Sau khi gộp `#643`, giao phiên Thi công mở **PR 2** bên dưới để khép kín 2 điểm còn sót (`loader/build.gradle.kts:16` và `BrowserDownloadService.onTimeout`). |
| **`#216`** (`Bump kotlin from 2.0.21 to 2.4.21`) | `dependabot` (`head 4e5f949`) | **ĐÓNG NGAY** | Đỏ 6/6 job CI từ 12/10. Chỉ sửa 1 dòng `gradle/libs.versions.toml`; AGP `8.7.3` hiện tại không tương thích KGP `2.4.21` và nổ lỗi `kotlinOptions`. Thay bằng **PR 4** trong lộ trình dưới đây. |
| **`#217`** (`Bump com.android.application from 8.7.3 to 9.4.1`) | `dependabot` (`head ce0ac6a`) | **ĐÓNG NGAY** | Đỏ 5/5 job CI từ 12/10. Chỉ sửa 1 dòng `gradle/libs.versions.toml`; thiếu Gradle `9.6.0` trong `gradle-wrapper.properties`, thiếu `apksig = 9.4.1`, và đụng độ Built-in Kotlin với `alias(libs.plugins.kotlin.android)` ở 4 module. Thay bằng **PR 3** trong lộ trình dưới đây. |

---

## Phạm vi

- **Trong phạm vi:**
  - Cập nhật lộ trình theo đúng cây thư mục `main@4c4757b1` (9 module trong `settings.gradle.kts`, **đã bao gồm module mới `:reader`** được gộp từ PR `#457` ngày 11/11).
  - Nâng `targetSdk 36`, JDK 21 trên CI, Gradle `9.6.0` + AGP `9.4.1` (Built-in Kotlin), Kotlin `2.4.21` + R8 `9.x`, và NDK `28.2.13676358` (căn lề 16 KB page size).
- **Ngoài phạm vi:**
  - Không viết lại mã gốc giả lập trong `:kirikiri`, `:rgss`, `:renpy`, `:j2me`; không đổi định dạng save, phím ảo hay `config/monika-config.json`.
  - **Cấm nâng `compileSdk` của `:kirikiri` vượt `33`** (`kirikiri/build.gradle:10`) vì `KR2Activity.getDeviceId()` trả `String` sẽ đụng chữ ký `int Context.getDeviceId()` của Android 14+ (API 34+).

---

## Luồng hoạt động & Thứ tự 5 PR cập nhật theo `main@4c4757b1`

> So với bản nháp ngày 09/11 (6 PR), PR `#643` đã hoàn thành phần cốt lõi của `targetSdk 36` + toolchain JVM 21 cho Robolectric, đồng thời `main` có thêm module `:reader` (`reader/build.gradle.kts`). Vì vậy lộ trình rút gọn còn **5 PR tuần tự**:

| Thứ tự | Thời điểm | Phạm vi thi công cụ thể (Tệp & Dòng trên `main@4c4757b1`) | Cổng kiểm bắt buộc | Cách lùi nếu vỡ |
|---|---|---|---|---|
| **PR 1 (`#643` sẵn có)** | **Ngay bây giờ** | **Gộp `#643` (`targetSdk 36` + bù inset + test API 36):**<br>- `app/build.gradle.kts`: `compileSdk = 36`, `targetSdk = 36`, `loaderDex` dùng `android-36/android.jar`, `tasks.withType<Test>` dùng `javaLauncher` JVM 21.<br>- `gradle.properties`: `org.gradle.java.installations.fromEnv=JAVA_HOME_21_X64,JAVA_HOME_21_ARM64,JAVA_HOME_21`.<br>- `AndroidManifest.xml` (`github`, `play`, `j2me`): `enableOnBackInvokedCallback="false"` cho activity game nhúng + `configChanges` đủ `density\|layoutDirection\|navigation`.<br>- Phủ `dockContentClearance()` và 5 lớp test `TargetSdk36*Test`. | Đã xanh 11/11 check trên `#643`. | `git revert` merge commit `#643` (không ảnh hưởng dữ liệu người dùng). |
| **PR 2 (Khép kín SDK 36 & Chuẩn bị tiền AGP 9)** | **Ngay sau `#643`** (trước hoặc song song A85) | **Đồng bộ nốt các module với SDK 36 & dọn `kotlinOptions` cũ trên AGP `8.7.3`:**<br>1. `loader/build.gradle.kts:16`: đổi `platforms/android-35/android.jar` → `platforms/android-36/android.jar` (khớp với `app/build.gradle.kts:187`).<br>2. `reader/build.gradle.kts:27`, `j2me/build.gradle:10`, `dexlib/build.gradle:7`, `libretrodroid/build.gradle:10`, `renpy/build.gradle:15`: nâng `compileSdk` lên `36` (**giữ nguyên `:kirikiri` ở `33`**, `:rgss` ở `34`).<br>3. `app/src/github/java/vn/aow/monika/download/BrowserDownloadService.kt:55`: thêm `override fun onTimeout(startId: Int, fgsType: Int) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }` để chặn crash `RemoteServiceException` khi chạm hạn mức 6h `dataSync` trên Android 15/16.<br>4. Đổi `kotlinOptions { jvmTarget = "17" }` sang `kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }` ở cả **4 module Kotlin**: `app/build.gradle.kts:95`, `reader/build.gradle.kts:36`, `j2me/build.gradle:38-40`, `libretrodroid/build.gradle:44-46`.<br>5. Nâng `actions/setup-java` lên `java-version: 21` ở `.github/workflows/{build,coverage,codeql}.yml` và xóa dòng `fromEnv` tạm trong `gradle.properties`. | `./gradlew testDebugUnitTest assembleDebug` + CI `build`, `coverage`, `codeql`, `gate` xanh. | Revert PR 2 độc lập; PR 1 (`#643`) vẫn giữ nguyên `targetSdk 36`. |
| **PR 3 (Gradle `9.6.0` + AGP `9.4.1` — Đóng 48 cảnh báo Dependabot)** | **Ngay sau khi đóng A85** | **Nâng hệ thống build sang AGP `9.4.1` & Built-in Kotlin:**<br>1. `gradle/wrapper/gradle-wrapper.properties:3`: nâng lên `gradle-9.6.0-bin.zip`.<br>2. `gradle/libs.versions.toml`: nâng `agp = "9.4.1"`, `apksig = "9.4.1"`, `serialization = "1.8.1"`, `coroutinesTest = "1.10.2"`.<br>3. **Xóa `alias(libs.plugins.kotlin.android)`** ở `build.gradle.kts:52` và **cả 4 module Kotlin** (`app/build.gradle.kts:2`, `reader/build.gradle.kts:8`, `j2me/build.gradle:6`, `libretrodroid/build.gradle:5`) vì AGP 9 tích hợp sẵn Built-in Kotlin và báo lỗi nếu khai báo trùng.<br>4. Tắt chọn lọc Built-in Kotlin (`android.enableBuiltInKotlin=false` hoặc cấu hình per-module) nếu 4 module thuần Java (`:dexlib`, `:kirikiri`, `:rgss`, `:renpy`) bị kéo thừa `kotlin-stdlib`.<br>5. `build.gradle.kts:18-45`: lược bỏ các dòng `force` `io.netty` / `protobuf-java` / `commons-io` đã được AGP `9.4.1` nâng sẵn; chạy `scripts/kiem-phu-thuoc.py` xác nhận 47/48 cảnh báo AGP cũ về `0`. | `./gradlew testDebugUnitTest koverXmlReportGithubDebug assembleGithubRelease assemblePlayRelease :tools:pack-check:check` + `scripts/kiem-phu-thuoc.py` xanh; đóng `#217`. | Revert 1 commit PR 3 (quay về Gradle `8.14.3` + AGP `8.7.3` trong 1 bước). |
| **PR 4 (Kotlin `2.4.21` + R8 `9.x`)** | Sau PR 3 | **Nâng trình biên dịch Kotlin & R8:**<br>1. `gradle/libs.versions.toml`: nâng `kotlin = "2.4.21"` và `r8` lên bản `9.x` khớp AGP `9.4.1` (để task `loaderDex` và R8 minify đọc được metadata Kotlin `2.4`).<br>2. Kiểm tra tương thích Compose Compiler (`kotlin-compose` đi theo `kotlin = "2.4.21"`) trên `:app` và `:reader`. | `./gradlew testDebugUnitTest assembleGithubRelease assemblePlayRelease` + `compose-stability.yml` + `test-crash-symbols.py` xanh; đóng `#216`. | Hạ `kotlin` và `r8` trong `gradle/libs.versions.toml` về bản của PR 3. |
| **PR 5 (NDK `r28` & Căn lề 16 KB Page Size)** | Sau PR 4 (trước `01/02/2027`) | **Chuẩn hóa 16 KB Page Size cho Android 16 / Google Play:**<br>1. Nâng `ndkVersion` lên `"28.2.13676358"` ở `app/build.gradle.kts:26`, `j2me/build.gradle:11`, `libretrodroid/build.gradle:11` và `scripts/ci-install-j2me-ndk.sh:7`.<br>2. Thêm `scripts/check-16kb-alignment.py` kiểm tra mọi `lib/arm64-v8a/*.so` trong APK có `LOAD p_align >= 0x4000` (16384).<br>3. Xử lý prebuilt 4 KB `com.github.nikita36078:mobile-ffmpeg:v4.3.2-compact` trong `j2me/build.gradle:93` (tái liên kết với `-Wl,-z,max-page-size=16384` hoặc tách khỏi bản Play). | `python3 scripts/check-16kb-alignment.py` báo `0` tệp `.so` 4 KB trên cả 2 APK release; test J2ME & LibretroDroid xanh. | Hạ `ndkVersion` về bản cũ nếu phát sinh lỗi ABI trên máy 32-bit `armeabi-v7a`. |

---

## File cần sửa / tạo (Tổng hợp theo 9 module hiện có)

| Module / Tệp | PR chạm | Lưu ý kỹ thuật bắt buộc |
|---|---|---|
| `app/build.gradle.kts` | PR 1 (`#643`), PR 2, PR 3, PR 5 | Đã lên `compileSdk = 36`, `targetSdk = 36` ở `#643`; PR 2 đổi `kotlinOptions`; PR 3 bỏ `alias(libs.plugins.kotlin.android)`. |
| `reader/build.gradle.kts` *(Module mới)* | PR 2, PR 3 | Nâng `compileSdk = 36`, đổi `kotlinOptions` (dòng 36) ở PR 2; bỏ `alias(libs.plugins.kotlin.android)` (dòng 8) ở PR 3. |
| `loader/build.gradle.kts` | PR 2 | Dòng `16`: đổi `platforms/android-35/android.jar` → `platforms/android-36/android.jar`. |
| `j2me/build.gradle`, `libretrodroid/build.gradle` | PR 2, PR 3, PR 5 | Đổi `kotlinOptions` ở PR 2; bỏ plugin `kotlin-android` ở PR 3; nâng `ndkVersion` ở PR 5. |
| `dexlib/build.gradle`, `renpy/build.gradle` | PR 2, PR 3 | Nâng `compileSdk = 36` ở PR 2; giữ thuần Java ở PR 3. |
| `kirikiri/build.gradle`, `rgss/build.gradle` | Không nâng `compileSdk` | **Giữ nguyên `:kirikiri` ở `compileSdk 33`** (tránh đụng `Context.getDeviceId()` của API 34+) và `:rgss` ở `34`. |
| `app/src/github/java/vn/aow/monika/download/BrowserDownloadService.kt` | PR 2 | Thêm `override fun onTimeout(startId: Int, fgsType: Int)` gọi `stopForeground` + `stopSelf`. |
| `gradle/libs.versions.toml`, `build.gradle.kts`, `gradle-wrapper.properties` | PR 3, PR 4 | Nâng Gradle `9.6.0` + AGP `9.4.1` ở PR 3; nâng Kotlin `2.4.21` + R8 `9.x` ở PR 4. |

---

## Cổng kiểm

### Cổng tự động
- Ở **mọi PR (PR 1 → PR 5)**:
  ```bash
  ./gradlew testDebugUnitTest
  ./gradlew assembleDebug
  ```
- Ở **PR 2, PR 3, PR 4, PR 5** (cổng kiểm release & bản Play):
  ```bash
  ./gradlew assembleGithubRelease assemblePlayRelease
  python3 scripts/check-play-apk.py
  ```
- Ở **PR 3** (kiểm tra đóng 48 cảnh báo Dependabot ở classpath AGP):
  ```bash
  ./gradlew koverXmlReportGithubDebug :tools:pack-check:check
  python3 scripts/kiem-phu-thuoc.py sbom.json
  ```
- Ở **PR 5** (kiểm tra căn lề 16 KB):
  ```bash
  python3 scripts/check-16kb-alignment.py app/build/outputs/apk/play/release/app-play-universal-release.apk
  ```

### Cổng nhìn
- Mở ứng dụng trên máy ảo / thiết bị Android 15–16 (navigation bar 3 nút và cử chỉ): thanh Dock dưới đáy và nút hành động cuối danh sách ở `HomeScreen`, `LibraryScreen`, `GamesScreen`, `DownloadsScreen`, `SearchScreen`, `GameDetailScreen`, `LibraryReadPage` không bị che khuất.
- Bấm nút Back vật lý / vuốt cạnh trong game J2ME (`MicroActivity`), Kirikiri (`KirikiriGameActivity`), RGSS (`RgssGameActivity`): hiện đúng menu trong game, không bị thoát ngang bởi Predictive Back của Android 16.

---

## Rủi ro & cách lùi

- **Rủi ro 1 — Lệch `android.jar` giữa `:app` và `:loader` sau khi gộp `#643`:** `#643` đã đổi `app/build.gradle.kts:187` sang `android-36/android.jar` nhưng quên `loader/build.gradle.kts:16` (`android-35/android.jar`). Giải quyết ngay ở **PR 2** bằng cách đổi `loader/build.gradle.kts:16` sang `android-36/android.jar`.
- **Rủi ro 2 — Xung đột Built-in Kotlin trên AGP 9 với module mới `:reader`:** `:reader` mới thêm ngày 11/11 có `alias(libs.plugins.kotlin.android)` ở dòng 8. Đã đưa `:reader` vào danh sách bắt buộc của **PR 2 & PR 3**.
- **Rủi ro 3 — Vỡ R8 minify hoặc Kover khi lên AGP `9.4.1` / Kotlin `2.4.21`:** Vì đã tách **PR 3 (AGP 9.4.1)** và **PR 4 (Kotlin 2.4.21)** thành 2 PR riêng và chỉ chạy **sau khi đóng xong A85**, nếu bất kỳ PR nào gặp lỗi tương thích plugin, chỉ cần revert đúng PR đó trên `main` mà không ảnh hưởng tới `targetSdk 36` (đã lên từ PR 1 & PR 2) hay tiến độ bảo mật A85.
