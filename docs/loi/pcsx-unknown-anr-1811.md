# Hai nhóm chưa ai nhận: `unknown`/`anr` (7 issue, 26 lượt) và `pcsx_rearmed`/`core-error` (2 issue, 55 lượt)

Ngày 18/11/2026 · nhánh `loi/pcsx-unknown-anr` · nền `origin/main` `4c4757b1` (configVersion 65)
Phạm vi: chỉ hai nhóm nêu trên. **Không** sửa `Diagnostics.kt` (#655), `GLRetroView.kt`/`RetroActivity.kt` (#656),
`app/.../azahar/*` (#600), `libretrodroid/src/main/cpp/**`, tệp khoá (`config/monika-config.json`,
`app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/**`).
Tài liệu chẩn đoán `unknown/anr` của #634 **không viết lại** ở đây; mục 1 chỉ **kiểm lại dẫn chiếu** và ghi phần mới.

## 0. Kết luận

| Nhóm | Lượt | Nguyên nhân gốc | Lượt này |
|---|---:|---|---|
| `unknown`/`anr` (7 issue) | 26 | **Không có phiên chơi** ⇒ không có `session.core`. Ba đường: ANR ở tiến trình chính, ở `:midlet` trước #638, hoặc ở `:game` **trước** `Diagnostics.begin`/sau khi Android không giữ `processStateSummary`. Không phải lỗi ghi nhận lõi. | Chỉ đọc + kiểm dẫn chiếu; **không sửa mã** (Diagnostics.kt thuộc #655) |
| `pcsx_rearmed`/`core-error` (2 issue) | 55 | Có **ít nhất hai** cơ chế. Lượt này tìm ra và **sửa** một cơ chế tái hiện được: nhập `.cue` và `.bin` **rời** thì mã cũ tách thành **hai thư mục game** ⇒ `.cue` không thấy tệp đĩa ⇒ `retro_load_game()` trả `false` (mã `1`) ⇒ `core-error`, lặp lại mọi lần mở game. | Sửa `Importer` + `DiscSet`; 10 test mới |
| Còn chặn | — | (1) ghim bản lõi `pcsx_rearmed` — tệp khoá; (2) mã lỗi (`reason`) chưa vào thân issue — `Diagnostics.kt` (#655) + Worker; (3) ZIP buildbot cắt cụt vẫn được nhận — đề xuất ở §2.5 | Đặc tả, không sửa |

## 1. `unknown`/`anr`: vì sao ANR không có phiên chơi rơi vào `unknown`

### 1.1 Chuỗi ghi nhận (kiểm lại hôm nay, khớp #634)

| Bước | Bằng chứng tệp:dòng |
|---|---|
| Lõi chỉ tồn tại trong phiên | `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:152-162` (`begin` ghi `Session(... core ...)`), `:201-215` (ghi ảnh chụp `diag/sessions/<pid>.json` theo PID) |
| Bảy nơi gọi `begin` (đều lúc mở game) | `azahar/AzaharActivity.kt:107`, `azahar/AzaharInstallActivity.kt:54`, `diag/MidletDiagnostics.kt:133`, `runner/KirikiriGameActivity.kt:46`, `runner/RenpyGameActivity.kt:60`, `runner/RetroActivity.kt:128`, `runner/RgssGameActivity.kt:82` |
| Chết do Android báo | `Diagnostics.kt:855` `collectProcessDeaths`, quét tối đa 12 lần thoát `:859`, bỏ mốc ≤ lần quét trước `:863` |
| Ảnh chụp phiên chỉ đọc cho `anr`/`lowmem` | `Diagnostics.kt:877` (`if (kind == "anr" \|\| kind == "lowmem") readDeathSession(...) else null`), khớp `:908-915` (`matchingDeathSession` đòi `processStateSummary` khớp thế hệ) |
| Không có phiên ⇒ chuỗi `"unknown"` (không phải ô rỗng) | `Diagnostics.kt:928` (chỉ xét `lowmem`/`anr`) và `:930` (`r.core.ifBlank { "unknown" }`) |
| Tiến trình chính không bao giờ có phiên | `app/src/github/java/vn/aow/monika/ui/MainActivity.kt:84-89` chỉ gọi `collect` + `collectProcessDeaths` trong `onResume`; không có `begin` (cùng lý do với `app/src/play/.../MainActivity.kt:73-80`) |
| Worker không cứu được nhãn | `cloudflare/crash-worker/worker.js:833` `core: s(r.core, 60) \|\| s(ss?.core, 60) \|\| "unknown"` — vì app đã gửi `"unknown"` (truthy) nên nhánh dự phòng không chạy |
| Bot lấy lõi từ phiên | `scripts/crash-issue-bot.py:72-74` `core = token(session.get("core",""))`, chỉ khi `"unknown"` mới thử `report["core"]` (#632) ⇒ vẫn `unknown` |

### 1.2 Ba đường dẫn tới "không có phiên" (không phân biệt được nếu thiếu token)

1. **Tiến trình chính**: ANR khi đang ở feed/thư viện/cài đặt/tải game — không có `begin` nào chạy (bảng trên).
2. **`:midlet` trước #638** (13/11): 6/7 issue mở 31/10–01/11, trước mốc đó.
3. **`:game` trước `begin`**: `RetroActivity.kt:118` (`GameSaveGate.acquire`) → `:128` (`begin`); ANR do khoá save nghẽn > 5 s rơi vào khe này.
   Cùng nhóm: preload ở `runner/GamePreload.kt`, `CrashActivity` (`:crash`, không có `begin`).

**Không kết luận được** issue nào thuộc đường nào: thân issue không chép `title`/`component`/`device`, cần
`CRASH_ADMIN_TOKEN` để đọc bản ghi gốc — **[CHƯA KIỂM]** (không có token trong lượt này).

### 1.3 `Thành phần:` thiếu ở 6/7 issue **không** chứng minh gì

Bot chỉ thêm dòng `Thành phần:` từ commit `e8653ce2` (08/11/2026); 6/7 issue mở trước mốc đó, #534 (07/11) cũng trước.
Thân issue chỉ được PATCH khi tăng ≥ 5 lượt (`crash-issue-bot.py:123`), nên issue cũ giữ thân cũ.

## 2. `pcsx_rearmed`/`core-error`: nguyên nhân gốc

### 2.1 Số liệu (từ thân issue, không cần token)

| Issue | `count` trong thân | Khoá `seen` | `Thành phần` | Mốc |
|---|---:|---:|---|---|
| [#322](https://github.com/aowvn-10diem/aowvn-monika/issues/322) | 37 (+10) | 13 | **không có** | tái diễn 20/10 → 07/11 |
| [#578](https://github.com/aowvn-10diem/aowvn-monika/issues/578) | 18 (+18) | 4 | `engine:libretro:pcsx_rearmed` | 18 lượt / 4 máy |

37 + 18 = **55**, khớp `docs/loi/TONG-HOP-1311.md`. (Bảng giao việc ghi "#322(53) #578(2)" — không khớp thân issue;
con số hiện tại cần token để đọc — **[CHƯA KIỂM]**.)

Cửa sinh báo cáo duy nhất: `runner/RetroActivity.kt:393-394` (`GLRetroErrors` → `Diagnostics.stage("core-error-$code")`)
→ `:190` `onCoreFailure` → `:196` `Diagnostics.recordCoreFailure` (`Diagnostics.kt:615-627`, `reason = what` =
`"lõi báo lỗi mã N"`). Mã `1` = `retro_load_game()` trả `false`; `0` = không `dlopen` được `.so`.

### 2.2 Đã loại trừ (giữ nguyên từ #636, không phân tích lại)

- SRAM hỏng: `libretrodroid/src/main/cpp/libretrodroid.cpp:164-181` không ném ngoại lệ; SRAM nạp **sau** `retro_load_game`.
- Máy GLES 2.0: mã `2`, `pcsx_rearmed` là lõi video mềm.
- Danh sách đuôi: `systems[ps1].extensions` **đã có** `cue`, `pbp`, `chd`, `m3u` (configVersion 65) — không phải nguyên nhân.
- BIOS: `pcsx_rearmed` dùng HLE BIOS khi thư mục hệ thống trống (`library/DiscSet.kt:19-20`); `ps1` không khai `systemFiles`.

### 2.3 Cơ chế **mới tìm ra và đã sửa**: nhập `.cue` và `.bin` rời → hai thư mục

| Bước | Bằng chứng tệp:dòng (số dòng trên `origin/main` `4c4757b1`, **trước** bản sửa này) |
|---|---|
| Mỗi tệp rời được cấp **thư mục mới** theo tên tệp | `library/Importer.kt:52-60` (`importFile`) và `:108-113` (`importUri`) gọi `newGameDir` |
| `newGameDir` đặt tên thư mục = tên tệp không đuôi, trùng thì thêm `" (2)"` | `Importer.kt:161-169` |
| Người chơi chọn **nhiều tệp** một lúc, mỗi URI nhập riêng | `app/src/github/java/vn/aow/monika/ui/screens/LibraryScreen.kt:190` (`OpenMultipleDocuments`) → `:154-186` `importAll` → `:166` `Importer.importUri` (bản `play` y hệt, `:182`/`:158`) |
| Hệ quả | `Game.cue` → `…/Game/`, `Game.bin` → `…/Game (2)/`; `.cue` không thấy `.bin` ⇒ `retro_load_game()` `false` ⇒ **mã 1** ⇒ `core-error` mỗi lần mở game |

**Bằng chứng tái hiện** (test tạm, đã xoá sau khi đo): cùng một test `cueAndBinGoToSameFolder`, chạy trên
`origin/main` **FAIL** (`AssertionError` — hai thư mục khác nhau), chạy trên bản sửa **PASS**.

```
# trước (git stash phần app/src/main):
EvidenceSplitTest > cueAndBinGoToSameFolder FAILED   java.lang.AssertionError at EvidenceSplitTest.kt:33
# sau:
BUILD SUCCESSFUL
```

Đây là **một** cơ chế sinh `core-error` mã `1` cho PS1 và nó giải thích được vì sao lỗi **lặp lại ổn định** trên
nhiều máy (mỗi lần mở game đều thiếu tệp đĩa) — không cần giả thuyết "ROM hỏng".

### 2.4 Đã sửa (3 tệp mã + 2 tệp test, không đụng tệp bị chặn)

| Tệp | Thay đổi |
|---|---|
| `app/src/main/java/vn/aow/monika/library/DiscSet.kt` | `companionFolder(root, name)`: tìm thư mục game **đã có** mà tệp rời thuộc về, theo 3 mức chắc chắn — (1) cùng tên gốc với `.cue`/`.m3u` trong thư mục, (2) thư mục mang đúng tên gốc và đã có tệp đĩa (bỏ hậu tố chống trùng `" (2)"`, giữ tên đĩa thật `" (Disc 1)"`), (3) tệp danh sách nhắc đúng tên tệp (`Game (Track 1).bin`). Tách `references()` dùng chung với `inspect()`; đọc nội dung tối đa `MAX_LIST_READS = 200` tệp danh sách |
| `app/src/main/java/vn/aow/monika/library/Importer.kt` | `gameDirFor()`: tệp rời dùng thư mục bộ đĩa nếu có, gói nén vẫn thư mục riêng; áp cho cả `importFile` và `importUri` |
| `app/src/main/java/vn/aow/monika/library/ArchiveExtractor.kt` | tách `isArchiveName(name)` (biết là gói nén **trước khi** có tệp, cho `importUri`) |
| `DiscSetTest.kt` (+5), `ImporterTest.kt` (+5) | cùng tên gốc, nhập ngược thứ tự, tên track khác tên cue, `.m3u` nhiều đĩa, không gom oan, **không ghi đè** tệp trùng tên |

Nguyên tắc giữ nguyên: chỉ gom khi **chắc chắn** cùng bộ; thư mục đã có tệp cùng tên thì **không** gom.
Bộ đĩa **đã bị tách** từ trước bản sửa này thì không tự gộp lại — màn hình chờ đã báo đúng tên tệp thiếu
(`DiscSet.inspect`, `RetroActivity.kt:295-310`), người chơi nhập lại tệp đĩa là xong.

### 2.5 Còn lại — đề xuất (không sửa trong lượt này)

1. **Ghim bản lõi** `cores.pcsx_rearmed`: `version = "1"`, buildbot `nightly/android/latest/{abi}`, không `sha256`,
   không symbols ⇒ hai máy cùng nhãn chạy hai binary khác nhau và `isReady()` (`runner/CoreManager.kt:53-58`) chỉ so
   chuỗi `"1"` nên máy đã cài **không bao giờ** tải lại. Cần PM + sửa `config/monika-config.json` (tệp khoá).
2. **Mã lỗi vào thân issue**: `recordCoreFailure` đã đặt `reason = "lõi báo lỗi mã N"`, Worker trả `reason`
   (`worker.js:825`) nhưng bot không in. Cần sửa `scripts/crash-issue-bot.py` + `Diagnostics.kt` (#655) — ngoài phạm vi lượt này.
3. **Kiểm ELF/ABI khi cài lõi chưa ghim**: `CoreManager.kt:122-141` lấy entry `.so` đầu tiên trong ZIP rồi
   `renameTo` — ZIP buildbot cắt cụt vẫn được nhận và `isReady()` nói "sẵn sàng" ⇒ `core-error` mã `0` vĩnh viễn
   cho tới khi người chơi xoá tay. Nên gọi `PackTransaction.validate` (như nhánh `artifacts`) và so số byte đã tải
   với `contentLength`. **[CHƯA KIỂM]** đây có phải ca của 13/4 máy trong #322/#578 hay không (cần token).

## 3. Cách kiểm chứng lại

```bash
./gradlew :app:testGithubDebugUnitTest --tests "vn.aow.monika.library.DiscSetTest" \
    --tests "vn.aow.monika.library.ImporterTest"
```
- Kỳ vọng: 38 test, **0 lỗi mới**. Ca `incompleteMultiPartSetStaysPendingInStagingFolder` hỏng sẵn trên Windows
  (so đường dẫn `_TaiVe/_phan` với dấu `\`) — đã đối chiếu: hỏng cả trên `origin/main` khi chưa sửa.
- Sau bản phát hành: mở `/stats` xem `pcsx_rearmed`/`core-error` có giảm; nếu còn, đọc `reason` của bản ghi mới
  (cần `CRASH_ADMIN_TOKEN`) để tách mã `0`/`1`/`-1`.

## 4. Phần chưa kiểm

- Không có `CRASH_ADMIN_TOKEN` ⇒ **không** đọc được `reason`, `device`, `crumbs` của bất kỳ bản ghi #322/#578 hay 7
  issue `unknown/anr` nào. Mọi câu về phân bố mã lỗi và về tiến trình là **đọc mã**, giữ nhãn **[CHƯA KIỂM]**.
- Chưa có bản phát hành nào chứa bản sửa này ⇒ chưa có số đo "trước/sau" ngoài test.
- `Importer.importUri` lấy tên từ `displayName` của URI; test dùng `importFile` (cùng hàm chọn thư mục) nên nhánh
  `content://` của picker được kiểm bằng đọc mã, không bằng test — **[CHƯA KIỂM]** trên máy thật.
