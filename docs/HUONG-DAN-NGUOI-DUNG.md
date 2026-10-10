# Hướng dẫn sử dụng AowVN Monika

Hướng dẫn này mô tả các thao tác có trong giao diện và các định dạng được cấu hình cho AowVN Monika. Trên trang Releases, chọn APK ở bản **không có nhãn Pre-release** nếu bạn muốn dùng bản ổn định. Tên nút có thể khác ở phiên bản cũ hơn; nếu khác, làm theo chữ trên màn hình của bản đang cài.

> Danh sách hệ máy bên dưới lấy từ cấu hình và đường chạy trong mã. Việc nhận diện đuôi tệp không đảm bảo mọi game đều mở được. Chơi game thật, âm thanh, lưu/tải, hiệu năng và thiết bị cụ thể vẫn **[CHƯA KIỂM]**.

## Cài đặt và mở app

1. Mở [Releases của AowVN Monika](https://github.com/aowvn-10diem/aowvn-monika/releases).
2. Chọn bản phát hành ổn định mới nhất, không chọn mục có nhãn **Pre-release**, rồi tải tệp APK.
3. Mở APK và làm theo trình cài đặt Android. Nếu Android yêu cầu quyền cài ứng dụng từ nguồn này, chỉ bật quyền cho ứng dụng đang mở APK rồi quay lại trình cài đặt.
4. Mở **AowVN Monika**.

## Các khu vực chính

- **Trang chủ**: xem bài viết và game nổi bật từ aow.vn.
- **Game**: khám phá bài viết về game.
- **Tìm kiếm**: tìm bài viết.
- **Thư viện**: xem game đã thêm trên máy, tìm kiếm/lọc và mở game bằng nút **Chơi** trên ô game.
- **Tải xuống**: xem tiến trình tải và các tệp đã tải. Có thể mở từ nút **Menu** ở thanh dưới hoặc lối tắt trên Trang chủ.

Tải bài viết và tải tệp cần kết nối mạng. Thêm tệp đã có sẵn trên máy không cần tải game từ app.

## Thêm game vào Thư viện

### Chọn tệp trên máy

1. Mở **Thư viện**.
2. Mở **Menu thư viện** ở góc trên, chọn **Thêm game từ máy**.
3. Trong bộ chọn tệp Android, chọn tệp game hoặc tệp nén. Có thể chọn nhiều tệp khi game được chia thành nhiều phần.
4. Chờ tác vụ thêm/giải nén kết thúc; game đã nhận diện sẽ xuất hiện trong Thư viện.
5. Chạm **Chơi** trên ô game. Nút **⋯** hoặc giữ lâu trên ô sẽ mở menu game. Nếu app hỏi cài ứng dụng ngoài hoặc chuẩn bị một thành phần, đọc thông báo và làm theo lựa chọn trên màn hình.

ZIP, RAR và 7z được hỗ trợ để giải nén. Một số cách giải nén có thể cần tải thành phần phụ. Nếu tệp có mật khẩu, mở menu của game, chọn **Giải nén**, nhập mật khẩu lấy từ nguồn bạn tải tệp rồi chọn **Giải nén**. Với bộ nén nhiều phần, hãy thêm đủ các phần; nếu app báo đang chờ phần tiếp theo, thêm các phần còn lại.

### Quét các thư mục trên máy

Trong **Menu thư viện**, chọn **Quét cả máy** để tìm game ở các thư mục khác. Android có thể yêu cầu quyền **Truy cập mọi tệp**. Đây là lựa chọn riêng: bạn vẫn có thể thêm từng tệp qua **Thêm game từ máy** mà không bật quét toàn máy.

### Tải game từ bài viết

1. Mở bài viết có liên kết tải và dùng nút **Tải game** nếu nút đó có trong bài.
2. Theo dõi tác vụ ở **Tải xuống**.
3. Khi app báo đã tải xong, mở Thư viện. Một số liên kết mở trình duyệt thay vì tải trực tiếp trong app; sau khi tải bằng trình duyệt, dùng **Thêm game từ máy** để chọn tệp.

## Hệ máy và loại tệp được cấu hình

Các đuôi bên dưới là những đuôi đang khai báo trong cấu hình main. Đường chạy mô tả mã chọn; kết quả với từng game và thiết bị **[CHƯA KIỂM]**.

| Hệ máy | Đuôi tệp được khai báo | Đường chạy được cấu hình |
|---|---|---|
| Nintendo DS | .nds | Lõi libretro trong Monika |
| Game Boy Advance | .gba | Lõi libretro trong Monika |
| Game Boy / Color | .gb, .gbc | Lõi libretro trong Monika |
| Super Nintendo | .sfc, .smc, .swc, .fig | Lõi libretro trong Monika |
| NES / Famicom | .nes, .fds, .unf, .unif | Lõi libretro trong Monika |
| Sega Mega Drive | .md, .gen, .smd | Lõi libretro trong Monika |
| Sega Master System | .sms | Lõi libretro trong Monika |
| Sega Game Gear | .gg | Lõi libretro trong Monika |
| PC Engine | .pce, .sgx | Lõi libretro trong Monika |
| Nintendo 64 | .z64, .n64, .v64 | Lõi libretro trong Monika |
| Nintendo 3DS | .3ds, .cci, .cxi, .3dsx, .cia | Azahar khi module phù hợp sẵn có; nếu không thì chọn lõi libretro theo cấu hình |
| Sega Dreamcast | .gdi, .cdi | Lõi libretro trong Monika |
| Atari Lynx | .lnx | Lõi libretro trong Monika |
| Atari 2600 | .a26 | Lõi libretro trong Monika |
| Atari 7800 | .a78 | Lõi libretro trong Monika |
| WonderSwan | .ws, .wsc | Lõi libretro trong Monika |
| Neo Geo Pocket | .ngp, .ngc | Lõi libretro trong Monika |
| PlayStation 1 | .cue, .pbp, .chd, .m3u | Lõi libretro trong Monika |
| PSP | .iso, .cso | Lõi libretro trong Monika |
| RPG Maker 2000/2003 | .ldb | Lõi libretro trong Monika |
| Flash | .swf | Trình web Ruffle |
| RPG Maker MV/MZ | Không khai báo đuôi đơn lẻ | Trình web HTML5; nhận diện gói **[CHƯA KIỂM]** |
| TyranoScript | Không khai báo đuôi đơn lẻ | Trình web HTML5; nhận diện gói **[CHƯA KIỂM]** |
| ONScripter | Không khai báo đuôi đơn lẻ | Trình web ONSyuri; nhận diện gói **[CHƯA KIỂM]** |
| Java (J2ME) | .jar, .jad | J2ME Loader được nhúng; bước cài/chạy thực tế **[CHƯA KIỂM]** |
| Android (APK) | .apk, .apks, .xapk, .apkm | Mở luồng cài đặt Android |
| Kirikiri (KAG) | .xp3 | Engine Kirikiri nhúng khi gói phù hợp với máy; bước chạy game thật **[CHƯA KIỂM]** |
| Ren'Py | Không khai báo đuôi đơn lẻ | Chuyển sang JoiPlay theo cấu hình; nhận diện và chạy từng gói **[CHƯA KIỂM]** |
| RPG Maker XP/VX/Ace | Không khai báo đuôi đơn lẻ | RGSS nhúng khi máy có module phù hợp, có JoiPlay dự phòng theo cấu hình; chạy thật **[CHƯA KIỂM]** |
| Symbian / N-Gage | .sis, .sisx, .ngage | Chuyển sang EKA2L1 theo cấu hình; hỗ trợ thực tế **[CHƯA KIỂM]** |

Nếu game dùng lõi libretro, lõi mặc định lấy từ cấu hình. Với hệ có nhiều lõi, vào **Cài đặt → Lõi giả lập** để chọn lõi khác. Tên tệp chỉ giúp app phân loại ban đầu, không xác nhận ROM hợp lệ hay tương thích.

Khi Azahar khả dụng, phần **Cài đặt → Nintendo 3DS** có nút **Chọn file .cia để cài**; phần mô tả trong app ghi rằng có thể cài game, bản cập nhật hoặc DLC vào bộ nhớ do Monika quản lý. Kết quả cài/chạy thực tế **[CHƯA KIỂM]**.

## Báo lỗi game

Nếu thấy mục **Báo lỗi game này** trong menu game hoặc màn chuẩn bị Kirikiri, bạn có thể gửi thông tin:

1. Chọn loại lỗi: **Không lên hình**, **Không có tiếng**, **Phím không hoạt động**, **Game bị treo** hoặc **Lỗi khác**.
2. Thêm **Mô tả thêm (tùy chọn)** nếu cần.
3. Chọn **Kèm ảnh game** nếu có ảnh. Nếu app không chụp được ảnh, màn hình báo rằng vẫn gửi được.
4. Đọc ghi chú **Kèm thông tin máy và nhật ký lỗi gần đây**, rồi chọn **Gửi báo lỗi**. Việc gửi cần kết nối mạng.

Nếu hiện **Đã gửi báo lỗi**, app xác nhận đã gửi. Nếu hiện **Gửi báo lỗi thất bại**, app báo đã lưu báo cáo và chép phần chữ để bạn gửi cho AowVN.

## Khi gặp sự cố

- **Không thấy game**: thử chọn tệp trực tiếp bằng **Thêm game từ máy**. Nếu muốn tìm thêm ở thư mục khác, dùng **Quét cả máy** và cân nhắc quyền Android yêu cầu.
- **Tệp nén chưa giải được**: xác nhận đã chọn đủ các phần; nếu cần mật khẩu, dùng mục **Giải nén** trong menu game.
- **App báo chưa nhận diện được loại game**: kiểm tra đã giải nén gói và chọn đúng tệp game. Khả năng nhận diện gói không có đuôi riêng như thư mục MV/MZ, TyranoScript, Ren'Py hoặc RGSS **[CHƯA KIỂM]**.
- **Cần engine/app ngoài**: làm theo hộp thoại của Monika; nếu được chuyển sang app khác, hoàn tất thao tác trong app đó rồi quay lại Monika. Khả năng chạy thực tế phụ thuộc gói và thiết bị **[CHƯA KIỂM]**.
