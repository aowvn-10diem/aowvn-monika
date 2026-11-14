# Nhóm `melondsds / killed`: vì sao vẫn bị tắt, và đã sửa gì

Ngày 13/11/2026 · nhánh `loi/melondsds-killed` · nền `main` `f24ece45`
(mốc trong phiếu việc ghi `c3a8a9c4`; `main` đã tiến thêm do #615 gộp trước khi tạo nhánh.)

## 1. Hiện trạng nhóm

Không còn issue `melondsds / killed` nào đang mở. Toàn bộ đã đóng, phần lớn ngày 08/11, #306 đóng 10/11 với bình luận chuyển theo dõi sang #549:

| Issue | Lượt (tại 08/11) | Đóng |
|---|---:|---|
| #306 | 405 | 10/11 → #549 |
| #447 | 176 | 08/11 |
| #317 | 148 | 08/11 |
| #532 | 29 | 08/11 |
| #526 | 16 | 08/11 |
| #524 | 11 | 08/11 |
| #349 | 2 | 08/11 |
| #519 | 1 | 08/11 |

(`lowmem` cùng lõi: #335 89 lượt, #566 — cũng đã đóng.)

Lưu ý khi đọc số: `Số lần quan sát` là **cộng dồn lịch sử**, không phải mức tăng hiện tại — xem `docs/loi/lowmem-1311.md` (bot cộng dồn và vẫn cập nhật issue đã đóng).

## 2. Vì sao nhóm này không sửa được bằng cách nhả bộ đệm tiến trình chính

- `RetroActivity` (mọi lõi libretro, gồm `melondsds`) chạy ở **tiến trình riêng `:game`** (`app/src/github/AndroidManifest.xml:82`, `app/src/play/AndroidManifest.xml:55`) — tách khỏi tiến trình chính.
- Vì vậy việc #574 giải phóng `Coil` memory cache / feed / AdBlock ở tiến trình chính **không thể** làm giảm `melondsds/killed` (khác tiến trình, khác vòng đời).
- Trong `:game` khi người chơi chuyển sang app khác, phần RAM lớn còn giữ là chính lõi giả lập: ROM + JIT + texture/FBO, cộng EGL context (`preserveEGLContextOnPause = true`, `libretrodroid/.../GLRetroView.kt:111`). Đây là bản chất của trình giả lập: nhả hết thì mất trạng thái đang chơi.
- Muốn nhả thật khi vào nền phải: chụp state (có hạn 2 s, có thể rỗng) → `retro_unload_game`/`retro_deinit` → nạp lại + `unserialize` khi quay lại. Đổi lại: resume chậm, rủi ro mất tiến trình chơi nếu chụp state hụt. **Không làm** trong lượt này (không có bằng chứng người chơi bị tắt *khi đang chơi*).

## 3. Bằng chứng chất lượng dữ liệu

- `docs/loi/TON-0811.md` (PR #542, chưa gộp) ghi: trong một cửa sổ V174, **75/78 lượt `melondsds/killed` mang chứng thư `1e08a903aef9`**, khác chứng thư phát hành `c46902e97029` ⇒ phần lớn là bản mod/đóng gói lại, không phải bản chính thức. Đánh giá này ghi `[CHƯA KIỂM]` trong tài liệu gốc.
- `scripts/crash-insight.py` **mặc định loại** báo cáo mang chứng thư khác bản phát hành (`partition_by_cert`, `:310,334`); muốn gộp cả bản mod/đóng gói lại thì truyền `--all-certs` (`:342`).

## 4. Lỗ hổng đã sửa: không phân biệt "bị tắt khi đang chơi" và "bị tắt khi ở nền"

Trước lượt này, `RetroActivity` chỉ ghi các stage `core-ready` / `loading-game` / `view-created` / `first-frame` / `core-error-N` — **không có stage nào cho lúc đã vào nền**. Vì vậy mọi báo cáo `killed`/`lowmem` của phiên game đều hiện tiêu đề "khi đang chơi", kể cả khi người chơi đã chuyển sang app khác và Android thu hồi tiến trình nặng — đúng ca thường gặp nhất. Không có cách nào tách nhóm bằng dữ liệu.

Đã sửa:
- `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt` — `onPause()` ghi `Diagnostics.stage(..., "paused")` (sau `saveSram()`), `onResume()` ghi `"playing"` khi đã qua khung đầu.
- `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt` — tách `phaseLabel(stage)`; `"paused"` → "khi đang ở nền", `"first-frame"/"playing"` → "khi đang chơi", còn lại → "lúc đang khởi động".

Kết quả: từ bản có bản sửa, tiêu đề báo cáo nói rõ `Game bị hệ thống tắt khi đang ở nền — …`, nên lần sau chỉ cần lọc theo câu đó là biết nhóm còn lại là ca thật.

Test: `:app:testGithubDebugUnitTest --tests vn.aow.monika.DiagnosticsTest` — **15/15 PASS**, thêm `deadSessionInBackgroundSaysOnBackground` và `phaseLabelDistinguishesPlayingPausedAndLoading`.

## 5. Còn lại / cần kiểm

- **[CHƯA KIỂM]** tỉ lệ bị tắt **khi đang chơi** trên máy ≥ 6 GB sau 0.8.10: cần `CRASH_ADMIN_TOKEN` để đọc `stage`/`env` của 100 báo cáo gần nhất (`scripts/crash-reports.sh <id>`); trước bản có bản sửa, mọi báo cáo đều ghi "đang chơi" nên **không** dùng được để kết luận.
- Đo trên máy thật: chơi NDS → Home (giữ nguyên game) → `adb shell dumpsys meminfo <pid của :game>` xem `TOTAL PSS`; nếu > 400 MB khi đã ở nền thì đáng xem lại cấu hình lõi (`melonds_*` trong `config/monika-config.json`) thay vì nhả core.
- Không đóng/mở lại issue nào; theo dõi tại #549.

## 6. Ghi chú phạm vi

Không sửa `AzaharActivity`, `GLRetroView`/`EmulationCall` (PR #619), các tệp của #574 (`FeedRepository`, `WebGameActivity`, `AppTrimMemoryTest`). Không đụng `config/monika-config.json`.
