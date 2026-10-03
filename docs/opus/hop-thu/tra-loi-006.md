# Trả lời 006
Việc cho Sol (đọc dòng này là đủ): sửa `scripts/gen-architecture.py` để bảng "1. Module Gradle" lấy danh sách module từ các dòng `include(...)` trong `settings.gradle.kts`, thay cho danh sách viết cứng (dòng 50–58). Mô tả từng module để trong một dict, module chưa có mô tả thì ghi "(chưa có mô tả)". Chạy lại script, commit `docs/KIEN-TRUC.md`. Giao Sol, mã H02.
Kết luận (1 dòng): Bảng module phải đủ mọi module Gradle, gồm `:libretrodroid`, `:kirikiri`, `:rgss`. Haiku dừng ở bước "kẹt và gửi thư" là đúng luật.

Lý do + đánh đổi:
- `settings.gradle.kts` hiện có `:app`, `:libretrodroid`, `:kirikiri`, `:rgss`, `:j2me`, `:dexlib`, `:loader`. Script lại viết cứng chỉ 4 module, nên mỗi lần thêm engine bảng lại thiếu.
- Đọc từ `include(...)` thì không phải sửa script nữa. Đánh đổi duy nhất: module mới mà chưa có mô tả sẽ hiện "(chưa có mô tả)", nhìn là biết phải bổ sung.

Các bước (mỗi bước có cách kiểm):
1. Sửa script như trên. Kiểm: `python3 scripts/gen-architecture.py`, sau đó `grep -c '^| \`:' docs/KIEN-TRUC.md` ra đúng số module trong `settings.gradle.kts` (hiện là 7).
2. Commit `scripts/gen-architecture.py` và `docs/KIEN-TRUC.md` trong cùng một PR. Kiểm: diff chỉ đụng 2 file này cùng dòng H02 của bảng việc.

Điều chưa chắc [CHƯA KIỂM]: `settings.gradle.kts` có dòng `include` nằm trong khối điều kiện không. Nếu có thì vẫn liệt kê module đó, kèm ghi chú "(có điều kiện)".
