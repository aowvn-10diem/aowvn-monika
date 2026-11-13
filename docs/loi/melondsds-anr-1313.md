# Nhóm `melondsds / anr`: 9 chữ ký còn lại sau #606/#619

Ngày 13/11/2026 — nhánh `chan-doan/melondsds-anr-1313` — nền `main` `f24ece45`.
**Chỉ tài liệu, không sửa mã.** Không gộp, không đụng `config/`.

Tài liệu này **không nhắc lại** ba giả thuyết (a)/(b)/(c) đã viết ở `docs/loi/melondsds-anr-1311.md` (PR #619, đã gộp). Ở đây chỉ thêm ba thứ chưa có: (1) bảng số của cả 9 issue, (2) **[đính chính]** cách chữ ký của bot được tính — hệ quả là nhóm này không thể là một nguyên nhân duy nhất, (3) bảng quyết định đọc `detail`/`stage` để tách nhánh.

## 1. Bảng nhóm (9 issue `crash-moi`, tổng 421 lượt)

Số lấy từ khối `aow-crash-v100` trong thân từng issue. Cột **"số báo cáo"** = số khoá trong `seen`; theo `scripts/crash-issue-bot.py:76` mỗi khoá là `digest(id báo cáo)[:32]` và `id` chính là mốc thời gian của lần chết (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:866`), nên **mỗi khoá ≈ một lần gửi báo cáo**, còn giá trị của khoá là `count` mà máy đó đã cộng dồn trước khi gửi.

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

- Hai chữ ký chiếm **93%** lượt (#350 + #318 = 393/421). Sáu issue còn lại cộng lại 11 lượt — **không đủ mẫu** để kết luận gì; đừng mở việc sửa riêng cho chúng.
- #350 và #318 khác nhau về *hình dạng phân bố*: #350 dồn vào ít báo cáo nhưng lặp rất nhiều trên cùng máy (33 lần) → giống một vòng lặp **tất định** theo máy/cấu hình; #318 trải trên 52 báo cáo → giống một điều kiện chung (mọi máy đều gặp khi làm đúng thao tác). Đây là suy luận từ phân bố, **không phải bằng chứng nguyên nhân** — `[CHƯA KIỂM]`.
- Cột **máy / phiên bản / giai đoạn để trống** vì thân issue chỉ có `signature`, `count`, `seen` — bot cố tình không chép thiết bị, game, URL (`scripts/crash-issue-bot.py:4`). Ba trường này **có** trong báo cáo gốc trên Worker: `device`, `app`, và `session.stage` (riêng `stage` được ghi thêm cho `anr`/`lowmem` ở `cloudflare/crash-worker/worker.js:832-836`). Muốn điền cột thì phải đọc Worker (mục 5).
- Số "lượt" là **cộng dồn lịch sử**, không phải mức tăng hiện tại (xem `docs/loi/lowmem-1311.md` §1) — đừng dùng để nói "đang tăng".

## 2. [Đính chính] chữ ký của bot không phải "3 frame Java đầu" — nó là **3 frame native đầu**

Chuỗi suy luận, đọc theo mã:

1. Bot gộp nhóm bằng `signature = digest([core, kind, frames(report)])` — `scripts/crash-issue-bot.py:71`.
2. `frames()` lấy **3 dòng đầu tiên** khớp hoặc frame Java `at x.y(Z)` hoặc frame native `#NN pc … <mod>.so (symbol)` — `scripts/crash-issue-bot.py:39-53`.
3. Với `kind = anr`, `detail` **không** phải stack Java: nó là trace lấy từ `ApplicationExitInfo.traceInputStream` rồi **lọc theo `INTERESTING`** (`.so|libretro|SIG…|::|libGLES|libEGL|vulkan|…`) — `Diagnostics.kt:302-326`, dùng ở `:854`.
4. Hệ quả: một dòng Java như `at android.opengl.GLSurfaceView$GLThread.onPause(...)` **không khớp** `INTERESTING` nên bị loại khỏi `detail`; chỉ frame native còn lại. Vì vậy chữ ký ANR thực chất là **tổ hợp frame native** của trace.

Ba hệ quả thực dụng:

- **9 issue = 9 tổ hợp frame native khác nhau**, không phải 9 lần cùng một chỗ. Nhóm `melondsds / anr` là **hỗn hợp nhiều điểm chờ**, không phải một lỗi. Bất kỳ tài liệu nào (kể cả `melondsds-anr-1311.md`) nói "nguyên nhân là X" cho cả nhóm đều chưa đủ bằng chứng — `[CHƯA KIỂM]`.
- **Không bao giờ thấy `GLSurfaceView.onPause` trong chữ ký.** Câu "nếu trace rơi vào `GLSurfaceView.onPause` thì…" ở `melondsds-anr-1311.md` §6.1 chỉ đúng nếu đọc `adb bugreport`/logcat ANR, **không** đúng nếu chỉ đọc `detail` của báo cáo. Cách đọc đúng: nhìn **frame native đầu tiên** rồi tra bảng ở mục 4.
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

Vậy đường còn lại **không đi qua mã app** — đúng như kết luận của `docs/loi/ppsspp-anr-1311.md` §2, và vì vậy nhóm này chỉ còn 4 nghi phạm dưới đây.

## 4. Bốn nghi phạm còn lại, kèm `tệp:dòng`

### (a) `onPause` trên Main Thread khi GLThread đang trong khung dài

- `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:366-373` — observer `ON_PAUSE`: `touchInput.pause(...)` (`:370`), rồi **`onPause()` của `GLSurfaceView`** (`:371`, Android chờ GLThread báo đã dừng khung — không có tham số hạn), rồi `LibretroDroid.pause()` (`:372` → `libretrodroid/src/main/cpp/libretrodroid.cpp:506-507` chờ `coreLock`).
- `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:635-641` — `onPause()` của Activity gọi `saveSram()` (`:639`, hạn 300 ms) **trước** `super.onPause()`, nên 300 ms đó tiêu vào cùng ngân sách 5 giây trước khi vào đường chờ GL.
- Frame native kỳ vọng: `libart.so (art::Monitor::Wait)` hoặc `libc.so (__epoll_pwait)` — dấu hiệu **Java wait**, tức đang đứng ở `:371`.

### (b) Đóng game: `onDetachedFromWindow → requestExitAndWait`

- `RetroActivity.kt:646-657` — `onDestroy()` chờ SRAM `runBlocking { withTimeoutOrNull(1500) { sramJob?.join() } }` (`:649`, hạn ở `:691`) **trước** `super.onDestroy()` (`:651`); sau đó view bị gỡ khỏi cây → framework `GLSurfaceView.onDetachedFromWindow` → `requestExitAndWait()` (không hạn) chờ GLThread chạy hết hàng đợi.
- Trong hàng đợi còn `destroy()` — `GLRetroView.kt:153` → `libretrodroid.cpp:453-470` (`retro_unload_game` + `retro_deinit` dưới `coreLock`).
- Frame native kỳ vọng: `libc.so (__futex_wait_ex(...))` hoặc `libart.so (art::Monitor::Wait)`; nếu thấy `pthread_mutex_lock` của chính `libretrodroid.so` thì là chờ `coreLock`, không phải chờ GLThread.

### (c) Mở game lần đầu (nạp lõi trên GLThread) — **không tự gây ANR**

- `RetroActivity.kt:308` đặt `initializeOnGlThread = true` → `GLRetroView.kt:403-431` chạy `createCore()` + `loadGameFromPath()` + `LibretroDroid.onSurfaceCreated()` (`libretrodroid.cpp:204-241`) **trên GLThread**; Main Thread chỉ hiện overlay, không chờ.
- Nên (c) bị loại với tư cách nguyên nhân trực tiếp. Nó chỉ là **điều kiện làm khung dài** trong ~60 giây đầu (mốc mềm `StartupProgress.kt:40`, dùng ở `RetroActivity.kt:287-292`): người chơi bấm Quay lại / chuyển app ngay lúc lõi đang nạp → ANR rơi vào (a) hoặc (b). `[CHƯA KIỂM]` về tỉ lệ thật.

### (d) Khung dài **bên trong** `coreLock` — phần riêng của nhóm melondsds

Đây là phần #619 chưa chạm tới và cũng là phần dễ bị bỏ qua nhất: không phải "chờ vô hạn", mà là "khoá bị giữ quá lâu, nhiều lần".

- `libretrodroid.cpp:519-543` — `step()` giữ `coreLock` **suốt cả khung**, gồm `video->renderFrame()` (`:538`) và **`fpsSync->wait()`** (`:542`). `wait()` là `sleep_until` (`libretrodroid/src/main/cpp/fpssync.cpp:59-61`), chỉ bỏ qua khi `useVSync` — mà `useVSync` bật theo `|content − screen| < 5` Hz (`fpssync.cpp:41`, `fpssync.h:43`). Trên màn 90/120 Hz, mỗi khung khoá bị giữ thêm ~16,7 ms **trong lúc ngủ**, tức `coreLock` gần như không bao giờ rảnh khi đang chơi. Mọi lời chờ ở (a)/(b) vì thế trả giá ít nhất một khung, không phải "thường về ngay".
- `libretrodroid.cpp:524-535` — số lần `retro_run` trong **một** lần giữ khoá là `frames × frameSpeed`, với `frames` do `advanceFrames()` trả (chặn trên 2, `:526-529`) và `frameSpeed` do nút tua nhanh đặt 1…4 (`RetroActivity.kt:224`). Khung chậm nhất bị nhân tối đa 8.
- `libretrodroid.cpp:537-539` + `libretrodroid/src/main/cpp/video.cpp:140-161` — `renderFrame()` gọi `updateProgram()` (`video.cpp:161`), tức **biên dịch GLSL nằm trong khung đang giữ khoá**. Từ #619, yêu cầu đổi shader chỉ được xếp hàng, nên việc biên dịch rơi đúng vào khung kế tiếp — ngay sau khi người chơi đổi kiểu hiển thị/độ phân giải.
- `libretrodroid.cpp:550-559` → `video.cpp:247-250` — lõi đổi geometry giữa chừng (melondsds có hai màn và đổi layout) → `updateRendererSize` cấp phát lại FBO, cũng trong khoá.

## 5. Cách kiểm (đủ để chốt nhánh, không cần đọc mã lại)

1. **Đọc báo cáo gốc.** `CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh "<id>"` (chi tiết một báo cáo) rồi `python scripts/crash-report-detail.py <id>` — script in `Máy`, `Bản app`, `Lõi`, **`Giai đoạn`** (`crash-report-detail.py:126`) và toàn bộ `detail` (`:128`). Đây là hai lệnh duy nhất cần cho việc tách nhóm.
2. **Đọc frame native đầu tiên** trong `detail` (dòng `#00 pc …` của luồng `"main"`), không tìm chuỗi `onPause` — chuỗi đó đã bị lọc mất (mục 2).
3. **Bảng quyết định:**

| Frame native đầu của `main` | Kết luận | Việc tiếp |
|---|---|---|
| `libart.so (art::Monitor::Wait)` / `libc.so (__epoll_pwait)` + `stage` = `playing`/`paused` | (a) — chờ GLThread ở `GLRetroView.kt:371` | hướng sửa ngoài repo (§6 của #619); chỉ còn giảm khung dài |
| `libc.so (__futex_wait_ex)` + có `libretrodroid.so` trong 3 frame đầu | chờ `coreLock` — **#606/#619 còn sót đường** | mở issue mới kèm trace, ghi rõ tên lời gọi |
| `libart.so (art::Monitor::Wait)` + `stage` = lúc thoát game | (b) — `requestExitAndWait` / `retro_deinit` | đo `retro_deinit` của melondsds; xem lại 1,5 s ở `RetroActivity.kt:649` |
| `libGLESv2.so` / `libEGL.so` (`eglSwapBuffers`, `glLinkProgram`) | khung đang dựng shader — (d) | tái hiện: đổi kiểu hiển thị rồi thoát ngay |
| `libc.so (__ioctl)` / `libc.so (read)` trên luồng khác | nghi luồng I/O (SAF, firmware, savestate) | `[CHƯA KIỂM]` |
4. **Đếm theo `stage`** (đã có sẵn cho `anr`): nếu phần lớn báo cáo là `first-frame`/`playing` thì là (a)/(d); nếu là stage mới `paused`/`playing` xen kẽ (sau khi #624 gộp) thì là ca **ở nền**, không phải lúc chơi.
5. **Thống kê tổng hợp:** `python scripts/crash-insight.py --out-dir <thư mục>` — mặc định **loại** báo cáo mang chứng thư khác bản phát hành (`partition_by_cert`); muốn gộp cả bản mod/dựng lại thì thêm `--all-certs`, và khi đó phải nói rõ trong kết luận.
6. **Tái hiện có kiểm soát** (máy yếu, NDS): mở game → tua nhanh 4× → đổi kiểu hiển thị → thoát ngay; lặp 5 lần, ghi số lần `Davey!` > 2 000 ms trong logcat và số lần `stage` cuối là `first-frame`. Không cần `bugreport` nếu `detail` đã đủ frame native.

## 6. Còn lại / rủi ro

- **Chưa có trace nào của 9 issue này trong phiên này** (không có `CRASH_ADMIN_TOKEN`) → mọi phân nhánh ở mục 4 đều `[CHƯA KIỂM]`. Việc đầu tiên của lượt sau là chạy mục 5.1 cho #350, #318, #314 rồi điền lại bảng ở mục 1 (cột máy/phiên bản/giai đoạn).
- Nếu trace rơi vào (a)/(b): **không sửa được trong repo** — chỉ giảm được khung dài (mục 4d) hoặc đổi hành vi (không gọi `onPause` của view khi chưa có khung đầu). Đổi hành vi vòng đời là thay đổi có rủi ro, cần PM duyệt.
- Nếu trace rơi vào (d): sửa `step()` (bỏ khoá quanh `renderFrame`/`fpsSync->wait`, hoặc chia khoá theo khung) là **động vào lõi `libretrodroid`** — có nguy cơ race với `destroy()` (`libretrodroid.cpp:453-470`) vốn đang dựa vào việc khoá suốt khung. Phải có test và phải do PM quyết.
- Sáu issue ≤ 3 lượt (#533, #529, #439, #597, #530, #440): **không mở việc riêng**, chỉ gộp vào bảng theo dõi; mẫu quá nhỏ.
- Không đóng issue nào; không sửa `config/`; không đụng `GLRetroView`/`EmulationCall` (đã chốt ở #619) hay các tệp của #574.
