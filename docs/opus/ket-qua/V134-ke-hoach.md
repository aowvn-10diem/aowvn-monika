# V134 phần 2 — chuyển save 3DS, PSP, PS1 và kế hoạch các hệ còn lại

Lệnh #119/6281691420 và PM chốt #119/6284744849 ngày 20/10. Tiếp tục sau PR #309 phần 1 đã được PM gộp; không sửa lại PR đó. Phần 2 dùng configVersion 48, thêm profile 3DS/PSP/PS1, giữ nguyên profile Java/NDS/GBA, URL, engine, app version và workflow. Không bật Azahar hay nạp lõi khi chuyển save.

## Cách dùng và phạm vi

Dùng hai nút **Xuất save / Nhập save** đã có ở menu game. Vẫn SAF chọn/ghi ZIP, không xin quyền lưu trữ mới; component và token Monika, motion FULL/LITE/OFF giữ nguyên. Phải thoát màn chơi. Khóa file OS dùng chung cả RetroActivity và AzaharActivity, không khóa nhầm tiến trình preload.

- **3DS:** đọc title ID từ header NCCH hoặc phân vùng đầu NCSD (CCI/3DS/CXI), không gọi JNI hay giải mã ROM. Citra libretro giữ `<saveDirectory>/Citra/`; Azahar dùng thư mục app `azahar` khi launcher hiện hành chọn engine đó. Chỉ kho SD `Nintendo 3DS/<ID0>/<ID1>/title/<high>/<low>/data/00000001` của title đã chọn; phải mở game một lần tạo kho, nhiều thẻ cùng title thì chặn thay vì chọn đoán. Không đụng NAND, extdata, DLC, title metadata hay thư mục game khác. Citra đang dùng đường dẫn ngoài mặc định thì chặn. Cây Citra/Azahar được nhận theo đúng title ID; bản Checkpoint dạng `<0xTitleID tên game>/<bản sao>/<nội dung>` được chuẩn hóa về cây file. Dump JKSM/Checkpoint phẳng cần đóng gói **nội dung một bản save đã giải mã** vào `<16 ký tự title ID>.zip`; không nhập toàn kho JKSV. Container DISA/DIFF và file save mã hóa không được giải mã hay ghi đoán. CIA/3DSX, extdata, nhiều archive trong một bản sao: **[CHƯA KIỂM]**, báo lỗi rõ. State Azahar chỉ các slot 00–03 theo tên title ID; libretro dùng slot Monika hiện có. State trong ZIP Monika cần đúng engine/lõi/version; revision thực lúc state được tạo vẫn [CHƯA KIỂM].
- **PSP:** đọc `PSP_GAME/PARAM.SFO` trong ISO9660 hoặc CSO v1/block 2048, đối chiếu DISC_ID. Đọc metadata tối đa 2 MiB, không giải nén ROM ra ổ đĩa; CSO/ZSO biến thể chưa hỗ trợ bị chặn. Giữ đường dẫn PPSSPP libretro hiện tại `<saveDirectory>/PSP/SAVEDATA/<DISC_ID + hậu tố>`. Nhận ZIP có PSP/SAVEDATA hoặc một/nhiều thư mục thuộc cùng DISC_ID; mỗi thư mục cần PARAM.SFO với SAVEDATA_DIRECTORY khớp. Giữ byte dữ liệu PPSSPP (kể cả dữ liệu mã hóa game quản lý), không tự đổi khóa mã hóa hoặc sửa SFO. File lẻ không có đủ cây/thông tin game bị chặn. State lấy từ các slot Monika; không nhận state PPSSPP ngoài chưa xác minh phiên bản.
- **PS1:** PCSX-ReARMed/libretro thẻ thứ nhất 128 KiB. Nhận MCR/MCD/SRM đúng kích thước, magic MC và checksum header. GME DexDrive có magic `123-456-STD`, tổng 128 KiB + 3904 byte; bỏ đúng 3904 byte rồi kiểm thẻ, kể cả GME đổi đuôi. Không đệm một file thẻ thiếu byte để làm nó có vẻ hợp lệ. File ngoài vẫn cần tên khớp game và người dùng xác nhận: thẻ PS1 có thể chứa nhiều game, tên file không chứng minh nội dung. Không nhập thẻ thứ hai/shared card, single-save MCS hay container VGS/MEM chưa kiểm. ZIP Monika kèm các state của đúng lõi/version.

Thư mục/đuôi/bộ chuyển trong `saveTransfer.systems`, trường treeDirectory/nativeDirectory mới có mặc định cho JSON cũ. Không chạy bộ chuyển tùy ý do config tải về. Không đổi thư mục save libretro đang sử dụng. treeDirectory/nativeDirectory chỉ nhận layout backend đã kiểm; đường dẫn nội bộ PSP/Citra/Azahar khác bị chặn, không tự dời dữ liệu sang nơi engine không dùng. Game lớn hơn 4 GiB bị chặn; SHA-256 đọc theo luồng, không xuất ROM.

## An toàn dữ liệu

Kiểm toàn bộ gói và mã game trước ghi; caps phần 1 giữ nguyên (256 mục, 32 MiB/file, 64 MiB tổng, chừa manifest 64 KiB), thêm giới hạn độ sâu/nút cây và metadata ISO/CSO. Chặn traversal, symlink, tên trùng hoa/thường, file không thuộc game. Save cây có thể có file rỗng hợp lệ; state rỗng vẫn bị bỏ khỏi ZIP và không được nhập. Không nới kiểm file SRAM/RMS rỗng của phần 1.

Sao lưu nguyên hiện trạng save/state trước ghi bằng ZIP staging + atomic rename. Snapshot rollback giữ nguyên cả file rỗng. Khi nhập cây, xóa file save cũ không còn trong phạm vi thay thế **sau sao lưu**, không xóa ROM/game hay save của game khác. Với ZIP PSP ngoài chỉ thay các thư mục được nhập, giữ các slot/thư mục khác cùng game. ZIP Monika/backup có treeSnapshot khôi phục toàn cây game, kể cả hiện trạng chưa có save (có cảnh báo rõ trước khi trả cây về trống); không xóa các state vắng trong gói. Rollback lỗi move/delete khôi phục mọi file đã chạm; backup vẫn giữ và có thể xuất bằng SAF. Không tự dọn backup.

## Nguồn gốc đọc trước khi code

- [PPSSPP libretro](https://github.com/hrydgard/ppsspp/blob/master/libretro/libretro.cpp): RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY → memStickDirectory; [SavedataParam](https://github.com/hrydgard/ppsspp/blob/master/Core/Dialog/SavedataParam.cpp): `ms0:/PSP/SAVEDATA/`, gameName/saveName, SAVEDATA_DIRECTORY trong PARAM.SFO.
- [PPSSPP ISOFileSystem](https://github.com/hrydgard/ppsspp/blob/master/Core/FileSystems/ISOFileSystem.cpp), [BlockDevices](https://github.com/hrydgard/ppsspp/blob/master/Core/FileSystems/BlockDevices.cpp): ISO9660 và header/index CSO; chỉ triển khai metadata ISO/CSO v1 nói trên, không port engine.
- [Citra libretro](https://github.com/libretro/citra/blob/master/src/citra_libretro/citra_libretro.cpp): `citra_use_libretro_save_path=LibRetro Default`, save dir + `Citra/`.
- [Azahar SD save source](https://github.com/azahar-emu/azahar/blob/master/src/core/file_sys/archive_source_sd_savedata.cpp): ID0/ID1/title/high/low/data/00000001; [savestate](https://github.com/azahar-emu/azahar/blob/master/src/core/savestate.cpp): `<TitleID>.<slot>.cst`. Bản backup Checkpoint/JKSM thực chưa lấy/kiểm; chỉ nhận layout file đã giải mã mô tả ở trên, không tuyên bố mọi revision tương thích.
- [PCSX-ReARMed sio.c](https://github.com/libretro/pcsx_rearmed/blob/master/libpcsxcore/sio.c): MCD_SIZE và header GME 3904 byte; [frontend/libretro.c](https://github.com/libretro/pcsx_rearmed/blob/master/frontend/libretro.c): SAVE_RAM lấy Mcd1Data với MCD_SIZE, không gộp hai thẻ.

## Kiểm chứng

Test tự sinh ISO/CSO/SFO, NCSD/NCCH và thẻ MCR/GME; không commit save/ROM thật. Có positive/negative metadata, truncation/kích thước/header/SFO sai, PSP sai DISC_ID, 3DS sai title/cây trộn/container mã hóa, symlink/nhiều thẻ mơ hồ, backup rỗng và xuất → nhập → khôi phục cây PSP (file rỗng, file cũ thừa, game khác giữ nguyên). ConfigTest kiểm profile/mặc định/override, cache 47 rơi về 48 và config cây không an toàn bị chặn.

Root `./gradlew testDebugUnitTest --offline` đã thử: tải Gradle 8.14.3 distribution bị Connection refused trước SDK/unit, không local app PASS. CI/review phải gắn đúng head mới; không chuyển chứng cứ/phê duyệt phần 1.

**[CHƯA KIỂM]** save thật Citra/Azahar/Checkpoint/JKSM/PPSSPP/DexDrive và chơi tiếp/SAF trên điện thoại. Theo PM6284744849, bằng chứng này là cổng lên **stable**: kiểm bản thử, ghi `docs/TEST-THUC-TE.md`, mất save là lỗi chặn. Đây không phải kết luận đã ổn định, không cho phép Sol dispatch máy thật, phát hành, bật engine hay tự gộp.

## Các hệ còn lại — chỉ kế hoạch, chưa triển khai

| Nhóm hiện có trong config | Việc cần đối chiếu trước khi mở phạm vi sau |
|---|---|
| GBC, SNES, NES, Genesis, SMS, GG, PCE | SRAM/EEPROM/RTC và header/endianness theo từng core; slot state/version, đuôi nhận và fixture roundtrip riêng. Không suy từ GBA. |
| N64, Dreamcast | Mempak/EEPROM/Flash, VMU nhiều cổng; map game/disc và khối shared cần UI chọn, không ghi cả thẻ dùng chung tự động. |
| Lynx, Atari2600/7800, WonderSwan, NeoGeoPocket | Xác minh core có persistence nào, kích thước và đường dẫn; hệ không có save phải báo không có dữ liệu. |
| RPG2k/EasyRPG, RGSS | Save theo thư mục game/engine/version, không chạm script/RTP/asset; thử Ruby Marshal/an toàn trước khi hỗ trợ chuyển giữa engine. |
| RPGMV, Tyrano, Kirikiri, Ren'Py, ONScripter | Nguồn save trong app/WebView/localStorage hoặc thư mục engine, định danh origin/game; không dùng đường dẫn đoán và không chạy script từ save. |
| Flash | SharedObject theo origin/game; cần dump có manifest và kiểm quota, không xuất cookie/tài khoản WebView. |
| APK | Sandbox UID của ứng dụng khác: không truy cập riêng tư/không xin quyền rộng; chỉ nhận xuất chính thức của game nếu có. |
| Symbian | Ổ đĩa/UID và cấu trúc EKA2L1; chỉ hỗ trợ sau đối chiếu đường dẫn/version và kiểm ảnh hưởng game khác. |

Mỗi nhóm cần lệnh giao việc thực, schema config có mặc định, fixture an toàn và gate review/CI riêng. Bảng này không khởi tạo việc/nhánh/PR/lượt chạy mới.
