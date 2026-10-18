# Bàn giao PM tạm → PM chính, 12/10/2026 22:50 VN

PM tạm (phiên Opus thay thế, 09–12/10) trả quyền cho PM chính. Dán khối dưới vào phiên PM chính.

```
Bạn là PM chính (Opus) dự án Aow Monika (aowvn-10diem/aowvn-monika), vừa trở lại sau thời gian PM tạm điều hành. Đọc file này rồi tiếp quản ngay; không đọc lại toàn bộ lịch sử.

## Trạng thái lúc bàn giao (12/10 22:50 VN)
- main: 7357a3f (gộp #214). Bản thử mới nhất: v0.8.2 (tag 8690f03, run 38154846119, APK 19 186 125 byte, chứng thư khớp c46902e9…d45ab20c). Chưa có bản ổn định mới; phát hành ổn định cần sếp nói "cứ làm ổn định".
- Gộp sau v0.8.2 (ứng viên v0.8.3): #212 V93b (test chập chờn Robolectric), #200 V85c, #211, #125, #213 V52, #218 V95 (sửa test Ren'Py làm đỏ `pack`), #214 (github-script 7→9).
- configVersion 41 [CHƯA KIỂM có đổi sau đó không].
- Xác nhận chơi được trên máy thật: NDS, PSP, Java. GBA, 3DS và các hệ khác [CHƯA KIỂM]. Kirikiri vẫn thử nghiệm.

## PR đang mở
| PR | Việc | Tình trạng | Bước tiếp |
|---|---|---|---|
| #210 solh/V92 | Flow Maestro "Báo lỗi" phải mở Menu đang tự ẩn (A31) | head 0fb2d0a, Luna Ultra "Cần sửa", job `report` đỏ; Sol High chưa đẩy bản sửa từ 11/10 | Nhắc Sol High; đây là chỗ chặn chính (job `report` đỏ ở mọi PR) |
| #215 checkout 4→7 | dependabot | đỏ không do PR: `report` (lỗi #210); `pack` đã hết đỏ sau #218, cần chạy lại | Chờ #210 rồi chạy lại 1 lần |
| #216 Kotlin 2.0→2.4, #217 AGP 8.7→9.4 | dependabot | CI đỏ, nâng cấp lớn | HOÃN (PM tạm quyết), để mở |

## Quyết định đã chốt trong thời gian PM tạm
- A29 v0.8.0 lúc 08:00 · A30 NDS bố cục DraStic · A31 tự ẩn tên game/menu mọi giả lập · A32 v0.8.1 trước 18:00 · A33 bàn phím Monika luôn đục, khóa/ẩn thanh độ mờ · A34 v0.8.2 khi #205 gộp · A35 kênh lệnh duy nhất issue #119 (Nova thêm #90, Haiku-2 thêm #120), bản sao ở đầu docs/opus/KE-HOACH.md.
- A36 (sếp chốt 12/10): CHẾ ĐỘ TIẾT KIỆM TOKEN. Không mở việc mới; lượt định kỳ không có gì mới thì dừng, không comment; báo ≤ 3 dòng; CI chạy lại tối đa 1 lần.
- Sếp dừng cả đội 12:53–17:30 ngày 12/10, rồi cho làm tiếp (vẫn A36).

## Câu hỏi còn chờ sếp
- A37: ra v0.8.3 từ các PR đã gộp ở trên không? (cần PR nâng phiên bản + CHANGELOG, giao Nova).
- Phản hồi máy thật v0.8.2: Kara no Shoujo (XP3 nhúng trong exe), màn chờ NDS có các bước tiến độ, thử 3DS .3ds đã giải mã.

## Đội (A35: lệnh chỉ ở #119)
| Người | Nền | Việc hiện tại |
|---|---|---|
| Sol High | ChatGPT | #210 V92 |
| Sol | ChatGPT | xong V95 (#218), đang chờ |
| Nova | Claude | đã phân loại dependabot; chờ |
| Luna 3 → Luna Ultra | ChatGPT | duyệt hai lượt PR app/đường phát hành |
| Haiku-2 | Claude | tiền duyệt, lịch 3 giờ/lần |
Nhắc: Sol/Sol High/Luna là phiên ChatGPT, sếp phải tự gọi; PM chỉ đăng lệnh ở #119.

## Lịch tự chạy (routine) trên tài khoản sếp
| ID | Tên | Lịch | Trạng thái |
|---|---|---|---|
| trig_01TJvZPEawkeT7awsGW88XLc | PM Monika tiết kiệm (:12) | 2 giờ/lần 07–23h VN | TẮT lúc bàn giao (bắn vào phiên PM tạm) |
| trig_01U165ByF84kygj2UC7ZNHfr | PM Monika tăng tốc (:42) | mỗi giờ | TẮT (A36) |
| trig_01CKjE1xNGhUFsoE4meZvabB | PM Monika đêm (:27) | 23–05h | TẮT (A36) |
| trig_015yTEBk6RRHaXfTu2MjwCfF | Haiku-2 kiểm thư 3 giờ | 50 */3 UTC | BẬT |
| trig_01Qqki2Sce3eY12BmvNMQwEh | Review PR định kỳ | 40 */6 UTC | BẬT |
Ba lịch PM bắn vào phiên PM tạm (session_015T6ttKMeB6ssv57mzo4DH9); PM chính muốn nhận lịch thì tạo lịch mới cho phiên của mình, đừng bật lại ba lịch này.

## Luật gộp/phát hành (giữ nguyên)
- Gộp: Đạt đúng exact head + CI bắt buộc xanh; PR app/đường phát hành cần Luna 3 → Luna Ultra; PR docs/test-only 1 Đạt. Merge commit, SHA đủ 40 ký tự.
- Bản thử: release.yml dispatch sha=SHA main đầy đủ, prerelease=true; báo 1 dòng ở #119 trước; kiểm bằng skill kiem-phat-hanh.
- Công cụ: `bash .claude/skills/pm-kiem-thu/scripts/pr-duyet.sh 200` (nằm trên nhánh docs/opus-tra-loi).

## Lưu ý
- #218 được PM tạm gộp với 1 Đạt (Luna 3) vì chỉ sửa test trong scripts/ và đang chặn CI cả đội; Luna Ultra chưa duyệt lượt hai.
- Test Robolectric chập chờn (`LibraryStorageAccessTest.kt:72/76`, `V69UxTest.kt:84/88`): V93b (#212) đã gộp, độ ổn định [CHƯA KIỂM].
- Báo trạng thái kiểu "chưa thấy trên GitHub", không nói "không làm" (bài học Sol 11/10).
```
