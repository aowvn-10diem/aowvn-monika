# Nhóm `unknown` / `anr`: ANR không có phiên chơi, không phải lỗi ghi nhận lõi

Ngày 13/11/2026 · nhánh `chan-doan/unknown-anr-1313` · nền `main` `0e9ef5bd` · **mọi số dòng trong tệp tính theo `0e9ef5bd`** · nhãn `crash-moi`, chỉ đọc (không sửa mã trong lượt này)

## 1. Kết luận ngắn

- Nhóm `lõi unknown + loại anr` đang mở: **7 issue · 26 lượt · 16 khoá `seen`** ([#398](https://github.com/aowvn-10diem/aowvn-monika/issues/398), [#399](https://github.com/aowvn-10diem/aowvn-monika/issues/399), [#413](https://github.com/aowvn-10diem/aowvn-monika/issues/413), [#414](https://github.com/aowvn-10diem/aowvn-monika/issues/414), [#415](https://github.com/aowvn-10diem/aowvn-monika/issues/415), [#419](https://github.com/aowvn-10diem/aowvn-monika/issues/419), [#534](https://github.com/aowvn-10diem/aowvn-monika/issues/534)).
- **Hai bản sửa hiện có không giải quyết trọn nhóm này — thực tế là không đổi được issue nào trong 7 issue.**
  - PR **#632** (bot lấy `core` cấp ngoài) chỉ có tác dụng khi báo cáo **không có phiên** nhưng **có `core` cấp ngoài thật**. Với `anr`, app luôn ghi `core = "unknown"` khi không có phiên (`Diagnostics.kt:920`), Worker truyền nguyên `"unknown"` (`worker.js:833`), nên fallback của bot vẫn ra `unknown`. Không có chỗ nào trong app tạo `core` cấp ngoài thật mà thiếu phiên (mục 4).
  - Bản sửa local `Diagnostics.kt` (nhánh `deepseek1/unknown-core`, commit `589d7d1b`, **chưa push**) mở đọc phiên chết cho `native`/`resource`/`initfail`; nhánh `anr` **giữ nguyên** (`Diagnostics.kt:867` đã đọc phiên cho `anr` từ trước) ⇒ **0 tác dụng** với nhóm này.
- **Đường thứ ba là nguyên nhân của cả 7 issue: ANR không có `session.core`** (không có phiên, hoặc phiên từ bản app quá cũ chưa có trường lõi). Có `session.core` thì tiêu đề đã là lõi thật ngay cả trước #632, vì bot đọc `session.core` từ đầu (`crash-issue-bot.py:69`). Các đường cụ thể: tiến trình chính (không bao giờ gọi `Diagnostics.begin`), `:midlet` (J2ME không có `Diagnostics`), `:game` trước `begin` (`GameWarmService`/`GamePreload`, hoặc `GameSaveGate.acquire` đứng trước `begin`), và phiên bị `matchingDeathSession` loại vì thiếu/lệch `processStateSummary`. Chi tiết ở mục 3.
- **Không có lỗi "tiêu đề/backfill" trong nhóm:** 0/7 issue có dòng `Thành phần`. Trên toàn repo có 7 issue vừa `Thành phần` vừa tiêu đề `unknown`, nhưng "Thành phần" là **thành phần**, không phải **lõi**; các ca đó đều là "không có phiên" thật, không phải tiêu đề ghi sai (mục 5). Bot cũng **không bao giờ PATCH tiêu đề** (`crash-issue-bot.py:219-220`).

## 2. Bảng issue

Số liệu lấy từ thân issue ngày 13/11/2026 (`Số lần quan sát đã thông báo` + khối `<!-- aow-crash-v100:… -->`).

- `Lượt` = số lần bot đã cộng dồn (không phải số người).
- `Khoá seen` = số khoá ẩn danh trong trường `seen` — **không** phải số máy/model.
- `Phiên bản`, `App/game`: **không có trong thân issue** — bot chỉ chép lõi/loại/chữ ký/số lượt (`scripts/crash-issue-bot.py:4`, `docs/CRASH-ISSUE-BOT.md`). Cần `CRASH_ADMIN_TOKEN` để đọc `app`, `device`, `title`, `component` của từng bản ghi ⇒ **[CHƯA KIỂM]**.
- `Tái diễn?` = cùng chữ ký được cộng thêm lượt sau lần đầu (`lượt ≥ 2`).

| Issue | Lượt | Khoá `seen` | Phiên bản | App/game | Tái diễn? |
|---|---:|---:|---|---|---|
| [#398](https://github.com/aowvn-10diem/aowvn-monika/issues/398) | 1 | 1 | không có trong issue | không có trong issue | Không |
| [#399](https://github.com/aowvn-10diem/aowvn-monika/issues/399) | 2 | 2 | không có trong issue | không có trong issue | Có (2 lượt / 2 khoá) |
| [#413](https://github.com/aowvn-10diem/aowvn-monika/issues/413) | 15 | 5 | không có trong issue | không có trong issue | Có (15 lượt / 5 khoá) |
| [#414](https://github.com/aowvn-10diem/aowvn-monika/issues/414) | 1 | 1 | không có trong issue | không có trong issue | Không |
| [#415](https://github.com/aowvn-10diem/aowvn-monika/issues/415) | 1 | 1 | không có trong issue | không có trong issue | Không |
| [#419](https://github.com/aowvn-10diem/aowvn-monika/issues/419) | 1 | 1 | không có trong issue | không có trong issue | Không |
| [#534](https://github.com/aowvn-10diem/aowvn-monika/issues/534) | 5 | 5 | không có trong issue | không có trong issue | Có (5 lượt / 5 khoá) |
| **Tổng** | **26** | **16** | | | |

Hai issue chiếm 20/26 lượt là **#413** (15) và **#534** (5). Mỗi issue là một chữ ký riêng; không có chữ ký trùng giữa các issue.

## 3. Vì sao `Lõi: unknown` — đọc từ mã

### 3.1 Lõi chỉ tồn tại trong "phiên"

- `Diagnostics.begin(...)` ghi `Session(core = …)` và ảnh chụp `diag/sessions/<pid>.json` — `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:152-162`, `:201-215`.
- Chỉ **6 nơi** gọi `begin`, tất cả đều là màn mở game:
  `AzaharActivity.kt:107`, `AzaharInstallActivity.kt:54`, `RetroActivity.kt:128`, `RenpyGameActivity.kt:60`, `RgssGameActivity.kt:82`, `KirikiriGameActivity.kt:46`.
- Chết trong phiên (`Diagnostics.collect`, `Diagnostics.kt:223-258`) → báo cáo có `session` → có lõi.
- Chết do Android báo (`collectProcessDeaths`, `Diagnostics.kt:845-893`) → chỉ có lõi nếu đọc được ảnh chụp phiên khớp (`Diagnostics.kt:867`, `:898-905`).

### 3.2 Suy ra: cả 7 issue đều **không có `session.core`**

Bot lấy lõi **từ phiên** ngay từ đầu: `core = token(session.get("core", ""))` (`scripts/crash-issue-bot.py:69`). Nếu báo cáo có `session.core` thật thì tiêu đề issue đã là lõi đó **trước cả #632**. Cả 7 tiêu đề đều `unknown` ⇒ `session.core` rỗng/không có ở mọi bản ghi của 7 chữ ký này.

`Session.core` không bao giờ do `begin` ghi rỗng, và trường này có từ 29/9/2026 (`0a02ed30`), trước mọi issue trong nhóm (31/10–07/11). Vì vậy đây không phải "phiên có nhưng thiếu lõi" ở bản app hiện hành, mà là **không có phiên** (hoặc báo cáo do bản app trước 29/9 gửi lên).

### 3.3 `#632` không đổi được nhãn của báo cáo `anr`

Chuỗi ghi nhận hiện tại:

1. App tạo báo cáo `anr` → `record()` gọi `enrichDeathReport` (`Diagnostics.kt:716`).
2. Không có phiên → `core = r.core.ifBlank { "unknown" }` ⇒ **chuỗi `"unknown"`**, không phải ô rỗng (`Diagnostics.kt:917-924`; điều kiện `if (r.kind != "lowmem" && r.kind != "anr") return r` ở `:918`, nhánh `anr`/`lowmem` giữ fallback `"unknown"`). Bản local `589d7d1b` (nhánh `deepseek1/unknown-core`, **chưa push**) đặt tên tập này là `EXPLICIT_UNKNOWN_KINDS` nhưng hành vi `anr` không đổi — **[CHƯA KIỂM]** vì chưa push.
3. Worker chỉ thêm `core` cấp ngoài cho `anr`/`lowmem`: `core: s(r.core, 60) || s(ss?.core, 60) || "unknown"` (`cloudflare/crash-worker/worker.js:832-836`). Vì `r.core` đã là `"unknown"` (truthy), kết quả vẫn `"unknown"`.
4. Bot #632 fallback sang `report["core"]` (cấp ngoài) → `token("unknown")` = `"unknown"` (`crash-issue-bot.py:26-27`, `:69`).

Với bản app **cũ hơn V101** (trước 13/10), `collectProcessDeaths` còn không gắn `session` cho `anr` và cũng không có `core` cấp ngoài; Worker ghi `"unknown"`. Vẫn không đổi.

⇒ Trong mã hiện tại **không có producer nào** tạo `core` cấp ngoài thật mà thiếu `session`. Test mới của #632 dựng tay `{"core": "citra"}` ở cấp ngoài, nhưng không chỉ ra đường sinh ra dữ liệu đó. **#632 là thay đổi vô hại nhưng không giải quyết #413/#534** (và không nên dùng nó để đóng 2 issue này). Nếu Worker có bản ghi do client khác gửi thì kết luận này có thể khác — **[CHƯA KIỂM]** vì không có token.

### 3.4 Bản sửa local `589d7d1b` cũng không đổi `anr`

- `readsDeathSession(kind)` thêm `native`/`resource`/`initfail`; `anr` **đã** nằm trong tập đọc từ trước.
- `enrichDeathReport` giữ nguyên nhánh `lowmem`/`anr` (fallback `"unknown"`), chỉ đổi nhánh các loại khác.
- Không có phiên thì vẫn không có gì để đọc.

⇒ Bản sửa này đúng cho nhóm `unknown/native` (PR #625), **không phải** nhóm `anr`.

## 4. Đường thứ ba: những ca `anr` không thể có lõi

### (a) Tiến trình chính — không bao giờ có phiên

`MainActivity` không gọi `Diagnostics.begin`; chỉ có `collect` + `collectProcessDeaths` trong `onResume` (`app/src/github/java/vn/aow/monika/ui/MainActivity.kt:84-89`; `app/src/play/java/vn/aow/monika/ui/MainActivity.kt:73-80`). ANR khi đang ở feed/thư viện/cài đặt/tải game ⇒ `deathSession = null` (`Diagnostics.kt:867`) ⇒ `unknown` theo đúng nghĩa "không có lõi nào để ghi".

### (b) J2ME `:midlet` — không có `Diagnostics`

Activity chạy ở tiến trình `:midlet` (`j2me/src/main/AndroidManifest.xml:52`, `javax.microedition.shell.MicroActivity`). Toàn bộ `j2me/src` **không có** tham chiếu `Diagnostics` (grep 0 kết quả). ANR của game Java ⇒ không phiên ⇒ `unknown`. Đây là ca đã thấy ở #302 (`Thành phần: app:midlet`, loại `resource`).

### (c) `:game` trước khi `begin`

- `GameWarmService`/`GamePreload` chạy ở `:game` để dựng nóng tiến trình (`app/src/main/java/vn/aow/monika/runner/GamePreload.kt:67`; manifest `app/src/github/AndroidManifest.xml:220`, `app/src/play/AndroidManifest.xml:75`) — không gọi `begin`. ANR lúc preload engine ⇒ không phiên.
- Trong chính Activity game, có việc nặng đứng **trước** `begin`:
  - `AzaharActivity.kt:99` `GameSaveGate.acquire(...)` → `begin` ở `:107`.
  - `RetroActivity.kt:118` `GameSaveGate.acquire(...)` → `begin` ở `:128`.
  Nếu khoá save làm nghẽn main thread >5 giây, ANR xảy ra khi chưa có ảnh chụp phiên ⇒ `unknown`. **[CHƯA KIỂM]** đây có phải ca của 7 issue hay không.

### (d) Có phiên nhưng bị `matchingDeathSession` loại

`matchingDeathSession` yêu cầu `processStateSummary` **không null** và **khớp** `generation`, rồi `pid`, `startedAt`, `stageAt`, `lastAlive` phải nằm trong khoảng hợp lệ (`Diagnostics.kt:898-905`). Nếu Android không giữ summary (một số ca ANR/API 30), hoặc mốc thời gian lệch, phiên có sẵn vẫn bị bỏ ⇒ `unknown`. Chưa có số đo tần suất — **[CHƯA KIỂM]**.

### (e) Cửa sổ quét và vòng đời

- Chỉ quét tối đa **12** lần thoát gần nhất (`Diagnostics.kt:849`) và bỏ mọi mốc `timestamp <= last` (`:853`); `last-scan` chỉ tiến lên (`:893`). Nếu có >12 lần thoát giữa hai lần mở `MainActivity`, các ANR cũ hơn bị mất vĩnh viễn.
- Chỉ quét khi `MainActivity.onResume` chạy; nếu người chơi không quay lại app (hoặc vào thẳng game bằng shortcut) thì bản ghi có thể già đi khỏi cửa sổ của Android.
- ANR được người dùng chọn "Chờ" (Wait) không tạo `ApplicationExitInfo` ⇒ không bao giờ có báo cáo. Đây là "không ghi", không phải "ghi sai".

### (f) Tiến trình `:crash`

`CrashActivity` chạy ở `:crash` (`app/src/github/AndroidManifest.xml:151`, `app/src/play/AndroidManifest.xml:64`), cũng không có `begin`. ANR ở đây cũng rơi vào nhóm không lõi.

**Không xác định được 7 issue thuộc (a)/(b)/(c)/(d)/(f) nào** nếu không đọc `title`/`component` của bản ghi Worker — thân issue không chép các trường đó. Xem mục 6.

## 5. Kiểm riêng: `Thành phần:` mà tiêu đề vẫn `unknown`

### 5.1 Trong 7 issue `anr`

**0/7** có dòng `Thành phần:`. Nhưng **không thể suy** từ đây rằng bản ghi không có thành phần:

- Bot chỉ thêm dòng này từ commit `e8653ce2` ngày **08/11/2026** ("bot(V100): ghi Thành phần vào issue").
- 6/7 issue mở 31/10–01/11, #534 mở 07/11 — đều **trước** mốc đó.
- Thân issue chỉ được PATCH khi có thêm ≥5 lượt (`crash-issue-bot.py:118`), nên issue cũ có thể giữ thân cũ dù sau đó có bản ghi mới.

⇒ Thiếu `Thành phần` ở nhóm này **không** chứng minh được gì về tiến trình.

### 5.2 Toàn repo: 7 issue vừa `unknown` vừa `Thành phần`

| Issue | Loại | `Thành phần` | Kết luận sơ bộ |
|---|---|---|---|
| [#614](https://github.com/aowvn-10diem/aowvn-monika/issues/614) | `op-fail` | `op:va-rom` | Ngoài phiên game — không có lõi (đã bàn ở #618) |
| [#608](https://github.com/aowvn-10diem/aowvn-monika/issues/608) | `op-fail` | `op:tai-xuong` | Ngoài phiên game |
| [#595](https://github.com/aowvn-10diem/aowvn-monika/issues/595) | `op-fail` | `op:giai-nen` | Ngoài phiên game |
| [#512](https://github.com/aowvn-10diem/aowvn-monika/issues/512) | `op-fail` | `op:tai-feed` | Ngoài phiên game |
| [#305](https://github.com/aowvn-10diem/aowvn-monika/issues/305) | `op-fail` | `op:tai-feed` | Ngoài phiên game |
| [#304](https://github.com/aowvn-10diem/aowvn-monika/issues/304) | `lowmem` | `app:game` (+1) | Có tiến trình `:game` nhưng **không có phiên** (lowmem ở preload/nền) — cần xem riêng **[CHƯA KIỂM]** |
| [#302](https://github.com/aowvn-10diem/aowvn-monika/issues/302) | `resource` | `app:midlet` | `:midlet` không gọi `begin` — không có lõi theo thiết kế |

Không ca nào là **lỗi tiêu đề/backfill**:

- "Thành phần" là `component`, "Lõi" là `core`. Biết tiến trình **không** suy ra được lõi (nhiều tiến trình dùng chung một app, `:game` có thể chạy nhiều engine).
- Bot **không cập nhật tiêu đề** khi PATCH issue (`crash-issue-bot.py:219-220` chỉ gửi `{"body": ...}`). Nhưng tiêu đề lại sinh từ `group["core"]` và chữ ký băm cả `core` (`:71`, `:150`), nên một báo cáo có lõi thật sẽ tạo **chữ ký/issue khác**, không làm tiêu đề cũ sai đi.
- #302 và #304 là các ca "có thành phần, thiếu phiên" thật, đúng bản chất — không phải tiêu đề ghi nhầm.

## 6. Hai bản sửa phủ được gì

| Thay đổi | Phủ | Không phủ |
|---|---|---|
| **#632** `scripts/crash-issue-bot.py` fallback `report["core"]` | Báo cáo `anr`/`lowmem` **không phiên** mà **có** `core` cấp ngoài thật. Theo mã hiện tại không có producer nào tạo trường hợp này; nếu có dữ liệu cũ khác thì cần token để xác nhận **[CHƯA KIỂM]** | 7 issue `unknown/anr` — app ghi `"unknown"` chứ không để trống, nên fallback vẫn ra `unknown` |
| **Local `589d7d1b`** `Diagnostics.kt` (chưa push) | `native`/`resource`/`initfail` có ảnh chụp phiên; `enrichDeathReport` điền lõi/hệ/giai đoạn cho các loại đó | `anr`: đã đọc phiên từ trước, nhánh fallback `unknown` giữ nguyên ⇒ 0 tác dụng |

Kết luận: **không được**. Nhóm này cần một trong các hướng ở mục 7, không phải hai bản sửa trên.

## 7. Đề xuất (chưa sửa mã trong lượt này)

1. **Đo trước khi sửa** — có `CRASH_ADMIN_TOKEN` thì đọc từng bản ghi của 7 chữ ký:
   `CRASH_ADMIN_TOKEN=… scripts/crash-reports.sh <id>` → xem `title` (chứa "tiến trình chính/`midlet`/`game`"), `component`, `stage`, `app`, `device`.
   Đây là cách duy nhất phân biệt (a)/(b)/(c)/(d) của mục 4.
2. **Lưu tiến trình thành trường riêng** — hiện tên tiến trình chỉ nằm trong chuỗi `title` (`Diagnostics.kt:880`) và không được chép vào issue. Thêm trường `process` vào `Report`, ghi vào Worker, và in vào thân issue (chuỗi ASCII: `main`/`midlet`/`game`/`crash`) để triage không cần token.
3. **Phiên tối giản cho tiến trình chính** — `begin` hiện ghi một `active-session.json` dùng chung (`Diagnostics.kt:138`, `:201-215`); thêm phiên cho main process phải theo PID và không được ghi đè phiên `:game` đang chạy. Cần đặc tả riêng **[CHƯA KIỂM]**.
4. **Cầu phiên cho J2ME** — module `j2me/` không phụ thuộc `:app`; cần interface/callback do app đăng ký, hoặc app suy từ tên tiến trình. Thay đổi thiết kế, tách việc riêng.
5. **Nhãn lõi khi không có phiên** — cân nhắc `core = "app"` (tiến trình chính) / `"j2me"` (`:midlet`) thay vì `unknown` để issue nói đúng "không có lõi engine", nhưng đây là quyết định nghĩa dữ liệu, cần PM chốt.
6. Sau khi sửa: đo `/stats` (`byCoreKindStage`) sau ~2 tuần; kỳ vọng `unknown/anr` giảm, còn `unknown/op-fail` **không đổi** (đúng bản chất).

## 8. Phần chưa kiểm

- **[CHƯA KIỂM]** Không có `CRASH_ADMIN_TOKEN`: không đọc được `title`, `component`, `stage`, `app`, `device`, `detail` của từng bản ghi trong 7 issue ⇒ **chưa biết** issue nào thuộc tiến trình chính / `:midlet` / `:game` trước `begin` / phiên lệch.
- **[CHƯA KIỂM]** Phiên bản app và máy của từng lượt: thân issue không chép; cột `Khoá seen` chỉ là hash ẩn danh.
- **[CHƯA KIỂM]** Tần suất `matchingDeathSession` loại phiên hợp lệ (mục 4d).
- **[CHƯA KIỂM]** Kết luận "#632 không đổi được ca nào" dựa trên mã hiện tại; nếu Worker còn bản ghi do client khác gửi (không qua `Diagnostics.kt`) thì có thể có ngoại lệ.
- **[CHƯA KIỂM]** #304 (`lowmem`, `app:game`) có cùng cơ chế "không phiên" hay không; nằm ngoài nhóm `anr`, chỉ ghi nhận để không lẫn.

## 9. Cách kiểm chứng lại

1. `scripts/crash-reports.sh <id>` cho các id trong 7 chữ ký (cần token) — đọc `title`/`component`/`stage`/`app`.
2. `scripts/crash-report-detail.py --file <report.json>` — in `Máy`, `Bản app`, `Lõi`, `Game`, `Hệ`, `Giai đoạn`.
3. Đối chiếu mã: `Diagnostics.kt:867`, `:898-905`, `:917-924`; `worker.js:832-836`; `crash-issue-bot.py:69`; `MainActivity` (github/play) `onResume`.
4. Kiểm tra lại số liệu bằng khối `<!-- aow-crash-v100:… -->` trong thân 7 issue (tổng 26 lượt / 16 khoá).
