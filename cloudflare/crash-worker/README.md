# Worker nhận báo lỗi Aow Monika

- Địa chỉ: `https://aowvn-monika-crash.aowvn-system.workers.dev`
- `POST /report` — app gửi báo lỗi (JSON, tối đa 60.000 byte UTF-8, 20 báo cáo/giờ/IP, chỉ lưu băm IP). Lưu KV `aowvn-monika-crash-reports`, giữ 60 ngày.
- Ngưỡng chống spam (V153) đổi bằng biến môi trường của Worker: `RATE_LIMIT_MAX` (mặc định `20`, tối đa `100000`) và `RATE_LIMIT_WINDOW_SECONDS` (mặc định `3600`, tối đa `604800`). Quá ngưỡng trả `429` kèm header `Retry-After` = số giây còn lại của cửa sổ. Env sai/thiếu thì dùng mặc định. **V194:** bộ đếm hạn mức nằm trong bộ nhớ của isolate (không ghi KV) và mỗi báo cáo chỉ **1 lần ghi KV** (bản ghi chính) — trước đây là 2 ghi/báo cáo (khoá `rl:` + bản ghi), đốt hạn mức 1000 ghi/ngày của gói miễn phí.
- `GET /stats`, `GET /reports`, `GET /reports/<id>` — xem báo cáo, cần header `Authorization: Bearer <mã quản trị>`.
- V155b: danh sách `/reports` trả thêm `cert` = 16 hex đầu SHA-256 chứng thư ký APK (rỗng với app cũ không gửi), để bảng `crash-insight` đếm theo chứng thư và phát hiện bản không chính thức; bản ghi đầy đủ vẫn giữ `certSha256` 64 hex.
- Mã quản trị là secret `ADMIN_TOKEN` của Worker (không ghi trong repo). Mất mã thì nhờ dựng lại Worker với mã mới. **V195:** thiếu/để trống `ADMIN_TOKEN` → mọi endpoint quản trị trả `503` (đóng cửa, không so sánh gì); so sánh mã dựa trên SHA-256 hai vế thay vì so chuỗi thô.
- Xem nhanh: `CRASH_ADMIN_TOKEN=... scripts/crash-reports.sh` (tóm tắt) hoặc `... scripts/crash-reports.sh <id>` (chi tiết).
- Đã triển khai bản `worker.js` của repo ngày 07/10/2026 (V29, xem `docs/opus/ket-qua/V29-deploy.md`); crumbs/env/component/pid/ảnh nay được lưu. [CHƯA KIỂM] ảnh JPEG thật từ máy người chơi.

## Giới hạn của bộ đếm chống spam (V194, PM chốt chấp nhận)
Bộ đếm hạn mức (`RATE_LIMIT_MAX` / `RATE_LIMIT_WINDOW_SECONDS`) nằm trong **bộ nhớ của isolate** nên:
- **Mất khi isolate tắt** (hết hạn, deploy bản mới, Cloudflare thu hồi) — bộ đếm về 0, không nhớ lượt trước đó.
- **Không chia sẻ giữa các colo**: mỗi colo đếm riêng, nên một IP có thể gửi tới ~`RATE_LIMIT_MAX` báo cáo/colo/cửa sổ.
- Vì vậy đây là **chặn mềm** (đủ chống spam vô tình và lỗi vòng lặp), **không phải hạn mức cứng**. Chặn cứng vẫn là **ngân sách ghi KV: 1000 ghi/ngày** của gói miễn phí — mỗi báo cáo đúng 1 ghi (V194).

Cơ chế **đếm dùng chung** giữa các isolate/colo (KV hoặc Durable Object) **được để dành cho V153/V195**, không làm trong V194: mọi cách đếm dùng chung đều tốn thêm lượt ghi/đọc KV, đúng thứ V194 phải giảm.

## Chặn cứng khi bị spam: WAF rate limiting cho `POST /report` (V194, Luna 5)
Bộ đếm trong Worker chỉ là chặn mềm (mục trên). Khi bị spam thật, bật thêm **một luật Cloudflare WAF Rate Limiting** — lớp chặn cứng ở biên, **không cần secret mới** và không tốn lượt KV nào (request bị chặn không vào Worker):
1. Dashboard Cloudflare → chọn zone `aow.vn` → **Security → WAF → Rate limiting rules → Create rule**.
2. **If incoming requests match:** `Field = URI Path`, `Operator = equals`, `Value = /report`; thêm `AND Method = POST`. (Đường nhận báo cáo là `POST /report`; `GET /reports` của quản trị **không** khớp luật này.)
3. **Rate:** ví dụ **30 request / 1 phút / theo IP** (đặt trên `RATE_LIMIT_MAX` của Worker để Worker còn kịp trả 429 kèm `Retry-After` trước).
4. **Action:** *Block* trong **10 phút** (hoặc *Managed Challenge* nếu muốn nhẹ hơn).
5. Bật rule, rồi kiểm bằng `curl -X POST .../report` lặp nhanh từ một IP: các request vượt ngưỡng phải bị chặn ở biên (không thấy trong log Worker).

Ghi chú: luật WAF tính theo IP ở biên nên **không phụ thuộc isolate/colo** — đây là lớp bù cho giới hạn của bộ đếm trong Worker. Ngưỡng WAF là cấu hình dashboard, không nằm trong repo.

## Hợp đồng JSON (V29)

`Diagnostics.Report` được gửi tới `POST /report`; Worker chỉ bổ sung trường, vẫn nhận app cũ. `kind`, `title`, `app` bắt buộc là chuỗi; JSON sai trả 400. Báo cáo và giá trị KV đều tối đa 60.000 **byte UTF-8**; quá giới hạn trả 413, không ghi báo cáo. Body được đọc theo stream có chặn dung lượng, không dựa riêng Content-Length.

| Trường | Giới hạn khi lưu |
|---|---|
| id, time | Số nguyên không âm an toàn JS; id mặc định 0, time mặc định thời điểm nhận |
| kind / title / app / device | 32 / 300 / 80 / 300 đơn vị UTF-16 |
| component / fingerprint / count | 160 / 128; count số nguyên an toàn ≥1, mặc định 1 |
| env / reason / detail | 2.000 / 300 / 20.000 đơn vị UTF-16 |
| crumbs / log | 40 / 250 dòng, mỗi dòng 300 đơn vị UTF-16 |
| fromGame | boolean, mặc định false |
| session | null nếu không có; pid, startedAt, stageAt, lastAlive là số nguyên không âm |
| session.kind/core/coreInfo/game/system/stage | 20 / 60 / 200 / 160 / 60 / 40 đơn vị UTF-16 |
| core / system / stage (V101, chỉ anr/lowmem) | 60 / 60 / 40; lấy field mới, rồi session thực có, cuối cùng `unknown` |
| certSha256 (V155) | đúng 64 hex thường; viết hoa được chuẩn hoá, sai dạng hoặc thiếu thì **bỏ trường**, không chặn báo cáo |

V101 giữ snapshot phiên theo PID ở app, cập nhật cùng stage; PID hoặc thời điểm không khớp thì không gán phiên khác. `unknown` nghĩa thiếu bằng chứng, không tạo Session/PID/game giả. `core/system/stage` bổ sung cho báo cáo ANR/lowmem mới và metadata danh sách; JSON của loại lỗi khác giữ nguyên. Báo cáo cũ trong KV không bị viết lại. Worker thay đổi ở đây là mã chuẩn bị; Sol không deploy, server đang chạy có thể vẫn bỏ ba field mới cho đến khi người có quyền triển khai. Không tuyên bố sửa lại 78 báo cáo lịch sử hay xác nhận ANR/RAM trên máy thật.

Các chuỗi/danh sách tùy chọn sai loại được thay bằng rỗng. `seen`/`sent` là cờ UI cục bộ, không lưu ở Worker. Trường lạ bỏ qua. KV giữ 60 ngày; metadata danh sách gồm `component`, `fp`, `count` để lọc lỗi. GET chi tiết trả cùng JSON đã lưu, không cắt thêm. Kho cũ giữ nguyên; báo cáo cũ có thể thiếu trường mới.

Kiểm: `node --test cloudflare/crash-worker/worker.test.js` (KV giả trong bộ nhớ, không mạng). `CrashContractTest` dùng **cùng** `fixtures/app-report.json` để kiểm serializer Kotlin; Build chạy cả hai. Test Worker phủ gửi → lưu → danh sách → đọc, app cũ, loại sai, byte UTF-8, quyền đọc và giới hạn spam.

Triển khai: cần người có quyền sửa/deploy Worker `aowvn-monika-crash` và gắn namespace KV `REPORTS` hiện có; nếu dùng API cần quyền Cloudflare Workers Scripts:Edit và Workers KV Storage tương ứng tài khoản. Sonnet/chủ tài khoản triển khai sau PM duyệt, giữ binding và secret hiện tại. [CHƯA KIỂM] ai có quyền này và mã đang chạy. Sol không đổi secret, không triển khai, không chuyển giao thức/endpoint hay dữ liệu KV cũ.
