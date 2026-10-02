# Phương án câu 5 — Nhúng sâu Ren'Py và RPG Maker XP/VX/Ace (native, không mở app ngoài)

> Người viết: Opus (cố vấn ngoài). Ngày 03/10/2026 (GMT+7). Đọc trên `main` @ `c288f3a`, app 0.7.2, `configVersion` 30.
> Người thi công: Claude Code (Sonnet). Người quyết: sếp.
> **Chưa có dòng nào trong tài liệu này được chạy trên máy thật.** Mọi dữ kiện "đã đọc" là đọc mã nguồn, có ghi file và dòng. Chỗ **[CHƯA KIỂM]** phải kiểm trước khi dựa vào.
> Tài liệu này **thay thế** hai file `PLAN-rpgmaker-monika.md` và `PLAN-A-rgss-android.md` Opus gửi trước khi được đọc repo (hai file đó giả định sai về kiến trúc Monika).

Quyết định của sếp đã chốt:

| # | Quyết định |
|---|---|
| Q1 | Nhúng sâu, native, **không mở app ngoài** |
| Q2 | RPG Maker XP/VX/Ace ưu tiên native để đạt hiệu suất; core libretro chỉ là dự phòng; viết lại vỏ Android của bản port |
| Q3 | **Làm RPG Maker trước, Ren'Py sau** (03/10/2026) |
| Q4 | Luật "không mở app ngoài" **áp cho mọi hệ**, gồm cả Kirikiri trên máy 32-bit và Symbian (03/10/2026). Tắt app ngoài **theo từng hệ**, khi hệ đó đã có engine nhúng |
| Q6 | **Symbian vẫn phải nhúng, nhưng làm sau.** Sonnet nhắc sếp theo mục 8 (03/10/2026) |
| Q5 | Game thử: Sonnet **hỏi sếp khi tới bước cần**, không tự tải game (03/10/2026) |

---

## 1. Kết luận

**Theo đúng mô hình Kirikiri cho cả hai engine** (Java nhỏ trong APK + gói native tải thêm qua `PackManager` + Activity ở `runner/`, tiến trình `:game`), với hai khác biệt về cách có được phần native: **RGSS tự dựng mkxp-z từ nguồn trên CI; Ren'Py không tự dựng mà đóng gói lại nhị phân chính thức từ RAPT**, tách hai gói `renpy8` (Python 3) và `renpy7` (Python 2).

---

## 2. Lý do và đánh đổi

### 2.1 Vì sao là mô hình Kirikiri

| Tiêu chí | Mô hình Kirikiri (chọn) | Runner `web` (như Onsyuri) | Core libretro qua `:libretrodroid` |
|---|---|---|---|
| Hợp luật "app nhẹ" | Có: APK chỉ thêm Java | Có | Có |
| Native, hiệu suất | Có | Không (wasm trong WebView) | RGSS: Ruby chạy trong sandbox WebAssembly; Ren'Py: không có core |
| Hạ tầng sẵn có trong repo | `SimpleModule`, `PackManager`, `KirikiriPrepActivity`, `ComposeHost`, `Diagnostics`, workflow `build-kirikiri.yml` làm mẫu | `WebGameActivity` | `RetroActivity`, `CoreManager` |
| Rủi ro chính | Dựng native, xung đột lớp SDL | Bộ nhớ và tốc độ trên máy yếu **[CHƯA KIỂM]** | Core mkxp-z **không hỗ trợ Win32API** (đã đọc `binding-sandbox/binding-sandbox.cpp` 641–670), LibretroDroid cần 3 bản vá |

Hai đường không chọn vẫn giữ làm dự phòng, ghi ở mục 6.

### 2.2 Vì sao Ren'Py dùng nhị phân chính thức thay vì tự dựng

| | Tự dựng bằng `renpy-build` | Đóng gói lại RAPT chính thức (chọn) |
|---|---|---|
| Yêu cầu | Ubuntu 24.04, quyền sudo, máy ảo ≥ 64 GB đĩa, tải tay một số file bên thứ ba (đã đọc `README.rst` của `renpy/renpy-build`) | Tải 1–2 file zip có sẵn checksum |
| Có sẵn ABI | arm64-v8a, armeabi-v7a, x86_64 | Như bên, đã dựng sẵn tại `prototype/renpyandroid/src/main/jniLibs/<abi>/librenpython.so` (đã đọc `renpybuild/context.py` dòng 127) |
| Sửa được mã native | Có | Không |
| Công sức | Lớn | Nhỏ |

Ren'Py không cần sửa native: bản Android chính thức đã nhận thư mục game và thư mục save qua tham số và biến môi trường (mục 4.3). Kirikiri trong repo cũng đã dùng lại thư viện dựng sẵn (`build-kirikiri.yml` bước "Thư viện dựng sẵn (thirdparty) + assets từ APK 1.3.9"), nên đây không phải tiền lệ mới.

### 2.3 Vì sao hai gói Ren'Py

| Nhánh | Bản mới nhất | Python | SDL | Nguồn |
|---|---|---|---|---|
| Ren'Py 8 | 8.5.3 (15/05/2026) | 3.12.8 | SDL2 2.0.20 (có vá) | nhánh `fix` của `renpy/renpy-build`, `tasks/python3.py`, `tasks/sdl2.py` |
| Ren'Py 7 | 7.8.7 (17/03/2025), bản cuối của dòng 7 **[CHƯA KIỂM]** | 2.7.18 | SDL2 2.0.20 (có vá) | nhánh `fix-7` |

Game Ren'Py mang mã Python riêng của nó. Mã viết cho Python 2 không chạy trên Python 3, nên một runtime không phủ được cả hai dòng. Làm `renpy8` trước; `renpy7` là bước sau, cùng mã Java.

Ghi chú: nhánh `master` của `renpy-build` đã chuyển sang SDL3 3.4.8. Không dùng `master`; ghim theo bản phát hành 8.5.3.

### 2.4 Dung lượng

| Hạng mục | Số đã có | Ghi chú |
|---|---|---|
| APK Monika tăng thêm | **[CHƯA KIỂM]** | Chỉ thêm Java: lớp SDL (2 bộ), `org.renpy.android.*`, `org.jnius.*`. Đo sau bước E0/P2/R2, ghi vào mục 4 của tài liệu bàn giao |
| Gói `renpy8` mỗi ABI | **[CHƯA KIỂM]** | Chỉ biết `renpy-8.5.3-rapt.zip` = 68.554.524 byte cho cả 3 ABI + project mẫu |
| Gói `renpy7` mỗi ABI | **[CHƯA KIỂM]** | `renpy-7.8.7-rapt.zip` = 58.159.071 byte |
| Gói `rgss` mỗi ABI | **[CHƯA KIỂM]** | Tham chiếu: gói Kirikiri arm64 = 17.163.785 byte |

Nếu gói > 15 MB thì `PackPolicy` tự hỏi "Tải luôn bằng 4G / Đợi Wi-Fi" như hiện tại, không cần mã mới.

### 2.5 Rủi ro

| # | Rủi ro | Mức | Xử lý |
|---|---|---|---|
| X1 | **Hai bộ lớp `org.libsdl.app.*` khác phiên bản trong một APK.** Ren'Py dùng SDL2 2.0.20, bản port mkxp-z dùng SDL2 2.26.3 (`app/jni/get_deps.sh`). Lớp Java của SDL phải khớp phiên bản với phần native | Chặn | Ren'Py giữ nguyên `org.libsdl.app`. RGSS **đổi gói** lớp SDL sang `vn.aow.monika.rgss.sdl` và vá chuỗi tên lớp trong `src/core/android/SDL_android.c` khi dựng (bước R1) **[CHƯA KIỂM]** |
| X2 | Bản port mkxp-z chỉ cam kết RPG Maker XP (README `BookerRues9/mkxp-z-android-reworked`) | Cao | Cổng quyết định R6 bằng game thật. Không đạt cho VX/Ace thì dùng dự phòng ở mục 6 riêng cho VX/Ace |
| X3 | Nạp `.so` ngoài APK với SDL (`getMainSharedObject`, `loadLibraries`) | Cao | Ghi đè hai hàm này trong Activity (mẫu: `KirikiriGameActivity.onLoadNativeLibraries`). Kiểm bằng Emulator Test trước khi làm tiếp |
| X4 | Ren'Py: lớp `PythonSDLActivity` phụ thuộc Play Asset Delivery (`com.google.android.play:asset-delivery`) | Trung bình | Vá bỏ, theo cách `kirikiri/patches/` |
| X5 | Game Ren'Py chỉ có `.rpyc` biên dịch bởi bản khác: bị bỏ qua nếu `script_version` khác (`renpy/script.py` dòng ~928) | Trung bình | **[CHƯA KIỂM]** tần suất. Chẩn đoán đọc `traceback.txt`/`log.txt` rồi báo rõ |
| X6 | Game RGSS gọi Win32API | Trung bình | mkxp-z native có MiniFFI (`-DMKXPZ_MINIFFI`). Không chạy thì báo rõ tên lời gọi |
| X7 | Python / Ruby không khởi tạo lại được trong cùng tiến trình | Trung bình | Activity kết thúc tiến trình khi thoát, như `KR2Activity` (`System.exit(0)`) |
| X8 | R8 xóa hoặc đổi tên thành viên được native gọi qua JNI | Trung bình | Thêm `-keep` vào `app/proguard-rules.pro`, kiểm bằng `dexdump` (luật trong `CLAUDE.md`) |
| X9 | Nghĩa vụ giấy phép: mkxp-z GPL-2.0-or-later; Ren'Py MIT + phần LGPL (`sphinx/source/license.rst`) | Pháp lý | Mô tả Release ở `aowvn-monika-packs` ghi repo + commit nguồn (luật đã có trong `plan-engine-moi.md`). Việc công khai mã Monika vẫn là mục ⛔ sếp đã ghi nhận. Đây không phải tư vấn pháp lý |
| X10 | RTP của XP/VX/Ace có bản quyền riêng | Pháp lý | Không đóng gói. Người chơi tự thêm |

### 2.6 Công sức (tương đối)

| Khối | Công | Rủi ro |
|---|---|---|
| E0 Hạ tầng chung | Nhỏ | Thấp |
| R RGSS | Lớn | Cao |
| P Ren'Py 8 | Vừa | Trung bình |
| P7 Ren'Py 7 | Nhỏ (sau khi có P) | Trung bình |

**Thứ tự đã chốt (Q3): E0 → R (RGSS) → P (Ren'Py 8) → P7 (Ren'Py 7).**

Hệ quả của việc RGSS đi trước: bước R3 là nơi đầu tiên kiểm đường "SDL + nạp `.so` từ gói + tiến trình `:game`" (U4), nên R3 phải qua Emulator Test trước khi làm R4 trở đi. Việc đổi gói lớp SDL của RGSS (X1) vẫn phải làm **ngay từ R1** dù Ren'Py chưa có, vì Ren'Py dùng nhị phân dựng sẵn nên không đổi tên lớp được.

Không cần tách `:theme` / `:core` (câu 1) trước: Activity của engine nằm trong `app/runner/` như `KirikiriGameActivity`, module mới chỉ chứa Java của upstream.

---

## 3. Dữ kiện đã đọc (để Sonnet không phải dò lại)

### 3.1 Trong repo Monika

| Dữ kiện | Nguồn |
|---|---|
| Hệ `renpy` và `rgss` đang là `runner: external`, `externalApp: joiplay`, chưa có `engine` | `config/monika-config.json` → `systems` |
| Nhận diện đã có: `renpy` (`renpy/__init__.py`, `renpy/__init__.pyo`, `game/script.rpyc`, `game/archive.rpa`), `rgss` (`Game.rgssad`, `Game.rgss2a`, `Game.rgss3a`, `Data/Scripts.rxdata`, `.rvdata`, `.rvdata2`); `entry` đang rỗng | `config` → `engines` |
| Kirikiri được nhận trong nhánh `"external"` của `GameLauncher.launch` bằng điều kiện `system.engine == KIRIKIRI && packs.supported(...)` | `runner/GameLauncher.kt` |
| Gói mới = thêm 1 dòng vào `PackManager.installers` + khai báo `modules.<id>` | `pack/PackManager.kt` |
| `SimpleModule.ensure(id, mainFile)`: tải → SHA-256 → giải nén giữ cây thư mục vào `filesDir/packs/<id>/` → ghi `version`. Hỗ trợ `{abi}`, `sha256ByAbi`, `sizeByAbi` | `pack/SimpleModule.kt`, `config/MonikaConfig.kt` (`ModuleDef`) |
| Nạp nhiều `.so` có phụ thuộc lẫn nhau: `AzaharModule` thử nạp lặp danh sách `pending` rồi nạp file chính | `azahar/AzaharModule.kt` dòng 103–107 |
| Lớp phủ Compose lên Activity không phải Compose: `ComposeHost` | `runner/KirikiriOverlay.kt` dòng 31 |
| Vòng đời chẩn đoán: `Diagnostics.begin / stage / heartbeat / end`, `recordHandled`, `crumb` | `diag/Diagnostics.kt` |
| Emulator Test của Kirikiri: đẩy gói thật vào `files/packs/kirikiri`, mở Activity, đợi dòng log `MonikaGame: kirikiri-lib-loaded` | `scripts/ci-emulator-games.sh` dòng 55–77 |
| `minSdk 26`, `targetSdk 35`, `compileSdk 35`, NDK app 22.1.7171670, ABI `arm64-v8a` + `armeabi-v7a` | `app/build.gradle.kts` |
| Chưa có lớp `org.libsdl.*` nào trong repo | grep toàn repo |

### 3.2 Ren'Py Android (nhánh `fix`, commit `735be92`)

| Dữ kiện | Nguồn |
|---|---|
| Chỉ một thư viện native: `getLibraries()` trả `{"renpython"}`; mọi thứ (Python, SDL2, ffmpeg…) liên kết tĩnh vào `librenpython.so` | `PythonSDLActivity.java` dòng 75; `tasks/renpython.py` `link_android` |
| Native gọi ngược `activity.preparePython()` qua `GetObjectClass` + `GetMethodID` (ghi đè được ở lớp con) | `runtime/librenpython3_android.c` `call_prepare_python` |
| Native export hàm JNI `PythonSDLActivity_nativeSetEnv` gắn với tên lớp `org.renpy.android.PythonSDLActivity`; module Python `android` gọi `autoclass('org.renpy.android.PythonSDLActivity').mActivity` | `librenpython3_android.c`; `runtime/android/__init__.py` |
| Khởi động: `chdir($ANDROID_PRIVATE)`, Python home = `$ANDROID_PRIVATE`, chạy `$ANDROID_PRIVATE/main.py` với `argv = {python, main.py}` cố định | `librenpython3_android.c` `start_python` |
| Java đặt 4 biến trước khi chạy: `ANDROID_PRIVATE`, `ANDROID_PUBLIC`, `ANDROID_OLD_PUBLIC`, `ANDROID_APK` | `PythonSDLActivity.preparePython` dòng 248–266 |
| `main.py` là liên kết tới `renpy.py`. `bootstrap` nhận tham số vị trí `basedir` và tùy chọn `--savedir` | `main.py`; `renpy/arguments.py` dòng 79–106; `renpy/bootstrap.py` dòng 265–268 |
| Trước khi đọc tham số, `bootstrap` **thực thi file `<renpy_base>/environment.txt`** như mã Python | `renpy/bootstrap.py` dòng 226–233 |
| Thư mục `renpy/common` trên hệ file được đưa vào đường tìm kiếm nếu tồn tại | `renpy.py` `predefined_searchpath`, đoạn `if commondir and os.path.isdir(commondir)` |
| Khi `renpy_base != basedir`, loader không tìm game trong APK | `renpy/loader.py` dòng 86–89 |
| Log trên Android ghi vào `$ANDROID_PUBLIC` | `renpy.py` `path_to_logdir` |
| Module Gradle gốc: `compileSdk android-36`, `minSdk 21`, phụ thuộc `asset-delivery`, `androidx.activity`, `slf4j` | `rapt/prototype/renpyandroid/build.gradle` |
| Game thử có sẵn trong repo Ren'Py: `the_question/` | `renpy/renpy` |
| File phát hành: `https://www.renpy.org/dl/8.5.3/renpy-8.5.3-rapt.zip`, `renpy-8.5.3-sdk.zip` (163.008.121 byte), kèm file `.sums` | danh mục thư mục tải của renpy.org |

### 3.3 Bản port mkxp-z (`BookerRues9/mkxp-z-android-reworked`, 25/05/2026)

| Dữ kiện | Nguồn |
|---|---|
| Lõi mkxp-z 2.4, binding MRI, GLES2, MiniFFI | `app/jni/mkxp-z.mk` |
| Ruby 3.1.0 nhánh `mkxp-z-3.1` của `mkxp-z/ruby`; OpenAL Soft 1.23.0; PhysFS 3.2.0; SDL2 2.26.3 + SDL2_image 2.6.3 + SDL2_ttf 2.20.2 + SDL2_sound 2.0.1; OpenSSL 1.1.1t | `app/jni/get_deps.sh` |
| Thư viện động hiện tại: `SDL2 SDL2_ttf SDL2_image SDL2_sound openal ruby` + `c++_shared` | `mkxp-z.mk` dòng 112; `Application.mk` |
| **Đang build không tối ưu:** `CFLAGS := -O0` cho Ruby/OpenAL/pixman/OpenSSL; `APP_OPTIM := debug` | `app/jni/Makefile`; `Application.mk` |
| NDK 23.2.8568313, API 23, có arm64-v8a và armeabi-v7a | `README.md`, `Makefile` |
| Hợp đồng JNI với Activity (lõi dùng `GetObjectClass(activity)` nên đổi tên lớp được): trường tĩnh `String GAME_PATH`; hàm tĩnh `getSystemLanguage()`, `hasVibrator()`, `vibrate(int)`, `vibrateStop()`, `inMultiWindow(Activity)`; `getArguments()` trả `{"debug"}` hoặc `{}` | `src/main.cpp` 257–266; `src/system/systemImpl.cpp` 34–38; `binding/android-binding.cpp` 84–141; `MainActivity.java` 231 |
| Phím ảo gửi qua `SDLActivity.onNativeKeyDown / onNativeKeyUp` | `MainActivity.java` 139–140 |
| Khóa `mkxp.json` có sẵn: `rgssVersion`, `gameFolder`, `RTP`, `fontSub`, `solidFonts`, `preloadScript`, `pathCache`, `printFPS`, `fixedFramerate`, `frameSkip`, `vsync`, `syncToRefreshrate`, `smoothScaling`, `fixedAspectRatio`… | `app/jni/mkxp-z/mkxp.json` |
| Vỏ Java (`com.hatkid.mkxpz.*`, ~1.100 dòng) và file build (`Makefile`, `*.mk`, `get_deps.sh`) **không có giấy phép** | repo không có file license ngoài `mkxp-z/COPYING` |
| Upstream mkxp-z `dev` đã chuyển sang SDL3; nâng lõi là việc riêng, không làm ở bản đầu | `mkxp-z/mkxp-z` `src/meson.build` dòng 33 |

---

## 4. Các bước thi công

Quy ước: mỗi bước một PR vào nhánh riêng, `./gradlew testDebugUnitTest` xanh trước khi mở PR. Mọi thay đổi config tăng `configVersion`. Sau mỗi khối, cập nhật mục 4 của `docs/GIAO-TIEP-VOI-OPUS.md` và chạy lại `scripts/gen-architecture.py`.

### Khối E0 — Hạ tầng chung cho "engine SDL nhúng"

| Bước | Việc | File / package | Cách kiểm |
|---|---|---|---|
| E0.1 | Tổng quát `KirikiriPrepActivity` thành `EnginePrepActivity`: tham số gồm id gói, file chính, tên hiển thị, lớp Activity đích, và hàm kiểm quyền đọc. `KirikiriPrepActivity` thành lớp mỏng gọi sang, **không đổi hành vi** | `runner/EnginePrepActivity.kt` (mới), `runner/KirikiriPrepActivity.kt`, `AndroidManifest.xml` | Test giao diện hiện có của màn chuẩn bị Kirikiri vẫn xanh; ảnh chụp trong `app/build/screenshots/` không đổi |
| E0.2 | Bảng định tuyến engine: thay điều kiện viết cứng cho Kirikiri bằng tra theo `system.engine` trong một `Map<String, EngineRoute>` (id gói, file chính, hàm mở). Đăng ký `kirikiri` vào bảng | `runner/GameLauncher.kt`, `AppGraph.kt` (tạo bảng ở đây, đúng luật DI thủ công) | `ConfigTest` + test mới: hệ có `engine` đã đăng ký và gói `supported` → đi đường nhúng |
| E0.3 | Luật "không mở app ngoài" (Q4), làm theo config-first và **theo từng hệ**: thêm trường `allowExternalApp: Boolean = true` vào `SystemDef` (mặc định `true` để đọc được config cũ). Khi một hệ có `allowExternalApp: false`: `GameLauncher` **không bao giờ** gọi `launchExternal` cho hệ đó. Có `engine` đã đăng ký và gói hỗ trợ ABI máy → chạy nhúng. Còn lại → `LaunchResult.Failed` với câu tiếng Việt nói rõ lý do (vd. "Máy 32-bit chưa chạy được Kirikiri trong Monika"). Thư viện và Cài đặt ẩn lời mời cài app ngoài của hệ đó. **Lịch tắt:** `rgss` tắt khi gói `rgss` phát hành (R5); `renpy` tắt khi gói `renpy8` phát hành (P4); `kirikiri` tắt cùng lúc với `rgss` (sếp đã chốt, máy 32-bit sẽ nhận thông báo chưa hỗ trợ); **`symbian` giữ `true`** cho tới khi làm xong việc ở mục 8 | `config/MonikaConfig.kt`, `runner/GameLauncher.kt`, `ui/screens/` (chỗ hiện `NeedApp` / `OpenedApp`), `config/monika-config.json` | Unit test: `true` → hành vi cũ; `false` → không có Intent mở app ngoài cho hệ đó; `ConfigTest` đọc được config thiếu trường này |
| E0.4 | Giữ tương thích app cũ: trong config **không đổi** `runner: "external"` và `externalApp: "joiplay"` của `renpy`/`rgss`; chỉ **thêm** `engine`. App 0.7.2 trở về trước không biết engine mới và không biết trường `allowExternalApp` nên vẫn chạy như cũ | `config/monika-config.json` (làm ở P4, R5) | `ConfigTest`: config mới vẫn đọc được bằng mô hình cũ (trường mới có mặc định) |

### Khối R — RPG Maker XP/VX/Ace (mkxp-z native)

| Bước | Việc | File / package | Cách kiểm |
|---|---|---|---|
| R0 | **Spike ngoài repo:** dựng nguyên trạng `BookerRues9/mkxp-z-android-reworked` cho arm64 theo README của nó. Sửa đúng hai chỗ: `CFLAGS -O0` → `-O2`; `APP_OPTIM debug` → `release`. Ghi kích thước từng `.so` | máy CI hoặc máy phiên; kết quả ghi `docs/opus/ket-qua/R0.md` | Có APK thử; có bảng kích thước `.so`. Dừng nếu 5 lần dựng hỏng với 5 nguyên nhân khác nhau → sang mục 6 |
| R1 | Workflow `build-rgss.yml` + thư mục `engines/rgss/`: script dựng **do Monika viết lại** (không chép `Makefile`/`*.mk` của bản port, vì không có giấy phép). Ghim tag/commit + checksum từng phụ thuộc. Mặc định `-O2`, release, cắt ký hiệu. Dựng `arm64-v8a` và `armeabi-v7a`. **Đổi gói lớp SDL**: vá chuỗi `org/libsdl/app` (và macro tiền tố JNI) trong `SDL_android.c` thành `vn/aow/monika/rgss/sdl` **[CHƯA KIỂM]**. Ưu tiên liên kết tĩnh để còn ít `.so` nhất; nếu vẫn nhiều file thì ghi thứ tự nạp trong `manifest.json`. Kiểm game có cần mạng không: không cần thì bỏ `-DMKXPZ_SSL` và bỏ OpenSSL (bản 1.1.1t đã hết hỗ trợ). Lõi mkxp-z 2.4 + 10 file vá Android lấy từ bản port (là sửa đổi trên mã GPL), ghi `engines/rgss/UPSTREAM.md` | `.github/workflows/build-rgss.yml`, `engines/rgss/` | Máy sạch chạy một lệnh ra `rgss-{abi}.zip` + `.sha256`; không có file build nào chép từ bản port trong repo |
| R2 | Module `:rgss`: chỉ chứa lớp SDL 2.26.3 **lấy từ bản phát hành SDL** (`android-project/app/src/main/java/org/libsdl/app/`), đã đổi gói sang `vn.aow.monika.rgss.sdl` bằng script (ghi trong `UPSTREAM.md`). So với lớp SDL trong bản port; nếu bản port có sửa thì ghi từng chỗ và tự áp lại | `rgss/` (module mới), `settings.gradle.kts`, `app/build.gradle.kts` | `assembleRelease` xanh; `dexdump` thấy cả `org.libsdl.app.SDLActivity` (Ren'Py) và `vn.aow.monika.rgss.sdl.SDLActivity`; đo APK tăng |
| R3 | `RgssGameActivity : vn.aow.monika.rgss.sdl.SDLActivity`, tiến trình `:game`. **Viết mới hoàn toàn**, không chép `com.hatkid.mkxpz`. Phải có đủ hợp đồng JNI ở mục 3.3: `@JvmField` tĩnh `GAME_PATH`, các hàm `@JvmStatic` `getSystemLanguage`, `hasVibrator`, `vibrate`, `vibrateStop`, `inMultiWindow`. Ghi đè nạp thư viện từ gói. Gán `GAME_PATH` từ Intent trước `super.onCreate`. Kết thúc tiến trình khi thoát. Manifest: `sensorLandscape`, `configChanges` như `KirikiriGameActivity` | `runner/RgssGameActivity.kt`, `AndroidManifest.xml`, `app/proguard-rules.pro` | Emulator Test (R6); `dexdump` thấy các thành viên JNI còn nguyên tên sau R8 |
| R4 | `MkxpConfigWriter`: sinh `mkxp.json` trong thư mục game. `rgssVersion` theo dấu hiệu nhận diện (`.rgssad`/`.rxdata` → 1, `.rgss2a`/`.rvdata` → 2, `.rgss3a`/`.rvdata2` → 3), `RTP` trỏ thư mục người chơi đã thêm, `fontSub`/phông có dấu tiếng Việt (đóng trong gói, không trong APK), các khóa hiệu năng chốt ở R0. Gộp với `mkxp.json` có sẵn của game, giữ khóa lạ. Tên khóa và kiểu giá trị đọc từ `mkxp.json` mẫu, không tự đoán | `runner/MkxpConfigWriter.kt` + test | Unit test: đúng theo từng engine; gộp không làm mất khóa lạ |
| R5 | Lớp phủ và phím: dùng lại bố cục `GamePadOverlay` (`pad: "rpg"` đã có trong `SystemDef`), nối phím sang `SDLActivity.onNativeKeyDown/Up`; menu Monika qua `ComposeHost`. Đăng ký `PackManager.installers` (`rgss`), `EngineRoute`, config `modules.rgss` + `systems[rgss].engine = "rgss"` + `entry` cho luật nhận diện; tăng `configVersion` | `runner/RgssOverlay.kt`, `runner/GamePadOverlay.kt` (chỉ nếu cần mở API), `pack/PackManager.kt`, `AppGraph.kt`, `config/…` | Test giao diện (Robolectric) cho lớp phủ; `ConfigTest` |
| R6 | **Cổng quyết định bằng game thật.** Sếp cấp 5 game: G1 XP, G2 VX, G3 VX Ace có `.rgss3a`, G4 dựng trên Pokémon Essentials, G5 việt hóa có dấu. Đo trên máy thật (sếp) hoặc ghi rõ "chỉ máy ảo": khởi động, chơi được, save/load sau khi tắt hẳn app, FPS so với tốc độ gốc, âm thanh, lỗi Win32API, chữ có dấu | `docs/TEST-MAY-THAT.md` thêm mục RGSS; Emulator Test thêm ca nạp gói + đợi `MonikaGame: rgss-lib-loaded` | G1, G2, G3, G5 đạt → đi tiếp R7. Chỉ G1 đạt → báo sếp: dùng dự phòng mục 6 cho VX/Ace. Từ 2 game trở lên không khởi động → chuyển hẳn dự phòng |
| R7 | RTP, save, chẩn đoán: màn hướng dẫn tự thêm RTP (đọc `Game.ini` để biết game cần RTP nào); save nối `SaveVault` (kiểm save nằm trong thư mục game hay nơi khác **[CHƯA KIỂM]**); khi `:game` chết, đính log cuối với nhãn `engine:rgss`, dịch các lỗi thường gặp sang tiếng Việt | `runner/`, `library/`, `diag/`, `ui/screens/` | Mỗi lỗi có một tình huống tái hiện |

### Khối P — Ren'Py 8

| Bước | Việc | File / package | Cách kiểm |
|---|---|---|---|
| P0 | **Spike trên máy CI, chưa đụng app.** Tải `renpy-8.5.3-rapt.zip` và `renpy-8.5.3-sdk.zip`, kiểm checksum theo file `.sums`. Xác nhận: (a) có `prototype/renpyandroid/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/librenpython.so`; (b) thư mục `private` của Android nằm ở đâu và gồm gì; (c) thư mục `renpy/` của SDK có dùng được làm `renpy_base` trên Android không; (d) kích thước từng phần | script tạm, kết quả ghi `docs/opus/ket-qua/P0.md` | Bảng liệt kê đường dẫn + kích thước thật. Chưa rõ (b) hoặc (c) thì dùng cách thay thế: dựng APK `the_question` bằng SDK rồi lấy `lib/`, `assets/private.mp3`, `assets/x-renpy/` từ APK |
| P1 | Workflow `build-renpy-pack.yml` (`workflow_dispatch`, tham số `version`, `publish`): tải file chính thức đã ghim URL + SHA-256 → ráp gói `renpy8-{abi}.zip` → `sha256sum` → đẩy sang `aowvn-monika-packs` (tag `engines-renpy8-<n>`) theo cách `build-kirikiri.yml` đang làm | `.github/workflows/build-renpy-pack.yml`, `packs/renpy/` (script ráp gói + `environment.txt` + `manifest.json`) | Artifact có đủ file theo bố cục dưới; Release ghi nguồn (URL renpy.org, phiên bản, link `license.rst`) |
| P2 | Module `:renpy`: Java từ `rapt/prototype/renpyandroid/src/main/java` của **đúng bản 8.5.3** (`org.renpy.android.*`, `org.jnius.*`, `org.kamranzafar.jtar.*`) + lớp `org.libsdl.app.*` của SDL 2.0.20 (trong RAPT đã có). Bản vá đặt ở `renpy/patches/`: bỏ Play Asset Delivery, bỏ `renpyiap`, bỏ `slf4j`. Chỉ sửa chỗ có chú thích `Aow Monika:`. `build.gradle` Groovy riêng như `:kirikiri`; ghi `UPSTREAM.md` | `renpy/` (module mới), `settings.gradle.kts`, `app/build.gradle.kts` | `./gradlew :renpy:assembleRelease` xanh; `assembleRelease` của app xanh; đo APK tăng bao nhiêu, ghi lại |
| P3 | `RenpyGameActivity : PythonSDLActivity`, tiến trình `:game`. Việc phải làm trong lớp này: (1) ghi đè nạp thư viện để `System.load("<gói>/librenpython.so")`; (2) ghi đè `getMainSharedObject()` trả đường dẫn đó; (3) ghi đè `preparePython()`: gán `mActivity`, **không** gọi `unpackData`, đặt biến môi trường theo bảng dưới bằng `nativeSetEnv`; (4) `Diagnostics.begin/stage/end` như `KirikiriGameActivity`; (5) lớp phủ `ComposeHost` với menu Monika; (6) kết thúc tiến trình khi thoát | `runner/RenpyGameActivity.kt`, `runner/RenpyOverlay.kt`, `AndroidManifest.xml`, `app/proguard-rules.pro` | Emulator Test (P5) |
| P4 | Đăng ký: `PackManager.installers` thêm `renpy8`; `EngineRoute` cho `renpy`; config thêm `modules.renpy8`, `systems[renpy].engine = "renpy"`, `prefetchByExtension` nếu có tín hiệu chắc (vd. `rpa`, `rpyc`); tăng `configVersion` | `pack/PackManager.kt`, `AppGraph.kt`, `config/monika-config.json`, `ConfigTest` | Unit test; `python3 scripts/audit-cores.py` không lỗi |
| P5 | Emulator Test: đẩy gói thật vào `files/packs/renpy8`, đẩy `the_question` (lấy từ repo `renpy/renpy` đúng tag 8.5.3) vào thư mục game, mở `RenpyGameActivity`, đợi log `MonikaGame: renpy-lib-loaded` rồi `MonikaGame: renpy-main-menu` (in từ `environment.txt` hoặc từ bước `stage`), chụp màn hình | `scripts/ci-emulator-games.sh`, `.github/workflows/emulator-test.yml` | Kết quả `LOADED` trên API 30 và 34; ảnh chụp có menu chính của game |
| P6 | Chọn runtime theo game: hàm thuần `RenpyVersion.detect(dir)` trả `PY3` / `PY2` / `UNKNOWN`. Dấu hiệu đề xuất **[CHƯA KIỂM]**: có thư mục `lib/py3-*` → PY3; có `lib/py2-*` hoặc `renpy/__init__.pyo` → PY2. `UNKNOWN` → thử PY3. PY2 mà chưa có gói `renpy7` → báo "game Ren'Py 7, sẽ hỗ trợ ở bản sau" | `library/RenpyVersion.kt` + test | Unit test với cây thư mục giả; sếp cấp 2 game thật (một bản 7, một bản 8) để đối chiếu |
| P7 | Save và chẩn đoán: save nằm ở thư mục Monika quản (`--savedir`), nối vào `SaveVault`. Khi tiến trình `:game` chết bất thường, đọc `traceback.txt` và `log.txt` trong `$ANDROID_PUBLIC` của game đó, đính vào báo cáo với nhãn `engine:renpy` | `runner/RenpyGameActivity.kt`, `library/SaveVault.kt`, `diag/` | Unit test phần đọc file; thử bằng game cố ý lỗi trong Emulator Test |

**Bố cục gói `renpy8-{abi}.zip` (đề xuất, chốt sau P0):**

```
librenpython.so
manifest.json            phiên bản Ren'Py, nguồn, SHA-256 từng file
private/
  main.py                giữ nguyên bản gốc
  environment.txt        của Monika (xem dưới)
  lib/python3.12/…       thư viện chuẩn Python cho Android (từ RAPT)
  renpy/…                engine Ren'Py 8.5.3, gồm cả renpy/common
empty.zip                file zip rỗng hợp lệ, để ANDROID_APK trỏ vào
```

**Biến môi trường đặt trong `preparePython()`:**

| Biến | Giá trị | Vì sao |
|---|---|---|
| `ANDROID_PRIVATE` | `<filesDir>/packs/renpy8/private` | Native `chdir` vào đây, lấy làm Python home và chạy `main.py` ở đây |
| `ANDROID_PUBLIC` | thư mục riêng cho từng game, do Monika tạo | Log và traceback của game ghi vào đây |
| `ANDROID_OLD_PUBLIC` | cùng thư mục trên | Mã gốc luôn đọc biến này |
| `ANDROID_APK` | `<gói>/empty.zip` | Loader Android luôn mở file này; không được trỏ vào APK Monika **[CHƯA KIỂM]** |
| `MONIKA_GAME_BASE` | thư mục game (thư mục chứa `game/`) | Dùng trong `environment.txt` |
| `MONIKA_SAVE_DIR` | thư mục save của game trong Monika | Dùng trong `environment.txt` |

**`private/environment.txt` (mã mẫu, [CHƯA KIỂM]):**

```python
# Aow Monika: bootstrap của Ren'Py thực thi file này trước khi đọc tham số
# (renpy/bootstrap.py). Dùng nó để trỏ Ren'Py tới game và thư mục save.
import os as _os, sys as _sys
_base = _os.environ.get("MONIKA_GAME_BASE")
_save = _os.environ.get("MONIKA_SAVE_DIR")
if _base:
    _sys.argv[1:] = [_base, "run"] + (["--savedir", _save] if _save else [])
del _os, _sys, _base, _save
```

Nếu P0 hoặc P5 cho thấy cách này không chạy (thứ tự thực thi khác ở bản 7.8.7, hoặc tham số không nhận): thay `private/main.py` bằng một file bọc đặt `sys.argv` rồi chạy `renpy.py` gốc (đổi tên thành `renpy_main.py`). Không vá mã Ren'Py.

### Khối P7 — Ren'Py 7

| Bước | Việc | Cách kiểm |
|---|---|---|
| P7.1 | `build-renpy-pack.yml` thêm biến thể 7.8.7 → gói `renpy7-{abi}.zip`. Kiểm `environment.txt` có được thực thi ở bản 7.8.7 không **[CHƯA KIỂM]**; không thì dùng `main.py` bọc | Artifact + checksum |
| P7.2 | Kiểm Java 8.5.3 trong `:renpy` có khớp JNI với `librenpython.so` 7.8.7 không (cùng SDL 2.0.20 nhưng mã Java RAPT có thể khác) **[CHƯA KIỂM]**. Không khớp → thêm module `:renpy7` với gói lớp đã đổi tên, hoặc dừng báo sếp | Emulator Test với game thử dựng bằng Ren'Py 7 |
| P7.3 | `PackManager` + config `modules.renpy7`; `RenpyVersion` trả PY2 → dùng gói này | Unit test + 1 game Ren'Py 7 thật của sếp |

---

## 5. Điều chưa chắc

| # | Điều chưa chắc | Kiểm ở bước | Nếu sai thì |
|---|---|---|---|
| U1 | Bố cục thật của `renpy-8.5.3-rapt.zip` và vị trí thư mục `private` cho Android | P0 | Dựng APK `the_question` rồi lấy từ APK |
| U2 | `ANDROID_APK` trỏ vào file zip rỗng có làm `android.apk.APK` lỗi không | P0, P5 | Trỏ vào zip có sẵn `assets/x-renpy/x-common/` |
| U3 | `environment.txt` sửa được `sys.argv` trước khi `bootstrap` đọc tham số, trên cả 8.5.3 và 7.8.7 | P5, P7.1 | `main.py` bọc |
| U4 | `SDLActivity` của SDL 2.0.20 và 2.26.3 cho ghi đè `loadLibraries()` và `getMainSharedObject()` để nạp `.so` ngoài APK | P3, R3 | Sửa nhỏ trong lớp SDL của module, có chú thích `Aow Monika:` |
| U5 | Cách đổi gói lớp SDL (chuỗi nào, macro nào trong `SDL_android.c`) | R1 | Đường khác: nâng/hạ SDL của mkxp-z về đúng 2.0.20 để dùng chung lớp với Ren'Py |
| U6 | mkxp-z liên kết tĩnh thành ít `.so` được tới đâu | R0, R1 | Nhiều `.so` + thứ tự nạp trong `manifest.json`, nạp theo cách `AzaharModule` |
| U7 | Mức tăng tốc sau khi bỏ `-O0`/`debug`; các khóa `JITEnable` có tác dụng trên Android không | R0 | Ghi số đo, không cam kết |
| U8 | `:renpy` biên dịch được với `compileSdk` của Monika (gốc là android-36) | P2 | Hạ về 35 nếu không dùng API 36; hoặc để module có `compileSdk` riêng như `:kirikiri` |
| U9 | Dấu hiệu phân biệt game Ren'Py 7 và 8 | P6 | Cần game thật của sếp |
| U10 | Tỉ lệ game Ren'Py chỉ có `.rpyc` không khớp `script_version` | P6, P7 | Báo lỗi rõ ràng; không có cách sửa nếu thiếu `.rpy` |
| U11 | Game trên bộ nhớ ngoài có được hệ file xử lý không phân biệt hoa/thường không (ảnh hưởng game làm trên Windows) | R6 | Chuẩn hóa tên file khi nhập game |
| U12 | Kích thước mọi gói và mức tăng APK | P0, P2, R0, R2 | Gói > 15 MB vẫn đúng luật mạng, chỉ khác là hỏi người chơi |
| U13 | Giấy phép `the_question` cho phép dùng trong CI | P5 | Tạo project mẫu bằng SDK (file sinh ra theo CC0, `license.rst`) |
| U14 | Hai Activity engine khác nhau lần lượt dùng chung tiến trình `:game` có xung đột thư viện không | P5, R6 | Đặt tiến trình riêng `:renpy`, `:rgss`; kiểm `Diagnostics` có phụ thuộc tên tiến trình không |

**Sonnet phải hỏi sếp (Q5), không tự tải game và không tự đoán:**

| Khi tới bước | Hỏi sếp | Dùng cho |
|---|---|---|
| R0 (ngay khi bắt đầu spike) | 1 game RPG Maker XP bất kỳ để dựng thử | Mốc tốc độ, kiểm khởi động |
| R6 | 5 game: G1 XP, G2 VX, G3 VX Ace có `.rgss3a`, G4 dựng trên Pokémon Essentials, G5 việt hóa có dấu. Ưu tiên game AowVN đã việt hóa | Cổng quyết định |
| R6 | Sếp chạy bảng thử trên máy thật, hoặc xác nhận chấp nhận kết quả "chỉ máy ảo" | Cổng quyết định |
| P6 | 2 game Ren'Py: một bản làm bằng Ren'Py 7, một bản bằng Ren'Py 8 | Kiểm dấu hiệu chọn runtime |
| P7.3 | 1 game Ren'Py 7 (dùng lại game ở P6 nếu được) | Kiểm gói `renpy7` |
| Bất kỳ lúc nào chạm ngưỡng "Dừng" hoặc thực tế khác plan | Báo sếp, chờ quyết | — |

Mỗi lần hỏi: một câu, nói rõ cần gì và để làm bước nào. Trong lúc chờ, làm tiếp các bước không phụ thuộc game (viết test, workflow, tài liệu).

---

## 6. Dự phòng (chỉ dùng khi cổng quyết định không đạt)

| Engine | Dự phòng | Điều kiện dùng | Ghi chú |
|---|---|---|---|
| RGSS | Core libretro mkxp-z (nhánh `libretro` của `white-axe/mkxp-z`, 30/06/2026) chạy qua `:libretrodroid` + `RetroActivity` | R0 không dựng được, hoặc R6 không đạt | Phải tự dựng 2 giai đoạn (WASI SDK 30). Không hỗ trợ Win32API. `:libretrodroid` cần vá: từ chối context không phải GLES trong `environment_handle_set_hw_render`, xử lý `SHUTDOWN`, chuyển `SET_MESSAGE` lên Kotlin. Không dùng `gameVirtualFiles` (core cần VFS v3, LibretroDroid có v2) |
| Ren'Py | Bản web chính thức (`renpy-8.5.3-web.zip`, 12.938.159 byte) chạy trong `WebGameActivity` như Onsyuri | P0–P5 không đạt | Không native. Bộ nhớ và tốc độ **[CHƯA KIỂM]** |

Không có dự phòng nào mở app ngoài.

---

## 7. Bảng tổng kết

| Câu hỏi | Kết luận | Việc Sonnet cần làm tiếp |
|---|---|---|
| 5. Nhúng sâu Ren'Py và RPG Maker XP/VX/Ace: theo mô hình Kirikiri hay hướng khác? | Theo mô hình Kirikiri cho cả hai. RGSS: tự dựng mkxp-z native trên CI, viết lại vỏ và hệ build, đổi gói lớp SDL. Ren'Py: đóng gói lại nhị phân RAPT chính thức, hai gói `renpy8` / `renpy7`. Không mở app ngoài, tắt theo từng hệ (trường `allowExternalApp` trong config). **RPG Maker làm trước**. Symbian nhúng sau (mục 8) | 1) E0.1–E0.4. 2) R0: hỏi sếp 1 game XP, dựng thử, dừng báo kết quả. 3) R1–R5, rồi R6: hỏi sếp 5 game thử, dừng chờ quyết. 4) R7; đặt `allowExternalApp: false` cho `rgss` và `kirikiri` khi gói `rgss` đã phát hành. 5) P0 (spike, dừng báo kết quả), rồi P1–P7. 6) P7.1–P7.3. 7) **Nhắc sếp về Symbian** theo mục 8. Sau mỗi khối cập nhật mục 4 của `docs/GIAO-TIEP-VOI-OPUS.md` |

---

## 8. Việc để sau — Sonnet phải nhắc sếp

| Việc | Vì sao để sau | Khi nào nhắc | Nhắc thế nào |
|---|---|---|---|
| **Nhúng Symbian (EKA2L1) vào Monika** | Sếp chốt: vẫn phải nhúng, nhưng làm sau RPG Maker và Ren'Py (Q6). Chưa có phương án thi công; `docs/plan-engine-moi.md` bước D mới ghi là cần spike CI riêng (dựng `libeka2l1-android.so` từ `src/emu/android/`, GPLv3, NDK 25.1, nhiều submodule; firmware do người chơi tự cung cấp) | (1) Ngay khi cổng R6 có kết quả. (2) Lần nữa khi khối P (Ren'Py 8) xong. (3) Mỗi lần cập nhật mục 4 của `docs/GIAO-TIEP-VOI-OPUS.md` mà việc này còn mở | Một dòng trong báo cáo: "Symbian vẫn mở app ngoài EKA2L1, chưa có phương án nhúng. Sếp có muốn giao Opus viết phương án bây giờ không?" |

Việc Sonnet làm ngay khi gộp tài liệu này: thêm dòng trên vào mục 5 ("Chưa làm / việc đang chờ") của `docs/GIAO-TIEP-VOI-OPUS.md`, và thêm câu hỏi thứ 8 vào mục 7 của file đó: "Nhúng Symbian (EKA2L1): đóng gói thế nào, nhập firmware ra sao?". Cho tới khi việc này xong, hệ `symbian` giữ `allowExternalApp: true` (bước E0.3).
