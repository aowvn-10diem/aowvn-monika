# `citra/anr` + `citra/killed`: 31 lượt còn lại sau #622/#623/#627/#635

Ngày 14/11/2026 — nhánh `chan-doan/citra-anr-killed` — mã đọc trên `origin/main` **`15f210a6`**.
**Chỉ tài liệu, không sửa mã.** Không đụng `config/`, workflow, `Diagnostics.kt`, `GLRetroView.kt`. Không đóng issue nào.

Tài liệu này **không lặp lại**: #622 `citra-native-1311.md` (lõi citra trôi bản, không symbols, nhãn sai), #635 `citra-con-lai-1313.md` (cả họ citra còn lại), #623 `ppsspp-anr-1311.md` (nghi phạm ANR của `:libretrodroid`), #627 (`Components.kt` hết gắn nhầm `engine:azahar`), #633 `melondsds-anr-1313.md` (§4 nghi phạm, §5.2 phép đo).
Ở đây chỉ có bốn việc: (1) bảng 4 issue `anr`/`killed`, (2) trả lời **cùng lớp hay đặc thù citra**, (3) đọc đúng nghĩa `killed`, (4) ánh xạ phần **đã phủ** và phần **chuyển sang phép đo §5.2**.

## 0. Đã được phủ ở đâu (không viết lại)

| Nguồn | Đã chốt | Áp cho `anr`/`killed` thế nào |
|---|---|---|
| #622 (đã gộp) `docs/loi/citra-native-1311.md` | Lõi citra **không ghim bản** (`version = "1"`, `nightly/latest`, không `sha256`, không gói symbols); 3DS đi lõi libretro | Dùng nguyên: cùng `.so`, cùng `CoreManager`/`RetroActivity`; không suy lại |
| #627 (đã gộp) | `Components.kt:19-34` cho dấu hiệu libretro **thắng** Azahar; `citra` đơn độc → `engine:libretro:citra` (`:41`) | #607 mang nhãn `engine:azahar` là **nhãn cũ**, không phải engine Azahar |
| #635 (PR mở) `docs/loi/citra-con-lai-1313.md` | Bảng 7 issue citra; `core-error`; `killed` là nhánh `else`; #617 **không** phải bản sửa #607 | Dẫn lại số liệu, không chép phân tích; tài liệu này bổ sung phép so **cùng lớp** với `melondsds/anr` |
| #623 (PR mở) `docs/loi/ppsspp-anr-1311.md` | Nghi phạm ANR chung của `:libretrodroid`: `onPause()` trên Main Thread chờ GLThread; chờ SRAM 1,5 s khi huỷ | Dùng nguyên cho 3 issue `citra/anr` |

**Khác biệt duy nhất của tài liệu này so với #635:** #635 trả lời "nhóm citra còn lại gồm những gì"; ở đây trả lời "nhóm `citra/anr` có phải **cùng một lớp lỗi** với `melondsds/anr` không, và `killed` thực sự là gì".

## 1. Bảng 4 issue

Số lấy từ khối `aow-crash-v100` trong thân issue (`gh issue view <n>`). **Lượt** = `count` bot cộng dồn; **báo cáo** = số khoá trong `seen` (`scripts/crash-issue-bot.py:76`, mỗi khoá = `digest(id báo cáo)[:32]`, `id` là mốc thời gian chết — `Diagnostics.kt:243,248`). Một báo cáo có thể mang `count > 1` vì app cộng dồn theo dấu vân tay trước khi gửi (`Diagnostics.kt:720`).

| Issue | Lượt | Báo cáo | Máy | Thành phần (thân issue) | Giai đoạn | Tái diễn? |
|---|---:|---:|---|---|---|---|
| [#336](https://github.com/aowvn-10diem/aowvn-monika/issues/336) `anr` | 9 | 9 | 9 báo cáo, **không có trong issue** | (trống — báo cáo trước #627) | không có trong issue | **có** — 9 báo cáo cùng một chữ ký, mở 23/10 (xem §2.3) |
| [#460](https://github.com/aowvn-10diem/aowvn-monika/issues/460) `anr` | 2 | 2 | 2 báo cáo | (trống) | không có | 2 báo cáo trong ngày 02/11 — chưa đủ mẫu |
| [#328](https://github.com/aowvn-10diem/aowvn-monika/issues/328) `anr` | 2 | 2 | 2 báo cáo | (trống) | không có | 2 báo cáo trong ngày 22/10 — chưa đủ mẫu |
| [#607](https://github.com/aowvn-10diem/aowvn-monika/issues/607) `killed` | **18** | **1** | **1 lần cài** (`seen` = một khoá, giá trị 18) | `engine:azahar` — **sai**, xem mục 3.2 | không có | **có, tất định trên một máy** — 18 lần chết cùng dấu vân tay, gửi một lần |
| **Tổng** | **31** | **14** | — | — | — | 27/31 lượt đến từ hai issue (#607 và #336) |

Đọc bảng:

- Hai con số 9 và 18 **không cùng đơn vị**. #336 = 9 lần chết trên 9 báo cáo; #607 = 18 lần chết trên **một** lần cài. Vì vậy không được nói "killed chiếm 58% nhóm citra" như một tín hiệu người dùng.
- Cột **Máy** để "báo cáo" chứ không phải "người": bot chỉ nhận `signature/count/seen` (`scripts/crash-issue-bot.py:128-136`) và **không** chép thiết bị/game (`scripts/crash-issue-bot.py:4`). Số máy thật chỉ có trong báo cáo gốc trên Worker.
- Cột **Giai đoạn** (`start` → `core-ready` → `view-created` → `first-frame` → `playing`, `Diagnostics.kt:50-51`) **không** nằm trong thân issue. Đây là trường quyết định tách "ANR lúc nạp lõi" khỏi "ANR lúc chơi" — xem §4.
- "Tái diễn" ở #336/#607 là suy từ hình dạng `seen` (nhiều báo cáo cùng chữ ký / một báo cáo mang 18 lượt), **không** phải từ ngày cập nhật issue.

## 2. `citra/anr` có cùng lớp với `melondsds/anr` không? — **Có, cùng lớp**

Trả lời ngắn: **cùng lớp, không phải cùng nguyên nhân đã chứng minh**. Cả hai nhóm đi đúng một đường mã; citra **không có nhánh ANR riêng** nào trong app. Khác biệt của citra chỉ là *độ nặng* của một đơn vị việc, tức làm nghi phạm dễ xảy ra hơn, không tạo lớp lỗi mới.

### 2.1 Bốn mắt xích dùng chung (không phải của riêng citra)

| Mắt xích | Bằng chứng (`tệp:dòng`) |
|---|---|
| Tiến trình `:game`, cùng `GLSurfaceView`/`LifecycleObserver` | `libretrodroid/.../GLRetroView.kt:358-374`; session do `Diagnostics.begin` ghi từ `RetroActivity.kt:128` |
| Main Thread chờ GLThread ở `onPause()` **không hạn** | `GLRetroView.kt:366-373`; `onPause()` của framework ở `:371`, rồi `LibretroDroid.pause()` `:372` |
| GLThread giữ `coreLock` suốt khung, gồm `retro_run()` và `fpsSync->wait()` | `libretrodroid.cpp:539-586` (`retro_run` `:553`, `renderFrame` `:558`, `fpsSync->wait()` `:562`) |
| Chờ SRAM **1 500 ms** trước `super.onDestroy()` | `RetroActivity.kt:646-650`, hằng `:691`; `saveSram()` hạn 300 ms `:669,689` |

Ngoài ra ANR được ghi **ở lần mở app kế tiếp** từ `ApplicationExitInfo.REASON_ANR` (`Diagnostics.kt:232-241`), chỉ trên API ≥ 30, trace lọc theo `INTERESTING` (`:301-326`), crumb của tiến trình chết được đọc lại ở `:246` (đường `:game`). Tất cả đều **không** phân biệt lõi.

### 2.2 Citra khác ở đâu — và vì sao đó **không** phải lớp lỗi mới

1. **Khung đầu nặng hơn.** `cores.citra` **không có `options`** (`config/monika-config.json:1120-1128`) trong khi `cores.melondsds` có khối `options` ngay sau (`:1135-1136`). Nghĩa là citra chạy mặc định độ phân giải/đồ hoạ của lõi. Hệ quả chỉ là **đơn vị việc ở §2.1 dài hơn**, làm `t1→t2` dễ vượt ngưỡng — không phải một đường chờ khác. **[CHƯA KIỂM]** độ dài thật.
2. **Dựng shader lần đầu — dùng chung, không riêng citra.** `Video::updateProgram()` biên dịch GLSL khi chương trình chưa có hoặc đổi cấu hình (`libretrodroid/src/main/cpp/video.cpp:102-137`), gọi trong `renderFrame()` (`:161`). Sau khi mất/đổi EGL context, `Video` được **dựng lại** (`libretrodroid.cpp:255-269`) nên chương trình bị biên dịch lại ở khung kế tiếp — đúng lúc người chơi vừa resume/đổi kiểu hiển thị rồi thoát. Mã này áp cho **mọi** lõi dùng `:libretrodroid`; chính `GLRetroView.kt:469-470` đã ghi nhận "GLThread đang biên dịch shader … rất lâu". Citra chỉ là lõi nặng nhất trong nhóm.
3. **Citra là lõi HW-render.** `onSurfaceCreated()` gọi `retro_get_system_av_info` (`libretrodroid.cpp:235`), **huỷ** context HW cũ (`:239-240`) rồi **reset** context HW mới (`:269-270`) — đều trên GLThread. Việc này làm khung đầu sau khi khôi phục surface dài hơn; vẫn là cùng một đơn vị việc. **[CHƯA KIỂM]** phần citra tự làm bên trong `retro_run` (JIT, dựng shader PICA).
4. **`.cia` — không còn là đường ANR.** Từ PR #611 (gộp), nhánh libretro **chặn** `.cia` trước khi mở core: `app/src/github/java/vn/aow/monika/runner/GameLauncher.kt:52-53` gọi `ciaBlockedReason(...)`; định nghĩa `:139-142`; bản `play` tương ứng `app/src/play/java/vn/aow/monika/runner/GameLauncher.kt:109`. Vì vậy `.cia` chỉ có thể là nguyên nhân `core-error` **trước** bản có chặn (#301/#610), **không** phải ANR ở bản hiện tại. **[CHƯA KIỂM]** cho các báo cáo cũ trước #611.
5. **Thiếu sysdata → hỏng lúc nạp, không phải ANR.** `cores.citra` không khai `systemFiles` (`config:1120-1128`) nên `CoreManager.isReady` bỏ qua kiểm (`app/src/main/java/vn/aow/monika/runner/CoreManager.kt:57`); thư mục hệ thống có thể rỗng (`CoreManager.kt:34`, `RetroActivity.kt:326`). Thiếu sysdata làm `retro_load_game` thất bại hoặc nạp chậm trên **GLThread** → `core-error` hoặc làm khung đầu dài; **không** chặn Main Thread trực tiếp. **[CHƯA KIỂM]**.

### 2.3 Một khác biệt về **hình dạng mẫu** (đáng chú ý, ngược với `melondsds`)

`melondsds/anr` có 9 issue = 9 tổ hợp frame native khác nhau (`melondsds-anr-1313.md` §2) — tức nhóm đó là **hỗn hợp điểm chờ**.
`citra/anr` chỉ có **3 chữ ký**, và #336 là **9 báo cáo khác nhau nằm đúng một chữ ký**. Vì chữ ký = băm `[lõi, loại, tối đa 3 frame]` (`scripts/crash-issue-bot.py:39-53,71`), **nếu** `frames()` không rỗng thì 9 lần chết của #336 có cùng ba frame native đầu — một điểm chờ lặp lại được, khác hẳn "nhiều điểm chờ" của melondsds.

Nhưng **không** được suy tiếp thành "đã biết điểm chờ": thân issue không mang `detail` nên ba frame đó là gì vẫn **[CHƯA KIỂM]**. Còn một khả năng khác phải loại trừ: nếu `frames()` trả **rỗng** (trace protobuf bị cắt vụn), mọi ANR của citra sẽ gộp vào một chữ ký — khi đó con số 9 **không** chứng minh frame giống nhau. Ba chữ ký khác nhau của ba issue nghiêng về giả thuyết `frames()` **không** rỗng, nhưng chỉ báo cáo gốc mới chốt được. Cách đọc đúng là §5.1 của `melondsds-anr-1313.md` (đọc frame native đầu của luồng `main` trong báo cáo gốc), không tìm chuỗi `onPause` (đã bị `INTERESTING` lọc — `Diagnostics.kt:326`).

**Kết luận §2:** `citra/anr` = **cùng lớp** `melondsds/anr`; nghi phạm giữ nguyên là **(a)** Main Thread chờ một đơn vị việc GLThread (`GLRetroView.kt:371`) và **(b)** 1 500 ms chờ ghi SRAM (`RetroActivity.kt:649`). Citra không thêm nghi phạm mới; nó chỉ làm (a) nặng hơn trong ~60 giây đầu. **Không** sửa `step()`/`coreLock` (lý do ở `melondsds-anr-1313.md` §4.4 và §6).

## 3. `citra/killed` — nhánh `else`, **không** đồng nghĩa hết RAM

### 3.1 `killed` là gì trong mã

`Diagnostics.collect` chỉ tách 4 loại rõ; **mọi** lý do còn lại rơi vào `else -> "killed"`:

| `ApplicationExitInfo.reason` | `kind` | Ghi chú |
|---|---|---|
| `REASON_CRASH_NATIVE` | `native` | `Diagnostics.kt:233` |
| `REASON_CRASH` | `java` | `:234` |
| `REASON_ANR` | `anr` | `:235` |
| `REASON_LOW_MEMORY` | `lowmem` | `:236` |
| `REASON_USER_REQUESTED` / `REASON_USER_STOPPED` / `REASON_EXIT_SELF` | **không báo cáo** | `:237-238` (thoát bình thường) |
| `REASON_SIGNALED`, `REASON_INITIALIZATION_FAILURE`, `REASON_EXCESSIVE_RESOURCE_USAGE`, `REASON_DEPENDENCY_DIED`, `REASON_OTHER`, … | **`killed`** | `:240` (`else`) |

Ba hệ quả:

- **`killed` bao gồm cả bị giết vì thiếu RAM.** Nhánh `lowmem` chỉ đúng khi Android *tự khai* `REASON_LOW_MEMORY`. Khi `lmkd`/kernel `SIGKILL` vì áp lực bộ nhớ, lý do thường là `REASON_SIGNALED`/`REASON_OTHER` ⇒ vẫn vào `killed`. Vì vậy "killed ≠ hết RAM" đúng theo nghĩa **không suy ra**, nhưng **cũng không loại trừ** hết RAM.
- **`killed` không có trace.** `traceStrings` trả rỗng trừ `CRASH_NATIVE`/`ANR` (`Diagnostics.kt:303`), nên `detail` của killed rỗng; `log` là logcat của pid đã chết (thường rỗng). `reason` thì **có** và mang `status`, `description`, `importance`, `pss` (`Diagnostics.kt:284-298`) — đọc một báo cáo là biết ngay là `SIGNALED` hay `OTHER`.
- **`killed` chỉ phát từ tiến trình `:game`.** `collect` cần tệp session (`Diagnostics.kt:223-228`), mà session chỉ được ghi khi mở game (`RetroActivity.kt:128`). Đường quét tiến trình chính `collectProcessDeaths` **bỏ** mọi lý do ngoài 5 nhánh (`Diagnostics.kt:861`). Nên #607 chắc chắn là tiến trình game bị hệ thống tắt — **không** phải app chính.

### 3.2 #607: 18 lượt nhưng **một** lần cài — nhãn `engine:azahar` là nhãn cũ

- `seen` chỉ có **một** khoá, giá trị **18** ⇒ một báo cáo, một lần cài, 18 lần chết cùng dấu vân tay (`Diagnostics.kt:720`; bot cộng `count` của báo cáo, `scripts/crash-issue-bot.py:72-76`).
- Vì vậy #607 **không đại diện người dùng chính thức**, kể cả khi chứng thư là bản phát hành: mẫu là **một thiết bị**. Nếu chứng thư là bản mod/gói lại thì càng không đại diện. Xem §3.3 — chưa kiểm được chứng thư.
- Nhãn `engine:azahar` trong thân issue là nhãn **trước #627**: luật cũ khớp chữ `citra` trước luật libretro. Sau #627 mọi báo cáo mới vào `engine:libretro:citra` (`Components.kt:19-34,41`). Nhãn này **không** có nghĩa #607 chạy engine Azahar — 3DS không còn trường `engine` từ configVersion 44 (`config:199-210`), và #617 sửa `AzaharBridge.ask()` nên **không** phải bản sửa của #607 (đã chốt ở #635 §5.2).
- Chữ ký của `killed` **không phải** điểm sập: `frames()` băm `detail` + `log` (`scripts/crash-issue-bot.py:39-53`), mà detail rỗng (`Diagnostics.kt:303`) và `log` là logcat của pid đã chết, không phải stack ANR ⇒ chữ ký killed phản ánh **dòng log tình cờ**, không phải một khung lỗi; nếu log cũng rỗng thì mọi `killed` của một lõi gộp thành một chữ ký. Cả hai trường hợp đều cho cùng kết luận: chữ ký `killed` **không dùng để triage nguyên nhân**. Đây là lý do các issue `killed` của citra cứ ra chữ ký mới (#308/#485/#511 đã đóng, #607 mới) dù có thể cùng một hiện tượng: **đóng theo chữ ký không làm hết nhóm**. **[CHƯA KIỂM]** nội dung log thật của #607.

### 3.3 Chứng thư của #607 — **chưa kiểm được**, và cách kiểm

Cách đúng (đã ghi ở #635 §5.3): chạy `scripts/crash-insight.py` hai lần.

```text
CRASH_ADMIN_TOKEN=<mã> python3 scripts/crash-insight.py --out-dir out     # mặc định: chỉ báo cáo khớp chứng thư phát hành
CRASH_ADMIN_TOKEN=<mã> python3 scripts/crash-insight.py --out-dir out --all-certs   # gộp cả bản mod/gói lại
```

Mặc định script **loại** báo cáo mang chứng thư khác `c46902e97029` (`scripts/crash-insight.py:8-10,34`); chênh lệch số của nhóm `citra / killed` giữa hai lần là câu trả lời. Kèm trường `a` (bản app) và `reason` để biết #607 đến từ bản nào, lý do gì.

Lượt này **không chạy được**, ba rào cản:

1. Không có `CRASH_ADMIN_TOKEN` trong môi trường phiên này (không đọc secret, không thử auth lại).
2. `gh workflow run crash-insight.yml` bị **403** (PAT không có quyền Actions) — nên không dùng được đường CI.
3. Lần chạy CI gần nhất là **08/11** (`success`), artifact giữ **3 ngày** (`retention-days: 3`) ⇒ đã hết hạn; và #607 tạo **11/11**, muộn hơn cả lần chạy đó.

⇒ **Chứng thư của #607: [CHƯA KIỂM].** Không được viết "#607 là bản mod" cũng không được viết "#607 là bản phát hành". Chỉ được viết điều suy ra từ cấu trúc: **một lần cài, 18 lần chết**.

## 4. Đã phủ / chuyển sang phép đo §5.2

| Phần | Trạng thái | Nơi xử lý |
|---|---|---|
| Vì sao lõi citra trôi bản, không symbols, `.cia` từng vào lõi | **đã phủ** | #622 |
| Nhãn `engine:azahar` sai; báo cáo mới vào `engine:libretro:citra` | **đã phủ, đã sửa mã** | #627 |
| `core-error` của citra (mã lỗi, `onCoreFailure` chỉ trước khung đầu) | **đã phủ** | #635 §3 |
| `.cia` bị chặn ở nhánh libretro | **đã sửa mã** | PR #611 (`GameLauncher.kt:52-53,139-142`) |
| Cơ chế ANR chung: `onPause()` chờ GLThread, `coreLock` trong `step()` (đã hạ), chờ SRAM 1,5 s | **đã phủ** | #623 + `melondsds-anr-1313.md` §4 |
| `citra/anr` **cùng lớp** `melondsds/anr`, không có nhánh riêng | **chốt ở tài liệu này** | §2 |
| `killed` = nhánh `else`; #607 = 1 lần cài; chữ ký killed là băm log | **chốt ở tài liệu này** | §3 |
| **Số đo `t1→t2`, `t7→t8`, `t0→t1` cho citra** | **chưa đo — chuyển sang §5.2** | `melondsds-anr-1313.md` §5.2 |
| Chứng thư / `reason` / `stage` của #607 và 3 issue anr | **chưa đọc — chờ token** | §3.3, §5.1 |

### 4.1 Chuyển nguyên phép đo, chỉ thêm ba ghi chú cho citra

Kế hoạch đo **giữ nguyên** `melondsds-anr-1313.md` §5.2 (cầu nối `Diag.sink` từ `:libretrodroid` sang app, crumb `pause:t0…t11`, đọc bằng `elapsedRealtime`). Hai điểm cần sửa khi áp cho citra:

- **Crumb của báo cáo `anr` đường `:game` được đọc ở `Diagnostics.kt:246`**, không phải `:866`. §5.2 dẫn `:866` — đó là `collectProcessDeaths` (tiến trình chính). Với `citra/anr`, session tồn tại nên báo cáo đi qua `collect`; crumb vẫn lên báo cáo, nhưng dẫn đúng dòng.
- **Đọc kèm `stage` để tách "nạp lõi" khỏi "đang chơi".** Với citra, giả thuyết đặc thù duy nhất (§2.2) đều dồn vào `stage = first-frame` (`Diagnostics.kt:50-51`, `RetroActivity.kt:360`). Nếu đa số báo cáo là `playing` thì citra **không** khác melondsds về bản chất và không cần phân tích riêng nữa.
- **Không thêm mốc riêng cho shader/HW context.** Chúng nằm trong `t1→t2` (một đơn vị việc của GLThread); tách thêm mốc bên trong `step()` sẽ phải sửa `libretrodroid.cpp` — ngoài phạm vi và đụng đúng vùng `coreLock` mà #623/`melondsds-anr-1313.md` §6 đã loại.

## 5. Việc tiếp theo (rẻ → đắt)

1. **Đọc báo cáo trước khi sửa** (cần token): `crash-insight.py` + `--all-certs` cho #607 (chứng thư, `a`, `reason`); `scripts/crash-reports.sh <id>` + `crash-report-detail.py` cho #336/#460/#328 (`stage`, frame native đầu, crumb `sram`/`pause`).
2. **Chạy §5.2 trên một bản thử** (cần PM duyệt vì thêm `Diag.kt` + vài dòng crumb): lấy `t1→t2` và `t7→t8` cho citra; so với melondsds trên cùng máy.
3. **Hỏi người chơi #336 `adb bugreport`** ngay sau khi treo — không có bugreport thì mọi kết luận khung vẫn `[CHƯA KIỂM]`.
4. **Không mở việc riêng cho #460/#328** (2 báo cáo mỗi cái) và **không mở việc theo chữ ký `killed`** (§3.2).
5. **Không đóng #607** bằng #617; chỉ xét sau khi có `reason` thật + qua ≥ 2 bản không cộng lượt (ngưỡng bot: `delta ≥ 5`, `crash-issue-bot.py:118`).

## 6. `[CHƯA KIỂM]` và giới hạn bằng chứng

- Không có `CRASH_ADMIN_TOKEN` ⇒ không đọc được `detail`/`log`/`stage`/`reason`/`cert` của bất kỳ báo cáo nào trong 4 issue.
- Thân issue **không** có phiên bản, đời máy, tên game, mã lỗi; mọi suy theo ngày tạo chỉ là phỏng đoán.
- "Cùng lớp" là kết luận **từ mã** (cùng `GLRetroView`/`RetroActivity`/`Diagnostics`), **không** phải từ khung đã giải mã.
- Việc #336 có 9 báo cáo cùng chữ ký chỉ nói `frames()` giống nhau (hoặc cùng rỗng), **không** nói frame nào; nội dung frame **[CHƯA KIỂM]**.
- Chứng thư #607 **[CHƯA KIỂM]**; kết luận "một lần cài" chỉ dựa trên `seen` có một khoá.
- `count` là cộng dồn theo dấu vân tay trên máy (`Diagnostics.kt:720`), không phải số người; bot chỉ cộng lượt issue khi `delta ≥ 5`.

## 7. Rủi ro

- **Số dòng có thể trôi.** Tài liệu chốt `15f210a6`; nếu PR sau chèn dòng vào `libretrodroid.cpp` (vùng ≥ 158) thì `pause()`/`step()` dịch xuống — đọc lại theo tên hàm, đừng tin số dòng.
- **#635 đang mở.** Nếu #635 gộp trước, phần §3.1 trùng ý với §5.1 của #635; giữ tài liệu này như bản **so sánh lớp** + `killed`, không nhân đôi bảng 7 issue.
- **`killed` dễ bị dùng sai làm KPI.** 18 lượt của một máy có thể bị đọc thành "lỗi phổ biến". Chỉ số đúng là **số báo cáo (1)**, không phải lượt.
- **Đừng "sửa ANR citra" bằng cách hạ cấu hình đồ hoạ** khi chưa có `stage`: nếu đa số ANR là `playing` chứ không phải `first-frame`, hạ cấu hình không đổi được gì mà làm xấu game.
- **Không** sửa `step()`/`coreLock`, không đụng `Diagnostics.kt`/workflow/`config/` trong lượt này.
