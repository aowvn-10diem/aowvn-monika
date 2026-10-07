# Worker nhận báo lỗi Aow Monika

- Địa chỉ: `https://aowvn-monika-crash.aowvn-system.workers.dev`
- `POST /report` — app gửi báo lỗi (JSON, tối đa 60.000 byte UTF-8, 20 báo cáo/giờ/IP, chỉ lưu băm IP). Lưu KV `aowvn-monika-crash-reports`, giữ 60 ngày.
- `GET /stats`, `GET /reports`, `GET /reports/<id>` — xem báo cáo, cần header `Authorization: Bearer <mã quản trị>`.
- Mã quản trị là secret `ADMIN_TOKEN` của Worker (không ghi trong repo). Mất mã thì nhờ dựng lại Worker với mã mới.
- Xem nhanh: `CRASH_ADMIN_TOKEN=... scripts/crash-reports.sh` (tóm tắt) hoặc `... scripts/crash-reports.sh <id>` (chi tiết).
- Đã triển khai bản `worker.js` của repo ngày 07/10/2026 (V29, xem `docs/opus/ket-qua/V29-deploy.md`); crumbs/env/component/pid/ảnh nay được lưu. [CHƯA KIỂM] ảnh JPEG thật từ máy người chơi.

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

Các chuỗi/danh sách tùy chọn sai loại được thay bằng rỗng. `seen`/`sent` là cờ UI cục bộ, không lưu ở Worker. Trường lạ bỏ qua. KV giữ 60 ngày; metadata danh sách gồm `component`, `fp`, `count` để lọc lỗi. GET chi tiết trả cùng JSON đã lưu, không cắt thêm. Kho cũ giữ nguyên; báo cáo cũ có thể thiếu trường mới.

Kiểm: `node --test cloudflare/crash-worker/worker.test.js` (KV giả trong bộ nhớ, không mạng). `CrashContractTest` dùng **cùng** `fixtures/app-report.json` để kiểm serializer Kotlin; Build chạy cả hai. Test Worker phủ gửi → lưu → danh sách → đọc, app cũ, loại sai, byte UTF-8, quyền đọc và giới hạn spam.

Triển khai: cần người có quyền sửa/deploy Worker `aowvn-monika-crash` và gắn namespace KV `REPORTS` hiện có; nếu dùng API cần quyền Cloudflare Workers Scripts:Edit và Workers KV Storage tương ứng tài khoản. Sonnet/chủ tài khoản triển khai sau PM duyệt, giữ binding và secret hiện tại. [CHƯA KIỂM] ai có quyền này và mã đang chạy. Sol không đổi secret, không triển khai, không chuyển giao thức/endpoint hay dữ liệu KV cũ.
