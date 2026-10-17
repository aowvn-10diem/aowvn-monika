# Prompt gọi Nova làm V77 + V78 (10/10/2026)

```
Nova, đây là việc GẤP của sếp cho bản ổn định v0.7.9 (repo aowvn-10diem/aowvn-monika, nhánh chính main). Ưu tiên hơn V45b và mọi việc khác. Hạn: 10/10 22:00 giờ VN. Hộp thư của bạn: issue #90 (báo tiến độ 1–2 dòng ở đó, tiền tố "[Nova → PM]").

Bắt đầu: git fetch origin main docs/opus-tra-loi; đọc CLAUDE.md; nạp skill j2me-loader (V77), them-he-may + giao-dien-monika (V78). Mỗi việc một nhánh nova/<mã> từ main mới nhất, một PR trạng thái thường (không nháp), tiêu đề "[viec-<mã>] …".

V77 — Game Java (J2ME):
- Bỏ thanh menu trên đầu màn chơi J2ME.
- Đưa toàn bộ chức năng menu J2ME (kể cả chụp màn hình) vào menu Monika.
- Nút vào menu Monika thành MỘT nút menu bấm được trên màn chơi.
- Không đổi bàn phím ảo J2ME (bố cục, mã phím).

V78 — NDS (có thể tách 2 PR: (a)+(b) trước, (c) sau):
(a) Hai màn hình NDS to ra, chiếm tối đa chiều rộng (hiện quá bé).
(b) Thanh tiêu đề (hệ máy + tên game) TỰ ẨN khi đang chơi, hiện lại khi chạm mép trên hoặc mở menu.
(c) Mục "Màn hình" trong Cài đặt Monika: bố cục NDS (trên–dưới / cạnh nhau / một màn lớn), tỉ lệ, khoảng cách.
- Config-first: tùy chọn lõi melonDS DS và mặc định nằm trong config/monika-config.json; trường mới trong MonikaConfig.kt có giá trị mặc định; tăng configVersion (main hiện đã 39 sau V81 — merge main trước khi đẩy để không trùng số).

Luật:
- Giao diện chỉ dùng token Monika.*, Radius, primaryGradient() và ui/theme/Components.kt; thời lượng animation từ Monika.motion.
- Test ảnh Robolectric cho mọi màn đổi; chạy ./gradlew testDebugUnitTest trước khi đẩy (không chạy được thì ghi rõ trong PR).
- Tiền duyệt: Haiku-2; duyệt lần hai: Luna Ultra; PM gộp. Không tự gộp, không push thẳng main, không force-push.
- Không đụng khóa ký/token/secret, không phát hành, không commit game/ROM.
- Tham khảo phong cách nút sau này (V85, chưa làm ngay): docs/opus/thiet-ke/ui-kit-aowvn/README.md trên nhánh docs/opus-tra-loi.

Mở PR sớm, báo link PR ở #90 ngay khi có.
```
