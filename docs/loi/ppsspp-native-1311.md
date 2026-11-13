# Nhóm `ppsspp / native` — phân tích 4 nghi phạm (13/11/2026)

Phạm vi: nhóm **B** (native crash trong `.so` của lõi) theo `docs/loi/TON-0811.md`, phần lõi `ppsspp`.
Người lập: phiên **Lỗi 2**. Mọi kết luận chưa có khung đã giải mã đều ghi **[CHƯA KIỂM]**.

## 1. Số liệu và giới hạn

| Issue | Chữ ký (12 hex) | Lượt | Thiết bị trong thân issue | Ghi chú |
|---|---|---:|---:|---|
| [#316](https://github.com/aowvn-10diem/aowvn-monika/issues/316) | `3f181942717a` | 114 | 52 | tái diễn (có) |
| [#372](https://github.com/aowvn-10diem/aowvn-monika/issues/372) | `7af5e6657638` | 90 | 12 | tái diễn (chưa) |
| [#478](https://github.com/aowvn-10diem/aowvn-monika/issues/478) | — | 1 | 1 | cùng loại |
| [#520](https://github.com/aowvn-10diem/aowvn-monika/issues/520) | `4e65d6ad0246` | 1 | 1 | cùng loại |
| **Tổng** | | **206** | | |

- Thân issue `crash-moi` chỉ có **hash chữ ký** (`lõi + loại + tối đa 3 frame đầu`), **không** có khung, build_id, hay tên tệp `.so`. Vì vậy mọi kết luận về nguyên nhân dưới đây là **đọc mã**, không phải từ dữ liệu crash. **[CHƯA KIỂM]**
- 52 thiết bị khác nhau ở #316 ⇒ không phải lỗi của một máy/driver cụ thể, nhưng cũng không loại trừ được nhóm GPU. **[CHƯA KIỂM]**
- Chưa có `CRASH_ADMIN_TOKEN` trong phiên này nên **không** đọc được báo cáo gốc trên Worker (không có khung/BuildId). Đây là chốt chặn của cả nhóm B.

## 2. Bốn nghi phạm

### (a) Đổi game / chuyển lõi khi `retro_unload_game` chưa xong — **loại trừ được bằng mã**

- `LibretroDroid::destroy()` (gọi `retro_unload_game` + `retro_deinit`) giữ `coreLock`: `libretrodroid/src/main/cpp/libretrodroid.cpp:453-482`.
- `loadGameFromPath/Bytes/VirtualFiles` **không** giữ `coreLock` (`libretrodroid.cpp:340-451`) ⇒ nếu chạy **song song** với `destroy()` thì khoá không che được. Nhưng trên đường của app, hai việc này **cùng một luồng GL**:
  - `RetroActivity` đặt `initializeOnGlThread = true` (`app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:308`);
  - `GLRetroView.onDestroy()` đẩy `destroy()` vào hàng đợi GL: `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:144-145`;
  - `initializeCore()` (nơi gọi `loadGame*`) chạy trên luồng GL: `GLRetroView.kt:391-419`.
- Kết luận: với đường hiện tại **không có** chồng lấn `destroy()` ↔ `loadGame*`. Nếu sau này có màn khác dùng `GLRetroView` mà để `initializeOnGlThread = false` thì giả thuyết này sống lại. **[CHƯA KIỂM]** trên máy thật.

### (b) `Environment` callback bị gọi sau `destroy()` — **còn hở một phần**

- `destroy()` khoá `coreLock` và gọi `Environment::getInstance().deinitialize()` ở cuối (`libretrodroid.cpp:480`).
- `step()` giữ `coreLock` **suốt** `retro_run` (`libretrodroid.cpp:520`), và callback `environment` do lõi gọi **bên trong** `retro_run`. Vì vậy **không được** thêm `coreLock` vào `handle_callback_environment` (`environment.cpp:255-259`): mutex không tái nhập ⇒ tự deadlock.
- Phần đã vá ở #606 (đã gộp): khoá cho `updateVariable/getVariables` và các lời gọi JNI khác (xem `libretrodroid.cpp:134-205`).
- Phần còn hở: callback đến từ **luồng nền của chính lõi** (audio/GPU worker của ppsspp) sau khi `retro_unload_game` trả về. Nếu ppsspp còn giữ thread riêng, nó có thể gọi lại `environment_handle_*` khi `video/audio/vfs` đã bị đặt `nullptr` (`libretrodroid.cpp:473-481`). Đây là **giả thuyết**, chưa có khung để chứng minh; nếu đúng thì là lỗi upstream (ppsspp phải join thread của nó trong `retro_unload_game`). **[CHƯA KIỂM]**

### (c) Cấu hình `lite` cho ppsspp chưa đủ — **có đề xuất, chưa vào main**

- Main hiện tại (`config/monika-config.json`, `configVersion 62`): `cores.ppsspp.perf.lite` chỉ có `ppsspp_auto_frameskip = enabled`; `full` có `ppsspp_internal_resolution = 960x544`.
- **#601 đang mở** (chưa gộp) hạ thêm: `ppsspp_vertex_cache = disabled`, `lite` → `480x272` (giữ `960x544` cho `full`), và thêm `-Wl,--build-id=sha1` cho `libretrodroid`.
- `ppsspp_vertex_cache = disabled` là ứng viên **hợp lý** cho SIGSEGV trên driver GPU lỗi (vertex cache dựng shader/buffer hình học), nhưng **chưa** có bằng chứng nối nó với 206 lượt này. Không tự sửa config: đổi `cores.*` phải tăng `configVersion` + đồng bộ Cloudflare (luật config-first). **[CHƯA KIỂM]**

### (d) Lõi ppsspp không khớp BuildId (ghi chú cũ ở #337) — **đã hết, không còn là nghi phạm**

- Lõi ppsspp không tải từ buildbot nữa: config trỏ `aowvn-monika-packs` release `cores-ppsspp-{abi}.zip` (`cores-ppsspp-1`) kèm `sha256` từng ABI.
- Bản `cores-ppsspp-1` **có** `symbols-ppsspp-arm64-v8a.zip` (53,5 MB) và `symbols-ppsspp-armeabi-v7a.zip` (51,1 MB), cùng `sources-*.zip` và `manifest.json` (kiểm bằng `gh release view cores-ppsspp-1 --repo aowvn-10diem/aowvn-monika-packs`).
- Đường dựng (`scripts/prepare-core-build.py`): `CMAKE_BUILD_TYPE=RelWithDebInfo`, `-Wl,--build-id=sha1` (`:343-360`), **bắt buộc** có debug info (`require_debug_symbols`, `:301-306`, `:396`), chỉ strip bản phát hành sau khi đã đóng gói symbols (`:406`, `:441-447`). Nguồn ppsspp ghim `9366aa7dc2d2fb1f49cde97ffbf5ab434d4d6ff9` (`engines/sources.lock.json`).
- Kết luận: **đủ điều kiện retrace** cho ppsspp. Chốt chặn duy nhất là quyền đọc Worker.

## 3. Cách lấy khung (việc tiếp theo, cần `CRASH_ADMIN_TOKEN`)

1. Lấy danh sách + chọn một báo cáo `engine:libretro:ppsspp` / `kind=native` của chữ ký cần kiểm:
   `CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh`
2. Tải symbols đúng ABI của bản đang chạy (tag trong báo cáo → `cores-ppsspp-<n>`):
   `gh release download cores-ppsspp-1 --repo aowvn-10diem/aowvn-monika-packs -p 'symbols-ppsspp-arm64-v8a.zip'`
3. Giải mã, chỉ ghép khung có PC + BuildId (không đoán theo tên `.so`):
   `scripts/crash-reports.sh <id> --symbols-dir <thư mục đã giải nén> --symbolizer <llvm-symbolizer>`
4. Kết quả mong đợi: tên hàm/dòng trong `libppsspp_libretro.so`. Nếu khung nằm trong `ppsspp/…` → **lỗi upstream**, mở issue ở `hrydgard/ppsspp` kèm commit ghim ở trên. Nếu khung nằm trong `environment.cpp`/`video.cpp` của `libretrodroid` → mới là việc sửa trong repo.

## 4. Vì sao chưa sửa mã

- Không có khung ⇒ không biết khung nào trong 3 frame đầu; sửa lúc này là đoán. Hai nghi phạm (a) và (d) đã bị loại bằng mã; (b) chỉ còn nhánh "thread nền của lõi" (sửa ở upstream hoặc phải chứng minh trước); (c) là thay config, cần PM duyệt + đồng bộ Cloudflare, và #601 đã mang sẵn thay đổi đó.
- Không đụng `libretrodroid/src/main/cpp/**` và `GLRetroView`/`EmulationCall` trong lượt này: `#619` (Lỗi 1) đang mở trên hai tệp Kotlin đó; vùng native vừa được #606 vá.

## 5. Điều kiện để đóng #316 / #372 / #478 / #520

- Có ít nhất 1 báo cáo được giải mã tới **tên hàm + dòng** cho mỗi chữ ký; ghi kết quả vào issue.
- Nếu khung thuộc upstream: mở issue upstream + dán link; giữ issue của mình mở tới khi có bản lõi mới.
- Nếu khung thuộc `libretrodroid`: PR sửa + test, đo lại số lượt sau 2 bản.
- Trước khi có khung: **không** đóng, **không** hứa bản sửa. **[CHƯA KIỂM]**
