# Nhóm `ppsspp / anr`: hiện trạng trống và nghi phạm ANR còn lại sau #606/#619

Ngày 13/11/2026 · nhánh `deepseek3/ppsspp-anr-1311` · nền `origin/main` `f24ece45` (mốc `c3a8a9c4` là tổ tiên của nhánh này).
Chỉ tài liệu — **không sửa mã** trong lượt này (tránh đụng #619 và các PR đang mở).

Mọi kết luận về nguyên nhân dưới đây đều là suy luận từ mã, **chưa có khung ANR** để chứng minh → ghi **[CHƯA KIỂM]**.

## 1. Hiện trạng: nhóm `ppsspp / anr` **trống**

Lọc `crash-moi` đang mở (`gh issue list --label crash-moi --state open --limit 100`, 75 issue):

| Nhóm | Số issue | Lượt | Ghi chú |
|---|---:|---:|---|
| Lõi `ppsspp` (mọi loại) | 5 | 214 | #316 `native` 114, #372 `native` 90, #416 `unknown` 8, #478 `native` 1, #520 `native` 1 |
| Loại `anr` (mọi lõi) | 27 | 476 | chỉ `melondsds` (10), `unknown` (7), `desmume` (4), `citra` (3), `gambatte`/`snes9x`/`mgba` (1 mỗi lõi) |
| **Giao: `ppsspp` + `anr`** | **0** | **0** | không có issue nào mang cả hai |

Đối chiếu `docs/loi/TON-0811.md` (nguồn PR #542, chưa gộp): bảng tồn cũng không có dòng `ppsspp` + `anr` — chỉ #374 `killed` (48), #386 `lowmem` (10) và các issue `native`. **[CHƯA KIỂM]** nghĩa là PSP có ANR thật; nhiều khả năng cùng một lần treo được bot ghi thành `killed`/`lowmem`/`native`, hoặc người chơi không báo. Không được suy từ "0 issue" ra "không có ANR" — chỉ suy ra **không có chữ ký `anr` nào của ppsspp để phân tích**.

Hệ quả cho lượt này: phân tích dưới đây dựa trên **đường mã**, không dựa trên chữ ký; đóng issue là chuyện của nhóm `ppsspp/native` (`docs/loi/ppsspp-native-1311.md`), tài liệu này chỉ chốt phần ANR.

## 2. #606 và #619 **đã** xử lý gì (để không trùng)

| Đường gọi | Trước | Sau | Vị trí |
|---|---|---|---|
| `viewport`, `viewportAlignment`, `setControllerType` | chờ GLThread | chỉ `queueEvent` | `GLRetroView.kt:69-80`, `:269-272` |
| `frameSpeed`, `shader`, `updateVariables` | chờ `coreLock` không hạn | `EmulationCall.post` (không chờ) | `GLRetroView.kt:59-67`, `:280-285` |
| `getVariables` | chờ không hạn | `runBounded` 2 000 ms, hết hạn trả rỗng | `GLRetroView.kt:274-279`, `EmulationThreadTask.kt:74` |
| mọi `runOnEmulationThread` | chờ không hạn | hạn 2 000 ms + giá trị dự phòng | `GLRetroView.kt:464-488` |
| `serializeSRAMOrNull` | — | hạn 300 ms | `GLRetroView.kt:238-241`, `RetroActivity.kt:669` |
| C++ `pause/resume/destroy/onSurfaceChanged/onSurfaceCreated/setShaderConfig/...` | không khoá | `coreLock` + `variablesLock`, huỷ HW context trước khi dựng Video mới | `libretrodroid.cpp:132-241`, `:453-517`, `:605-612` |

Kết luận: **mọi lời gọi từ UI Thread đi qua mã của app đều đã có hạn**. Vì vậy ANR còn lại (nếu có) phải nằm ở đường **không đi qua mã app** — tức đường của `GLSurfaceView`/vòng đời Activity. Đây là điểm khác biệt cốt lõi so với nhóm `melondsds/anr`.

## 3. Ba nghi phạm, kèm `tệp:dòng`

### (a) Tạm dừng/nền hoá app khi GLThread đang trong khung dài (dựng shader/JIT lần đầu) — nghi phạm chính

- `RenderLifecycleObserver.pause()` gọi **`onPause()` của `GLSurfaceView`** ngay trên Main Thread: `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:366-373`.
- `GLSurfaceView.onPause()` (AOSP) chờ GLThread báo đã dừng khung đang chạy; **không có tham số hạn** và không có cách bọc hạn từ phía app. Kế tiếp `LibretroDroid.pause()` (`:372` → `libretrodroid.cpp:506-507`) lại chờ `coreLock` — cùng khoá mà `step()` giữ **suốt** khung: `libretrodroid.cpp:519-566`, gồm cả `video->renderFrame()` (`:538`) và `fpsSync->wait()` (`:542`).
- Vì sao đúng với ppsspp: khung đầu của ppsspp dựng shader cho backend GL của nó; `libretrodroid.cpp:537-539` chỉ vẽ khung sau khi `retro_run` trả về, nên toàn bộ thời gian dựng shader nằm trong một khung. Người chơi hay chuyển app/khoá máy đúng lúc màn chờ còn hiện (mốc chờ mềm của app là **60 giây**: `StartupProgress.kt:40`, dùng ở `RetroActivity.kt:287-292`) → Main Thread đứng ở `onPause()` tới khi khung xong. **[CHƯA KIỂM]**
- Khung ANR cần thấy: `main` ở `GLSurfaceView$GLThread.onPause` → `Object.wait` (không phải `pthread_mutex_lock` của `coreLock`). Đây là dấu hiệu phân biệt với nhóm `melondsds`.

### (b) Đóng game: Main Thread chờ GLThread thoát, trong hàng đợi còn `destroy()`

- `RetroActivity.onDestroy()`: `runBlocking { withTimeoutOrNull(1 500) { sramJob?.join() } }` rồi `killProcess` — `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:646-657`.
- `GLRetroView.onDestroy()` đẩy `LibretroDroid.destroy()` vào hàng đợi GL: `GLRetroView.kt:146-157`.
- Khi view bị gỡ khỏi cửa sổ, `GLSurfaceView.onDetachedFromWindow()` → `requestExitAndWait()` chờ GLThread thoát, **không hạn**; GLThread phải chạy nốt việc trong hàng đợi, tức `destroy()` = `retro_unload_game` + `retro_deinit` dưới `coreLock` (`libretrodroid.cpp:453-482`). ppsspp giải phóng shader/texture/VM JIT ở bước này nên có thể lâu. Cộng thêm 1,5 giây chờ SRAM ở Activity là **cùng một ngưỡng ANR 5 giây**. **[CHƯA KIỂM]**
- Khung ANR cần thấy: `main` ở `GLSurfaceView$GLThread.requestExitAndWait` → `Object.wait`, hoặc `RetroActivity.onDestroy` → `runBlocking`.

### (c) Mở game lần đầu: **không** tự gây ANR, nhưng là điều kiện làm (a)/(b) nặng thêm

- `initializeOnGlThread = true` (`RetroActivity.kt:308`) → `createCore()` + `loadGameFromPathWithProgress` chạy trên GLThread: `GLRetroView.kt:403-431`. Main Thread **không** chờ (màn chờ là overlay), nên riêng việc mở game không đủ 5 giây treo UI. **[CHƯA KIỂM]**
- Rủi ro nằm ở tổ hợp: GLThread bận dựng shader trong lúc người chơi bấm Quay lại / chuyển app / tắt màn hình → rơi vào (a) hoặc (b).
- Ghi chú cấu hình: `cores.ppsspp.options.ppsspp_vertex_cache = "disabled"` (`config/monika-config.json:964`) là biện pháp cho nhóm native; tắt vertex cache làm mỗi khung nhiều lệnh vẽ/shader hơn, nên **có thể** làm khung đầu dài thêm và tăng xác suất (a). Chưa có số đo để nói mức ảnh hưởng. **[CHƯA KIỂM]** — không đề nghị đổi config trong lượt này (đổi `cores.*` phải tăng `configVersion` + đồng bộ Cloudflare, và đang chờ kết luận nhóm native).

### Xoay màn hình: **không** phải nghi phạm sau #619

- `RetroActivity` khai báo `configChanges="orientation|screenSize|screenLayout|..."` (`app/src/github/AndroidManifest.xml:81-83`) → xoay máy **không** dựng lại Activity, không `retro_init`/`retro_load_game` lại, không tạo EGL context mới.
- Đường xoay chỉ còn `onConfigurationChanged` → `applyScreenLayout` (`updateVariables` = `post`) và `applyLayout` (chỉ đổi `layoutParams`): `RetroActivity.kt:447-451`, `:458-467`, `:411-419`. Không còn lời gọi JNI chờ trên UI Thread.
- `onSurfaceCreated` dựng lại Video dưới `coreLock` (`libretrodroid.cpp:204-241`) chỉ xảy ra khi **mất EGL context** (khoá/mở máy, đổi độ phân giải), và chạy trên GLThread — không chặn Main Thread. Nếu sau này bỏ `screenSize` khỏi `configChanges`, kết luận này phải xét lại.

## 4. Vì sao chưa sửa, và điều kiện đóng

Không sửa mã vì: (i) nhóm không có issue/chữ ký để đối chiếu; (ii) cả ba nghi phạm đều nằm trong `GLSurfaceView`/vòng đời Activity — sửa phải đổi cách pause (ví dụ không gọi `onPause()` của view trên Main Thread, hoặc chặn người chơi thoát khi chưa có khung đầu), là thay đổi hành vi có rủi ro, cần PM chốt; (iii) #619 vừa gộp vào đúng các tệp này.

Điều kiện để mở việc sửa:

1. Có ít nhất một `adb bugreport`/trace `ANR in vn.aow.monika` của người chơi PSP, xác định `main` đứng ở đâu: `onPause` (a), `requestExitAndWait` (b), hay `pthread_mutex_lock` (nghĩa là #606/#619 còn sót đường gọi).
2. Nếu là (a)/(b): đo thời lượng khung đầu của ppsspp trên máy yếu (logcat `Davey!` hoặc thêm vệt `retro_run > 2 000 ms`), rồi mới chọn hướng.
3. Nếu là `pthread_mutex_lock`: đó là lỗi mới trong repo — mở issue riêng kèm khung.

## 5. Cách lấy bằng chứng (việc tiếp theo)

1. `adb bugreport` ngay sau khi người chơi báo treo; tìm `ANR in vn.aow.monika` trong `bugreport-*.txt` rồi đọc mục `"main" prio=5 ... waiting` — ghi tên khung trên cùng.
2. Đối chiếu vệt có sẵn của app: `Diagnostics.crumb` ghi `sram` khi chụp SRAM quá 300 ms (`RetroActivity.kt:669-673`), `Diagnostics.stage("first-frame")` (`:360`), `libretro-startup` soft-timeout (`:290`). Có vệt `sram` + ANR cùng lượt là dấu hiệu GLThread đang kẹt khung dài.
3. Tái hiện có kiểm soát trên máy yếu: mở game PSP lần đầu (xoá shader cache của ppsspp) → chuyển app sang màn hình chính trong 3 giây đầu → quay lại; lặp 5 lần, ghi số lần có `Davey!` > 2 000 ms.
4. Trước khi kết luận, đọc lại `docs/loi/ppsspp-native-1311.md` để không lặp phần native (BuildId, `vertex_cache`, `environment.cpp`).
