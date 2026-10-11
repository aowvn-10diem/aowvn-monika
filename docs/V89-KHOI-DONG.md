# V89 — tiến độ khởi động libretro (v0.8.2)

Màn chờ phân biệt kiểm tra/cache lõi, tải lõi (MB/% nếu có Content-Length), giải nén, chuẩn bị ROM (MB), `retro_init`, đọc ROM, `retro_load_game`, chuẩn bị âm thanh/thành tựu, khởi tạo hình ảnh và chờ khung hình đầu. Với core dùng `need_fullpath`, việc đọc ROM do chính core thực hiện nằm trong thời gian `retro_load_game`; không suy diễn thành thời gian đọc riêng.

Mỗi lần đổi bước ghi `libretro-startup` breadcrumb với thời gian bước trước (ms, đồng hồ đơn điệu). Sau 60 giây từ đầu màn chuẩn bị hiện **Sao chép chẩn đoán**, giữ game tiếp tục nạp. Khung hình đầu đóng màn chờ. Timeout không coi là lỗi lõi, không đổi lõi hoặc tự gửi báo lỗi. Chẩn đoán có môi trường, bản lõi, kích thước ROM, thời gian từng bước và breadcrumb; qua `Diagnostics.scrub` trước khi sao chép.

Để UI còn đáp ứng khi `retro_init` chậm, màn Retro bật `initializeOnGlThread`; các client khác giữ mặc định cũ. Hủy Activity xếp việc destroy sau việc đang chạy trên GL, tránh destroy đồng thời với nạp game. JNI callback chỉ sống trong lời gọi hiện tại; R8 giữ `onStage` của lớp triển khai.

## Kiểm nghi phạm V78c

- Main nền `a8a8823` vẫn có bốn khóa trong `cores.melondsds.options`.
- Đã tải ZIP đúng URL config: <https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/melondsds_libretro_android.so.zip> ngày 11/10/2026 UTC; SHA-256 `462346c0d0258f9871071cb4231e6d163e3c12598186d797b2c6b5a3617ece3f`. Đây là URL nightly có thể thay đổi; hash xác định mẫu đã kiểm. Cả bốn khóa xuất hiện trong binary.
- Đối chiếu schema nguồn melonDS DS tại SHA [`f394adbacb5722ee97c1b37c8064da9a25818310`](https://github.com/JesseTG/melonds-ds/tree/f394adbacb5722ee97c1b37c8064da9a25818310/src/libretro/config): số layout `1` hợp lệ (mặc định `2`), layout `top-bottom` hợp lệ/mặc định, hybrid ratio `2` hợp lệ/mặc định, gap `0` hợp lệ. Không khẳng định buildbot dựng đúng SHA nguồn này.
- Chưa có bằng chứng bốn tùy chọn gây chậm; chưa sửa config hay bố cục NDS. So sánh bật/tắt và bisect trên ROM/máy sếp **[CHƯA KIỂM]**. Thời gian chậm thực tế của Pokemon Black 2 Kaizo **[CHƯA KIỂM]**; cần lấy chẩn đoán từ bản chứa V89 để xác định bước.
- Cache lõi đã tồn tại theo file `.so` và version (có khóa liên tiến trình); test thêm kiểm lần chạy thứ hai không gửi request cho cả core legacy và pinned. Không có bằng chứng app luôn tải lại core trên máy sếp.

## Kiểm thử

`StartupProgressTest`: thời gian bước, cập nhật tiến độ không reset đồng hồ, đúng biên 60 giây, nạp tiếp sau timeout, khung hình trước timeout; MB khi thiếu Content-Length. `CorePinTest`: thứ tự tải/giải nén, dùng cache và không gửi request lần hai. `RetroStartupTest`: tiến độ vẫn hiện, nút sao chép chỉ có sau timeout và nhận click.

Lệnh bắt buộc `./gradlew testDebugUnitTest --console=plain` đã thử trước push: môi trường dừng khi tải Gradle 8.14.3 (`java.net.ConnectException: Connection refused`), chưa chạy test. Kết quả CI ghi trong PR. Gameplay, xoay màn hình, nền/khôi phục lúc core đang nạp và JNI callback trên máy thật **[CHƯA KIỂM]**.
