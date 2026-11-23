# Nhóm `melondsds / anr` (9 issue, 421 lượt): chặng nào — và vì sao báo cáo hiện tại không trả lời được

Ngày 18/11/2026 · nhánh `loi/melondsds-anr` · mã đọc trên `origin/main` **`4c4757b1`**.

**Kết luận trước:** **chưa xác định được nguyên nhân gốc** ⇒ **không vá** hành vi vòng đời. Lượt này vá
đúng **chỗ mù dữ liệu**: trace ANR trong báo cáo đang bị lọc mất **dòng tiêu đề luồng** và **khung Java**,
nên không báo cáo nào cho biết Main Thread đang chờ ở chặng nào. Sau khi gộp, báo cáo ANR kế tiếp **tự**
mang khối `main` (tên luồng + khung `at …` + `| held mutexes=`), và không đổi chữ ký bot của 9 issue đang mở.

Bản đồ nhóm, phân bố và ba nghi phạm đã có ở `docs/loi/melondsds-anr-1313.md` (PR #633, đã gộp); tài liệu
này **không nhắc lại** mà chỉ ghi (1) phần còn thiếu, (2) bằng chứng mới về chỗ mù, (3) việc đã sửa,
(4) cách đọc báo cáo kế tiếp.

## 1. Trạng thái từng câu hỏi

| Câu hỏi | Trả lời | Bằng chứng |
|---|---|---|
| ANR xảy ra ở chặng nào (pause / destroy / main thread)? | **Chưa biết** — còn 3 ứng viên: chờ GLThread lúc pause, chờ ghi SRAM 1 500 ms lúc destroy, `requestExitAndWait` ở nhánh huỷ không `isFinishing` | §3, §4 |
| Có phải `coreLock` / `step()`? | **Không** (đã hạ ở 1313 §4.4): Main Thread chỉ vào khoá ở `GLRetroView.kt:361` và `:372`, cả hai **sau khi GLThread đã dừng** ⇒ chờ ≈ 0 | 1313 §4.4 |
| 9 issue có cùng một nguyên nhân? | **Không chắc** — chữ ký = 3 **khung native** đầu, 9 tổ hợp khác nhau; thêm nữa trace **không ghi khung đó thuộc luồng nào** | §2, §4 |
| Đủ dữ liệu để sửa an toàn? | **Chưa**: không có trace gốc (không dùng `CRASH_ADMIN_TOKEN`), không có số đo `t1→t2` / `t7→t8` | §3 |
| Đã làm gì | Bổ sung **khối `main`** vào `detail` của báo cáo ANR, giữ nguyên chữ ký bot; 8 ca test | §5 |
| Cùng lõi, khác loại: #567 `core-error`, #397/#482 `unknown` | **Khác gốc** — không có chặng pause/destroy nào; không gộp vào PR | §7 |
| Issue đóng được | **Không đóng issue nào** — 421 lượt vẫn nguyên | — |

Bảng số (nguồn: `melondsds-anr-1313.md` §1, đọc từ khối `aow-crash-v100` của từng issue):

| Issue | Lượt | Số báo cáo | Ghi chú |
|---|---:|---:|---|
| #350 | 216 | 14 | dồn một máy (33 lần/báo cáo) → nghi vòng lặp tất định |
| #318 | 177 | 52 | trải rộng nhiều máy → nghi điều kiện chung |
| #314 | 17 | 11 | thưa |
| #533 / #529 / #439 | 3 / 3 / 2 | 2 / 2 / 1 | mẫu nhỏ |
| #597 / #530 / #440 | 1 / 1 / 1 | 1 / 1 / 1 | mẫu nhỏ |
| **Tổng** | **421** | **85** | 93 % lượt nằm ở #350 + #318 |

## 2. Chữ ký bot chỉ là 3 khung **native** — không phải 3 khung Java

- `signature = digest([core, kind, frames(report)])` — `scripts/crash-issue-bot.py:71`; `frames()` lấy
  **3 dòng đầu** khớp frame Java `at x.y(Z)` **hoặc** native `#NN pc … <mod>.so` — `:39-53`.
- `detail` của `kind=anr` do `Diagnostics.traceStrings` dựng: chỉ giữ đoạn văn bản khớp `INTERESTING`
  (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:311-336`).
- Hệ quả: frame Java `at android.opengl.GLSurfaceView$GLThread…` **bị loại**, nên chữ ký thực chất là
  tổ hợp **native**. Điều này đã ghi ở 1313 §2; ở đây bổ sung hệ quả nặng hơn ở §4.

## 3. Chặng còn thiếu — đo cái gì, ở đâu

Ba chặng dưới đây là toàn bộ đường còn lại sau #606/#619 (1313 §3). Cột "đo được chưa" tính cả **#656**
(`de-xuat/do-anr-chang`, đang mở — `PauseProbe.kt`/`PauseOrigin.kt`/`Diag.sink`):

| Chặng | Mốc | Đo được chưa | Thiếu gì |
|---|---|---|---|
| Chờ GLThread lúc pause (nghi phạm #1) | `t1→t2` (`GLRetroView.kt:371`) | **#656 sẽ đo** | — (chờ gộp) |
| `LibretroDroid.pause()` (gồm `audio->stop()`) | `t2→t3` (`GLRetroView.kt:372`) | **#656 sẽ đo** | — |
| Chờ ghi SRAM lúc destroy (nghi phạm #3) | `t7→t8` (`RetroActivity.kt:693`) | **#656 sẽ đo** | — |
| Đơn vị việc **dài nhất** của GLThread (`initializeCore()` + khung đầu dựng shader) | — | **chưa ai đo** | crumb bọc `GLRetroView.initializeCore()` (`GLRetroView.kt:403-431`) và `LibretroDroid.step()` (`libretrodroid.cpp:539`) |
| `requestExitAndWait` (nhánh không `isFinishing`) | `t10→t11` | **chưa đo** | phải tự gỡ view + bọc crumb; 1313 §5.2 đã nêu |
| **Luồng nào đang chờ** + **khung Java nào** | — | **nay mới có** (PR này) | — |

Chưa có số nào cho bất kỳ dòng "chưa" nào: phiên này **không** đọc Worker (không dùng `CRASH_ADMIN_TOKEN`)
và **không** chạy máy thật. Mọi giá trị thời gian vẫn `[CHƯA KIỂM]`.

## 4. Bằng chứng mới: trace ANR hiện **không** nói được Main Thread đang ở đâu

Đọc `Diagnostics.traceStrings` (`Diagnostics.kt:311-336`):

1. Trace `ApplicationExitInfo` được cắt thành từng "run" văn bản in được (`:327-330`);
2. mỗi run chỉ được giữ nếu khớp `INTERESTING` (`:323`) và bị cắt còn 200 ký tự;
3. `INTERESTING` (`:336`) = `\.so|libretro|SIG[A-Z]+|signal|abort|Abort|assert|backtrace|::|Fatal|fault|SEGV|libc|libGLES|libEGL|vulkan`.

Ba hệ quả kiểm được ngay trên mã, không cần báo cáo gốc:

- **Mất tiêu đề luồng.** Dòng `"main" prio=5 tid=1 Native` không khớp `INTERESTING` ⇒ bị bỏ. Vì vậy
  **không thể biết** khung native trong `detail` thuộc `main` hay `GLThread 321` / `RenderThread` /
  luồng audio. Bảng quyết định "frame native đầu **của luồng main**" ở 1313 §5.1 vì thế **chưa kiểm
  chứng được** — dòng đầu tiên trong `detail` không chắc là của `main`.
- **Mất khung Java.** `at android.opengl.GLSurfaceView$GLThread.guardedRun(GLSurfaceView.java:1723)`
  không khớp ⇒ bị bỏ. Đây đúng là khung trả lời "đang chờ `GLSurfaceView.onPause()`" hay "đang trong
  `RetroActivity.onDestroy()`".
- **Mất `| held mutexes=`.** Nếu ANR thật sự do chờ mutex (giả thuyết đã hạ nhưng chưa loại hẳn cho
  đường JNI chưa rà — 1313 §4.4), dấu hiệu này cũng bị lọc mất.

Nói cách khác: 9 chữ ký hiện tại là **9 tổ hợp khung native ở 9 chỗ**, nhưng báo cáo **không** kèm luồng
và **không** kèm khung Java — nên không thể quy chúng về "pause" hay "destroy", cũng không loại được khả
năng một phần ANR đến từ luồng khác. Đây là lý do **không vá** ở lượt này.

## 5. Đã sửa (chỉ chẩn đoán, không đổi hành vi)

| Tệp | Thay đổi |
|---|---|
| `app/src/main/java/vn/aow/monika/diag/AnrTrace.kt` (mới) | `appendMainThread`: nối khối `main` vào **cuối** danh sách dòng đã có, **chỉ khi** đã có ≥ 3 dòng (bot lấy tối đa 3 khung đầu ⇒ chữ ký không đổi). `mainSection`: giữ tiêu đề luồng, `native:`, `at `, `kernel:`, `| state=`, `| held`; dừng ở luồng kế tiếp; trần 16 dòng × 200 ký tự |
| `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt` | `traceStrings`: gom các "run" vào danh sách rồi nối khối `main` khi `reason == REASON_ANR`. 80 dòng cũ giữ nguyên thứ tự và nội dung |
| `app/src/test/java/vn/aow/monika/diag/AnrTraceTest.kt` (mới) | 8 ca: giữ 3 khung đầu, dừng ở luồng kế tiếp, không nối khi < 3 khung, không có `main`, chống trùng dòng, trần số dòng, giữ `| held mutexes=`, cắt dòng dài |

Không đụng: `GLRetroView.kt`, `step()`/`coreLock` (chốt ở #619), `config/`, workflow, tệp của #574/#624.
Không đổi thứ tự `onPause`/`onDestroy`, không đổi hạn 1 500 ms.

Vì sao an toàn với chữ ký: `frames()` đọc `detail` **theo thứ tự** (`crash-issue-bot.py:40-52`) và dừng
khi đủ 3 khung; phần nối thêm nằm **sau** nên chỉ xuất hiện khi báo cáo cũ có **< 3** khung — trường hợp
đó `appendMainThread` **không làm gì**.

## 6. Cách đọc báo cáo ANR kế tiếp (sau khi gộp PR này)

Mở `detail` của báo cáo `melondsds / anr`, tìm dòng bắt đầu bằng `"main"` (khối cuối), rồi đọc khung
`at …` / `native:` bên dưới:

| Dấu hiệu trong khối `main` | Kết luận | Việc tiếp |
|---|---|---|
| `at android.opengl.GLSurfaceView$GLThread…` (hoặc `GLSurfaceView.onPause`) | Main Thread đứng ở `GLRetroView.kt:371` — chờ GLThread | so với crumb `t1→t2` của #656 |
| `native: … __epoll_pwait` + **không** có khung Java của app | Main Thread **rảnh** lúc chụp trace | ANR do input/binder/khác, không phải chặng pause ⇒ mở nhánh mới |
| `at vn.aow.monika.runner.RetroActivity.onDestroy` + `kotlinx.coroutines…`/`runBlocking` | chờ `sramJob.join()` (`RetroActivity.kt:693`) | so với `t7→t8` |
| `| held mutexes=` có tên khoá + khung `libretrodroid.so` | chờ khoá native ở đường JNI **chưa rà** | mở issue mới kèm khối `main` |
| không có dòng `"main"` | máy không thu được trace | ghi `[CHƯA KIỂM]`, không suy |

## 7. Cùng lõi melonDS DS nhưng **khác gốc**: `core-error` #567, `unknown` #397/#482

| Issue | Loại | Lượt | Sinh ở đâu | Vì sao **không** cùng gốc ANR |
|---|---|---:|---|---|
| #567 | `core-error` | 21 | `RetroActivity.kt:392-400` nhận `GLRetroErrors` → `onCoreFailure` (`:190-196`) → `Diagnostics.recordCoreFailure` (`Diagnostics.kt:621-633`) | Lõi **báo lỗi mã 0–4** rồi Activity hiện màn "Đổi sang lõi…"; tiến trình **còn sống**, không có luồng nào bị treo 5 s. `detail` chỉ là `error code=N core=melondsds` (`:399`), **không có khung** ⇒ `frames()` rỗng, mọi mã lỗi của lõi này gộp **một chữ ký**: #567 là một **thùng**, không phải một lỗi |
| #397 | `unknown` | 13 | `Diagnostics.collect` khi `exit == null` (`Diagnostics.kt:232-241`) | ANR chỉ được ghi khi `REASON_ANR` (`:235`). `unknown` = Android **không** có bản ghi chết (API < 30, hoặc log vòng đã bị xoá) — đã kết luận ở `docs/loi/loi-khac-1313.md` §5 |
| #482 | `unknown` | 1 | như trên | 1 lượt, không kết luận |

⇒ **Không gộp ba issue này vào PR**: không có chặng pause/destroy nào để sửa, cũng không có dữ liệu mới.
**Không đóng** #567/#397/#482. Việc rẻ nhất cho #567 (chưa làm lượt này): đọc `session.stage`
(`core-error-<mã>`, đặt ở `RetroActivity.kt:394`) của 6 báo cáo trên Worker để biết mã; nếu là mã 1 thì đi
theo `loi-khac-1313.md` §3.4 (tệp game hỏng/thiếu tệp đi kèm), không phải ANR.

## 8. Việc tiếp theo

1. Gộp **#656** (đo `t0…t9`) và PR này (khối `main`). Hai thứ bù nhau: #656 cho **thời gian từng chặng**,
   PR này cho **luồng + điểm chờ**.
2. Chờ **một** báo cáo `melondsds / anr` mới rồi đọc theo §6; chỉ cần 1 báo cáo có khối `main` là tách
   được pause / destroy / main-rảnh.
3. Chỉ sau đó mới quyết định sửa: nếu `t1→t2` lớn + `at …GLThread` ⇒ sửa ở biên pause (phương án B của
   1313 §7, cần PM duyệt vì đổi hành vi vòng đời); nếu `t7→t8` ≈ 1 500 ms ⇒ xem lại ngân sách ghi SRAM
   (đổi hành vi mất lượt lưu — cần PM duyệt).
4. **Không** đóng issue nào; **không** thu hẹp `coreLock`; **không** bỏ `fpsSync->wait()` ra ngoài khoá
   (use-after-free — 1313 §6).
5. Việc còn thiếu chưa làm: crumb bọc `initializeCore()`/`step()` (đơn vị việc GLThread) và `t10→t11`
   cho nhánh huỷ không `finishing`.
