## V195 — Chuyển kho báo lỗi Worker từ KV sang D1 (`CRASH_DB`) chịu tải 1.000 người dùng đồng thời

- Người làm: Argon (đặc tả) · DeepSeek 3 (thi công) · Sếp (tạo D1 & gắn binding)
- Thiết kế gốc (nếu có): `aowvn-monika-noibo#15`
- Trạng thái: Nháp

### 1. Mục tiêu
Chuyển nơi lưu báo lỗi của `cloudflare/crash-worker/worker.js` từ KV (`env.REPORTS`, trần miễn phí chỉ 1.000 lượt ghi/ngày) sang Cloudflare D1 (`env.CRASH_DB`, database `aowvn-monika-crash`, trần miễn phí 100.000 hàng ghi/ngày) có gộp trùng theo chữ ký lỗi. Khi thiếu binding `CRASH_DB`, Worker tự rơi về `env.REPORTS` để không gián đoạn hệ thống đang chạy; toàn bộ công cụ đọc (`crash-insight.py`, `crash-issue-bot.py`, `stability-gate.py`, `crash-reports.sh`) giữ nguyên định dạng JSON không cần sửa.

### 2. Ngoài phạm vi
- Không đổi định dạng báo cáo từ Android (`Diagnostics.kt`) và không đổi cổng tải gói `G3` (`/pack/<name>`).
- Không di trú lịch sử báo cáo cũ đang nằm trong KV sang D1 (KV cũ tự hết hạn sau 60 ngày).
- Cache biên `caches.default` cho Worker cấu hình (`cloudflare/worker.js`) tách PR nhỏ riêng nếu cần.

### 3. Tiêu chí đạt
- [ ] Có tệp `cloudflare/crash-worker/schema.sql` gồm **1 bảng `crash_reports`** (`dedupe_key TEXT PRIMARY KEY`, `id TEXT NOT NULL UNIQUE`, `first_seen_ms`, `last_seen_ms`, `expires_at_ms`, `hit_count`, `kind`, `core`, `sys_name`, `game`, `app`, `stage`, `report_time`, `component`, `fingerprint`, `cert16`, `payload_json`) và **đúng 1 index phụ** `idx_crash_reports_last_seen (last_seen_ms DESC, id DESC)` để mỗi lượt upsert chỉ tốn ≤ 3 `rows_written`.
- [ ] **Quy tắc gộp trùng (`dedupe_key`) & bất biến tương thích:**
  - Nếu `slim.kind === "user" || !slim.fingerprint`: `dedupe_key = id` (không gộp, giữ nguyên ảnh chụp và mô tả riêng của người chơi).
  - Ngược lại: `dedupe_key = "g:" + sha256Hex(fingerprint + "\n" + slim.app + "\n" + slim.kind + "\n" + core + "\n" + slim.component + "\n" + cert16)`.
  - Khi `ON CONFLICT(dedupe_key) DO UPDATE`: **giữ nguyên `id` và `first_seen_ms` của lần nhận đầu tiên** (khớp regex `r:\d{13}:[a-f0-9-]{8,36}` của `stability-gate.py` và cách tính `delta` của `crash-issue-bot.py`), cập nhật `last_seen_ms`, `expires_at_ms = now + 60 * 86400 * 1000`, `hit_count = MIN(1000000, hit_count + excluded.hit_count)`, và đồng bộ trường `count` trong `payload_json` bằng `hit_count` đã kẹp `<= 1_000_000`.
- [ ] Các đường đọc quản trị (`/reports`, `/reports/<id>`, `/report-index`, `/stats`) lọc `WHERE expires_at_ms > ?now` và trả đúng schema JSON như KV (`index.app === detail.app` đầy đủ ≤ 80 ký tự); dọn rác lười (`DELETE ... WHERE expires_at_ms <= ?now LIMIT 100`) chạy tối đa 1 lần / 60 giây mỗi isolate.
- [ ] Khi `env.CRASH_DB` vắng mặt, mọi đường `POST /report` và `GET /reports*`, `/report-index`, `/stats` chạy nhánh `env.REPORTS` cũ và vượt qua toàn bộ test hiện có trong `worker.test.js` + bộ test giả lập D1 mới.

### 4. Bản Play
`không` (chỉ thay đổi mã phía máy chủ `cloudflare/crash-worker/`, không đụng APK).

### 5. Config
Không đổi (`config/monika-config.json` / `MonikaConfig.kt` giữ nguyên `crash.endpoint`).

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | Đặc tả `docs/dac-ta/V195-worker-d1.md` | — | PR này |
| 2 | Thêm `cloudflare/crash-worker/schema.sql` + nhánh `env.CRASH_DB` trong `worker.js` (giữ fallback `env.REPORTS`) + test đơn vị giả lập D1 trong `worker.test.js` | 1 | — |
| 3 | Cập nhật `cloudflare/crash-worker/README.md` (hướng dẫn tạo DB `aowvn-monika-crash`, chạy `schema.sql`, gắn binding `CRASH_DB`) + bọc `caches.default` cho `cloudflare/worker.js` | 2 | — |

### 7. Rủi ro
- Không đụng dữ liệu người chơi trên máy (save, phím, bố cục).
- Không phát sinh thêm lượt gọi Worker từ app; ở mức 1.000 người dùng đồng thời (~6.000 báo lỗi/ngày), lượng ghi giảm từ 6.000 KV writes (vượt 6× trần miễn phí) xuống ~18.000 D1 `rows_written` (~18% trần miễn phí 100.000 hàng/ngày) và giữ số hàng mỗi phiên bản < 200 để không khóa nhầm `stability-gate.py`.
