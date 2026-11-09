## V202 — Xử lý gốc báo cáo hết RAM (`363ea01f`, `3305de8b`) và `melondsds` `killed` / `ANR`

- Người làm: Argon (phân tích & đặc tả, 08/11/2026) → DeepSeek / Đội Lỗi thi công từng PR (mã V202 tạm, PM chốt)
- Nguồn dữ liệu: [#549](https://github.com/aowvn-10diem/aowvn-monika/issues/549), [#304](https://github.com/aowvn-10diem/aowvn-monika/issues/304), [#306](https://github.com/aowvn-10diem/aowvn-monika/issues/306), [#318](https://github.com/aowvn-10diem/aowvn-monika/issues/318), PR #542 (`docs/loi/TON-0811.md`), artifact `crash-insight` run [`42502867747`](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/42502867747) trên `main@392b029`
- Trạng thái: Nháp (chờ PM duyệt trước PR mã đầu tiên)

### 1. Mục tiêu
Người chơi game NDS (`melondsds`, ROM Việt hóa 128–512 MB) trên máy 3–6 GB RAM không còn bị văng giữa chừng hoặc đơ màn hình khi bấm Home / khóa máy, đồng thời tiến trình chính của app tự nhả bộ nhớ đệm khi ra nền hoặc khi vào game. Bảng chẩn đoán lỗi chỉ còn ghi nhận các ca thiếu RAM khi đang dùng thật, chấm dứt cảnh >89% báo cáo bị lấp bởi việc Android dọn tiến trình nền bình thường.

### 2. Ngoài phạm vi
- Không biên dịch lại nhị phân `libmelondsds_libretro_android.so` (kho `libretro-super` / gói `cores` ngoài repo); chỉ sửa tầng C++/Kotlin trong `:libretrodroid` và `:app`.
- Không chuyển `WebGameActivity` sang tiến trình riêng (sẽ làm đứt `localStorage`/cookie WebView hiện có của người chơi).
- Không đổi định dạng tệp save `.srm`/savestate, phím bấm hay bố cục màn hình.

### 3. Tiêu chí đạt
- [ ] **PR 1 (`Diagnostics`)**: Bỏ qua `ApplicationExitInfo` có `REASON_LOW_MEMORY` khi `importance >= IMPORTANCE_CACHED` (400) và không có `deathSession`, hoặc khi `processName` chứa `sandboxed_process`; không trộn `crumbs` cũ vào `Components.of` khi `trace` rỗng; ghi kèm `imp=<importance> pss=<pss>MB` trong `describeReason`.
- [ ] **PR 2 (`:libretrodroid` C++)**: Giải phóng ngay `file.data` và `game_info.path` bằng RAII sau `core->retro_load_game(&game_info)` trong `loadGameFromPath` và `loadGameFromVirtualFiles` (kể cả khi ném ngoại lệ); kiểm tra lỗi mở tệp/`tellg() < 0` và sửa `fclose(file)` trong `utils.cpp`.
- [ ] **PR 3 (`RetroActivity` & `GLRetroView`)**: Không còn gọi `CountDownLatch.awaitUninterruptibly()` vô thời hạn hay `sramFile.writeBytes` trên luồng UI trong `onPause()`; chụp SRAM trực tiếp trên `GLThread` trước khi dừng luồng hoặc chờ tối đa 400 ms rồi ghi đĩa nguyên tử (`.tmp` → `renameTo`) trên `Dispatchers.IO`; bọc khóa khi đọc/cập nhật `Variable`.
- [ ] **PR 4 (`MonikaApp` & bộ đệm)**: Cài `MonikaApp.onTrimMemory(level)` ở cả hai nguồn `github`/`play` và gọi xả chủ động ngay trước khi mở tiến trình `:game`/`:midlet`: xóa `imageLoader.memoryCache`, giới hạn `FeedRepository.cache` bằng `LruCache(60)` đồng bộ luồng (xóa `contentHtml` bài cũ khi trim), nhả `AdBlock` (`hashes`/`engine`) khi nền, bổ sung `WebGameActivity.onPause`/`onDestroy` (`web.destroy()`) và `InAppBrowserActivity.onPause`/`onResume`.

### 4. Bản Play
`điều chỉnh`: PR 1–3 dùng chung (`src/main`, `:libretrodroid`). PR 4 cài `onTrimMemory` cho cả `app/src/github/.../MonikaApp.kt` và `app/src/play/.../MonikaApp.kt` (bản Play không đụng `InAppBrowserActivity`); chạy đủ `scripts/truoc-khi-push.sh` để giữ sạch scanner Play.

### 5. Config
Không đổi (`config/monika-config.json` và `MonikaConfig.kt` giữ nguyên).

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | Ai làm | PR |
|---|---|---|---|---|
| 1 | Lọc `IMPORTANCE_CACHED` + chặn nhiễm nhãn `crumbs` (`363ea01f`/`3305de8b`) trong `Diagnostics.kt` + unit test | Đặc tả duyệt | DeepSeek | #… |
| 2 | Vá rò rỉ native heap `retro_game_info` (128–512 MB) & `FILE*` trong `:libretrodroid` | Đặc tả duyệt (song song PR 1) | DeepSeek | #… |
| 3 | Tách lưu SRAM khỏi khóa cứng UI thread khi `onPause` (`melondsds` ANR `#318`) | 2 | DeepSeek | #… |
| 4 | `onTrimMemory`, `FeedRepository` LRU, `AdBlock.trim()`, dọn `WebView` khi thoát | Đặc tả duyệt (song song PR 1–2) | DeepSeek | #… |

### 7. Rủi ro
- **An toàn dữ liệu save (`PR 3`)**: Nếu ghi `.srm` bất đồng bộ mà `RetroActivity.onDestroy()` gọi `Process.killProcess(myPid())` (`RetroActivity.kt:646`) quá sớm, tệp `.srm` sẽ cụt → bắt buộc ghi ra `<tên>.srm.tmp`, `fd.sync()`, `renameTo` xong **trước** khi gọi `killProcess` (hoặc đợi job IO hoàn tất tối đa 1.500 ms trong `onDestroy` khi `isFinishing`).
- **Tương thích lõi libretro (`PR 2`)**: Chuẩn `libretro` quy định lõi có `need_fullpath == false` phải tự sao chép dữ liệu trong `retro_load_game`, trừ khi lõi bật `RETRO_ENVIRONMENT_GET_GAME_INFO_EXT` — hiện `environment.cpp:234-351` không cài đặt callback này nên giải phóng sau `retro_load_game` an toàn cho mọi lõi hiện hữu (`melondsds`, `mgba`, `gambatte`, `fceumm`, `snes9x`).
- Không thêm bất kỳ lượt gọi Worker nào; ngược lại giảm ~85–90% lượt gửi báo cáo `lowmem` rác lên KV.

---

### 8. Bằng chứng nguyên nhân gốc (`main@392b029`)

1. **Giải mã toán học `363ea01f` và `3305de8b` — cùng 1 hiện tượng, không có tiến trình mạng `:net`** (Mức rủi ro: **Cao** — chiếm `902/1.007 = 89,6%` lượt sự kiện ở run `42502867747`, `11.906` lượt tích lũy ở `#304`):
   - Hàm băm `Diagnostics.fingerprint` (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:709-715`) tạo khóa `"${r.kind}|${r.component}|${r.session?.core}|${r.reason.take(60).replace(/\d+/, "")}|$frames"`:
     - `"lowmem|app:chính||LOW_MEMORY (status )|".hashCode()` = **`0x363ea01f`** (`828` lượt/cửa sổ).
     - `"lowmem|net||LOW_MEMORY (status )|".hashCode()` = **`0x3305de8b`** (`71` lượt/cửa sổ).
     - `"lowmem|pack:sevenzip||LOW_MEMORY (status )|".hashCode()` = **`0x93057733`** (`2` lượt/cửa sổ).
     - `"killed|engine:libretro:melondsds|melondsds|SIGNALED (status )|".hashCode()` = **`0x98a66014`**.
   - **Lỗi 1a**: `Diagnostics.collectProcessDeaths` (`Diagnostics.kt:819-826`) thu mọi `REASON_LOW_MEMORY` mà **không kiểm `e.importance`**, gom cả tiến trình nền `IMPORTANCE_CACHED (400)` và `SandboxedProcessService0` của WebView (`72fcdd6f`). Khi người chơi mở game ở `:game` hoặc qua app khác, `lmkd` thu hồi tiến trình `chính` đang cached là cơ chế bình thường của Android.
   - **Lỗi 1b**: Với `LOW_MEMORY`, `traceStrings(e)` trả rỗng (`Diagnostics.kt:302`), nhưng dòng `Diagnostics.kt:833` truyền `trace + "\n" + crumbs.joinToString("\n")` vào `Components.of` (`app/src/main/java/vn/aow/monika/diag/Components.kt:25-32`). Chỉ cần đầu phiên mạng chập chờn để lại crumb `SocketTimeoutException`/`UnknownHostException` (`Diagnostics.kt:451`), lần thu hồi nền cuối ngày bị gán nhầm thành `"net"` (`3305de8b`) hoặc `"pack:sevenzip"` (`93057733`).

2. **Rò rỉ C++ nhân đôi kích thước ROM (`128–512 MB`) trong `:libretrodroid` khi chạy `melondsds`** (Mức rủi ro: **Nghiêm trọng** — gốc của `#306` `481` lượt, `#447` `176` lượt, `#317` `148` lượt, `#335` `89` lượt):
   - `libretrodroid/src/main/cpp/libretrodroid.cpp:319, 326-341` (`loadGameFromPath`) và `:386, 397-409` (`loadGameFromVirtualFiles`):
     - Dòng `319` & `386`: `Utils::cloneToCString` (`libretrodroid/src/main/cpp/utils/utils.cpp:57-61`) cấp `new char[len + 1]` không bao giờ `delete[]`.
     - Dòng `326-328` & `397-399`: khác `ppsspp` (`need_fullpath = true`), lõi `melondsds` đặt `system_info.need_fullpath == false`, khiến `Utils::readFileAsBytes` (`utils.cpp:27-47`) cấp **`new char[size]` nạp trọn tệp `.nds` (128–512 MB) vào native heap**, truyền vào `core->retro_load_game(&game_info)` (bản thân `melondsds` tự cấp bộ đệm cart riêng + JIT), rồi kết thúc hàm **mà không `delete[] file.data`**.
     - Phụ trợ `utils.cpp:28-35, 40-46`: mở `ifstream` thiếu `std::ios::binary` và không kiểm `tellg() < 0` (ép sang `size_t` thành `SIZE_MAX` → `std::bad_alloc`/`SIGABRT`); `readFileAsBytes(int fd)` gọi `fdopen` rồi `close(fileDescriptor)` thay vì `fclose(file)` làm rò `FILE*` và không kiểm `file == nullptr`.
   - Đúng 1 ROM NDS 256 MB làm `:game` đội thêm **256 MB RAM chết** suốt phiên chơi → tổng PSS vượt ngưỡng trên máy 3–6 GB → Android giết sạch tiến trình nền `app:chính` (`363ea01f`) rồi giết luôn `:game` bằng `SIGKILL` (`SIGNALED` → `killed`).

3. **Khóa cứng UI thread khi `onPause` gây `melondsds` ANR (`#318` `168` lượt, `ffdfa861`)** (Mức rủi ro: **Cao**):
   - Khi tạm dừng Activity, `RenderLifecycleObserver.pause()` (`libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt:343-350`) chạy trước `RetroActivity.onPause()`, gọi `GLSurfaceView.onPause()` và `LibretroDroid.pause()`. Ngay sau đó `RetroActivity.onPause()` (`app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:629-635, 650-657`) gọi `view.serializeSRAM()` → `runOnEmulationThread` (`GLRetroView.kt:440-448`) đẩy `queueEvent` vào `GLThread` đã dừng/đang kẹt giữ `coreLock` trong `melondsds` (`melonds_threaded_renderer = enabled`) rồi gọi **`latch.awaitUninterruptibly()` không timeout**, kèm `sramFile.writeBytes(bytes)` ngay trên UI thread.
   - Thêm vào đó, `GLRetroView.kt:254-262` (`getVariables`/`updateVariables`, gọi từ `RetroActivity.kt:238, 468`) gọi JNI trực tiếp không đồng bộ với `coreLock` (`libretrodroid.cpp:620`).

4. **Tiến trình `app:chính` giữ 80–150 MB bộ nhớ khi đã ra nền / nhường `:game`** (Mức rủi ro: **Trung bình–Cao**):
   - Toàn repo hiện không có hàm `onTrimMemory` nào.
   - `app/src/github/java/vn/aow/monika/MonikaApp.kt:22-27` (và `src/play`): Coil `MemoryCache` giữ `20%` heap (~50–100 MB bitmap) khi app đã ẩn; dòng `38, 86-90` khởi tạo sẵn `J2meRuntime` ngay trong `app:chính` dù game Java chạy ở `:midlet`.
   - `app/src/main/java/vn/aow/monika/feed/FeedRepository.kt:38, 54, 76, 100, 103`: `cache = mutableMapOf<String, Post>()` không giới hạn, không thread-safe, giữ nguyên `contentHtml` của mọi bài đã mở cùng `pagesCache` (tới 500 trang).
   - `app/src/main/java/vn/aow/monika/runner/WebGameActivity.kt:63, 143-151` (chạy trong `app:chính` — `app/src/github/AndroidManifest.xml:139-144`): thiếu `web.onPause()`/`pauseTimers()` và **không có `onDestroy()` gọi `web.destroy()`**, giữ sống WebView + renderer sau khi thoát game web. `app/src/github/java/vn/aow/monika/browser/InAppBrowserActivity.kt:395-406` thiếu `web?.onPause()`/`onResume()`.
   - `app/src/main/java/vn/aow/monika/browser/AdBlock.kt:21-22`: giữ thường trực `hashes` (~2 MB) + `FilterEngine` trong `AppGraph.adblock` sau khi đóng trình duyệt.

---

### 9. Thiết kế sửa chi tiết cho DeepSeek (theo từng PR)

- **PR 1 (`Diagnostics.kt`)** — *Ai làm: DeepSeek*:
  1. Tách hàm thuần `internal fun shouldReportLowMem(importance: Int, processName: String?, hasDeathSession: Boolean): Boolean`: trả `false` nếu `processName?.contains("sandboxed_process") == true`, hoặc nếu `!hasDeathSession && importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED`. Gọi tại `Diagnostics.kt:822-832` (vẫn cập nhật `newest` và `clearDeathSession`/`Breadcrumbs.drop` để không quét lại).
  2. Tại `Diagnostics.kt:833`: đổi nguồn đoán thành phần sang `val hint = if (trace.isNotBlank()) "$trace\n${crumbs.joinToString("\n")}" else ""` để khi `trace` rỗng (`lowmem`), `Components.of(c, deathSession, hint)` chỉ dựa vào `deathSession`, rơi về `"app:$proc"` thay vì nhiễm `"net"`/`"pack:*"`.
  3. Tại `describeReason` (`Diagnostics.kt:284-298`): nối thêm `"imp=${e.importance} pss=${e.pss shr 10}MB"` (chú ý `fingerprint` ở dòng `714` đã xóa chữ số `\d+` nên không làm vỡ gộp nhóm).

- **PR 2 (`:libretrodroid` C++)** — *Ai làm: DeepSeek*:
  1. Trong `utils.cpp:27-47`: mở `std::ifstream fileStream(filePath, std::ios::binary | std::ios::ate)`; nếu `!fileStream` hoặc `pos < 0` thì ném `std::runtime_error`; ở bản nhận `fileDescriptor`, kiểm `if (!file) { close(fileDescriptor); throw ...; }`, đọc xong gọi `fclose(file)` (tự đóng cả `fd`, bỏ `close(fileDescriptor)` rời).
  2. Trong `libretrodroid.cpp:318-341` và `385-409`: bọc `std::unique_ptr<const char[]> pathHolder(Utils::cloneToCString(...))` và `std::unique_ptr<char[]> dataHolder(file.data)`; ngay sau `bool result = core->retro_load_game(&game_info);` gọi `dataHolder.reset(); pathHolder.reset();` trước cả nhánh `if (!result) throw ...` và trước `afterGameLoad()`.

- **PR 3 (`GLRetroView.kt` & `RetroActivity.kt`)** — *Ai làm: DeepSeek*:
  1. Thêm hàm không chặn vô hạn trong `GLRetroView.kt`: `fun serializeSRAMOrNull(timeoutMs: Long = 400L): ByteArray?` dùng `latch.await(timeoutMs, TimeUnit.MILLISECONDS)` (nếu quá hạn trả `null`, không treo UI).
  2. Trong `RetroActivity.kt`: gọi chụp SRAM **trước** `super.onPause()` (hoặc đăng ký callback chụp ngay đầu `RenderLifecycleObserver.pause()` trước dòng `onPause()` của `GLSurfaceView` tại `GLRetroView.kt:348`), sau đó đẩy việc ghi tệp sang job nền `Dispatchers.IO` ghi vào `File(sramFile.parentFile, sramFile.name + ".tmp")` → `fos.fd.sync()` → `renameTo(sramFile)`. Trong `RetroActivity.onDestroy()` (`:642-647`), chờ job ghi SRAM đang chạy tối đa `1_500 ms` rồi mới `Process.killProcess`.
  3. Bọc `std::lock_guard<std::mutex> lock(coreLock)` hoặc đọc bản sao an toàn khi truy cập biến lõi từ UI thread.

- **PR 4 (`MonikaApp`, `FeedRepository`, `AdBlock`, `WebView`)** — *Ai làm: DeepSeek*:
  1. Thêm `AppGraph.trimMemory(level: Int)` gọi từ `MonikaApp.onTrimMemory(level)` (cả `github` & `play`) và gọi chủ động với `TRIM_MEMORY_UI_HIDDEN` ngay tại `RetroActivity.start` (`RetroActivity.kt:676`) / nơi mở `:game`, `:midlet`:
     - Khi `level >= TRIM_MEMORY_UI_HIDDEN (20)` hoặc `level >= TRIM_MEMORY_RUNNING_LOW (10)`: `coil.Coil.imageLoader(app).memoryCache?.clear()`, `feed.trimMemory()`, `adblock.trimMemory()`.
  2. `FeedRepository.kt:38, 103`: thay `mutableMapOf` bằng `android.util.LruCache<String, Post>(60)` (thread-safe); `trimMemory()` đặt `pagesCache = null`, giữ `lists` tối đa 3 khóa và cắt `contentHtml = ""` cho các mục trong `cache` (mở lại bài sẽ tải lại hoặc lấy từ `lists`).
  3. `AdBlock.kt:21-22`: thêm `fun trimMemory()` gán `hashes = LongArray(0); engine = FilterEngine()` (lần sau mở `InAppBrowserActivity`, `ensure()` nạp lại từ `domains.bin`/`rules.txt` trên đĩa mất vài chục ms, không tốn mạng). Chỉ gọi `initJ2me()` trong tiến trình `:midlet` (kiểm tra trước xem tiến trình chính có chỗ nào đọc `J2meRuntime` lúc liệt kê thư viện không — nếu chỉ dùng lúc chạy midlet thì bỏ khỏi `app:chính`).
  4. `WebGameActivity.kt`: giữ biến trường `private var webView: WebView? = null`; trong `onPause()` gọi `webView?.onPause(); webView?.pauseTimers()`, `onResume()` gọi `webView?.onResume(); webView?.resumeTimers()`, `onDestroy()` gỡ khỏi parent, `stopLoading()`, `loadUrl("about:blank")`, `destroy()`, gán `null`. Bổ sung tương tự `web?.onPause()` / `web?.onResume()` cho `InAppBrowserActivity.kt:395`.

---

### 10. Cách đo trước/sau & bộ test bắt buộc

- **Đo cục bộ bằng `adb` trên máy thật / emulator 4 GB RAM** *(DeepSeek/Đội Lỗi chạy ở PR 2 & PR 4; hiện `[CHƯA KIỂM]` trên máy thật)*:
  1. **Đo `:game` (`PR 2`)**: Mở cùng 1 ROM `.nds` ≥ 256 MB bằng `melondsds`, chờ vào màn hình chính 30 giây, chụp `adb shell dumpsys meminfo vn.aow.monika:game | grep -E "Native Heap|TOTAL PSS"`. **Kỳ vọng**: `Native Heap` và `TOTAL PSS` sau PR 2 giảm xấp xỉ đúng kích thước tệp `.nds` (giảm `≥ 200 MB` với ROM 256 MB).
  2. **Đo `app:chính` (`PR 4`)**: Cuộn 30 bài có ảnh trên Trang chủ + mở 5 bài chi tiết + mở rồi đóng `InAppBrowserActivity` và `WebGameActivity`; chụp `dumpsys meminfo vn.aow.monika`, sau đó bấm Home và chạy `adb shell am send-trim-memory vn.aow.monika UI_HIDDEN`, chụp lại sau 3 giây. **Kỳ vọng**: `TOTAL PSS` của `vn.aow.monika` giảm `≥ 40 MB`, số tiến trình `sandboxed_process` về `0` sau khi đóng WebView.
- **Đo sản phẩm sau phát hành (Đội Lỗi kiểm sau 2 bản release)**:
  - Trong `crash-insight` mới: chữ ký `3305de8b` (`lowmem|net`) và `93057733` (`lowmem|pack:sevenzip`) về **0** cho `versionCode` mới; lượt `363ea01f` giảm **> 85%** (chỉ còn ca foreground/visible thật); nhóm `melondsds` `killed`/`anr` (`#306`, `#318`) trên `versionCode` mới giảm **> 60%**.
- **Bộ test tự động bắt buộc (chạy qua `./gradlew testDebugUnitTest`)**:
  - `DiagnosticsLowMemFilterTest` (`PR 1`): kiểm `shouldReportLowMem` với `IMPORTANCE_CACHED` (bỏ), `IMPORTANCE_FOREGROUND` (giữ), `IMPORTANCE_CACHED` nhưng có `deathSession` hợp lệ (giữ), `sandboxed_process0` (bỏ); kiểm `collectProcessDeaths` có crumb `"SocketTimeoutException"` + `trace` rỗng vẫn ra `component = "app:chính"`, không sinh `3305de8b`.
  - Test C++/JNI hoặc kiểm đọc tệp lỗi (`PR 2`): đường dẫn không tồn tại ném ngoại lệ sạch (không `bad_alloc` tràn `size_t`), nạp ROM `need_fullpath = false` trả `retro_load_game == true/false` đều giải phóng buffer.
  - `RetroSramSaveTest` (`PR 3`): mô phỏng `GLThread` bị treo > 1 giây, gọi `serializeSRAMOrNull(200)` trả `null` trong `< 350 ms` không khóa luồng gọi; kiểm ghi `.tmp` → `renameTo` giữ nguyên tệp `.srm` cũ nếu luồng ghi lỗi giữa chừng.
  - `AppTrimMemoryTest` (`PR 4`, Robolectric): nạp ảnh vào Coil `MemoryCache` + 70 bài vào `FeedRepository` + nạp domain vào `AdBlock`, gọi `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` → `memoryCache.size == 0`, `FeedRepository` chỉ giữ `≤ 60` mục đã rỗng `contentHtml`, `AdBlock.ready == false` và `ensure()` nạp lại từ đĩa thành công không gọi mạng.
