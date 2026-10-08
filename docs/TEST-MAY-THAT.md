# Checklist test trên máy thật — Aow Monika 0.7.7 (RC pre-release; chưa có bản ổn định)

Cài bản **arm64** (đa số máy từ 2017 trở lên). Máy báo "không tương thích" → cài bản **armeabi-v7a**.
Ghi kết quả vào cột cuối: ✅ đạt · ❌ lỗi (ghi ngắn lỗi gì, chụp màn hình nếu được) · ⏭ bỏ qua.

Thông tin máy: Hãng/đời máy: ______ · Android: ______ · RAM: ______

## Thử nhanh theo module

Dùng game hoặc tệp đã có trên máy. Một số module cần mạng để tải gói chạy trong lần mở đầu tiên.

| Module | Cần chuẩn bị | Các bước | Đạt khi |
|---|---|---|---|
| Máy chơi game cổ điển | ROM của một hệ libretro có trong app | 1. Thư viện → Thêm game từ máy → chọn ROM.<br>2. Chờ lõi tải nếu app yêu cầu.<br>3. Mở game, bấm vài phím ảo.<br>4. Mở rồi thoát menu Monika. | Game vào được, phím điều khiển được và quay lại Thư viện không văng. |
| 3DS | Tệp game 3DS đã có trên máy | 1. Thư viện → Thêm game từ máy → chọn tệp.<br>2. Chờ gói Azahar tải nếu được yêu cầu.<br>3. Mở game và thử phím ảo.<br>4. Thoát về Thư viện. | Game mở được, phím phản hồi và app quay lại Thư viện bình thường. |
| Game Java | Tệp `.jar` đã có trên máy | 1. Thư viện → Thêm game từ máy → chọn `.jar`.<br>2. Chờ bước cài game nếu xuất hiện.<br>3. Mở game, bấm phím ảo.<br>4. Thoát rồi mở lại game. | Game mở được, phím phản hồi và app không văng khi mở lại. |
| Kirikiri | Tệp `.xp3` hoặc thư mục game Kirikiri đã có trên máy | 1. Thư viện → Thêm game từ máy → chọn tệp.<br>2. Chờ gói Kirikiri tải nếu được yêu cầu.<br>3. Mở game, chạm một lựa chọn hoặc cảnh.<br>4. Thoát về Thư viện. | Game mở, thao tác chạm có phản hồi và app quay lại Thư viện bình thường. |
| Ren'Py | Gói `.zip` game Ren'Py đã có trên máy và JoiPlay đã cài | 1. Thư viện → Thêm game từ máy → chọn `.zip`.<br>2. Chờ giải nén.<br>3. Mở game; làm theo thông báo để chọn thư mục trong JoiPlay.<br>4. Quay lại Monika. | Monika nhận ra game và mở JoiPlay hoặc báo cách khắc phục nếu app ngoài chưa sẵn sàng. |
| RPG Maker XP/VX/Ace | Gói `.zip` game và JoiPlay đã cài | 1. Thư viện → Thêm game từ máy → chọn `.zip`.<br>2. Chờ giải nén.<br>3. Mở game; làm theo thông báo để chọn thư mục trong JoiPlay.<br>4. Thử phím điều khiển rồi quay lại Monika. | Monika nhận ra game và mở JoiPlay; game nhận thao tác điều khiển. |
| Game web | Tệp `.swf` hoặc gói `.zip` game web đã có trên máy | 1. Thư viện → Thêm game từ máy → chọn tệp/gói.<br>2. Chờ giải nén nếu có.<br>3. Mở game từ Thư viện.<br>4. Thử thao tác trong game rồi thoát. | Game mở được, thao tác có phản hồi và app quay lại Thư viện bình thường. |
| Game Android (APK) | APK của game và các tệp dữ liệu đi kèm nếu có | 1. Thư viện → Thêm game từ máy → chọn APK.<br>2. Làm theo hộp thoại cài đặt Android.<br>3. Mở game đã cài.<br>4. Dùng nút quay lại để trở về Monika. | Android cài được APK, game mở và Monika vẫn hoạt động sau khi quay lại. |
| Vá Việt hóa ROM | ROM libretro trong Thư viện và bản vá IPS/BPS/UPS tương ứng đã có trên máy | 1. Mở menu của game trong Thư viện.<br>2. Chọn “Vá Việt hóa (IPS/BPS/UPS)”.<br>3. Chọn bản vá và đợi hoàn tất.<br>4. Mở game mới có hậu tố “(Việt hóa)”. | Tạo được game đã vá, ROM gốc còn nguyên và game mới có trong Thư viện. |
| Đọc bài và thông báo | Kết nối mạng | 1. Mở Trang chủ, kéo xuống làm mới danh sách bài.<br>2. Mở một bài rồi quay lại.<br>3. Chọn nhãn nhận thông báo trong Cài đặt.<br>4. Thoát vào lại Cài đặt để kiểm tra. | Bài mở được; lựa chọn nhãn thông báo vẫn còn sau khi mở lại app. |
| Thư viện và tải game | Kết nối mạng và một bài có tệp tải phù hợp để thử | 1. Mở bài có tệp tải.<br>2. Bấm Tải và theo dõi tiến độ.<br>3. Chờ tải, giải nén hoàn tất nếu có.<br>4. Mở Thư viện. | Tệp tải xong; game xuất hiện trong Thư viện và có thể mở. |
| Trong lúc chơi | Một game nhúng có lớp phủ Monika | 1. Mở menu Monika.<br>2. Đổi độ mờ phím rồi tiếp tục chơi.<br>3. Xoay máy nếu game hỗ trợ xoay.<br>4. Dùng Back để đóng/mở menu.<br>5. Thoát game về Thư viện. | Phím và menu phản hồi; app không văng và trở lại Thư viện. |

## 1. Cài đặt & mở app

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 1.1 | Cài APK, mở app | Mở được, không văng. Hiện màn Trang chủ có menu nổi phía dưới | |
| 1.2 | Android 13+: app hỏi quyền thông báo → Cho phép | Không văng | |
| 1.3 | Xoay ngang / dọc ở Trang chủ | Giao diện không vỡ, không văng | |
| 1.4 | Cài đặt → xem dòng "Bản {versionName} · cấu hình v{configVersion}" | Hiện đúng số phiên bản và cấu hình của APK đang cài | |

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

### 4.19 Tay cầm ảo: hiệu ứng bấm, rung và bố cục (chỉ thử trên APK có tính năng)

Các mục V70a/V70c này cần kiểm trên máy thật; giả lập CI không thay thế cảm nhận rung. Nếu APK chưa có mục **Tay cầm ảo** hoặc **Chỉnh phím**, ghi **[CHƯA KIỂM]** và bỏ qua thay vì đánh dấu đạt.

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 4.19.1 | Mở game có phím ảo, nhấn rồi thả D-pad và một nút hành động | Khi nhấn, nút thu còn 92% cỡ ban đầu và dịch xuống 2 dp, rồi bật lại; phím không bị kẹt sau khi thả | |
| 4.19.2 | Cài đặt → Tay cầm ảo → tắt hiệu ứng bấm; nhấn nút rồi bật lại và thử | Khi tắt, không chạy hoạt ảnh lún; khi bật, hiệu ứng trở lại. Game vẫn nhận đúng phím | |
| 4.19.3 | Cài đặt → Tay cầm ảo → thử Tắt/Nhẹ/Vừa/Mạnh; nhấn, thả rồi giữ một phím | Mức rung đổi theo lựa chọn; nhả rung nhẹ hơn hoặc tắt; Tắt/cài đặt rung khi chạm của Android tắt thì không rung; giữ phím không rung lặp | |
| 4.19.4 | Mở **Chỉnh phím** nếu có; kéo nút, đổi cỡ 70–140%, độ mờ 20–100%, ẩn/hiện nút rồi chọn Xong | Giá trị nằm trong khoảng; bố cục nằm trong tầm ngón cái, không che phần chơi; mã phím trong game không đổi | |
| 4.19.5 | Lưu bố cục theo hệ thống, ghi đè theo game; mở lại, thử preset Chuẩn/Gọn/Tay trái và **Về mặc định** | Bố cục theo phạm vi đã chọn được giữ; preset áp dụng được; khôi phục mặc định trả nút về vị trí gốc | |

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

## 6b. Kirikiri (visual novel .xp3) — thử bản pre-release 0.7.7 (chưa có bản stable)

Mốc mã nguồn: app 0.7.7 (`versionCode` 42), `configVersion` 36 trên main `85269ea` (ứng viên RC, tag hiện là pre-release). Đây là checklist chờ người thử; các ô KQ để trống không phải bằng chứng đã chạy.

Kirikiri nhúng sâu trong Monika (không phải app ngoài): màn chuẩn bị tự tải gói, menu Việt hóa, chạy ở tiến trình riêng.

**Trước khi test:** chuẩn bị file game .xp3 (hoặc thư mục có `startup.tjs` + `data.xp3`).

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 6b.1 | Máy arm64 (arm64-v8a): Thư viện → +Game → chọn file `.xp3` lần đầu | Có bước tải gói Kirikiri + thông báo tiến độ, rồi vào game | |
| 6b.2 | Máy 32-bit (armeabi-v7a): Bấm game Kirikiri | Hiện thông báo "Kirikiri chưa hỗ trợ máy 32-bit" (không tải gói arm64) | |
| 6b.3 | Vào game Kirikiri → chạm vào cảnh, kéo → có phản ứng (chữ hiện, menu, phím) | Không văng; nhân vật / nội dung hiện được | |
| 6b.4 | Game có tiếng (BGM, hiệu ứng) | Âm thanh phát bình thường | |
| 6b.5 | Lưu / Tải game trong menu game hoặc gọi menu Monika | Lưu được, tải đúng vị trí đã lưu, không mất tiến độ | |
| 6b.6 | Menu Monika (… phím dưới cùng) → Tua nhanh (giữ Ctrl để bỏ qua thoại đã đọc) | Game tua nhanh bỏ qua thoại | |
| 6b.7 | Menu Monika → Menu game | Menu Kirikiri hiện các tùy chọn (Lưu, Tải, Cấu hình, v.v.) | |
| 6b.8 | Thoát game (bấm menu, chọn Thoát hoặc bấm back) | Quay lại Thư viện, game vẫn có icon + tên | |
| 6b.9 | Thư mục có `<tên>.xp3`, `<tên>.exe` cùng tên và các tệp `patch*.xp3` → thêm thư mục vào Thư viện | Mở đúng `<tên>.xp3`; không chọn tệp `patch*.xp3` làm lối vào | |

## 6c. RPG Maker XP/VX/Ace (nhúng)

Config 36 (`systems.rgss`: `engine: "rgss"`, `allowExternalApp: true`) chọn **engine RGSS nhúng** của Monika. Gói `engines-rgss-6` hiện chỉ có bản `arm64-v8a`; máy hỗ trợ ABI này tải gói rồi chạy trong Monika. Máy không có gói phù hợp mới dùng JoiPlay dự phòng. Chưa có bằng chứng engine chạy trên máy thật **[CHƯA KIỂM]**.

| # | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|
| 6c.1 | Mở game RPG Maker XP/VX/Ace | Game vào được màn chơi, không văng | |
| 6c.2 | Dùng D-pad di chuyển nhân vật | Nhân vật di chuyển theo hướng bấm | |
| 6c.3 | Bấm A hoặc START để xác nhận trong game | Game nhận phím xác nhận | |
| 6c.4 | Bấm B hoặc SELECT để hủy hoặc mở menu trong game | Game nhận phím hủy/menu | |
| 6c.5 | Mở menu Monika | Có các mục “Chơi tiếp”, “Chạy nhanh (giữ Shift)”, “Độ mờ phím” và “Thoát game” | |
| 6c.6 | Menu Monika → “Chơi tiếp” | Trở lại màn chơi | |
| 6c.7 | Bấm nút Back để mở rồi đóng menu Monika | Back đổi trạng thái menu | |
| 6c.8 | Menu Monika → “Chạy nhanh (giữ Shift)” | Trạng thái chạy nhanh bật/tắt theo lựa chọn | |
| 6c.9 | Menu Monika → “Độ mờ phím” | Độ mờ phím thay đổi | |
| 6c.10 | Trong menu của game, lưu rồi tải lại | Game trở về vị trí đã lưu | |
| 6c.11 | Thử mở game trên máy Android 32-bit | Ghi lại nếu vào được hoặc thông báo lỗi; chạy trên máy 32-bit thật **[CHƯA KIỂM]** | |

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
