# Bộ asset giao diện AowVN (sếp gửi 10/10/2026) — V85

Nguồn: 9 ảnh "sprite sheet" sếp gửi PM ngày 10/10/2026, phong cách retro pixel 16-bit, thương hiệu Aow.vn. Đây là **ảnh tham chiếu thiết kế** (chữ, nhãn, logo đã in sẵn trên ảnh), **không** cắt ra dùng thẳng trong app. App vẽ lại bằng Compose theo token rút ra dưới đây.

| Tệp | Nội dung | Dùng cho Monika |
|---|---|---|
| `01-chien-dau.png` | Nút lệnh chiến đấu, chọn mục tiêu, thanh HP/MP, khung hiệu ứng, kết quả trận, điều hướng | Tham khảo kiểu nút, mũi tên điều hướng |
| `02-he-thong-luu-cai-dat.png` | Lưu/Tải/Cài đặt/Menu/Hệ thống (4 trạng thái), ô lưu game, công tắc, thanh trượt âm thanh/đồ họa, tab cài đặt | Menu trong game (lưu/tải nhanh), màn Cài đặt, ô save |
| `03-hud-thanh-chi-so.png` | Thanh HP/MP/năng lượng/kinh nghiệm, thanh tải/nạp, đồng hồ tròn, pin, chip trạng thái, bộ đếm | Thanh tiến độ tải gói/game, chip trạng thái |
| `04-tay-cam.png` | **Nút tay cầm** Xbox/PlayStation/Switch/PC, D-pad, stick, nút hành động, tổ hợp nút, nút đặc biệt (tạm dừng, tua nhanh, toàn màn hình…) | **Bộ nút ảo của mọi giả lập (V85)** |
| `05-tien-ich-icon.png` | Nút chức năng lớn/nhỏ, tab, trạng thái nút, checkbox/radio/công tắc, thanh menu, phân trang, khung/viền, logo | Nút chung toàn app, thanh tab |
| `06-con-tro-ban-do.png` | Con trỏ, khung chọn, chỉ dẫn, bản đồ nhỏ, hiện/ẩn | Khung chọn (focus) khi dùng tay cầm thật |
| `07-tui-do-cua-hang.png` | Tab danh mục, ô vật phẩm, khung chọn/trạng thái, dòng danh sách, cửa hàng, bộ lọc | Lưới game trong Thư viện, bộ lọc |
| `08-hop-thoai.png` | Hộp thoại thông báo/cảnh báo/xác nhận/thành công, toast, hộp lựa chọn, bóng thoại | Hộp thoại + toast của app (V79, V81, V82) |
| `09-nut-chung.png` | Nút hành động lớn/trung/pill/compact, 4 trạng thái, toggle, segmented, bộ icon | **Chuẩn trạng thái nút** |

## Token rút ra (đo từ ảnh, sai số ± vài đơn vị màu) [đo bằng PIL trên `09-nut-chung.png`, `04-tay-cam.png`]

| Token đề xuất | Màu | Đo ở |
|---|---|---|
| `aow.orange` (nền nút chính) | `#FD4C0F` | Nút "BẮT ĐẦU" hàng THƯỜNG |
| `aow.orangeLight` (dải sáng mép trên) | `#FD4E14` → `#FC5217` | Mép trên nút cam |
| `aow.orangeSelected` | `#EA2D01` | Hàng CHỌN |
| `aow.orangePressed` | `#D92501` | Hàng NHẤN |
| `aow.dark` (nền nút tối) | `#171919` – `#1B1D1E` | Nút "TIẾP TỤC" |
| `aow.darkPressed` | `#8E0F01` (đỏ sẫm) | Nút tối hàng NHẤN |
| `aow.disabled` / `aow.disabledDark` | `#79797A` / `#575859` | Hàng VÔ HIỆU |
| `aow.outline` | `#000000`, viền pixel dày ~3 px ở 1448 px ngang | Mọi nút |
| `aow.focus` (viền chọn) | `#FFF61F` – `#FCD228` | Hàng CHỌN (viền vàng) |
| `aow.padSurface` (D-pad, stick) | `#282C2F` | `04-tay-cam.png` mục 2 |
| Nhãn Xbox A / B / X / Y | `#6CE12C` / `#FF362A` / `#1C97FB` / `#F8DA03` | `04-tay-cam.png` mục 1 |
| Nền trang tham chiếu | `#FEFEFA` (không dùng trong app tối) | — |

Hình khối: góc "cắt pixel" (bo vuông bậc, không bo tròn mịn), viền đen dày, bóng đổ cứng 1 bậc phía dưới, chữ IN HOA đậm kiểu pixel, mũi tên `>` ở cuối nút hành động. Bốn trạng thái bắt buộc: **thường / chọn (viền vàng) / nhấn (sẫm hơn, lún) / vô hiệu (xám)**.

## V85 — Bộ nút ảo mọi giả lập theo asset chung (giao Sol, Luna 3 → Luna Ultra)

1. Thêm nhóm token trên vào `Monika.colors` (không hard-code màu trong màn hình), kèm hình khối "pixel" vào `ui/theme/Components.kt` (một `Shape` góc bậc + viền + bóng cứng).
2. Bộ nút ảo dùng chung (V70a) vẽ lại theo `04-tay-cam.png` + `09-nut-chung.png`: D-pad tối `aow.padSurface` viền đen; A/B/X/Y tròn tối, chữ màu theo hệ (mặc định kiểu Switch chữ trắng; tùy chọn màu kiểu Xbox); L/R/ZL/ZR dạng phím vai tối; SELECT/START/Menu dạng pill tối; trạng thái nhấn theo `aow.orangePressed`/`aow.darkPressed` + lún V70a (giữ rung).
3. Áp cho **mọi giả lập**: libretro (`InGameOverlay`), Kirikiri, RPG Maker, Ren'Py, J2ME (bàn phím ảo J2ME đổi màu/khung theo token, giữ bố cục phím). Thứ tự hiển thị và mã phím không đổi.
4. Tương phản: chữ/biểu tượng trên nút đạt tối thiểu 4.5:1 so với nền nút, và nút đọc được trên nền game sáng lẫn tối (giữ yêu cầu V75).
5. Test ảnh Robolectric cho từng giả lập (trước/sau), `./gradlew testDebugUnitTest`.

Ngoài phạm vi V85 (làm sau khi sếp duyệt): hộp thoại/toast theo `08-hop-thoai.png`, màn Cài đặt theo `02`, lưới Thư viện theo `07`.
