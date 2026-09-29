# Worker nhận báo lỗi Aow Monika

- Địa chỉ: `https://aowvn-monika-crash.aowvn-system.workers.dev`
- `POST /report` — app gửi báo lỗi (JSON, tối đa 60 KB, 20 báo cáo/giờ/IP, chỉ lưu băm IP). Lưu KV `aowvn-monika-crash-reports`, giữ 60 ngày.
- `GET /stats`, `GET /reports`, `GET /reports/<id>` — xem báo cáo, cần header `Authorization: Bearer <mã quản trị>`.
- Mã quản trị là secret `ADMIN_TOKEN` của Worker (không ghi trong repo). Mất mã thì nhờ dựng lại Worker với mã mới.
- Xem nhanh: `CRASH_ADMIN_TOKEN=... scripts/crash-reports.sh` (tóm tắt) hoặc `... scripts/crash-reports.sh <id>` (chi tiết).
- Mã nguồn `worker.js` ở đây là bản đã triển khai.
