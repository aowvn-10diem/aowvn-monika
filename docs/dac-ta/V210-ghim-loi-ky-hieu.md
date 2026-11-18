# V210 — Chiến lược ghim lõi + ký hiệu và nguyên nhân gốc `melondsds/anr`

> **Loại:** kỹ thuật — không đổi giao diện

## Mục tiêu

1. **Chấm dứt tình trạng "trôi bản đêm + mù ký hiệu"** trên các lõi giả lập có lượng lỗi thực địa cao nhất (đếm ngày 16/11 ở `#337` comment `6924786548`: **74 issue / 1.285 lượt**, trong đó **18/22 lõi** trỏ `buildbot.libretro.com/nightly/android/latest/...` đổi bản mỗi đêm và không có gói ký hiệu; khoảng **662 lượt** nằm trên nhóm lõi vừa trôi bản vừa không giải được địa chỉ hàm C++).
2. **Định hình kiến trúc phát hành gói lõi khi kho `aowvn-monika-packs` chuyển sang riêng tư (`[A70]`)** mà vẫn **tuân thủ 100% nghĩa vụ mã nguồn GPLv2/GPLv3** và tốn **0 lượt gọi Worker Cloudflare** cho gói ký hiệu/mã nguồn.
3. **Sửa đứt gãy trong đường ống giải ký hiệu (`scripts/crash-report-detail.py`)**: hiện tại script chỉ khớp biểu thức chính quy tombstone thô của Android ≤ 11 và bỏ qua hoàn toàn định dạng `TombstoneParser.toLines()` trên Android 12+ (`+0x<pc> ... [build_id <id>]`) cũng như khung native trong báo cáo `anr`.
4. **Chỉ ra và triệt tiêu nguyên nhân gốc `melondsds/anr` (421 lượt / 9 issue)** bằng bằng chứng `tệp:dòng` trên `main` (`4c4757b1`), đặc biệt là **thủ phạm mới phát hiện ở `LibretroCheats.kt:10` + `GLRetroView.kt:216`** (gọi đồng bộ `setCheat` `CountDownLatch.await(2000ms)` trên Main UI Thread cho cả **200–1.000 mã cheat đang tắt** ngay tại `first-frame`), khớp với cụm lặp tất định 33 lần của `#350` (216/421 lượt).

---

## Phạm vi

- **Trong phạm vi:**
  - **Đợt 1 (P0 — thực thi ngay trong V210, phủ ~610 lượt lỗi):** Ghim commit, dựng từ nguồn kèm `RelWithDebInfo` (`-g -O2`) + `BuildId` SHA-1, xuất bộ 3 gói (`<core>-<abi>.zip`, `symbols-<core>-<abi>.zip`, `sources-<core>-<abi>.zip`) cho 4 lõi:
    1. **`melondsds`** (`arm64-v8a`, CMake, GPLv3 — **442 lượt** / 12 issue: 421 `anr`, 21 `native`/`core-error`).
    2. **`desmume`** (`arm64-v8a` + `armeabi-v7a`, `ndk-build`, GPLv2+ — **105 lượt** / 7 issue, trong đó 97 lượt `native` `SIGSEGV`/`SIGABRT`; đây là nhóm crash C++ lớn thứ 2 toàn app sau `ppsspp` mà đề xuất ban đầu của đội bỏ sót).
    3. **`pcsx_rearmed`** (`arm64-v8a` + `armeabi-v7a`, `ndk-build`, GPLv2 — **55 lượt** / 4 issue).
    4. **`snes9x`** (`arm64-v8a` + `armeabi-v7a`, `ndk-build`, Snes9x Non-Commercial — **8 lượt** / 3 issue).
  - **Tổng quát hóa công cụ dựng & đăng gói:** Mở rộng `scripts/prepare-core-build.py`, `scripts/ppsspp-snapshot.py` và `.github/workflows/publish-ppsspp.yml` để nhận tham số `core` bất kỳ trong `engines/sources.lock.json` theo tag độc lập từng lõi `cores-<core_id>-<n>`.
  - **Sửa bộ giải ký hiệu `scripts/crash-report-detail.py` + `scripts/test-crash-symbols.py`:** Hỗ trợ cả định dạng `TombstoneParser` (`lib.so+0x1a2b3c ... [build_id ...]`) lẫn định dạng tombstone thô (`pc 001a2b3c ... (BuildId: ...)`) cho cả `kind == "native"` và `kind == "anr"`.
  - **Vá dứt điểm điểm nghẽn ANR `melondsds` ở tầng Kotlin/JNI (`LibretroCheats.kt`, `GLRetroView.kt`, `libretrodroid.cpp`)** + kết nối dữ liệu chẩn đoán từ `#656` và `#660`.
- **Ngoài phạm vi (làm ở Đợt 2 & Đợt 3 sau V210):**
  - **Đợt 2 (P1 — ~137 lượt):** (a) Dựng lại 3 lõi `mgba`, `gambatte`, `fceumm` từ `prepare-core-build.py` để bổ sung `symbols-*.zip` + `sources-*.zip` (hiện `cores-gb-gba-nes-1` mới là bản chụp binary từ buildbot chưa có gói ký hiệu, và `cores.<id>.url` cấp cao trong `config/monika-config.json:853,920,1007` vẫn sót URL buildbot); (b) Thêm công thức dựng `melonds` (4 lượt) và `citra` (121 lượt, chỉ `arm64-v8a`, tách workflow riêng `timeout-minutes: 45` vì CMake + submodules lớn).
  - **Đợt 3 (P2 — 0 lượt lỗi hiện tại):** 12 lõi còn lại (`a5200`, `beetle_ngp`, `beetle_vb`, `beetle_wswan`, `fbneo`, `freeintv`, `genesis_plus_gx`, `handy`, `mednafen_pce_fast`, `opera`, `prosystem`, `stella2014`) — chỉ làm sau khi hoàn tất A85.
  - Sửa trực tiếp mã C++ nội bộ của lõi `melondsds`, `desmume`, `pcsx_rearmed` (chỉ mở PR vá upstream/patch sau khi có stacktrace đã giải ký hiệu từ V210).

---

## Luồng hoạt động

### 1. Phán quyết chiến lược ghim lõi, kho riêng tư `[A70]` và nghĩa vụ GPL

#### 1.1. Bảng phân tầng ưu tiên 22 lõi theo số liệu thực địa (16/11)

| Đợt | Lõi (`core_id`) | Hệ máy | Kiến trúc (`abis`) | Hệ dựng | Giấy phép | Lượt lỗi / Issue | Trạng thái hiện tại (`configVersion = 65`) | Quyết định ở V210 |
|---|---|---|---|---|---|---:|---|---|
| **Đã ghim chuẩn** | `ppsspp` | PSP | `arm64-v8a`, `armeabi-v7a` | CMake | GPLv2+ | 495 / 26 | Đã ghim `cores-ppsspp-1` + có `symbols` & `sources` | Giữ nguyên làm mẫu chuẩn |
| **Đợt 1 (P0)** | `melondsds` | NDS | `arm64-v8a` | CMake | **GPLv3** | **442 / 12** | Trôi nightly, không `sha256`, không `symbols` | **Ghim ngay (`cores-melondsds-1`) + vá ANR** |
| **Đợt 1 (P0)** | `desmume` | NDS | `arm64-v8a`, `armeabi-v7a` | `ndk-build` | **GPLv2+** | **105 / 7** | Trôi nightly, không `sha256`, không `symbols` | **Bổ sung vào Đợt 1 (`cores-desmume-1`)** |
| **Đợt 1 (P0)** | `pcsx_rearmed` | PSX | `arm64-v8a`, `armeabi-v7a` | `ndk-build` | **GPLv2** | **55 / 4** | Trôi nightly, không `sha256`, không `symbols` | **Ghim ngay (`cores-pcsx_rearmed-1`)** |
| **Đợt 1 (P0)** | `snes9x` | SNES | `arm64-v8a`, `armeabi-v7a` | `ndk-build` | **Non-commercial** | **8 / 3** | Trôi nightly, không `sha256`, không `symbols` | **Ghim ngay (`cores-snes9x-1`)** |
| **Đợt 2 (P1)** | `citra` | 3DS | `arm64-v8a` | CMake | GPLv2+ | 121 / 8 | Trôi nightly, chưa có trong `sources.lock.json` | Tách workflow riêng sau Đợt 1 |
| **Đợt 2 (P1)** | `mgba`, `gambatte`, `fceumm` | GBA, GBC, NES | `arm64-v8a`, `armeabi-v7a` | CMake / `ndk-build` | MPLv2 / GPLv2 | 12 / 4 | Đã ghim binary `cores-gb-gba-nes-1` nhưng **thiếu `symbols-*.zip`** | Dựng lại từ nguồn ở Đợt 2 + xóa URL buildbot cấp cao |
| **Đợt 2 (P1)** | `melonds` | NDS (dự phòng) | `arm64-v8a`, `armeabi-v7a` | CMake | GPLv3 | 4 / 3 | Trôi nightly, chưa có trong `sources.lock.json` | Thêm vào `sources.lock.json` ở Đợt 2 |
| **Đợt 3 (P2)** | 12 lõi còn lại | Arcade, MD, PCE... | Cả 2 ABI | `ndk-build` | Đa dạng | 0 / 0 | Trôi nightly | Hoãn sau A85 |

#### 1.2. Đặt gói ở kho riêng tư `[A70]` và quy tắc đặt tên tag
- **Tag độc lập theo từng lõi (`cores-<core_id>-<n>`):** Không gom chung nhiều lõi vào một tag kiểu `cores-snes-psx-nds-1`. Mỗi lõi có vòng đời nâng cấp riêng: `cores-melondsds-1`, `cores-desmume-1`, `cores-pcsx_rearmed-1`, `cores-snes9x-1`. Khi cần vá riêng `melondsds`, chỉ dựng lại `cores-melondsds-2`, không bắt người dùng tải lại 3 lõi kia.
- **Phân bổ 3 loại gói khi `aowvn-monika-packs` chuyển sang Private (`[A70]`, `noibo#8`):**

| Loại gói | Tên tệp chuẩn | Nơi lưu trữ | Ai truy cập | Chi phí Worker Cloudflare |
|---|---|---|---|---|
| **1. Gói cài đặt (Binary đã strip debug, giữ `BuildId`)** | `<core_id>-<abi>.zip` (bên trong có `<core_id>_libretro_android.so`, `SOURCE.txt`, `LICENSES/`) | (a) Release `aowvn-monika-packs` (tag `cores-<core_id>-<n>`)<br>(b) R2 bucket `monika-packs` (key `<core_id>-<n>-<abi>.zip`) | App Monika gọi `https://api.monika.aow.vn/pack/<core_id>-<n>-<abi>.zip` (hoặc tên trong `PACKS_ALLOW` được ánh xạ qua `PACKS_MAP` ở `worker.js:434-466`) → Worker xác thực cổng G3 và trả `302` kèm presigned URL R2 SigV4 | **1 lượt duy nhất** khi người dùng tải lõi lần đầu; **0 lượt** khi mở game |
| **2. Gói ký hiệu DWARF (Unstripped `.so`)** | `symbols-<core_id>-<abi>.zip` (bên trong có `<core_id>_libretro_android.so` nguyên bản chứa `.debug_info`, `.symtab`, `.note.gnu.build-id` + `METADATA.json`) | **Chỉ đặt ở Release `aowvn-monika-packs` (Private)** | Chỉ đội vận hành / CI tải qua `gh release download` (`scripts/crash-report-detail.py:139`) bằng quyền đọc repo `aowvn-monika-packs` | **0 lượt** (không đưa lên R2, không thêm vào `PACKS_ALLOW`) |
| **3. Gói mã nguồn hoàn chỉnh (Corresponding Source)** | `sources-<core_id>-<abi>.zip` (tarball nguồn đúng commit + submodules + `BUILD-RECIPE.py` + `sources.lock.json`) | (a) Release `aowvn-monika-packs`<br>(b) **Bản sao trên Release lưu trữ nguồn `core-sources-1` của repo công khai `aowvn-monika`** | Bất kỳ người dùng nào nhận APK/core (tự do tải trực tiếp từ GitHub Public Release theo đúng nghĩa vụ GPL) | **0 lượt** |

#### 1.3. Nghĩa vụ GPL về mã nguồn khi bật `[A70]`
- **Ràng buộc pháp lý (GPLv2 §3 & GPLv3 §6):**
  - `melondsds` dùng **GPLv3**, `desmume` / `pcsx_rearmed` / `ppsspp` / `fceumm` / `gambatte` dùng **GPLv2/GPLv2+**.
  - Khi app phân phối tệp nhị phân `<core>_libretro_android.so` tới máy người dùng (dù tải qua cổng `api.monika.aow.vn/pack/...`), dự án **bắt buộc** phải cung cấp *Corresponding Source* (mã nguồn chính xác của commit đã biên dịch + toàn bộ submodules + kịch bản và cờ biên dịch `prepare-core-build.py`) ở một nơi mà người nhận nhị phân truy cập được **không cần tài khoản nội bộ**.
  - Nếu `aowvn-monika-packs` chuyển sang Private (`[A70]`) mà `sources-<core>-<abi>.zip` chỉ nằm trong Release của `aowvn-monika-packs`, người dùng ngoài sẽ bị chặn `404` khi tải mã nguồn → **vi phạm GPLv2/GPLv3**.
- **Giải pháp chuẩn (vừa giữ kín kho gói nhị phân `[A70]`, vừa tuân thủ tuyệt đối GPL):**
  1. Trong mỗi gói cài `<core>-<abi>.zip` (do `scripts/package-native-symbols.py:67-105` đóng gói), giữ nguyên `SOURCE.txt` và `LICENSES/`, đồng thời bổ sung dòng URL tải trực tiếp `sources-<core>-<abi>.zip` từ Release `core-sources-1` trên repo công khai `aowvn-monika`.
  2. Khi workflow `publish-ppsspp.yml` (nâng cấp thành workflow đăng gói tổng quát) chạy xong, ngoài việc đăng cả 3 gói lên `aowvn-monika-packs`, workflow tự động tải riêng `sources-<core>-<abi>.zip` (hoặc đổi tên thành `sources-<core>-<n>-<abi>.zip`) lên tag `core-sources-1` (`prerelease: true`, `latest: false`) của repo công khai `aowvn-monika`.
  3. **Tác dụng kép:** Bản nhị phân `.so` (đã tối ưu PGO/LTO) và gói ký hiệu `symbols-*.zip` vẫn nằm kín trong kho riêng tư `aowvn-monika-packs` + R2 sau cổng G3; kẻ fork repo công khai chỉ thấy mã nguồn C++ gốc của upstream (vốn đã công khai trên GitHub Libretro) chứ không lấy được gói `.so` dựng sẵn hay kho ROM.
- **Lưu ý riêng cho `snes9x`:** Giấy phép Snes9x là *Non-Commercial* (cấm thu tiền trực tiếp từ lõi Snes9x). Vì Aow Monika phát hành miễn phí 100%, không bán ứng dụng và không thu phí mở khóa hệ SNES nên hoàn toàn hợp lệ; tuy nhiên nếu tương lai bản Play có gói trả phí, phải tách `snes9x` khỏi gói thương mại.

---

### 2. Kế hoạch dựng, đăng gói và giải ký hiệu theo `build_id`

```
engines/sources.lock.json (ghim commit 40 ký tự)
        │
        ▼
scripts/prepare-core-build.py --core <id> --abi <abi>
  ├─ CMake (melondsds): -DCMAKE_BUILD_TYPE=RelWithDebInfo + -Wl,--build-id=sha1
  ├─ ndk-build (desmume, pcsx_rearmed, snes9x): APP_CFLAGS="-g -O2" APP_LDFLAGS="-Wl,--build-id=sha1"
  ├─ Kiểm bắt buộc require_debug_symbols() trên obj/local/<abi>/libretro.so (symbol_library)
  └─ Strip bản phát hành bằng llvm-strip --strip-unneeded (giữ nguyên .note.gnu.build-id)
        │
        ▼
scripts/package-native-symbols.py
  ├─ <core>-<abi>.zip         (bản strip nhẹ cho app + SOURCE.txt + LICENSES/)
  ├─ symbols-<core>-<abi>.zip (bản unstripped DWARF + METADATA.json chứa build_id)
  └─ sources-<core>-<abi>.zip (tarball nguồn + BUILD-RECIPE.py + sources.lock.json)
        │
        ▼
scripts/ppsspp-snapshot.py --core <id> --tag cores-<id>-<n>
  └─ Cập nhật config/monika-config.json (cores.<id>.url + cores.<id>.artifacts.<abi>.{url, sha256, size, version})
     và tăng configVersion (+1)
        │
        ▼
scripts/crash-report-detail.py (khi có crash native / ANR)
  └─ Đọc stack từ TombstoneParser (+0x<pc> [build_id <id>]) hoặc tombstone thô (pc <pc> (BuildId: <id>))
     → Tải symbols-<core>-<abi>.zip từ aowvn-monika-packs theo coreVersion (cores-<id>-<n>)
     → Khớp build_id → llvm-symbolizer giải ra đúng tệp.cpp:dòng
```

#### 2.1. Ba điểm cần sửa trong đường ống dựng & giải ký hiệu hiện tại
1. **`scripts/prepare-core-build.py:336-340, 395-406`**:
   - Hiện tại `build_ndk_core()` (`:336-340`) gọi `ndk-build` không truyền cờ `-g` và `--build-id=sha1`, nên một số `Android.mk` (như `desmume` hoặc `pcsx_rearmed`) có thể thiếu `.debug_info` hoặc thiếu `BuildId` chuẩn SHA-1 trong `obj/local/<abi>/libretro.so`.
   - Dòng `395`: `if args.core == "ppsspp": require_debug_symbols(symbol_library)` chỉ kiểm tra mỗi `ppsspp`. Phải bật `require_debug_symbols(symbol_library)` cho **mọi lõi** được dựng qua script này.
   - Dòng `405`: `if args.core in ("mgba", "ppsspp"):` mới chạy `llvm-strip --strip-unneeded` trên `built_library`. Cần chạy `llvm-strip --strip-unneeded` cho **mọi lõi** trên `built_library` (sau khi đã giữ bản `symbol_library` nguyên vẹn ở `obj/local/<abi>/` hoặc bản sao `.unstripped.so`).
2. **`scripts/ppsspp-snapshot.py` và `.github/workflows/publish-ppsspp.yml`**:
   - Hiện tại `scripts/ppsspp-snapshot.py:13` khóa cứng `ABIS = ("arm64-v8a", "armeabi-v7a")` và chỉ ghi vào `config["cores"]["ppsspp"]`. Với `melondsds` (chỉ có `arm64-v8a`), script cũ sẽ lỗi thiếu tệp `armeabi-v7a`.
   - Giải pháp: Thêm đối số `--core <core_id>` (mặc định `"ppsspp"` để tương thích ngược 100% với test hiện có), đọc danh sách `abis` từ `engines/sources.lock.json` (`lock["cores"][core]["abis"]`), và cập nhật cả `config["cores"][core]["url"]` (trỏ vào URL `arm64-v8a` đã ghim, xóa hẳn link `buildbot.libretro.com/nightly/...`) lẫn `config["cores"][core]["artifacts"][abi]`.
3. **`scripts/crash-report-detail.py:66, 80` (Lỗi nghiêm trọng làm mù giải ký hiệu trên Android 12+ và báo cáo ANR)**:
   - Trên Android 12+ (API 31+), `Diagnostics.kt:317` trích xuất tombstone protobuf của hệ thống qua `TombstoneParser.toLines()` (`app/src/main/java/vn/aow/monika/diag/TombstoneParser.kt:153-155`), tạo ra dòng khung có dạng:
     `#00 ppsspp_libretro_android.so+0x1a2b3c func+42 [build_id dd1e9133a02d0d3017d8a196cb45cd49f50c2294]`
   - Thế nhưng `scripts/crash-report-detail.py:80` chỉ có duy nhất một regex:
     `FRAME_RE = re.compile(r"#\d+\s+pc\s+([0-9a-fA-F]+)\s+(\S+).*?\(BuildId:\s*([0-9a-fA-F]+)\)")`
     Do đó **100% báo cáo từ `TombstoneParser` không khớp regex và không được giải ký hiệu!**
   - Đồng thời dòng `66` viết `if report.get("kind") != "native": return`, khiến các báo cáo `anr` có đính kèm khung native hoàn toàn không được giải ký hiệu.

---

### 3. Nguyên nhân gốc `melondsds/anr` (421 lượt / 9 issue)

#### 3.1. Bảng đối chiếu dữ liệu thực địa (`#337`, `docs/loi/melondsds-anr-1311.md`, `melondsds-anr-1313.md`)

| Issue | Lượt | Báo cáo | Lượt / báo cáo | Đặc trưng thực địa | Nguyên nhân gốc khớp |
|---|---:|---:|---:|---|---|
| **#350** | **216** | **69** | ~3,13 (cụm **33 lượt / 1 báo cáo** tất định) | 51,3% tổng `melondsds/anr`; lặp 33 lần với cùng chuỗi hành động ngay khi vào game / nạp màn chơi | **Thủ phạm #1 (Mới phát hiện):** `RetroActivity.kt:387` gọi `cheats.applyAll()` trên **Main UI Thread** ngay tại `first-frame`; `LibretroCheats.kt:10` gọi `GLRetroView.setCheat` (`CountDownLatch.await(2000ms)`) cho **toàn bộ 200–1.000 mã cheat đang tắt** |
| **#318** | **177** | **52** | ~3,40 | 42,0% tổng `melondsds/anr`; rải đều nhiều máy khi tạm dừng / khóa màn hình / chuyển ứng dụng | **Thủ phạm #2:** `GLRetroView.kt:371` gọi `GLSurfaceView.onPause()` trên Main Thread (qua `onActivityPrePaused` trước thân `RetroActivity.onPause`), giữ khóa `sGLThreadManager` chờ vô hạn khi `GLThread` đang kẹt trong `melondsds` `retro_run()` / `initializeCore()` |
| **#367, #489, #312, #463, #523, #602, #638** | **28** | **13** | 1,0–3,7 | 6,7% còn lại; xảy ra khi thoát game / lưu SRAM hoặc máy yếu | **Thủ phạm #3:** Cộng dồn thời gian chờ tuần tự trên Main Thread (`GLSurfaceView.onPause()` + `serializeSRAMOrNull(300ms)` ở `RetroActivity.kt:680,713` + `sramJob.join(1500ms)` ở `:693`) |

#### 3.2. Các giả thuyết cũ đã kiểm chứng và loại trừ
- **`viewport`, `frameSpeed`, `shader`, `updateVariables`, `getVariables`:** Đã chuyển sang bất đồng bộ `EmulationCall.post` / gắn timeout ở PR `#606` (`64ff519`) và `#619` (`92bb5e7`). Không còn chặn Main Thread trên `main`.
- **Giả thuyết "`coreLock` trong `LibretroDroid::step()` chặn `LibretroDroid::pause()`" (`docs/loi/melondsds-anr-1311.md`):** **Bác bỏ** (đúng như phân tích ở `docs/loi/melondsds-anr-1313.md` §4.4). Trong `GLRetroView.kt:371-372`, Main Thread gọi `GLSurfaceView.onPause()` (dòng 371) **trước** khi gọi `LibretroDroid.pause()` (dòng 372). `GLSurfaceView.onPause()` chỉ trả về sau khi `GLThread` đã kết thúc vòng `onDrawFrame()` → `step()` và nhả `coreLock`. Vì vậy dòng 372 không bao giờ tranh chấp `coreLock` với `step()`. **Tuyệt đối cấm** nhả `coreLock` quanh `fpsSync->wait()` trong `libretrodroid.cpp:250` vì `destroy()` xóa `fpsSync` dưới `coreLock`, nhả khóa sẽ gây lỗi *Use-After-Free* (`SIGSEGV`).
- **Giả thuyết "`GLSurfaceView.onDetachedFromWindow()` gọi `requestExitAndWait()` khi thoát game":** Không xảy ra trên đường thoát chuẩn (`isFinishing == true`) vì `RetroActivity.onDestroy()` (`RetroActivity.kt:700`) gọi `Process.killProcess(Process.myPid())` trước khi `onDestroy()` trả về cho `ActivityThread` gỡ View khỏi Window.

#### 3.3. Phân tích chi tiết 3 thủ phạm gốc rễ trên `main` (`4c4757b1`)

##### Thủ phạm #1 (PHÁT HIỆN MỚI — Khóa cứng Main Thread `O(N)` lần tại `first-frame` và khi chỉnh Cheat)
- **Bằng chứng `tệp:dòng`:**
  1. `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:378`: Khi mở bất kỳ game NDS nào, `cheats.prepare()` tự động tải tệp `.cht` của game từ kho `libretro-database` (`CheatDb.kt:87`) và lưu vào `filesDir/cheats/$gameKey.json`. Một game NDS phổ biến (Pokémon, Yu-Gi-Oh!, Mario & Luigi, Fire Emblem...) có **200 đến 1.200 mã cheat** trong tệp `.cht`, tất cả mặc định `enabled = false`.
  2. Từ lần mở game thứ 2 trở đi (hoặc ngay sau khi tải `.cht` xong), `cheats.prepare()` nạp ngay danh sách 200–1.200 mục này từ đĩa vào bộ nhớ trước khi khung hình đầu tiên xuất hiện.
  3. Ngay khi khung hình đầu tiên (`first-frame`) được vẽ, `RetroActivity.kt:381-387` chạy trên **Main UI Thread** (`lifecycleScope.launch` mặc định `Dispatchers.Main`):
     ```kotlin
     lifecycleScope.launch {
         retroView.getGLRetroEvents().first { it is GLRetroView.GLRetroEvents.FrameRendered }
         ...
         if (!raWanted) cheats.applyAll()
     }
     ```
  4. `CheatController.applyAll()` (`CheatController.kt:65`) gọi `LibretroCheats.apply(retroView, list, lastApplied)`. Hãy xem `app/src/main/java/vn/aow/monika/cheat/LibretroCheats.kt:4-12`:
     ```kotlin
     fun apply(v: GLRetroView, list: List<CheatEntry>, previous: List<CheatEntry> = emptyList()) {
         val turnedOff = previous.any { prev ->
             prev.enabled && list.none { it.id == prev.id && it.enabled && it.code == prev.code }
         }
         if (turnedOff || previous.isEmpty()) runCatching { v.resetCheat() }
         if (list.none { it.enabled }) return
         list.forEachIndexed { i, e -> runCatching { v.setCheat(i, e.enabled, e.code) } }
     }
     ```
     - **Tình huống A (Người chơi bật dù chỉ 1 mã cheat trong danh sách 500 mã):** Dòng 9 `if (list.none { it.enabled }) return` đi qua, và dòng 10 gọi `v.setCheat(i, e.enabled, e.code)` cho **cả 500 mã** (499 mã `enabled = false` + 1 mã `enabled = true`) vì dòng 10 **quên kiểm tra `if (e.enabled)`**!
     - **Tình huống B (Người chơi tắt mã cheat cuối cùng hoặc chỉnh danh sách):** `v.resetCheat()` chạy, và mỗi lần bật/tắt 1 mã trong UI (`CheatController.kt:37`), toàn bộ 500 mã lại bị đẩy qua `v.setCheat(...)` tuần tự trên Main UI Thread!
  5. Nghiêm trọng hơn, trong `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:216-220`:
     ```kotlin
     fun setCheat(index: Int, enable: Boolean, code: String) {
         runOnEmulationThread(true) {
             LibretroDroid.setCheat(index, enable, code)
         }
     }
     ```
     Trong khi `resetCheat()` (`:222`) dùng `EmulationCall.post(deliverToGlThread)` (bất đồng bộ, không chặn Main Thread), thì `setCheat()` là hàm `Unit` duy nhất còn sót lại vẫn gọi `runOnEmulationThread(true)` — tức là **tạo `CountDownLatch(1)` và gọi `latch.await(2000ms)` trên Main UI Thread cho từng mã cheat một!**
  6. Vì `GLThread` của `melondsds` đang chạy `step()` tốn ~16,7 ms mỗi khung (hoặc lâu hơn khi biên dịch JIT/shader đầu màn), mỗi lệnh `setCheat` đồng bộ trên Main Thread phải xếp hàng sau một nhịp `onDrawFrame()` của `GLThread`. Chỉ cần 300 mã trong danh sách × 16,7 ms = **5.010 ms chặn cứng Main UI Thread ngay tại `first-frame`**, kích hoạt ANR Input Dispatching tức thì!
  7. Ngoài ra, ở tầng C++ (`libretrodroid/src/main/cpp/libretrodroid.cpp:705-717`), `LibretroDroid::resetCheat()` và `LibretroDroid::setCheat()` gọi thẳng `core->retro_cheat_reset()` / `core->retro_cheat_set(...)` mà **thiếu guard `if (!core) return;`**, dễ gây `SIGSEGV` nếu lệnh chạy trước khi `core` khởi tạo xong hoặc sau khi `destroy()`.

##### Thủ phạm #2 (Chặn Main Thread không giới hạn ở `GLSurfaceView.onPause()` — khớp `#318`, 177/421 lượt)
- **Bằng chứng `tệp:dòng`:**
  1. `GLRetroView.kt:369-376`: `RenderLifecycleObserver.pause()` đăng ký làm `LifecycleObserver` của `RetroActivity`. Trong AndroidX Lifecycle, sự kiện `ON_PAUSE` được phát tới các observer từ `ReportFragment.onActivityPrePaused()` — nghĩa là **`GLRetroView.pause()` chạy TRƯỚC cả dòng đầu tiên của `RetroActivity.onPause()` (`RetroActivity.kt:671`)**.
  2. Dòng `GLRetroView.kt:371` gọi `super<GLSurfaceView>.onPause()`, đi vào `GLThread.onPause()` giữ khóa `sGLThreadManager` và gọi `sGLThreadManager.wait()` **không có timeout** trên Main UI Thread cho đến khi `GLThread` thoát khỏi `onDrawFrame()` hoặc `onSurfaceCreated()` và đặt `mPaused = true`.
  3. Với `melondsds`, luồng `GLThread` có thể giữ `onDrawFrame()` / `onSurfaceCreated()` vượt quá 5 giây trong 2 trường hợp:
     - Nạp ROM NDS lớn / giải nén / khởi tạo BIOS & firmware trong `initializeCore()`.
     - Trong `retro_run()`, `melondsds` đồng bộ với luồng dựng hình phụ `melonds_threaded_renderer` hoặc biên dịch khối lệnh JIT ARM9/ARM7 đúng lúc hệ điều hành thu hồi CPU khi chuyển app xuống nền.

##### Thủ phạm #3 (Cộng dồn thời gian chờ lưu SRAM khi thoát game — `RetroActivity.kt:680, 693, 713`)
- Khi người dùng bấm Thoát hoặc vuốt đóng app, Main UI Thread phải chịu chuỗi chờ tuần tự:
  `GLSurfaceView.onPause()` (`GLRetroView.kt:371`) + `serializeSRAMOrNull(300ms)` (`RetroActivity.kt:680` hoặc `:713`) + `runBlocking { withTimeoutOrNull(1500) { sramJob?.join() } }` (`RetroActivity.kt:693`).
- Nếu `onPause()` đã tốn 3,5 giây mà phần lưu SRAM trên I/O chậm tốn thêm 1,8 giây, tổng thời gian chặn Main Thread vượt ngưỡng 5 giây ANR. (Hiện tại ở `RetroActivity.kt:676`, `saveSRAMAsync()` đã được gọi trong `onPause()`, nhưng `onDestroy()` dòng `713` lại gọi tiếp `retroView.serializeSRAMOrNull()` lần thứ hai nếu `sramJob` chưa kịp xong).

#### 3.4. Dữ liệu nào còn thiếu (`[CHƯA KIỂM]`) và cách lấy ngay không cần sửa app
- **Còn thiếu `[CHƯA KIỂM]`:**
  1. Giá trị trường `stage` (`init`, `load-game`, `first-frame`, `running`, `pause`) và 3 dòng đầu của luồng `main` / `GLThread` trong mẫu báo cáo thực tế trên Cloudflare Worker của `#350` và `#318` (hiện các báo cáo `docs/loi/melondsds-anr-1311.md` và `1313.md` chưa chạy được `scripts/crash-reports.sh detail` vì môi trường agent không có `CRASH_ADMIN_TOKEN`).
  2. Phân phối thời gian thực tế (ms) của từng bước `t0 → t9` trong `pause()` và `onDestroy()` trên máy người dùng.
- **Cách lấy ngay (0 rủi ro):**
  1. **PM gộp PR `#660`** (`Mở rộng crash-insight để in chi tiết top 12 lỗi có nhiều lượt nhất`) và bấm chạy `workflow_dispatch` của `.github/workflows/crash-insight.yml`: GitHub Actions có sẵn `CRASH_ADMIN_TOKEN` sẽ in ngay `stage`, `device`, `android`, `breadcrumbs` và khung stack của `#350` & `#318` vào Job Summary mà không lộ token.
  2. **PM gộp PR `#656`** (`Đo thời gian từng bước dừng/hủy RetroActivity vào breadcrumb khi ANR`): khi ANR xảy ra, `PauseProbe` tự động ghi dòng `pause_probe step=... isFinishing=... t0->t1=...ms` vào breadcrumb.

---

## File cần sửa / tạo

> Để giữ mỗi PR nhỏ, dễ duyệt và dễ lùi, chia thi công V210 thành **3 PR độc lập** theo đúng thứ tự dưới đây.

### PR 1 — Vá ngay điểm nghẽn ANR Cheat (`LibretroCheats` + `GLRetroView` + `libretrodroid.cpp`) & Sửa bộ giải ký hiệu `crash-report-detail.py`

| Tệp | Hành động | Mô tả chi tiết cho phiên Thi công / Lỗi |
|---|---|---|
| `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt` | Sửa dòng `216-220` | Đổi `setCheat` từ `runOnEmulationThread(true)` (chặn Main Thread bằng `CountDownLatch.await(2000ms)`) sang `EmulationCall.post(deliverToGlThread)` giống hệt `resetCheat()` ở dòng `222-226`. |
| `app/src/main/java/vn/aow/monika/cheat/LibretroCheats.kt` | Sửa dòng `4-12` | (1) Khi `turnedOff || previous.isEmpty()`, sau khi gọi `v.resetCheat()`, **chỉ gọi `v.setCheat(i, true, e.code)` cho các mục có `e.enabled == true`** (không gửi hàng trăm mã `enabled == false` xuống JNI).<br>(2) Khi `previous.isNotEmpty() && !turnedOff`, chỉ gọi `v.setCheat(i, true, e.code)` cho các mục đang `e.enabled == true` mà ở `previous` chưa bật hoặc đổi mã. |
| `libretrodroid/src/main/cpp/libretrodroid.cpp` | Sửa dòng `705-717` | Thêm `if (!core) return;` ở đầu hàm `LibretroDroid::resetCheat()` và `LibretroDroid::setCheat(...)` (dưới `std::lock_guard<std::mutex> lock(coreLock)`), đồng bộ với `unserializeState` / `unserializeSRAM`. |
| `scripts/crash-report-detail.py` | Sửa dòng `65-88` | (1) Đổi `if report.get("kind") != "native": return` thành `if report.get("kind") not in ("native", "anr"): return`.<br>(2) Hỗ trợ cả 2 biểu thức chính quy:<br>- Dạng thô: `#\d+\s+pc\s+([0-9a-fA-F]+)\s+(\S+).*?\(BuildId:\s*([0-9a-fA-F]+)\)`<br>- Dạng `TombstoneParser` (`TombstoneParser.kt:153-155`): `#\d+\s+(\S+?)\+0x([0-9a-fA-F]+)\b.*?\[build_id\s+([0-9a-fA-F]+)\]` |
| `scripts/test-crash-symbols.py` | Sửa | Thêm ca kiểm tự động cho dòng khung dạng `TombstoneParser` (`#00 ppsspp_libretro_android.so+0x1a2b3c ... [build_id ...]`) và báo cáo `kind = "anr"`. |

**Mẫu mã thay thế cho `LibretroCheats.kt`:**
```kotlin
package vn.aow.monika.cheat

import com.swordfish.libretrodroid.GLRetroView

object LibretroCheats {
    fun apply(v: GLRetroView, list: List<CheatEntry>, previous: List<CheatEntry> = emptyList()) {
        val turnedOff = previous.any { prev ->
            prev.enabled && list.none { it.id == prev.id && it.enabled && it.code == prev.code }
        }
        if (turnedOff || previous.isEmpty()) {
            if (turnedOff || list.any { it.enabled }) {
                runCatching { v.resetCheat() }
            }
            list.forEachIndexed { i, e ->
                if (e.enabled) runCatching { v.setCheat(i, true, e.code) }
            }
            return
        }
        val prevById = previous.associateBy { it.id }
        list.forEachIndexed { i, e ->
            if (!e.enabled) return@forEachIndexed
            val old = prevById[e.id]
            if (old == null || !old.enabled || old.code != e.code) {
                runCatching { v.setCheat(i, true, e.code) }
            }
        }
    }
}
```

### PR 2 — Chuẩn hóa công cụ dựng & đăng gói cho Đợt 1 (`melondsds`, `desmume`, `pcsx_rearmed`, `snes9x`)

| Tệp | Hành động | Mô tả chi tiết cho phiên Thi công |
|---|---|---|
| `engines/sources.lock.json` | Sửa dòng `113-173` | Thêm công thức `"desmume"` (`repo: "https://github.com/libretro/desmume.git"`, `subdir: "desmume/src/frontend/libretro/jni"`, `build_system: "ndk-build"`, `abis: ["arm64-v8a", "armeabi-v7a"]`). Thay thế `"PIN_COMMIT_BEFORE_RELEASE"` của `snes9x`, `pcsx_rearmed`, `melondsds`, `desmume` bằng SHA-1 commit 40 ký tự thực tế của nhánh ổn định trên GitHub Libretro. |
| `scripts/prepare-core-build.py` | Sửa dòng `132-168, 336-340, 395-406` | (1) Thêm hàm `recipe_desmume` vào `RECIPES` (`ndk-build` tại `desmume/src/frontend/libretro/jni`).<br>(2) Trong `build_ndk_core()`, truyền thêm `APP_CFLAGS=-g -O2`, `APP_CPPFLAGS=-g -O2`, `APP_LDFLAGS=-Wl,--build-id=sha1` để bản `obj/local/<abi>/libretro.so` (`symbol_library`) luôn có đủ DWARF `.debug_info` và `BuildId` SHA-1.<br>(3) Bỏ điều kiện `if args.core == "ppsspp":` ở dòng `395` — gọi `require_debug_symbols(symbol_library)` cho **mọi lõi**.<br>(4) Chạy `llvm-strip --strip-unneeded` trên `built_library` cho **mọi lõi** (đảm bảo `built_library` khác đường dẫn với `symbol_library`; với CMake như `melondsds`/`mgba`/`ppsspp` đã tách `.unstripped.so`). |
| `scripts/ppsspp-snapshot.py` | Sửa | Thêm `--core <core_id>` (mặc định `"ppsspp"`), đọc `abis = lock["cores"][core]["abis"]`, cập nhật `config["cores"][core]["url"]` (trỏ URL `arm64-v8a` đã ghim) và `config["cores"][core]["artifacts"][abi]` (`url`, `sha256`, `size`, `version`). |
| `scripts/package-native-symbols.py` | Sửa dòng `67-93` | Trong `SOURCE.txt` đóng gói kèm `<core>-<abi>.zip`, ghi thêm dòng `Public Source Archive (GPL): https://github.com/aowvn-10diem/aowvn-monika/releases/download/core-sources-1/sources-<core>-<abi>.zip` để đáp ứng nghĩa vụ GPL khi `aowvn-monika-packs` chuyển sang Private (`[A70]`). |
| `.github/workflows/publish-ppsspp.yml` | Sửa | Thêm input `core` (`choice`: `ppsspp`, `melondsds`, `desmume`, `pcsx_rearmed`, `snes9x`, `mgba`, `gambatte`, `fceumm`; mặc định `ppsspp`). Dựng ma trận ABI theo `sources.lock.json` của `core` được chọn; đăng `<core>-<abi>.zip`, `symbols-<core>-<abi>.zip`, `sources-<core>-<abi>.zip` lên `aowvn-monika-packs` (tag `cores-<core>-<n>`); đồng thời đăng bản sao `sources-<core>-<abi>.zip` lên tag `core-sources-1` của `aowvn-monika`. |

### PR 3 — Chạy workflow dựng 4 lõi Đợt 1 và mở PR config đổi URL

| Tệp | Hành động | Mô tả chi tiết cho PM & phiên Thi công |
|---|---|---|
| `config/monika-config.json` | Sửa tự động qua `publish-ppsspp.yml` | Cập nhật `cores.melondsds` (`cores-melondsds-1`), `cores.desmume` (`cores-desmume-1`), `cores.pcsx_rearmed` (`cores-pcsx_rearmed-1`), `cores.snes9x` (`cores-snes9x-1`) kèm `sha256`, `size`, `version` và tăng `configVersion`. |
| `cloudflare/crash-worker/worker.js` | Sửa (khi bật `[A70]`) | Thêm tên tệp gói đã ghim của 4 lõi Đợt 1 vào `PACKS_ALLOW` / `PACKS_MAP` khi đồng bộ lên bucket R2 `monika-packs`. |

---

## Cổng kiểm

### Cổng tự động
- Chạy kiểm thử đơn vị của ứng dụng:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Chạy kiểm thử đóng gói và giải ký hiệu native:
  ```bash
  python3 scripts/test-crash-symbols.py
  python3 scripts/prepare-core-build.py --list
  ```
- Sau khi workflow dựng gói chạy trên GitHub Actions, kiểm tra từng gói trên Release `aowvn-monika-packs`:
  1. Tệp `<core>-<abi>.zip` có `sha256` và `size` khớp 100% với `config/monika-config.json`.
  2. Tệp `symbols-<core>-<abi>.zip` chứa `METADATA.json` có trường `build_id` khác rỗng và tệp `.so` bên trong chứa section `.debug_info` / `.symtab`.
  3. Tệp `sources-<core>-<abi>.zip` có mặt trên cả `aowvn-monika-packs` và tag `core-sources-1` của `aowvn-monika`.

### Cổng nhìn
- Mở game NDS (`melondsds` và `desmume`), PSX (`pcsx_rearmed`), SNES (`snes9x`) trên thiết bị `arm64-v8a`:
  - Tải lõi mới thành công, kiểm tra `sha256` trong `CoreManager` không báo lỗi.
  - Với game NDS có tệp `.cht` lớn (> 300 mã), vào game (`first-frame`), bật/tắt 1 mã cheat và bấm Thoát: không còn hiện tượng khựng giao diện hay ANR.
  - Khi có báo cáo lỗi mới từ bản lõi đã ghim (`coreVersion: cores-melondsds-1` / `cores-desmume-1`), chạy `bash scripts/crash-reports.sh detail <id>` phải in ra khối `=== ĐÃ GIẢI KÝ HIỆU ===` kèm tên hàm và `tệp.cpp:dòng`.

---

## Rủi ro & cách lùi

| Rủi ro | Mức độ | Cách phòng tránh & Cách lùi |
|---|---|---|
| Công thức `ndk-build` của `desmume` hoặc `pcsx_rearmed` thiếu cờ kiến trúc hoặc báo lỗi biên dịch trên NDK r26d | Trung bình | Thử dựng trước bằng `build-cores.yml` (`workflow_dispatch`) trên nhánh PR trước khi chạy `publish-ppsspp.yml`. Nếu 1 lõi trong 4 lõi lỗi biên dịch, vẫn đăng 3 lõi kia bình thường vì tag `cores-<core>-<n>` hoàn toàn độc lập. |
| Lõi mới dựng từ nguồn có hành vi khác bản nightly cũ trên một số máy | Thấp | `CoreManager.kt:71-145` tải lõi theo `config/monika-config.json`. Nếu phát hiện lỗi hồi quy ở lõi nào, chỉ cần phát hành `configVersion + 1` lùi `cores.<id>` về URL trước đó (mất < 2 phút, không cần phát hành lại APK). |
| Nghĩa vụ GPL khi `aowvn-monika-packs` chuyển sang Private (`[A70]`) | Cao (pháp lý) | Bắt buộc giữ `SOURCE.txt` + `LICENSES/` trong `<core>-<abi>.zip` và đăng `sources-<core>-<abi>.zip` lên Release công khai `core-sources-1` của `aowvn-monika` trước hoặc cùng lúc bật `[A70]`. |
| Thay đổi `LibretroCheats.apply` làm mã cheat đang bật không được áp dụng khi nạp lại game | Thấp | Khi `previous.isEmpty()` (lần áp dụng đầu tiên tại `first-frame`), hàm mới vẫn gọi `v.setCheat(i, true, e.code)` cho mọi mã có `e.enabled == true`, chỉ bỏ qua các mã `e.enabled == false`. Nếu cần lùi, revert 1 tệp `LibretroCheats.kt`. |
