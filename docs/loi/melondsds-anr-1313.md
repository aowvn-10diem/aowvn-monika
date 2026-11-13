# Nhóm `melondsds / anr`: 9 chữ ký còn lại sau #606/#619

Ngày 13/11/2026 — nhánh `chan-doan/melondsds-anr-1313` — mã đọc trên `origin/main` **`fa88ddd0`** (dẫn chiếu `Diagnostics.kt` giữ nguyên từ `0e9ef5bd`; `5b94cbf6` của #618 là bản chèn 11 dòng cuối cùng vào tệp đó).
**Chỉ tài liệu, không sửa mã.** Không gộp, không đụng `config/`.

**Bản 2 (13/11, sau phân tích ~14:00Z ở #337):** giả thuyết **`coreLock` đã bị hạ** khỏi vị trí nghi phạm chính (lý do + `tệp:dòng` ở §4.4); nghi phạm còn đứng được là **độ dài một đơn vị việc của GLThread**, **`requestExitAndWait()` khi thoát** và **1 500 ms chờ SRAM** (§4.1–4.3), kèm phép đo trực tiếp bằng `Diagnostics.crumb` (§5.2).

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

Vậy đường còn lại **không đi qua mã app** — đúng như kết luận của `docs/loi/ppsspp-anr-1311.md` **§2** (tài liệu đó **chưa lên `main`**, đang ở PR **#623**) — nên §4 chỉ còn ba nghi phạm và một giả thuyết đã hạ.

## 4. Nghi phạm còn đứng được — sau khi **hạ** giả thuyết `coreLock`

Bản 1 của tài liệu này (head `93255a3`) xếp (d) "khoá `coreLock` bị giữ quá lâu" là **nghi phạm chính**. **Sai.** Phân tích 13/11 ~14:00Z ở #337 (`[Trưởng nhóm OpenCode → PM] đề xuất xử lý coreLock`) đọc lại **từng** đường của Main Thread: Main Thread **không** chờ `coreLock` tới mức gây ANR — thứ nó chờ là **Java monitor của GLThread**. Bằng chứng ở §4.4. Từ đây còn ba nghi phạm, tất cả đều quy về **độ dài một đơn vị việc của GLThread** hoặc **thời gian chờ có sẵn trong vòng đời Activity**, và **chưa cái nào được đo** — mọi kết luận vẫn `[CHƯA KIỂM]`.

### 4.1 `GLRetroView.kt:371` — chờ Java monitor của GLThread (nghi phạm số 1)

- `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:366-373`: observer `ON_PAUSE` gọi `touchInput.pause(...)` (`:370`), rồi **`onPause()` của `GLSurfaceView`** (`:371`) — hàm này chờ GLThread báo đã dừng và **không có tham số hạn**; rồi `LibretroDroid.pause()` (`:372`).
- **Không phải chờ khoá:** `pause()` (`libretrodroid/src/main/cpp/libretrodroid.cpp:506-507`) có lấy `coreLock`, nhưng nó đứng **sau** `:371`, lúc GLThread đã pause ⇒ chờ ≈ 0 (§4.4).
- Thời gian chờ = **một đơn vị việc** của GLThread: khung đang dở ở `Renderer.onDrawFrame` (`GLRetroView.kt:377-385` → `LibretroDroid.step`). Đơn vị việc **dài nhất** không phải khung chơi bình thường mà là lúc GLThread đang nạp: `initializeCore()` (`GLRetroView.kt:403-431`: `createCore` + `loadGameFromPath` + `onSurfaceCreated`) và khung đầu có `updateProgram()` (`libretrodroid/src/main/cpp/video.cpp:161` — biên dịch GLSL).
- `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:635-641` — `onPause()` của Activity gọi `saveSram()` (`:639`, hạn 300 ms) **trước** `super.onPause()`, nên 300 ms đó tiêu vào cùng ngân sách 5 giây trước khi vào đường chờ GL.
- Nghi phạm này chỉ "đứng" khi ca ANR rơi vào **~60 giây đầu** (`StartupProgress.kt:40`): người chơi bấm Quay lại / chuyển app đúng lúc lõi đang nạp. `[CHƯA KIỂM]` — chỉ `stage` của báo cáo mới phân biệt được, xem §5.2.
- Frame native kỳ vọng: `libart.so (art::Monitor::Wait)` hoặc `libc.so (__epoll_pwait)` — dấu hiệu **Java wait**, tức đang đứng ở `:371`.

### 4.2 `requestExitAndWait()` khi đóng game (nghi phạm số 2)

- `RetroActivity.kt:646-657` — `onDestroy()` chờ SRAM (§4.3) **trước** `super.onDestroy()` (`:651`); `super.onDestroy()` gỡ view khỏi cây → framework `GLSurfaceView.onDetachedFromWindow` → `requestExitAndWait()` (**không hạn**) chờ GLThread chạy hết hàng đợi.
- Trong hàng đợi còn `destroy()` — `GLRetroView.kt:153` → `retro_unload_game` + `retro_deinit` (`libretrodroid.cpp:469-470`). `destroy()` lấy khoá **bên trong** hàng đợi GL (`libretrodroid.cpp:454`), nên đây **không** phải chờ `coreLock` từ Main Thread — vẫn là chờ Java monitor của GLThread.
- Cộng dồn: 1 500 ms chờ SRAM (§4.3) + `requestExitAndWait` (không hạn) nằm trong **cùng** ngân sách 5 giây của một lần ANR. `[CHƯA KIỂM]` cho phần `retro_deinit` của melondsds dài bao lâu.
- Frame native kỳ vọng: `libart.so (art::Monitor::Wait)`; nếu thấy `pthread_mutex_lock` của `libretrodroid.so` thì là ca khác (§4.4).

### 4.3 1 500 ms chờ ghi SRAM trước khi gỡ view (nghi phạm số 3)

- `RetroActivity.kt:646-650`: `runBlocking { withTimeoutOrNull(SRAM_WRITE_WAIT_MS) { sramJob?.join() } }`, hằng **1 500 ms** ở `:691`. Đứng **trước** `super.onDestroy()` nên cộng thẳng vào thời gian Main Thread bận.
- Chỉ có `sramJob` khi đã qua khung đầu: `saveSram()` thoát sớm nếu `!firstFrame` (`:667`), nên nghi phạm này chỉ đứng với ca **đã chơi rồi mới thoát**.
- Nếu ANR xảy ra lúc thoát game trên máy ghi `.srm` chậm thì 1,5 s + `requestExitAndWait` là tổ hợp đáng ngờ nhất. `[CHƯA KIỂM]`.

### 4.4 (đã hạ) `coreLock` / `step()` — **không** phải nghi phạm

Bảng rà từng đường Main Thread (đọc mã trên `origin/main` `fa88ddd0`):

| Đường (Main Thread) | Lấy `coreLock`? | Thật ra chờ gì |
|---|---|---|
| `GLRetroView.kt:371` `onPause()` (framework) | không | **Java monitor** của GLThread, không hạn |
| `GLRetroView.kt:372` `LibretroDroid.pause()` | có (`libretrodroid.cpp:507`) | ≈ 0: đứng **sau** `:371`, GLThread đã dừng |
| `GLRetroView.kt:361` `LibretroDroid.resume()` | có (`libretrodroid.cpp:485`) | ≈ 0: gọi **trước** `onResume()` (`:362`), GLThread còn pause |
| `RetroActivity.kt:651` `super.onDestroy()` → `requestExitAndWait()` | không | **Java monitor** GLThread, không hạn |
| `GLRetroView.kt:71-80` `setViewport` | có, nhưng chạy **trong** `queueEvent` = GLThread (`libretrodroid.cpp:744`) | Main Thread không chờ |

Vì sao giả thuyết cũ không đứng:

- **Thứ tự gọi**: hai chỗ Main Thread **có** vào `coreLock` là `:361` (resume) và `:372` (pause), cả hai đều **sau khi GLThread đã dừng** — nên thời gian chờ khoá thực tế ≈ 0. Cái chặn Main Thread là **Java monitor**, không phải `std::mutex`.
- **Trần giữ khoá quá thấp**: `coreLock` là `std::mutex` thường (`libretrodroid.h:185`), mọi chỗ dùng `std::lock_guard`; bằng chứng không re-entrant: `resume()` đang giữ khoá phải gọi `refreshAspectRatioLocked()` (`libretrodroid.cpp:503`) chứ không được gọi `refreshAspectRatio()` (`:573` — bản sau khoá lại và **tự deadlock**). `step()` giữ khoá suốt `:520-566`, **nhưng** trên máy 60 Hz: NDS 59,83 fps ⇒ `|content − screen| < 5` ⇒ `useVSync = true` (`fpssync.cpp:41`) ⇒ `wait()` **return ngay** (`fpssync.cpp:59-61`) và `advanceFrames()` trả 1 ⇒ **trong khoá không có lúc nào ngủ**; trần = `frameSpeed` lần `retro_run`, tua 4× ⇒ ~67 ms.
- **Xấu nhất vẫn không đủ**: màn 120 Hz ⇒ `useVSync = false` ⇒ ngủ trong khoá; tua 4× ⇒ 8 × 16,7 ms + `renderFrame` ≈ **150 ms**. Cách ngưỡng ANR 5 000 ms hơn 30 lần.
- **Câu ở bản 1 phải rút lại**: "trên màn 90/120 Hz `coreLock` gần như không bao giờ rảnh" **chỉ đúng ở tần số đó**, và kể cả vậy mỗi lần giữ khoá chỉ ~150 ms; trên 60 Hz còn không ngủ trong khoá. Nó **không** chứng minh được ANR.
- **Bỏ `fpsSync->wait()` ra ngoài khoá cũng không giúp**: `onPause()`/`requestExitAndWait()` chờ **cả hàm `onDrawFrame`**, không chờ khoá — ngủ ngoài khoá vẫn nằm trong khung, thời gian chờ **y nguyên**. Xem §6 để biết vì sao hướng đó còn nguy hiểm hơn.
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
| `libart.so (art::Monitor::Wait)` / `libc.so (__epoll_pwait)` | chờ **Java monitor** — §4.1 (lúc mở/pause) hoặc §4.2 (lúc thoát); phân biệt bằng `stage` | chạy phép đo §5.2 |
| `libc.so (__futex_wait_ex)` + `libretrodroid.so` trong 3 frame đầu | chờ `coreLock` từ một đường JNI **chưa rà** — **không** phải `step()` (§4.4) | mở issue mới kèm trace |
| `libGLESv2.so` / `libEGL.so` (`glLinkProgram`, `eglSwapBuffers`) | khung đang dựng shader — §4.1 | tái hiện: đổi kiểu hiển thị rồi thoát ngay |
| `libc.so (__ioctl)` / `libc.so (read)` trên luồng khác | nghi luồng I/O (SAF, firmware, savestate) | `[CHƯA KIỂM]` |
3. **Đếm theo `stage`** (đã có sẵn cho `anr`): nếu phần lớn báo cáo là `first-frame` thì nghiêng §4.1 lúc nạp; `playing` thì là khung chơi; `paused` (sau khi gộp #624 — PR đang mở, thêm `stage = paused`) thì là ca **ở nền**.
4. **Thống kê tổng hợp:** `python scripts/crash-insight.py --out-dir <thư mục>` — mặc định **loại** báo cáo mang chứng thư khác bản phát hành (`partition_by_cert`); muốn gộp cả bản mod/dựng lại thì thêm `--all-certs`, và khi đó phải nói rõ trong kết luận.

### 5.2 Phép đo trực tiếp — phương án A đã đề xuất cho PM (không đổi hành vi)

Đây là cách **duy nhất** biến `[CHƯA KIỂM]` thành số mà không sửa mã native: đặt đồng hồ vào đúng 4 chỗ Main Thread có thể bận trong `RetroActivity.kt:635-658`.

- Chỗ đo: `onPause()` (`:635-641`) bọc `saveSram()` (`:639`) và `super.onPause()` (`:640`); `onDestroy()` (`:646-657`) bọc `runBlocking { withTimeoutOrNull(SRAM_WRITE_WAIT_MS) { sramJob?.join() } }` (`:649`) và `super.onDestroy()` (`:651`). Dùng `SystemClock.elapsedRealtime()`, ngưỡng 200 ms, ghi `Diagnostics.crumb(this, "pause", "chờ gl sram=…ms super=…ms stage=…")`.
- Vì sao đọc được: crumb của tiến trình chết được đọc lại cho báo cáo `anr` (`Diagnostics.kt:866`, `val crumbs = Breadcrumbs.read(c, e.pid)`), nên số đo xuất hiện ngay trong báo cáo ANR kế tiếp của #350/#318/#314.
- Cách đọc kết quả:

| Số đo | Kết luận |
|---|---|
| `super=` ≥ 2 000 ms, `stage=first-frame` | chờ GLThread lúc nạp — §4.1; đi tiếp phương án B (không gọi `onPause()` của view khi chưa có khung đầu) |
| `super=` ≥ 2 000 ms, `stage=playing` | chờ GLThread ở khung chơi — phải tìm vì sao **một** khung dài ≥ 2 s (JIT/shader) `[CHƯA KIỂM]` |
| `join=` ≈ 1 500 ms và/hoặc `destroy=` lớn | §4.2 + §4.3 — cắt ngân sách chờ SRAM/`retro_deinit` |
| mọi số < 200 ms mà vẫn ANR | **cả ba nghi phạm trên sai** → quay lại nhánh I/O/firmware/savestate (`[CHƯA KIỂM]`) |
- Đối chiếu nhanh trên máy thật: `adb logcat -s MonikaGame | Select-String 'Davey!'` (khung > 2 s), rồi đọc crumb `pause` qua `scripts/crash-reports.sh <id>`.
- **Chưa có số nào** trong phiên này (không có `CRASH_ADMIN_TOKEN`, chưa chạy máy thật) — toàn bộ bảng trên là kế hoạch, không phải kết quả.

## 6. Vì sao **không** sửa `step()` / thu hẹp `coreLock`

- `fpsSync` là `std::unique_ptr` (`libretrodroid.h:190`), bị `destroy()` giải phóng (`libretrodroid.cpp:476`) và bị `afterGameLoad()` gán lại (`:698`). Hôm nay nó chỉ an toàn **nhờ** `step()` giữ khoá suốt khung.
- `destroy()` có đường **gọi thẳng từ Main Thread** khi `initializeOnGlThread = false` (`GLRetroView.kt:154`); `reset()` (`libretrodroid.cpp:655-658`) cũng có đường `useEmulationThread = false`.
- Thu hẹp khoá quanh `fpsSync->wait()` ⇒ Main Thread có thể chạy `destroy()` đúng lúc GLThread đang ngủ **ngoài** khoá nhưng vẫn cầm con trỏ `fpsSync` ⇒ **use-after-free → SIGSEGV native** (mất phiên, có thể mất lượt lưu SRAM cuối). Đổi một ANR hiếm thành một crash chắc chắn hơn.
- Đổi lại **không** giảm được thời gian chờ của Main Thread (§4.4: nó chờ cả `onDrawFrame`, không chờ khoá). ⇒ Không làm, trừ khi có thiết kế khoá khác (ví dụ `shared_ptr`/cờ sống) và test riêng — ngoài phạm vi tài liệu này.

## 7. Còn lại / rủi ro

- **Chưa có trace và chưa có số đo nào** cho 9 issue này (không có `CRASH_ADMIN_TOKEN`, chưa chạy máy thật). Việc đầu tiên của lượt sau: chạy §5.1 cho #350/#318/#314, rồi §5.2 để có số; sau đó điền lại bảng §1 (máy/phiên bản/giai đoạn).
- **Không** triển khai hướng cũ (thu hẹp `coreLock`, bỏ `fpsSync->wait()` ra ngoài khoá): vừa không giảm thời gian chờ của Main Thread, vừa mở use-after-free `fpsSync` (§6) — hỏng nặng hơn lỗi đang có.
- Nếu §5.2 xác nhận §4.1: phương án B (không gọi `onPause()` của view khi chưa có khung đầu) là thay đổi **hành vi vòng đời** (giữ EGL surface, tắt cổng ghi âm thanh thay vì `stop()`) ⇒ cần PM duyệt riêng và test máy thật.
- **Đính chính đã gửi PM:** bảng ưu tiên 13/11 12:07Z ở #337 ghi nhóm này "nghi phạm `coreLock`" — theo bản 2, dòng đó **sai**; bảng ưu tiên nên đọc lại theo §4.4 (không sửa `step()`/`coreLock`).
- Sáu issue ≤ 3 lượt (#533, #529, #439, #597, #530, #440): **không mở việc riêng**, chỉ gộp vào bảng theo dõi; mẫu quá nhỏ.
- Không đóng issue nào; không sửa `config/`; không đụng `GLRetroView`/`EmulationCall` (đã chốt ở #619), các tệp của #574, hay `step()`/`coreLock`.
