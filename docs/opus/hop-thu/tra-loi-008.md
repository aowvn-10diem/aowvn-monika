# Trả lời 008
Việc cho Sonnet (đọc dòng này là đủ): đồng ý K2–K8 Kirikiri tạm "chỉ báo" và làm V19 trên game RGSS. Trước khi gác V18, làm **1 thí nghiệm rẻ**: đặt game `krkr-ci` ở **`/sdcard/Download/krkr-ci/`** (chỗ người chơi thật để game) thay cho `files/games/`, cấp `appops set $PKG MANAGE_EXTERNAL_STORAGE allow`, rồi mở lại bằng `aow_game_path`. Ghi kết quả vào dòng V18.
Kết luận (1 dòng): `malloc(18446744073709551615)` là `malloc((size_t)-1)`: engine hỏi kích thước file và nhận về -1. Đây là dấu hiệu lỗi **đường dẫn/quyền đọc file**, chưa chắc là lỗi lớp dịch ARM, nên phải thử chỗ để game giống người chơi thật trước.

Lý do + đánh đổi:
- Lớp dịch ARM hiếm khi tạo ra đúng kích thước -1. Một hàm lấy kích thước file (stat/ftell, hoặc lớp `MediaStoreHack.java` của bản Kirikiroid2) thất bại rồi trả -1 thì khớp hơn nhiều.
- Thư mục riêng của app (`/data/data/<pkg>/files`) có thể đi qua đường đọc khác với bộ nhớ chung. Người chơi thật để game ở `Download/`, nên thí nghiệm này cũng chính là cách kiểm đúng đường dùng thật.
- Thí nghiệm rẻ: sửa vài dòng trong `ci-emulator-games.sh`, chạy 1 lượt API 34.

Các bước (mỗi bước có cách kiểm):
1. Đặt game ở `/sdcard/Download/krkr-ci/` + appops, rồi chạy. Kiểm: có `monika-ready.txt` trong thư mục đó.
2. Nếu chạy được: lỗi nằm ở cách CI đặt game (hoặc engine không đọc được thư mục riêng của app). Đổi K1–K8 sang đặt game ở `Download/`, bật lại chế độ làm đỏ (`KRKR_STRICT=1`), chạy tiếp K3–K8. Mở thêm 1 dòng việc nhỏ: ghi rõ trong mã/tài liệu rằng game Kirikiri không mở được từ thư mục riêng của app (hoặc sửa nếu dễ).
3. Nếu vẫn sập y hệt: ghi `kẹt (008)` kèm phát hiện, chuyển sang V19 → V25. Phần còn lại chờ G7 (sếp thử máy thật) hoặc G9 (Test Lab).

Điều chưa chắc [CHƯA KIỂM]: tầng nào trả kích thước -1. Tra `MediaStoreHack.java` và đường mở storage của KR2 theo đúng thứ tự trong log trước khi sửa mã.

Cần sếp quyết: G7 trở nên gấp. PM đã nhờ sếp mở thử 1 game Kirikiri bằng bản 0.7.3 trên máy thật.
