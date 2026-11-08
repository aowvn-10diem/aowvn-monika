# Backlog theo nhu cầu người dùng (BA, lượt đầu 04/11/2026)

Người viết: Nova (BA). Việc: [A82] mục 2b. Chỉ tài liệu; **không** giao việc, PM chọn và giao.

## Nguồn và giới hạn (đọc trước)

| Nguồn | Đọc được gì | Giới hạn |
|---|---|---|
| Issue nhãn `crash-moi` (bot gom từ Worker) | 84 issue mở lúc đọc (04/11); phần lớn 60 mục cập nhật gần nhất là `crash-moi` | "Tần suất" là số lần bot đã thông báo, **không phải số người**. Bot chỉ đọc 100 báo cáo mới nhất của Worker ([CRASH-ISSUE-BOT.md](../CRASH-ISSUE-BOT.md)) |
| [docs/ds/tong-hop-crash.md](../ds/tong-hop-crash.md) | Bảng 38 chữ ký ngày 30/10: theo lõi, theo loại, tần suất | Số liệu 30/10; từ đó có thêm issue mới (nêu từng mục dưới) |
| Issue do người dùng tự viết | **Không có** (ngoài hộp thư nội bộ và `kiem-dinh-ky`) | [CHƯA KIỂM] nhu cầu người dùng nói thẳng bằng lời |
| Góp ý trên aow.vn | Nguồn Blogger đọc được chỉ có 38 bình luận từ 2020–2021, không nhắc Monika | [CHƯA KIỂM] góp ý thật có thể nằm ở Discord, Facebook, diễn đàn: BA chưa đọc được |

Vì vậy cả 5 việc dưới đây dựa trên **bằng chứng gián tiếp** (báo lỗi tự động), chưa phải lời người chơi.

## 5 việc người dùng cần nhất

### 1. App không bị đứng/treo khi dùng
- **Vấn đề:** app không trả lời, người chơi phải tắt rồi mở lại (loại `anr`).
- **Bằng chứng:** 30/10: 7 chữ ký, 16 lượt (DS-04). Từ 31/10: thêm 12 issue `anr` mở mới: #398, #399, #400, #402, #413, #414, #415, #419, #439, #440, #459, #460 (nhiều cái chưa rõ lõi, chạm nhiều hệ).
- **Tiêu chí đạt:** (a) sau bản có sửa, 7 ngày liền bot không mở issue `anr` mới; (b) mở Thư viện và vào 1 game mỗi hệ (NDS, GBA, SNES, 3DS) không có khung hình đứng quá 5 giây trên máy thật [CHƯA KIỂM: chưa có máy thật chạy bài này].
- **Bản:** cả hai.

### 2. Máy yếu không bị tắt app giữa chừng
- **Vấn đề:** hết bộ nhớ, hệ thống tắt app khi đang chơi (loại `lowmem`, `killed`).
- **Bằng chứng:** 30/10: `lowmem` 3 chữ ký, 2.050 lượt (#304 đúng 2.048 = 2^11, có thể là **trần đếm của bot** [CHƯA KIỂM]); `killed` 7 chữ ký, 45 lượt (#306, #308…). Từ 31/10 thêm: #447, #484, #485, #392 (killed), #483, #386 (lowmem).
- **Tiêu chí đạt:** (a) trên máy RAM thấp, vào game nặng (PSP/3DS mẫu) app báo "máy yếu" và dùng thiết lập nhẹ thay vì bị tắt; (b) không có issue `lowmem`/`killed` mới trong 7 ngày sau bản sửa. Ngưỡng RAM [CHƯA KIỂM: PM/DeepSeek chốt].
- **Bản:** cả hai.

### 3. Tải hoặc nhập game thất bại thì báo rõ và cho thử lại
- **Vấn đề:** thao tác tải/nhập không xong mà người chơi không biết vì sao (loại `op-fail`).
- **Bằng chứng:** 30/10: 6 chữ ký, 495 lượt (#305 một mình 490). Từ 31/10: #393, #395, #401, #407, #464, #465. Bản 0.8.9 đã chặn báo lỗi nhầm khi mạng ngắt (#425), nên số liệu sau 02/11 phải đọc lại [CHƯA KIỂM]. Thao tác nào gây `op-fail` [CHƯA KIỂM: bot không ghi].
- **Tiêu chí đạt:** với 3 nguyên nhân (mất mạng, thiếu dung lượng, tệp hỏng), app hiện thông báo tiếng Việt nói đúng nguyên nhân kèm nút **Thử lại**; kiểm bằng test giao diện, mỗi nguyên nhân 1 ca.
- **Bản:** phần nhập tệp: cả hai; phần tải game: chỉ bản GitHub (Play không có tải game).

### 4. Game nặng (3DS, PSP) chạy không văng
- **Vấn đề:** game 3DS/PSP thoát đột ngột hoặc báo lỗi lõi khi đang chơi.
- **Bằng chứng:** 30/10: 3DS (`citra`) 6 chữ ký, 82 lượt (#301 56 lượt, #308 16); PSP (`ppsspp`) 5 chữ ký, 101 lượt (#372 90 lượt). Từ 31/10: #404, #458, #460, #485 (3DS); #386, #416, #478 (PSP).
- **Tiêu chí đạt:** bài thử máy thật (Test Lab ARM hoặc máy thử của đội) mở 3 game 3DS và 3 game PSP mẫu, mỗi game chạy 10 phút không văng; kết quả ghi vào báo cáo. [CHƯA KIỂM: hiện Test Lab chưa chạy bài trên máy ARM, chỉ dựng bộ thử.]
- **Bản:** cả hai.

### 5. Game Nintendo DS chạy ổn định
- **Vấn đề:** game NDS thoát đột ngột, treo, hoặc app bị tắt (hai lõi `melondsds` và `desmume`).
- **Bằng chứng:** 30/10: `melondsds` 8 chữ ký/32 lượt, `desmume` 7 chữ ký/18 lượt. Từ 31/10: #397, #439, #440, #447, #482 (melondsds); #400 (desmume). Nhiều chữ ký nhưng ít lượt mỗi chữ ký: nguyên nhân chung hay riêng từng máy [CHƯA KIỂM].
- **Tiêu chí đạt:** bài thử máy thật mở 3 game NDS mẫu mỗi game 10 phút không văng; số chữ ký NDS mở mới trong 7 ngày sau bản sửa giảm so với 7 ngày trước.
- **Bản:** cả hai.

*Việc 1, 2, 4, 5 chồng nhau một phần (cùng gốc là ổn định và bộ nhớ). PM có thể gộp khi giao; việc ≥ 3 PR cần đặc tả theo [docs/dac-ta/MAU.md](../dac-ta/MAU.md) trước.*

## Việc cần sếp quyết (qua PM)

- **Kênh nghe người chơi:** hiện BA chỉ thấy báo lỗi tự động. Muốn xếp việc theo nhu cầu thật, cần một nguồn góp ý đọc được (xuất bình luận Discord/Facebook/diễn đàn theo tuần, hoặc biểu mẫu góp ý trong app). Sếp chọn kênh nào [CHƯA KIỂM: kênh nào đông người chơi nhất].
