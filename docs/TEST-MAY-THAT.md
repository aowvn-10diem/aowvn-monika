# Checklist test trên máy thật — Aow Monika 0.2.0

Cài bản **arm64** (đa số máy từ 2017 trở lên). Máy báo "không tương thích" → cài bản **armeabi-v7a**.
Ghi kết quả vào cột cuối: ✅ đạt · ❌ lỗi (ghi ngắn lỗi gì, chụp màn hình nếu được) · ⏭ bỏ qua.

Thông tin máy: Hãng/đời máy: ______ · Android: ______ · RAM: ______

## 1. Cài đặt & mở app

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 1.1 | Cài APK, mở app | Mở được, không văng. Hiện màn Trang chủ có menu nổi phía dưới | |
| 1.2 | Android 13+: app hỏi quyền thông báo → Cho phép | Không văng | |
| 1.3 | Xoay ngang / dọc ở Trang chủ | Giao diện không vỡ, không văng | |
| 1.4 | Cài đặt → xem dòng "Bản 0.2.0 · cấu hình v…" | Hiện đúng số bản | |

## 2. Đọc bài & thông báo (cần mạng Việt Nam)

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 2.1 | Trang chủ: kéo xuống danh sách bài | Có bài mới nhất của aow.vn, ảnh bìa hiện đủ | |
| 2.2 | Chọn 1 nhãn (vd. Game NDS Việt Hóa) | Chỉ còn bài của nhãn đó | |
| 2.3 | Mở 1 bài | Đọc được nội dung, ảnh, thấy mục link tải | |
| 2.4 | Cài đặt → chọn nhãn nhận thông báo | Lưu được, thoát ra vào lại vẫn còn | |
| 2.5 | Để app chạy nền ~1–2 giờ khi aow.vn có bài mới | Có thông báo bài mới, bấm vào mở đúng bài | |

## 3. Tải & giải nén game

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 3.1 | Mở bài có link **Pixeldrain** → bấm Tải | Tải nền, có thông báo tiến độ | |
| 3.2 | Tải xong file .zip/.rar | Thông báo "Đang giải nén **xx%**" chạy tăng dần, rồi "Đã tải xong" | |
| 3.3 | Bấm thông báo "Đã tải xong" | Mở tab Thư viện, game có ảnh bìa + tên gọn (không còn "Việt Hóa", đuôi file…) | |
| 3.4 | Game file nén có mật khẩu `aowvn.org` | Tự giải được, không phải nhập | |
| 3.5 | Game chia nhiều phần (part1, part2…) — tải lần lượt | Tải phần 1: báo "đang chờ phần tiếp theo". Đủ phần: tự giải nén | |
| 3.6 | Thư viện → "Thêm game từ máy" → chọn file .7z **có mật khẩu** | Giải nén được (nếu mật khẩu khác aowvn.org: bấm Giải nén, nhập mật khẩu) | |
| 3.7 | Thêm file game nặng (ISO PSP > 500 MB) | Thanh "Đang giải nén xx%" ở Thư viện chạy đều, không đứng im lâu | |
| 3.8 | Link host khác (Google Drive, Mega…) | Mở trình duyệt để tải; tải xong dùng "Thêm game từ máy" | |

## 4. Chơi game giả lập (lần đầu mỗi hệ sẽ tải lõi — cần mạng)

Mỗi hệ thử 1 game. Kiểm: **vào được game · có tiếng · phím ảo bấm ăn · không giật nặng**.

| # | Hệ | Vào game | Tiếng | Phím | Mượt | KQ |
|---|---|---|---|---|---|---|
| 4.1 | NDS (lõi desmume) | | | | | |
| 4.2 | NDS — Cài đặt đổi lõi sang melonDS, mở lại game | | | | | |
| 4.3 | GBA | | | | | |
| 4.4 | GBC / GB | | | | | |
| 4.5 | PS1 | | | | | |
| 4.6 | PSP | | | | | |
| 4.7 | RPG Maker 2000/2003 | | | | | |
| 4.8 | Flash (.swf) | | | | | |
| 4.9 | RPG Maker MV/MZ | | | | | |

**Trong game** (thử ở 1 game bất kỳ):

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 4.10 | Máy dọc | Game ở trên, phím ở dưới, phím không che game | |
| 4.11 | Máy ngang | Game giữa, phím hai bên | |
| 4.12 | Menu … → chọn ô 2 → Lưu → chơi tiếp → Tải từ ô 2 | Quay về đúng chỗ đã lưu; ô 2 có dấu • | |
| 4.13 | Menu … → Tốc độ 2x | Game chạy nhanh gấp đôi | |
| 4.14 | Menu … → Độ mờ phím | Phím mờ/đậm theo 3 mức | |
| 4.15 | Menu … → Chỉnh vị trí & cỡ phím → kéo, đổi cỡ → Xong | Thoát game vào lại vẫn giữ vị trí | |
| 4.16 | Menu … → **Tùy chọn giả lập** | Có danh sách tùy chọn; bấm 1 dòng đổi giá trị; "Về mặc định" hoạt động | |
| 4.17 | Thoát game, vào lại (game có save trong game) | Save trong game còn | |
| 4.18 | Tay cầm Bluetooth (nếu có) | Bấm được trong game | |

## 5. Game Java (J2ME)

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 5.1 | Mở game .jar lần đầu | Có bước cài vài giây, rồi vào game | |
| 5.2 | Phím ảo | Phím bo tròn kiểu Monika, nhấn thấy lún + đổi màu cam-hồng, **rung nhẹ "tách"** | |
| 5.3 | Chơi vài phút, thoát, vào lại | Không văng, save còn | |
| 5.4 | Game 3D (nếu có) | Chạy được | |

## 6. APK & app ngoài

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 6.1 | Game dạng .apk | Mở trình cài Android, cài được | |
| 6.2 | Game Kirikiri / Ren'Py khi chưa cài app ngoài | Hiện hướng dẫn + nút tải app | |
| 6.3 | Đã cài app ngoài → bấm Chơi | Mở đúng app ngoài | |

## 6b. Kirikiri (visual novel .xp3) — 0.7.3

Kirikiri nhúng sâu trong Monika (không phải app ngoài): màn chuẩn bị tự tải gói, menu Việt hóa, chạy ở tiến trình riêng.

**Trước khi test:** chuẩn bị file game .xp3 (hoặc thư mục có `startup.tjs` + `data.xp3`).

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 6b.1 | Máy arm64 (arm64-v8a): Thư viện → +Game → chọn file `.xp3` lần đầu | Có bước tải gói Kirikiri + thông báo tiến độ, rồi vào game | |
| 6b.2 | Máy 32-bit (armeabi-v7a): Bấm game Kirikiri | Hiện thông báo "Kirikiri chưa hỗ trợ máy 32-bit" hoặc tự tải gói arm64 xong báo lỗi | |
| 6b.3 | Vào game Kirikiri → chạm vào cảnh, kéo → có phản ứng (chữ hiện, menu, phím) | Không văng; nhân vật / nội dung hiện được | |
| 6b.4 | Game có tiếng (BGM, hiệu ứng) | Âm thanh phát bình thường | |
| 6b.5 | Lưu / Tải game trong menu game hoặc gọi menu Monika | Lưu được, tải đúng vị trí đã lưu, không mất tiến độ | |
| 6b.6 | Menu Monika (… phím dưới cùng) → Tốc độ 2x | Game tua nhanh | |
| 6b.7 | Menu Monika → Cài đặt nhân vật / ngôn ngữ (nếu game hỗ trợ Việt hóa) | Chữ Việt hóa hiển thị đủ dấu, không xáo trộn | |
| 6b.8 | Thoát game (bấm menu, chọn Thoát hoặc bấm back) | Quay lại Thư viện, game vẫn có icon + tên | |

## 7. Bộ nhớ & ổn định

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 7.1 | Cài đặt → Bộ nhớ: đổi giới hạn (vd. 2 GB) | Hiện dung lượng đang dùng đúng | |
| 7.2 | Ghim 1 game, tải thêm game cho vượt giới hạn | Game cũ không ghim bị dọn, game ghim còn nguyên, game vừa tải còn | |
| 7.3 | Game "Đã dọn" → tải lại | Ô "Đã dọn" cũ biến mất, thay bằng game mới | |
| 7.4 | Dùng liên tục 30 phút, chuyển app qua lại | Không văng, máy không nóng bất thường | |
| 7.5 | Máy yếu (RAM ≤ 3 GB): Cài đặt → Hiệu ứng | Tự chọn "tiết kiệm", lướt vẫn mượt | |

## Gửi lại cho Claude

Chỉ cần gửi: **các dòng ❌** (số mục + mô tả + ảnh). Nếu app văng: bật lại app, nếu có thông báo lỗi thì chụp lại.
