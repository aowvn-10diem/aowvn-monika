## V205 — Sửa crash native & ANR còn tái diễn (ngoài nhóm hết RAM V202)

- Người làm: Argon (đặc tả) → Đội Lỗi / DeepSeek (thi công)
- Thiết kế gốc (nếu có): `docs/loi/TON-0811.md`, artifact `crash-insight` run `42502867747`, tiếp nối `docs/dac-ta/V202-het-ram-melonds.md`
- Trạng thái: Nháp

### 1. Mục tiêu
Chấm dứt các vụ văng game bất ngờ (`SIGSEGV`/`SIGABRT`) và treo màn hình (ANR) còn tái diễn trên `ppsspp`, `citra`/`azahar`, `melondsds` và `desmume` khi xoay màn hình, đổi cài đặt trong game, khóa/mở máy hoặc thoát game 3DS; đồng thời khôi phục khả năng giải mã stack native/R8 cho Đội Lỗi.

### 2. Ngoài phạm vi
- Nhóm hết RAM nền (`363ea01f`, `3305de8b`), giải phóng đệm ROM `need_fullpath=false` và timeout `serializeSRAM` (đã thuộc `V202`, PR `#551`).
- Thay đổi phiên bản mã nguồn thượng nguồn của `libazahar.so` hoặc lõi Libretro.

### 3. Tiêu chí đạt (kèm bằng chứng gốc & cách đo trước/sau)
- [ ] **Khóa luồng `:libretrodroid` (trị `#316`, `#372`, `#520`/`cf160d95`):** Bảo vệ `Environment::variables` (`libretrodroid/src/main/cpp/environment.cpp:76-127, 436-456`) bằng `std::mutex` chống Use-After-Free khi UI Thread (`RetroActivity.kt:238, 343, 445, 468`) ghi đè lúc `GLThread` đọc `c_str()`; giữ `coreLock` + kiểm tra `nullptr` cho `audio`/`fpsSync`/`video`/`core`/`input` trong `pause()`/`resume()`/`destroy()`/`onSurfaceChanged()` (`libretrodroid.cpp:186, 425, 443, 448-453`); gọi `getHwContextDestroy()()` trước khi tái tạo trong `onSurfaceCreated()` (`libretrodroid.cpp:189-224`); giải phóng chuỗi `cloneToCString` ở `setCheat` (`:604`) và đẩy `setControllerType` (`GLRetroView.kt:250`) qua `queueEvent`. *Test:* xoay màn hình + bật/tắt tùy chọn liên tục 50 lần khi chạy PSP/NDS và khóa/mở màn hình 20 lần không sập.
- [ ] **Gỡ chặn UI Thread trên `GLRetroView` (trị ANR `ffdfa861`, `#318`, `#529`, `#533`, `#535`, `#522`):** Đổi setter `viewport` (`GLRetroView.kt:65-69`) từ `runOnEmulationThread(true)` sang `queueEvent` bất đồng bộ; bổ sung timeout (≤ `2_000 ms`) cho `latch.await` tại `GLRetroView.kt:447` để không khóa cứng Main Thread khi `GLThread` đang biên dịch shader hoặc nạp state. *Test:* unit test kiểm tra `runOnEmulationThread` trả fallback khi `GLThread` bị giữ quá hạn.
- [ ] **Ổn định vòng đời `AzaharActivity` & `.cia` (trị `#301`, `#458`, `#404`, `#336`, `#308`):** Không truyền thẳng tệp `.cia` (`monika-config.json:209`) vào `NativeLibrary.run` (`AzaharActivity.kt:306`) mà dẫn người chơi qua luồng cài đặt `CiaInstallWorker`; tại `AzaharActivity.kt:355-356`, `join(1500)` luồng `NativeEmulation` sau `stopEmulation()` trước khi `killProcess` để tránh cắt cụt Disk Shader Cache (`AzaharConfig.kt:24`), kèm cơ chế xóa tệp shader cache hỏng nếu phiên trước sập trước `first-frame`; đặt `System/is_new_3ds = "0"` mặc định cho máy `EmuTier.LITE` (`AzaharConfig.kt:29`). *Test:* mở thử tệp `.cia` hiện thông báo cài đặt (0 lượt `core-error`); thoát/mở lại game 3DS 10 lần liên tiếp không sập shader cache.
- [ ] **Hạ cấu hình rủi ro VRAM & lọc nhiễu chứng thư trong thống kê lỗi (trị `#374`, `#369`):** Bổ sung `ppsspp_vertex_cache = disabled` và ép `ppsspp_internal_resolution = 480x272` trên `lite` (`monika-config.json:962-969`); hạ `desmume_internal_resolution` mặc định về `256x192` (`monika-config.json:825-835`). Trong `scripts/crash-insight.py` và `scripts/crash-ton-table.py`, tách riêng báo cáo mang chứng thư khác bản phát hành chính thức (như 75/78 lượt `d529a7fd` run `42502867747`) để đo đúng tỷ lệ lỗi trước/sau.
- [ ] **Đóng gói ký hiệu giải mã (`TON-0811.md:178`):** Thêm `-Wl,--build-id=sha1` vào `libretrodroid/src/main/cpp/CMakeLists.txt:109` và lưu trữ `mapping.txt` + `libretrodroid.so` chưa strip ở mỗi bản phát hành để `scripts/crash-reports.sh` giải mã 100% khung hàm Nhóm B & G.
- [ ] **Đo lường trước/sau:** So sánh `crash-insight` sau 7 ngày phát hành (chỉ tính chứng thư chính thức): lượt `ppsspp/native` (`cf160d95`), `melondsds/anr` (`ffdfa861`) và `citra/core-error` giảm ≥ 80% trên phiên bản mới.

### 4. Bản Play
`có` (áp dụng chung cho `:libretrodroid`, `AzaharActivity`, `CMakeLists.txt` và `monika-config.json` sau bước lọc Play).

### 5. Config
Cập nhật giá trị mặc định trong `config/monika-config.json`:
- Hệ `3ds`: tách xử lý đuôi `cia` sang cài đặt NAND thay vì chạy trực tiếp như ROM.
- Lõi `ppsspp`: thêm `ppsspp_vertex_cache: "disabled"`, ép `480x272` cho mức `lite`.
- Lõi `desmume`: hạ `desmume_internal_resolution` mức `full` về `"256x192"`.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | `:libretrodroid`: khóa `variablesLock` & `coreLock`, sửa `hw_context_destroy`, bỏ chặn UI ở `GLRetroView` + unit test | — | #... |
| 2 | `AzaharActivity` & `AzaharConfig`: chặn chạy thẳng `.cia`, chờ luồng thoát trước `killProcess`, hạ `is_new_3ds` trên `LITE` | — | #... |
| 3 | `monika-config.json` (`ppsspp`, `desmume`) + tách lọc chứng thư trong `crash-insight.py` / `crash-ton-table.py` | — | #... |
| 4 | `CMakeLists.txt` (`--build-id=sha1`) + bước lưu trữ gói `mapping.txt` & unstripped `.so` khi phát hành | 1 | #... |

### 7. Rủi ro
- Không đụng định dạng tệp save (`.srm`, `.state`, NAND 3DS); việc chờ `NativeEmulation.join(1500)` trước `killProcess` giúp **bảo vệ** dữ liệu save NAND và shader cache đang ghi dở.
- Không phát sinh thêm bất kỳ lượt gọi mạng hay Worker nào.
