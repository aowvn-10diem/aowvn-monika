# Nhóm `melondsds / anr`: 9 chữ ký còn lại sau #606/#619

Ngày 13/11/2026 — nhánh `chan-doan/melondsds-anr-1313` — mã đọc trên `origin/main` **`fa88ddd0`** (dẫn chiếu `Diagnostics.kt` giữ nguyên từ `0e9ef5bd`; `5b94cbf6` của #618 là bản chèn 11 dòng cuối cùng vào tệp đó).
**Chỉ tài liệu, không sửa mã.** Không gộp, không đụng `config/`.

**Bản 2 (13/11, sau phân tích ~14:00Z ở #337):** giả thuyết **`coreLock` đã bị hạ** khỏi vị trí nghi phạm chính (lý do + `tệp:dòng` ở §4.4); nghi phạm còn đứng được là **độ dài một đơn vị việc của GLThread** (§4.1) và **1 500 ms chờ SRAM** (§4.3); `requestExitAndWait()` chỉ còn ở **nhánh huỷ không `isFinishing`** (§4.2). Cách kiểm: đọc `detail`/`stage` (§5.1) và phép đo bằng crumb có cầu nối `:libretrodroid` → app (§5.2).

Tài liệu này **không nhắc lại** ba giả thuyết (a)/(b)/(c) đã viết ở `docs/loi/melondsds-anr-1311.md` (PR #619, đã gộp). Ở đây chỉ thêm: (1) bảng số của cả 9 issue, (2) **[đính chính]** cách chữ ký của bot được tính — hệ quả là nhóm này không thể là một nguyên nhân duy nhất, (3) bảng quyết định đọc `detail`/`stage` để tách nhánh, (4) **bản 2**: hạ giả thuyết `coreLock` và phép đo thay thế (§4, §5.2).

## 1. Bảng nhóm (9 issue `crash-moi`, tổng 421 lượt)

Số lấy từ khối `aow-crash-v100` trong thân từng issue. Cột **"số báo cáo"** = số khoá trong `seen`; theo `scripts/crash-issue-bot.py:76` mỗi khoá là `digest(id báo cáo)[:32]` và `id` chính là mốc thời gian của lần chết (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:877`), nên **mỗi khoá ≈ một lần gửi báo cáo**, còn giá trị của khoá là `count` mà máy đó đã cộng dồn trước khi gửi.

| Issue | Lượt (cộng dồn) | Số báo cáo | Lặp nhiều nhất trong 1 báo cáo | Máy | Phiên bản | Giai đoạn | Tái diễn? |
|---|---:|---:|---:|---|---|---|---|
| #350 `d98da506cbec` | 216 (51%) | 14 | 33 | không có trong issue | không có trong issue | không có trong issue | **có, dày** — 216/14 ≈ 15 lượt mỗi báo cáo, một máy lặp 33 lần |
| #318 `9a90aaa75e95` | 177 (42%) | 52 | 21 | không có trong issue | không có trong issue | không có trong issue | **có, rộng** — 177/52 ≈ 3,4 lượt mỗi báo cáo, nhiều máy |
| #314 `03d00b1fe612` | 17 (4%) | 11 | 3 | không có | không có | không có | có, thưa (17/11 ≈ 1,5) |
| #533 `efb3d440e3fb` | 3 | 2 | 2 | không có | không có | không có | có (2 báo cáo) |
| #529 `3f83495c63f7` | 3 | 2 | 2 | không có | không có | không có | có (2 báo cáo) |
| #439 `1234bc7608a8` | 2 | 1 | 2 | không có | không có | không có | có (1 báo cáo mang 2 lượt) |
| #597 `43db74d66a37` | 1 | 1 | 1 | không có | không có | không có | chưa biết (một lần) |
| #530 `4e1e2d8c1490` | 1 | 1 | 1 | không có | không có | không có | chưa biết (một lần) |
| #440 `2ac1f7179798` | 1 | 1 | 1 | không có | không có | không có | chưa biết (một lần) |
| **Tổng** | **421** | **85** | 33 | — | — | — | 421/85 ≈ 5 lượt mỗi báo cáo |

Đọc bảng:

- Hai chữ ký chiếm **93%** lượt (#350 + #318 = 393/421). **Bảy issue còn lại 28 lượt** (421 − 393), trong đó #314 = 17 còn sáu issue ≤ 3 lượt chỉ 11 lượt — **không đủ mẫu** để kết luận gì; đừng mở việc sửa riêng cho chúng.
- #350 và #318 khác nhau về *hình dạng phân bố*: #350 dồn vào ít báo cáo nhưng lặp rất nhiều trên cùng máy (33 lần) → giống một vòng lặp **tất định** theo máy/cấu hình; #318 trải trên 52 báo cáo → giống một điều kiện chung (mọi máy đều gặp khi làm đúng thao tác). Đây là suy luận từ phân bố, **không phải bằng chứng nguyên nhân** — `[CHƯA KIỂM]`.
- Cột **máy / phiên bản / giai đoạn để trống** vì thân issue chỉ có `signature`, `count`, `seen` — bot cố tình không chép thiết bị, game, URL (`scripts/crash-issue-bot.py:4`). Ba trường này **có** trong báo cáo gốc trên Worker: `device`, `app`, và `session.stage` (riêng `stage` được ghi thêm cho `anr`/`lowmem` ở `cloudflare/crash-worker/worker.js:832-836`). Muốn điền cột thì phải đọc Worker (mục 5).
- Số "lượt" là **cộng dồn lịch sử**, không phải mức tăng hiện tại (xem `docs/loi/lowmem-1311.md` §1, đã gộp qua PR #621) — đừng dùng để nói "đang tăng".

## 2. [Đính chính] chữ ký của bot không phải "3 frame Java đầu" — nó là **3 frame native đầu**

Chuỗi suy luận, đọc theo mã:

1. Bot gộp nhóm bằng `signature = digest([core, kind, frames(report)])` — `scripts/crash-issue-bot.py:71`.
2. `frames()` lấy **3 dòng đầu tiên** khớp hoặc frame Java `at x.y(Z)` hoặc frame native `#NN pc … <mod>.so (symbol)` — `scripts/crash-issue-bot.py:39-53`.
3. Với `kind = anr`, `detail` **không** phải stack Java: nó là trace lấy từ `ApplicationExitInfo.traceInputStream` rồi **lọc theo `INTERESTING`** (`.so|libretro|SIG…|::|libGLES|libEGL|vulkan|…`) — `Diagnostics.kt:302-326` (không đổi sau #618), dùng ở `:865`.
4. Hệ quả: một dòng Java như `at android.opengl.GLSurfaceView$GLThread.onPause(...)` **không khớp** `INTERESTING` nên bị loại khỏi `detail`; chỉ frame native còn lại. Vì vậy chữ ký ANR thực chất là **tổ hợp frame native** của trace.

Ba hệ quả thực dụng:

- **9 issue = 9 tổ hợp frame native khác nhau**, không phải 9 lần cùng một chỗ. Nhóm `melondsds / anr` là **hỗn hợp nhiều điểm chờ**, không phải một lỗi. Bất kỳ tài liệu nào (kể cả `melondsds-anr-1311.md`) nói "nguyên nhân là X" cho cả nhóm đều chưa đủ bằng chứng — `[CHƯA KIỂM]`.
- **Không bao giờ thấy `GLSurfaceView.onPause` trong chữ ký.** Câu "nếu trace rơi vào `GLSurfaceView.onPause` thì…" ở `melondsds-anr-1311.md` (PR #619, đã gộp) **§3(c)**, hướng xử lý ở **§6 mục 2**, chỉ đúng nếu đọc `adb bugreport`/logcat ANR, **không** đúng nếu chỉ đọc `detail` của báo cáo. Cách đọc đúng: nhìn **frame native đầu tiên** rồi tra bảng ở §5.1.
- Nếu trace ở dạng protobuf bị cắt vụn, `frames()` có thể trả rỗng → mọi ANR của một lõi **gộp thành một chữ ký**. Ở nhóm này ta thấy 9 chữ ký nên các báo cáo đang có frame đọc được; nhưng khi thấy một issue `anr` có `count` lớn bất thường mà `signature` lạ, nên kiểm `detail` trước khi tin — `[CHƯA KIỂM]` cho phần này.

## 3. Sau #606 và #619 còn lại những đường nào (để không sửa lại lần hai)

`#606` (đã gộp) và `#619` (đã gộp, `EmulationCall.kt` có trên `main`) đã bọc **mọi** lời gọi JNI phát từ UI Thread bằng hàng đợi GLThread có hạn:

| Đường | Trạng thái trên `main` | Vị trí |
|---|---|---|
| `viewport`, `viewportAlignment`, `setControllerType` | chỉ `queueEvent`, không chờ | `GLRetroView.kt:71-80`, `:269-272` |
| `frameSpeed`, `shader`, `updateVariables` | `EmulationCall.post` — không chờ | `GLRetroView.kt:59-67`, `:280-285` |
| `getVariables` | `runBounded` 2 000 ms, hết hạn trả rỗng | `GLRetroView.kt:274-279`, `EmulationThreadTask.kt:74` |
| `runOnEmulationThread` | hạn 2 000 ms + giá trị dự phòng | `GLRetroView.kt:464-488` |
| chụp SRAM | hạn 300 ms, hết hạn bỏ lượt lưu | `GLRetroView.kt:234-241`, `RetroActivity.kt:669,689` |

`#574` (giải phóng Coil/feed/AdBlock) **không liên quan** nhóm này: nó chạy ở **tiến trình chính**, còn ANR ở đây mang thành phần `engine:libretro:melondsds`, tức tiến trình `:game` (`app/src/github/AndroidManifest.xml:82`). Đừng tính #574 vào phần "đã xử lý" của nhóm.

Vậy đường còn lại **không đi qua mã app** — đúng như kết luận của `docs/loi/ppsspp-anr-1311.md` **§2** (tài liệu đó **chưa lên `main`**, đang ở PR **#623**) — nên §4 chỉ còn **hai nghi phạm chính** (§4.1, §4.3) và **một nhánh hiếm** (§4.2), bên cạnh giả thuyết đã hạ (§4.4).

## 4. Nghi phạm còn đứng được — sau khi **hạ** giả thuyết `coreLock`

Bản 1 của tài liệu này (head `93255a3`) xếp (d) "khoá `coreLock` bị giữ quá lâu" là **nghi phạm chính**. **Sai.** Phân tích 13/11 ~14:00Z ở #337 (`[Trưởng nhóm OpenCode → PM] đề xuất xử lý coreLock`) đọc lại **từng** đường của Main Thread: Main Thread **không** chờ `coreLock` tới mức gây ANR — thứ nó chờ là **Java monitor của GLThread**. Bằng chứng ở §4.4. Từ đây còn **hai nghi phạm chính** (#1 §4.1, #3 §4.3) và **một nhánh hiếm** (#2 §4.2), tất cả quy về **độ dài một đơn vị việc của GLThread** hoặc **thời gian chờ có sẵn trong vòng đời Activity**; **chưa cái nào được đo** — mọi kết luận vẫn `[CHƯA KIỂM]`.

### 4.1 `GLRetroView.kt:371` — chờ Java monitor của GLThread (nghi phạm số 1)

- `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:366-373`: observer `ON_PAUSE` gọi `touchInput.pause(...)` (`:370`), rồi **`onPause()` của `GLSurfaceView`** (`:371`) — hàm này chờ GLThread báo đã dừng và **không có tham số hạn**; rồi `LibretroDroid.pause()` (`:372`).
- **Không phải chờ khoá:** `pause()` (`libretrodroid/src/main/cpp/libretrodroid.cpp:506-507`) có lấy `coreLock`, nhưng nó đứng **sau** `:371`, lúc GLThread đã pause ⇒ chờ ≈ 0 (§4.4).
- Thời gian chờ = **một đơn vị việc** của GLThread (đo đúng chặng `t1→t2` ở §5.2): khung đang dở ở `Renderer.onDrawFrame` (`GLRetroView.kt:377-385` → `LibretroDroid.step`). Đơn vị việc **dài nhất** không phải khung chơi bình thường mà là lúc GLThread đang nạp: `initializeCore()` (`GLRetroView.kt:403-431`: `createCore` + `loadGameFromPath` + `onSurfaceCreated`) và khung đầu có `updateProgram()` (`libretrodroid/src/main/cpp/video.cpp:161` — biên dịch GLSL).
- `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:635-641` — `onPause()` của Activity gọi `saveSram()` (`:639`, hạn 300 ms) rồi mới `super.onPause()`. Nhưng `ON_PAUSE` (và do đó observer ở `:366-373`) được phát **trước** thân `onPause()` — xem §5.2 — nên chặng chờ GL ở `:371` xảy ra **trước** `saveSram()`; 300 ms SRAM cộng vào cùng ngân sách 5 giây nhưng nằm **sau**, không phải trước, chặng chờ GL.
- Nghi phạm này chỉ "đứng" khi ca ANR rơi vào **~60 giây đầu** (`StartupProgress.kt:40`): người chơi bấm Quay lại / chuyển app đúng lúc lõi đang nạp. `[CHƯA KIỂM]` — chỉ `stage` của báo cáo mới phân biệt được, xem §5.2.
- Frame native kỳ vọng: `libart.so (art::Monitor::Wait)` hoặc `libc.so (__epoll_pwait)` — dấu hiệu **Java wait**, tức đang đứng ở `:371`.

### 4.2 `requestExitAndWait()` — chỉ còn ở nhánh huỷ **không** `isFinishing` (nghi phạm yếu)

- **Trên đường thoát bình thường, chặng này không tồn tại.** `RetroActivity.kt:646-657`: sau `super.onDestroy()` (`:651`), nhánh `isFinishing` gọi `killProcess` (`:656`) **trước khi** `ActivityThread.handleDestroyActivity` kịp `removeViewImmediate` ⇒ view chưa bị gỡ, `GLSurfaceView.onDetachedFromWindow`/`requestExitAndWait` **không bao giờ chạy**. Lưu ý: `super.onDestroy()` **không** gỡ view — nó chỉ chạy callback vòng đời; việc gỡ nằm ở `ActivityThread` **sau khi** `onDestroy()` trả về.
- Nhánh còn lại: activity bị huỷ **không** do người dùng thoát (`isFinishing == false`) — ví dụ đổi `uiMode`/`density`, vì `RetroActivity` không khai hai cờ đó trong `configChanges` (`app/src/github/AndroidManifest.xml:83`). Khi đó view mới bị gỡ và `requestExitAndWait()` (**không hạn**) chờ GLThread chạy hết hàng đợi; hàng đợi có `destroy()` (`GLRetroView.kt:153` → `retro_unload_game` + `retro_deinit`, `libretrodroid.cpp:469-470`).
- `destroy()` lấy khoá **bên trong** hàng đợi GL (`libretrodroid.cpp:454`) ⇒ không phải chờ `coreLock` từ Main Thread, mà chờ Java monitor của GLThread.
- Vì hiếm và **không** nằm trên đường "thoát game", đây là **nghi phạm yếu**: chỉ đứng nếu trace cho thấy ca huỷ không-finishing. `[CHƯA KIỂM]`.
- Frame native kỳ vọng: `libart.so (art::Monitor::Wait)`; nếu thấy `pthread_mutex_lock` của `libretrodroid.so` thì là ca khác (§4.4).

### 4.3 1 500 ms chờ ghi SRAM trước khi gỡ view (nghi phạm số 3)

- `RetroActivity.kt:646-650`: `runBlocking { withTimeoutOrNull(SRAM_WRITE_WAIT_MS) { sramJob?.join() } }`, hằng **1 500 ms** ở `:691`. Đứng **trước** `super.onDestroy()` nên cộng thẳng vào thời gian Main Thread bận — đo bằng chặng `t7→t8` (§5.2).
- Chỉ có `sramJob` khi đã qua khung đầu: `saveSram()` thoát sớm nếu `!firstFrame` (`:667`), nên nghi phạm này chỉ đứng với ca **đã chơi rồi mới thoát**.
- **Hệ quả cần kiểm:** `saveSram()` chạy **sau** khi view đã pause (§5.2), nên lời gọi `serializeSRAMOrNull` được xếp vào GLThread trong lúc GLThread đang đứng ⇒ có thể không được chạy tới khi resume, hết hạn 300 ms và **bỏ lượt lưu**. Nếu báo cáo thật có crumb `sram` "chụp quá hạn" ở mọi lần ra nền thì đó là lỗi mất SRAM, không chỉ là chuyện ANR — `[CHƯA KIỂM]`.
- Nếu ANR xảy ra lúc thoát game trên máy ghi `.srm` chậm thì **1,5 s chờ ghi SRAM** (`t7→t8`) cộng 300 ms `saveSram` là tổ hợp đáng ngờ nhất; **`requestExitAndWait` không nằm trên đường này** (§4.2). `[CHƯA KIỂM]`.

### 4.4 (đã hạ) `coreLock` / `step()` — **không** phải nghi phạm

Bảng rà từng đường Main Thread (đọc mã trên `origin/main` `fa88ddd0`):

| Đường (Main Thread) | Lấy `coreLock`? | Thật ra chờ gì |
|---|---|---|
| `GLRetroView.kt:371` `onPause()` (framework) | không | **Java monitor** của GLThread, không hạn |
| `GLRetroView.kt:372` `LibretroDroid.pause()` | có (`libretrodroid.cpp:507`) | ≈ 0: đứng **sau** `:371`, GLThread đã dừng |
| `GLRetroView.kt:361` `LibretroDroid.resume()` | có (`libretrodroid.cpp:485`) | ≈ 0: gọi **trước** `onResume()` (`:362`), GLThread còn pause |
| `RetroActivity.kt:656` `killProcess` (nhánh `isFinishing`) | không | **không chờ gì** — tiến trình chết ngay, `requestExitAndWait` không chạy (§4.2) |
| `GLRetroView.kt:71-80` `setViewport` | có, nhưng chạy **trong** `queueEvent` = GLThread (`libretrodroid.cpp:744`) | Main Thread không chờ |

Vì sao giả thuyết cũ không đứng:

- **Thứ tự gọi**: hai chỗ Main Thread **có** vào `coreLock` là `:361` (resume) và `:372` (pause), cả hai đều **sau khi GLThread đã dừng** — nên thời gian chờ khoá thực tế ≈ 0 (chặng `t2→t3`, §5.2). Cái chặn Main Thread là **Java monitor**, không phải `std::mutex`.
- **Trần giữ khoá quá thấp**: `coreLock` là `std::mutex` thường (`libretrodroid.h:185`), mọi chỗ dùng `std::lock_guard`; bằng chứng không re-entrant: `resume()` đang giữ khoá phải gọi `refreshAspectRatioLocked()` (`libretrodroid.cpp:503`) chứ không được gọi `refreshAspectRatio()` (`:573` — bản sau khoá lại và **tự deadlock**). `step()` giữ khoá suốt `:520-566`, **nhưng** trên máy 60 Hz: NDS 59,83 fps ⇒ `|content − screen| < 5` ⇒ `useVSync = true` (`fpssync.cpp:41`) ⇒ `wait()` **return ngay** (`fpssync.cpp:59-61`) và `advanceFrames()` trả 1 ⇒ **trong khoá không có lúc nào ngủ**; trần = `frameSpeed` lần `retro_run`, tua 4× ⇒ ~67 ms.
- **Xấu nhất vẫn không đủ**: màn 120 Hz ⇒ `useVSync = false` ⇒ ngủ trong khoá; tua 4× ⇒ 8 × 16,7 ms + `renderFrame` ≈ **150 ms**. Cách ngưỡng ANR 5 000 ms hơn 30 lần.
- **Câu ở bản 1 phải rút lại**: "trên màn 90/120 Hz `coreLock` gần như không bao giờ rảnh" **chỉ đúng ở tần số đó**, và kể cả vậy mỗi lần giữ khoá chỉ ~150 ms; trên 60 Hz còn không ngủ trong khoá. Nó **không** chứng minh được ANR.
- **Bỏ `fpsSync->wait()` ra ngoài khoá cũng không giúp**: `onPause()` (và `requestExitAndWait()` ở nhánh hiếm §4.2) chờ **cả hàm `onDrawFrame`**, không chờ khoá — ngủ ngoài khoá vẫn nằm trong khung, thời gian chờ **y nguyên**. Xem §6 để biết vì sao hướng đó còn nguy hiểm hơn.
- Nếu trace lại chỉ ra `pthread_mutex_lock` + `libretrodroid.so` trong 3 frame đầu của luồng `main` thì đó **không** phải `step()` — mọi đường vào khoá từ Main Thread đã rà ở bảng trên — mà là một đường JNI khác chưa rà; khi đó mở issue mới kèm trace.

### 4.5 Mở game lần đầu (nạp lõi trên GLThread) — **không tự gây ANR**, chỉ làm §4.1 nặng hơn

- `RetroActivity.kt:308` đặt `initializeOnGlThread = true` → `GLRetroView.kt:403-431` chạy `createCore()` + `loadGameFromPath()` + `LibretroDroid.onSurfaceCreated()` (`libretrodroid.cpp:204-241`) **trên GLThread**; Main Thread chỉ hiện overlay, không chờ.
- Nên (c) không phải nguyên nhân trực tiếp; nó là **điều kiện** để đơn vị việc ở §4.1 dài nhất trong ~60 giây đầu (mốc mềm `StartupProgress.kt:40`, dùng ở `RetroActivity.kt:287-292`). `[CHƯA KIỂM]` về tỉ lệ thật.

## 5. Cách kiểm

### 5.1 Đọc trace ANR của báo cáo (không cho biết thời gian)

1. **Đọc báo cáo gốc.** `CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh "<id>"` (chi tiết một báo cáo) rồi `python scripts/crash-report-detail.py <id>` — script in `Máy`, `Bản app`, `Lõi`, **`Giai đoạn`** (`crash-report-detail.py:126`) và toàn bộ `detail` (`:128`). Đây là hai lệnh duy nhất cần cho việc tách nhóm.
2. **Đọc frame native đầu tiên** trong `detail` (dòng `#00 pc …` của luồng `"main"`), không tìm chuỗi `onPause` — chuỗi đó đã bị lọc mất (mục 2). Bảng quyết định:

| Frame native đầu của `main` | Kết luận | Việc tiếp |
|---|---|---|
| `libart.so (art::Monitor::Wait)` / `libc.so (__epoll_pwait)` | chờ **Java monitor** — §4.1 (lúc pause); nhánh huỷ không-finishing là §4.2; phân biệt bằng `stage` | chạy phép đo §5.2 |
| `libc.so (__futex_wait_ex)` + `libretrodroid.so` trong 3 frame đầu | chờ `coreLock` từ một đường JNI **chưa rà** — **không** phải `step()` (§4.4) | mở issue mới kèm trace |
| `libGLESv2.so` / `libEGL.so` (`glLinkProgram`, `eglSwapBuffers`) | khung đang dựng shader — §4.1 | tái hiện: đổi kiểu hiển thị rồi thoát ngay |
| `libc.so (__ioctl)` / `libc.so (read)` trên luồng khác | nghi luồng I/O (SAF, firmware, savestate) | `[CHƯA KIỂM]` |
3. **Đếm theo `stage`** (đã có sẵn cho `anr`): nếu phần lớn báo cáo là `first-frame` thì nghiêng §4.1 lúc nạp; `playing` thì là khung chơi; `paused` (sau khi gộp #624 — PR đang mở, thêm `stage = paused`) thì là ca **ở nền**.
4. **Thống kê tổng hợp:** `python scripts/crash-insight.py --out-dir <thư mục>` — mặc định **loại** báo cáo mang chứng thư khác bản phát hành (`partition_by_cert`); muốn gộp cả bản mod/dựng lại thì thêm `--all-certs`, và khi đó phải nói rõ trong kết luận.

### 5.2 Phép đo trực tiếp — phương án A đã đề xuất cho PM (không đổi hành vi)

**Bước 1 bắt buộc — cầu nối chẩn đoán từ `:libretrodroid` sang app.** `t1`, `t2`, `t3` nằm trong `GLRetroView` (module `:libretrodroid`), mà module này **không** thấy lớp app (`git grep vn.aow.monika -- libretrodroid` rỗng) nên **không** gọi được `Diagnostics.crumb`. Phải thêm một sink một dòng:

```kotlin
// libretrodroid/src/main/java/com/swordfish/libretrodroid/Diag.kt
object Diag { @Volatile var sink: ((String) -> Unit)? = null }
```

app gán sink lúc tạo view (`RetroActivity.kt:343-346`): `Diag.sink = { msg -> Diagnostics.crumb(this, "pause", msg) }`; trong `RenderLifecycleObserver.pause()` gọi `Diag.sink?.invoke("t1 <elapsedRealtime>")`, `"t2 …"`, `"t3 …"`. (Tiền lệ cùng kiểu: `startupListener` của `GLRetroViewData` — app truyền callback vào module.) **Trước khi có cầu nối, `t1→t3` là `[CHƯA ĐO ĐƯỢC]`** — không được trình bày như đã đo được.

**Cửa sổ đo phải bắt đầu TRƯỚC thân `onPause()`.** Với `lifecycle-runtime` 2.8.7 / `activity-compose` 1.9.3 (`gradle/libs.versions.toml:11-12`), `ON_PAUSE` được phát ở `Application.ActivityLifecycleCallbacks.onActivityPrePaused` (API 29+) — tức `ReportFragment.LifecycleCallbacks` chạy **trước** `RetroActivity.onPause():635`; với API 26–28 (`minSdk = 26`), AOSP `Activity.performPause` gọi `mFragments.dispatchPause()` **trước** `onPause()`. Nên chặng chờ GLThread ở `GLRetroView.kt:371` xảy ra **trước** dòng đầu `onPause()` của Activity. Đặt `t0` ở đầu `onPause()` sẽ bỏ mất đúng phần có thể là nguyên nhân: `super.onPause()` (`:640`) luôn ~0 và bảng kết luận sẽ **xoá oan nghi phạm #1**. Cùng lý do, `requestExitAndWait()` (nếu có) xảy ra **sau khi** `onDestroy()` trả về ⇒ `super.onDestroy()` (`:651`) cũng **không** bọc nó; và trên đường thoát bình thường nó không chạy (§4.2).

**Mốc gốc `t0`:** `Application.ActivityLifecycleCallbacks.onActivityPrePaused` (API 29+; tiền lệ đăng ký callbacks trong repo: `loader/src/main/java/vn/aow/monika/loader/MonikaLoaderProvider.java:62`). API 26–28 **không có** callback này ⇒ `t0 ≈ t1` (bắt đầu observer) và chặng `t0→t1` **không đo được** — phải ghi rõ trong crumb.

**Nguyên tắc ghi crumb:** ghi một crumb **trước** mỗi lời gọi có thể chặn (nếu tiến trình bị ANR-kill giữa chặng, crumb mở cuối cùng cho biết đang đứng ở đâu) và một crumb **sau khi** lời gọi trả về. Giá trị là `SystemClock.elapsedRealtime()`, nhờ vậy tính được hiệu giữa hai mốc bất kỳ. Trong app dùng `Diagnostics.crumb` tag `pause` (`Diagnostics.kt:178`); trong `:libretrodroid` dùng `Diag.sink` (bước 1).

| Chặng | Crumb mở | Crumb đóng | Module | Đo được ngay? |
|---|---|---|---|---|
| Hệ thống/framework trước khi vào view | `pause:t0` (`onActivityPrePaused`) | `pause:t1` (dòng đầu `RenderLifecycleObserver.pause()`, `GLRetroView.kt:367`) | app + `:libretrodroid` | **không** — cần bước 1 |
| **Chờ GLThread — nghi phạm #1** | `pause:t1` | `pause:t2` (ngay sau `onPause()` của `GLSurfaceView`, `:371`, trả về) | `:libretrodroid` | **không** — cần bước 1 |
| `LibretroDroid.pause()` | `pause:t2` | `pause:t3` (sau `:372`) | `:libretrodroid` | **không** — cần bước 1 |
| Phần còn lại của dispatch | `pause:t3` | `pause:t4` (dòng đầu `RetroActivity.onPause():635`) | app | **không** (cần `t3`) |
| `saveSram()` | `pause:t4` | `pause:t5` (sau `:639`) | app | có |
| `super.onPause()` | `pause:t5` | `pause:t6` (sau `:640`) | app | có |
| Chờ ghi SRAM | `pause:t7` (dòng đầu `onDestroy():646`) | `pause:t8` (sau `runBlocking { join }`, `:649`) | app | có |
| `super.onDestroy()` | `pause:t8` | `pause:t9` (sau `:651`) | app | có — **không** gồm `requestExitAndWait` |
| `requestExitAndWait` (tuỳ chọn, **không** phải đường thoát) | `pause:t10` (ngay trước `root.removeView(retroView)`) | `pause:t11` (sau khi trả về) | app | chỉ ở nhánh `!isFinishing` |

- **`t10/t11` không dùng cho đường thoát game.** Khi `isFinishing == true`, tiến trình bị `killProcess` ở `RetroActivity.kt:656` trước khi `ActivityThread` kịp `removeViewImmediate`, nên `onDetachedFromWindow` không chạy: chặng này **không xảy ra, không cần đo, và không đo được**. Chỉ nhánh huỷ không-finishing (ví dụ đổi `uiMode`/`density` — `AndroidManifest.xml:83`) mới có; muốn có số thì nhánh đo tự gỡ view (`root.removeView(retroView)`) và bọc crumb trước/sau. Nếu không thêm lời gọi đó ⇒ `[CHƯA KIỂM]`. **Không** lấy `onStop()` làm mốc: AOSP `performDestroyActivity` gọi `callActivityOnStop` **trước** `onDestroy` nên hiệu sẽ âm.
- Vì sao đọc được: crumb của tiến trình chết được đọc lại cho báo cáo `anr` (`Diagnostics.kt:866`, `val crumbs = Breadcrumbs.read(c, e.pid)`), nên các mốc xuất hiện ngay trong báo cáo ANR kế tiếp của #350/#318/#314.
- Cách đọc kết quả:

| Số đo | Kết luận |
|---|---|
| `t1→t2` ≥ 2 000 ms *(sau bước 1)* | **nghi phạm #1 xác nhận** — chờ GLThread; `stage=first-frame` ⇒ lúc nạp lõi (§4.5), `stage=playing` ⇒ một khung chơi dài bất thường |
| `t0→t1` ≥ 2 000 ms *(sau bước 1, API 29+)* | hệ thống/framework chậm **trước** khi vào view — không phải GLThread, không phải `coreLock`; xét I/O/SAF/binder `[CHƯA KIỂM]` |
| `t10→t11` ≥ 2 000 ms *(chỉ nhánh `!isFinishing`)* | nghi phạm #2 — `requestExitAndWait`/`retro_deinit`; trên đường thoát game chặng này **không tồn tại** |
| `t7→t8` ≈ 1 500 ms | **nghi phạm #3 xác nhận** — chờ ghi SRAM |
| `t2→t3` ≥ 2 000 ms *(sau bước 1)* | chờ `coreLock` thật — quay lại §4.4 và tìm đường JNI chưa rà |
| **`t5→t6` ≈ 0** | **KHÔNG** chứng minh nghi phạm #1 vô can: phần chờ GLThread nằm ở `t1→t2`, **trước** thân `onPause()`. Muốn kết luận vô can phải thấy cả `t0→t1` và `t1→t2` đều nhỏ |
| mọi chặng < 200 ms mà vẫn ANR | không nghi phạm nào đứng → quay lại nhánh I/O/firmware/savestate (`[CHƯA KIỂM]`) |
- Đối chiếu nhanh trên máy thật: `adb logcat -s MonikaGame | Select-String 'Davey!'` (khung > 2 s), rồi đọc crumb `pause` qua `scripts/crash-reports.sh <id>`.
- **Chưa có số nào** trong phiên này (không có `CRASH_ADMIN_TOKEN`, chưa chạy máy thật, chưa có cầu nối bước 1) — toàn bộ bảng trên là kế hoạch, không phải kết quả.

## 6. Vì sao **không** sửa `step()` / thu hẹp `coreLock`

- `fpsSync` là `std::unique_ptr` (`libretrodroid.h:190`), bị `destroy()` giải phóng (`libretrodroid.cpp:476`) và bị `afterGameLoad()` gán lại (`:698`). Hôm nay nó chỉ an toàn **nhờ** `step()` giữ khoá suốt khung.
- `destroy()` có đường **gọi thẳng từ Main Thread** khi `initializeOnGlThread = false` (`GLRetroView.kt:154`).
- Thu hẹp khoá quanh `fpsSync->wait()` ⇒ Main Thread có thể chạy `destroy()` đúng lúc GLThread đang ngủ **ngoài** khoá nhưng vẫn cầm con trỏ `fpsSync` ⇒ **use-after-free → SIGSEGV native** (mất phiên, có thể mất lượt lưu SRAM cuối). Đổi một ANR hiếm thành một crash chắc chắn hơn.
- Đổi lại **không** giảm được thời gian chờ của Main Thread (§4.4: nó chờ cả `onDrawFrame`, không chờ khoá). ⇒ Không làm, trừ khi có thiết kế khoá khác (ví dụ `shared_ptr`/cờ sống) và test riêng — ngoài phạm vi tài liệu này.

## 7. Còn lại / rủi ro

- **Chưa có trace và chưa có số đo nào** cho 9 issue này (không có `CRASH_ADMIN_TOKEN`, chưa chạy máy thật). Việc đầu tiên của lượt sau: chạy §5.1 cho #350/#318/#314, rồi §5.2 để có số; sau đó điền lại bảng §1 (máy/phiên bản/giai đoạn).
- **Không** triển khai hướng cũ (thu hẹp `coreLock`, bỏ `fpsSync->wait()` ra ngoài khoá): vừa không giảm thời gian chờ của Main Thread, vừa mở use-after-free `fpsSync` (§6) — hỏng nặng hơn lỗi đang có.
- Nếu §5.2 xác nhận §4.1: phương án B (không gọi `onPause()` của view khi chưa có khung đầu) là thay đổi **hành vi vòng đời** (giữ EGL surface, tắt cổng ghi âm thanh thay vì `stop()`) ⇒ cần PM duyệt riêng và test máy thật.
- **Đính chính đã gửi PM:** bảng ưu tiên 13/11 12:07Z ở #337 ghi nhóm này "nghi phạm `coreLock`" — theo bản 2, dòng đó **sai**; bảng ưu tiên nên đọc lại theo §4.4 (không sửa `step()`/`coreLock`).
- Sáu issue ≤ 3 lượt (#533, #529, #439, #597, #530, #440): **không mở việc riêng**, chỉ gộp vào bảng theo dõi; mẫu quá nhỏ.
- Không đóng issue nào; không sửa `config/`; không đụng `GLRetroView`/`EmulationCall` (đã chốt ở #619), các tệp của #574, hay `step()`/`coreLock`.
