# `core-error` đọc được: mã lỗi · lõi · hệ máy · bước — 19/11/2026

Nhóm `core-error` là "lõi trả mã lỗi nhưng tiến trình còn sống". Trước bản này báo cáo chỉ có một câu
`lõi báo lỗi mã N` (`RetroActivity` gọi `Diagnostics.recordCoreFailure("lõi báo lỗi mã $code", "error code=$code core=$coreId")`),
nên không ai trả lời được: **thiếu BIOS/firmware, ROM hỏng, hay sai hệ máy?** Tài liệu này ghi lại bảng mã,
nguồn của từng kết luận, và những gì app còn **không** biết.

## 1. Năm mã, nghĩa và nơi sinh (nguồn: `libretrodroid/src/main/cpp/errorcodes.h`)

| Mã | Hằng số | Nghĩa | Sinh ở | Trước khung hình đầu? |
|---:|---|---|---|---|
| `0` | `ERROR_LOAD_LIBRARY` | không `dlopen` được `.so` của lõi | `create()` lỗi (`libretrodroidjni.cpp`) | **có** |
| `1` | `ERROR_LOAD_GAME` | `retro_load_game()` trả `false` | `libretrodroid.cpp` → jni | **có** |
| `2` | `ERROR_GL_NOT_COMPATIBLE` | máy không có OpenGL ES 3 mà lõi cần | `libretrodroid.cpp` khi lõi gọi `SET_HW_RENDER` | **có** |
| `3` | `ERROR_SERIALIZATION` | lỗi `saveState`/`loadState` | `libretrodroidjni.cpp` | không (sau khung đầu) |
| `4` | `ERROR_CHEAT` | lỗi mã cheat | `libretrodroidjni.cpp` | không |
| `-1` | `ERROR_GENERIC` | ngoại lệ khác trong `initializeCore` | `GLRetroView.catchExceptions` | **có** |

`onCoreFailure` thoát ngay khi `failureHandled || loadingGone` (`RetroActivity.kt:193`), nên báo cáo `core-error`
**chỉ** sinh trước khung hình đầu — mã `3`/`4` thực tế không vào nhóm này.

## 2. BIOS/Firmware: mặc định **không** phải nguyên nhân

Đây là chỗ dễ đoán sai nhất. Nguồn:

| Lõi | Kết luận | Nguồn |
|---|---|---|
| `melondsds` / `melonds` | `bios7.bin`, `bios9.bin`, `firmware.bin` là **tuỳ chọn**; lõi có BIOS/Firmware thay thế sẵn. Chỉ chế độ DSi mới **bắt buộc** `dsi_bios7.bin`/`dsi_bios9.bin`/`dsi_firmware.bin` | docs.libretro.com/library/melonds_ds (bảng BIOS: "Optional", "Required in DSi mode") + README melonDS DS |
| `pcsx_rearmed` | không có BIOS thật thì lõi dùng **HLE BIOS** | docs.libretro.com/library/pcsx_rearmed ("will default to a High-Level Emulation BIOS") |
| `mgba` | chạy được khi không có `gba_bios.bin` | docs.libretro.com/library/mgba |
| `desmume` | BIOS tuỳ chọn, chỉ đọc khi bật "Use External BIOS/Firmware (restart)" | docs.libretro.com/library/desmume |

Vì vậy thông điệp mới **loại bỏ** giả thuyết BIOS khi lõi đang ở mặc định (`melonds_sysfile_mode = builtin`),
và chỉ nêu thiếu BIOS khi người chơi đã đặt `external` mà thư mục hệ thống không có tệp — kèm câu "chưa chắc là
nguyên nhân duy nhất". App không đọc log của lõi nên **không** khẳng định được quan hệ nhân–quả
[CHƯA KIỂM] cho từng lõi.

## 3. Đã gắn gì vào báo cáo và màn hình chờ

`CoreLoadError.diagnose(Facts)` trả `Diagnosis` gồm `text` (màn chờ), `short` (toast), `summary` (vệt sự kiện,
≤200 ký tự) và `report` (khối nhiều dòng vào `Report.detail`):

```
mã lỗi: 1 (ERROR_LOAD_GAME): retro_load_game() trả false — lõi không nạp được tệp game
lõi: melondsds (đã có gói lõi trên máy)
hệ: Nintendo DS (nds) — suy từ đuôi .nds
bước: core-load-game — đang nạp game (retro_load_game)
ROM: game.nds · 134.0 MB
đuôi lõi melondsds nhận: .nds
nguồn BIOS/Firmware đang chọn: Có sẵn trong lõi (builtin)
nguyên nhân đọc được: chưa rõ nguyên nhân — tệp game có thể hỏng, bị cắt cụt hoặc đổi đuôi từ tệp khác
```

- Hệ máy suy từ **đuôi ROM** theo `config.systems[].extensions` (cùng luật `PrefetchPlanner`), không tin nhãn trong Intent.
- "Bước" lấy từ `StartupProgress.stage()` — thêm accessor, không đổi luồng chạy.
- Thiếu BIOS: đọc tên tệp trong `system/` **và** `system/melonDS DS/` (README melonDS DS) để không báo thiếu oan.
  Không đọc được thư mục hệ thống thì **bỏ** phần BIOS và ghi rõ vào báo cáo ("không đọc được thư mục hệ thống") —
  không kết luận thiếu, cũng không lập báo cáo riêng.
- Không đưa đường dẫn tuyệt đối vào báo cáo; `Diagnostics.scrub` vẫn chạy như cũ.

## 4. Còn chưa biết

- Quan hệ nhân–quả giữa "đặt `external` mà thiếu tệp" và `retro_load_game()` trả `false`: **chưa có log của lõi**.
- `desmume/op-fail` (#368) đi qua `Diagnostics.op`, **không** qua `recordCoreFailure` — PR này không đổi đường đó.
- Muốn lưu mã lỗi thành **trường riêng** trong JSON báo cáo (thay vì nằm trong `detail`) thì phải sửa
  `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt` — đang có PR #683/#684 mở nên lượt này chỉ để xuất.
