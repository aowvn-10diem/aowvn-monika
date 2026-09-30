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

| F9 | App luôn được ghi vào `Android/data/<pkg>` **của chính nó** (không cần quyền) | Android docs "App-specific storage" |
| F10 | ART tự nạp mọi `classesN.dex` trong APK (Android 5+); ContentProvider của app được tạo **trước** `Application.onCreate` | Android docs multidex; vòng đời ContentProvider |

**Hệ quả thiết kế (sếp đã chốt)**
- APK/APKS/XAPK + OBB: làm **không cần gỡ lỗi** (F4).
- Game cần Data hoặc game cũ (targetSdk thấp): **3 cách, thử theo thứ tự, mỗi game có checklist**:
  - **Cách 1 — Monika chỉnh gói (mặc định, tự động):** chèn bộ nạp data nhỏ vào APK (+ nâng targetSdk nếu game cũ), ký lại, cài, **Monika tự kiểm tra game có chạy không**. Hầu hết game AowVN đã được ký lại sẵn nên ký lại thêm không làm mất gì (sếp xác nhận).
  - **Cách 2 — Cấp quyền thư mục (kiểu ZArchiver):** chỉ cho Data, chỉ máy chưa vá 03/2024.
  - **Cách 3 — Gỡ lỗi không dây:** cài APK gốc với cờ bỏ chặn + chép Data bằng quyền shell. Xong **tự tắt Gỡ lỗi không dây**.
- Cách 1 không chạy → Monika hướng dẫn user Cách 2 / Cách 3, checklist ghi lại cách nào đã thử, kết quả ra sao.

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
| `repack/ApkRepacker.kt` | Cách 1: chèn dex bộ nạp + khai báo Service/Provider vào manifest, (tùy) nâng targetSdk, ký lại |
| `repack/RepackKeyStore.kt` | Khóa ký riêng mỗi máy (tạo lần đầu, lưu trong bộ nhớ riêng của Monika) |
| `repack/HealthCheck.kt` | Cách 1: tự kiểm tra game chạy được không (nhịp sống, bắt crash, xác nhận của user) |
| `loader/` (module riêng, build ra `assets/monika-loader.dex`) | Mã chạy **bên trong game**: nhận lệnh chép data, báo tiến độ, gửi nhịp sống + crash về Monika |
| `InstallChecklist.kt` | Lưu checklist Cách 1/2/3 theo từng game |
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
| `LOW_TARGET_SDK` | `SDK_INT >= 34` và `targetSdk < minInstallable` (34→23; ≥35→24) | Đi checklist 3 cách (§5) |
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
5. Nếu ghi bị từ chối (`EACCES`) → **[CHƯA KIỂM]** kiểm tra lại quyền "Cài ứng dụng không rõ nguồn gốc" đang bật; nếu bật mà vẫn lỗi thì chuyển sang Cách 3 (§6) cho bước chép.
6. Test tay bắt buộc: Android 11, 13, 14, 15 (mỗi bản 1 máy).

---

## 5. Giai đoạn 3 — Game cần Data / game cũ: 3 cách + checklist

**Android ≤ 10:** chép Data thẳng (xin `WRITE_EXTERNAL_STORAGE`, `maxSdkVersion="29"`), không cần 3 cách dưới. Game cũ không bị chặn cài trên Android ≤ 13.

**Android 11+ và game cần Data, hoặc Android 14+ và game `LOW_TARGET_SDK`:** đi checklist.

### 5.0 Checklist theo từng game (`InstallChecklist`)
- Lưu trong `GameMeta` (file `.monika.json` của game) hoặc prefs theo `packageName + versionCode`:
```kotlin
enum class Method { REPACK, SAF, ADB }            // Cách 1, 2, 3
enum class State { NOT_TRIED, RUNNING, OK, FAILED, NOT_AVAILABLE }
data class Attempt(val method: Method, val state: State, val reason: String?, val at: Long)
```
- UI (MonikaMenuSheet "Cài game Android cũ"), mỗi dòng 1 cách, emoji trạng thái (✅ được / ❌ không được + lý do ngắn / ⏳ đang thử / ⚪ chưa thử / 🚫 máy không hỗ trợ):
  - **Cách 1 — Monika tự chỉnh game (tự động)** — ghi chú: "Khuyên dùng. Không cần bật gì."
  - **Cách 2 — Cấp quyền thư mục** — chỉ hiện khi game cần Data; `NOT_AVAILABLE` nếu máy đã biết là chặn (5.2 bước 5) hoặc game chỉ bị chặn targetSdk (Cách 2 không cài được game cũ).
  - **Cách 3 — Gỡ lỗi không dây** — ghi chú: "Bật tạm trong lúc cài, xong Monika tự tắt."
- Luồng: tự chạy Cách 1. `OK` → xong. `FAILED` → sheet hiện checklist, nút "Thử Cách 2"/"Thử Cách 3" (ẩn cách `NOT_AVAILABLE`), kèm lý do Cách 1 hỏng.
- Chuyển cách phải **gỡ bản đã cài** trước nếu chữ ký khác (Cách 1 ký bằng khóa Monika; Cách 2/3 dùng APK gốc): hỏi user, `ACTION_DELETE` package, rồi tiếp tục.
- Có nút "Thử lại từ đầu" (xóa checklist). Checklist hiện trong trang chi tiết game ở Thư viện.
- Gửi kèm checklist vào báo lỗi (`Diagnostics`) để nhóm biết game nào cần cách nào; sau này có thể đưa gợi ý "game này dùng Cách 3" vào config/bài viết.

### 5.1 Cách 1 — Monika tự chỉnh game (`ApkRepacker` + `loader` + `HealthCheck`)

**a) Bộ nạp (module `loader`, Java thuần, không phụ thuộc AndroidX, build thành `assets/monika-loader.dex`):**
- `MonikaLoaderProvider` (ContentProvider, `exported=false`, `initOrder` cao): chạy khi tiến trình game khởi động →
  - gửi nhịp sống "started" về Monika;
  - cài `Thread.setDefaultUncaughtExceptionHandler` (gọi tiếp handler cũ) để gửi tóm tắt crash Java về Monika;
  - `registerActivityLifecycleCallbacks` → báo "đã hiện màn hình game" khi Activity đầu tiên `onResume`.
- `MonikaLoaderService` (`exported=true`) nhận lệnh chép data từ Monika:
  - kiểm tra người gọi (`Binder.getCallingUid` → tên gói `vn.aow.monika` / `com.aow.monika` + chữ ký trùng chữ ký Monika đã cài; sai → từ chối);
  - đọc file data qua `ContentResolver.openInputStream` từ provider của Monika (`content://<monika>.gamedata/<pkg>/...`, Monika cấp quyền đọc tạm bằng `grantUriPermission` cho gói game);
  - ghi vào `getExternalFilesDir(null).parentFile` = `Android/data/<pkg>/` (F9), chạy luồng nền, báo % về Monika;
  - xong → báo tổng số file + tổng byte để Monika đối chiếu.
- Kênh báo về Monika: gọi `ContentResolver.call()` tới provider `vn.aow.monika.loaderbus` của Monika (Monika kiểm tra gói gọi có trong danh sách game Monika đã chỉnh).
- Không có quyền mạng, không đọc gì ngoài data được Monika cấp.

**b) `ApkRepacker`:**
1. Đọc base.apk bằng ARSCLib (Apache-2.0): thêm `<provider>` + `<service>` của bộ nạp vào `AndroidManifest.xml` nhị phân (tên class có tiền tố `vn.aow.monika.loader.` để không trùng).
2. Thêm `monika-loader.dex` thành `classes<N+1>.dex` (N = số dex hiện có).
3. Game `LOW_TARGET_SDK`: nâng `targetSdkVersion` lên đúng mức tối thiểu (23 cho Android 14, 24 cho 15+) — **không nâng cao hơn**. Ghi rõ rủi ro trong lý do nếu hỏng (thư viện .so có "text relocation" bị từ chối khi targetSdk ≥ 23).
4. Xóa chữ ký cũ (`META-INF/*.SF|*.RSA|*.EC|*.DSA|MANIFEST.MF`), zipalign (4 byte; `.so` không nén căn 16 KB — giữ nguyên cách lưu gốc), ký v1+v2+v3 bằng apksig (Apache-2.0) với khóa `RepackKeyStore`.
5. Game nhiều split: ký lại **mọi** split cùng khóa; chỉ sửa manifest/dex ở base.
6. Làm trong WorkManager foreground, file tạm ở `cacheDir/apkinstall/`, xóa sau khi xong.
7. Đo và ghi vào báo cáo: kích thước Monika tăng thêm (ARSCLib + apksig + loader.dex).

**c) `RepackKeyStore`:** tạo cặp khóa RSA 2048 lần đầu (Android Keystore **không dùng được** cho apksig vì cần khóa xuất ra được → tạo bằng `KeyPairGenerator` thường, lưu PKCS12 trong `filesDir`, mật khẩu ngẫu nhiên lưu mã hóa bằng Android Keystore). Cảnh báo trong Cài đặt: gỡ Monika = mất khóa → cập nhật game đã chỉnh phải gỡ game trước. Sao lưu khóa: đưa vào SaveVault nếu có.

**d) `HealthCheck` — Monika tự kiểm tra game chạy được không:**
1. Sau khi cài + chép data xong, sheet "Monika sẽ mở thử game ~15 giây" → mở game.
2. Chấm điểm tự động trong 15 giây:
   - Không nhận "started" trong 10 giây → `FAILED` ("Game không khởi động").
   - Nhận crash Java → `FAILED` (kèm dòng lỗi đầu).
   - "started" rồi mất nhịp sống trước khi có "đã hiện màn hình" → `FAILED` ("Game thoát ngay" — thường do kiểm tra chữ ký hoặc lỗi .so).
   - Có "đã hiện màn hình" và tiến trình còn sống ≥ 8 giây → **nghi là được**.
3. Khi user quay lại Monika: hỏi 1 chạm "Game có vào được màn hình chơi không?" [Được] / [Không]. Được → `OK`. Không → `FAILED` ("User báo không vào được").
4. Data: đối chiếu số file + tổng byte bộ nạp báo với nguồn; lệch → `FAILED` ("Chép data thiếu").
5. **[CHƯA KIỂM]** độ tin cậy nhịp sống trên máy tiết kiệm pin gắt (Xiaomi/Oppo); ghi kết quả thử.

### 5.2 Cách 2 — Cấp quyền thư mục (`SafDataAccess`, kiểu ZArchiver)
1. Đảm bảo thư mục `Android/data/<pkg>` tồn tại: hướng dẫn user **mở game 1 lần rồi thoát** (Monika mở giúp bằng `getLaunchIntentForPackage`, chờ user quay lại) — Android tạo thư mục khi game khởi động.
2. Tạo URI gợi ý:
   `DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Android/data/<pkg>")`
   và mở `ACTION_OPEN_DOCUMENT_TREE` với `EXTRA_INITIAL_URI` = URI đó. Sheet hướng dẫn trước: "Bấm **Dùng thư mục này** → **Cho phép**".
3. Nhận tree URI → kiểm tra đúng `primary:Android/data/<pkg>` (sai thư mục → báo và cho chọn lại) → `takePersistableUriPermission(READ|WRITE)`, lưu URI theo package vào prefs để lần sau không phải xin lại.
4. Chép cây thư mục bằng `DocumentFile` (tạo thư mục con, ghi file, % tiến độ).
5. Phát hiện bị chặn: nút "Dùng thư mục này" bị mờ → user bấm Hủy/quay lại, hoặc URI trả về không đúng thư mục. Khi đó checklist ghi Cách 2 = `FAILED`, ghi nhớ "máy này chặn" (lần sau Cách 2 = `NOT_AVAILABLE`), gợi ý Cách 3.
- Cách 2 cài **APK gốc** (không chỉnh), nên chỉ dùng được khi game không bị chặn targetSdk.
6. **[CHƯA KIỂM]** trên máy có bản vá ≥ 03/2024 (dự kiến bị chặn theo F6). Ghi kết quả thử vào `docs/`.

---

## 6. Giai đoạn 4 — Cách 3: Gỡ lỗi không dây

Dùng khi Cách 1 (và Cách 2 nếu có) không được. Cài **APK gốc** (không chỉnh) với cờ bỏ chặn targetSdk nếu cần (F1, F2) + chép Data bằng quyền shell.

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
- Data: push vào `/sdcard/Android/data/<pkg>/…` (shell ghi được), hoặc `cp -r` từ thư mục tạm Monika đã chép ra `/sdcard/Download/.monika-tmp/` rồi xóa tạm.
- Mọi lệnh: timeout, đọc exit code + stdout, lỗi → thông báo tiếng Việt + ghi log vào `Diagnostics`.
- Dọn `/data/local/tmp/monika/` sau khi xong.

### 6.4 Sau khi cài game cũ
- Android có thể hiện hộp thoại "Ứng dụng này được tạo cho phiên bản Android cũ" khi mở game lần đầu → sheet hướng dẫn trước: bấm OK là chạy.
- Game targetSdk < 23 được cấp quyền kiểu cũ; Android 10+ có màn "xem lại quyền" khi mở lần đầu → hướng dẫn tương tự.

---

## 7. `ApkInstallFlow` — trình tự tổng

```
inspect → Problem chặn (32-bit, minSdk, dung lượng)? báo & dừng
        → cần Data hoặc LOW_TARGET_SDK?
             không → SessionInstaller (APK gốc) → OBB → xong
             có    → Android ≤ 10: cài gốc + chép Data thẳng → xong
                     Android 11+: checklist §5
                        Cách 1: ApkRepacker → SessionInstaller → OBB → bộ nạp chép Data → HealthCheck
                        FAILED → sheet checklist → user chọn Cách 2 / Cách 3
        → xong: thông báo "Đã cài <tên>" + nút Chơi
        → dọn file tạm; hỏi "Xóa file cài để giải phóng X GB?"
```
- OBB luôn chép bằng `ObbInstaller` (F4), cách nào cũng vậy; lỗi quyền → làm trong Cách 3.
- Trạng thái từng bước ghi vào `GameTasks` → thẻ game trong Thư viện hiện "Đang cài… 45%".
- Thư viện: game đã cài hiện nút **Chơi** (so `packageName`), kèm biểu tượng nhỏ nếu game đang dùng bản Monika chỉnh (Cách 1).

---

## 8. Test

**Unit (Robolectric, chạy trong CI):**
- `ApkInspectorTest`: file mẫu nhỏ tự tạo trong `src/test/resources/apkinstall/` — APK đơn, APKS (base + 3 split), XAPK (manifest.json + obb), APKM giả; thư mục bố cục `Android/obb`, `obb/<pkg>`, `data/<pkg>`.
- `SplitSelectorTest`: các tổ hợp ABI/mật độ.
- `ProblemsTest`: 32-bit trên máy 64-bit-only (giả lập `SUPPORTED_32_BIT_ABIS` rỗng), targetSdk thấp theo từng SDK_INT, minSdk cao.
- `ObbNameTest`: đổi tên OBB theo quy ước.
- `ApkRepackerTest`: APK mẫu (tự build 1 app test nhỏ trong `src/test/resources`) → sau khi chỉnh: manifest có provider/service bộ nạp, có `classesN.dex` mới, targetSdk đúng mức tối thiểu, chữ ký hợp lệ (dùng `ApkVerifier` của apksig), split ký cùng khóa.
- `InstallChecklistTest`: chuyển trạng thái, lưu/đọc lại, ẩn Cách 2 khi không áp dụng.
- `HealthCheckTest`: các kịch bản nhịp sống (không start, crash, thoát sớm, chạy ổn) → kết quả đúng.
- Test instrumented (ghi chú, chạy tay trên máy): cài APK đã chỉnh, bộ nạp chép data 500 MB, nhận % và tổng byte khớp.

**Thử tay (bắt buộc trước khi phát hành, ghi kết quả vào `docs/apkinstall-test.md`):**
| Ca | Android 11 | 13 | 14 | 15/16 |
|---|---|---|---|---|
| APK thường | | | | |
| APKS / XAPK | | | | |
| OBB | | | | |
| Cách 1 – game cần Data | | | | |
| Cách 1 – game targetSdk thấp | — | — | | |
| HealthCheck bắt được game crash / thoát ngay | | | | |
| Cách 2 – Data qua quyền thư mục | | | | |
| Cách 3 – Data + game cũ qua Gỡ lỗi (tự tắt sau cùng) | | | | |
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
| 4 | Module `loader` + `ApkRepacker` + `RepackKeyStore` | `ApkRepackerTest` xanh; APK đã chỉnh cài được trên máy thật |
| 5 | Bộ nạp chép Data + `HealthCheck` + `InstallChecklist` + sheet checklist | Game cần Data chạy được qua Cách 1; game kiểm tra chữ ký bị HealthCheck đánh `FAILED` và hiện Cách 2/3 |
| 6 | `SafDataAccess` (Cách 2) | Chạy trên máy chưa vá; máy đã vá tự đánh `NOT_AVAILABLE` |
| 7 | `LocalAdb` + UI ghép đôi + tự tắt (Cách 3) | Cài được game gốc targetSdk thấp trên Android 14+; Gỡ lỗi không dây tự tắt sau khi xong |
| 8 | Thư viện hiện Chơi/Cài, checklist trong trang game, dọn file, `GameTasks` | Từ bài viết → tải → cài → chơi không cần thao tác file tay |
| 9 | Tăng version, commit, chạy release | APK phát hành, gửi sếp thử |

Mỗi bước: build + `testDebugUnitTest` xanh rồi mới commit; commit message tiếng Việt, kết thúc bằng 2 dòng attribution theo `CLAUDE.md`.

---

## 10. Quy tắc cho người thi công

- Không đoán: điều gì chưa thử trên máy thật thì ghi **[CHƯA KIỂM]** trong báo cáo.
- Không thêm quyền Trợ năng, không dùng root/Shizuku.
- Chỉ sửa APK game theo đúng Cách 1 (thêm bộ nạp + nâng targetSdk tối thiểu). Không đụng mã game, không gỡ quảng cáo/bản quyền, không đổi tên gói.
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
- ARSCLib (Apache-2.0) — https://github.com/REAndroid/ARSCLib
- apksig (Apache-2.0) — https://android.googlesource.com/platform/tools/apksig/
- Samsung: Sunsetting 32-bit app support — https://docs.samsungknox.com/dev/knox-sdk/kbas/kba-1150-sunsetting-32-bit-app-support-on-samsung-devices/
