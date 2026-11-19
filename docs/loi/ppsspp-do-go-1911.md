# `ppsspp/native` 206 lượt: đo cửa sổ unload bằng vệt chẩn đoán (19/11/2026)

Nhánh `loi/ppsspp-do-go`, nền `origin/main` **`6cdf4ce1`** (configVersion 65, app 0.8.15).
Phạm vi: `libretrodroid/src/main/cpp/**` — **chỉ thêm vệt**, không sửa logic lõi.
Người lập: phiên con **Trưởng nhóm Lỗi**. Mọi kết luận chưa có báo cáo mới đều ghi **[CHƯA KIỂM]**.

## 0. Kết luận trước

| | |
|---|---|
| Nhóm đang nhắm | `ppsspp/native` — #316 (114 lượt), #372 (90), #520 (1), #478 (1) = **206 lượt**; kèm `ppsspp/unknown` #416 (8) |
| Lượt này làm gì | **Đo**, không vá: **6** mốc vệt quanh `dlopen`/`destroy()` → `retro_deinit` → `dlclose`, mỗi mốc ghi số thread + **tên từng thread** + core + handle + BuildId |
| Vì sao đo được | Vệt ghi vào **file `diag/crumbs/<pid>.log`** (sống sót native crash) **và** logcat tag `MonikaUnload` (`Diagnostics.logcat(pid)` đã lấy vào trường `log` của báo cáo) |
| Đóng issue | **Không** — chưa có báo cáo nào trên bản có vệt. Cần **một báo cáo mới** (§5) |
| Đã kiểm | biên dịch `arm64-v8a` + `armeabi-v7a` `exit=0` (§6); `testDebugUnitTest` (§6) |
| Chưa kiểm | chạy trên máy thật, đọc vệt thật, tái hiện crash **[CHƯA KIỂM]** |

## 1. Giả thuyết cần đo

`LibretroDroid::destroy()` gọi `retro_unload_game()` rồi `retro_deinit()`, sau đó đặt `core = nullptr`
(`libretrodroid.cpp:498-536`), tức chạy `Core::close()` → **`dlclose(libHandle)`** (`core.cpp:74-84`).
Trên bionic, `dlclose` **munmap** segment của `.so` khi refcount về 0. Nếu lõi (ppsspp) còn một thread nội bộ
sống sau `retro_deinit`, lệnh kế tiếp của thread đó nằm trong vùng **đã unmap** ⇒ `SIGSEGV`/`SIGBUS` với PC
**bên trong `.so` của lõi**, không có khung nào của `libretrodroid.so` phía trên — đúng hình dạng chữ ký
`ppsspp/native` (chỉ băm `lõi + loại + ≤3 frame`) và đúng việc #316 trải **51 thiết bị** khác nhau
(`docs/loi/ppsspp-native-1311.md` §1). Đây là nghi phạm mạnh nhất còn lại sau PR #663
(`docs/loi/native-cpp-hien-truong-1811.md` §1.3), và **chưa có bằng chứng nào**. **[CHƯA KIỂM]**

Không vá vì: bỏ `dlclose` là đổi chính sách vòng đời tài nguyên, và chưa biết thread nào còn sống. Lượt này
chỉ trả lời câu hỏi hẹp: **"tiến trình chết trong cửa sổ unload hay không, và lúc dlclose còn thread lạ nào"**.

## 2. Vệt đặt ở đâu

Tệp mới: `libretrodroid/src/main/cpp/diag.h`, `diag.cpp` (thêm vào `CMakeLists.txt:88-89`).
API: `diag::setCore(path, handle)` khi `dlopen` xong; `diag::mark("<mốc>")` ở từng mốc.

| Mốc | Vị trí | Ý nghĩa |
|---|---|---|
| `dlopen-done` | `core.cpp:47-48` | `.so` đã map, **trước** `retro_init` ⇒ **mốc gốc**: mọi thread ở đây là thread của ta (GLThread, oboe, binder…) |
| `destroy-enter` | `libretrodroid.cpp:504` | bắt đầu gỡ game (đang giữ `coreLock`) |
| `deinit-before` | `libretrodroid.cpp:520` | ngay trước `retro_deinit()` |
| `deinit-done` | `libretrodroid.cpp:522` | ngay sau `retro_deinit()` trả về |
| `dlclose-before` | `core.cpp:78` | ngay trước `dlclose(libHandle)` |
| `dlclose-done` | `core.cpp:80` | ngay sau `dlclose()` trả về |
| `core-null` | `libretrodroid.cpp:524` | `destroy()` không có lõi (đường lỗi) — để không nhầm với "không có vệt" |

Mỗi dòng vệt (một dòng, không xuống dòng giữa):

```
HH:MM:SS.mmm [unload] deinit-done core=libppsspp_libretro.so bid=e865a46584af57b8f08e3d8f7209d39947c86e9b dl=0x7f0a1c2d30 t=812345 thr=17 [*1310:Emu,*1312:GPU,1234:main,1250:GLThread,1320:Audio (+6)]
```

| Trường | Nguồn | Ghi chú |
|---|---|---|
| `core=` | tên tệp `.so` của lõi (không ghi đường dẫn đầy đủ) | |
| `bid=` | GNU BuildId đọc từ note `NT_GNU_BUILD_ID` của `.so` | khớp `build_id` trong tombstone ⇒ biết đúng gói ký hiệu cần tải |
| `dl=` | handle `dlopen` | |
| `t=<ms>` | `CLOCK_MONOTONIC` | đo độ dài từng đoạn (unload / deinit / dlclose) |
| `thr=N` | đếm mục trong `/proc/self/task` | lỗi mở thư mục ⇒ `thr=?` (không ném) |
| `[tid:tên,…]` | `/proc/self/task/<tid>/comm`; trần **48** thread, tên ≤12 ký tự | `*` = thread sinh **sau** `dlopen-done` (nhóm quyết định — xem §4); `(+N)` = số thread bị cắt khỏi danh sách |

**Thứ tự và độ dài dòng có chủ đích** (`diag.cpp:37`, `:45`, `:239-271`, `:349-395`): `core=` và `bid=` đứng
**đầu**, trước danh sách thread; dòng tự giới hạn **≤ 290 ký tự** (`kMaxLineChars`), dưới mức `Diagnostics.logcat`
cắt mỗi dòng logcat còn **300** ký tự (`Diagnostics.kt:380`). Khi danh sách thread dài, phần bị cắt là **đuôi**,
và thứ tự ưu tiên là: thread `*` (sinh sau `dlopen-done`) → tên đặc trưng của lõi (`Emu`/`GPU`/`Audio`/`Render`,
`core N`) → còn lại. Vì vậy không bao giờ mất `core=`/`bid=` hay thread của lõi, kể cả khi tiến trình `:game`
có 40 thread.

Ghi ở 2 nơi, **best-effort**, không ném lỗi:

1. `__android_log_print(INFO, "MonikaUnload", …)` — `Diagnostics.logcat(pid)` (`Diagnostics.kt:369-377`) lấy **mọi** dòng của pid đang chết vào trường `log`, nên vệt vào báo cáo dù không mở được tệp.
2. Nối vào chính tệp vệt sẵn có: `filesDir/diag/crumbs/<pid>.log` (`Breadcrumbs.kt:16-28`) ⇒ hiện ở mục "vệt sự kiện trước lỗi" của báo cáo. Thư mục suy từ `/proc/self/cmdline` (`<gói>[:tiến trình]`) + `uid/100000` (`/data/user/<n>/<gói>/files/diag/crumbs`, dự phòng `/data/data/<gói>/…`); **chỉ nhận thư mục đã tồn tại**, không tạo thư mục, không đổi quyền tệp.

## 3. Không đổi hành vi

- Không đổi thứ tự lời gọi: `retro_unload_game()` → `retro_deinit()` → `core = nullptr` → `Environment::deinitialize()` → `VFS::deinitialize()` giữ nguyên; `dlclose` vẫn chạy, vẫn đúng một lần.
- Không thêm `sleep`, không `join`, không `pthread_kill`, không đổi `coreLock`.
- Chi phí: 1 lần đọc ELF khi `dlopen` (đọc ~64 B + note, không đọc cả tệp) + 6 lần đọc `/proc/self/task` trong cả vòng đời một phiên (mỗi lần ~20 tệp `comm` nhỏ). Không đụng đường `retro_run`.
- Nếu không suy được đường dẫn vệt: chỉ còn logcat, không lỗi, không cảnh báo cho người chơi.

## 4. Cách đọc vệt và bảng quyết định

### 4.1 Đọc ở đâu trong báo cáo

1. Trường `log` (báo cáo `native` của tiến trình `:game`): tìm dòng có `MonikaUnload`.
2. Mục "vệt sự kiện trước lỗi" (`crumbs`): tìm dòng có `[unload]`.
3. Đối chiếu `bid=` với danh sách `symbols-ppsspp-*` (release `cores-ppsspp-1`) rồi giải ký hiệu như
   `docs/loi/native-cpp-hien-truong-1811.md` §5.2.

### 4.2 Bảng quyết định

> **Lưu ý về luật "thread count > 1 ⇒ xác nhận"**: trong tiến trình `:game` **luôn** có nhiều thread của
> chính app (`main`, `GLThread`, thread oboe/audio, `JavaBinder`, `Signal Catcher`, `RenderThread`…). Vì vậy
> `thr > 1` **không** phân biệt được gì. Vệt vì thế ghi thêm **tên thread**, và mốc gốc `dlopen-done`
> (trước `retro_init`) cho biết tập thread "không phải của lõi". Quyết định dựa trên **hiệu tập**, không dựa
> trên con số. Thread có dấu `*` là thread sinh **sau** `dlopen-done` — đúng nhóm cần nhìn; `(+N)` chỉ là số
> thread bị cắt khỏi danh sách (danh sách dài quá ngân sách 48 mục/290 ký tự), **không** phải thread đã chết.

| Dấu hiệu trong vệt | Cách đọc | Kết luận |
|---|---|---|
| Có `dlclose-before`, **không** có `dlclose-done` | chết đúng bên trong lời gọi `dlclose` | **Xác nhận chết trong cửa sổ unload.** Nếu thêm thread lạ ở hàng dưới ⇒ đúng giả thuyết §1 |
| Ở `dlclose-before`/`deinit-done` có thread **`*`** hoặc tên đặc trưng của lõi (`Emu`, `GPU`, `Audio`, `Render`, `core N`…) | thread sinh ra trong lúc lõi chạy, còn sống sau `retro_deinit` | **Xác nhận giả thuyết**: còn thread lõi khi `dlclose`. Sang bước A/B `dlclose` (thí nghiệm 3, `native-cpp-hien-truong-1811.md` §5) trước khi sửa |
| Tập thread ở `dlclose-before` **chỉ là** tập ở `dlopen-done` (không có `*`, cộng thread chết đi) | không có thread lõi sống sót | **Loại giả thuyết `dlclose`**; chuyển sang nghi phạm khác: chết trong `retro_run` (lõi/GPU), cấu hình `ppsspp_vertex_cache`, hoặc file game |
| Có `dlclose-done` nhưng tiến trình chết **< 1 s** sau dòng đó, và tên thread chết **không** có ở `dlopen-done` | thread lõi tỉnh dậy sau khi bị munmap (đang kẹt trong syscall) | Vẫn tính là **xác nhận**; phải so tên thread chết trong tombstone với danh sách ở `dlclose-done` |
| Chỉ có `thr=N` với `N>1`, không có tên thread lõi | thread của app | **Không** kết luận |
| Không có dòng `[unload]` nào | bản chưa có vệt, hoặc crumbs bị cắt (>24 KB), hoặc chết trước khi vào `destroy()` | Chưa kết luận — xem mốc cuối cùng có được (`playing` ⇒ chết giữa màn chơi) |
| `thr=?` | không đọc được `/proc/self/task` | Vệt vẫn dùng được cho **thời điểm** chết; phần thread bỏ trống |

Ba câu trả lời mà bảng này đưa ra, theo thứ tự: **(1)** chết trong cửa sổ unload hay giữa màn chơi;
**(2)** nếu trong cửa sổ unload thì có thread lạ nào; **(3)** BuildId của `.so` để giải ký hiệu đúng gói.

## 5. Cần gì để kết luận

1. **Một bản phát hành có vệt này** (bản chứa PR này; vệt chỉ có từ bản đó — báo cáo cũ **không** cứu được).
2. **Ít nhất 1 báo cáo mới cho #316 và 1 cho #372** (hai chữ ký lớn nhất, 204/206 lượt), kèm `device`,
   `signal`, `frames`, `bid`. Điều kiện lấy: `CRASH_ADMIN_TOKEN` (không có trong phiên này).
3. Đối chiếu theo §4.2, ghi kết quả vào từng issue:
   - xác nhận ⇒ mở thí nghiệm A/B `dlclose` (2 APK thử, đếm số lần chết trên máy có báo cáo) rồi mới bàn sửa;
   - loại ⇒ đóng nhánh nghi phạm này trong tài liệu, chuyển sang `retro_run`/GPU/cấu hình, và **vẫn không** vá mù.
4. Chỉ đóng #316/#372/#478/#520 khi có bảng khung đã giải mã **hoặc** kết quả A/B giảm số lần chết về 0
   (giữ nguyên điều kiện ở `native-cpp-hien-truong-1811.md` §6). PR này **không** đóng issue nào.

## 6. Kiểm chứng đã chạy

Máy Windows của repo, NDK `22.1.7171670` (đúng `ndkVersion`), CMake `3.22.1` + Ninja:

```
cmake -G Ninja -DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -S libretrodroid/src/main/cpp -B <build>
ninja -C <build>
```

| ABI | Kết quả | `liblibretrodroid.so` | Ghi chú |
|---|---|---:|---|
| `arm64-v8a` | `exit=0`, 136/136 bước | 14.941.656 B | không cảnh báo ở `diag.cpp`/`core.cpp`/`libretrodroid.cpp` (chỉ cảnh báo cũ `initializeGLESLogCallbackIfNeeded` của `log.h`) |
| `armeabi-v7a` | `exit=0`, 136/136 bước | 10.716.212 B | nhánh ELF32 của bộ đọc BuildId biên dịch được |

Kiểm BuildId: `llvm-readelf -n liblibretrodroid.so` → `Displaying notes found in: .note.gnu.build-id`,
`GNU 0x00000014 NT_GNU_BUILD_ID` (desc 20 B) ⇒ đúng định dạng bộ đọc trong `diag.cpp:80-160` mong đợi.
Dựng lại thuật toán đó bằng một đoạn đọc ELF độc lập (PowerShell, cùng offset/`PT_NOTE`/`NT_GNU_BUILD_ID`)
trên chính tệp vừa dựng: ra `cb9f14894ab06a4128ea252d7732b985f5ec3eab`, **trùng** `llvm-readelf` ⇒ cách đọc
note đúng (đây là kiểm thuật toán, không phải kiểm mã C++ đã chạy trên máy thật).

`./gradlew testDebugUnitTest --continue` (Windows, `ANDROID_HOME` trỏ SDK của máy; `BUILD FAILED` sau 5m56s):

| Bộ test | Kết quả | Ghi chú |
|---|---|---|
| `:app:testGithubDebugUnitTest` | **1309 test, 19 hỏng, 3 bỏ qua** | 1306 test cũ + 3 test mới của `UnloadLineTest`; 19 ca hỏng đúng bộ môi trường Windows đã biết |
| `:app:testPlayDebugUnitTest` | **28 test, 1 hỏng** | `PlayAskGroupTest > configuredGroupReceivesReadableImageAndQuestionThroughChooser` — ca đã biết |
| `UnloadLineTest` (mới) | **3/3 đạt, 0 bỏ qua** | chạy riêng `--tests "vn.aow.monika.diag.UnloadLineTest"` cũng `BUILD SUCCESSFUL` |
| các module khác (`libretrodroid`, `j2me`, `reader`, …) | không hỏng | |

19 ca hỏng của `github` đều do môi trường, không liên quan mã: `FileSystemException` trên thư mục tạm
(`ConsoleSaveTransferTest`, `GameSaveTransferTest`), dấu phân cách `\` thay `/` (`DeviceScannerTest` ×6,
`ImporterTest`), `Failed to find configured root` với đường dẫn `C:\` (`LibraryDocumentsTest`), `lateinit
property app` của Robolectric (`RarTest` ×6), `IOException: Không lưu được lõi/cache` khi `renameTo` trên
Windows (`CorePreparationCacheTest` ×2), và `DeathContextTest` (thứ tự stage `start` thay `core-ready` trên
Windows).

**Không có ca hỏng mới**: lượt này chỉ **thêm** một test JVM (`UnloadLineTest`) và sửa mã native; không sửa
mã sản phẩm Kotlin/Java nào (`app/src/main/**` không đổi), nên 19 ca hỏng giữ nguyên đúng danh sách cũ.

`UnloadLineTest` gồm 3 ca: (1) đọc `diag.cpp` để khoá hằng số `kMaxThreads = 48`, `kMaxLineChars = 290` và
thứ tự `core=`/`bid=` trước `t=`; (2) dòng 40 thread (thread lõi ở cuối dãy tid) vẫn giữ `core=`, `bid=`,
`*…:Emu`, `*…:GPU` và có `(+N)`; (3) dòng dài quá 300 ký tự sau khi bị `take(300)` vẫn còn `core=`/`bid=`.

**[CHƯA KIỂM]**: chạy trong game thật, sự có mặt của dòng `[unload]` trong báo cáo thật, và hành vi khi
`filesDir` không suy được (chỉ còn logcat).

## 7. Việc không làm trong lượt này

- Không sửa `diag/Diagnostics.kt` (PR #683 đang mở trên chính tệp đó): chỗ cắt 300 ký tự
  (`Diagnostics.kt:380`) được xử lý **từ phía native** — đặt `core=`/`bid=` lên đầu dòng và tự giới hạn
  290 ký tự, nên dòng bị cắt không bao giờ mất trường quyết định.
- Không sửa `GLRetroView.kt` / `RetroActivity.kt`.
- Không sửa `config/`, không đổi `configVersion`, không đụng tệp khoá/chữ ký.
- Không bỏ `dlclose`, không thêm join/sleep, không đổi thứ tự gọi.
- Không đóng issue, không hứa bản sửa.
