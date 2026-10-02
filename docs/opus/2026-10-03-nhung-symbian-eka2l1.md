# Phương án câu 8 — Nhúng Symbian / N-Gage (EKA2L1): đóng gói và nhập firmware

> Người viết: Opus (cố vấn ngoài). Ngày 03/10/2026 (GMT+7). Đọc Monika trên nhánh `ccr-95498459-h58r3b` @ `289bfaa` (trùng `main`), app 0.7.3, `configVersion` 31.
> Mã EKA2L1 đọc ở `EKA2L1/EKA2L1` nhánh `master` @ `c396ac8` (03/10/2026). Đường dẫn và số dòng trong tài liệu này tính theo commit đó.
> Người thi công: Claude Code (Sonnet). Người quyết: sếp.
> **Chưa có dòng nào được chạy trên máy thật.** Số đo dung lượng lấy từ APK chính thức `EKA2L1-Android.apk` (release `continous`, sửa lần cuối 02/10/2026, 28.211.802 byte), **không** phải bản Monika tự dựng. Chỗ **[CHƯA KIỂM]** phải kiểm trước khi dựa vào.

Quyết định của sếp liên quan (đã chốt trong `docs/opus/2026-10-03-nhung-renpy-rgss.md`):

| # | Quyết định |
|---|---|
| Q4 | Không mở app ngoài, áp cho mọi hệ, tắt **theo từng hệ** khi engine nhúng của hệ đó đã phát hành |
| Q5 | Game thử: Sonnet **hỏi sếp khi tới bước cần**, không tự tải |
| Q6 | Symbian vẫn phải nhúng, **làm sau RPG Maker và Ren'Py** |

Điều sếp nên biết trước khi đọc: quét toàn bộ 299 bài trên aow.vn qua feed (03/10/2026) **không có bài nào về Symbian/N-Gage** (mọi chữ "Nokia" đều thuộc game Java). Vì vậy game thử và firmware đều phải do sếp cấp, và không có tín hiệu nào để Monika tải trước gói.

---

## 1. Kết luận

**Làm theo mô hình Azahar, không theo mô hình Kirikiri:** lớp JNI của EKA2L1 được **viết lại bằng Kotlin**, giữ nguyên tên `com.github.eka2l1.emu.*`, đặt trong `:app` (như `org.citra.citra_emu.*` của Azahar). Phần native là **gói tải thêm `symbian`**, gồm 1 file `libnative-lib.so` và 4 thư mục tài nguyên, có cho cả `arm64-v8a` lẫn `armeabi-v7a`. Gói nặng khoảng 10–12 MB, dưới ngưỡng 15 MB nên tự tải cả khi dùng 4G. Gói được dựng từ nguồn EKA2L1 ghim commit, bằng chính Gradle của họ trên CI (giống job `azahar-android`). **Không vá mã native.**

**Firmware:** người chơi tự nhập một lần trong Cài đặt → "Máy Symbian", theo 3 đường: gói cấu hình sẵn `.zip`, ROM máy `.rom` (kèm `.rpkg` nếu cần), hoặc firmware `.vpl`. Monika lưu ở `filesDir/symbian/`, tách khỏi thư mục gói, nên nâng cấp gói không làm mất máy đã nhập. Mỗi máy có ổ C/D/E riêng.

**Game:** lần đầu bấm chơi, Monika cài `.sis`/`.sisx` (hoặc thẻ game N-Gage) vào máy Symbian hợp đời, rồi ghi lại UID của game. Từ lần sau, bấm là mở thẳng game.

---

## 2. Lý do và đánh đổi

### 2.1 Vì sao là mô hình Azahar

| Tiêu chí | Mô hình Azahar (chọn) | Mô hình Kirikiri (module chứa Java gốc) | Nhúng nguyên app EKA2L1 |
|---|---|---|---|
| Java phải mang vào APK | Kotlin tự viết, khoảng 300 dòng | `Emulator.java` gốc kéo theo appcompat, RxJava, CameraX, ML Kit barcode, Gson, SnakeYAML, filepicker (`src/emu/android/app/build.gradle`, khối `dependencies`) | Toàn bộ |
| Hợp luật "app nhẹ" | Có | Kém: thêm nhiều thư viện | Không |
| Giao diện Monika | Có | Phải vá nhiều | Không |
| Tiền lệ trong repo | `app/src/main/java/org/citra/citra_emu/` + `-keep class org.citra.citra_emu.** { *; }` (`app/proguard-rules.pro` dòng 26) | `:kirikiri` | — |

Native chỉ gọi ngược Java qua **tên lớp + tên hàm + chữ ký** (mục 3.3). Giữ đúng 3 thứ đó là đủ; phần thân hàm do Monika tự viết.

### 2.2 Vì sao dựng từ nguồn thay vì lấy `.so` từ APK chính thức

| | Dựng từ nguồn, ghim commit (chọn) | Lấy `.so` từ APK chính thức |
|---|---|---|
| Ghim được phiên bản | Có (commit) | Không: release `continous` là tag cuộn, bị dời mỗi lần `master` đổi (`.github/workflows/build.yml`, bước "Move the rolling tag") |
| Nghĩa vụ GPLv3 (nêu đúng nguồn của nhị phân) | Rõ | Phải tự dò commit trong APK |
| Công sức | Như job `azahar-android`: NDK 25.1.8937393, CMake 3.22.1, JDK 17, submodule đệ quy (40 cái trong `.gitmodules`), FFmpeg dựng lúc build, libuv tải bằng FetchContent | Không dựng |
| Dùng ở đâu | Gói phát hành | Spike S0 và dự phòng (mục 6) |

### 2.3 Dung lượng (đã đo trên APK chính thức)

| Thành phần | arm64-v8a | armeabi-v7a |
|---|---|---|
| `libnative-lib.so` (chưa nén) | 29.463.144 byte | 19.242.100 byte |
| Tài nguyên `resources/` + `patch/` + `compat/` + `scripts/` (chưa nén, dùng chung) | 3.009.503 byte | như bên |
| **Gói thử** = .so + tài nguyên, `zip -9` | **11.853.725 byte** | **10.371.199 byte** |
| APK Monika tăng thêm | Chỉ Kotlin **[CHƯA KIỂM]**, đo sau S1 | như bên |

Những thứ trong APK EKA2L1 mà Monika **không** cần:

- `libuv.so`: `libnative-lib.so` đã liên kết tĩnh libuv. `readelf` không thấy `NEEDED libuv.so` và thấy 285 ký hiệu `uv_*` nằm sẵn trong thư viện.
- `libbarhopper_v3.so`, `libimage_processing_util_jni.so`, `assets/mlkit_barcode_models/`: thuộc ML Kit (quét mã vạch).
- `assets/Roboto-Regular.ttf`, `assets/dexopt/`.

`NEEDED` của `libnative-lib.so` chỉ có thư viện hệ thống (`libandroid`, `libEGL`, `libjnigraphics`, `libdl`, `libOpenSLES`, `libz`, `libm`, `liblog`, `libc`) và C++ STL đã liên kết tĩnh. Vì vậy chỉ cần nạp **một file**, không cần thứ tự nạp như `AzaharModule.load()`.

### 2.4 Máy 32-bit chạy được (khác Kirikiri)

| ABI | Bộ dịch CPU | Nguồn |
|---|---|---|
| arm64-v8a | `dynarmic` (khóa `cpu` trong `config.yml`, mặc định `"dynarmic"`) | `src/emu/config/include/config/options.inl` dòng 40; `src/emu/system/src/epoc.cpp` dòng 684 |
| armeabi-v7a | `r12l1` (bộ dịch ARM-sang-ARM của EKA2L1), **ép cứng**, không theo config | `epoc.cpp` dòng 666; macro `EKA2L1_ARCH_ARM` chỉ bật cho `__arm__` 32-bit (`src/emu/common/include/common/platform.h`) |

### 2.5 Rủi ro

| # | Rủi ro | Mức | Xử lý |
|---|---|---|---|
| X1 | **Thiếu hoặc sai chữ ký một hàm callback** → `startNative` gặp `NoSuchMethodError` hoặc sập. Native lấy sẵn các hàm lưu trữ và camera ngay trong `startNative` (`register_storage_callbacks`, `register_camera_callbacks`) | Chặn | Test hợp đồng JNI ở S1 (đối chiếu danh sách mục 3.3 bằng reflection) |
| X2 | R8 đổi tên hoặc xóa hàm callback | Cao | `-keep class com.github.eka2l1.emu.** { *; }`, kiểm bằng `dexdump` (luật trong `CLAUDE.md`) |
| X3 | Native đọc đường dẫn và danh sách app **một lần khi khởi động**. Bản gốc khởi động lại tiến trình sau khi cài game, cài máy hoặc gói cấu hình (`AppUtils.restart`, chú thích "read once during startup") | Cao | Luồng S5 tách cài và chơi thành 2 lần chạy tiến trình `:game`. Thử bỏ bước khởi động lại ở S5 (U3) |
| X4 | Gói tải lại thì `SimpleModule` **xóa sạch** `filesDir/packs/<id>/` (`out.deleteRecursively()`) | Cao nếu đặt sai chỗ | Dữ liệu người chơi (firmware, game đã cài, save) để ở `filesDir/symbian/`, không bao giờ để trong thư mục gói |
| X5 | JIT `dynarmic` chạy dưới lớp dịch ARM của máy ảo x86_64 trong Emulator Test | Trung bình | S6 chỉ kiểm tới "nạp thư viện + `startNative`". Muốn chạy xa hơn thì ghi `cpu: dyncom` (trình thông dịch) vào `config.yml` của máy ảo **[CHƯA KIỂM]** |
| X6 | Firmware có bản quyền của Nokia | Pháp lý | Monika không đóng gói, không dẫn link, không đưa lên CI. Chỉ hướng dẫn người chơi tự lấy từ máy của mình. Đây không phải tư vấn pháp lý |
| X7 | GPLv3 (`LICENSE` của EKA2L1) | Pháp lý | Như mục ⛔ sếp đã ghi nhận: công khai mã Monika trước khi phát hành bản có engine GPL. Mô tả Release gói ghi repo và commit nguồn |
| X8 | Firmware dạng VPL nặng (hàng trăm MB) khi chép vào máy | Trung bình | Kiểm chỗ trống bằng `StatFs` trước khi chép; chép xong thì xóa bản tạm |
| X9 | Thư viện tên chung `libnative-lib.so` | Thấp | Nạp bằng đường dẫn tuyệt đối (`System.load`). Không engine nào khác của Monika dùng tên này |
| X10 | Upstream đổi rất nhanh (release cuộn) | Thấp | Ghim commit; nâng cấp bằng tham số `eka2l1_ref` của workflow |

### 2.6 Công sức (tương đối)

| Khối | Công | Rủi ro |
|---|---|---|
| S0 Spike CI | Nhỏ | Trung bình (thời gian build) |
| S1 Lớp JNI + test hợp đồng | Nhỏ | Thấp |
| S2 Gói + định tuyến | Nhỏ | Thấp |
| S3 Runtime trong `:game` | Vừa | Trung bình |
| S4 Nhập firmware | Vừa | Trung bình |
| S5 Cài game + màn chơi + bàn phím | Vừa–lớn | Trung bình |
| S6 Emulator Test | Nhỏ | Thấp |
| S7 Phát hành + tắt app ngoài | Nhỏ | Thấp |

Tổng thể **nhẹ hơn RGSS**: không phải dựng lại hệ build, không đổi gói lớp SDL, không vá native.

---

## 3. Dữ kiện đã đọc (để Sonnet không phải dò lại)

### 3.1 Trong repo Monika

| Dữ kiện | Nguồn |
|---|---|
| Hạ tầng E0 đã xong: `EngineRoutes.all` (khóa = `system.engine`), `EnginePrepActivity`, `allowExternalApp` | `runner/EngineRoutes.kt`, `runner/EnginePrepActivity.kt`, `config/MonikaConfig.kt` (commit `289bfaa`) |
| Hệ `symbian`: `runner: external`, `externalApp: eka2l1`, đuôi `sis`, `sisx`, `ngage`, chưa có `engine` | `config/monika-config.json` |
| `EnginePrepActivity` **bỏ entry là thư mục**: `route?.open?.invoke(this, entry?.takeIf { it.isFile }, …)`. Thẻ game N-Gage là thư mục nên sẽ bị mất | `runner/EnginePrepActivity.kt` (dòng `route?.open?.invoke`) |
| Nhận diện game dạng thư mục: `marker` chỉ so **đuôi đường dẫn file** (`p.endsWith("/$marker", ignoreCase = true)`), không có ký tự đại diện | `library/GameLibrary.kt`, `GameDetector.detectFiles` |
| Mẫu engine native có JNI riêng: `AzaharModule` (tải, kiểm SHA-256, nạp `.so`), `AzaharBridge` (native gọi ra, giao diện Monika vẽ), `AzaharInstallActivity` (cài vào "bộ nhớ máy", chạy ở `:game`) | `azahar/` |
| Mẫu job CI: clone đệ quy → Gradle của upstream → trích `lib/<abi>/*.so` → `jni-symbols.txt` (`nm -D`), `needed.txt` (`readelf`), `SOURCE.txt`, `manifest.json` → zip → Release | `.github/workflows/build-engines.yml`, job `azahar-android` |
| `Diagnostics` dựa vào tên tiến trình `:game` để gộp báo cáo game chết | `diag/Diagnostics.kt` dòng 428 |
| Emulator Test: x86_64, `google_apis`, API 30/34; mẫu đẩy gói thật vào `files/packs/<id>` như Kirikiri | `.github/workflows/emulator-test.yml`, `scripts/ci-emulator-games.sh` dòng 55–77 |
| Bàn phím điện thoại "kiểu Monika" đã có cho J2ME (trái số 3×4, phải L ↑ R / ← OK → / ↓) | `j2me/.../lcdui/keyboard/VirtualKeyboard.java` dòng 82 |
| `PadLayout` hiện chưa có bố cục điện thoại | `runner/GamePadOverlay.kt` |
| `prefetchByExtension` có dạng `{"xp3": ["kirikiri"], …}` | `config/monika-config.json` |
| `GameMeta` (`.monika.json` trong thư mục game) có thể không ghi được với thư mục ngoài từ bản cài trước | `library/GameMeta.kt` (chú thích hàm `read`) |

### 3.2 Hàm native Monika sẽ gọi (lớp `com.github.eka2l1.emu.Emulator`, tất cả `static`)

| Hàm | Chữ ký Java | Ghi chú | Nguồn (`src/emu/android/app/src/main/cpp/src/`) |
|---|---|---|---|
| `setDirectory` | `(String)V` | Đặt thư mục làm việc = `file_directory(path)`. **Phải truyền đường dẫn có `/` ở cuối**, nếu không sẽ lấy thư mục cha (bản gốc truyền `emulatorDir` luôn có `/` cuối) | `native-lib.cpp` 61–72 |
| `startNative` | `()Z` | `false` khi chưa có máy nào; dù vậy `launcher` đã được tạo nên `installDevice` vẫn gọi được | `native-lib.cpp` 74–84; `state.cpp` 160–167 |
| `getApps` | `()[String` | Mảng phẳng `[uid, tên, uid, tên…]`, đã ẩn app hệ thống khi `hide-system-apps: true` | `launcher.cpp` 84–97 |
| `launchApp` | `(I)V` | Gọi sau `surfaceChanged` lần đầu. Khi app Symbian thoát, native gọi `exitInstance()` | `launcher.cpp` 288–304; `EmulatorActivity.java` 571–573 |
| `surfaceChanged` / `surfaceDestroyed` / `surfaceRedrawNeeded` | `(Surface,II)V` / `()V` / `()V` | | `native-lib.cpp` 120–145 |
| `pressKey` | `(II)V` | (mã phím Symbian, 0 = nhấn / 1 = nhả) | `EmulatorActivity.java` 604, 617 |
| `touchScreen` | `(IIIII)V` | (x, y, z, hành động 0 = chạm / 1 = kéo / 2 = nhả, id ngón) theo tọa độ trong SurfaceView | `EmulatorActivity.java` ~625–680 |
| `installApp` | `(String)I` | Cài vào ổ E: (máy S80 thì D:), trả mã kết quả | `launcher.cpp` 306–315 |
| `installNGageGame` | `(String)I` | Thư mục thẻ game N-Gage (phải có `system/apps/<game>/`) hoặc file nén; chép vào `E:\system` | `launcher.cpp` 810–813; `src/emu/system/src/epoc.cpp` `install_ngage_game_card` |
| `getDevices` / `getDeviceFirmwareCodes` | `()[String` | Tên máy / mã firmware, cùng thứ tự với id | `launcher.cpp` 317–337 |
| `setCurrentDevice` | `(IZ)V` | `true` = đổi tạm trong tiến trình; `false` = ghi vào `config.yml` | `launcher.cpp` 357–382 |
| `installDevice` | `(String,String,ZZ)I` | (rpkg, rom hoặc vpl, `installRPKG`, `isolateDrives`) | `launcher.cpp` 422–461 |
| `doesRomNeedRPKG` | `(String)Z` | | `launcher.cpp` 418–420 |
| `getPackages` / `uninstallPackage` | `()[String` / `(II)V` | Mảng phẳng `[uid, index, tên…]` | `launcher.cpp` 463–486 |
| `getAppIcon` | `(J)[Bitmap` | (ảnh, mặt nạ), dùng làm ảnh bìa | `launcher.cpp` 118 |
| `setScreenParams` | `(IIIILjava/lang/String;FZ)V` | Màu nền, tỉ lệ, kiểu co giãn, căn lề, ảnh nền, độ mờ, giữ tỉ lệ | `native-lib.cpp` 339 |
| `submitInput` / `submitQuestionDialogResponse` | `(String)V` / `(I)V` | Trả kết quả hộp thoại | `native-lib.cpp` 371, `Emulator.java` 804 |
| `saveScreenshotTo` | `(String)Z` | | `Emulator.java` 810 |
| `setCurrentMMCID` | `(String)V` | ID thẻ nhớ cho game N-Gage có khóa chép | `launcher.cpp` 827–829 |

Thư viện xuất **45** hàm JNI: `Emulator` 40, `EmulatorCamera` 4, `EmulatorLocation` 1 (đếm bằng `readelf --dyn-syms` trên APK chính thức). Kotlin chỉ cần khai báo `external` những hàm Monika gọi, vì JNI tìm hàm theo tên khi gọi lần đầu.

### 3.3 Hàm Java mà native gọi ngược — **thiếu một hàm là hỏng**

Mọi hàm đều `static`, đúng tên và đúng chữ ký:

| Lớp | Hàm | Chữ ký | Lúc native lấy hàm | Monika làm gì |
|---|---|---|---|---|
| `Emulator` | `openContentUri` | `(Ljava/lang/String;Ljava/lang/String;)I` | Ngay trong `startNative` | Viết đủ (Monika dùng đường dẫn thật nên hầu như không bị gọi) |
| `Emulator` | `listContentUriDir` | `(Ljava/lang/String;)[Ljava/lang/String;` | như trên | như trên |
| `Emulator` | `contentUriCreateDirectory`, `contentUriCreateFile`, `contentUriCopyFile`, `contentUriRenameFileTo` | `(Ljava/lang/String;Ljava/lang/String;)I` | như trên | như trên |
| `Emulator` | `contentUriRemoveFile` | `(Ljava/lang/String;)I` | như trên | như trên |
| `Emulator` | `contentUriMoveFile` | `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I` | như trên | như trên |
| `Emulator` | `contentUriGetFileInfo` | `(Ljava/lang/String;)Ljava/lang/String;` | như trên | như trên |
| `Emulator` | `contentUriFileExists` | `(Ljava/lang/String;)Z` | như trên | như trên |
| `Emulator` | `isExternalStoragePreservedLegacy` | `()Z` | như trên | Trả `false` |
| `Emulator` | `getAppClassLoader` | `()Ljava/lang/ClassLoader;` | Ngay trong `startNative` | Trả class loader của app |
| `Emulator` | `showInputDialog` | `(Ljava/lang/String;I)V` | Khi game cần nhập chữ | Hộp nhập chữ của Monika → `submitInput` |
| `Emulator` | `closeInputDialog` | `()V` | như trên | Đóng hộp nhập |
| `Emulator` | `showQuestionDialog` | `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V` | Khi game hỏi Có/Không | Sheet Monika → `submitQuestionDialogResponse` |
| `Emulator` | `exitInstance` | `()V` | Khi app Symbian thoát | `Diagnostics.end`, đóng màn, kết thúc tiến trình |
| `Emulator` | `vibrate` / `stopVibrate` | `(I)Z` / `()V` | Khi game rung | Rung theo Cài đặt Monika |
| `Emulator` | `openWebPage` | `(Ljava/lang/String;)Z` | Khi game mở link | Bản đầu trả `false` |
| `Emulator` | `prepareAudioRecord` | `()V` | Khi game ghi âm | Bản đầu trả về ngay, không xin quyền micro |
| `Emulator` | `setMulticastLock` | `(Z)V` | Chơi mạng qua Bluetooth/mDNS | Bản đầu không làm gì |
| `EmulatorCamera` | `getCameraCount` `()I`, `initializeCamera` `(I)I`, `releaseCamera` `(I)V`, `destroyCamera` `(I)V`, `getSupportedImageOutputFormats` `()[I`, `getFlashMode` `(I)I`, `setFlashMode` `(II)Z`, `isCameraFacingFront` `(I)Z`, `getOutputImageSizes` `(I)[Landroid/util/Size;`, `captureImage` `(III)Z`, `receiveViewfinderFeed` `(IIII)Z`, `stopViewfinderFeed` `(I)V` | | **Ngay trong `startNative`**, không kiểm null (`emulator_camera_jni.cpp` 39–58) | Lớp giả: không có camera (`getCameraCount = 0`, còn lại trả lỗi hoặc `false`) |
| `EmulatorLocation` | `start`, `stop` | `()V` | Khi game xin vị trí | Không làm gì |
| `TlsTrust` | `verify` | `([[BLjava/lang/String;)Z` | Khi game mở kết nối TLS | Kiểm chuỗi chứng chỉ bằng `X509TrustManager` của hệ thống, lỗi thì `false` |

Nguồn: `src/emu/common/src/android/storage.cpp` 60–103; `src/emu/common/src/android/jniutils.cpp`; `src/emu/android/app/src/main/cpp/src/launcher.cpp`; `src/emu/common/src/android/applauncher.cpp`, `audio.cpp`; `src/emu/drivers/src/hwrm/backend/vibration_jdk.cpp`; `src/emu/services/src/bluetooth/protocols/mdns_socket.cpp` 245; `src/emu/drivers/src/location/backend/android/location_android.cpp` 49, 58; `src/emu/drivers/src/network/tls_trust_android.cpp`.

Ngoài ra native tự tìm lớp qua class loader của app (`jni::init_classloader`), nên gọi từ luồng native vẫn thấy lớp của Monika.

### 3.4 Thư mục làm việc, tài nguyên, cấu hình

| Dữ kiện | Nguồn |
|---|---|
| `config.yml` đọc và ghi ở thư mục làm việc | `src/emu/config/src/config.cpp` 344, 362 |
| Dữ liệu máy ở `data/` tương đối với thư mục làm việc (khóa `data-storage`) | `options.inl` 45; `config.h` 135 |
| Danh sách máy: `data/devices.yml`, mỗi máy là một map theo mã firmware, có `platver` (vd. `epoc6`, `epoc93fp1`, `epoc94`), `manufacturer`, `firmcode`, `model`, `machine-uid`, `isolated-drives` | `src/emu/system/src/devices.cpp` 236–261; `src/emu/common/src/types.cpp` (`epocver_to_string`) |
| Khóa config có sẵn, dùng được: `cpu`, `device`, `language`, `data-storage`, `hide-system-apps` (mặc định `true`), `enable-upnp` (mặc định `true`), `enable-hw-gles1` | `options.inl` 40–85 |
| Bản gốc chép từ assets vào thư mục làm việc: `resources/`, `patch/` (xóa rồi chép lại khi đổi phiên bản), `compat/`, `scripts/` (chép đè). Phiên bản so bằng file `version` | `Emulator.java` 160–197 |
| CMake chép 4 thư mục này vào assets lúc build (shader GLES, DLL vá cho Symbian, `compat/*.yml`, script Lua, `defaultbank.hsb`/`.sf2`) | `src/emu/android/app/src/main/cpp/CMakeLists.txt` |
| `Emulator` gốc nạp thư viện bằng `static { System.loadLibrary("native-lib"); }`. Monika **không** được làm vậy, phải `System.load(<đường dẫn gói>)` trước lần gọi native đầu tiên | `Emulator.java` 131–133 |
| `-DCI=ON` bật `BUILD_FOR_USER`; workflow gốc dựng bằng `./gradlew assembleRelease -PciArg='-DCI=ON'` | `CMakeLists.txt` 148–150; `.github/workflows/build.yml` job `build-android` |
| Bản release dựng `armeabi-v7a` + `arm64-v8a`; bản debug chỉ `x86_64` | `src/emu/android/app/build.gradle` |

### 3.5 Firmware: 3 đường nhập của bản gốc

| Đường | Người chơi đưa gì | Hàm | Nguồn |
|---|---|---|---|
| Gói cấu hình sẵn | `.zip` chứa thư mục `data/` hoàn chỉnh | Không cần native: giải nén, **thay** `data/` cũ (có hỏi), rồi khởi động lại | `AppsListFragment.java` 406–489 |
| ROM máy (bản dump từ máy của mình) | `.rom`, và `.rpkg` nếu `doesRomNeedRPKG` trả `true` | `installDevice(rpkg, rom, true, isolate)` | `DeviceListFragment.java` 220–324; `launcher.cpp` 422–461 |
| Firmware Nokia | Thư mục có file `.vpl` (kèm các file firmware nó trỏ tới) | `installDevice("", vpl, false, isolate)` → `install_firmware` | như trên |

Mã lỗi `installDevice` (`Emulator.java`): 0 xong · 1 không thấy file · 2 không đủ dung lượng · 3 RPKG hỏng · 4 không xác định được đời máy · 5 máy đã có · 6 lỗi chung · 7 không chép được ROM · 8 VPL không hợp lệ · 9 ROFS hỏng · 10 ROM hỏng · 11 FPSX hỏng.

Mã lỗi `installNGageGame`: 0 xong · 1 không thấy thư mục dữ liệu game · 2 có hơn một thư mục dữ liệu · 3 không thấy thông tin đăng ký · 4 thông tin đăng ký hỏng · 5 lỗi chung.

### 3.6 Nhận diện gói SIS và phím

| Dữ kiện | Nguồn |
|---|---|
| 4 byte đầu (little-endian) = `0x10201A7A` → SIS mới (Symbian 9: S60v3/v5). 4 byte thứ hai = `0x1000006D` hoặc `0x10003A12` → SIS cũ (S60v1/v2, N-Gage). 4 byte đầu = mã trường `SISController` → SIS "stub" | `src/emu/loader/src/sis.cpp` 42–72; `src/emu/loader/include/loader/sis_common.h` |
| Mã phím Symbian: `0`–`9` = mã ASCII; `*` = `'*'`; `#` = `0x7F`; lên `0x10`, xuống `0x11`, trái `0x0E`, phải `0x0F`, OK `0xA7`, mềm trái `0xA4`, mềm phải `0xA5`, C (xóa) `0x01`, ✎ (sửa) `0x12`, Gọi `10`, Kết thúc `0xB5` | `src/emu/android/app/src/main/java/com/github/eka2l1/emu/Keycode.java` |
| Phím vật lý mặc định: số, `*`, `#`, D-pad, Enter → OK, `SOFT_LEFT/RIGHT`, `CALL` → Gọi, `ENDCALL` → C, `SHIFT_LEFT` → ✎ | `settings/KeyMapper.java` 30–51 |
| Không có save state. Game Symbian tự lưu vào ổ C:/E: của máy giả | (không có JNI nào cho save state) |

---

## 4. Các bước thi công — Khối S

Quy ước như phương án câu 5: mỗi bước một PR, `./gradlew testDebugUnitTest` xanh trước khi mở PR, sửa config phải tăng `configVersion`. Sau mỗi khối, cập nhật mục 4 của `docs/GIAO-TIEP-VOI-OPUS.md`.

**Thứ tự (theo Q6):** S1–S7 làm **sau** khối P (Ren'Py 8). Riêng S0 chỉ chạy CI, không đụng app, nên có thể chạy sớm nếu sếp đồng ý (mục 8, [A1]).

| Bước | Việc | File / package | Cách kiểm |
|---|---|---|---|
| **S0** | **Spike CI, chưa đụng app.** Thêm job `symbian-android` vào `build-engines.yml` (thêm `symbian` vào input `only`; input `eka2l1_ref`, mặc định `c396ac8`). Chép cách làm của job `azahar-android`: clone `--recurse-submodules` (cần lịch sử để Gradle gọi `git rev-parse`) → JDK 17 → `sdkmanager "ndk;25.1.8937393" "cmake;3.22.1"` → trong `src/emu/android` chạy `./gradlew assembleRelease -PciArg='-DCI=ON'` → với mỗi ABI lấy `lib/<abi>/libnative-lib.so` cùng `assets/{resources,patch,compat,scripts}` → sinh `jni-symbols.txt` (`llvm-nm -D`, lọc `Java_`), `needed.txt`, `SOURCE.txt` (GPLv3, repo, commit), `manifest.json` (`engine`, `repo`, `commit`, `abi`, `built`) → `symbian-{abi}.zip` + `.sha256`. S0 **chỉ đưa lên artifact**, chưa tạo Release. Ghi thời gian build và kích thước vào `docs/opus/ket-qua/S0.md` | `.github/workflows/build-engines.yml`, `docs/opus/ket-qua/S0.md` | Artifact có 2 zip; `needed.txt` chỉ có thư viện hệ thống; `jni-symbols.txt` đủ 45 dòng; kích thước gần mục 2.3. **Dừng** nếu build hỏng 3 lần vì 3 nguyên nhân khác nhau → báo sếp, dùng dự phòng mục 6 |
| **S1** | Lớp JNI bằng Kotlin, gói `com.github.eka2l1.emu`: `Emulator` (`object`, hàm `@JvmStatic external` cho các hàm ở mục 3.2, hàm `@JvmStatic` cho mọi callback ở mục 3.3, chuyển sang `SymbianBridge`); `EmulatorCamera` (giả, đủ 12 hàm); `EmulatorLocation` (`start`/`stop` rỗng); `TlsTrust.verify`. **Không có khối `static` nạp thư viện.** Đặt `engines/symbian/jni-symbols.txt` (lấy từ S0), `engines/symbian/jni-callbacks.txt` (lớp, tên, chữ ký theo mục 3.3), `engines/symbian/UPSTREAM.md` (repo, commit, giấy phép). Thêm `-keep class com.github.eka2l1.emu.** { *; }` | `app/src/main/java/com/github/eka2l1/emu/`, `symbian/SymbianBridge.kt`, `engines/symbian/`, `app/proguard-rules.pro` | Test `SymbianJniContractTest`: (a) mỗi `external` trong Kotlin có ký hiệu trong `jni-symbols.txt`; (b) mỗi dòng trong `jni-callbacks.txt` có hàm static đúng chữ ký JVM (dựng chuỗi chữ ký bằng reflection). `assembleRelease` + `dexdump` thấy đủ hàm sau R8 |
| **S2** | Gói và định tuyến: `PackManager.installers` thêm `SYMBIAN = "symbian"`, file chính `libnative-lib.so`. `EngineRoutes.all` thêm `"symbian"`. Thêm `acceptsDir: Boolean = false` vào `EngineRoute`, để `EnginePrepActivity` truyền cả entry là thư mục khi `acceptsDir = true` (Kirikiri giữ nguyên hành vi). **Chưa** thêm `engine` vào hệ `symbian` trong config | `pack/PackManager.kt`, `runner/EngineRoutes.kt`, `runner/EnginePrepActivity.kt`, `AppGraph.kt` (nếu cần) | Unit test: config chưa có `modules.symbian` → `EngineRoutes.usable("symbian") == null` → vẫn mở app ngoài như cũ; route Kirikiri không đổi |
| **S3** | Runtime trong `:game`: `SymbianRuntime` với 2 thư mục: gói `filesDir/packs/symbian/` và dữ liệu `filesDir/symbian/`. Khi `filesDir/symbian/.pack-version` khác `modules.symbian.version`: chép lại `resources/`, `patch/`, `compat/`, `scripts/` từ gói (đúng cách bản gốc ở mục 3.4). `config.yml` chưa có thì ghi mặc định: `hide-system-apps: true`, `enable-upnp: false` (chỉ dùng khóa ở mục 3.4). Rồi `System.load(<gói>/libnative-lib.so)` → `Emulator.setDirectory("<filesDir>/symbian/")` (**có `/` cuối**) → `startNative()`. `Diagnostics.begin(kind "symbian", core "eka2l1", info = manifest.json)`, `stage("lib-loaded")`, `stage("native-started:<true/false>")`; in log `MonikaGame: symbian-lib-loaded`, `MonikaGame: symbian-native-started`. `SymbianBridge` theo mẫu `AzaharBridge`: hộp nhập chữ, hộp Có/Không, rung, `exitInstance` | `symbian/SymbianRuntime.kt`, `symbian/SymbianBridge.kt` | Unit test phần chép tài nguyên + ghi `config.yml`; S6 |
| **S4** | **Nhập firmware**: Cài đặt → "Máy Symbian" (chỉ hiện khi gói `symbian` hỗ trợ ABI của máy). `SymbianSetupActivity` (`:game`) liệt kê máy (đọc `data/devices.yml` bằng Kotlin để có `platver`; tên lấy từ `getDevices`), cho thêm, đổi tên (`setDeviceName`), xóa (`deleteDevice`). Thêm máy theo 3 đường ở mục 3.5: **(1) Gói cấu hình sẵn `.zip`**: kiểm có `data/devices.yml`, giải nén vào `data_tmp/`, hỏi rồi mới thay `data/`; làm bằng Kotlin, chạy ở tiến trình chính, không cần gói native. **(2) ROM**: chọn `.rom`; nếu `doesRomNeedRPKG` thì hỏi thêm `.rpkg`; gọi `installDevice(rpkg, rom, true, true)`. **(3) VPL**: chọn thư mục (`OpenDocumentTree`), chép cả thư mục, tìm `*.vpl`, gọi `installDevice("", vpl, false, true)`. Với (2) và (3): chép file từ SAF vào `filesDir/symbian/import/` (kiểm chỗ trống bằng `StatFs`), xong thì xóa. Luôn `isolateDrives = true` (mỗi máy có ổ C/D/E riêng). Đổi mã lỗi sang câu tiếng Việt (mục 3.5). Thêm máy xong thì khởi động lại `:game` (S5). Thêm nút "Sao lưu máy Symbian (.zip)": nén `data/` thành đúng định dạng gói cấu hình sẵn, để cài lại Monika không phải nhập lại. Màn hình ghi rõ: "Monika không cung cấp firmware. Hãy dùng bản sao từ máy Nokia của chính bạn." | `symbian/SymbianSetupActivity.kt`, `symbian/SymbianDevices.kt` (đọc `devices.yml`), `ui/screens/SettingsScreen.kt`, `AndroidManifest.xml` | Unit test: kiểm zip hợp lệ/không hợp lệ, đọc `devices.yml` mẫu, bảng mã lỗi. Robolectric chụp màn. Firmware thật: hỏi sếp (mục 5) |
| **S5** | **Cài game và chơi.** (a) `SisInfo.detect(file)` đọc 12 byte đầu → `NEW` / `OLD` / `STUB` / `UNKNOWN`. (b) Chọn máy: `OLD` → máy có `platver` từ `epoc6` tới `epoc81b`; `NEW`/`STUB` → `epoc91` tới `epoc95`; nhiều máy hợp thì lấy máy dùng gần nhất cho đời đó; không có máy hợp → báo "Game này cần máy Symbian 9 (S60v3/v5). Vào Cài đặt → Máy Symbian để thêm". Đổi được trong menu game. (c) Lưu ánh xạ ở `filesDir/symbian/games.json`: `key` → `{uid, firmwareCode, kind, size, mtime}`. Không ghi vào `.monika.json`, vì thư mục game có thể không ghi được. (d) Luồng: `SymbianFlowActivity` (tiến trình chính) điều phối: chưa có máy → `SymbianSetupActivity`; chưa có ánh xạ → `SymbianInstallActivity` (`:game`): `setCurrentDevice(id, false)`, lưu `getApps()` trước khi cài, rồi `installApp` từng `.sis`/`.sisx` trong thư mục game (theo tên) hoặc `installNGageGame(thư mục)`, ghi trạng thái chờ vào `games.json`, đóng màn, kết thúc tiến trình. `SymbianFlowActivity` đợi `:game` tắt hẳn (dò `ActivityManager.runningAppProcesses`, tối đa khoảng 3 giây) rồi mở `SymbianGameActivity`. Màn này so `getApps()` với danh sách cũ: đúng 1 app mới → lưu UID; 0 hoặc nhiều app mới → sheet Monika cho chọn (tên + biểu tượng từ `getAppIcon`). (e) `SymbianGameActivity` (`:game`): `setCurrentDevice(id, true)`, `setScreenParams` (nền than, giữ tỉ lệ, dọc thì căn trên), `SurfaceView`, `launchApp(uid)` sau `surfaceChanged` đầu tiên; `exitInstance` → `Diagnostics.end`, đóng màn, kết thúc tiến trình. (f) Bàn phím ảo: thêm `PadLayout.PHONE` theo thiết kế bàn phím J2ME (trái số 3×4; phải mềm trái / ↑ / mềm phải, ← OK →, ↓; hàng nhỏ C, ✎, Gọi, Kết thúc), gửi mã phím ở mục 3.6. Màn cảm ứng S60v5 chạm thẳng vào game bằng `touchScreen`. Phím vật lý và tay cầm map theo `KeyMapper` gốc, thêm `BUTTON_A` → OK. (g) Menu trong game (thành phần Monika): Thoát, Chụp màn hình (`saveScreenshotTo`), Ẩn/hiện bàn phím, Đổi máy Symbian cho game này (xóa ánh xạ, cài lại). (h) Ảnh bìa: game chưa có bìa thì lấy `getAppIcon(uid)`. (i) Thư viện: thêm luật `{"system": "symbian", "markers": ["system/apps/*/*.app"]}` để nhận thẻ N-Gage dạng thư mục. Việc này cần `GameDetector` hiểu `*` = một đoạn đường dẫn, chỉ áp khi marker có `*` | `symbian/` (Flow, Install, Game, `SisInfo`, `SymbianGames`), `runner/GamePadOverlay.kt`, `library/GameLibrary.kt`, `config/monika-config.json`, `AndroidManifest.xml` | Unit test `SisInfo` (byte giả), chọn máy theo `platver`, đọc/ghi `games.json`, marker có `*`. Robolectric chụp lớp phủ `PHONE`. Đo khoảng cách giữa 2 lần chạy tiến trình. **Thử bỏ khởi động lại (U3):** nếu `getApps()` thấy app mới ngay sau `installApp` thì gộp cài và chơi vào một lần chạy |
| **S6** | Emulator Test: thêm khối Symbian vào `ci-emulator-games.sh` theo mẫu Kirikiri: tải gói theo URL trong config, đẩy vào `files/packs/symbian`, mở `SymbianGameActivity` với `--ez selftest true` (chỉ nạp thư viện + `startNative`, in kết quả, thoát). Không có firmware nên mong đợi `symbian-lib-loaded`, `symbian-native-started false` và không có `FATAL`/`Fatal signal`. Kiểm này bắt được lỗi X1 và X2 trên bản release thật | `scripts/ci-emulator-games.sh`, `.github/workflows/emulator-test.yml` | `LOADED` trên API 30 và 34. CI **không bao giờ** chứa firmware |
| **S7** | Phát hành và chuyển hẳn: chạy workflow S0 với tùy chọn đưa lên `aowvn-monika-packs` (tag `engines-symbian-<n>`). Điền `modules.symbian` (`url` có `{abi}`, `abis: ["arm64-v8a","armeabi-v7a"]`, `sha256ByAbi`, `sizeByAbi`, `version` = `<commit 7 ký tự>-aow<n>`). Thêm `systems[symbian].engine = "symbian"`, **giữ** `runner: external` + `externalApp: eka2l1` cho app cũ (như E0.4). `prefetchByExtension` thêm `"sis": ["symbian"], "sisx": ["symbian"]`. Tăng `configVersion` và phiên bản app. Sếp chạy bảng thử máy thật (thêm mục Symbian vào `docs/TEST-MAY-THAT.md`). Đạt thì đặt `allowExternalApp: false` cho `symbian` và bỏ hướng dẫn cài EKA2L1 | `config/monika-config.json`, `docs/TEST-MAY-THAT.md`, `docs/GIAO-TIEP-VOI-OPUS.md` mục 4 | `ConfigTest`; `python3 scripts/audit-cores.py` không lỗi; kết quả máy thật của sếp |

---

## 5. Điều chưa chắc

| # | Điều chưa chắc | Kiểm ở bước | Nếu sai thì |
|---|---|---|---|
| U1 | Build EKA2L1 trên GitHub Actions mất bao lâu, có qua giới hạn thời gian không (FFmpeg dựng lúc build, libuv tải lúc cấu hình) | S0 | Cache như workflow gốc (`build/ffmpeg-cache`); vẫn hỏng thì dùng dự phòng mục 6 |
| U2 | Kích thước `.so` tự dựng có khớp bản chính thức (đã cắt ký hiệu chưa) | S0 | Thêm bước `llvm-strip --strip-unneeded` như các job khác |
| U3 | Sau `installApp`, `getApps()` có thấy app mới mà không cần khởi động lại tiến trình | S5 | Giữ luồng 2 lần chạy tiến trình |
| U4 | Đợi `:game` tắt hẳn rồi mở lại có ổn định trên mọi máy | S5 | Dùng cách bản gốc: `startActivity` rồi `Runtime.exit(0)` (`AppUtils.restart`) |
| U5 | Hộp văn bản / câu hỏi trong lúc cài SIS: ngôn ngữ thì native tự chọn theo ngôn ngữ của máy (`src/emu/system/src/epoc.cpp` 259–280), nhưng hàm `show_text` không được gắn trên Android (chỉ bản Qt gắn), nên các câu hỏi khi cài bị bỏ qua (`src/emu/package/src/sis_script_interpreter.cpp` 617–622). Chưa rõ game nào cài sai vì thế | S5 | Báo rõ trong màn cài; nếu game thật cài sai thì hỏi sếp có vá native không (mục 6) |
| U6 | Luật chọn máy theo `platver` đúng với game thật (vd. game S60v3 chạy trên máy S60v5) | S5 | Người chơi đổi máy trong menu game; điều chỉnh luật theo game của sếp |
| U7 | JIT `dynarmic` dưới lớp dịch ARM trong máy ảo x86_64 | S6 | S6 dừng ở `startNative`; thử `cpu: dyncom` |
| U8 | Đuôi `.ngage` trong config là gì (không thấy trong mã EKA2L1; game N-Gage 2.0 cần cài app N-Gage và giấy phép qua `installNG2Licenses`) | S5 | Cần sếp cho file mẫu; chưa có thì để `.ngage` báo "chưa hỗ trợ" |
| U9 | Dung lượng một máy sau khi cài từ VPL | S4 | Ghi số thật; cảnh báo trước khi cài nếu máy ít chỗ |
| U10 | Tọa độ chạm khi SurfaceView không phủ cả màn (bàn phím chiếm nửa dưới) | S5 | Dùng tọa độ trong SurfaceView như bản gốc |
| U11 | Game có nhiều `.sis` (game + thư viện phụ như Python/Open C) cần thứ tự cài riêng | S5 | Cho chọn file chính; thư viện phụ cài trước |
| U12 | Bộ dịch `r12l1` trên máy 32-bit có ổn với game thật | S7 | Cần sếp thử trên máy 32-bit nếu có |

**Sonnet phải hỏi sếp (Q5), không tự tải firmware hay game:**

| Khi tới bước | Hỏi sếp | Dùng cho |
|---|---|---|
| S0 | Không cần | — |
| S4 | Firmware của sếp, ít nhất một trong 3 dạng ở mục 3.5. Tốt nhất có cả máy Symbian 9 (S60v3/v5) và máy N-Gage | Kiểm 3 đường nhập |
| S5 | 3 game: một `.sisx` S60v3 (bàn phím), một game S60v5 (cảm ứng), một thẻ N-Gage 1.0 dạng thư mục `system/apps/...`. Kèm file `.ngage` nếu sếp có | Kiểm cài, chọn máy, bàn phím, chạm |
| S7 | Sếp chạy bảng thử trên máy thật | Cổng tắt app ngoài |
| Bất kỳ lúc nào chạm ngưỡng "Dừng" hoặc thực tế khác plan | Báo sếp, chờ quyết | — |

Mỗi lần hỏi: một câu, nói rõ cần gì và cho bước nào. Trong lúc chờ, làm tiếp các phần không cần firmware (test, workflow, giao diện).

---

## 6. Dự phòng

| Khi | Làm gì |
|---|---|
| S0 không dựng được từ nguồn | Lấy `libnative-lib.so` + 4 thư mục tài nguyên từ `EKA2L1-Android.apk` chính thức. Chép APK sang `aowvn-monika-packs` bằng `mirror-pack.yml` để ghim (tag cuộn sẽ bị dời). Ghi SHA-256 của APK và commit nguồn (lấy `GIT_HASH` trong `BuildConfig` của APK) vào `SOURCE.txt` để đủ nghĩa vụ GPL. Phần còn lại giữ nguyên |
| Native cần vá (vd. U5) | Thêm bước áp bản vá trong job S0, theo mẫu `kirikiri/patches/`, ghi vào `engines/symbian/UPSTREAM.md` |

EKA2L1 không có lõi libretro nên không có đường libretro. Sau khi tắt app ngoài cho `symbian` (S7), không còn dự phòng nào mở app ngoài (Q4). Trước S7, hệ `symbian` vẫn mở app EKA2L1 như hiện nay.

---

## 7. Bảng tổng kết

| Câu hỏi | Kết luận | Việc Sonnet cần làm tiếp |
|---|---|---|
| 8. Nhúng Symbian (EKA2L1): đóng gói thế nào, nhập firmware ra sao? | **Đóng gói:** mô hình Azahar. Lớp JNI viết lại bằng Kotlin, giữ tên `com.github.eka2l1.emu.*`. Gói tải thêm `symbian-{abi}.zip` (arm64 + armv7, khoảng 10–12 MB) dựng từ nguồn ghim commit bằng Gradle gốc, không vá native. **Firmware:** người chơi tự nhập trong Cài đặt → Máy Symbian theo 3 đường (zip cấu hình sẵn / ROM (+RPKG) / VPL). Lưu ở `filesDir/symbian/`, mỗi máy ổ riêng, có nút sao lưu ra zip. **Game:** cài lần đầu, lưu UID, sau đó mở thẳng | 1) [A1] Nếu sếp đồng ý thì chạy S0 ngay; không thì đợi sau khối P. 2) S1 → S3 (mỗi bước một PR). 3) S4: hỏi sếp firmware. 4) S5: hỏi sếp 3 game. 5) S6. 6) S7: sếp thử máy thật, đạt thì `allowExternalApp: false` cho `symbian`. Sau mỗi khối cập nhật mục 4 của `docs/GIAO-TIEP-VOI-OPUS.md` |

---

## 8. Cần sếp chốt

| # | Câu hỏi | Đề xuất của Opus |
|---|---|---|
| A1 | Chạy S0 (spike CI, không đụng app, không phát hành gì) ngay bây giờ, hay đợi xong RPG Maker + Ren'Py như Q6? | Chạy S0 ngay để có số thật về thời gian build và kích thước. S1–S7 vẫn đợi sau khối P |
| A2 | Làm đủ 3 đường nhập firmware ngay bản đầu? | Đủ 3, theo thứ tự: zip cấu hình sẵn → ROM (+RPKG) → VPL. Zip làm trước vì thuần Kotlin, dễ kiểm nhất |

Đã tự chọn (kỹ thuật, không cần sếp quyết): mỗi máy Symbian có ổ riêng (`isolateDrives = true`); dữ liệu người chơi ở `filesDir/symbian/`; ánh xạ game → UID ở `filesDir/symbian/games.json`; chạy trong tiến trình `:game` để `Diagnostics` nhận đúng báo cáo game chết.

### Lệnh giao Sonnet cho S0 (dán nguyên văn khi sếp chốt A1)

> Đọc `docs/opus/2026-10-03-nhung-symbian-eka2l1.md`, làm bước **S0** và chỉ S0. Thêm job `symbian-android` vào `.github/workflows/build-engines.yml` theo đúng mẫu job `azahar-android` và mô tả ở bảng mục 4. Commit mặc định `c396ac8`. Chỉ upload artifact, không tạo Release, không sửa app hay config. Chạy workflow bằng `workflow_dispatch` với `only=symbian`. Ghi thời gian build, kích thước từng file, nội dung `needed.txt` và số dòng `jni-symbols.txt` vào `docs/opus/ket-qua/S0.md`, rồi dừng và báo sếp.
