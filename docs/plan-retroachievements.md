# Kế hoạch: RetroAchievements (RA) trong Aow Monika

> Viết cho người thực hiện (kể cả model nhỏ). Làm **đúng thứ tự**, mỗi bước có **"Kiểm"** — chưa đạt thì **không** sang bước sau.
> Gặp ô **⛔ DỪNG** → không tự quyết, báo sếp.
> Mọi dữ kiện dưới đây đã được kiểm ngày 01/10/2026 (nguồn ghi kèm). Thứ gì ghi **[CHƯA KIỂM]** thì phải kiểm trước khi dựa vào.

## 0. Tổng quan

| Giai đoạn | Làm gì | Cần native (C/C++)? | Phát hành |
|---|---|---|---|
| **1** | Thành phần **Thành tựu**: đăng nhập RA (tên + khóa web API), hồ sơ, game vừa chơi, thành tựu vừa mở, danh sách thành tựu từng game, gắn game trong Thư viện với game RA (băm file), Việt hóa giao diện + bản dịch theo game | Không | 0.6.0 |
| **2** | Mở khóa thành tựu **ngay khi đang chơi** (rcheevos đọc bộ nhớ lõi mỗi khung hình), thông báo trong game, hardcore | Có (fork LibretroDroid + rcheevos) | 0.7.0 |

Thành phần mới nằm ở **một thư mục riêng**: `app/src/main/java/vn/aow/monika/achievements/` (theo quy ước `docs/KIEN-TRUC.md`).
Phần native giai đoạn 2 nằm trong module mới `:libretrodroid`.

---

## Dữ kiện đã kiểm (dùng lại, không cần tra lại)

### Web API (giai đoạn 1) — nguồn: https://api-docs.retroachievements.org (bản .md: thêm `.md` vào đường dẫn, mục lục: `/llms.txt`)
- Gốc: `https://retroachievements.org/API/API_<Tên>.php`. **Bắt buộc** tham số `y` = khóa web API của người dùng (không có → HTTP 401, đã thử).
- Người dùng lấy khóa tại `https://retroachievements.org/controlpanel.php` → mục "Keys". Tài liệu RA dặn: giữ khóa như mật khẩu.
- Tham số `u` = tên người dùng **hoặc ULID**. Tên có thể đổi (từ 2025) → sau lần đầu, lưu **ULID** và dùng ULID.
- Endpoint dùng:

| Endpoint | Tham số | Dùng cho |
|---|---|---|
| `API_GetUserProfile.php` | `u` | Kiểm đăng nhập + hồ sơ (`User`, `ULID`, `UserPic`, `TotalPoints`, `TotalSoftcorePoints`, `RichPresenceMsg`, `LastGameID`) |
| `API_GetUserRecentlyPlayedGames.php` | `u`, `c` (≤50), `o` | Game vừa chơi (`GameID`, `ConsoleName`, `Title`, `ImageIcon`, `NumAchieved`, `NumPossibleAchievements`, `ScoreAchieved`…) |
| `API_GetUserRecentAchievements.php` | `u`, `m` (phút, mặc định 60) | Thành tựu vừa mở (`Date`, `HardcoreMode`, `Title`, `Description`, `BadgeName`, `Points`, `GameTitle`, `GameID`) |
| `API_GetGameInfoAndUserProgress.php` | `g`, `u` | Thành tựu 1 game + tiến độ (`Achievements` là **object** khóa = ID, mỗi mục có `Title`, `Description`, `Points`, `BadgeName`, `DisplayOrder`, `type`, `DateEarned`, `DateEarnedHardcore`; ngoài ra `UserCompletion`, `NumAwardedToUser`…) |
| `API_GetGameList.php` | `i` = console ID, `h=1` (kèm hash), `f=1` (chỉ game có thành tựu) | Bảng hash → GameID để gắn game trong Thư viện |
| `API_GetConsoleIDs.php` | `g=1` | (tùy) danh sách hệ máy |

- Ảnh (đã thử HTTP 200): `https://media.retroachievements.org` + đường dẫn trong JSON, ví dụ `…/Images/067895.png`, `…/UserPic/<tên>.png`.
  Huy hiệu: `https://media.retroachievements.org/Badge/<BadgeName>.png`, bản xám (chưa mở): `…/Badge/<BadgeName>_lock.png`.
- JSON trả về khóa **PascalCase** (vd. `"GameID"`). Dùng kotlinx.serialization với `@SerialName` + `ignoreUnknownKeys = true`.

### Băm game (để biết file trong Thư viện là game RA nào) — nguồn: rcheevos `src/rhash/hash.c`, `hash_rom.c` (commit `f87c0de`)
- Hash = **MD5 hex chữ thường** của:
  - **Cả file**: GB, GBC, GBA, Game Gear, Master System, Mega Drive, Neo Geo Pocket, WonderSwan, Atari 2600, 32X…
  - **NES**: bỏ 16 byte đầu nếu file bắt đầu bằng `4E 45 53 1A` ("NES\x1a") hoặc `46 44 53 1A` ("FDS\x1a").
  - **SNES**: nếu `size - (size / 0x2000) * 0x2000 == 512` thì bỏ 512 byte đầu.
  - NDS, N64, PSX, PSP, PCE, Lynx, 7800, Dreamcast…: **thuật toán riêng** → giai đoạn 1 **không** băm (hiện "Chưa hỗ trợ nhận diện, sẽ có ở bản sau"); giai đoạn 2 dùng `rc_hash` của rcheevos.
- Mã hệ RA (`include/rc_consoles.h`) ↔ hệ Monika (`config/monika-config.json`):

| Monika | RA ID | Giai đoạn 1 băm được? |
|---|---|---|
| `gba` | 5 | Có (cả file) |
| `gbc` đuôi `.gb` | 4 | Có |
| `gbc` đuôi `.gbc` | 6 | Có |
| `nes` | 7 | Có (bỏ header) |
| `snes` | 3 | Có (bỏ header 512) |
| `genesis` | 1 | Có (cả file; bỏ qua `.m3u`) |
| `sms` | 11 | Có |
| `gg` | 15 | Có |
| `ngp` | 14 | Có |
| `ws` | 53 | Có |
| `a2600` | 25 | Có |
| `nds` | 18 | Không (giai đoạn 2) |
| `n64` | 2 | Không |
| `ps1` | 12 | Không |
| `psp` | 41 | Không |
| `pce` | 8 | Không |
| `lynx` | 13 | Không |
| `a7800` | 51 | Không |
| `dc` | 40 | Không |
| `3ds` | 62 | Không làm (engine Azahar không qua LibretroDroid) |

### rcheevos (giai đoạn 2) — https://github.com/RetroAchievements/rcheevos, commit `f87c0de` (26/09/2026), **MIT**
- API chính (`include/rc_client.h`): `rc_client_create(read_memory, server_call)`, `rc_client_begin_login_with_password/with_token`, `rc_client_begin_identify_and_load_game(client, console_id, file_path, NULL, 0, cb, ud)` (cần define `RC_CLIENT_SUPPORTS_HASH`), `rc_client_do_frame`, `rc_client_idle`, `rc_client_set_event_handler`, `rc_client_set_hardcore_enabled`, `rc_client_reset`, `rc_client_unload_game`, `rc_client_destroy`, `rc_client_create_achievement_list`, `rc_client_get_user_agent_clause`.
- Đọc bộ nhớ lõi libretro có sẵn: `src/rc_libretro.h` → `rc_libretro_memory_init(regions, mmap_or_NULL, get_core_memory_info, console_id)` + `rc_libretro_memory_read(...)`.
- Hướng dẫn tích hợp: https://github.com/RetroAchievements/rcheevos/wiki/rc_client-integration — điểm bắt buộc:
  - **User-Agent**: `<product>/<số phiên bản> (<hệ điều hành>) <rcheevos clause>`, ví dụ `AowMonika/0.7.0 (Android 14) rcheevos/…`. Thiếu/không nhận ra → **máy chủ hạ hardcore thành softcore**. Muốn hardcore được công nhận phải **nhắn RAdmin** để duyệt client (https://retroachievements.org/messages/create?to=RAdmin) — ⛔ việc của sếp.
  - Hardcore mặc định **bật**. Khi hardcore: **cấm nạp save state** (lưu được), **cấm cheat**, cấm tua lùi/chậm (tua nhanh được).
  - Bật hardcore → nhận sự kiện `RC_CLIENT_EVENT_RESET` → phải reset lõi rồi gọi `rc_client_reset`.
- Mã nguồn cần biên dịch: `src/*.c`, `src/rapi/*.c`, `src/rcheevos/*.c`, `src/rhash/*.c`. `rc_client_raintegration.c` và `rc_client_external.c` tự rỗng nếu **không** define `RC_CLIENT_SUPPORTS_RAINTEGRATION` / `RC_CLIENT_SUPPORTS_EXTERNAL` (không define).

### LibretroDroid (giai đoạn 2) — https://github.com/Swordfish90/LibretroDroid, tag `0.14.0` = commit `8835c30`
- **Giấy phép GPLv3.** Monika đang dùng bản AAR từ JitPack → nghĩa vụ GPL **đã có từ trước** (phân phối APK thì phải cung cấp mã nguồn). Fork không thay đổi điều này. ⛔ Sếp xác nhận repo/mã nguồn công khai trước khi phát hành 0.7.0.
- **Không có hàm đọc bộ nhớ** ở phía Kotlin (`GLRetroView` chỉ có state/SRAM/cheat). Native thì có sẵn con trỏ hàm `retro_get_memory_data/size` (`cpp/core.h` dòng 42–43).
- `cpp/environment.cpp` **chưa xử lý** `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` → phải thêm (rcheevos cần bản đồ bộ nhớ cho GBA/NDS/N64…).
- Vòng chạy: `LibretroDroid::step()` (`cpp/libretrodroid.cpp` ~dòng 446) gọi `core->retro_run()` trong vòng `for (frames * frameSpeed)` → móc `rc_client_do_frame` **ngay sau mỗi** `retro_run()`.
- JNI: lớp Java `com.swordfish.libretrodroid.LibretroDroid` (các `static native`), C++ `cpp/libretrodroidjni.cpp` (`Java_com_swordfish_libretrodroid_LibretroDroid_*`).
- Submodule cần chép thẳng vào repo (giống cách đã làm TinySoundFont):
  - `libretrodroid/src/main/cpp/oboe` ← google/oboe commit `b15f5e39c01a7ada306d959e5129620b145fb8b4`
  - `libretrodroid/src/main/cpp/libretro/libretro-common` ← libretro/libretro-common commit `b0c348ea5543c4d7fb0bc479258aa6988b20c0c9`
- Build: CMake `3.22.1` (SDK của máy phiên **chưa có** `cmake/` → `sdkmanager "cmake;3.22.1"`), `-DANDROID_STL=c++_static`, đã có cờ trang 16 KB.

---

## GIAI ĐOẠN 1 — Thành phần "Thành tựu" (không native)

### 1.1 Mô hình + API (`achievements/RaApi.kt`, `achievements/RaModels.kt`)
- `RaModels.kt`: data class `@Serializable` cho 5 endpoint ở bảng trên (chỉ khai trường dùng tới; `@SerialName("GameID")`…). `Achievements` của game là `Map<String, RaAchievement>`.
- `RaApi(http: OkHttpClient)`: `suspend fun profile(user)`, `recentGames(user, count=20)`, `recentUnlocks(user, minutes=7*24*60)`, `gameProgress(gameId, user)`, `gameList(consoleId)` — tất cả `withContext(Dispatchers.IO)`, thêm `y=<key>`.
  Lỗi 401 → ném `RaAuthException`; lỗi mạng → `IOException` để UI hiện "Không kết nối được RetroAchievements".
- Hàm `media(path)` = `"https://media.retroachievements.org$path"`, `badge(name, locked)`.
- Dùng `AppGraph.http` (OkHttp sẵn có). **Không** thêm thư viện `api-kotlin` (tránh thêm phụ thuộc Retrofit).
- **Kiểm:** unit test Robolectric `RaModelsTest` parse đúng 2 JSON mẫu chép từ tài liệu (profile + game progress) — đặt mẫu trong `app/src/test/resources/ra/`.

### 1.2 Lưu đăng nhập (`achievements/RaAccount.kt`)
- Lưu `user`, `ulid`, `apiKey` trong SharedPreferences `ra` (tiến trình chính), **khóa được mã hóa** bằng `vn.aow.monika.apkinstall.repack.AndroidKeystoreWrap.wrap/unwrap` (đã có sẵn) + Base64.
- `signIn(user, key)`: gọi `profile(user)` → thành công thì lưu cả `ULID`; sai → báo "Tên hoặc khóa không đúng".
- `signOut()` xóa sạch.
- **Kiểm:** test lưu → đọc lại ra đúng khóa (Robolectric có thể không có Android Keystore → nếu test lỗi vì Keystore, tách lớp `KeyWrap` giả trong test, giống `RepackKeyStore` đang làm).

### 1.3 Gắn game Thư viện ↔ game RA (`achievements/RaHasher.kt`, `achievements/RaGameIndex.kt`)
- `RaHasher.consoleFor(systemId, file)`: theo bảng hệ máy ở trên (gbc phân `.gb`→4, `.gbc`→6).
- `RaHasher.hash(file, consoleId)`: MD5 stream (đừng đọc cả file vào RAM), áp quy tắc NES/SNES. Trả `null` với hệ "Không".
- `RaGameIndex`: tải `API_GetGameList?i=<id>&h=1&f=1`, lưu `filesDir/ra/gamelist-<id>.json` 7 ngày; dựng `Map<hash, GameID>`.
- Cache kết quả theo game: `filesDir/ra/match.json` `{ "<đường dẫn>|<kích thước>|<ngày sửa>": gameId | -1 }` để không băm lại.
- **Kiểm:** unit test hash với file tạo trong test: (a) GBA 4 KB ngẫu nhiên = MD5 cả file; (b) NES có header "NES\x1a" → MD5 phần sau 16 byte; (c) SNES 8192+512 byte → bỏ 512.

### 1.4 Giao diện (Compose, dùng `ui/theme` sẵn có — xem `docs/KIEN-TRUC.md`)
- **Màn "Thành tựu"** `achievements/AchievementsScreen.kt`, route mới `Routes.ACHIEVEMENTS` trong `ui/MainActivity.kt` (NavHost dòng ~184). Lối vào: thẻ ở **Trang chủ** + mục trong **Cài đặt**.
  - Chưa đăng nhập: giải thích ngắn, 2 ô (Tên RA, Khóa web API), nút **"Lấy khóa ở đâu?"** mở `controlpanel.php` bằng `InAppBrowserActivity.start(...)`.
  - Đã đăng nhập: thẻ hồ sơ (ảnh, điểm hardcore/softcore, đang chơi), "Game vừa chơi" (ảnh + `NumAchieved/NumPossibleAchievements` thanh tiến độ), "Thành tựu vừa mở" (huy hiệu + tên + điểm + ngày).
- **Sheet thành tựu 1 game** `achievements/GameAchievementsSheet.kt`: lưới huy hiệu (đã mở màu / chưa mở `_lock`), tên, mô tả, điểm, nhãn `progression`/`win_condition`/`missable` dịch thành "Cốt truyện"/"Phá đảo"/"Dễ lỡ". Lối vào: menu game trong Thư viện (mục "Thành tựu RA") — chỉ hiện khi đã đăng nhập và game gắn được.
- Ảnh: Coil (`coil-compose` 2.7.0 đã có).
- **Toàn bộ chữ tiếng Việt** (không dùng strings tiếng Anh). Ngày giờ hiển thị theo **GMT+7**, định dạng `dd/MM/yyyy HH:mm`.
- **Kiểm:** `./gradlew :app:testDebugUnitTest` xanh; build debug; chạy thử Maestro smoke (`.maestro/smoke.yaml`) vẫn xanh trên CI Emulator Test.

### 1.5 Việt hóa nội dung thành tựu (tùy chọn trong giai đoạn 1)
- RA **không có** bản dịch chính thức (tên/mô tả do người làm bộ thành tựu viết, thường tiếng Anh).
- Cách làm (config-first, không cần APK mới): file `config/ra-vi/<GameID>.json` = `{ "<AchievementID>": { "t": "Tên tiếng Việt", "d": "Mô tả" } }`, app tải từ cùng nguồn với `monika-config.json`, có thì hiện tiếng Việt + nút "Xem bản gốc".
- ⛔ **Không** tự dịch máy hàng loạt rồi commit khi sếp chưa duyệt chất lượng. Có thể dịch tay mẫu 1 game (ví dụ Pokémon Emerald) để sếp duyệt.

### 1.6 Phát hành 0.6.0
- Bump `versionCode`/`versionName` trong `app/build.gradle.kts`, commit (trailer theo CLAUDE.md), push `main`, chạy Release (`workflow_dispatch`, tag `v0.6.0`), lấy link Pixeldrain.
- Chạy `python3 scripts/gen-architecture.py` để cập nhật `docs/KIEN-TRUC.md` (thành phần mới `achievements`).

---

## GIAI ĐOẠN 2 — Mở khóa trong game (native)

⛔ **Trước khi bắt đầu**, sếp phải xác nhận: (a) chấp nhận nghĩa vụ GPLv3 (mã nguồn công khai), (b) đã/ sẽ nhắn RAdmin xin duyệt client "AowMonika" cho hardcore. Chưa có (b) thì vẫn làm được nhưng **mở khóa sẽ chỉ tính softcore**.

### 2.1 Đưa LibretroDroid vào repo (chưa sửa gì) — commit riêng
1. `git clone --depth 1 --branch 0.14.0 https://github.com/Swordfish90/LibretroDroid /tmp/ld` ; chép `/tmp/ld/libretrodroid/` → `libretrodroid/` ở gốc repo Monika (bỏ `.git`).
2. Chép submodule đúng commit ở mục "Dữ kiện" vào `libretrodroid/src/main/cpp/oboe` và `…/libretro/libretro-common` (tải tarball `https://codeload.github.com/<repo>/tar.gz/<commit>` rồi giải nén; xóa thư mục `samples/`, `tests/`, `docs/` của oboe cho nhẹ).
3. `libretrodroid/build.gradle`: bỏ `maven-publish`/`publishing`; `compileSdk`/`minSdk` theo app (minSdk 26); giữ `cmake { version '3.22.1' }`; thêm `ndkVersion "22.1.7171670"` (đang dùng cho `:j2me`). Nếu biên dịch lỗi do NDK cũ → thử NDK 27 (`sdkmanager "ndk;27.0.12077973"`) và ghi lại.
4. `settings.gradle.kts`: `include(":libretrodroid")`. `app/build.gradle.kts`: thay `implementation(libs.libretrodroid)` bằng `implementation(project(":libretrodroid"))`. Giữ JitPack nếu còn thư viện khác cần.
5. CI: trong `.github/workflows/release.yml` và `emulator-test.yml`, cạnh bước "Cài NDK", thêm `"cmake;3.22.1"` vào lệnh `sdkmanager`. Máy phiên: `yes | sdkmanager "cmake;3.22.1"`.
6. Thêm `libretrodroid/LICENSE` (GPLv3 gốc) và `docs/LIBRETRODROID.md` (nguồn, commit, danh sách sửa, quy ước chú thích `Aow Monika:` như `docs/J2ME-LOADER.md`).
- **Kiểm (bắt buộc trước khi sửa native):** `./gradlew :app:assembleRelease` xanh, `scripts/check-r8-mapping.py` OK, **Emulator Test** trên CI: 6 dòng `OK gba / gba-lcd / gba-sharp / gba-smooth / gb / nes`. Chưa xanh → không sang 2.2.

### 2.2 Thêm rcheevos — commit riêng
- Chép `include/` và `src/` của rcheevos commit `f87c0de` vào `libretrodroid/src/main/cpp/rcheevos/` + `LICENSE` (MIT).
- `CMakeLists.txt`: `file(GLOB RC_SRC rcheevos/src/*.c rcheevos/src/rapi/*.c rcheevos/src/rcheevos/*.c rcheevos/src/rhash/*.c)`, thêm vào `add_library(libretrodroid …)`; `target_include_directories(... rcheevos/include)`; `target_compile_definitions(libretrodroid PRIVATE RC_CLIENT_SUPPORTS_HASH)`.
- **Kiểm:** build release xanh, APK chạy game như cũ (Emulator Test xanh).

### 2.3 Native: bản đồ bộ nhớ + cầu nối rcheevos
1. `environment.cpp`: xử lý `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` → **sao chép sâu** `retro_memory_map` (mảng `retro_memory_descriptor`, chuỗi `addrspace`) vào biến của `Environment`; trả `true`. Xóa khi `destroy`.
2. File mới `cpp/achievements.h/.cpp` (chú thích `Aow Monika:`), lớp `Achievements`:
   - `create(consoleId, gamePath, userAgent, hardcore)` → `rc_client_create(readMemory, serverCall)`, `rc_client_set_event_handler`, `rc_client_set_hardcore_enabled`, log qua `rc_client_enable_logging` (mức WARN) ra logcat tag `MonikaRA`.
   - `readMemory(address, buf, n)` → `rc_libretro_memory_read(&regions, …)`; `regions` dựng **sau khi** `retro_load_game` thành công bằng `rc_libretro_memory_init(&regions, mmapHoặcNULL, getCoreMemoryInfo, consoleId)` với `getCoreMemoryInfo(id, info)` dùng `core->retro_get_memory_data/size`.
   - `serverCall(request, cb, cbData)` → lưu `{cb, cbData}` vào map theo id tăng dần → gọi lên Java `RetroAchievements.httpRequest(id, url, postData, contentType)` (JNI, lấy `JNIEnv` qua `JavaVM->GetEnv`/`AttachCurrentThread`).
   - JNI xuống: `onHttpResponse(id, httpStatus, body)` → tạo `rc_api_server_response_t { body, body_length, http_status_code }` → gọi `cb(&resp, cbData)`, xóa id.
   - Sự kiện → gọi lên Java `RetroAchievements.onEvent(type, id, title, description, badgeName, points)`; `RC_CLIENT_EVENT_RESET` → `core->retro_reset()` rồi `rc_client_reset`.
   - `doFrame()` → `rc_client_do_frame(client)`; `idle()` → `rc_client_idle`.
   - `destroy()` → `rc_client_unload_game` + `rc_client_destroy`.
3. `libretrodroid.cpp`: trong `step()` sau **mỗi** `core->retro_run()` gọi `if (achievements) achievements->doFrame();`. Sau `retro_load_game` thành công: dựng `regions` + `rc_client_begin_identify_and_load_game(client, consoleId, gamePath, NULL, 0, cb, nullptr)`. `destroy()`: hủy `achievements` **trước** `retro_unload_game`.
4. Đăng nhập trong game: dùng **token** (không lưu mật khẩu). Lần đầu: `rc_client_begin_login_with_password` từ màn Cài đặt (giai đoạn 2 thêm ô mật khẩu RA) → lấy `rc_client_get_user_info(client)->token` → lưu mã hóa (như 1.2) → các lần sau `begin_login_with_token`.

### 2.4 Kotlin trong module `:libretrodroid`
- Lớp mới `com.swordfish.libretrodroid.RetroAchievements` (Java/Kotlin, `Aow Monika:`): các `native` tương ứng (`setup(consoleId, userAgent, hardcore, user, token)`, `onHttpResponse`, `idle`, `achievementList(): Array<…>`), và `interface Listener { fun onUnlocked(...); fun onGameLoaded(...); fun onLoginFailed(...) }`.
- `httpRequest` chạy OkHttp trên luồng IO (không chặn luồng GL), User-Agent: `AowMonika/<versionName> (Android <release>) <rc_client_get_user_agent_clause>`.
- `GLRetroViewData`: thêm trường `achievements: RetroAchievements.Config?` (null = tắt) — truyền vào `LibretroDroid.create`.

### 2.5 App (`achievements/` + `runner/RetroActivity.kt`)
- `RetroActivity`: nếu đã đăng nhập RA + hệ máy có trong bảng console → đặt `achievements` cho `GLRetroViewData` (console ID theo bảng; NDS = 18…).
- Thông báo mở khóa: thẻ nổi 3 giây góc trên (huy hiệu + "🏆 Đã mở: <tên>" + điểm) trong `InGameOverlay`; rung nhẹ.
- Menu trong game: mục **"Thành tựu"** → sheet danh sách từ `rc_client_create_achievement_list` (đã mở / còn lại / tiến độ).
- **Hardcore** (Cài đặt → RetroAchievements, mặc định **bật**): khi bật thì **ẩn/khóa** "Nạp state", "Mã cheat", tốc độ < 1x (turbo vẫn được). Hiện dòng giải thích tiếng Việt.
- Gửi lỗi: log `MonikaRA` vào báo cáo `Diagnostics` (nhãn thành phần `achievements`).

### 2.6 Kiểm giai đoạn 2
- CI Emulator Test vẫn 6 OK (RA tắt khi chưa đăng nhập — không được làm hỏng chạy game).
- Thử tay (sếp, máy thật, tài khoản RA thật): game GBA có thành tựu dễ (vd. thành tựu đầu game) → thấy thông báo mở khóa và thấy trên trang hồ sơ RA. Ghi rõ softcore/hardcore.
- [CHƯA KIỂM] `rc_libretro_memory_init` với từng lõi Monika (mgba, gambatte, fceumm, snes9x, genesis_plus_gx, melondsds/desmume…) — kiểm từng hệ, hệ nào không đọc được bộ nhớ thì tắt RA cho hệ đó trong bảng console.

### 2.7 Phát hành 0.7.0
- Như 1.6. Cập nhật `docs/LIBRETRODROID.md`, `docs/KIEN-TRUC.md`.

---

## Rủi ro đã biết
| Rủi ro | Cách xử lý |
|---|---|
| Tự build LibretroDroid làm hỏng chạy game | Bước 2.1 tách commit riêng, phải xanh Emulator Test trước khi sửa |
| Hardcore bị hạ thành softcore | User-Agent đúng định dạng + sếp xin RAdmin duyệt |
| GPLv3 | Mã nguồn Monika công khai (sếp quyết) |
| Lõi không khai bản đồ bộ nhớ đúng | Kiểm từng hệ (2.6), tắt hệ lỗi |
| Khóa web API/ token lộ | Mã hóa bằng Android Keystore, không log, không gửi kèm báo lỗi |
| Tốc độ: `rc_client_do_frame` mỗi khung | Thường nhẹ; nếu tụt FPS ở máy yếu → chỉ bật khi đã đăng nhập (mặc định đã vậy) |
