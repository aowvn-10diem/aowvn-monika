-- V195 — D1 schema cho crash Worker (binding `CRASH_DB`).
-- Mỗi HÀNG là một nhóm fingerprint (gộp trùng): báo cáo cùng dấu vân tay chỉ tăng `count`/`last_t`.
-- Mỗi báo cáo vẫn ĐÚNG 1 lượt ghi (upsert): gộp làm giảm số HÀNG LƯU và kích thước `/reports`,
-- KHÔNG làm giảm số hàng ghi mỗi ngày (D1 free: 100.000 hàng ghi/ngày).
-- Báo cáo không có fingerprint dùng `id` làm khoá nên mỗi báo cáo là một hàng riêng.
-- `count` do client gửi được Worker kẹp về 1..50 (ngoài khoảng → 1) trước khi ghi.
-- Hạn lưu 30 ngày: hàng có `last_t` cũ hơn 30 ngày bị Worker xoá kèm lượt POST (tối đa 1 lần/giờ)
-- và bị loại khỏi `/reports` `/stats` `/report-index`; nhánh KV cũ tự hết hạn sau 60 ngày.
--
-- Cột khớp bảng "khoá ngắn" trong worker.js (REPORT_SHORT_KEYS):
--   k = kind · c = core · y = system · g = game · a = app · st = stage
--   t = time (ms) · component · fp = fingerprint · count · cert (16 hex đầu SHA-256 chứng thư)
-- `id` giữ id báo cáo ĐẦU TIÊN của nhóm (để /reports/<id> vẫn mở được), `detail` là JSON đầy đủ mới nhất.
--
-- Áp dụng ĐÚNG THỨ TỰ (bảng chưa tạo thì Worker tự lùi KV, không lỗi):
--   wrangler d1 create aowvn-monika-crash
--   wrangler d1 execute aowvn-monika-crash --remote --file=cloudflare/crash-worker/d1-schema.sql
--   (rồi gắn binding `CRASH_DB` cho Worker trong wrangler.toml/dashboard và deploy)

CREATE TABLE IF NOT EXISTS reports (
  id        TEXT    PRIMARY KEY,
  fp        TEXT    NOT NULL,
  k         TEXT    NOT NULL DEFAULT '',
  c         TEXT    NOT NULL DEFAULT '',
  y         TEXT    NOT NULL DEFAULT '',
  g         TEXT    NOT NULL DEFAULT '',
  a         TEXT    NOT NULL DEFAULT '',
  st        TEXT    NOT NULL DEFAULT '',
  t         INTEGER NOT NULL DEFAULT 0,
  component TEXT    NOT NULL DEFAULT '',
  count     INTEGER NOT NULL DEFAULT 1,
  cert      TEXT    NOT NULL DEFAULT '',
  detail    TEXT    NOT NULL DEFAULT '',
  first_t   INTEGER NOT NULL DEFAULT 0,
  last_t    INTEGER NOT NULL DEFAULT 0
);

-- Gộp trùng: mỗi fingerprint đúng 1 hàng (upsert tăng count).
CREATE UNIQUE INDEX IF NOT EXISTS reports_fp ON reports (fp);

-- Truy vấn danh sách / phân trang index: mới nhất trước, id làm khoá phụ để cursor ổn định.
-- Cũng là chỉ mục cho lượt dọn hàng quá hạn (`DELETE ... WHERE last_t < ?`) và điều kiện `last_t >= ?`.
CREATE INDEX IF NOT EXISTS reports_list ON reports (last_t DESC, id DESC);

-- Thống kê theo (lõi, loại, giai đoạn).
CREATE INDEX IF NOT EXISTS reports_stats ON reports (c, k, st);
