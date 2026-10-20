# Nhật ký thay đổi

Các thay đổi chính người dùng thấy được, từ v0.6.0 đến bản mới nhất. Mỗi mục liên kết tới tag phát hành; từng gạch đầu dòng dẫn tới commit làm thay đổi đó.

## v0.8.6 (Đang chuẩn bị — Bản thử)

> Chưa tạo tag hoặc phát hành. Danh sách đối chiếu main `adcbecc`, đã gồm phần save 3DS/PSP/PS1 của #312 theo [PM6292996531](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6292996531). Đây là bản thử để kiểm chuyển save, chưa xác nhận tương thích/chơi tiếp trên điện thoại. Sửa gốc lỗi Azahar dành cho bản sau theo [PM6285561724](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6285561724).

- **Mới:** Game Java có nút **Xuất save / Nhập save** trong Thư viện, chuyển kho RMS đúng bộ game qua trình chọn tệp Android; không xuất game JAR. Chưa kiểm save thật JL-Mod/J2ME Loader. ([f08d6e8](https://github.com/aowvn-10diem/aowvn-monika/commit/f08d6e8eecd983bfddad5503503aca14ab17c6ce))
- **Mới:** Nintendo DS và GBA có thể xuất ZIP save của Monika hoặc nhập save thô SAV/SRM; DS nhận DSV DeSmuME có footer hợp lệ, kể cả khi đổi đuôi. ZIP Monika đối chiếu mã game/lõi; save thô vẫn cần người chơi chọn đúng game và bản vùng. Không xác nhận mọi save từ trình giả lập khác đều tương thích. ([f08d6e8](https://github.com/aowvn-10diem/aowvn-monika/commit/f08d6e8eecd983bfddad5503503aca14ab17c6ce))
- **An toàn:** Trước khi nhập, Monika sao lưu save cũ và có nút **Lưu bản sao cũ**; hủy trước xác nhận không ghi save. Không chuyển save khi game đang mở; state rỗng được bỏ khỏi ZIP, state còn lại cần đúng lõi/phiên bản. Save thật, SAF và khôi phục trên máy chưa được kiểm. ([f08d6e8](https://github.com/aowvn-10diem/aowvn-monika/commit/f08d6e8eecd983bfddad5503503aca14ab17c6ce))
- **Mới:** Chuyển save 3DS theo title ID của đúng game trong cây Citra/Azahar; nhận bản sao đã giải mã có đủ định danh, chặn cây mơ hồ hoặc container mã hóa. PSP nhận ZIP SAVEDATA có DISC_ID/PARAM.SFO khớp game, giữ dữ liệu nguyên byte; không tự giải mã hay đổi khóa. Chưa kiểm save thật hoặc chơi tiếp trên điện thoại. ([adcbecc](https://github.com/aowvn-10diem/aowvn-monika/commit/adcbeccaa4b433c479b6ef9230cf9ba84a69b0c3))
- **Mới:** PS1 nhận thẻ nhớ thứ nhất MCR hợp lệ và GME có header đúng; không nhập thẻ thiếu, thẻ dùng chung hoặc thẻ thứ hai. Sao lưu trước khi thay dữ liệu. ([adcbecc](https://github.com/aowvn-10diem/aowvn-monika/commit/adcbeccaa4b433c479b6ef9230cf9ba84a69b0c3))
- **Sửa lỗi:** Bỏ RAM/state placeholder 0 byte khỏi ZIP xuất và backup để có thể nhập lại. Kho RMS Java cũ rỗng/hỏng bị chặn trước tạo backup hoặc ghi save, giữ nguyên dữ liệu; record payload 0 byte có header hợp lệ vẫn được giữ và khôi phục. ([adcbecc](https://github.com/aowvn-10diem/aowvn-monika/commit/adcbeccaa4b433c479b6ef9230cf9ba84a69b0c3))

### Cách thử chuyển save

1. Giữ một bản save tốt bên ngoài Monika trước khi thử. Thoát màn chơi, vào **Thư viện → menu game Java/NDS/GBA/3DS/PSP/PS1 → Xuất save**, chọn nơi giữ ZIP bằng trình chọn tệp Android. Với save cây 3DS/PSP, cần kho của đúng game đã được tạo.
2. Chọn **Nhập save**, chọn ZIP vừa xuất hoặc định dạng ngoài phù hợp. Kiểm đúng tên game/hệ/cảnh báo rồi chọn **Sao lưu và nhập**; chọn **Hủy** nếu chưa chắc. Với Java, cần đủ kho RMS đúng suite; không chọn JAR. Với 3DS/PSP, kiểm title ID/DISC_ID và cấu trúc bản sao; không chọn ROM hoặc toàn kho nhiều game. Với PS1, dùng thẻ MCR/GME hợp lệ của đúng game. State ngoài không thuộc ZIP Monika chưa được hỗ trợ.
3. Chọn **Lưu bản sao cũ** để giữ ZIP dự phòng, rồi mở game và kiểm tiến trình đã lưu. Nếu cần, thoát game và nhập ZIP dự phòng để thử khôi phục. Ghi kết quả máy/lõi/định dạng và bước tái hiện vào `docs/TEST-THUC-TE.md`; chưa có bằng chứng chơi tiếp và khôi phục trên máy thật thì không ghi đã ổn định.

> **Cần kiểm trên máy thật:** Save thật, SAF, chơi tiếp và khôi phục trên điện thoại còn chưa kiểm; CI dùng fixture tổng hợp. Nếu chuyển save lỗi hoặc mất tiến trình, dừng thử trên bản save duy nhất và giữ bản dự phòng. Chưa đủ bằng chứng máy thật để lên stable; phần save không xác nhận đã sửa lỗi khởi động Azahar.

## v0.8.5 (Đang chuẩn bị — Bản thử)

> Chưa phát hành, chưa tạo tag. Bản vá ưu tiên 3DS theo main `8a96ff3`; chưa xác nhận đã chơi được trên máy thật. V125 (#281) và vệt JNI V118-crumb chưa gộp, không thuộc bản này.

- **Sửa lỗi:** Chỉnh cách khởi động game 3DS để không nạp sớm thư viện đồ họa tùy biến, kể cả khi bật trình điều khiển riêng. ([bb22c29](https://github.com/aowvn-10diem/aowvn-monika/commit/bb22c29d14928577d9c62ed949ea2f261b321da6), [#282](https://github.com/aowvn-10diem/aowvn-monika/pull/282))

## [v0.8.4](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.8.4) (Bản thử)

> Phát hành ngày 17/10/2026 (UTC), bản thử nghiệm. Danh sách theo main `bd9ac022`; không xác nhận kết quả chơi trên máy thật.

- **Sửa lỗi:** Game không bị dọn tự động theo tuổi hoặc giới hạn bộ nhớ; chỉ dọn game khi chọn Dọn ngay trong Cài đặt → Bộ nhớ. ([84ab5b4](https://github.com/aowvn-10diem/aowvn-monika/commit/84ab5b4369187d88f84a6b5ef7848a1335f78db9))
- **Sửa lỗi:** Khi mở game 3DS với thiết lập mặc định, ứng dụng bỏ bước nạp đồ họa tùy biến có thể gây văng. ([bd9ac02](https://github.com/aowvn-10diem/aowvn-monika/commit/bd9ac022377d078fc2046ead5dcafb075eb2e723))
- **Mới:** Lỗi khi tải, nhập, mở game hoặc cài gói được ghi để hỗ trợ báo lỗi, theo lựa chọn gửi của người chơi. ([82d7007](https://github.com/aowvn-10diem/aowvn-monika/commit/82d7007772ca74f8db36bb80b0089c24a94694d3))
- Nội bộ: báo cáo treo/hết bộ nhớ chỉ gắn phiên khi bằng chứng đúng tiến trình, tránh nhầm PID được dùng lại. ([eef398c](https://github.com/aowvn-10diem/aowvn-monika/commit/eef398c8a00ad946d9a80c344aec4ad58cc1bbf5))
- Nội bộ: cấu hình Kirikiri trỏ gói 16 có mã phiên bản và ký hiệu hỗ trợ đọc báo lỗi. ([93a7e79](https://github.com/aowvn-10diem/aowvn-monika/commit/93a7e79b0e3fc24503fd298f4b316807c921ed54))
- Nội bộ: thêm mã gom chữ ký báo lỗi và định tuyến việc sửa; không xác nhận bot đã kích hoạt. ([25302b5](https://github.com/aowvn-10diem/aowvn-monika/commit/25302b5d29b4bb5602548fbe63f6121899e6a8cc))
- Nội bộ: chuẩn bị cổng kiểm chữ ký lỗi trước bản ổn định; chưa có phép đếm mọi phiên của V114. ([3fa449f](https://github.com/aowvn-10diem/aowvn-monika/commit/3fa449f6a1c7b061d77aa727a0353b57cce56930))
- Nội bộ: chặn mã mới nuốt lỗi im lặng trong các luồng người dùng. ([23ccb8e](https://github.com/aowvn-10diem/aowvn-monika/commit/23ccb8e83f6bd33271104d98dfa8496ac9defa33))

## [v0.8.3](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.8.3) (Bản thử)
<!-- bien-tap: v0.8.3 -->
> Phát hành ngày 13/10/2026 (UTC), bản thử nghiệm.

- **Sửa lỗi:** Một số máy mở game 3DS bị văng ngay khi vào game. Bản này đã chỉnh để các máy đó có thể chơi được. ([d199001](https://github.com/aowvn-10diem/aowvn-monika/commit/d199001b835eed66a15f58461ec9e387369eba86))
- **Sửa lỗi:** Chạm màn hình khi game Nintendo DS đang nạp không còn làm app treo. ([89ca662](https://github.com/aowvn-10diem/aowvn-monika/commit/89ca6624b8934faaeb753d8179a145a5f31a9713), [0027e6e](https://github.com/aowvn-10diem/aowvn-monika/commit/0027e6ec4bcda0c8102d9d0723148917913da5d4), [338921d](https://github.com/aowvn-10diem/aowvn-monika/commit/338921d16b2814fe54ae8921d4db59d742b1ef52))
- **Mới:** Game Nintendo DS có thể vào nhanh hơn ở các lần sau, vì bước chuẩn bị của lõi được lưu lại. ([89ca662](https://github.com/aowvn-10diem/aowvn-monika/commit/89ca6624b8934faaeb753d8179a145a5f31a9713), [0027e6e](https://github.com/aowvn-10diem/aowvn-monika/commit/0027e6ec4bcda0c8102d9d0723148917913da5d4), [338921d](https://github.com/aowvn-10diem/aowvn-monika/commit/338921d16b2814fe54ae8921d4db59d742b1ef52))
- **Mới:** Khi thanh tên game và menu tự ẩn, chạm vùng mép màn hình để hiện lại; mở lại menu ổn định hơn và dùng được với trợ năng. ([98375af](https://github.com/aowvn-10diem/aowvn-monika/commit/98375af9c94bd5b6f079d999a391a273e00613d9), [413bfc3](https://github.com/aowvn-10diem/aowvn-monika/commit/413bfc3a83796d6241d80923e4eb1050be7febd3))
- **Mới:** Bàn phím ảo của game Java đổi sang bộ nút mới của AowVN. ([32b0c1b](https://github.com/aowvn-10diem/aowvn-monika/commit/32b0c1bac2c1de491d87dbfe471c3f32831e2eaf))
- Nội bộ: gói Kirikiri mới ghi mã phiên bản và đính kèm tệp ký hiệu để báo lỗi sập đọc được tên hàm; chỉ có hiệu lực khi phát hành gói Kirikiri mới. ([dd752c2](https://github.com/aowvn-10diem/aowvn-monika/commit/dd752c2504d15278e871b9be55bb017b2f3929a4))
- Nội bộ: có cờ cấu hình để bật lại lớp đồ họa tùy biến của 3DS (mặc định tắt), dùng khi cần thử. ([d199001](https://github.com/aowvn-10diem/aowvn-monika/commit/d199001b835eed66a15f58461ec9e387369eba86))
- Nội bộ: sửa các bài kiểm thử giao diện và dữ liệu Ren'Py cho ổn định; bản cài của người dùng không đổi. ([408b901](https://github.com/aowvn-10diem/aowvn-monika/commit/408b901e46a5eecf802a0615c5f6a8de20d16f47), [248395a](https://github.com/aowvn-10diem/aowvn-monika/commit/248395a31e9e4f0145da73e1d16ff396d44d32f4))

## [v0.8.2](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.8.2) (Bản thử)
<!-- bien-tap: v0.8.2 -->
> Phát hành ngày 11/10/2026 (UTC), bản thử nghiệm.

- **Mới:** Khi game giả lập (gồm Nintendo DS) mở lâu, màn chờ hiện từng bước: tải lõi, giải nén, nạp game, khởi tạo, thay vì chỉ "Đang khởi động game…". ([be45e32](https://github.com/aowvn-10diem/aowvn-monika/commit/be45e327b6448c939f1bc185ffe307dc31c3a776))
- **Mới:** Sau 60 giây màn chờ hiện nút "Sao chép chẩn đoán". Game vẫn tiếp tục nạp, không tự báo lỗi hay đổi lõi. ([be45e32](https://github.com/aowvn-10diem/aowvn-monika/commit/be45e327b6448c939f1bc185ffe307dc31c3a776))
- **Sửa lỗi:** Game Kirikiri có kịch bản nằm sẵn trong tệp exe không còn bị báo "không tìm thấy tệp khởi động". ([4b672c5](https://github.com/aowvn-10diem/aowvn-monika/commit/4b672c579bffba374193b7da32557b7247884b45))
- Nội bộ: chẩn đoán Kirikiri ghi thêm thông tin về script nhúng trong exe, để hỗ trợ báo lỗi. ([4b672c5](https://github.com/aowvn-10diem/aowvn-monika/commit/4b672c579bffba374193b7da32557b7247884b45))
- Nội bộ: điểm danh hằng ngày và mốc đánh giá dùng một đồng hồ có thể thay khi kiểm thử; cách tính theo múi giờ giữ nguyên. ([04dfd6b](https://github.com/aowvn-10diem/aowvn-monika/commit/04dfd6b4f4bc3c1776e9b10054d911852c56d08f))

## [v0.8.1](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.8.1) (Bản thử — Pre-release)
<!-- bien-tap: v0.8.1 -->
> Phát hành ngày 11/10/2026 (UTC), bản thử nghiệm.

- **Mới:** Nintendo DS ở màn dọc xếp kiểu DraStic: hai màn hình game nằm trên và rộng hơn trước, bàn phím ảo ở dưới. ([b116a93](https://github.com/aowvn-10diem/aowvn-monika/commit/b116a939f14b379b258e4e7cde117c93d10083be))
- **Mới:** Lõi NDS mặc định dùng đúng tay cầm NDS có nút X và Y, thay vì tay cầm GBA. ([b116a93](https://github.com/aowvn-10diem/aowvn-monika/commit/b116a939f14b379b258e4e7cde117c93d10083be))
- **Mới:** Thanh tên game, nút menu và gợi ý như "D-pad → Cần" tự ẩn sau vài giây khi chơi giả lập (NDS, PSP, GBA), Kirikiri, RPG Maker, Ren'Py và game web. Chạm mép màn hình để hiện lại. ([b116a93](https://github.com/aowvn-10diem/aowvn-monika/commit/b116a939f14b379b258e4e7cde117c93d10083be))
- **Mới:** Game Java: nút menu chuyển xuống ô trống của bàn phím ảo, không đè lên màn hình game. Khi không có bàn phím Monika, nút nằm góc trên phải và mờ sau vài giây. ([801752e](https://github.com/aowvn-10diem/aowvn-monika/commit/801752e8cd191742e046c63f915952a6bd3be823))

## [v0.8.0](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.8.0) (Bản thử — Pre-release)
<!-- bien-tap: v0.8.0 -->
> Phát hành ngày 11/10/2026 (UTC), bản thử nghiệm.

- **Mới:** Trong Cài đặt → Màn hình, chỉnh được bố cục, tỉ lệ và khoảng cách hai màn hình Nintendo DS; áp dụng ở lần mở game tiếp theo. ([608b12a](https://github.com/aowvn-10diem/aowvn-monika/commit/608b12a9cad8950fd95816ae96bbd8a893dd2d6d))
- **Mới:** Bộ nút mới mang phong cách AowVN (viền pixel, bóng đổ cứng), có hiệu ứng khi nhấn và ba mức hiệu ứng để chọn. ([fb5edac](https://github.com/aowvn-10diem/aowvn-monika/commit/fb5edac0f1d885142839ffe05a0b43339d75522c))
- **Mới:** Tay cầm ảo khi chơi giả lập dùng mặt nút AowVN; vẫn nhận phím và rung như trước, chữ trên nút rõ ở độ mờ mặc định. ([73a1a0f](https://github.com/aowvn-10diem/aowvn-monika/commit/73a1a0f99100c76d499380085f032834737690e1))
- Nội bộ: chẩn đoán Kirikiri ghi tối đa năm mẫu tên mục ngắn, có thông báo riêng khi chỉ mục đầy đủ có dấu hiệu tên băm và thiếu kịch bản đọc được, tránh nhận nhầm thư mục chỉ có tài nguyên. ([49bdbd5](https://github.com/aowvn-10diem/aowvn-monika/commit/49bdbd59e06da3e18063976a9df44a261c33b082))
- **Sửa lỗi:** Tên tệp tải xuống có dấu “+” hoặc dấu cách không còn bị đổi sai. ([3bcc30c](https://github.com/aowvn-10diem/aowvn-monika/commit/3bcc30cb21ad33b84d3423fb5f548ca1a5cf4db6))

> V85c chưa gộp vào main tại thời điểm chốt danh sách, chuyển sang v0.8.1 theo [PM A29](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6099303176).

## [v0.6.0](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.0)

- Kết nối RetroAchievements bằng tài khoản web API và xem hồ sơ của mình. ([cba6e15](https://github.com/aowvn-10diem/aowvn-monika/commit/cba6e15372c78b8652a37c019c78f37ab352579a))
- Xem các game vừa chơi và thành tựu vừa mở trong ứng dụng. ([cba6e15](https://github.com/aowvn-10diem/aowvn-monika/commit/cba6e15372c78b8652a37c019c78f37ab352579a))
- Xem danh sách thành tựu theo game; Monika ghép game trong Thư viện với dịch vụ ở 11 hệ máy. ([cba6e15](https://github.com/aowvn-10diem/aowvn-monika/commit/cba6e15372c78b8652a37c019c78f37ab352579a))
- Nintendo DS dùng lõi melonDS DS làm lựa chọn chính. ([9f7ab72](https://github.com/aowvn-10diem/aowvn-monika/commit/9f7ab72fa2ed5de83d03229174423ce5930a70b9))

## [v0.6.1](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.1)

- Có nút “Dịch khung này” để đọc chữ trong hình game bằng OCR. ([0f0b19f](https://github.com/aowvn-10diem/aowvn-monika/commit/0f0b19f077f0a7f6ba436c56543f9ec110746e3d))
- Có thể dịch trên máy hoặc dùng khóa API dịch và AI do người dùng chọn. ([0f0b19f](https://github.com/aowvn-10diem/aowvn-monika/commit/0f0b19f077f0a7f6ba436c56543f9ec110746e3d))
- Bản dịch đã xem được lưu trên máy để dùng lại. ([0f0b19f](https://github.com/aowvn-10diem/aowvn-monika/commit/0f0b19f077f0a7f6ba436c56543f9ec110746e3d))

## [v0.6.2](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.2)

- Gói APK giảm từ khoảng 110 MB xuống khoảng 62 MB sau khi bỏ phần dịch/OCR đóng sẵn. ([addfa5a](https://github.com/aowvn-10diem/aowvn-monika/commit/addfa5a7c9745911d75331515f5a188bb64dc308))
- OCR dùng dịch vụ Google Play Services và tải mô hình khi cần. ([addfa5a](https://github.com/aowvn-10diem/aowvn-monika/commit/addfa5a7c9745911d75331515f5a188bb64dc308))
- Dịch dùng dịch vụ AI mặc định hoặc khóa API của người dùng; lựa chọn “mlkit” cũ tự chuyển sang “ai”. ([addfa5a](https://github.com/aowvn-10diem/aowvn-monika/commit/addfa5a7c9745911d75331515f5a188bb64dc308))

## [v0.6.3](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.3)

- Bộ chạy Azahar cho game 3DS được tải riêng khi cần thay vì nằm trong APK, giảm khoảng 13 MB cho ứng dụng. ([ad7e5b0](https://github.com/aowvn-10diem/aowvn-monika/commit/ad7e5b04971a99c11d1b6626ebeb71b01ab09e28))
- Monika bắt đầu chuẩn bị lõi phù hợp ngay khi người dùng tải game. ([7e89612](https://github.com/aowvn-10diem/aowvn-monika/commit/7e89612eb58bddf9a3c0484f6f0cf94ae1fc17d8))
- Trước khi cài gói Azahar, ứng dụng kiểm tra tính toàn vẹn của gói tải về. ([ec824e4](https://github.com/aowvn-10diem/aowvn-monika/commit/ec824e4bd4c2f1e6b4cb889be1a33c5d8178b495))

## [v0.6.4](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.4)

- Tệp giải nén 7-Zip được chuyển khỏi APK và chỉ tải khi cần mở định dạng phù hợp. ([af8aa5f](https://github.com/aowvn-10diem/aowvn-monika/commit/af8aa5fdab312e34ad2fe9104ea2abcfa18633a1))
- Ứng dụng chuẩn bị trước lõi và thành phần cần thiết theo game đang tải. ([d5e8d9a](https://github.com/aowvn-10diem/aowvn-monika/commit/d5e8d9aa10f2b8444744b29516583c0e19306c7b))
- Với gói trên 15 MB, Monika hỏi trước khi tải bằng 4G; gói nhỏ hơn có thể tự tải. ([d5e8d9a](https://github.com/aowvn-10diem/aowvn-monika/commit/d5e8d9aa10f2b8444744b29516583c0e19306c7b))
- APK nhẹ hơn khoảng 13,9 MB nhờ lược bỏ dữ liệu gỡ lỗi trong thư viện native. ([7e2a08e](https://github.com/aowvn-10diem/aowvn-monika/commit/7e2a08ed443ea7fd6f6ece076dc33593236f9a73))

## [v0.6.5](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.5)

- Có APK riêng cho điện thoại ARM 64-bit và ARM 32-bit. ([5f3f845](https://github.com/aowvn-10diem/aowvn-monika/commit/5f3f8451e36cccae4a55cfc161d7e5f01a6955bb))
- Các thư viện native được lược bỏ dữ liệu gỡ lỗi để giảm dung lượng bản ARM 64-bit. ([5f3f845](https://github.com/aowvn-10diem/aowvn-monika/commit/5f3f8451e36cccae4a55cfc161d7e5f01a6955bb))
- Bản ARM 32-bit cũng được phát hành thành gói cài riêng, phù hợp với thiết bị dùng chip này. ([5f3f845](https://github.com/aowvn-10diem/aowvn-monika/commit/5f3f8451e36cccae4a55cfc161d7e5f01a6955bb))

## [v0.6.6](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.6)

- Tệp ZIP được giải nén bằng công cụ Java, hỗ trợ ZIP có mật khẩu ZipCrypto/AES và tên tệp tiếng Việt. ([6e66270](https://github.com/aowvn-10diem/aowvn-monika/commit/6e66270d2084cec731833944781636713643b797))
- Tệp RAR và 7z dùng gói 7-Zip tải khi cần, thay cho thư viện giải nén cũ. ([6e66270](https://github.com/aowvn-10diem/aowvn-monika/commit/6e66270d2084cec731833944781636713643b797))
- Bản phát hành trở lại một APK universal, không cần chọn riêng gói theo loại chip. ([8a2ee71](https://github.com/aowvn-10diem/aowvn-monika/commit/8a2ee71d1c482fa1bb43b0164059b6e9d804670e))

## [v0.6.7](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.6.7)

- Game ONScripter có thể chạy trong Monika qua engine web, không cần mở trình giả lập ngoài cho hệ này. ([9876293](https://github.com/aowvn-10diem/aowvn-monika/commit/9876293eff3d260fed2352575ca564726b49e0ab))
- Thành phần ONScripter được tải riêng khi cần, không đóng sẵn trong APK. ([9876293](https://github.com/aowvn-10diem/aowvn-monika/commit/9876293eff3d260fed2352575ca564726b49e0ab))
- Hệ Symbian/N-Gage có thể mở bằng ứng dụng EKA2L1 đã cài trên máy. ([587d2cb](https://github.com/aowvn-10diem/aowvn-monika/commit/587d2cb1d81aea1f2823fd57aeab898a9a296588))

## [v0.7.0](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.0)

- Thành tựu RetroAchievements xuất hiện ngay trong game; có thêm chế độ Hardcore. ([c9bea87](https://github.com/aowvn-10diem/aowvn-monika/commit/c9bea87a651649ed4b237ea36c2902567bf48e40))
- Khi bật Hardcore, thao tác gian lận và tải trạng thái lưu bị khóa trong lúc chơi. ([c9bea87](https://github.com/aowvn-10diem/aowvn-monika/commit/c9bea87a651649ed4b237ea36c2902567bf48e40))
- Có thể vá ROM bằng tệp IPS, BPS hoặc UPS; game đã vá xuất hiện như một game riêng. ([dca9a82](https://github.com/aowvn-10diem/aowvn-monika/commit/dca9a82c61b24402523adcf09aa85e50f8d3493a))
- Khối diễn đàn được giữ trên Trang chủ; Facebook mở bằng ứng dụng Facebook, Discord vẫn dùng được. ([e4748d5](https://github.com/aowvn-10diem/aowvn-monika/commit/e4748d5cb38e11af4ee51db628e8b921966bd775))

## [v0.7.1](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.1)

- Báo cáo lỗi cho biết phần nào gặp sự cố và lưu lại các sự kiện gần lúc lỗi xảy ra. ([429cda1](https://github.com/aowvn-10diem/aowvn-monika/commit/429cda11853e6981cd2836308296de43f07394a8))
- Lỗi lặp được gộp lại và dữ liệu riêng được che trước khi lưu báo cáo. ([429cda1](https://github.com/aowvn-10diem/aowvn-monika/commit/429cda11853e6981cd2836308296de43f07394a8))
- Báo cáo bắt thêm game/native bị dừng, ứng dụng không phản hồi và lỗi WebView. ([429cda1](https://github.com/aowvn-10diem/aowvn-monika/commit/429cda11853e6981cd2836308296de43f07394a8))
- Có thể mở danh sách thành tựu ngay trong menu game. ([2ab1cfc](https://github.com/aowvn-10diem/aowvn-monika/commit/2ab1cfce56a4b73ae21681595fce1fe7b15ad608))

## [v0.7.2](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.2)

- Visual novel Kirikiri có thể mở và chạy bên trong Monika. ([6d4564e](https://github.com/aowvn-10diem/aowvn-monika/commit/6d4564e68982cc1cb73bda75790c7773d67f3b14))
- Monika tải gói Kirikiri khi cần và có thể mở thẳng game đã chọn từ Thư viện. ([163e8c0](https://github.com/aowvn-10diem/aowvn-monika/commit/163e8c0e86873c9c424af8dbaef6ac84092409f8))
- Báo cáo lỗi ghi rõ thành phần liên quan; menu game có đường mở danh sách thành tựu. ([aef2c5e](https://github.com/aowvn-10diem/aowvn-monika/commit/aef2c5e1c716d17092e3c12cf5161402492e4bdf))

## [v0.7.3](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.3)

- Màn chuẩn bị Kirikiri tự tải gói cần thiết trước khi mở game. ([92a65b2](https://github.com/aowvn-10diem/aowvn-monika/commit/92a65b22ece2f1346b61aa0875eb33da67a27d4d))
- Có menu Monika tiếng Việt trong game Kirikiri với các lối tiếp tục, mở menu game, tua nhanh và thoát. ([92a65b2](https://github.com/aowvn-10diem/aowvn-monika/commit/92a65b22ece2f1346b61aa0875eb33da67a27d4d))
- Gói Kirikiri phát hành kèm bản dịch giao diện và được tải riêng khi dùng. ([33f7ba0](https://github.com/aowvn-10diem/aowvn-monika/commit/33f7ba0bc27f9d1a721c22f9ff35ae85754f9282))

## v0.7.9

> Đang chuẩn bị, chưa tạo tag hoặc phát hành. Ghi chú đối chiếu các thay đổi đã gộp vào main `0acd449` theo quyết định PM: ra bản sớm, không chờ các phần NDS V78 còn chưa gộp. V78 và bộ giao diện V85 dành cho **bản sau**; sửa tên tệp tải về #182 chưa gộp nên chưa nằm trong bản này.

- Nút tay cầm ảo có nền sáng, chữ tối và viền kép để dễ nhìn trên nền game; giữ trạng thái nhấn, hiệu ứng và các mức rung. ([f2223c2](https://github.com/aowvn-10diem/aowvn-monika/commit/f2223c2fd7069d11f24458569f561cec83d91ba6))
- Game GBA mặc định tắt hiệu chỉnh màu và bộ lọc LCD GBA theo cấu hình. ([98d4d63](https://github.com/aowvn-10diem/aowvn-monika/commit/98d4d63b58a91a55a5e6408a8a6eaa0109d87986))
- Game Java bỏ thanh menu trên đầu; một nút menu nổi mở menu Monika và các mục riêng của game Java. ([0acd449](https://github.com/aowvn-10diem/aowvn-monika/commit/0acd4499357a0351a3e54aeb37f470caf0d7d876))
- Lần đầu vào Thư viện có giải thích quyền quét game và lựa chọn cấp quyền; nếu từ chối vẫn có thể thêm game thủ công, khi quay lại sau cấp quyền ứng dụng kiểm tra quyền thật trước khi quét. ([54ba15b](https://github.com/aowvn-10diem/aowvn-monika/commit/54ba15b8b2d8757055072e78ac40a23207697a10))
- Kirikiri có nhãn thử nghiệm và cảnh báo ngắn trước mỗi lần mở game, cho phép tiếp tục hoặc hủy; chưa xác nhận game Kirikiri chạy ổn trên máy ARM. ([16efdaa](https://github.com/aowvn-10diem/aowvn-monika/commit/16efdaa245af7387a701e2251e1b9ec336a31da0))
- Khi gửi báo lỗi thất bại, ứng dụng hiện lý do, giữ nội dung để thử lại và trả nút về trạng thái dùng được; màn lỗi chuẩn bị game có nút sao chép thông tin chẩn đoán đã che dữ liệu riêng. Nguyên nhân lỗi gửi trên điện thoại còn **[CHƯA KIỂM]**. ([14c94e2](https://github.com/aowvn-10diem/aowvn-monika/commit/14c94e2ee270f8d3bdb7bd0000fa9ab1c2303b15))
- Thư viện tách thư mục chỉ chứa PDF/tài liệu thành nhóm Tài liệu, có thể mở bằng ứng dụng ngoài hoặc xóa riêng tệp; game kèm PDF vẫn giữ trong nhóm game. Mở tài liệu bằng ứng dụng ngoài trên thiết bị thật còn **[CHƯA KIỂM]**. ([d0dff94](https://github.com/aowvn-10diem/aowvn-monika/commit/d0dff94da14de6f8ccd9335c6b08e737e8a7ff96))

## v0.7.8

> [Bản thử — Pre-release](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.8), phát hành ngày 10/10/2026 (UTC). Bộ nút ảo V70a và lô giao diện V69 đã gộp; menu V70b được PM dời sang bản sau. Bản thử chưa xác nhận game Kirikiri chạy ổn trên máy ARM.

- Sửa đường thu thông tin đồ họa Kirikiri: không gọi trình điều khiển khi chưa có ngữ cảnh EGL, tránh nguồn gây dừng ứng dụng đã thấy trên Android 17. Kiểm lại game trên máy ARM sau sửa còn **[CHƯA KIỂM]**. ([8b158f5](https://github.com/aowvn-10diem/aowvn-monika/commit/8b158f59cad9e3b64034f0d4f448e6fbdb894738))

- Bộ nút ảo dùng chung có hiệu ứng lún, bốn mức rung và mục Tay cầm ảo trong Cài đặt; giữ mã phím, hỗ trợ tắt hiệu ứng và tôn trọng cài đặt rung Android. ([0b4cdd2](https://github.com/aowvn-10diem/aowvn-monika/commit/0b4cdd284b3231dcb2430da0cf595d3d3cd9ed13))

- Khi danh sách game tải chậm hoặc lỗi, có trạng thái chờ và nút thử lại; Thư viện hiển thị đường dẫn gọn và cho sao chép đường dẫn đầy đủ. ([62c7c7e](https://github.com/aowvn-10diem/aowvn-monika/commit/62c7c7e758e3f67735deacd579634c52a059a251))
- Thẻ ủng hộ chỉ hiện ở Trang chủ và Cài đặt, có thể ẩn bảy ngày; thanh dưới chừa khoảng cho nội dung và tùy chọn giả lập thêm bản dịch tiếng Việt. ([62c7c7e](https://github.com/aowvn-10diem/aowvn-monika/commit/62c7c7e758e3f67735deacd579634c52a059a251))

## v0.7.7

> [Bản thử — Pre-release](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.7), phát hành ngày 08/10/2026 tại commit `3387671`. Các thay đổi dưới đây nằm giữa tag v0.7.6 (`4a1486e`) và tag v0.7.7; bản thử chưa xác nhận game Kirikiri chạy ổn trên máy ARM.

- Tại màn chuẩn bị Kirikiri, khi không tìm thấy lối vào, có thể gửi báo lỗi ngay; báo cáo thêm tóm tắt các gói XP3 và thông báo nêu lý do nếu gửi không thành công. ([731c8aa](https://github.com/aowvn-10diem/aowvn-monika/commit/731c8aa951896b609f434bfb7b1d2d71687b03b6), [c364421](https://github.com/aowvn-10diem/aowvn-monika/commit/c364421ccefdd144524f7edc3742fa4780334663))
- Bước kiểm lối vào Kirikiri có giới hạn chờ 8 giây; nếu đọc kho quá hạn, ứng dụng giữ lối vào đã chọn thay vì chờ vô hạn. ([0896613](https://github.com/aowvn-10diem/aowvn-monika/commit/08966138f15f5d44cdcc697c14d821a164007437))
- Khi quét thư mục có nhiều gói XP3, bộ đọc xử lý từng chỉ mục rồi bỏ dữ liệu tạm trước khi đọc gói kế tiếp, giảm lượng chỉ mục cùng giữ trong bộ nhớ. ([cce4929](https://github.com/aowvn-10diem/aowvn-monika/commit/cce4929dc056d6e93f32217db299221c4816b612))
- Khi không tìm thấy lối vào và chỉ mục đầy đủ cho thấy các gói `.xp3` có vẻ mã hóa, ứng dụng thông báo đây là khả năng chưa xác nhận và lõi Kirikiri hiện chưa hỗ trợ loại gói đó. ([147e70a](https://github.com/aowvn-10diem/aowvn-monika/commit/147e70a70aa3117baea496c477108bdda35c071b))
- Thông báo "Đã tải xong" mở được tệp lưu qua kho đa phương tiện hoặc trình chọn tệp Android, ngoài tệp do Monika tự cung cấp. ([6675158](https://github.com/aowvn-10diem/aowvn-monika/commit/66751589ca89461f69d0ee859df48dd6c287cf83))

## [v0.7.6](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.6) (Bản thử — Pre-release)

- Chọn tệp XP3 Kirikiri có `startup.tjs` ở gốc làm lối vào, thay vì nhầm sang tệp phụ. ([#93](https://github.com/aowvn-10diem/aowvn-monika/pull/93))
- Khi không tìm thấy lối vào, ghi thêm chi tiết chỉ mục XP3 trong dấu vết chẩn đoán để người dùng gửi báo cáo lỗi. ([#91](https://github.com/aowvn-10diem/aowvn-monika/pull/91))
- Bản thử này chưa xác nhận game Kirikiri chạy trên thiết bị thật; một ảnh màn hình app 0.7.6 vẫn cho thấy `NotFound` và nguyên nhân cụ thể **[CHƯA KIỂM]**. ([V26](opus/ket-qua/V26.md))

## [v0.7.5](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.5) (Bản thử — Pre-release)

- Nút "Báo lỗi game này" cho phép người dùng gửi báo cáo lỗi trực tiếp từ menu trong game, kèm thông tin chi tiết về engine và thành phần. ([#60](https://github.com/aowvn-10diem/aowvn-monika/pull/60), [b65c315](https://github.com/aowvn-10diem/aowvn-monika/commit/b65c315))
- **Bản thử**: Chưa có sửa lỗi Kirikiri Kara no Shoujo; sẽ bổ sung ở bản tiếp theo.

## [v0.7.4](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.4)

- Nếu thư mục game có tệp `.exe` và `.xp3` cùng tên, Kirikiri chọn đúng cặp game thay vì tệp `.exe` không liên quan. ([9a32943](https://github.com/aowvn-10diem/aowvn-monika/commit/9a32943375890d2581738e2abfcd9f39fca5de1d), [6454b56](https://github.com/aowvn-10diem/aowvn-monika/commit/6454b56292c8e5c677049be9efe6195709bd6a76))
- Tệp `patch*.xp3` không bị chọn nhầm làm tệp mở game. ([9a32943](https://github.com/aowvn-10diem/aowvn-monika/commit/9a32943375890d2581738e2abfcd9f39fca5de1d))
- Có thể mở game mà tệp `.exe` chứa sẵn dữ liệu XP3. ([9a32943](https://github.com/aowvn-10diem/aowvn-monika/commit/9a32943375890d2581738e2abfcd9f39fca5de1d))
