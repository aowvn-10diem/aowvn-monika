# `melondsds` / `core-error` mã 1: vì sao `retro_load_game()` trả false — 20/11/2026

Issue #567 (21 lượt, 6 báo cáo: `8441a36` ×6, `69cb6a9` ×5, `dc9fb2e` ×4, `33649a3` ×3, `5a48ac5` ×2,
`f75b9bf` ×1 — đều trong cửa sổ 09/11). Tài liệu này trả lời câu hỏi hẹp mà các lượt trước để mở: **lõi melonDS DS
trả `false` trong những trường hợp nào**, và app sửa được gì. Lượt #686 (mã lỗi đọc được) và #646 (`shared_font.bin`)
không làm lại ở đây.

## 1. Kết luận trước

| Câu hỏi | Trả lời | Bằng chứng |
|---|---|---|
| `retro_load_game() == false` của `melondsds` là lỗi gì? | **Chỉ** lỗi ở **tệp game** (hoặc thư mục hệ thống không dùng được). Không phải thiếu BIOS, không phải thiếu NAND DSi. | `core.cpp:619-726`, `console.cpp:324-483` |
| Thiếu BIOS/firmware/DSi NAND có ra mã 1 không? | **Không.** Mọi ngoại lệ BIOS/DSi đều là `config_exception` → lõi dựng **màn hình lỗi riêng** và trả `true`. | `exceptions.hpp:68-138`, `core.cpp:701-705`, `core.cpp:420-437` |
| App có chuẩn bị đủ thư mục hệ thống không? | Có: `systemDirectory` luôn là `filesDir/system` (tạo thư mục nếu thiếu) ⇒ lõi không rơi vào nhánh "không có thư mục hệ thống". | `RetroActivity.kt:368`, `CoreManager.kt:34`, `environment.cpp:320-323` |
| Sửa được trong app? | **Được, một phần**: 7 điều kiện làm lõi trả `false` đọc được từ header + kích thước tệp ⇒ kiểm trước, báo đúng tệp sai ở đâu thay vì "mã lỗi 1". | `NdsContent.kt`, `RetroActivity.kt:329-345` |
| Còn lại gì chưa biết? | Nhánh `environment_exception` (thư mục hệ thống không ghi được) và `std::exception` (`bad_alloc` khi ROM lớn, máy ít RAM) — app **không** phát hiện trước được. | mục 5 |

## 2. Nguồn: `JesseTG/melonds-ds` tag `v1.4.0` (`f394adba`), melonDS `JesseTG/melonDS@jtg/fix-uninitialized-opengl`

`retro_load_game` → `CoreState::LoadGame` (`libretro.cpp:96`, `core.cpp:619`). Hàm chỉ trả `false` ở 4 nhánh
`catch` cuối (`core.cpp:706-726`); nhánh `config_exception` **không** trả `false` mà dựng màn hình lỗi rồi trả
`true` (`core.cpp:701-705` → `InitErrorScreen` `core.cpp:420-437`).

| Điều kiện trả `false` | Mã nguồn | Kiểm trước được? |
|---|---|---|
| Tệp game là `.sav`/`.srm` (mở nhầm tệp save) | `core.cpp:926-928` (`content_exception`) | **có** |
| Tệp rỗng 0 byte | `core.cpp:930-932` | **có** |
| `retro_game_info.data == nullptr` (lỗi frontend) | `core.cpp:934-936` | không (app luôn nạp bytes) |
| Nhỏ hơn `sizeof(NDSHeader)` = 4096, hoặc nhỏ hơn 4 KiB khi parse | `console.cpp:676-679`, `NDSCart.cpp:394-398` | **có** |
| Lớn hơn 512 MB | `NDSCart.cpp:400-404` | **có** |
| ARM9 entry ngoài `0x02000000..0x023BFE00` | `console.cpp:684-687` | **có** |
| CRC logo Nintendo ≠ `0xCF56` và không phải homebrew | `console.cpp:689-692` | **có** |
| `ARM9/ARM7ROMOffset < 0x200` hoặc `offset + size` vượt kích thước đã làm tròn | `NDSCart.cpp:365-379` | **có** |
| `get_system_subdir_path()` lỗi ⇒ `environment_exception` | `console.cpp:1189-1192` (`CustomizeFirmware`, dùng cả ở chế độ DS) | không (I/O lúc chạy) |
| `std::bad_alloc` khi cấp phát ROM (`CopyToUnique` bản sao đầy đủ) | `NDSCart.cpp:381-406` | không |

Các ngoại lệ **không** sinh mã 1 (đã kiểm từng cái): `encrypted_rom_exception` (`console.cpp:423`),
`dsi_no_nand_found_exception` (`:494`), `dsi_no_firmware_found_exception` (`:498`), `dsi_missing_bios_exception`
(`:518-533`), `firmware_missing_exception` (`:542`), `wrong_firmware_type_exception` (`:550`),
`dsi_region_mismatch_exception` (`:1435`), `wrong_console_mode_exception` (`:280`) — tất cả kế thừa
`bios_exception`/`config_exception` (`exceptions.hpp:68-138`) ⇒ màn hình lỗi, `retro_load_game` trả **true**.

## 3. Vì sao các giả thuyết cũ bị loại

| Giả thuyết | Kết luận | Lý do |
|---|---|---|
| Thiếu firmware/BIOS DS | loại | config đặt `melonds_sysfile_mode = builtin` (`config/monika-config.json:1137`) ⇒ lõi dùng FreeBIOS; và thiếu BIOS cũng **không** ra mã 1 (mục 2) |
| Thiếu BIOS/NAND DSi | loại | nhánh DSi ném `config_exception` ⇒ màn hình lỗi. `melonds_console_mode` không đặt ⇒ `Auto`, chỉ vào DSi khi `UnitCode == 0x03` hoặc DSiWare retail (`console.cpp:238-255`) |
| Lõi chỉ có `arm64-v8a` (máy 32-bit) | loại | sai ABI ra `op-fail` ở bước tải lõi, không phải `core-error` (`CoreManager`/`Diagnostics.op`) |
| ROM `.nds` hỏng/cắt cụt/đổi đuôi | **còn đúng, và là nhóm duy nhất còn lại** | mục 2 |

## 4. Đã sửa trong app (lượt này)

`app/src/main/java/vn/aow/monika/library/NdsContent.kt` — kiểm tệp `.nds` **trước khi** tạo `GLRetroView`, chỉ
cho lõi `melondsds`, và chỉ chặn khi chắc chắn (mọi điều kiện ở mục 2 đều làm lõi trả `false` 100%):

- tệp `.sav`/`.srm`, tệp rỗng, tệp < 4 KB, tệp > 512 MB;
- header: ARM9 entry, CRC logo (bỏ qua cho homebrew: `ARM9ROMOffset < 0x4000` hoặc game code `####`),
  `ARM9/ARM7ROMOffset < 0x200`, `offset + size` vượt kích thước đã làm tròn luỹ thừa 2.

Chặn thì màn chờ hiện đúng lý do + "Quay lại" (`RetroActivity.kt:329-345`, cùng khuôn với `DiscSet`), ghi vệt
`nds-rom` để báo cáo sau đọc được. Không đọc được tệp ⇒ **không** kết luận (để lõi tự báo), tránh chặn oan.

`CoreLoadError` (mã 1, lõi `melondsds`): câu chẩn đoán đổi thành "đã kiểm header trước khi nạp" thay vì khuyên
nhập lại game — tới được lõi nghĩa là kích thước/logo/ARM9 đều hợp lệ.

Kiểm: `NdsContentTest` (15 ca, gồm ca không chặn oan homebrew + ROM hợp lệ), `CoreLoadErrorDiagTest` (+2 ca).
`gradlew :app:testGithubDebugUnitTest --tests "vn.aow.monika.*"`: 1350 ca, 18 lỗi — **đúng 18 ca môi trường**
Windows đã biết (`DeviceScannerTest`, `RarTest`, `ImporterTest`, `LibraryDocumentsTest`, `CorePreparationCacheTest`,
`ConsoleSaveTransferTest`, `GameSaveTransferTest`), không ca nào của lượt này.

## 5. Còn chưa biết / dữ liệu cần để đóng #567

1. **Chưa đọc được `reason` của 6 báo cáo cũ** (cần `CRASH_ADMIN_TOKEN` — lượt này không đụng). Sau bản phát hành,
   báo cáo mới có thêm mã lỗi (#686) và vệt `nds-rom`/`core-error` của lượt này.
2. Nếu sau bản này vẫn có mã 1 cho `melondsds`: ứng viên còn lại là `environment_exception` khi thư mục hệ thống
   không ghi được (đầy đĩa, quyền) và `bad_alloc` với ROM lớn trên máy ít RAM — cả hai **chưa** có bằng chứng
   [CHƯA KIỂM]. Cần: mã lỗi + dung lượng ROM + RAM trống trong báo cáo (đã có ở `Diagnostics.env`).
3. Chưa kiểm chạy thật trên máy [CHƯA KIỂM]: ca chặn chỉ kiểm bằng unit test dựng header tay; nhánh chạy thật cần
   một ROM NDS hỏng để đối chiếu thông điệp trên màn chờ.

Đóng #567 sau bản phát hành: nếu báo cáo mới **không** còn `melondsds/core-error` mã 1, hoặc mọi ca mới đều rơi vào
màn chờ `nds-rom` (tức app đã nói rõ lý do), thì đủ căn cứ đóng.
