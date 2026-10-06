# Nhật ký thay đổi

Các thay đổi chính người dùng thấy được, từ v0.6.0 đến bản mới nhất. Mỗi mục liên kết tới tag phát hành; từng gạch đầu dòng dẫn tới commit làm thay đổi đó.

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

## [v0.7.5](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.5) (Bản thử — Pre-release)

- Nút "Báo lỗi game này" cho phép người dùng gửi báo cáo lỗi trực tiếp từ menu trong game, kèm thông tin chi tiết về engine và thành phần. ([#93](https://github.com/aowvn-10diem/aowvn-monika/pull/93), [c3489f3](https://github.com/aowvn-10diem/aowvn-monika/commit/c3489f3))
- Cải thiện chẩn đoán lỗi Kirikiri; ứng dụng ghi lại các sự kiện gần lúc game bị dừng. ([#91](https://github.com/aowvn-10diem/aowvn-monika/pull/91), [ac7a843](https://github.com/aowvn-10diem/aowvn-monika/commit/ac7a843))
- Sửa lỗi nội bộ và nâng cấp thành phần. ([#85](https://github.com/aowvn-10diem/aowvn-monika/pull/85), [#88](https://github.com/aowvn-10diem/aowvn-monika/pull/88), [#82](https://github.com/aowvn-10diem/aowvn-monika/pull/82), [#83](https://github.com/aowvn-10diem/aowvn-monika/pull/83))
- **Lưu ý bản thử**: Lỗi "Kara no Shoujo không khởi động" chưa được sửa; sẽ có ở bản 0.7.6. Bản này là bản thử để kiểm tra tính năng báo lỗi mới.

## [v0.7.4](https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.4)

- Nếu thư mục game có tệp `.exe` và `.xp3` cùng tên, Kirikiri chọn đúng cặp game thay vì tệp `.exe` không liên quan. ([9a32943](https://github.com/aowvn-10diem/aowvn-monika/commit/9a32943375890d2581738e2abfcd9f39fca5de1d), [6454b56](https://github.com/aowvn-10diem/aowvn-monika/commit/6454b56292c8e5c677049be9efe6195709bd6a76))
- Tệp `patch*.xp3` không bị chọn nhầm làm tệp mở game. ([9a32943](https://github.com/aowvn-10diem/aowvn-monika/commit/9a32943375890d2581738e2abfcd9f39fca5de1d))
- Có thể mở game mà tệp `.exe` chứa sẵn dữ liệu XP3. ([9a32943](https://github.com/aowvn-10diem/aowvn-monika/commit/9a32943375890d2581738e2abfcd9f39fca5de1d))
