# Trả lời 007
Việc cho Sonnet (đọc dòng này là đủ): trong lúc CI bị chặn, làm các việc kiểm được bằng Gradle trên máy phiên. Theo thứ tự: V17 (P2 `:renpy`, kiểm bằng `./gradlew :renpy:assembleRelease assembleRelease testDebugUnitTest` chạy cục bộ), rồi phần D2 của V22 (`engines.<id>.errorPatterns` + unit test). V18 dừng tại `ec9f553`; khi CI chạy lại thì chạy Emulator Test API 34 trước tiên.
Kết luận (1 dòng): G10 (thanh toán GitHub Actions) là việc của sếp, PM đã nhắn sếp. Đội không ngồi chờ, chuyển sang việc không cần CI.

Lý do + đánh đổi:
- V17 và D2 không cần máy ảo và không cần dựng gói. Test cục bộ là đủ để push. Build trên CI chạy bù khi được mở lại.
- Push mã khi CI đang chặn thì không có lưới an toàn của CI. Vì vậy bắt buộc chạy `./gradlew testDebugUnitTest` cục bộ trước mỗi lần push (đúng `CLAUDE.md`), và ghi lệnh đã chạy vào commit.
- Đừng thử lại workflow liên tục: mỗi lượt bị từ chối không đem lại gì.

Các bước (mỗi bước có cách kiểm):
1. V17: làm theo dòng P2 của phương án. Kiểm: 3 lệnh Gradle trên xanh cục bộ; `dexdump` thấy cả `org.libsdl.app.SDLActivity` và `vn.aow.monika.rgss.sdl.SDLActivity`.
2. V22-D2: trường config có mặc định rỗng; `ConfigTest` đọc được config cũ. Kiểm: unit test xanh cục bộ.
3. Khi CI chạy lại (PM sẽ ghi lên bảng việc): Emulator Test API 34 với thăm dò tầng của V18, rồi V19.

Điều chưa chắc [CHƯA KIỂM]: sau khi sếp xử lý thanh toán, có cần chạy lại thủ công các run đã bị chặn không. Cứ chạy lại Emulator Test bằng `workflow_dispatch`.
