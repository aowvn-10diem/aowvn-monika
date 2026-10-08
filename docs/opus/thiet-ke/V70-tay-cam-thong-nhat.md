# V70: Tay cầm và menu trong game thống nhất (sếp giao 09/10/2026)

**Mục tiêu:** mọi giả lập và engine (libretro GB/GBA/NES/SNES/MD/N64/PS/PSP/NDS/3DS/Dreamcast, Kirikiri, RPG Maker, Ren'Py, game Java) dùng **một bộ nút và một menu**: cùng hình khối, cùng cảm giác bấm, cùng thứ tự thao tác. Bấm phải "đã tay": nút lún xuống, có rung phản hồi, đặt đúng chỗ ngón cái, và người chơi tự chỉnh được.

Hiện trạng (main `9f35f8d`): `runner/GamePadOverlay.kt` (634 dòng: `VirtualPad`, `DPad`, `AnalogStick`, `RoundKey`, `PillKey`, `Movable`, `EditChip`) chỉ phục vụ libretro. `KirikiriOverlay.kt`, `RgssOverlay.kt`, `RenpyOverlay.kt`, `J2meMenu.kt` mỗi file tự vẽ nút và menu riêng. Rung chỉ có ở `RgssGameActivity` (theo yêu cầu của lõi mkxp-z), nút ảo không rung.

## 1. Bộ thành phần chung `ui/controls/` (V70a, Sol)
| Thành phần | Quy cách |
|---|---|
| `MonikaKey` (nút tròn: A/B/X/Y, △○□×) | Đường kính 64 dp (vùng chạm ≥ 72 dp); nền kính mờ + viền 1 dp; nút chính dùng `primaryGradient()` |
| `MonikaPill` (L/R/ZL/ZR/LT/RT, SELECT/START) | Cao 40 dp, bo `Radius` tối đa; vai trái và vai phải đối xứng theo mép màn |
| `MonikaDPad` | Hình chữ thập liền khối 150 dp, có vùng chéo (bấm góc = 2 hướng) |
| `MonikaStick` | Đế 148 dp, núm 64 dp, vùng chết 12 %, nhả ra thì trở về tâm có lò xo |
| **Hiệu ứng bấm** | Khi nhấn: thu nhỏ còn 0,92, dịch xuống 2 dp, bóng đổ giảm (cảm giác lún); khi nhả: bật lại. Thời lượng lấy từ `Monika.motion`; `PerformanceTier.OFF` chỉ đổi màu, không chạy hoạt ảnh |
| **Rung (haptic)** | Lúc nhấn: `performHapticFeedback(VIRTUAL_KEY)`, máy API 26+ có hỗ trợ biên độ thì dùng `VibrationEffect`. Lúc nhả: rung nhẹ hơn hoặc tắt. Không rung khi đang giữ phím lặp |
| Mức rung | Tắt / Nhẹ / Vừa / Mạnh, mặc định **Vừa**; tôn trọng cài đặt "rung khi chạm" của hệ thống |
| Trạng thái | Nhấn, giữ, turbo (viền chạy), tắt; mọi trạng thái đều có test ảnh |
| Trợ năng | `contentDescription` tiếng Việt theo vai trò (ví dụ "Nút A"); màu nút trên nền sáng đạt tương phản ≥ 3:1 |

Thay toàn bộ nút trong `GamePadOverlay.kt`, `KirikiriOverlay.kt`, `RgssOverlay.kt`, `RenpyOverlay.kt` bằng bộ này. Mã phím gửi xuống lõi giữ nguyên, không đổi giao thức.

## 2. Menu trong game thống nhất (V70b, Luna)
Một thành phần `GameQuickMenu` dùng chung cho mọi engine. Thứ tự cố định; engine nào không hỗ trợ mục nào thì ẩn mục đó, không đổi thứ tự:

1. Chơi tiếp · 2. Lưu nhanh / Tải nhanh (ô 1–3) · 3. Tốc độ (tua nhanh) · 4. Chỉnh phím (mở trình sửa bố cục V70c) · 5. Tùy chọn giả lập · 6. Rung và độ mờ phím · 7. Báo lỗi game này · 8. Thoát game

Nhãn ngắn, tối đa 2 dòng, không cắt chữ. Riêng game Java (`J2meMenu.kt`) giữ bàn phím số nhưng menu dùng chung thành phần này.

## 3. Tự chỉnh bố cục (V70c, Sol, sau V70a)
- Chế độ sửa: kéo từng nút, chỉnh cỡ (70–140 %), độ mờ (20–100 %), ẩn hoặc hiện từng nút.
- Lưu bố cục **theo hệ máy** (mặc định) và có thể ghi đè **theo game**. Có nút "Về mặc định".
- Bộ mẫu dựng sẵn: Chuẩn, Gọn (màn nhỏ), Tay trái. Bố cục mặc định của từng hệ máy nằm trong `config/monika-config.json` (config-first: thêm trường mới có giá trị mặc định, tăng `configVersion`).
- Lưu bằng DataStore; xuất/nhập bố cục dạng chuỗi JSON để chia sẻ trong cộng đồng AowVN.

## 4. Cài đặt mới (V70a)
Mục "Tay cầm ảo" trong Cài đặt gồm: mức rung, hiệu ứng bấm (bật/tắt), cỡ nút chung, độ mờ chung, hiện nhãn phím, nút về mặc định.

## 5. Kiểm thử và duyệt
- Test Robolectric: ảnh cả 9 tay cầm ở 2 hướng màn hình (dọc, ngang) và trạng thái nhấn; unit test cho bản đồ phím (không đổi mã phím), mức rung, lưu/đọc bố cục.
- Test Lab Robo chạy lại sau khi gộp. Cảm giác rung chỉ kiểm được trên máy thật: sếp thử theo checklist mới trong `docs/TEST-MAY-THAT.md` (Luna bổ sung).
- Luna duyệt PR của Sol; Luna Ultra duyệt PR của Luna và rà UX lần hai (vùng chạm, tương phản, nhãn).

## 6. Phạm vi theo bản phát hành [A16]
| Phần | Bản | Hạn (giờ VN) |
|---|---|---|
| V70a bộ nút chung + hiệu ứng bấm + rung + cài đặt | 0.7.7 ổn định nếu xanh trước hạn; trễ thì 0.7.8 | 10/10 20:00 |
| V70b menu thống nhất | 0.7.7 ổn định nếu xanh trước hạn | 10/10 20:00 |
| V70c tự chỉnh bố cục + mẫu dựng sẵn | 0.7.8 (sau bản ổn định), được làm song song | 12/10 20:00 |

Lý do: thay toàn bộ nút ngay trước bản ổn định có rủi ro. Phần nào chưa xanh trước 10/10 20:00 thì chuyển sang 0.7.8, không giữ bản ổn định lại vì nó.
