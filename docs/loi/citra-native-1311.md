# Nhóm `citra / native` — chẩn đoán (13/11/2026)

Số dòng dẫn chiếu cập nhật theo `main` `f75b2e6d`.

Phạm vi: các issue `crash-moi` đang mở có **lõi `citra` + loại `native`**, tức lõi 3DS chạy qua **`:libretrodroid`**, không phải engine Azahar.
Người lập: phiên **Lỗi 2**. Lượt này **chỉ chẩn đoán, không sửa mã**. Mọi kết luận chưa có khung đã giải mã đều ghi **[CHƯA KIỂM]**.

## 1. Số liệu (danh sách `crash-moi` ngày 13/11, 75 issue mở)

| Issue | Chữ ký (12 hex) | Loại | Lượt | Thiết bị | Ghi chú |
|---|---|---|---:|---:|---|
| [#458](https://github.com/aowvn-10diem/aowvn-monika/issues/458) | `6476cb9d47e9` | native | 29 | 6 | lớn nhất |
| [#404](https://github.com/aowvn-10diem/aowvn-monika/issues/404) | `a47d68ae82bc` | native | 3 | 2 | |
| **Tổng nhóm** | | | **32** | | 2 issue |

Đối chiếu toàn bộ lõi 3DS (`citra`): 9 issue / **121 lượt** — `core-error` 57 (#301 56, #610 1), `native` 32 (#458, #404), `killed` 18 (#607), `anr` 13 (#336 9, #460 2, #328 2), `unknown` 1 (#373).
Nhóm `native` **không** phải nhóm lớn nhất của 3DS; nó đứng sau `core-error`. Nhưng `core-error` và `killed`/`anr` đã có PR riêng (#600/#611, #617), còn `native` thì chưa.

Giới hạn: thân issue chỉ có **hash chữ ký**, không có khung, build_id hay tên `.so`; phiên này không có `CRASH_ADMIN_TOKEN` nên **không** đọc được báo cáo gốc. **[CHƯA KIỂM]**

## 2. Khác biệt cấu hình so với `ppsspp` (cùng chạy qua `:libretrodroid`)

| Điểm | `ppsspp` | `citra` | Hệ quả |
|---|---|---|---|
| Nguồn lõi | packs release `cores-ppsspp-1`, `sha256` từng ABI | `https://buildbot.libretro.com/nightly/android/latest/{abi}/citra_libretro_android.so.zip`, `version = "1"` | **không ghim bản, không kiểm hash** |
| Symbols | `symbols-ppsspp-{abi}.zip` (RelWithDebInfo, `--build-id=sha1`) | **không có** | **không retrace được** nhóm này |
| ABI | arm64-v8a + armeabi-v7a | **chỉ arm64-v8a** | máy 32-bit bị `supportsAbi()` chặn (`CoreManager.kt:44-51`) → chỉ báo lỗi, không crash |
| `systemFiles` | buildbot `PPSSPP.zip` | **không khai** | `isReady()` bỏ qua kiểm sysdata (`CoreManager.kt:53-58`); core nhận `filesDir/system` (`RetroActivity.kt:326`) có thể **rỗng** |
| `options` / `perf` | `ppsspp_vertex_cache = disabled`, `perf.lite` 480x272 | **không khai** | dùng mặc định của lõi (hardware renderer, độ phân giải mặc định) |

## 3. Nghi phạm (có tệp:dòng)

### (1) Lõi citra không ghim bản + `version = "1"` — **nghi phạm chính**
- `config/monika-config.json` → `cores.citra.url` là buildbot **nightly/latest**, `version = "1"`.
- `CoreManager.kt:38` (`installedVersion`), `:45` (`wantedVersion` = `"1"`), `:56` (`isReady` so `installedVersion == wantedVersion`).
- Vì `version` luôn là `"1"`, app **không phân biệt** lõi tải hôm nào: mỗi máy có thể chạy một bản `citra_libretro_android.so` khác nhau nhưng cùng nhãn `1`, và không có `sha256` để biết bản thật.
- Hệ quả trực tiếp cho nhóm này: **không thể retrace** (không có symbols khớp BuildId) và **không thể tái hiện** (bản lõi trôi theo ngày). Đây là điểm khác ppsspp rõ nhất. **[CHƯA KIỂM]** về việc bản nightly đổi có làm tăng crash.

### (2) `.cia` lọt vào lõi libretro citra
- `config/monika-config.json` → `systems[3ds].extensions` có **`cia`**, `core = "citra"`.
- `app/src/github/java/vn/aow/monika/runner/GameLauncher.kt:48-59`: nhánh `libretro` **không** kiểm đuôi tệp — chỉ chọn lõi rồi `RetroActivity.start(...)`.
- Đường engine Azahar mới chặn `.cia` (`AzaharActivity` → `AzaharInstallActivity`, xem #611); máy **không** có engine Azahar (hoặc người chơi đặt `coreOverride`) vẫn mở `.cia` bằng `citra_libretro` — mà `.cia` là **gói cài**, không phải ROM. Kỳ vọng: lõi trả `retro_load_game = false` → `RetroException` (báo lỗi), nhưng không loại trừ được việc lõi đọc header `.cia` rồi sập. **[CHƯA KIỂM]**

### (3) Thiếu `systemFiles` cho citra
- `cores.citra` không khai `systemFiles`; `CoreManager.kt:57` coi lõi "ready" dù `filesDir/system` chưa có `sysdata` của Citra. Nhiều game cần shared font/khoá title; thiếu thường ra `core-error` (xem #301), nhưng đường đọc tệp hệ thống của lõi là điểm có thể sập native. **[CHƯA KIỂM]**

### (4) Chưa hạ cấu hình đồ hoạ cho citra
- `ppsspp` đã hạ (`ppsspp_vertex_cache = disabled`, `perf.lite`), `citra` **không** có `options`/`perf` ⇒ chạy mặc định của lõi. Cùng lớp rủi ro driver GPU (SIGSEGV trong `libcitra_libretro_android.so`, 3 khung đầu nằm trong thư viện) mà #601 đang xử lý cho ppsspp/desmume. **[CHƯA KIỂM]**

### (5) Nhãn thành phần gộp nhầm `engine:azahar` — **lỗi rõ ràng, đề xuất PR riêng**
- `app/src/main/java/vn/aow/monika/diag/Components.kt:25-26` khớp nhánh Azahar **trước** luật libretro ở `:33`, và `has` (`:47`) so khớp chuỗi con. **Dẫn chiếu cũ, nội dung đã đổi từ `f75b2e6d`**: bản đọc khi viết là `:20` / `:27` / `:37`; nay nhánh Azahar có thêm chốt `!libretro` và không còn khoá `"citra"` trong `has(...)`.
- Vệt phiên của lõi libretro có chuỗi `citra` (`begin: libretro · citra · Nintendo 3DS`, tên tệp `citra_libretro_android.so`), nên **mọi** crash của lõi citra libretro bị gắn `engine:azahar` — thấy đúng như vậy ở #610 (`Thành phần: engine:azahar`, lõi `citra`).
- Hệ quả: nhóm `native` này **có thể bị đếm thiếu**, và ngược lại báo cáo của engine Azahar có thể bị lẫn. `ComponentsTest` hiện **không** có ca nào cho `citra`/`azahar`/`libretro` (chỉ renpy/rgss/kirikiri/pack).
- Đề xuất (ngoài lượt này): đưa luật `session?.kind == "libretro"` lên trước, hoặc chỉ khớp `"azahar"` khi `session?.kind != "libretro"`, kèm 2 ca test (`begin: libretro · citra …` → `engine:libretro:citra`; vệt Azahar → `engine:azahar`).

## 4. Cách kiểm (đề xuất, theo thứ tự)

1. **Retrace trước** (rẻ nhất, quyết định mọi thứ): hiện **không** có symbols cho citra. Cần mirror lõi citra vào `aowvn-monika-packs` như ppsspp (dựng từ nguồn ghim, `RelWithDebInfo`, `-Wl,--build-id=sha1`, đóng `symbols-citra-{abi}.zip`) rồi `scripts/crash-reports.sh <id> --symbols-dir … --symbolizer …`. Không có bước này thì mọi kết luận chỉ là đọc mã. **[CHƯA KIỂM]**
2. **A/B `.cia`**: tạm bỏ `cia` khỏi `systems[3ds].extensions` (hoặc chặn `.cia` ở nhánh `libretro` của `GameLauncher`) trong một bản thử → so số lượt `citra/native` sau 2 bản. Nếu giảm mạnh ⇒ nghi phạm (2) đúng.
3. **A/B cấu hình**: thêm `cores.citra.perf.lite` (hạ độ phân giải) + `options` hạ hardware shader trên bản thử → so số lượt. Cần PM duyệt vì đụng `config/` (tăng `configVersion` + đồng bộ Cloudflare).
4. **A/B `systemFiles`**: khai `systemFiles` cho citra (bộ sysdata chuẩn) → xem `core-error`/`native` có đổi.
5. **Sửa nhãn** (mục 3.5) trước khi đo lại, nếu không số liệu sẽ tiếp tục bị gộp nhầm.

## 5. Kết luận

- Nhóm `citra/native` = **2 issue / 32 lượt**; chưa có bằng chứng để chỉ ra một nguyên nhân.
- Nghi phạm chính là **lõi citra không ghim bản và không có symbols** (khác hẳn ppsspp) — nó chặn cả retrace lẫn tái hiện.
- Hai nghi phạm có thể kiểm bằng A/B ngay khi có bản thử: `.cia` lọt vào lõi libretro, và cấu hình đồ hoạ chưa hạ.
- Lỗi rõ ràng phát hiện trong lượt này: **`Components.kt` gắn nhãn `engine:azahar` cho crash của lõi citra libretro** → đề xuất PR riêng (kèm test) trước khi đo lại nhóm.

Tất cả kết luận nguyên nhân ở trên: **[CHƯA KIỂM]** — cần khung đã giải mã hoặc bản thử A/B.
