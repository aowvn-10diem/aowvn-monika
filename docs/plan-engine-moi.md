# Kế hoạch: đưa Kirikiri, Ren'Py, RPG Maker XP/VX/Ace, ONScripter và Symbian (EKA2L1) vào Monika

> Hiện 4 hệ đầu chạy bằng **app ngoài** (`config/monika-config.json` → `externalApps`: Kirikiroid2 `org.tvp.kirikiri2`, JoiPlay `cyou.joiplay.joiplay`, ONScripter chưa có gói). Mục tiêu: **chạy ngay trong Monika**, người chơi không phải cài thêm app.
> Mỗi bước có **"Kiểm"**; chưa đạt thì không sang bước sau. **[CHƯA KIỂM]** = chưa xác minh, phải làm bước 0 (spike) trước khi dựa vào. ⛔ = cần sếp quyết.

## 0. Dữ kiện đã kiểm (02/10/2026)

| Engine | Mã nguồn | Giấy phép | Android | Ghi chú |
|---|---|---|---|---|
| **EKA2L1** (Symbian/N-Gage) | https://github.com/EKA2L1/EKA2L1, commit `d45af36` (02/10/2026) | **GPLv3** | Có sẵn dự án Android: `src/emu/android/` (Gradle + CMake `app/src/main/cpp/CMakeLists.txt`) | C++17, nhiều submodule (`dynarmic` bản riêng, `capstone`, `yaml-cpp`, `glm`, `fmt`, `spdlog`, `glfw`, `stb`, `libcxxabi`, `microprofile`). Cần **firmware/ROM Symbian do người dùng tự cung cấp** (không đóng kèm). Danh sách tương thích: repo `EKA2L1/Compatibility-List`. |
| **Kirikiroid2** (Kirikiri2/KirikiriZ) | https://github.com/zeas2/Kirikiroid2, commit `d1c2b12` (05/06/2024) | Giấy phép Kirikiri2/Z (W.Dee và cộng tác viên) — **cần đọc kỹ file `LICENSE`** [CHƯA KIỂM điều khoản] | Có: `project/android/AndroidManifest.xml`, `cocos/`, `src/` (~577 file C/C++/Java) | Dựa Cocos2d-x; phần video lấy từ Kodi (GPL?) [CHƯA KIỂM — có thể kéo cả dự án sang GPL]. |
| **ONScripter** | [CHƯA KIỂM] chọn bản: ONScripter gốc (Ogapee), ONScripter-EN, ONScripter-Jh (bản có Android) | GPL-2.0 (bản gốc) [CHƯA KIỂM từng bản] | [CHƯA KIỂM] bản nào có dự án Android còn bảo trì | Hiện config chưa có `packageNames` cho ONScripter. |
| **Ren'Py** | https://github.com/renpy/renpy | MIT cho phần lớn; một số thành phần LGPL [CHƯA KIỂM chi tiết] | Ren'Py đóng gói Android **theo từng game** (RAPT) — **không có "trình chạy chung" chính thức** [CHƯA KIỂM] | JoiPlay có plugin chạy game Ren'Py nhưng **đóng nguồn** → không nhúng được. |
| **RPG Maker XP/VX/Ace (RGSS)** | Ứng viên: `mkxp-z` (bản mkxp mở rộng) | GPL-2.0+ [CHƯA KIỂM] | [CHƯA KIỂM] có bản Android dùng được không | JoiPlay dùng mkxp nhưng đóng nguồn. |

- **Đã có cơ chế sẵn để dùng lại:** `AzaharModule.kt` (tải/nạp engine `.so` từ gói `.zip`, khóa theo phiên bản trong `config.modules`), workflow **"Build engines"** (dựng gói và đưa lên Release `engine-azahar-*`), bước trong `release.yml` đóng gói engine mới nhất vào `assets/engines/`. Mọi engine mới đi theo đúng đường này.
- ⚠ **Repo private ⇒ app không tải được asset của GitHub Releases** (đó là lý do Azahar phải đóng thẳng vào APK, +13 MB). Muốn engine tải theo nhu cầu thì **cần chỗ chứa công khai** (⛔ sếp quyết: GitHub Releases sau khi công khai repo, hoặc Cloudflare R2). Cùng câu hỏi với `docs/plan-dich-offline.md`.
- **Giấy phép:** EKA2L1 là GPLv3; Monika đã chịu nghĩa vụ GPL từ LibretroDroid (GPLv3). ⛔ Sếp xác nhận sẽ công khai mã nguồn Monika (đã nói sẽ làm thủ công) **trước khi phát hành** bản có engine GPL mới.

## 1. Kiến trúc chung ("engine module" thứ 2 trở đi)

1. Thư mục `engines/<tên>/` ở gốc repo: mã nguồn engine (chép đúng commit, ghi `UPSTREAM.md` nguồn + commit + danh sách sửa, chú thích `Aow Monika:` như `docs/J2ME-LOADER.md`) hoặc script tải đúng commit khi build (ưu tiên script nếu nguồn rất lớn như EKA2L1).
2. Workflow **"Build engines"** thêm job cho từng engine → gói `engine-<tên>-<phiên bản>-<abi>.zip` gồm `.so` + thư viện phụ + `manifest.json` (phiên bản, SHA-256, thứ tự nạp).
3. App: lớp `engine/EngineModule` dùng chung (tổng quát hóa `AzaharModule`): tải/giải nén/kiểm SHA-256/nạp, `ready()`, tiến độ, xóa gói.
4. Mỗi engine chạy trong **Activity + tiến trình riêng** (như `AzaharActivity`, `:game`), nhận đường dẫn game qua Intent; thoát là về Monika. Lỗi/sập đi qua `Diagnostics` (nhãn thành phần = tên engine).
5. Config: `modules.<tên>` (url, version, abis) + hệ máy trong `systems` đổi `runner` từ `external` sang `engine` (kèm `engine: "<tên>"`); app cũ không hiểu `engine` vẫn rơi về `external` nếu giữ `externalApp` làm dự phòng.
6. **Dự phòng:** nếu engine nhúng chưa sẵn sàng hoặc lỗi, app vẫn mở **app ngoài như hiện tại** (không làm mất tính năng đang có).

## 2. Thứ tự làm (từ dễ đến khó)

### Bước A — Chuẩn bị chung (1 lượt)
- Tổng quát hóa `AzaharModule` → `EngineModule`, thêm test (giải nén, SHA-256, nạp theo phiên bản). Azahar chuyển sang dùng lớp mới **không đổi hành vi**.
- Quyết định chỗ chứa gói (⛔).
- **Kiểm:** unit test xanh; Emulator Test xanh; 3DS (Azahar) vẫn mở như cũ.

### Bước B — ONScripter (nhỏ nhất)
- **Spike:** chọn bản ONScripter có mã nguồn Android và giấy phép rõ (ONScripter-Jh/EN) [CHƯA KIỂM]; dựng được `.so` bằng NDK 22.1 hiện dùng cho `:j2me`, chạy 1 game demo mở nguồn.
- Đóng gói engine + `OnscripterActivity`; hệ máy: nhận thư mục game (`0.txt`, `nscript.dat`, `*.nsa`).
- **Kiểm:** game mẫu chạy trên máy ảo CI (thêm ca vào `scripts/ci-emulator-games.sh` nếu có ROM thử miễn phí) và máy thật.

### Bước C — Kirikiri (Kirikiroid2)
- **Spike:** đọc `LICENSE` và giấy phép phần Kodi video; dựng Android project của `zeas2/Kirikiroid2` (Cocos2d-x → cần tải Cocos2d-x đúng phiên bản) ra `.so`; chạy `.xp3` mẫu.
- Nếu giấy phép/độ phức tạp cản trở → giữ app ngoài (như hiện tại) và chỉ cải thiện cách gọi (mở thẳng game, không bắt người chơi chọn thư mục).
- **Kiểm:** game `.xp3` mẫu chạy, tiếng Việt hiển thị đúng (phông).

### Bước D — Symbian (EKA2L1)
- **Spike 1 (build):** dựng `src/emu/android/` bằng NDK (cần CMake + submodule), ra `libeka2l1-android.so` (tên [CHƯA KIỂM]) cho arm64. Ghi kích thước và thời gian build trên CI (có thể rất nặng).
- **Spike 2 (chạy):** nạp firmware thử (người dùng cung cấp, **không** đưa vào repo) và 1 game `.sis`/`.n-gage` miễn phí để kiểm.
- **Tích hợp:** Monika giữ vai trò **quản lý thư viện + trình chạy**: người chơi nhập firmware 1 lần (Cài đặt → Symbian), thêm game `.sis/.sisx/.ngage`; app gọi engine qua Intent. Giao diện cài firmware, chọn thiết bị giả (vd. N-Gage, 5800), bàn phím ảo cho điện thoại phím số (tận dụng bàn phím ảo đã làm cho J2ME).
- Hệ máy mới trong config: `symbian` (đuôi `sis, sisx, ngage`, hoặc thư mục).
- **Kiểm:** game mẫu vào được màn chính; sập thì có báo cáo theo nhãn `symbian`.
- ⚠ **Giấy phép firmware:** ROM Symbian là phần mềm có bản quyền của hãng; Monika **không** phân phối, chỉ hướng dẫn người dùng tự lấy từ máy của mình.

### Bước E — RPG Maker XP/VX/Ace (RGSS) và Ren'Py (khó nhất)
- **Spike RGSS:** đánh giá `mkxp-z` (hoặc bản mkxp khác có Android) về giấy phép, dựng Android được không, chạy game RPG Maker Ace mẫu mở nguồn; cần Ruby/Mruby và cả "RTP" (gói tài nguyên của RPG Maker, bản quyền riêng).
- **Spike Ren'Py:** kiểm khả năng làm "trình chạy chung": Ren'Py cần Python + `pygame_sdl2` + thư mục game; xem RAPT có dùng lại được để dựng một app nạp game từ thư mục ngoài không.
- Nếu hai spike không đạt → **giữ JoiPlay làm app ngoài** (đã có), chỉ đổi cách mở. Đây là kết quả chấp nhận được.
- **Kiểm:** theo spike; không cam kết trước.

## 3. Rủi ro và quyết định

| Rủi ro | Cách xử lý |
|---|---|
| Kích thước APK tăng (mỗi engine hàng chục MB) | Chỉ tải khi dùng, **cần chỗ chứa công khai** (⛔) |
| Giấy phép hỗn hợp (Kirikiri/Kodi, firmware Symbian, RTP RPG Maker) | Mỗi spike ghi rõ; không rõ thì giữ app ngoài |
| Build native lớn (EKA2L1) làm CI chậm/hỏng | Tách workflow "Build engines" riêng, chỉ chạy khi đổi phiên bản engine |
| Máy 32-bit (armeabi-v7a) không chạy engine | Giữ dự phòng app ngoài; ghi `abis` trong config |
| Engine upstream đổi/ngừng bảo trì (Kirikiroid2 chưa cập nhật từ 06/2024) | Ghim commit, ghi trong `UPSTREAM.md` |

## 4. Gợi ý phân công model
- Bước A, B: Sonnet (medium).
- Bước C, D, E (spike native, giấy phép, kiến trúc): Opus (high), mỗi bước một session/nhánh riêng; em review rồi gộp.

---
## ✅ Chỗ chứa gói đã chốt (02/10/2026)
Repo công khai **`aowvn-10diem/aowvn-monika-packs`**. Gói đưa lên bằng workflow **Build engines** (tự đẩy sang repo phụ khi có secret `PACKS_TOKEN`) hoặc **Mirror pack** (chép gói có sẵn rồi kiểm link tải công khai + SHA-256). Link dạng `https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/<tag>/<file>` đặt vào `config.modules.<tên>.url`. Azahar đã chuyển sang dùng link này (APK không còn đóng sẵn). Bản nhị phân GPL phát hành ở đó phải ghi cách lấy mã nguồn (repo + commit) trong mô tả Release / `manifest.json`.

---
## ✅ Tiến độ (02/10/2026, GMT+7)
- **Bước A (chuẩn bị chung):** xong theo hướng `SimpleModule` (tải → kiểm SHA-256 → giải nén phẳng vào `filesDir/packs/<id>`) + `PackManager` (quy tắc mạng, tải trước). Azahar giữ `AzaharModule` riêng (không đổi hành vi).
- **Bước B (ONScripter):** engine **OnscripterYuri bản web (wasm)** dựng xong trên CI (commit 08f744b, gói 1,79 MB, GPLv2, đăng ở `aowvn-monika-packs` Release `engines-onsyuri-2`, SHA-256 `e46e7b53…cff`). Monika nhúng như Ruffle: `WebGameActivity` phục vụ trang/wasm từ gói, tự sinh `onsyuri_index.json` từ thư mục game (tải lười), phông dự phòng Manrope khi game không có `default.ttf`, lưu game qua IndexedDB của WebView. Hệ `onscripter` đổi từ `external` sang `web` + `webPlayer: onsyuri`, `engine: onsyuri` (để tải trước theo nhãn bài). **[CHƯA KIỂM trên máy thật]** — cần 1 game ONScripter mẫu để thử: âm thanh/video, phông, cảm ứng, lưu game.
- **Bước C (Kirikiri) — spike xong, KHÔNG nhúng được:** giấy phép (`LICENSE` Kirikiri2/Z, kiểu BSD: cho phân phối kèm thông báo bản quyền) **không phải trở ngại**, nhưng mã nguồn Kirikiroid2 (zeas2, commit d1c2b12) **thiếu thư mục `vendor/`** (libgdiplus, google_breakpad, android-ndk-profiler) mà `Android.mk` yêu cầu, dựa Cocos2d-x + `gnustl_static` + `android-10` (NDK đời 2015), là Activity SDL chứ không phải thư viện nhúng được. Dựng lại = dự án port riêng. **Giữ app ngoài Kirikiroid2** như hiện tại.
- **Bước D (Symbian / EKA2L1) — làm bản app ngoài trước:** thêm hệ `symbian` (đuôi `sis`, `sisx`, `ngage`) mở app **EKA2L1** (`com.github.eka2l1`, đã xác minh trong `build.gradle` của họ) + hướng dẫn cài firmware. EKA2L1 Android không nhận mở thẳng file `.sis` qua Intent (chỉ nhận `.json`) nên người chơi cài game bên trong EKA2L1. Nhúng thẳng (dựng `libeka2l1-android.so`, NDK 25.1, nhiều submodule, GPLv3) để sau, cần spike CI riêng.
- **Bước E (RGSS, Ren'Py) — spike xong, KHÔNG nhúng được, giữ JoiPlay:**
  - **RGSS:** `mkxp-z` (GPL-2.0+, thư mục gốc có `linux/`, `macos/`, `windows/`, `meson.build`) **không có bản dựng Android** trong repo chính; phải tự port (SDL2 + Ruby + OpenAL...) và còn cần RTP có bản quyền riêng. Không thực hiện được trong phạm vi hiện tại.
  - **Ren'Py:** không có trình chạy chung (RAPT đóng gói theo từng game); JoiPlay có plugin nhưng đóng nguồn.
  - ⇒ Giữ nguyên `externalApp: joiplay` cho `renpy` và `rgss`. Chỉ nên cải thiện cách gọi (mở thẳng game) nếu JoiPlay cho phép Intent — chưa kiểm.
