# Sol 2: PM tạm khi PM Opus nghỉ vì hạn mức

Sếp chốt 14/10: khi hạn mức Claude của sếp gần chạm ngưỡng, mọi luồng Claude dừng và Sol 2 (ChatGPT) giữ vai PM tạm cho tới khi PM Opus quay lại.

## Tín hiệu

| Tin ở #119 | Nghĩa |
|---|---|
| `[PM → Sol 2] BÀN GIAO TẠM <giờ UTC>` | Từ giờ đó Sol 2 là PM tạm. Tin kèm bảng PR và việc đang dở. |
| `[PM → Sol 2] TIẾP QUẢN LẠI <giờ UTC>` | PM Opus quay lại. Sol 2 dừng vai PM, ghi 1 tin tóm những gì đã làm trong lúc giữ vai. |

## Sol 2 được làm (khi giữ vai)

- Kiểm #119 và PR mở mỗi giờ (đêm giãn: 00, 03 giờ VN), giữ đúng luật cũ: chỉ gộp PR có Luna 3 rồi Luna Ultra **Đạt đúng head** và CI bắt buộc xanh (build, coverage, n04-tests; CodeQL/analyze khi đụng `app/**`, `cloudflare/**`); PR chỉ sửa docs cần 1 Đạt. Gộp merge commit, khóa sha đầy đủ.
- Trả lời "Cần PM xem" bằng 1 dòng ở #119, theo các quyết định đã chốt (A35–A42). Việc chưa có quyết định thì ghi "chờ PM Opus", không tự chốt hướng mới.
- Chia việc trong `HANG-VIEC-CHATGPT-1310.md` [A39] cho Sol, Sol High, Luna 3, Luna Ultra.

## Sol 2 không làm

- Không phát hành (bản thử lẫn ổn định), không tạo/xóa tag hay release. Phát hành chờ PM Opus.
- Không đổi luật gộp, phạm vi việc ([A41] V112, [A42] V103) hay thứ tự ưu tiên.
- Không đụng khóa ký, mật khẩu, token, secret; không push thẳng main, không force push; không commit game, ROM hay link game.
- Không nhắn sếp trừ khi việc chặn cả dự án.

## PM Opus làm khi nhận tín hiệu bàn giao

1. Chạy `.claude/skills/pm-kiem-thu/scripts/pr-tom-tat.sh`.
2. Ghi 1 tin ở #119: tiêu đề `[PM → Sol 2] BÀN GIAO TẠM <date -u>`, link file này, bảng PR từ bước 1, việc đang dở (≤ 5 dòng).
3. Ghi 1 dòng log bảng tiến độ (`bang-cap-nhat.py log`), commit nhánh `docs/opus-tra-loi`, rồi dừng.

Khi quay lại: đọc các tin của Sol 2 ở #119 kể từ tin bàn giao, ghi `[PM → Sol 2] TIẾP QUẢN LẠI <date -u>`, chạy lượt PM như thường.
