# Nhóm `unknown` / `native`: vì sao báo cáo không gắn được lõi

Số dòng dẫn chiếu cập nhật theo `main` `f75b2e6d`.

Ngày 13/11/2026 · nhánh `deepseek1/unknown-native-1311` · nền `main` `f24ece45` · nhãn `crash-moi`, chỉ đọc (không sửa mã trong lượt này)

## 1. Kết luận ngắn

- Lọc đúng **lõi `unknown` + loại `native`** trong 75 issue `crash-moi` đang mở: **1 issue, 1 lượt, 1 máy** ([#387](https://github.com/aowvn-10diem/aowvn-monika/issues/387)). Một mẫu duy nhất nên **không thể nói máy nào / lõi nào**.
- Cả họ `unknown` (mọi loại) là **22 issue · 296 lượt · 94 máy**. Trong đó `native` 1, `op-fail` 13, `anr` 7, `resource` 1.
- `unknown` **không phải "app chết trước khi kịp ghi"** trong đa số trường hợp. Có **2 lỗi ghi nhận** đọc được từ mã (mục 3.2) và **1 nhóm đúng bản chất** (`op-fail` ngoài phiên game thì không có lõi để ghi).
- Hai lỗi ghi nhận: **(b)** ở nhánh chết do Android báo (`ApplicationExitInfo`), app chỉ đọc "phiên chết" cho `anr`/`lowmem`, bỏ `native`/`resource` (nhánh `:game` do `collect()` xử lý vẫn có lõi); **(c)** bot chỉ lấy `lõi` trong `session.core`, bỏ qua `core` cấp ngoài mà Worker đã ghi. Đề xuất sửa cụ thể ở mục 4, **chưa sửa mã** trong lượt này.
- `resource` #302 là ca riêng: game Java (`:midlet`) chưa từng có phiên nên **không** sửa được bằng (b) — cần thêm phiên cho J2ME (mục 4.4, xem (d)).

## 2. Bảng issue

Số liệu lấy từ thân issue ngày 13/11/2026 (`Số lần quan sát đã thông báo`, khối `<!-- aow-crash-v100:… -->`).

- `Lượt` = số lần bot đã cộng dồn (không phải số người).
- `Máy` = số khoá máy **ẩn danh** trong trường `seen` — **không** phải tên/model máy.
- `Tái diễn` = cùng chữ ký được cộng thêm lượt sau lần thông báo đầu (`lượt ≥ 2`).

### 2.1 Đúng nhóm `unknown` + `native` (1 issue)

| Issue | Loại | Lượt | Máy | Mở ngày | Tái diễn? |
|---|---|---:|---:|---|---|
| [#387](https://github.com/aowvn-10diem/aowvn-monika/issues/387) | `native` | 1 | 1 | 30/10 | Không |

Thân issue **không có** dòng `Thành phần` (bản bot cũ) và không có tên tiến trình, nên **chưa biết** đây là tiến trình chính hay `:game`, càng không biết lõi.

### 2.2 Cả họ `unknown` (22 issue)

| Issue | Loại | Lượt | Máy | Mở ngày | Tái diễn? |
|---|---|---:|---:|---|---|
| [#302](https://github.com/aowvn-10diem/aowvn-monika/issues/302) | `resource` | 236 | 50 | 20/10 | Có |
| [#393](https://github.com/aowvn-10diem/aowvn-monika/issues/393) | `op-fail` | 20 | 14 | 31/10 | Có (bản sửa cuối 05/11) |
| [#413](https://github.com/aowvn-10diem/aowvn-monika/issues/413) | `anr` | 15 | 5 | 01/11 | Có |
| [#534](https://github.com/aowvn-10diem/aowvn-monika/issues/534) | `anr` | 5 | 5 | 07/11 | Có |
| [#401](https://github.com/aowvn-10diem/aowvn-monika/issues/401) | `op-fail` | 2 | 2 | 31/10 | Có |
| [#399](https://github.com/aowvn-10diem/aowvn-monika/issues/399) | `anr` | 2 | 2 | 31/10 | Có |
| [#387](https://github.com/aowvn-10diem/aowvn-monika/issues/387) | `native` | 1 | 1 | 30/10 | Không |
| [#395](https://github.com/aowvn-10diem/aowvn-monika/issues/395) | `op-fail` | 1 | 1 | 31/10 | Không |
| [#398](https://github.com/aowvn-10diem/aowvn-monika/issues/398) | `anr` | 1 | 1 | 31/10 | Không |
| [#407](https://github.com/aowvn-10diem/aowvn-monika/issues/407) | `op-fail` | 1 | 1 | 01/11 | Không |
| [#414](https://github.com/aowvn-10diem/aowvn-monika/issues/414) | `anr` | 1 | 1 | 01/11 | Không |
| [#415](https://github.com/aowvn-10diem/aowvn-monika/issues/415) | `anr` | 1 | 1 | 01/11 | Không |
| [#419](https://github.com/aowvn-10diem/aowvn-monika/issues/419) | `anr` | 1 | 1 | 01/11 | Không |
| [#525](https://github.com/aowvn-10diem/aowvn-monika/issues/525) | `op-fail` | 1 | 1 | 06/11 | Không |
| [#531](https://github.com/aowvn-10diem/aowvn-monika/issues/531) | `op-fail` | 1 | 1 | 07/11 | Không |
| [#564](https://github.com/aowvn-10diem/aowvn-monika/issues/564) | `op-fail` | 1 | 1 | 09/11 | Không |
| [#608](https://github.com/aowvn-10diem/aowvn-monika/issues/608) | `op-fail` | 1 | 1 | 11/11 | Không |
| [#614](https://github.com/aowvn-10diem/aowvn-monika/issues/614) | `op-fail` | 1 | 1 | 12/11 | Không |
| [#366](https://github.com/aowvn-10diem/aowvn-monika/issues/366) | `op-fail` | 1 | 1 | 26/10 | Không |
| [#371](https://github.com/aowvn-10diem/aowvn-monika/issues/371) | `op-fail` | 1 | 1 | 27/10 | Không |
| [#376](https://github.com/aowvn-10diem/aowvn-monika/issues/376) | `op-fail` | 1 | 1 | 27/10 | Không |
| [#319](https://github.com/aowvn-10diem/aowvn-monika/issues/319) | `op-fail` | 1 | 1 | 20/10 | Không |

Nhóm `op-fail` đã có bản chẩn đoán riêng (`docs/loi/op-fail-1311.md`, PR #618); tệp này chỉ nói phần **lõi `unknown`**.

## 3. `unknown` nghĩa là gì trong mã

### 3.1 Lõi được ghi từ đâu

- Lõi nằm trong **phiên** (`Session.core`), do nơi mở game truyền vào lúc bắt đầu: `Diagnostics.begin(...)` ghi `Session(... core ...)` — `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:152-162`. Nơi gọi: `RetroActivity.kt:128` (`coreId`), `AzaharActivity.kt:107`, `RenpyGameActivity.kt:60`, `RgssGameActivity.kt:82`, `KirikiriGameActivity.kt:46`, `AzaharInstallActivity.kt:54`.
- Chết **trong tầm kiểm soát** (tiến trình game còn phiên): `Diagnostics.collect` đọc lại phiên và ghi báo cáo kèm `session` — `Diagnostics.kt:223-256`. Ca này **luôn có lõi** nếu nơi mở game truyền đúng.
- Chết **ngoài tầm kiểm soát** (Android báo qua `ApplicationExitInfo`): `collectProcessDeaths` — `Diagnostics.kt:845-895`.

### 3.2 Năm đường dẫn tới `unknown`

**(a) Đúng bản chất — `op-fail` không thuộc phiên game.** `opFail` gắn `session = read(c)` nếu đúng tiến trình (`Diagnostics.kt:490`); thao tác ngoài lúc chơi (tải, giải nén, cập nhật cấu hình) không có phiên nên không có lõi. Đây là 13/22 issue — **không phải lỗi**, chỉ là thiếu ngữ cảnh "thao tác nào" (đã bàn ở #618).

**(b) Lỗi ghi nhận trong app — `native`/`resource` không đọc phiên chết ở nhánh `ApplicationExitInfo`.**
- `Diagnostics.kt:867`: `val deathSession = if (kind == "anr" || kind == "lowmem") readDeathSession(...) else null` ⇒ với các lần chết do `collectProcessDeaths` xử lý (tiến trình chính, hoặc `:game` **không** có báo cáo từ `collect`), `native`, `resource`, `initfail` **luôn** có `session = null`.
- Riêng chết `native` của `:game` đi qua `Diagnostics.collect` (mục 3.1) vẫn **có** `session` và có lõi — lỗi (b) không áp cho nhánh đó.
- `Diagnostics.kt:917-924` (`enrichDeathReport`): chỉ điền `core`/`system`/`stage` khi `kind` là `lowmem`/`anr`, các loại khác `return r` nguyên trạng.
- Trong khi đó app **có** ghi sẵn ảnh chụp phiên cho mọi tiến trình đang chạy game (`Diagnostics.kt:201-216`, tệp `diag/sessions/<pid>.json`) và `matchingDeathSession` (`:898-905`) đã kiểm kỹ thế hệ tiến trình + PID + mốc thời gian, dùng được cho `native`.

⇒ `unknown`/`native` (#387) khớp lỗi này **nếu** báo cáo của nó là lần chết do `collectProcessDeaths` xử lý (chưa kiểm được, xem mục 5). **#302 không khớp (b)**: tiến trình `:midlet` không có phiên nào để đọc, nên sửa (1) vẫn trả `null` — #302 cần fix riêng ở (d)/(4.4).

**(c) Lỗi ghi nhận ở bot — bỏ `core` cấp ngoài.**
- Worker ghi `c: slim.core || slim.session?.core || ""` (`cloudflare/crash-worker/worker.js:557`) và với `anr`/`lowmem` còn ghi hẳn `core` cấp ngoài, mặc định `"unknown"` (`worker.js:832-836`).
- Bot chỉ đọc `session.core`: `scripts/crash-issue-bot.py:69` — `core = token(session.get("core", ""))`; `token()` trả `"unknown"` khi rỗng/không hợp lệ (`:26-27`). `core` cấp ngoài **không được dùng**, dù chữ ký nhóm cũng chỉ băm `lõi + loại + ≤3 frame` (`:71`).
- Hệ quả: báo cáo `anr`/`lowmem` có `core` thật ở cấp ngoài nhưng `session` rỗng vẫn bị gom vào `unknown` — khớp #413 (5 máy) và #534 (5 máy).

**(d) Đúng bản chất — chưa từng có phiên.** Game Java (J2ME) **không** gọi `Diagnostics.begin` (không có trong `j2me/`), nên tiến trình `:midlet` không có phiên, không có lõi. #302 (`app:midlet`, 236 lượt, 50 máy) vì thế `unknown` **theo thiết kế hiện tại**, không phải bot gán nhầm. Muốn có lõi phải thêm phiên cho J2ME (mục 4.4).

**(e) "Chết trước khi ghi" — có thật nhưng hẹp.** Native chết trong tiến trình chính lúc chưa mở game (chưa `begin`) thì không có phiên để đọc — `unknown` là đúng. Chưa có bằng chứng #387 thuộc ca này **[CHƯA KIỂM]**.

## 4. Đề xuất sửa (chưa sửa mã trong lượt này)

1. **`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:867`** — cho `native` (và `resource`, `initfail`) đọc phiên chết như `anr`:
   `val deathSession = if (kind in setOf("anr", "lowmem", "native", "resource")) readDeathSession(...) else null`.
   Chỉ có tác dụng khi tiến trình **có** ảnh chụp phiên (`diag/sessions/<pid>.json`) — tức tiến trình đã chạy game; **không giúp #302** (`:midlet` không có phiên).
   Test cần thêm (Robolectric, cùng chỗ `DeathContextTest.kt`): `native` + ảnh chụp phiên khớp (PID, thế hệ, mốc) → báo cáo có `session.core`; ảnh chụp lệch thế hệ → vẫn `null` (giữ nguyên hành vi chống gán nhầm).
2. **`Diagnostics.kt:918`** — mở `enrichDeathReport` cho `native`/`resource`/`initfail` để `core`/`system`/`stage` cấp ngoài cũng được điền (hiện chỉ `lowmem`/`anr`).
3. **`scripts/crash-issue-bot.py:69`** — `core = token(session.get("core") or report.get("core") or "")`. Test trong `scripts/test-crash-issue-bot.py`: `session` rỗng + `core` cấp ngoài `"melondsds"` → nhóm theo `melondsds`; cả hai rỗng → `unknown` (không đổi hành vi cũ).
4. **J2ME (#302)** — cần một phiên cho tiến trình `:midlet` để có lõi `j2me` + hệ `Java`. Module `j2me` không phụ thuộc `:app`, nên phải qua cầu (interface/callback do app đăng ký) hoặc app tự suy từ tên tiến trình; cả hai là thay đổi thiết kế, cần đặc tả riêng **[CHƯA KIỂM]** hiệu quả.
5. Sau khi sửa (1)-(3): đo lại bằng `/stats` (`byCoreKindStage`) sau ~2 tuần; kỳ vọng `unknown` giảm ở `anr`/`native` (và ở `resource` **chỉ khi** tiến trình có phiên), còn `unknown`/`op-fail` **không đổi** (đúng bản chất).

## 5. Phần chưa kiểm

- **[CHƯA KIỂM]** Lõi thật của #387, #302, #393: phiên này **không có** `CRASH_ADMIN_TOKEN` nên không đọc được `/reports/{id}` (không xem `detail`, `frames`, `component`, `stage`, `env`). Mọi kết luận về "lõi nào" ở trên là suy từ mã, không từ dữ liệu báo cáo.
- **[CHƯA KIỂM]** #387 thuộc tiến trình nào: thân issue bản cũ không có dòng `Thành phần`.
- **[CHƯA KIỂM]** Tỉ lệ `unknown` sau 0.8.13 (chưa có bản phát hành nào chứa thay đổi đề xuất).
- Số `Máy` là khoá ẩn danh trong `seen`, không phải model/thiết bị; không suy ra được máy yếu/mạnh.

Cách đo khi có token:
1. `scripts/crash-reports.sh <id>` cho #387, #302, #393 — đọc `component`, `stage`, `detail` để biết tiến trình/thao tác.
2. `scripts/crash-insight.py --official-only` — đối chiếu số `unknown` theo ngày và theo bản.
3. Chỉ mở issue mới khi có tốc độ tăng thật; không mở lại #387 vì 1 lượt.

## 6. Đề xuất bước tiếp

- Gộp 3 sửa nhỏ (1)-(3) thành **một PR mã** riêng (không gộp vào PR tài liệu này), kèm 3 test nêu trên.
- Giữ #387 mở nhưng **không ưu tiên** (1 lượt, 1 máy); chỉ nâng ưu tiên nếu sau khi sửa (1)-(2) vẫn còn `unknown`/`native`.
- #302: tách quyết định "có làm phiên cho J2ME hay không" thành việc riêng — hiện tần suất 236 lượt/50 máy là lớn nhất trong họ `unknown`, nhưng bản chất là "chưa có lõi", không phải app ghi sai.
