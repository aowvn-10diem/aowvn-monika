# PLAN: Trình cài game Android trong Monika (APK / APKS / XAPK / OBB / Data / game cũ)

> Người thi công: Claude Code (Sonnet). Người duyệt: sếp.
> Đọc hết file này + `CLAUDE.md` trước khi viết code.
> Kết luận kỹ thuật dưới đây đã đối chiếu mã nguồn AOSP (link cuối file). Chỗ ghi **[CHƯA KIỂM]** = phải thử trên máy thật, không được tự khẳng định là chạy.

---

## 0. Mục tiêu & phạm vi

| # | Mục tiêu | Phạm vi |
|---|---|---|
| G1 | Cài **APK**, **APKS** (SAI/bundletool), **XAPK** (APKPure) | Có |
| G2 | Tự chép **OBB** vào `Android/obb/<pkg>/` | Có |
| G3 | Tự chép **Data** vào `Android/data/<pkg>/` (kiểu ZArchiver) | Có |
| G4 | Cài **game cũ** (targetSdk thấp, Android 14+ chặn) | Có |
| G5 | Game **chỉ có thư viện 32-bit** trên máy chỉ chạy 64-bit | **Không cài**. Phát hiện và báo: dùng app máy ảo (vd. VPhoneGaGa) hoặc điện thoại Android cũ |
| — | APKM (APKMirror, mã hóa) | Không hỗ trợ, báo rõ |
| — | Shizuku / root | Không dùng |

UI: mọi hộp thoại dùng **MonikaMenuSheet**, tiến trình dùng `Notifier.progress`, icon qua `MonikaIcon`/`Emoji` (emoji 3D). Chữ tiếng Việt ngắn, luôn nói bước tiếp theo.

---

## 1. Sự thật kỹ thuật đã kiểm

| # | Sự thật | Nguồn |
|---|---|---|
| F1 | Android 14 chặn cài app `targetSdk < 23`; Android 15/16 chặn `< 24` → lỗi `INSTALL_FAILED_DEPRECATED_SDK_VERSION` | AOSP `InstallPackageHelper.java` |
| F2 | Cờ `INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK` bị hệ thống **xóa ngầm** nếu người gọi không phải system/root/shell → app thường không tự bỏ chặn được | AOSP `PackageInstallerService.java` (~dòng 856–862) |
| F3 | APK `testOnly` cũng không cài được từ app thường (cần quyền `INSTALL_TEST_ONLY_PACKAGE`) | AOSP `PackageInstallerService.java` (~dòng 752–755) |
| F4 | App đã được bật "Cài ứng dụng không rõ nguồn gốc" được mount bộ nhớ chế độ `MOUNT_MODE_EXTERNAL_INSTALLER` → **ghi được `Android/obb/`** của app khác. Bật/tắt quyền thì hệ thống tự mount lại | AOSP `StorageManagerService.java` (~dòng 4620–4645, case `OP_REQUEST_INSTALL_PACKAGES`) |
| F5 | `Android/data/` của app khác: app thường không truy cập được, kể cả có `MANAGE_EXTERNAL_STORAGE` | Android docs "Manage all files" |
| F6 | **Cơ chế ZArchiver**: xin quyền SAF (`ACTION_OPEN_DOCUMENT_TREE`) cho **đúng thư mục con** `Android/data/<pkg>`. Thư mục phải tồn tại → game phải được cài và mở 1 lần trước (Android tạo thư mục khi game gọi `getExternalFilesDir`). Chạy trên Android 11–12, Android 13 tùy bản vá. **Bản vá bảo mật 03/2024 đã chặn** (trang chính thức ZArchiver). Mã AOSP hiện tại: `isRestrictedPath()` duyệt mọi thư mục cha nên mọi thư mục con của `Android/data` đều bị ẩn và chặn chọn | AOSP `ExternalStorageProvider.java`; zdevs.ru/en/za/android_data_obb.html |
| F7 | Quyền shell (qua Gỡ lỗi không dây) làm được: `pm install --bypass-low-target-sdk-block`, ghi `Android/data/<pkg>` | adb docs + F2 |
| F8 | Máy chỉ 64-bit: `Build.SUPPORTED_32_BIT_ABIS` rỗng (Pixel 7+, Galaxy S24+…). App không có cách chạy `.so` 32-bit | Android docs; Samsung Knox KBA-1150 |

**Hệ quả thiết kế**
- APK/APKS/XAPK + OBB: làm **không cần gỡ lỗi** (F4).
- Data: thử lần lượt 3 cách (§5): ghi thẳng (Android ≤ 10) → SAF từng gói kiểu ZArchiver (máy chưa vá) → Gỡ lỗi không dây.
- Game cũ: **bắt buộc** Gỡ lỗi không dây (F2). Xong việc thì Monika **tự tắt Gỡ lỗi không dây** (khuyến nghị bảo mật: không để cổng gỡ lỗi mở khi không dùng).

---

## 2. Kiến trúc

Gói mới `vn.aow.monika.apkinstall`:

| File | Việc |
|---|---|
| `ApkInspector.kt` | Đọc APK/APKS/XAPK/thư mục: package, version, minSdk, targetSdk, ABI, splits, OBB, Data |
| `SplitSelector.kt` | Chọn split hợp máy (ABI, mật độ, ngôn ngữ) |
| `SessionInstaller.kt` | Cài bằng `PackageInstaller` session (không cần gỡ lỗi) |
| `InstallResultReceiver.kt` | Nhận `STATUS_*` của session |
| `ObbInstaller.kt` | Chép OBB (F4) |
| `DataInstaller.kt` | Chép Data theo 3 chiến lược |
| `SafDataAccess.kt` | Cơ chế SAF từng gói (F6) |
| `adb/LocalAdb.kt` | Gỡ lỗi không dây: ghép đôi, kết nối, chạy lệnh, tự tắt |
| `adb/AdbPairingUi.kt` | Sheet hướng dẫn + nhập mã ghép đôi trong thông báo |
| `ApkInstallFlow.kt` | Điều phối: kiểm tra → cài → OBB → Data → báo kết quả |
| `ApkInstallSheet.kt` | UI tóm tắt game + cảnh báo + nút Cài |

Điểm nối vào code hiện có:
- `runner/GameLauncher.kt` (~dòng 56): `"apk" -> Installer.install(...)` → `ApkInstallFlow.start(activity, game)`.
- `ui/screens/DownloadsScreen.kt` (~291) và `download/BrowserDownloads.kt` (~197) đang gọi `Installer.installIntent` cho `.apk` → chuyển sang `ApkInstallFlow`.
- `config/monika-config.json`, hệ `apk`: thêm đuôi `apks`, `xapk`, `apkm`; tăng `configVersion`.
- `library/Importer.kt`: khi giải nén bài viết game Android, **giữ nguyên** thư mục `obb/`, `Android/obb/`, `data/`, `Android/data/` để flow dùng.
- Giữ object `Installer` cũ làm đường lui khi session lỗi lạ.

---

## 3. Giai đoạn 1 — Kiểm tra + cài APK/APKS/XAPK (không cần gỡ lỗi)

### 3.1 `ApkInspector`
```kotlin
data class InspectResult(
    val kind: Kind,                 // APK, APKS, XAPK, FOLDER, APKM_UNSUPPORTED
    val packageName: String,
    val label: String?,
    val versionCode: Long,
    val versionName: String?,
    val minSdk: Int,
    val targetSdk: Int,
    val apks: List<File>,           // base + split (đã giải nén ra thư mục tạm nếu từ .apks/.xapk)
    val nativeAbis: Set<String>,    // từ lib/<abi>/ của base + split ABI
    val obbFiles: List<ObbFile>,    // file nguồn + tên đích "main.<vc>.<pkg>.obb"
    val dataDir: File?,             // nội dung sẽ chép vào Android/data/<pkg>
    val problems: List<Problem>,
)
```
- Manifest: `PackageManager.getPackageArchiveInfo(base.path, 0)`; đọc `applicationInfo.targetSdkVersion`, `minSdkVersion`. Nhãn/icon: gán `applicationInfo.sourceDir = publicSourceDir = base.path` rồi `loadLabel`/`loadIcon`.
- ABI: liệt kê entry `lib/<abi>/*.so` bằng `ZipFile` (không giải nén).
- **XAPK**: zip có `manifest.json`. Đọc `package_name`, `version_code`, `split_apks[] {file,id}`, `expansions[] {file, install_location, install_path}`. Thiếu trường → dò theo đuôi file. OBB thường ở `Android/obb/<pkg>/`. **[CHƯA KIỂM]** biến thể manifest → parser khoan dung + test với file mẫu thật.
- **APKS**: zip gồm `base.apk` + `split_*.apk` (có thể kèm `toc.pb`, `info.json`, `icon.png`) → lấy mọi `*.apk`.
- **APKM**: có `info.json` + APK mã hóa → `APKM_UNSUPPORTED`.
- **Thư mục đã giải nén** (bài AowVN hay đóng gói kiểu này), dò các bố cục:
  - `*.apk` ở gốc hoặc sâu 1 cấp.
  - OBB: `Android/obb/<pkg>/`, `obb/<pkg>/`, `<pkg>/*.obb`, `*.obb` rời.
  - Data: `Android/data/<pkg>/`, `data/<pkg>/`, thư mục tên đúng `<pkg>` không chứa `.obb`.
  - Nhiều APK khác gói → sheet cho user chọn.
- Giải nén `.apks/.xapk` vào `cacheDir/apkinstall/<uuid>/`, xóa sau khi cài xong/thất bại.

### 3.2 `Problem` (kiểm tra trước khi cài)
| Problem | Điều kiện | Xử lý |
|---|---|---|
| `ONLY_32BIT` | `nativeAbis` khác rỗng, chỉ gồm `armeabi`/`armeabi-v7a`/`x86`, và `Build.SUPPORTED_32_BIT_ABIS` rỗng | **Dừng.** Sheet: "Máy bạn chỉ chạy app 64-bit, game này chỉ có bản 32-bit. Hãy dùng app máy ảo Android (vd. VPhoneGaGa) hoặc một điện thoại Android cũ." Nút "Đã hiểu". Không có nút Cài |
| `NO_MATCHING_ABI` | Có `.so` nhưng không ABI nào trong `Build.SUPPORTED_ABIS` | Dừng, báo tương tự |
| `LOW_TARGET_SDK` | `SDK_INT >= 34` và `targetSdk < minInstallable` (34→23; ≥35→24) | Đi nhánh game cũ (§6) |
| `MIN_SDK_TOO_HIGH` | `minSdk > SDK_INT` | Dừng: "Game cần Android X trở lên" |
| `DOWNGRADE` | Đã cài bản `versionCode` cao hơn | Hỏi: gỡ bản cũ rồi cài (cảnh báo mất dữ liệu) |
| `SIGNATURE_MISMATCH` | Chỉ biết sau khi cài (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) | Hỏi: gỡ bản cũ rồi cài lại |
| `LOW_SPACE` | Tổng dung lượng APK + OBB + Data > dung lượng trống × 0.9 | Dừng, báo cần thêm bao nhiêu GB |

Game thuần Java (không có `lib/`) → không bị ảnh hưởng 32/64-bit.

### 3.3 `SplitSelector`
- Luôn lấy `base.apk` + split tính năng (tên không bắt đầu bằng `config.`).
- ABI: đúng **1** split `config.<abi>` theo thứ tự `Build.SUPPORTED_ABIS` (đổi `-` thành `_`).
- Mật độ: split `config.<dpi>` gần `densityDpi` nhất (ldpi 120, mdpi 160, tvdpi 213, hdpi 240, xhdpi 320, xxhdpi 480, xxxhdpi 640).
- Ngôn ngữ: lấy mọi `config.<lang>`.

### 3.4 `SessionInstaller`
- `PackageInstaller.SessionParams(MODE_FULL_INSTALL)`, `setAppPackageName(pkg)`, `setSize(tổng byte)`; API 31+: `setInstallReason`/`setRequireUserAction(USER_ACTION_NOT_REQUIRED)` (chỉ có tác dụng khi Monika là nơi đã cài bản trước).
- Ghi từng APK qua `session.openWrite(name, 0, size)` + `fsync`, cập nhật % lên thông báo.
- `commit(PendingIntent → InstallResultReceiver)`; PendingIntent `FLAG_MUTABLE` (API 31+).
- `STATUS_PENDING_USER_ACTION` → mở `Intent.EXTRA_INTENT` (thêm `FLAG_ACTIVITY_NEW_TASK`).
- `STATUS_SUCCESS` → sang OBB/Data. Lỗi → map `EXTRA_STATUS_MESSAGE` sang tiếng Việt: `DEPRECATED_SDK_VERSION` → nhánh §6; `UPDATE_INCOMPATIBLE`; `INSUFFICIENT_STORAGE`; `NO_MATCHING_ABIS`; `VERSION_DOWNGRADE`; `USER_ABORTED` (user bấm Hủy → không báo lỗi đỏ).
- Chưa bật quyền cài → giữ logic cũ mở `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, quay lại thì tự tiếp tục flow (lưu trạng thái flow vào `SavedStateHandle`/prefs).
- Chạy trong `WorkManager` foreground (giống `ImportWorker`) để file vài GB không bị ngắt.

---

## 4. Giai đoạn 2 — OBB (không cần gỡ lỗi)

`ObbInstaller`:
1. Chỉ chạy sau khi cài APK thành công (package đã tồn tại).
2. Đích: `Environment.getExternalStorageDirectory()/Android/obb/<pkg>/`. `mkdirs()`.
3. Chép bằng `FileInputStream/FileOutputStream` (hoặc `Files.copy`), có % trên thông báo, kiểm tra kích thước sau khi chép. Nếu nguồn cùng phân vùng và Monika sở hữu → thử `renameTo` trước (nhanh), thất bại thì chép.
4. Đặt đúng tên: giữ tên gốc nếu đã đúng quy ước `main|patch.<vc>.<pkg>.obb`; nếu chỉ có 1 file lạ tên → đổi thành `main.<versionCode>.<pkg>.obb`.
5. Nếu ghi bị từ chối (`EACCES`) → **[CHƯA KIỂM]** kiểm tra lại quyền "Cài ứng dụng không rõ nguồn gốc" đang bật; nếu bật mà vẫn lỗi thì chuyển sang chiến lược gỡ lỗi (§6) cho bước chép.
6. Test tay bắt buộc: Android 11, 13, 14, 15 (mỗi bản 1 máy).

---

## 5. Giai đoạn 3 — Data `Android/data/<pkg>/` (kiểu ZArchiver)

`DataInstaller` thử lần lượt, dừng ở cách đầu tiên thành công:

| Thứ tự | Chiến lược | Điều kiện | Cách làm |
|---|---|---|---|
| D1 | Ghi thẳng | `SDK_INT <= 29` | Xin `WRITE_EXTERNAL_STORAGE` (manifest thêm `maxSdkVersion="29"`), chép như file thường |
| D2 | SAF từng gói (cơ chế ZArchiver) | `SDK_INT >= 30` | Xem 5.1 |
| D3 | Gỡ lỗi không dây | Khi D2 bị chặn | Dùng `LocalAdb` (§6) chạy lệnh chép bằng quyền shell |

### 5.1 `SafDataAccess` (D2)
1. Đảm bảo thư mục `Android/data/<pkg>` tồn tại: hướng dẫn user **mở game 1 lần rồi thoát** (Monika mở giúp bằng `getLaunchIntentForPackage`, chờ user quay lại) — Android tạo thư mục khi game khởi động.
2. Tạo URI gợi ý:
   `DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Android/data/<pkg>")`
   và mở `ACTION_OPEN_DOCUMENT_TREE` với `EXTRA_INITIAL_URI` = URI đó. Sheet hướng dẫn trước: "Bấm **Dùng thư mục này** → **Cho phép**".
3. Nhận tree URI → kiểm tra đúng `primary:Android/data/<pkg>` (sai thư mục → báo và cho chọn lại) → `takePersistableUriPermission(READ|WRITE)`, lưu URI theo package vào prefs để lần sau không phải xin lại.
4. Chép cây thư mục bằng `DocumentFile` (tạo thư mục con, ghi file, % tiến độ).
5. Phát hiện bị chặn: nút "Dùng thư mục này" bị mờ → user bấm Hủy/quay lại, hoặc URI trả về không đúng thư mục. Khi đó chuyển D3 và ghi nhớ "máy này chặn SAF" để lần sau đi thẳng D3.
6. **[CHƯA KIỂM]** trên máy có bản vá ≥ 03/2024 (dự kiến bị chặn theo F6). Ghi kết quả thử vào `docs/`.

---

## 6. Giai đoạn 4 — Game cũ + Data trên máy đã vá: Gỡ lỗi không dây

Dùng cho: `LOW_TARGET_SDK` (F1, F2) và D3.

### 6.1 Thư viện
- `libadb-android` (MuntashirAkon; giấy phép kép GPL-3.0-or-later **hoặc Apache-2.0** → chọn Apache-2.0, ghi vào `assets/licenses/`). Hỗ trợ ghép đôi bằng mã (Android 11+) và kết nối adbd của chính máy.
- Phụ thuộc: `org.conscrypt:conscrypt-android` (TLS ghép đôi). Kiểm tra kích thước APK tăng bao nhiêu, ghi vào báo cáo.
- Thêm luật `-keep` cần thiết vào `proguard-rules.pro`, kiểm tra bằng `dexdump` như `CLAUDE.md` hướng dẫn.
- Chỉ Android 11+ (Gỡ lỗi không dây). Android 8–10 không có game bị chặn targetSdk nên không cần.

### 6.2 Luồng người dùng
1. Sheet giải thích ngắn: "Game này làm cho Android đời cũ. Để cài, Monika cần bật tạm **Gỡ lỗi không dây** (chỉ trong lúc cài, xong tự tắt)."
2. Chưa bật Tùy chọn nhà phát triển → hướng dẫn bấm 7 lần "Số hiệu bản dựng" (mở `Settings.ACTION_DEVICE_INFO_SETTINGS`).
3. Mở `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` → user bật **Gỡ lỗi không dây** → vào "Ghép nối thiết bị bằng mã ghép nối".
4. Monika hiện **thông báo có ô nhập** (RemoteInput) để nhập mã 6 số + cổng ghép đôi mà không phải rời màn Cài đặt. Cổng ghép đôi/cổng kết nối: dò qua mDNS (`NsdManager`, dịch vụ `_adb-tls-pairing._tcp` và `_adb-tls-connect._tcp`). **[CHƯA KIỂM]** độ ổn định mDNS trên các hãng; có ô nhập tay cổng làm đường lui.
5. Kết nối localhost → chạy lệnh (mục 6.3), tiến độ trên thông báo.
6. **Tự tắt khi xong**: `settings put global adb_wifi_enabled 0` là lệnh cuối cùng (kết nối sẽ đóng ngay sau đó). Hiện toast "Đã cài xong và đã tắt Gỡ lỗi không dây". Nếu lệnh tắt thất bại → sheet nhắc user tự tắt, có nút mở đúng màn Cài đặt.
7. Lưu khóa ghép đôi (RSA) trong bộ nhớ riêng của app để lần sau chỉ cần bật lại Gỡ lỗi không dây, không phải ghép đôi lại. **[CHƯA KIỂM]** ghép đôi còn giữ sau khi tắt/bật không.

### 6.3 Lệnh shell
- Đẩy APK: ghi vào `/data/local/tmp/monika/` qua sync `push` của thư viện (shell đọc được), hoặc cài theo luồng `pm install-create / install-write / install-commit` với nhiều split:
  - `pm install-create -r --bypass-low-target-sdk-block -S <tổng byte>` → lấy session id
  - `pm install-write -S <size> <sid> <tên> -` (stream từng APK)
  - `pm install-commit <sid>`
  - Chỉ thêm `--bypass-low-target-sdk-block` khi `SDK_INT >= 34`.
- Data (D3): push vào `/sdcard/Android/data/<pkg>/…` (shell ghi được), hoặc `cp -r` từ thư mục tạm Monika đã chép ra `/sdcard/Download/.monika-tmp/` rồi xóa tạm.
- Mọi lệnh: timeout, đọc exit code + stdout, lỗi → thông báo tiếng Việt + ghi log vào `Diagnostics`.
- Dọn `/data/local/tmp/monika/` sau khi xong.

### 6.4 Sau khi cài game cũ
- Android có thể hiện hộp thoại "Ứng dụng này được tạo cho phiên bản Android cũ" khi mở game lần đầu → sheet hướng dẫn trước: bấm OK là chạy.
- Game targetSdk < 23 được cấp quyền kiểu cũ; Android 10+ có màn "xem lại quyền" khi mở lần đầu → hướng dẫn tương tự.

---

## 7. `ApkInstallFlow` — trình tự tổng

```
inspect → (Problem chặn? báo & dừng)
        → LOW_TARGET_SDK ? cài qua LocalAdb : SessionInstaller
        → OBB? ObbInstaller (lỗi quyền → LocalAdb)
        → Data? DataInstaller (D1 → D2 → D3)
        → xong: thông báo "Đã cài <tên>" + nút Chơi (mở getLaunchIntentForPackage)
        → dọn thư mục tạm; hỏi "Xóa file cài để giải phóng X GB?" (mặc định theo cài đặt dọn bộ đệm hiện có)
```
- Ghi trạng thái từng bước vào `GameTasks` để thẻ game trong Thư viện hiện "Đang cài… 45%".
- Thư viện: game APK đã cài hiện nút **Chơi** (mở app đã cài) thay vì **Cài** (so `packageName` với `PackageManager`).

---

## 8. Test

**Unit (Robolectric, chạy trong CI):**
- `ApkInspectorTest`: file mẫu nhỏ tự tạo trong `src/test/resources/apkinstall/` — APK đơn, APKS (base + 3 split), XAPK (manifest.json + obb), APKM giả; thư mục bố cục `Android/obb`, `obb/<pkg>`, `data/<pkg>`.
- `SplitSelectorTest`: các tổ hợp ABI/mật độ.
- `ProblemsTest`: 32-bit trên máy 64-bit-only (giả lập `SUPPORTED_32_BIT_ABIS` rỗng), targetSdk thấp theo từng SDK_INT, minSdk cao.
- `ObbNameTest`: đổi tên OBB theo quy ước.

**Thử tay (bắt buộc trước khi phát hành, ghi kết quả vào `docs/apkinstall-test.md`):**
| Ca | Android 11 | 13 | 14 | 15/16 |
|---|---|---|---|---|
| APK thường | | | | |
| APKS / XAPK | | | | |
| OBB | | | | |
| Data qua SAF (D2) | | | | |
| Data qua Gỡ lỗi (D3) | | | | |
| Game targetSdk < 23 | — | — | | |
| Game chỉ 32-bit trên máy 64-bit-only | báo đúng | | | |

File mẫu: **xin sếp**, không tự tải game.

---

## 9. Thứ tự thi công & tiêu chí xong

| Bước | Nội dung | Xong khi |
|---|---|---|
| 1 | `ApkInspector` + `SplitSelector` + `Problem` + test | Test xanh |
| 2 | `SessionInstaller` + receiver + nối `GameLauncher`/`DownloadsScreen`/`BrowserDownloads` | Cài được APK và APKS trên máy thật |
| 3 | XAPK + `ObbInstaller` + giữ thư mục trong `Importer` | Game có OBB chạy được sau khi cài |
| 4 | `SafDataAccess` (D1, D2) | Chép data thành công trên máy chưa vá; máy đã vá tự chuyển D3 |
| 5 | `LocalAdb` + UI ghép đôi + tự tắt | Cài được game targetSdk thấp trên Android 14+, Gỡ lỗi không dây tự tắt sau khi xong |
| 6 | Thư viện hiện Chơi/Cài, dọn file, `GameTasks` | Luồng từ bài viết → tải → cài → chơi không cần thao tác file tay |
| 7 | Tăng version, commit, chạy release | APK phát hành, gửi sếp thử |

Mỗi bước: build + `testDebugUnitTest` xanh rồi mới commit; commit message tiếng Việt, kết thúc bằng 2 dòng attribution theo `CLAUDE.md`.

---

## 10. Quy tắc cho người thi công

- Không đoán: điều gì chưa thử trên máy thật thì ghi **[CHƯA KIỂM]** trong báo cáo.
- Không thêm quyền Trợ năng, không dùng root/Shizuku, không sửa/ký lại APK của game.
- Không tự tìm/tải file game; xin sếp file mẫu.
- Không để lại Gỡ lỗi không dây đang bật sau khi Monika dùng xong.
- Báo cáo cuối: bảng việc đã làm / chưa làm / [CHƯA KIỂM], kích thước APK tăng thêm, link APK.

---

## Nguồn
- AOSP `InstallPackageHelper.java`, `PackageInstallerService.java`, `StorageManagerService.java`, `ExternalStorageProvider.java`, `Settings.java` — https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/
- Android minimum target SDK matrix — https://bayton.org/android/android-minimum-targetsdk-matrix/
- ZArchiver Android/[data|obb] — https://zdevs.ru/en/za/android_data_obb.html
- Android 13 closes file manager loophole — https://www.esper.io/blog/android-dessert-bites-28-file-manager-loophole-closed-73891524
- Storage updates in Android 11 — https://developer.android.com/about/versions/11/privacy/storage
- libadb-android — https://github.com/MuntashirAkon/libadb-android
- Samsung: Sunsetting 32-bit app support — https://docs.samsungknox.com/dev/knox-sdk/kbas/kba-1150-sunsetting-32-bit-app-support-on-samsung-devices/
