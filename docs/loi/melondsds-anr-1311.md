# Nhóm `melondsds / anr` sau #606: vì sao ANR còn, và đã sửa gì

Ngày 13/11/2026 · nhánh `loi/melondsds-anr` · nền `main` `c3a8a9c4`

## 1. Hiện trạng nhóm

9 issue mở, 421 lượt (`gh issue list --label crash-moi --state open`):

| Issue | Lượt | Mới | Ghi chú |
|---|---:|---:|---|
| #350 | 216 | +33 | chữ ký `d98da506cbec`, cập nhật 12/11 |
| #318 | 177 | +9 | chữ ký `9a90aaa75e95` |
| #314 | 17 | +5 | |
| #533, #529 | 3 mỗi issue | +3 | |
| #439 | 2 | +2 | |
| #597, #530, #440 | 1 mỗi issue | +1 | |

Số lượt vẫn tăng sau khi #606 gộp (`25ca0688`, `ac2944c0`): cơ chế `EmulationThreadTask` chỉ bọc **một phần** đường gọi, phần còn lại vẫn chờ khoá native trên Main Thread.

## 2. #606 đã làm gì

- `EmulationThreadTask.await(timeout)` (`libretrodroid/src/main/java/com/swordfish/libretrodroid/EmulationThreadTask.kt:46-63`) + hạn `CALL_TIMEOUT_MS = 2_000L` (`:74`).
- `GLRetroView`: `viewport`, `viewportAlignment`, `setControllerType` chuyển sang `queueEvent` (`:70-79`, `:271`); mọi lời gọi qua `runOnEmulationThread` có hạn 2 giây.
- C++: `coreLock` giữ trong `pause/resume/destroy/onSurfaceChanged/onSurfaceCreated/setShaderConfig/...` và **toàn bộ `step()`**, gồm cả `fpsSync->wait()` (`libretrodroid/src/main/cpp/libretrodroid.cpp:519-566`).

Hệ quả: từ #606, mỗi lời gọi JNI *có* khoá đó, nếu phát từ UI Thread và GLThread đang trong `retro_run`, sẽ chặn Main Thread cho tới khi khung hình xong.

## 3. Ba giả thuyết

### (a) Hạn 2000 ms vẫn ngắn hơn lần biên dịch shader/nạp state đầu tiên ⇒ UI chờ lặp
- Vị trí: `EmulationThreadTask.kt:74`, dùng ở `GLRetroView.kt:463` (bản trước nhánh này).
- Đánh giá: **không phải nguyên nhân chính, và không nên tăng hạn**. Ngưỡng ANR là 5 giây; hạn 2 giây một lần vẫn dưới ngưỡng. Chờ lặp chỉ xảy ra nếu nhiều lời gọi nối tiếp trên cùng luồng — kiểm mã: lưu state (`RetroActivity.kt:496,543`) và nạp state (`:558`) đã chạy trong `Dispatchers.IO`; chụp SRAM có hạn riêng 300 ms (`:669`). Tăng hạn sẽ làm ANR dễ xảy ra hơn, không khó hơn.
- Cách kiểm nếu vẫn nghi: vệt ANR có khung `GLRetroView.runOnEmulationThread` → `EmulationThreadTask.await` là (a).

### (b) Còn đường gọi **không** qua hạn — Main Thread chờ `coreLock` của lõi
- Vị trí (bản `main` trước nhánh này):
  - `GLRetroView.kt:59-61` — setter `frameSpeed` (nút tua nhanh, `RetroActivity.kt:224`) gọi thẳng `LibretroDroid.setFrameSpeed`.
  - `GLRetroView.kt:64-67` — setter `shader` (đổi kiểu hiển thị, `RetroActivity.kt:436`) gọi thẳng `LibretroDroid.setShaderConfig`.
  - `GLRetroView.kt:280-284` — `updateVariables(...)` gọi thẳng `LibretroDroid.updateVariable` cho **từng** biến (`RetroActivity.kt:242,440,465` — có chỗ áp cả loạt biến).
  - `GLRetroView.kt:274-278` — `getVariables()` gọi thẳng `LibretroDroid.getVariables` (`RetroActivity.kt:472`, mở bảng tùy chọn lõi).
- Phía C++: cả bốn JNI đều `std::lock_guard<std::mutex> lock(coreLock)` (`libretrodroid.cpp:132-141`, `:594-612`), cùng khoá mà `step()` giữ suốt khung hình (`:519-566`). melondsds bật JIT + dựng hình đa luồng ⇒ một khung có thể dài; Main Thread đứng ở `pthread_mutex_lock` không có hạn nào.
- Đánh giá: **khớp nhất với báo cáo ANR**: không có hạn, không có log, và xảy ra đúng lúc người chơi bấm tua nhanh/đổi tùy chọn trong game.
- Đã sửa trong nhánh này (mục 4).

### (c) Khung ANR đến từ luồng khác (Audio/Video/SAF)
- Đã rà: audio dùng Oboe ở luồng riêng, không chặn Main; lưu/nạp state và ghi SRAM đã ở `Dispatchers.IO`; `PixelCopy` gọi lại trên main nhưng bất đồng bộ; `Diagnostics` ghi tệp ở nền.
- **Đường còn lại không bọc được**: `GLSurfaceView.onPause()` trong `RenderLifecycleObserver.pause()` (`GLRetroView.kt:371`) — Android chờ GLThread kết thúc khung đang chạy, không có tham số hạn. Nếu khung đó là lần biên dịch JIT đầu tiên của melondsds thì Main Thread chờ tới khi xong.
- Cách kiểm: vệt ANR có khung `GLSurfaceView$GLThread.onPause` → `Object.wait` là (c). [CHƯA KIỂM] — phiên này không có `CRASH_ADMIN_TOKEN` nên chưa đọc được 3 khung đầu của #350/#318.

## 4. Đã sửa trong nhánh này

Thêm `EmulationCall` (`libretrodroid/src/main/java/com/swordfish/libretrodroid/EmulationCall.kt`) với hai kiểu gọi:
- `post(...)` — việc **không cần kết quả**: chỉ xếp hàng, Main Thread trả về ngay.
- `runBounded(...)` — việc **cần kết quả**: chờ có hạn rồi trả dự phòng.

Áp vào `GLRetroView.kt`:
- `frameSpeed` (`:59-61`), `shader` (`:64-67`), `updateVariables` (`:280-284`) → `post`. Đây là việc không cần kết quả: lõi đọc biến/cấu hình ở khung kế tiếp, thứ tự hàng đợi giữ nguyên như trước.
- `getVariables` (`:274-278`) → `runBounded` với hạn 2 giây, hết hạn trả mảng rỗng (bảng tùy chọn trống còn hơn treo UI tới ANR).
- `runOnEmulationThread` / `runOnEmulationThreadOrNull` (`:464-488`) dùng chung `EmulationCall` thay vì tự dựng task, giữ nguyên hạn và log cảnh báo.

Không đổi: `CALL_TIMEOUT_MS` vẫn 2 000 ms; các đường đã đúng từ #606 (viewport, controller, state, SRAM) giữ nguyên; không đụng `AzaharActivity`, PR #611/#617.

## 5. Test

`libretrodroid/src/test/java/com/swordfish/libretrodroid/EmulationThreadTaskTest.kt` — **10/10 PASS** (`:libretrodroid:testDebugUnitTest`):
- `postOnlyQueuesAndNeverWaitsForGlThread`: `post` không chạy việc trên thread gọi, trả về < 100 ms dù hàng đợi chưa xử lý.
- `runBoundedReturnsResultWhenGlThreadAnswers`: có kết quả thì trả đúng kết quả.
- `runBoundedFallsBackWhenGlThreadNeverRuns`: GLThread kẹt → trả dự phòng trong hạn, việc vẫn nằm trong hàng đợi và chạy khi GLThread xử lý muộn.

## 6. Còn lại / cần kiểm trên máy thật

1. #350/#318 cần một lượt có `CRASH_ADMIN_TOKEN`: đọc 3 khung đầu của vài báo cáo, đối chiếu bảng (a)/(b)/(c) ở trên để biết ANR còn lại thuộc nhánh nào. [CHƯA KIỂM]
2. Nếu traces rơi vào (c) — `GLSurfaceView.onPause` — thì hướng xử lý là **lưu state trước khi pause** (đã có, chạy trên IO) và chấp nhận chờ GLThread, hoặc hạ khối lượng khung hình của melondsds (tắt JIT/đa luồng ở mức máy yếu). Không sửa được trong repo này.
3. Máy thật nên đo: melondsds + tua nhanh 4× + mở bảng tùy chọn lõi vài lần, xem có còn ANR; lấy `adb bugreport` tìm `ANR in vn.aow.monika` để so khung.
