# Giảm 50% hạn mức: đúc các luồng lặp thành skill (04/10/2026)

**Kết luận:** gần như toàn bộ hạn mức đang mất vào việc **đọc lại ngữ cảnh dài** chứ không phải vào câu trả lời. Phiên PM hiện mang khoảng 600–740 nghìn token ngữ cảnh, và mỗi lần gọi công cụ lại đọc lại toàn bộ khối đó. Cách giảm mạnh nhất là chạy mỗi lượt kiểm hộp thư trong **phiên mới, gọn**, dựa vào skill và file trạng thái. Không kéo theo cuộc trò chuyện dài. Ước tính giảm khoảng 90% token mỗi lượt kiểm, vượt đích 50%.

## 1. Số đo thật (từ nhật ký phiên PM)

| Mục | Số |
|---|---|
| Ngữ cảnh mỗi lần gọi (cuối ngày 04/10) | ~600–740 nghìn token, phần lớn đọc từ cache |
| Lượt kiểm 12:01 | 26 lần gọi, ~15,5 triệu token đọc cache, ~19 nghìn token sinh ra |
| Lượt kiểm 14:31 | 22 lần gọi, ~15,1 triệu token đọc cache, ~28 nghìn token sinh ra |
| Tỉ lệ | Token sinh ra chưa tới 0,2% tổng token xử lý |

Nguồn: trường `usage` trong `~/.claude/projects/-home-user/<phiên>.jsonl`. Cùng cách đo này dùng để kiểm kết quả sau khi đổi.

## 2. Các luồng lặp và cách đúc

| # | Luồng lặp | Ai | Đúc thành | Tác động ước tính |
|---|---|---|---|---|
| S1 | **Kiểm hộp thư 30 phút/lần** (ls-remote, danh sách PR, CI, đọc tiền duyệt Luna, đọc câu trả lời của sếp, sửa KE-HOACH, cập nhật trang tiến độ, đặt lịch) | PM | Skill `pm-kiem-thu` + trigger **mở phiên mới mỗi lượt**. Trạng thái lưu ở repo (`docs/opus/pm/`), không trong scratchpad | Rất lớn (~90% mỗi lượt) |
| S2 | Việc lõi engine của Sonnet trong một phiên sống lâu | Sonnet | Mỗi việc lõi chạy 1 phiên Sonnet mới (PM tạo bằng `create_session`), dùng skill engine thay cho lịch sử trò chuyện | Lớn |
| S3 | `CLAUDE.md` ~3,3 nghìn token nạp vào **mọi** yêu cầu của mọi phiên | Tất cả | Rút `CLAUDE.md` xuống ≤ 1.200 token. Chuyển phần chi tiết sang skill: `phat-hanh-apk`, `giai-nen`, `j2me-loader`, `bao-loi-diag`, `them-he-may`, `dung-engine` | Trung bình, tác động lên mọi phiên |
| S4 | Lệnh dài viết tay mỗi lượt (python sửa bảng, gh api tóm tắt PR, gộp main kiểm xung đột) | PM | Script trong skill: `pr-tom-tat.sh`, `kh-set.py`, `bang-cap-nhat.py`, `gop-main.sh`, `val-goi-that.py` | Vừa (bớt token sinh ra + bớt lỗi) |
| S5 | Bản tin trạng thái | Sol | Đã có V39 (`bot/trang-thai`). PM đọc 1 file thay cho chục lần gọi | Vừa |
| S6 | Danh sách connector và skill không dùng cho lập trình bị chèn lại mỗi lần kết nối lại (ElevenLabs, Zoom, Notion, Canva, Booking…) | Sếp | Sếp tắt connector không dùng cho các phiên Monika | Vừa, cho mọi phiên |

## 3. Việc giao Sonnet: V46

Sonnet dựng các skill dưới `.claude/skills/` trên **main** (PR như thường lệ, Luna tiền duyệt, PM duyệt):

1. **`pm-kiem-thu`** (SKILL.md + `scripts/`). Từng bước của một lượt kiểm:
   - đọc `bot/trang-thai`;
   - đọc câu trả lời của sếp (ArtifactData `answers`);
   - tóm tắt PR;
   - duyệt theo luật Luna-trước;
   - sửa KE-HOACH;
   - cập nhật trang tiến độ (ArtifactData `board/state`);
   - ghi `docs/opus/pm/trang-thai-pm.json` lên nhánh `docs/opus-tra-loi`.

   Nguồn chất liệu: `docs/opus/pm/` trên nhánh `docs/opus-tra-loi` (công cụ hiện tại của PM), và prompt trigger hiện tại (bản sao ở mục 5).
2. **Skill cho Sonnet và đội:**
   - `phat-hanh-apk`: lấy từ mục "Phát hành APK" của CLAUDE.md, cộng `scripts/verify-apk-cert.py` và Pixeldrain;
   - `dung-engine`: build-engines, NDK, cách kiểm `needed.txt` và ELF, gói thật;
   - `them-he-may`: config + `GameLauncher` + `ConfigTest`;
   - `bao-loi-diag`: Diagnostics, `crash-reports.sh`;
   - `giai-nen`;
   - `j2me-loader`.
3. **Rút gọn `CLAUDE.md`:** giữ nguyên tắc bắt buộc và bảng chỉ đường tới skill. Không mất thông tin: mọi đoạn bị bỏ phải có trong một skill.
4. Mỗi skill có `description` ngắn, rõ khi nào dùng, để chỉ nạp khi cần.

**Đạt khi:**
- `CLAUDE.md` ≤ 1.200 token;
- 6 skill + `pm-kiem-thu` có trên main;
- một phiên PM mới chạy trọn một lượt kiểm chỉ bằng skill, ngữ cảnh mỗi lần gọi < 100 nghìn token (đo bằng `usage`).

**Luật:**
- Không đụng khóa, secret.
- Không đổi hành vi app.
- PR chỉ gồm `.claude/`, `CLAUDE.md`, `docs/`, `scripts/`.

## 4. Việc PM làm sau khi V46 gộp

- Đổi trigger kiểm thư sang chế độ **phiên mới mỗi lượt** (`create_new_session_on_fire`), prompt ngắn: "Dùng skill pm-kiem-thu".
- Đo lại sau một ngày bằng cùng cách ở mục 1. Đích: ≤ 7,5 triệu token mỗi lượt kiểm (−50%). Kỳ vọng: 1–2 triệu.
- Phiên trò chuyện chính với sếp chỉ dùng khi sếp nhắn.
- Việc lõi của Sonnet: mỗi việc 1 phiên mới.

## 5. Phụ lục: prompt trigger hiện tại (chất liệu cho skill pm-kiem-thu)

Xem `docs/opus/pm/trang-thai-pm.json` (trạng thái) và các bước trong `docs/opus/KE-HOACH.md` dòng "Phân vai". Bản đầy đủ của prompt nằm trong trigger `trig_01Nk2NQXsfxm8hQLAZAo4ZsS`. PM sẽ dán vào mô tả PR khi Sonnet cần.
