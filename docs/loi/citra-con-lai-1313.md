# Nhóm `citra` còn lại (`core-error` / `anr` / `killed` / `unknown`) — chẩn đoán 13/11/2026

Phạm vi: 7 issue `crash-moi` đang mở có **lõi `citra`**, trừ nhóm `citra/native` (#458, #404) đã có `docs/loi/citra-native-1311.md` (PR #622).
Nền: `origin/main` `f24ece45`. Lượt này **chỉ tài liệu** — không sửa mã, không đụng `config/`.
Mọi suy luận nguyên nhân chưa có khung/log đã giải mã: **[CHƯA KIỂM]**.

## 0. Phần đã được phủ (không lặp lại)

| Nguồn | Đã chốt | Áp cho nhóm này thế nào |
|---|---|---|
| PR #622 `docs/loi/citra-native-1311.md` (đã gộp) | Lõi citra **không ghim bản** (`nightly/latest`, `version = "1"`, không `sha256`, không gói symbols), `.cia` lọt vào lõi libretro, thiếu `systemFiles`, chưa hạ cấu hình đồ hoạ; phát hiện lỗi nhãn `engine:azahar` | Dùng nguyên: cùng một lõi `citra_libretro_android.so`, cùng `CoreManager`/`RetroActivity` |
| PR #627 (đã gộp 13/11 10:16) | `Components.kt`: dấu hiệu lõi libretro **thắng** dấu hiệu Azahar; `citra` đơn độc → `engine:libretro:citra`; `ComponentsTest` 9/9 | Xác nhận #610/#607 mang nhãn sai; nhãn **báo cáo mới** sẽ đúng (mục 2) |
| PR #623 `docs/loi/ppsspp-anr-1311.md` (đang mở) | Nghi phạm ANR chung của `:libretrodroid`: `onPause()` trên Main Thread khi GLThread đang trong khung dài; `onDetachedFromWindow → requestExitAndWait` + `RetroActivity.onDestroy` chờ SRAM 1,5 s | Dùng nguyên cho 3 issue `citra/anr` (mục 4) — không lặp lại phân tích |
| PR #611 (đã gộp), PR #600 (đang mở) | Vòng đời **engine Azahar** và `.cia` ở `AzaharActivity`/`AzaharInstallActivity` | **Không** phủ được nhóm này: 3DS hiện không đi đường Azahar (mục 2.4) |

## 1. Số liệu

Nguồn: thân issue `crash-moi` (13/11). `Lượt` = số lần bot đã thông báo (app cộng dồn, **không phải số người**).
`Máy` = số **báo cáo khác nhau** trong `seen` (`crash-issue-bot.py:76`, khóa = `sha256(report id)`), tức xấp xỉ **số lần cài/máy** đã gửi, không phải số người.

| Issue | Lượt | Máy | Phiên bản | Thành phần (issue ghi) | Tái diễn? |
|---|---:|---:|---|---|---|
| [#301](https://github.com/aowvn-10diem/aowvn-monika/issues/301) `core-error` | 56 | 13 | không có trong issue | (trống — bản app cũ không gửi) | chưa: cập nhật cuối 25/10 |
| [#610](https://github.com/aowvn-10diem/aowvn-monika/issues/610) `core-error` | 1 | 1 | không có trong issue | `engine:azahar` **(sai — mục 2)** | có: tạo 11/11, +1 |
| [#336](https://github.com/aowvn-10diem/aowvn-monika/issues/336) `anr` | 9 | 9 | không có trong issue | (trống) | có: cập nhật cuối 07/11 |
| [#460](https://github.com/aowvn-10diem/aowvn-monika/issues/460) `anr` | 2 | 2 | không có trong issue | (trống) | chưa: 02/11 |
| [#328](https://github.com/aowvn-10diem/aowvn-monika/issues/328) `anr` | 2 | 2 | không có trong issue | (trống) | chưa: 22/10 |
| [#607](https://github.com/aowvn-10diem/aowvn-monika/issues/607) `killed` | 18 | 1 | không có trong issue | `engine:azahar` **(sai — mục 2)** | có: tạo 11/11, +18 |
| [#373](https://github.com/aowvn-10diem/aowvn-monika/issues/373) `unknown` | 1 | 1 | không có trong issue | (trống) | chưa: 27/10 |

**Tổng theo loại**

| Loại | Số issue | Lượt | Máy | Issue |
|---|---:|---:|---:|---|
| `core-error` | 2 | **57** | 14 | #301, #610 |
| `anr` | 3 | 13 | 13 | #336, #460, #328 |
| `killed` | 1 | **18** | 1 | #607 |
| `unknown` | 1 | 1 | 1 | #373 |
| **Tổng** | **7** | **89** | **29** | |

Đối chiếu #622: toàn bộ lõi `citra` = 9 issue / **121 lượt** = 89 (nhóm này) + 32 (`native`: #458 29, #404 3) — khớp.

Hai giới hạn phải nhớ khi đọc bảng:

- **Không có phiên bản, đời máy, mã lỗi** trong thân issue (bot chỉ ghi lõi/loại/chữ ký/số lượt). Suy theo ngày tạo so với lịch phát hành chỉ là phỏng đoán → **[CHƯA KIỂM]**.
- Bot **chỉ cộng lượt khi có ≥ 5 quan sát mới** (`crash-issue-bot.py:118`), nên "không tăng" **không** đồng nghĩa "đã hết". "Tái diễn" ở bảng trên chỉ là "còn lượt mới sau mốc v0.8.10 (04/11)".

**Bối cảnh `killed` của citra** (để không đọc sai #607): 3 issue `killed` **đã đóng** `not_planned` ngày 08/11 — #308 (155 lượt), #485 (12), #511 (3) = **170 lượt**. #607 (18 lượt, 11/11) là **chữ ký mới** sau khi đóng ⇒ đóng theo chữ ký không làm hết nhóm.

## 2. #610 — `Thành phần: engine:azahar` là nhãn **sai**, không phải engine Azahar

### 2.1 Thân issue nói gì

#610: `Lõi: citra` · `Loại: core-error` · `Thành phần: engine:azahar` · 1 lượt (+1), 1 báo cáo. Không có tên lõi khác, không có mã lỗi.

### 2.2 Vì sao sai

- `Components.kt:20` (bản trước #627) khớp `has(t, "azahar", "citra")` **trước** luật libretro ở `:27`.
- Báo cáo `core-error` dựng `component = Components.of(c, session, detail)` với `detail = "error code=$code core=$coreId"` (`RetroActivity.kt:373`, `Diagnostics.kt:602`). Chuỗi đó **có chữ `citra`** (tên lõi) ⇒ rơi vào nhánh Azahar, dù không có một dấu hiệu Azahar nào.
- Vệt phiên cũng góp phần: `Diagnostics.begin(..., "libretro", "citra", ...)` ghi crumb `begin · libretro · citra · Nintendo 3DS` (`Diagnostics.kt:160`, `RetroActivity.kt:128`).

### 2.3 Sau #627 thì #610 vào nhóm nào

**`engine:libretro:citra`.** Nhánh mới: `!libretro` mới xét Azahar; hết chuỗi `has(t, "citra")` → `"engine:libretro:citra"` (đúng ca test `loiCitraLibretroKhongPhaiAzahar`: `Components.of(null, null, "citra")` → `engine:libretro:citra`). #610 không có dấu hiệu `AzaharActivity`/`AzaharBridge`/`libcitra-android` nên không thoát được lên nhánh Azahar.

Hai lưu ý:

- #627 chỉ đổi **báo cáo mới**. Thân #610 hiện tại vẫn ghi `engine:azahar` cho tới lần bot ghi đè kế tiếp (bot dựng lại mục `Thành phần` từ thành phần của cửa sổ hiện tại, `crash-issue-bot.py:126-131`).
- Số liệu **theo bảng thành phần** (như bảng trong `docs/loi/TON-0811.md`, dòng `engine:azahar | citra / killed | 1`) vì thế **đếm thiếu** nhóm citra libretro; số liệu **theo `Lõi:` trong tiêu đề** (bảng mục 1) không bị ảnh hưởng.

### 2.4 Không chỉ #610: hiện **không có đường nào** tới engine Azahar cho 3DS

- `config/monika-config.json:200-207`: hệ `3ds` chỉ có `runner: libretro`, `core: citra`; **không có** trường `engine`.
- `GameLauncher` (cả `github` và `play`, `:50-53`) chỉ mở `AzaharActivity` khi `system.engine == "azahar"` ⇒ điều kiện không bao giờ đúng với cấu hình hiện tại.
- Trường `engine: azahar` của 3DS đã bị **bỏ ở configVersion 44** (commit `7bf4d1c8` "3DS tạm chạy lõi libretro Citra thay Azahar"); config hiện tại là **63**.

Hệ quả: mọi báo cáo `citra` từ bản app ≥ v0.8.5 (18/10, lúc bỏ engine Azahar) là **lõi libretro**, không phải engine Azahar. #617 (gộp 13/11) lại nhận #607 là "chữ ký Azahar lớn nhất" — xem mục 5.
Không loại trừ được máy còn chạy bản app **< v0.8.5** (khi đó Azahar còn bật): issue không có trường phiên bản → **[CHƯA KIỂM]**.

## 3. `core-error` (#301 56 lượt, #610 1 lượt)

### 3.1 Đường phát, có `tệp:dòng`

1. Lõi trả lỗi → `GLRetroView.catchExceptions` bắt `RetroException` và phát `e.errorCode` (lỗi khác → `ERROR_GENERIC`) — `libretrodroid/.../GLRetroView.kt:450-459`.
2. `RetroActivity` thu mã lỗi: `Diagnostics.stage("core-error-$code")` rồi `onCoreFailure("lõi báo lỗi mã $code", "error code=$code core=$coreId")` — `RetroActivity.kt:366-374`.
3. `Diagnostics.recordCoreFailure` tạo báo cáo `kind = "core-error"` — `Diagnostics.kt:594-606`.

**Chỉ lỗi trước khung đầu mới thành `core-error`**: `onCoreFailure` thoát ngay nếu `failureHandled || loadingGone` (`RetroActivity.kt:190`). Sau khung đầu, lỗi lõi không sinh `core-error`.

Mã lỗi (`LibretroDroid.java:74-79`): `0` load library · `1` load game · `2` GLES < 3 · `3` serialization · `4` cheat · `-1` generic.
`1` = `retro_load_game()` trả `false` (`libretrodroid.cpp:366-371`) → JNI ném `ERROR_LOAD_GAME` (`libretrodroidjni.cpp:441-443`). `0` = `create()` lỗi, thường do `.so` thiếu/hỏng (`libretrodroidjni.cpp:415-420`). `2` = bật HW mà GLES < 3 (`libretrodroid.cpp:331-332`).

### 3.2 citra có gì đặc thù

- **`.cia` vẫn vào lõi**: `systems[3ds].extensions` có `cia` (`config:204-210`); nhánh `libretro` của `GameLauncher` **không** kiểm đuôi tệp (`GameLauncher.kt:48-59`); #611 chỉ chặn `.cia` ở `AzaharActivity` — đường hiện không dùng (mục 2.4). `.cia` là gói cài, không phải ROM ⇒ kỳ vọng `retro_load_game = false` ⇒ `core-error` mã 1. **Chưa có bằng chứng** đây là nguyên nhân của #301/#610: **[CHƯA KIỂM]**.
- **Thiếu `systemFiles`**: `cores.citra` không khai `systemFiles`; `CoreManager.isReady` bỏ qua kiểm sysdata (`CoreManager.kt:53-58`); `RetroActivity` truyền `filesDir/system` (`RetroActivity.kt:326`) có thể **rỗng** ⇒ lõi thiếu sysdata. **[CHƯA KIỂM]** đây có làm `retro_load_game` thất bại hay không.
- **Lõi trôi bản**: `version = "1"` + URL `nightly/latest`, không `sha256` (`config:1120-1127`) ⇒ hai máy cùng nhãn `1` có thể chạy hai `.so` khác nhau; lỗi `0`/`2` có thể xuất hiện rồi tự hết sau một lần tải. **[CHƯA KIỂM]** về tương quan với #301/#610.
- **Không phân biệt được mã lỗi từ issue**: #301 (56 lượt/13 báo cáo, dừng cộng từ 25/10) và #610 (1/1, 11/11) có thể là hai mã lỗi khác nhau. Cách đọc đúng: xem `crumbs` của báo cáo — app đã ghi sẵn `core-error-<mã>` (`RetroActivity.kt:368`) và `reason = "lõi báo lỗi mã <mã>"` (`Diagnostics.kt:600`).

## 4. `anr` (#336 9, #460 2, #328 2)

- Cơ chế chung đã phân tích ở **PR #623** (mục 3a/3b): `onPause()` của `GLSurfaceView` trên Main Thread (`GLRetroView.kt:366-373`) chờ GLThread, trong khi `step()` giữ `coreLock` suốt khung (`libretrodroid.cpp:519-542`, gồm `retro_run()` `:533`, `renderFrame()` `:538`, `fpsSync->wait()` `:542`); `LibretroDroid.pause()` lại chờ đúng khóa đó (`:506-507`). Đóng game: `onDetachedFromWindow → requestExitAndWait` + `RetroActivity.onDestroy` chờ SRAM 1,5 s. Không lặp lại ở đây.
- **Thêm cho citra**: đây là lõi nặng nhất trong nhóm `:libretrodroid` và **không có `options`/`perf`** (`config:1120-1127`) ⇒ chạy mặc định độ phân giải/đồ hoạ của lõi; khung đầu (JIT + dựng shader) dài hơn ⇒ xác suất rơi vào cửa sổ `onPause`/`onDestroy` cao hơn. **[CHƯA KIỂM]** — không có số đo khung.
- `anr` chỉ được ghi **lần mở app kế tiếp** từ `ApplicationExitInfo.REASON_ANR` (`Diagnostics.kt:232-241`), và chỉ trên API ≥ 30. Muốn chỉ đúng khung phải có `adb bugreport` của người chơi (như #623 mục 5); hiện **không có**. #336 đã có bình luận PM (07/11) kết luận đúng điểm này.

## 5. `killed` (#607 18 lượt)

### 5.1 `killed` không đồng nghĩa "hết RAM"

`kind` chỉ có 5 nhánh rõ: `REASON_CRASH_NATIVE → native`, `REASON_CRASH → java`, `REASON_ANR → anr`, `REASON_LOW_MEMORY → lowmem`, `REASON_USER_*`/`EXIT_SELF` → bỏ; **mọi lý do còn lại → `killed`** (`Diagnostics.kt:232-241`). Vì vậy `killed` gồm cả `REASON_OTHER`/`REASON_SIGNALED`/`REASON_INITIALIZATION_FAILURE`. Hết RAM đã có nhánh `lowmem` riêng. Cần đọc `reason` của chính báo cáo mới biết là lý do nào → **[CHƯA KIỂM]**.

### 5.2 Xung đột với #617 (đã gộp 13/11)

#617 ("hộp thoại engine Azahar không treo luồng giả lập vô hạn") nhận #607 là *"chữ ký Azahar lớn nhất chưa được PR nào nêu"* và sửa `AzaharBridge.ask()` (chờ có hạn 60 s). Nhưng theo mục 2.4, **3DS không còn đi đường Azahar** từ v0.8.5 ⇒ trong cấu hình hiện tại #607 **không thể** là lỗi `AzaharBridge`; nó là `citra` libretro bị hệ thống tắt. Kết luận: **không đóng #607 dựa trên #617**; chỉ nếu #607 đến từ máy còn bản app < v0.8.5 thì mới liên quan. **[CHƯA KIỂM]** vì issue không có phiên bản.

### 5.3 Kiểm chứng thư (cert) — cách làm, chưa chạy

Thân issue **không** có chứng thư; chỉ Worker trả (`/reports`, trường `cert`, 12 hex đầu của SHA-256 chứng thư ký APK). Cách kiểm:

1. `CRASH_ADMIN_TOKEN=<mã> python3 scripts/crash-insight.py --out-dir out` — **mặc định chỉ tính báo cáo khớp chứng thư phát hành** `c46902e97029` (`crash-insight.py:34`, `:8-10`); nhóm `citra / killed` trong bảng là số **đã lọc**.
2. Chạy lại với `--all-certs` để thấy cả báo cáo mang chứng thư khác (bản mod/gói lại) — so `số báo cáo`/`số lượt` của nhóm `citra / killed` giữa hai lần.
3. Nếu chênh lệch lớn: tách trước khi kết luận, đúng cách `docs/loi/TON-0811.md` (PR #542, chưa gộp) xử lý #306 (75/78 lượt mang chứng thư lạ `1e08a903aef9`).
4. Chứng thư là **trường client gửi lên** — chỉ là tín hiệu chẩn đoán, không phải bằng chứng xác thực. Kèm luôn trường `a` (bản app) của báo cáo để biết #607 đến từ bản nào.

Đối chiếu `docs/loi/TON-0811.md` (đọc từ nhánh PR #542): bảng tồn 08/11 chỉ ghi **một** chứng thư lạ cho `melondsds` (`1e08a903aef9`, 75 lượt); **không** có ca `citra`. Vì vậy chứng thư của #607/#301 hiện là **[CHƯA KIỂM]**.

## 6. `unknown` (#373, 1 lượt)

`kind = "unknown"` khi **không có bản ghi `ApplicationExitInfo`** cho PID đó (`Diagnostics.kt:239`: `exit == null`) — thường là máy API < 30 hoặc Android không giữ bản ghi. Không có lý do, không có vệt ⇒ **không có gì để chẩn đoán**; giữ mở, không hành động. Trùng với cách xử lý nhóm H trong `TON-0811.md` ("chờ frame từ Worker rồi phân loại").

## 7. Việc tiếp theo (rẻ → đắt)

1. **Đọc đúng báo cáo trước khi sửa** (cần `CRASH_ADMIN_TOKEN`, phiên này không có): `crash-insight.py` + `--all-certs` cho chứng thư/bản app; `scripts/crash-reports.sh <id> --symbols-dir …` cho `reason` và `crumbs` — xác nhận `core-error-<mã>` (#301/#610) và lý do tắt thật của #607.
2. **Đo lại sau #627**: nhãn `engine:libretro:citra` đã đúng từ bản có #627; số nhóm citra sau 1–2 bản mới là số dùng được để so.
3. **A/B `.cia`** (cần PM duyệt vì đụng `config/` hoặc `GameLauncher`): chặn `.cia` ở nhánh `libretro` (cả 2 flavor) hoặc tạm bỏ `cia` khỏi `systems[3ds].extensions` ở bản thử → so `citra/core-error`. Nếu #301 giảm mạnh thì nghi phạm `.cia` đúng.
4. **A/B `systemFiles`** cho citra (bộ sysdata chuẩn) → xem `core-error` có đổi (**[CHƯA KIỂM]**).
5. **ANR**: chờ `adb bugreport` của #336/#460/#328 (không có bugreport thì mọi kết luận chỉ là đọc mã). Có thể đối chiếu vệt `sram` và `libretro-startup soft-timeout` cùng lượt.
6. **Không đóng** #607 bằng #617; chỉ xét đóng khi v0.8.13 (13/11) qua 2 bản mà không cộng lượt **và** đã kiểm chứng thư/bản app.

## 8. Giới hạn bằng chứng

- Không có `CRASH_ADMIN_TOKEN` trong lượt này ⇒ không đọc được báo cáo gốc (chứng thư, bản app, `reason`, `crumbs`, khung).
- Thân issue **không** có phiên bản, đời máy, mã lỗi hay tên tệp game; cột "Phiên bản" vì thế để "không có trong issue", không suy từ ngày tạo.
- `Máy` = số báo cáo khác nhau, **xấp xỉ** số máy/lần cài; một máy có thể gửi nhiều báo cáo.
- Bot chỉ cộng lượt khi ≥ 5 quan sát mới ⇒ "không tái diễn" trong bảng chỉ là "chưa thấy lượt mới đủ ngưỡng".
- Mọi nhận định nguyên nhân (`retro_load_game` false vì `.cia`, thiếu sysdata, khung dài gây ANR, #607 là lõi bị tắt) đều **chưa có khung đã giải mã** → **[CHƯA KIỂM]**.
