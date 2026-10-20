# V134 phần 1 — chuyển save Java, NDS, GBA

Lệnh #119/6281691420 ngày 20/10/2026: phần 1 trước Azahar và #300–#308.
Một PR, configVersion 47 (45/46 dành cho Nova), app vẫn 0.8.5/code 50.
Không triển khai 3DS/PSP/PS1 trong phần này; phần 2 và kế hoạch các hệ còn lại chưa hoàn thành.

## Cách dùng

Thư viện → menu của game → **Xuất save** / **Nhập save**. SAF tạo/chọn tài liệu, không xin quyền bộ nhớ mới. Thoát hoàn toàn màn chơi trước khi chuyển save. Chỉ Java, NDS, GBA đã nhận diện, đọc được file game mới có nút.

Xuất ZIP `<tên game>-<hệ>-<ngày>.zip`, gồm SRAM/RMS, các state Monika hiện có và manifest SHA-256 của file game + hệ/lõi/phiên bản lõi. Không có ROM, đường dẫn game, tài khoản hay báo cáo crash. Java cần mở/cài game một lần; đối chiếu MIDlet-Name và MIDlet-Vendor với manifest bản đã cài, không đoán suite từ tên JAR.

Nhập file lẻ hoặc ZIP. Gói Monika phải khớp SHA-256 ROM và hệ/lõi; state chỉ khớp cùng phiên bản lõi. Save thô ngoài không có mã nhận diện hệ/game: kiểm tên ROM/tên hiển thị, báo rõ giới hạn này và yêu cầu xác nhận đúng game trước khi nhập. Không nhận state ngoài vì không xác minh được lõi/phiên bản. Không coi tên file là bằng chứng game chắc chắn.

RMS ngoài: ZIP một thư mục suite (`data/<suite>/`, `<suite>/`, hoặc file ở gốc ZIP); header `.rsh` và đủ record `.rsr`, phiên bản MIDRMS 3.0 không mã hóa. Tên thư mục khác suite bị chặn. File `.rsr` lẻ cần kho header/record của game đã tồn tại để kiểm số record/ID; header `.rsh` lẻ chỉ nhận khi số record bằng 0. File có bản RMS khác/mã hóa bị từ chối thay vì sửa đoán. Nguồn ngoài không mang định danh MIDlet nên hộp thoại luôn cảnh báo cần cùng game/nhà phát hành.

Trước ghi: kiểm toàn gói, giới hạn số mục/byte, chặn đường dẫn tuyệt đối, traversal, symlink, tên trùng, lẫn nhiều game. Tối đa 256 mục ZIP (kể manifest), 32 MiB/file, 64 MiB tổng, dành 64 KiB cho manifest. Giữ toàn bộ save/state cũ trong `filesDir/save-backups/<UUID>.zip` trước thay đổi đầu tiên. Ghi staging cùng thư mục + atomic rename; lỗi giữa chừng khôi phục các đích đã chạm, giữ lỗi khôi phục nếu có. Chỉ xóa record RMS dư của kho vừa nhập, sau khi sao lưu; không gọi xóa game, ROM, bản save của game/lõi khác. Có nút **Lưu bản sao cũ** sau nhập để xuất backup qua SAF. Backup không tự bị dọn.

## Nguồn gốc đã đọc trước khi code

- [J2ME Loader AndroidRecordStoreManager](https://github.com/nikita36078/J2ME-Loader/blob/master/app/src/main/java/javax/microedition/rms/impl/AndroidRecordStoreManager.java), đối chiếu bản nhúng ở `j2me/.../rms/impl/{AndroidRecordStoreManager,RecordStoreImpl}.java`: `.rsh` là MIDRMS + version + tên kho/count; `.rsr` là ID/tag/length/data. Đường dẫn nhúng đối chiếu `Config`, `AppInstaller`, `AppClassLoader`: `data/<suite>` theo bản đã cài. Không sửa mã J2ME/dexlib.
- [DeSmuME mc.cpp](https://github.com/TASEmulators/desmume/blob/master/desmume/src/mc.cpp): `DESMUME_BACKUP_FOOTER_TXT`, cookie `|-DESMUME SAVE-|`, 6 uint32 LE, version 0. Kiểm text/cookie/version/written/padded/addressBytes trước cắt footer 122 byte. `.sav`/`.srm` thô không có footer.
- [mGBA libretro.c](https://github.com/libretro/mgba/blob/master/src/platform/libretro/libretro.c) và [savedata.c](https://github.com/libretro/mgba/blob/master/src/gba/savedata.c): SRAM/EEPROM/Flash và đệm FF. GBA nhận 512/8192/32768/65536/131072 byte; dung lượng trung gian đệm FF lên mức kế, không cắt dữ liệu chưa kiểm. Config có thể chỉ định `targetBytes`, chỉ cắt phần đuôi toàn FF; mặc định giữ kích thước hợp lệ. Không chuyển byte-order khi nguồn thô đã tương thích.
- Bản nhúng `libretrodroid.cpp::unserializeSRAM`: không cho input lớn hơn RAM lõi; không sửa native. Các state Monika giữ nguyên byte, không chuyển giữa lõi.

## Config và tính tương thích

`saveTransfer.systems` khai báo thư mục/đuôi/loại chuyển/kích thước/targetBytes, có mặc định cho JSON cũ. Chỉ dispatch các bộ chuyển đã biên dịch (`raw`, `desmume`, `rms`), không chạy mã từ config. Cấu hình sai đường dẫn/bộ chuyển/kích thước bị chặn khi nạp config từ xa và khi mở chuyển save.

RetroActivity và chuyển save dùng chung resolver: giữ khóa `<ROM basename>-<path.hashCode()>`, hậu tố `-melondsds` riêng như trước. Cấu hình thư mục Java phải khớp thư mục data hiện hành của J2ME Loader; không tự chuyển/đổi cấu hình engine Java. Config cũ hơn 47 rơi về bản đóng gói.

## Bằng chứng và giới hạn

Test dùng byte tự sinh: RMS header/record; SAV/SRM/DSV; footer hỏng; FF padding/kích thước; không khớp hệ/ROM/lõi/state version; ZIP xấu/quá nhiều mục; symlink; lỗi atomic move giữa chừng/rollback; xuất → nhập → khôi phục backup; hủy trước commit không ghi; giữ ROM và save game khác; config mặc định/override; hai nút Monika và ảnh fixture.

**[CHƯA KIỂM]** chơi tiếp trên điện thoại thật, haptic, save Java/JL-Mod khác revision, file thực xuất từ DeSmuME/RetroArch/mGBA/VBA/My Boy. Chỉ bộ khung raw tương thích và schema RMS/DSV nguồn gốc đã đối chiếu, không coi test giả là thử thực trên các app này. Save có container/RTC/trailer khác chưa kiểm bị từ chối, không tuyên bố hỗ trợ mọi bản VBA/My Boy/JL-Mod. Khả năng lõi dùng được state/SRAM sau nhập cần kiểm máy thật.

Root `./gradlew testDebugUnitTest --offline` đã thử: Gradle 8.14.3 tải distribution bị Connection refused trước SDK/unit. Không có local app PASS; kết quả CI phải gắn đúng head trong PR.
