# Bàn giao PM sang Claude Project (13/10/2026)

Từ phiên PM cũ session_01EAtBeDqHPJ2AkJq2j1jYMC sang Project "Aow Monika". Đọc file này trước, sau đó chỉ đọc thêm khi cần.

## 0. Sếp làm (một lần)

1. Ở thanh bên claude.ai/code, mở menu của phiên PM cũ → **Continue as project**. Nếu không thấy mục **Projects** thì tài khoản chưa được mở tính năng; giữ phiên cũ.
2. Trong **Setup recommendations**: **chỉ giữ repo `aowvn-10diem/aowvn-monika`**, tắt `aowvn-monika-packs`. Lý do: chỉ Project có 1 repo mới áp luật quyền trong `.claude/settings.json`, gồm luật cho phép gộp PR và chạy phát hành mà sếp dán lúc 13/10 (b952a17). Khi cần repo packs thì mở thread riêng.
3. Tắt routine gợi ý trùng với lịch PM cũ, nếu có. PM mới tự tạo lịch (mục 3).
4. Dán khối "Project instructions" ở cuối file vào **Project settings → Memory → Project instructions**.

## 1. Trạng thái lúc bàn giao (10:30 UTC 13/10)

| Mục | Trạng thái |
|---|---|
| main | `64e693d` (#222 đã gộp). v0.8.3 = versionCode 48 |
| v0.8.3 | Bản thử đã phát hành 03:57Z (run 38343249083, APK SHA-256 `cc32478c…a4860e`, chứng thư khớp). **Chưa lên ổn định**: mới 1 lượt tải (của PM kiểm), chưa có kết quả máy thật |
| PR đủ luật chờ gộp | #224 #225 #226 #227 #228 #229 #230 #231 #232. Phiên cũ bị chặn vì có 2 repo; PM mới gộp ngay khi `luot-pm.sh` in `GOP` |
| Crashlog | 163 báo cáo, 53% là `?\|lowmem\|?` vô dụng. V112 (Sonnet) đang làm; test Robolectric của V112 đã gửi 30 báo cáo giả `op-fail tai-feed` lúc ~10:00Z, đã nhắn Sonnet sửa |
| Lỗi thật mới nhất | 3DS `libhook_impl.so+0x1ca90` SIGSEGV 0x68 trên nubia SM8550, bản **0.8.2**. Đúng lỗi V98 đã sửa trong 0.8.3 |
| Bảng tiến độ | https://claude.ai/artifact/7UC9kqrWqv3GNUhhtt4z5d, `board/state` version 150; nguồn `docs/opus/pm/bang-tien-do/state.json` |

## 2. Luật và quyết định còn hiệu lực

- **A35:** kênh lệnh duy nhất là issue #119; Nova dùng #90, Haiku-2 dùng #120.
- **A36:** tiết kiệm token, làm nhiều nói ít. Lượt không có gì mới thì dừng ngay.
- **A38:** PM tự phát hành, cả bản thử lẫn ổn định, không hỏi sếp, miễn đủ cổng.
- **A39:** đội ChatGPT chạy tối đa: mỗi người tự kéo việc kế tiếp trong hàng `HANG-VIEC-CHATGPT-1310.md`, review trong vòng 1 giờ.
- **A40:** tự động hóa. Mỗi lượt chạy `luot-pm.sh`, làm theo nhãn GOP/PMXEM/TAU/LOI. Nhịp tối thiểu 2 ngày 1 bản. Chỉ ping sếp khi chặn cả dự án. Chi tiết ở `QUY-TRINH-TU-DONG-1310.md`.
- **Luật gộp:** Đạt đúng head + CI bắt buộc xanh (build, coverage, n04-tests; CodeQL/analyze khi PR đụng `app/**`, `cloudflare/**`). PR app/CI cần Luna 3 rồi Luna Ultra; PR chỉ sửa docs cần 1 Đạt. Gộp bằng merge commit, khóa sha đầy đủ.
- **Cấm:** đụng/in khóa ký, mật khẩu, token, secret; push thẳng main, force push, xóa tag/release; PM tự viết mã app; commit game, ROM hay link game vào repo công khai (kể cả issue/PR/log). Bị bộ lọc an toàn chặn thì không vòng qua, báo sếp.

## 3. Việc đầu tiên của PM mới

1. `git checkout docs/opus-tra-loi && .claude/skills/pm-kiem-thu/scripts/luot-pm.sh`, rồi gộp các dòng `GOP`.
2. Tắt lịch của phiên cũ: `update_trigger trig_01C4mQ3t77Y28a4Bks4G2WTU enabled=false`. Tạo routine mới trong Project: mỗi giờ phút :12, 07–23 giờ VN, prompt "Lượt PM tự động [A40]…" (lấy từ prompt cũ).
3. Ghi 1 dòng ở #119: "PM chuyển sang Project từ <giờ>".
4. Sau khi gộp, sửa `luot-pm.sh` bỏ qua báo cáo `robolectric` trong mục LOI.

## 4. Đội

| Ai | Kênh | Việc đang có |
|---|---|---|
| Sol (ChatGPT) | #119 | V100 #229, V101 #231, V102 #232 (đủ luật) → V113, V114 |
| Sol High (ChatGPT) | #119 | V104 #224, V105 #228, V106 #230 (đủ luật) → V107 sau v0.8.3 ổn định |
| Luna 3 / Luna Ultra (ChatGPT) | #119 | duyệt; V108/V109; V110/V111 → V115 |
| Sonnet (Claude) | session_01HaU9f6SbsYY2fjxpeYbEs1 | V112 crashlog mọi thao tác |
| Nova (Claude) | #90 | duyệt docs |
| Haiku-2 (Claude) | #120, trig_015yTEBk6RRHaXfTu2MjwCfF mỗi 3 giờ | test độ phủ |

Đội ChatGPT cần sếp đánh thức bằng tay; Project không điều khiển được họ.

## 5. Chờ sếp (không gấp, ghi trên bảng)

- Thử v0.8.3 trên máy thật (3DS, Kara no Shoujo, NDS). Cần cho việc lên ổn định.
- Secret Actions `CRASH_ADMIN_TOKEN`, để V100/V103 chạy trong CI.
- Secret `TEST_3DS_URL` cho V106b.
- Environment + `VERSION_PR_TOKEN` nếu muốn bật V102 `create_pr=true`.
- Link mời Discord AowVN cho `TEST-THUC-TE.md`.

---

## Project instructions (sếp dán vào Project settings → Memory → Project instructions)

```
Bạn là PM (điều phối) dự án Aow Monika, repo aowvn-10diem/aowvn-monika. Gọi chủ dự án là "sếp", trả lời tiếng Việt, kết luận trước, ngắn, dùng bảng; quyết định chốt gắn nhãn [A41], [A42]… (đã có tới A40).

Khởi động: đọc docs/opus/hop-thu/BAN-GIAO-PROJECT-1310.md trên nhánh docs/opus-tra-loi, rồi CLAUDE.md của repo. Nhánh làm việc của PM: docs/opus-tra-loi.

Chế độ [A40] tự động: mỗi lượt chạy .claude/skills/pm-kiem-thu/scripts/luot-pm.sh; in YEN thì dừng ngay, không ghi, không báo. Làm theo nhãn: GOP (gộp đúng sha), PMXEM (chốt 1 dòng ở #119), TAU (cắt bản thử / lên ổn định theo docs/opus/hop-thu/QUY-TRINH-TU-DONG-1310.md, kiểm bằng skill kiem-phat-hanh), LOI (chuyển chữ ký lỗi cho người phụ trách). Nhịp tối thiểu 2 ngày 1 bản. Cập nhật bảng tiến độ https://claude.ai/artifact/7UC9kqrWqv3GNUhhtt4z5d khi có thay đổi. Đọc câu trả lời của sếp ở ArtifactData collection answers.

PM không tự viết mã app/engine: giao Sol, Sol High (ChatGPT, qua issue #119) hoặc mở thread Sonnet trong Project. Thread viết mã phải chạy ./gradlew testDebugUnitTest trước khi push, mở PR, chờ Luna 3 → Luna Ultra duyệt.

Chỉ nhắn sếp khi việc chặn cả dự án. Tiết kiệm token: đọc rẻ trước (script, grep), không đọc lại thứ đã chốt, báo cáo gộp ≤ 5 dòng.

Cấm: đụng hay in khóa ký, mật khẩu, token, secret; push thẳng main, force push, xóa tag/release; commit game, ROM hay link game vào repo công khai. Bị bộ lọc an toàn chặn thì không vòng qua, báo sếp. Số liệu phải có bằng chứng, chưa kiểm ghi [CHƯA KIỂM]; giờ lấy từ date -u.
```
