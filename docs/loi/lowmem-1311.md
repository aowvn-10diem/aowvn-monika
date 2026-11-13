# Nhóm `unknown / lowmem`: dữ liệu cũ hay vẫn tăng?

Ngày 13/11/2026 · nhánh `loi/lowmem-1311` · nền `main` `c3a8a9c4`

## 1. Kết luận ngắn

**Gom nhầm: không. Đếm sai: không. "Vẫn còn" = cộng dồn lịch sử + issue đã đóng vẫn được bot cộng tiếp.**
Không thấy lỗi phân loại hay lỗi dedupe trong `cloudflare/crash-worker/worker.js` và `scripts/crash-issue-bot.py`; đã sửa một điểm gây hiểu nhầm (mục 4).

## 2. Bằng chứng

### 2.1 Con số leo dần trên cùng một chữ ký, dù issue đã đóng

#304 (`a264e8cb16d5`, `unknown/lowmem`, thành phần `app:game (+1)`):

| Mốc | Số trong thân issue | Nguồn |
|---|---:|---|
| 30/10 | 2.048 | `docs/ds/tong-hop-crash.md` |
| 08/11 | 11.072 | `docs/loi/TON-0811.md` (PR #542, chưa gộp) |
| 13/11 | 16.930 (+703) | thân issue hiện tại |

`state: CLOSED` từ 10/11 (bình luận "gộp theo dõi tại #549"), nhưng `updatedAt` = 12/11 ⇒ bot vẫn ghi vào issue **đã đóng**.

Cơ chế (đọc mã, không suy đoán):
- `scripts/crash-issue-bot.py:124` — `saved["count"] = state["count"] + delta`: **cộng dồn vĩnh viễn**, không có cửa sổ thời gian, không reset.
- `scripts/crash-issue-bot.py:117-118` — `delta` = số lượt mới (theo `seen` hash báo cáo) so với lần thông báo trước; chỉ ghi khi `delta ≥ 5`.
- `scripts/crash-issue-bot.py:187-220` (hàm `live`) — đọc issue bằng `state=all` (`:197`) và PATCH **mọi** issue có chữ ký khớp (`:220`), kể cả issue đã đóng (chỉ cố ý không đổi `state`, xem test `test_all_reads_before_writes_and_existing_closed_issue_not_reopened`).

⇒ Với một issue đã đóng, "Số lần quan sát đã thông báo" **chỉ có thể tăng**, và `(+N)` là lượt mới kể từ lần thông báo trước — không phải "nhóm còn tái diễn mạnh".

### 2.2 Lượt mới là báo cáo mới, không phải báo lại bản cũ

- `Diagnostics.collectProcessDeaths` chỉ lấy `ApplicationExitInfo` **mới hơn** mốc `diag/last-scan.txt` (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:836,842`) và ghi mốc mới sau khi quét (`:882`) ⇒ không phát lại lần chết cũ mỗi lần mở app.
- Từ V194: gộp theo `fingerprint`, mỗi báo cáo đúng 1 lần ghi KV (`45d756c1`, `51bc9005`); gửi lowmem tối đa **1 lần mỗi phiên** cho mỗi fingerprint (`Diagnostics.kt:634-639`, `SEND_DEDUPE_MS` cho loại khác ở `:667`).
- Từ V202 (#569, `640a96c6`): chỉ báo `lowmem` khi tiến trình còn quan trọng hoặc có phiên chết khớp (`Diagnostics.kt:817-821`, dùng ở `:858`), nên việc Android dọn tiến trình nền bình thường không còn sinh báo cáo.

### 2.3 Không gộp `killed` vào `lowmem`

`ApplicationExitInfo.REASON_LOW_MEMORY → "lowmem"` (`Diagnostics.kt:236,847`); `killed` là `kind` riêng do phiên game tự báo. Chữ ký bot băm `lõi + loại + ≤3 frame` (`crash-issue-bot.py:71`) nên hai loại không thể lẫn.

## 3. Phần chưa kiểm được

**[CHƯA KIỂM]** Tỉ lệ lowmem **sau 0.8.10** (bản có V202): phiên này không có `CRASH_ADMIN_TOKEN` nên không đọc được `time` của 100 báo cáo gần nhất, cũng không tách được "máy yếu bị hệ thống dọn" với "app giữ RAM quá nhiều".

Cách đo (khi có token):
1. `scripts/crash-insight.py` — xem số báo cáo `lowmem` theo ngày và theo bản. Mặc định script **chỉ tính báo cáo khớp chứng thư bản phát hành** (`OFFICIAL_CERT_PREFIX`, `partition_by_cert` ở `:310,334`) và chỉ ghi tổng số báo cáo/lượt đã loại ở cuối bảng; truyền `--all-certs` (`:342`) khi cần xem cả bản mod/đóng gói lại.
2. `scripts/crash-reports.sh <id>` cho vài báo cáo `lowmem` mới nhất — đọc `env` (RAM trống/tổng, heap) để biết máy yếu hay rò rỉ.
3. So hai cửa sổ: trước và sau mốc phát hành bản có #569. Nếu số báo cáo/ngày không giảm trên máy ≥ 6 GB thì mở issue **mới** (đừng mở lại #304) kèm `env` và phiên game tương ứng.

## 4. Đã sửa trong nhánh này

`scripts/crash-issue-bot.py`: khi issue đã đóng, thân issue thêm một dòng

> Issue này đã đóng: số trên là **cộng dồn lịch sử**, "(+N)" là lượt mới kể từ lần thông báo trước — không phải mức tăng hiện tại. Bot vẫn cộng dồn để thấy tái diễn; mở lại issue nếu cần theo dõi tiếp.

Không đổi dữ liệu gửi đi (vẫn chỉ lõi/loại/chữ ký/số đếm/thành phần), không đổi việc mở/đóng issue, không đổi ngưỡng `delta ≥ 5`.

Test: `scripts/test-crash-issue-bot.py` — **24/24 OK**, thêm `test_closed_issue_body_marks_the_count_as_cumulative_history` (issue đóng có dòng ghi chú, issue mở không có, cập nhật nhiều lần không nhân đôi). Chạy offline, không cần Gradle.

## 5. Đề xuất

1. Giữ #304 ở trạng thái **đã đóng** (đã trỏ #549 từ 10/11); không mở lại chỉ vì con số tăng.
2. Theo dõi nhóm tại #549; chỉ mở issue mới khi có bằng chứng tốc độ (mục 3).
3. Nếu cần con số "mức tăng hiện tại" trên issue: thêm trường `last seen`/số lượt trong 24 giờ vào thân issue — cần thống nhất lại quy ước dữ liệu rời máy trong `docs/CRASH-ISSUE-BOT.md` trước khi làm (hiện quy ước chỉ cho lõi/loại/chữ ký/số đếm).
