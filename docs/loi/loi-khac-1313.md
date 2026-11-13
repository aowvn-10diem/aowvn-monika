# Nhóm "lõi khác" (8 lõi, 19 issue): vì sao không chẩn đoán được, và lõi nào thật sự khác

Ngày 13/11/2026 · nhánh `chan-doan/loi-khac-1313` · nền `origin/main` `0e9ef5bd`
Phạm vi: 19 issue `crash-moi` đang mở thuộc `snes9x`, `mgba`, `gambatte`, `pcsx_rearmed`, `mkxp-z`,
`kirikiroid2-yuri`, `melonds`, `melondsds` (trừ nhóm `melondsds/anr` đã có #633).
Chỉ tài liệu — **không sửa mã**, không đụng `config/`. Mọi kết luận chưa có báo cáo gốc đã giải mã: **[CHƯA KIỂM]**.

## 0. Phần đã phủ (không lặp lại)

| Nguồn | Đã chốt | Dùng cho nhóm này thế nào |
|---|---|---|
| #622 `docs/loi/citra-native-1311.md` | Lõi citra **không ghim bản** (`version = "1"`, `nightly/latest`, không `sha256`, không symbols) | Dùng nguyên cơ chế; **mục 4 đối chiếu 6 lõi libretro còn lại** của nhóm (2 đã ghim, 4 chưa) |
| #635 `docs/loi/citra-con-lai-1313.md` | Đường phát `core-error`, 5 mã lỗi, `onCoreFailure` chỉ chạy trước khung đầu | Dẫn lại, không phân tích lại; mục 3 chỉ bổ sung phần riêng của nhóm này |
| #623 `docs/loi/ppsspp-anr-1311.md` | Nghi phạm ANR của `:libretrodroid`: `onPause()`/`onDetachedFromWindow` trên Main Thread, `#606`/`#619` đã bọc hạn mọi lời gọi từ UI | Dùng nguyên cho 4 issue `anr` (mục 6) |
| #634 `docs/loi/unknown-anr-1313.md` | `anr` không có `session.core` → `unknown`; #632 không đổi được nhãn | **Không** áp cho nhóm này: 4 issue `unknown` ở đây **có** lõi (mục 5) |
| #626 `docs/loi/op-fail-con-lai-1311.md` | 16 issue `op-fail`, 14 issue không có `Thành phần` | #464 (`mgba`), #465 (`gambatte`) đã nằm trong bảng của #626 — mục 7 chỉ nhắc, không triage lại |

## 1. Kết luận ngắn

- **19 issue · 128 lượt.** Hai lõi chiếm **90/128 lượt**: `pcsx_rearmed` 55 (2 issue) và `melondsds` 35 (3).
  Ba issue lớn nhất là `core-error`: #322 (37), #567 (21), #578 (18) = **76/128 lượt (59%)**.
- **Không có một nguyên nhân lỗi chung.** Có **một nguyên nhân chung cho việc *không chẩn đoán được***:
  `unknown` = máy không có `ApplicationExitInfo` (API < 30); `core-error` = một cửa duy nhất nhưng thân issue
  **không mang mã lỗi**; `native` = không có gói ký hiệu khớp BuildId; `anr` = không có khung.
- **`core-error` không chỉ là `retro_load_game` trả false.** Đó là mã `1`; cùng cửa đó còn `0` (không `dlopen`
  được `.so`), `2` (GLES < 3), `-1` (mọi ngoại lệ khác trong `initializeCore`). Thân issue không ghi mã ⇒
  **không thể** nói #567/#578 là "ROM hỏng" hay "lõi hỏng" khi chưa đọc `reason`/`crumbs`. [CHƯA KIỂM]
- **Ghim bản tách đôi nhóm:** `mgba` + `gambatte` **đã ghim** (`artifacts` + `sha256`, pack `cores-gb-gba-nes-1`);
  `snes9x`, `pcsx_rearmed`, `melonds`, `melondsds` **chưa** (buildbot `nightly/latest`, `version = "1"`, không hash).
  Vì vậy giả thuyết "lõi trôi bản" của #622 áp cho 4/6 lõi libretro ở đây, **không** áp cho #403 (mgba).
- **`melonds` (#563, #535) có thể là hệ quả của `melondsds` (#567)**: `onCoreFailure` mời người chơi đổi sang
  lõi dự phòng, và `systems[nds].altCores` có `melonds` — nên hai issue `melonds` có thể là *cùng một game*
  thất bại hai lần. [CHƯA KIỂM]

## 2. Bảng issue

`Lượt` = "Số lần quan sát đã thông báo" trong thân issue (cộng dồn, **không phải số người**).
`Máy` = số khoá trong `seen` (`crash-issue-bot.py:76`) — xấp xỉ số **lần gửi báo cáo khác nhau**, không phải số máy.
`Tái diễn?` = thân issue bị bot PATCH sau lần tạo, đọc bằng `(+N)` trong thân (bot chỉ PATCH khi `delta ≥ 5`,
`crash-issue-bot.py:118`). **Không** dùng `updatedAt`: bình luận cũng đổi mốc này (#322/#385/#567/#578 đã có bình
luận chẩn đoán), nên chỉ `(+N)` mới là bằng chứng.

| Issue | Lõi | Loại | Lượt | Máy | Mở ngày | Tái diễn? |
|---|---|---|---:|---:|---|---|
| [#322](https://github.com/aowvn-10diem/aowvn-monika/issues/322) | `pcsx_rearmed` | `core-error` | 37 (+10) | 13 | 20/10 | **Có** (gộp 07/11) |
| [#578](https://github.com/aowvn-10diem/aowvn-monika/issues/578) | `pcsx_rearmed` | `core-error` | 18 (+18) | 4 | 09/11 | Không (18 lượt ngay lần tạo) |
| [#567](https://github.com/aowvn-10diem/aowvn-monika/issues/567) | `melondsds` | `core-error` | 21 (+21) | 6 | 09/11 | Không (21 lượt ngay lần tạo) |
| [#397](https://github.com/aowvn-10diem/aowvn-monika/issues/397) | `melondsds` | `unknown` | 13 (+12) | 7 | 31/10 | **Có** (gộp 02/11) |
| [#482](https://github.com/aowvn-10diem/aowvn-monika/issues/482) | `melondsds` | `unknown` | 1 (+1) | 1 | 03/11 | Không |
| [#385](https://github.com/aowvn-10diem/aowvn-monika/issues/385) | `kirikiroid2-yuri` | `native` | 13 (+5) | 8 | 30/10 | **Có** (gộp 02/11) |
| [#563](https://github.com/aowvn-10diem/aowvn-monika/issues/563) | `melonds` | `native` | 3 (+3) | 2 | 09/11 | Không |
| [#459](https://github.com/aowvn-10diem/aowvn-monika/issues/459) | `snes9x` | `anr` | 5 (+5) | 3 | 02/11 | Không |
| [#448](https://github.com/aowvn-10diem/aowvn-monika/issues/448) | `snes9x` | `native` | 2 (+2) | 2 | 02/11 | Không |
| [#418](https://github.com/aowvn-10diem/aowvn-monika/issues/418) | `snes9x` | `core-error` | 1 (+1) | 1 | 01/11 | Không |
| [#403](https://github.com/aowvn-10diem/aowvn-monika/issues/403) | `mgba` | `core-error` | 4 (+4) | 3 | 31/10 | Không |
| [#516](https://github.com/aowvn-10diem/aowvn-monika/issues/516) | `mgba` | `anr` | 1 (+1) | 1 | 05/11 | Không |
| [#464](https://github.com/aowvn-10diem/aowvn-monika/issues/464) | `mgba` | `op-fail` | 1 (+1) | 1 | 03/11 | Không (đã ở #626) |
| [#378](https://github.com/aowvn-10diem/aowvn-monika/issues/378) | `mgba` | `unknown` | 1 (+1) | 1 | 28/10 | Không |
| [#402](https://github.com/aowvn-10diem/aowvn-monika/issues/402) | `gambatte` | `anr` | 2 (+2) | 2 | 31/10 | Không |
| [#513](https://github.com/aowvn-10diem/aowvn-monika/issues/513) | `gambatte` | `unknown` | 2 (+2) | 2 | 05/11 | Không |
| [#465](https://github.com/aowvn-10diem/aowvn-monika/issues/465) | `gambatte` | `op-fail` | 1 (+1) | 1 | 03/11 | Không (đã ở #626) |
| [#535](https://github.com/aowvn-10diem/aowvn-monika/issues/535) | `melonds` | `anr` | 1 (+1) | 1 | 08/11 | Không |
| [#613](https://github.com/aowvn-10diem/aowvn-monika/issues/613) | `mkxp-z` | `native` | 1 (+1) | 1 | 12/11 | Không |
| **Tổng** | **8 lõi** | | **128** | | | 3 issue tái diễn |

**Theo lõi**

| Lõi | Số issue | Lượt | Loại có mặt | Ghim bản? |
|---|---:|---:|---|---|
| `pcsx_rearmed` | 2 | **55** | core-error | **Không** |
| `melondsds` | 3 | **35** | core-error, unknown | **Không** |
| `kirikiroid2-yuri` | 1 | 13 | native | Có (`engines-kirikiri-16`) |
| `snes9x` | 3 | 8 | anr, native, core-error | **Không** |
| `mgba` | 4 | 7 | core-error, anr, op-fail, unknown | Có (`cores-gb-gba-nes-1`) |
| `gambatte` | 3 | 5 | anr, unknown, op-fail | Có (`cores-gb-gba-nes-1`) |
| `melonds` | 2 | 4 | native, anr | **Không** |
| `mkxp-z` | 1 | 1 | native | Có (`engines-rgss-6`) |

**Theo loại**

| Loại | Số issue | Lượt | Cửa sinh báo cáo |
|---|---:|---:|---|
| `core-error` | 5 | **81** | `RetroActivity` → `Diagnostics.recordCoreFailure` (mục 3) |
| `native` | 4 | 19 | `REASON_CRASH_NATIVE` → `Diagnostics.collect` |
| `unknown` | 4 | 17 | `exitInfo == null` → `Diagnostics.collect` (mục 5) |
| `anr` | 4 | 9 | `REASON_ANR` → `Diagnostics.collect` (mục 6) |
| `op-fail` | 2 | 2 | `Diagnostics.opFail` (đã phủ ở #626) |

## 3. `core-error` sinh ở đâu, và vì sao không quy được nguyên nhân

### 3.1 Đường phát (một cửa duy nhất, đã dẫn ở #635 — chỉ chép lại mốc)

1. `initializeCore()` gọi `createCore()` → `loadGameFromPath` → `afterGameLoad` → `LibretroDroid.onSurfaceCreated()`
   (`libretrodroid/.../GLRetroView.kt:403-431`).
2. Ngoại lệ ở bất kỳ bước nào → `catchExceptions` phát `e.errorCode`, lỗi khác → `ERROR_GENERIC`
   (`GLRetroView.kt:450-459`).
3. `RetroActivity` thu mã: `Diagnostics.stage("core-error-$code")` rồi
   `onCoreFailure("lõi báo lỗi mã $code", "error code=$code core=$coreId")` (`app/.../runner/RetroActivity.kt:367-374`).
4. `onCoreFailure` **thoát ngay** nếu `failureHandled || loadingGone` (`RetroActivity.kt:190`) ⇒ chỉ lỗi **trước
   khung đầu** mới thành báo cáo.
5. `Diagnostics.recordCoreFailure` đặt `kind = "core-error"` (`app/.../diag/Diagnostics.kt:605-615`).

**Không có producer nào khác** cho `kind = "core-error"`: `git grep recordCoreFailure` chỉ ra một chỗ gọi.

### 3.2 Năm mã, và mã nào sinh được ở nhánh này

| Mã | Ý nghĩa | Sinh ở | Sinh được trước khung đầu? |
|---:|---|---|---|
| `0` | `ERROR_LOAD_LIBRARY` | `create()` lỗi, `.so` thiếu/hỏng (`libretrodroidjni.cpp:415-420`) | **Có** |
| `1` | `ERROR_LOAD_GAME` | `retro_load_game()` trả `false` (`libretrodroid.cpp:366-371` → `libretrodroidjni.cpp:441-443`) | **Có** |
| `2` | `ERROR_GL_NOT_COMPATIBLE` | bật HW mà GLES < 3 (`libretrodroid.cpp:331-332`) | **Có** |
| `3` | `ERROR_SERIALIZATION` | `saveState`/`loadState` (`libretrodroidjni.cpp:190,211,284`) | Không (sau khung đầu) |
| `-1` | `ERROR_GENERIC` | mọi `Exception` khác trong `initializeCore` | **Có** |

Vì vậy câu "core-error chỉ là `retro_load_game` trả false" **chỉ đúng với mã 1**. Trong nhóm này `0` và `-1`
là hai khả năng thật: `0` khi gói lõi tải về hỏng (mục 4), `-1` khi `afterGameLoad`/`onSurfaceCreated` ném lỗi
(ví dụ dựng Video/âm thanh) — cả hai đều hiện lên người chơi y như "lõi không hợp".

### 3.3 Hai giả thuyết đã **loại trừ** (để lượt sau khỏi thử lại)

- **SRAM hỏng không sinh `core-error`.** `unserializeSRAM` chỉ `return false` khi `retro_get_memory_data` null
  hoặc `size > sramSize`, **không ném** ngoại lệ (`libretrodroid/src/main/cpp/libretrodroid.cpp:164-181`);
  `RetroActivity` lại nạp SRAM *sau* `retro_load_game`. Nên save cũ/hỏng không thể tạo 55 lượt `pcsx_rearmed`.
- **Máy GLES 2.0 không sinh `core-error` cho 5 lõi này.** `useHWAcceleration` mặc định `false`
  (`libretrodroid/src/main/cpp/environment.h:138`) và chỉ bật khi lõi gọi `SET_HW_RENDER`; `snes9x`, `mgba`,
  `gambatte`, `pcsx_rearmed` là lõi video mềm ⇒ mã `2` không áp. `melondsds` để `melonds_render_mode = software`
  (`config:1129-1150`, dòng `melonds_sysfile_mode` ở `:1137`) nên cũng không. [CHƯA KIỂM] với máy bật HW thủ công.

### 3.4 Vì sao thân issue không trả lời được

- Bot băm chữ ký từ `lõi + loại + ≤3 khung` (`crash-issue-bot.py:69-71`); mã lỗi **không** vào chữ ký, cũng
  không vào thân issue. Mã chỉ nằm trong `reason` (`"lõi báo lỗi mã N"`, `Diagnostics.kt:611`) và vệt
  `core-error-N` (`RetroActivity.kt:368`) của báo cáo gốc.
- **App không kiểm ROM trước khi nạp.** `RomSniff.accepts` chỉ soi header cho `md`/`gen`
  (`app/.../library/RomSniff.kt:11-13`); mọi đuôi khác được nhận thẳng. Nên một `.gba`/`.nds`/`.sfc` hỏng,
  cắt cụt hay đổi tên từ `.zip` đi thẳng vào `retro_load_game` → `false` → `core-error` mã 1, **không phân biệt
  được** với lỗi lõi. Đây là lý do gốc khiến nhóm này là "bãi rác" chẩn đoán, không phải một bug.

## 4. Ghim bản và ký hiệu: 4/6 lõi libretro vẫn trôi

Bằng chứng đọc từ `origin/main:config/monika-config.json` (configVersion 63) + metadata buildbot hôm nay:

| Lõi | `artifacts` + `sha256`? | Nguồn | Hệ quả |
|---|---|---|---|
| `mgba`, `gambatte` | **Có**, 4 ABI, pack `cores-gb-gba-nes-1` | GitHub Releases (bất biến) | Hai máy cùng nhãn chạy **cùng** `.so`; sửa được theo phiên bản |
| `snes9x` | **Không**, `version = "1"` | `buildbot…/nightly/android/latest/{abi}` | `isReady` chỉ so chuỗi `"1"` (`CoreManager.kt:53-58`) ⇒ máy đã cài **không bao giờ** tải lại; máy mới cài nhận bản khác |
| `pcsx_rearmed` | **Không**, `version = "1"` | buildbot | như trên |
| `melonds`, `melondsds` | **Không**, `version = "1"` | buildbot | như trên |
| `kirikiroid2-yuri` | **Có** (`modules.kirikiri.version = 6e61ce3-aow3`, `sha256`) | packs `engines-kirikiri-16` | Ổn định, có ký hiệu (mục 7.6) |
| `mkxp-z` | **Có** (`modules.rgss.version = engines-rgss-6`, `sha256`) | release `engines-rgss-6` | Ổn định, **không** có ký hiệu (mục 7.5) |

Hai số đo cho thấy lõi trôi thật, không phải suy đoán:

- `HEAD https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/snes9x_libretro_android.so.zip`
  → `890.362 B`, `Last-Modified: Fri, 13 Nov 2026 06:24:10 GMT`.
- `…/melondsds_libretro_android.so.zip` → `2.003.724 B`, `Last-Modified: Thu, 12 Nov 2026 06:23:16 GMT`.

Nghĩa là **hôm qua và hôm nay buildbot đã thay `.so`**, trong khi app vẫn ghi nhãn `"1"` cho cả hai. Hai máy
cùng nhãn `1` có thể chạy hai binary khác nhau — đúng cơ chế #622 đã nêu cho `citra`, nay áp cho 4 lõi này.

Nhánh tải **không ghim** cũng không kiểm gì: nó lấy entry `.so` đầu tiên trong ZIP rồi `renameTo`
(`CoreManager.kt:107-141`); chỉ nhánh `artifacts` mới kiểm `sha256` (`CoreManager.kt:144-146`) và ELF/ABI
(`PackTransaction.validate`, `CoreManager.kt:166`). Vì vậy một ZIP buildbot cắt cụt vẫn được nhận → `core-error` mã `0`.

**Ký hiệu native**: ngoài 2 gói engine, repo **không có** gói symbols nào cho lõi libretro
(`gh release list` 52 bản: chỉ `symbols-kirikiri-16-arm64-v8a`; `engines-rgss-6/7` chỉ có
`rgss-arm64-v8a.zip` + `.sha256`; không có bản nháp nào). ZIP buildbot chỉ 890 KB (`snes9x`) và 2,0 MB
(`melondsds`) — cỡ của `.so` đã lược bảng ký hiệu, nên dù tải đúng bản cũng không có gì để ghép. ⇒ `native` của
`snes9x` (#448) và `melonds` (#563) **không thể** giải mã khung. Việc tự dựng lõi + ký hiệu đã nằm trong đặc tả
**V198** (`docs/dac-ta/V198-tu-dung-loi.md`, mục 3) — lượt này **không** đề xuất lại, chỉ ghi nhận V198 **chưa**
triển khai cho 6 lõi trong nhóm (chưa có lõi nào do Monika tự dựng được phát hành).

## 5. `unknown` (4 issue, 17 lượt): một nguyên nhân duy nhất, không phải lỗi lõi

- `kind = "unknown"` chỉ sinh ở **một chỗ**: `exit == null` trong `Diagnostics.collect`
  (`app/.../diag/Diagnostics.kt:232-241`).
- `exitInfo` trả `null` khi `Build.VERSION.SDK_INT < 30` (`Diagnostics.kt:277-282`) hoặc Android không còn bản
  ghi. `minSdk = 26` (`app/build.gradle.kts:40`) ⇒ **Android 8/9/10 nằm trong phạm vi hỗ trợ** và mọi lần chết
  của tiến trình game trên các máy đó đều thành `unknown`.
- Vì sao vẫn có lõi: `collect()` đọc **một** tệp phiên dùng chung `diag/active-session.json`
  (`Diagnostics.kt:138,198`), do tiến trình game ghi; tiến trình chính mở lại app thì `s.pid != myPid` nên báo
  cáo có `session` → bot lấy được `core` (`crash-issue-bot.py:69`).
- Hệ quả: `unknown` + có lõi **không** nói lõi hỏng — nó có thể là native, ANR, hết RAM, hoặc người chơi thoát
  bất thường, tất cả bị gộp. Không có `reason`, không có `trace`, `log` của pid đã chết thường rỗng.
- Khác hẳn #634: bên đó `unknown` là **thiếu `session.core`** (ANR ở tiến trình chính). Ở đây `core` có mà
  `reason` không có ⇒ hai nhóm khác nguyên nhân, đừng gộp.
- Việc đúng: **không hành động** trên #378/#513/#482/#397; chỉ ghi nhận "chờ bản app có thêm stage/`exitInfo`
  dự phòng". Muốn phân loại lại phải có `device` (đời máy/API) từ Worker — cần `CRASH_ADMIN_TOKEN`. [CHƯA KIỂM]

## 6. `anr` (4 issue, 9 lượt): cùng lớp #623, và đã có bản sửa sau mốc của cả 4

- Cơ chế đã phân tích ở #623: `GLSurfaceView.onPause()` trên Main Thread chờ GLThread
  (`GLRetroView.kt:366-373`), `LibretroDroid.pause()` chờ `coreLock` mà `step()` giữ suốt khung
  (`libretrodroid.cpp:519-566`); đóng game thì `onDetachedFromWindow → requestExitAndWait` + chờ SRAM 1,5 s.
  Bốn issue này đều chạy `RetroActivity` trong tiến trình `:game` (`app/src/github/AndroidManifest.xml:81-82`) trên
  cùng `GLRetroView` nên **không có cơ chế riêng** — không lặp lại phân tích.
- Mốc thời gian là bằng chứng mạnh nhất ở đây: `#606` vào main **11/11 23:16** (`833a4c0d`), `#619` vào main
  **13/11 15:15** (`76492b6a`); v0.8.13 phát hành **13/11 15:46** (giờ VN) — tức chỉ 31 phút sau #619, nên
  **nhiều khả năng có cả hai bản sửa** [CHƯA KIỂM: nếu tag v0.8.13 tạo trước mốc #619 thì chỉ có #606].
  Cả 4 issue `anr` đều **mở trước** mốc #606 và **không** được bot cộng lượt lần nào sau đó:

| Issue | Mở | Lượt cuối | Trước #606? |
|---|---|---|---|
| #402 `gambatte` | 31/10 | 31/10 (2 lượt cùng ngày) | Có |
| #459 `snes9x` | 02/11 | 02/11 (5 lượt cùng ngày) | Có |
| #516 `mgba` | 05/11 | 05/11 | Có |
| #535 `melonds` | 08/11 | 08/11 | Có |

- `#535` còn được **nêu đích danh** trong mã: chú thích `runBounded` ở `GLRetroView.kt:464-488` liệt kê
  `(ffdfa861, #318, #529, #533, #535)` là các chữ ký ANR mà hạn chờ 2 000 ms nhắm tới ⇒ #535 đã được xử lý theo
  thiết kế, không phải ca bỏ sót.
- Kết luận: **không mở việc mới** cho 4 issue này. Chỉ theo dõi sau v0.8.13 qua ≥ 2 bản; nếu có lượt mới thì
  mới cần `adb bugreport` (đúng cách #623 mục 5). [CHƯA KIỂM] vì chưa có bugreport nào.

## 7. Từng lõi: nghi phạm riêng, xếp theo số lượt có thể giải thích

### 7.1 `pcsx_rearmed` — #322 (37) + #578 (18) = 55 lượt

Hai issue cùng `core-error`, cùng lõi, khác chữ ký (nên khác khung/khác đuôi tệp). #322 sống 3 tuần và tái diễn
⇒ nhiều khả năng **một lớp game** thất bại ổn định, không phải lỗi mạng nhất thời. Ba nghi phạm:

| # | Nghi phạm | Vì sao | Cách kiểm |
|---|---|---|---|
| 1 | Đuôi trong `systems[ps1].extensions` mà lõi không đọc được: `cue`, `pbp`, `chd`, `m3u` (`config`, hệ `ps1`) | App không kiểm nội dung; `EntryPick` mặc định `"first"` (`MonikaConfig.kt:293`) nên với bộ `Game.cue` + `Game.m3u` nó có thể chọn `.cue` của **đĩa 1** | Cài 4 game mẫu (mỗi đuôi một cái), xem cái nào ra `core-error` mã 1 |
| 2 | Bộ đĩa nhiều tệp thiếu track | `Importer.importFile` chỉ giải nén nguyên cây (`Importer.kt:47-70`); người chơi tải lẻ `.cue` (không kèm `.bin`) vẫn được nhập, `.cue` trỏ tới tệp không tồn tại ⇒ `retro_load_game = false` | Đọc `detail`/`crumbs` của #322; hoặc tự tạo `.cue` mồ côi |
| 3 | Không có BIOS trong `systemDir` | `cores.pcsx_rearmed` **không** khai `systemFiles` (chỉ `ppsspp` có), nên không có luồng tải BIOS | Thử máy sạch: xoá `files/system`, mở 1 game PS1 |

Cả ba đều **[CHƯA KIỂM]**. Điểm cần nhớ: (3) **không** giải thích được một mình nếu lõi dùng HLE BIOS; chỉ (1)/(2)
mới cho lỗi ổn định theo *từng game*.

### 7.2 `melondsds` — #567 (21) + #397 (13) + #482 (1)

- #567 là **burst**: 21 lượt / 6 lần gửi, toàn bộ trong cửa sổ đầu (09/11), không tái diễn. Kiểu này khớp với
  "một bản phát hành đổi hành vi rồi tự hết", hoặc một nhóm game NDS cùng đuôi. `config` bật cho lõi này
  `melonds_jit_enable`, `melonds_sysfile_mode = builtin`, `melonds_render_mode = software`
  (`config:1129-1150`) — `builtin` nghĩa là **không** cần firmware ngoài, nên nghi phạm "thiếu sysfile" bị loại.
- Nghi phạm còn lại: `.nds` cắt cụt/trim hỏng (mục 3.4); hoặc `melondsds` chỉ khai `arm64-v8a`
  (`config:1129-1150`) nên máy `armeabi-v7a` **không** vào được lõi này — nhưng khi đó `CoreManager` báo lỗi qua
  `op-fail`, không phải `core-error` (đúng như #464/#465). [CHƯA KIỂM]
- Hai issue `unknown` (#397, #482) là mục 5, **không** phải lỗi lõi.

### 7.3 `melonds` — #563 (native, 3) + #535 (anr, 1)

`melonds` **không** phải lõi mặc định của NDS (`systems[nds].core = melondsds`, `config` hệ `nds`). Nó chỉ được
dùng khi người chơi đổi tay, hoặc khi `onCoreFailure` mời đổi sang lõi dự phòng:
`altCores` của NDS có `["melondsds","desmume","melonds"]` và nút "Đổi sang lõi …" gọi `start(...)` lại
(`RetroActivity.kt:197-209`). Vì vậy **#563/#535 có thể là cùng những game của #567** — một lần thất bại của
`melondsds` sinh ra `core-error`, người chơi bấm đổi lõi, rồi `melonds` sập. Đây là giả thuyết **liên nhóm**, không
phải hai lỗi độc lập. [CHƯA KIỂM] (cần `device`/`game` của 3 báo cáo #563 để đối chiếu với #567).

### 7.4 `snes9x` — #459 (anr, 5) + #448 (native, 2) + #418 (core-error, 1)

Ba loại trên **một** lõi, 8 lượt. `snes9x` không ghim bản (mục 4) và không có ký hiệu ⇒ #448 không giải mã được;
#459 thuộc lớp ANR mục 6; #418 là `core-error` 1 lượt — dưới ngưỡng bất kỳ kết luận nào. Không có `options` lạ:
`config` chỉ đặt `snes9x_reduce_sprite_flicker = enabled` + `shader = sharp`. Kết luận: **không hành động riêng**;
chờ gói ghim bản + ký hiệu của V198.

### 7.5 `mkxp-z` — #613 (native, 1)

- `modules.rgss` **ghim** (`engines-rgss-6`, `sha256`) nên bản lõi ổn định.
- Nhưng **không có gói ký hiệu**: release `engines-rgss-6` và `engines-rgss-7` chỉ có
  `rgss-arm64-v8a.zip` + `.sha256`; `build-rgss.yml:174-196` tạo `symbols-rgss-*` dạng **nháp** (cổng giấy phép
  G8) và hiện **không** có bản nháp nào (`gh api …/releases?per_page=100` lọc `draft == true` → rỗng).
  ⇒ #613 **không** retrace được hôm nay.
- Thêm một lệch nhỏ: `config` ghim `engines-rgss-6` trong khi repo đã có `engines-rgss-7` — khi bật ký hiệu phải
  đối chiếu đúng bản **đang cài** (6), không lấy bản 7.
- 1 lượt/1 báo cáo ⇒ chưa đủ để nói gì về nguyên nhân. [CHƯA KIỂM]

### 7.6 `kirikiroid2-yuri` — #385 (native, 13, tái diễn)

- Đây là issue **giàu bằng chứng nhất** của nhóm: lõi ghim bản (`engines-kirikiri-16`, `sha256`), và **có gói ký
  hiệu** `symbols-kirikiri-16-arm64-v8a.zip` (68.479.931 B, đính cả ở release packs `engines-kirikiri-16`).
  `build-kirikiri.yml:215-221` bắt buộc đính ZIP ký hiệu vào chính bản phát hành gói ("thiếu ZIP thì KHÔNG phát
  hành gói").
- Nghĩa là 8 lần gửi/13 lượt của #385 **có thể giải mã ngay** bằng một lệnh, không cần chờ ai:
  `CRASH_ADMIN_TOKEN=… scripts/crash-reports.sh <id> --symbols-dir <giải nén symbols-kirikiri-16> --symbolizer <llvm-symbolizer>`
  (chỉ ghép khung có PC + BuildId — `scripts/crash-reports.sh` dòng hướng dẫn đầu tệp).
- Vì tái diễn sau 2 ngày và cùng một bản lõi, đây là issue **nên** làm trước cả #322: chi phí thấp nhất, khả năng
  ra kết luận cao nhất.

### 7.7 `mgba` / `gambatte` — 12 lượt, 7 issue

Hai lõi **đã ghim** và đơn giản nhất trong nhóm, nhưng rải 7 issue/4 loại. Điều này tự nó là bằng chứng **phản**
giả thuyết "một nguyên nhân chung": nếu lỗi nằm ở lõi thì hai lõi ổn định nhất phải sạch. Thực tế:

- #403 `core-error` 4 lượt/3 lần gửi: `mgba` đã ghim ⇒ không phải "trôi bản"; nghi phạm còn lại là tệp `.gba`
  hỏng/đổi tên (mục 3.4) hoặc mã `-1` từ `afterGameLoad`. [CHƯA KIỂM]
- #378/#513 `unknown`: mục 5. #402 `anr`: mục 6. #464/#465 `op-fail`: đã ở #626 (lỗi tải/cài lõi, không phải lõi).

## 8. Việc tiếp theo (rẻ → đắt)

1. **Giải mã #385 trước** (rẻ nhất, đủ điều kiện): tải `symbols-kirikiri-16-arm64-v8a.zip`, chạy
   `crash-reports.sh` cho 8 báo cáo, ghi khung vào issue. Nếu khung nằm trong `libkrkr2yuri.so` thì mở PR mã.
2. **Đọc `reason` + `crumbs` của 3 issue `core-error` lớn** (#322, #567, #578) để tách mã `0/1/-1` — mỗi mã là
   một hướng sửa khác nhau. Cần `CRASH_ADMIN_TOKEN`; phiên này **không có**. Ghi mã vào bảng mục 2 khi có.
3. **A/B đuôi PS1** (nghi phạm 7.1-1): 4 game mẫu `cue`/`pbp`/`chd`/`m3u` trên một máy. Nếu `chd`/`pbp` fail
   ổn định thì sửa rẻ nhất là **thu hẹp `systems[ps1].extensions`** hoặc chặn trước khi mở game — nhưng đó là
   `config/`, cần PM duyệt và tăng `configVersion`.
4. **Không** đóng issue nào bằng "đã sửa ANR": 4 issue `anr` chỉ được xét sau khi v0.8.13 qua 2 bản **và** không
   cộng lượt (§6).
5. Đẩy nhanh **V198** cho 6 lõi trong nhóm: có `.so` ghim + ký hiệu thì `native` của `snes9x`/`melonds`/`pcsx_rearmed`
   mới đọc được. Không đề xuất lại thiết kế (đã có đặc tả); chỉ cần PM xếp lịch.
6. Với `mkxp-z`: bổ sung bước lưu `symbols-rgss-*` vào release **không nháp** (hiện là nháp theo G8) — việc này
   thuộc `build-rgss.yml` + giấy phép, ngoài phạm vi docs này.

## 9. Giới hạn bằng chứng

- Không có `CRASH_ADMIN_TOKEN` trong lượt này ⇒ **không** đọc được `reason`, `crumbs`, `log`, `device`, `cert`
  của bất kỳ báo cáo nào. Mã lỗi `0/1/2/-1` của 5 issue `core-error` vì thế vẫn là **[CHƯA KIỂM]**.
- Thân issue không có phiên bản app, đời máy, tên game. Cột "Máy" chỉ là số khoá `seen` (số lần gửi), **không**
  phải số người dùng.
- Bot chỉ cộng lượt khi `delta ≥ 5` (`crash-issue-bot.py:118`) ⇒ "không tái diễn" chỉ có nghĩa "chưa đủ 5 lượt
  mới". Với 4 issue `anr` (mỗi cái ≤ 5 lượt) điều này đúng là giới hạn thật.
- Nhận định "thân issue không mang mã lỗi nên không quy được nguyên nhân" là **kết luận từ mã**; nếu Worker có
  lưu mã ở trường khác thì kết luận này sai — kiểm bằng `crash-reports.sh <id>` khi có token.
- Các con số về buildbot (`Last-Modified`, kích thước) đo hôm nay 13/11/2026; buildbot đổi hằng ngày nên **không**
  dùng lại số này cho lượt sau.
