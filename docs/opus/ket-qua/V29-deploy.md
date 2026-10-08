# V29 — Triển khai Worker báo lỗi `aowvn-monika-crash`

**Ngày:** 07/10/2026 (sếp "Ok deploy" trong chat, PM duyệt trước đó). **Người làm:** Sonnet qua Cloudflare API.

## Trước
- Bản đang chạy (sửa lần cuối 29/09): script ~4 KB, `slim` chỉ giữ kind/title/app/device/reason/detail/log/session. **Không** lưu `crumbs`, `env`, `component`, `fingerprint`, `count`, `pid`, `image`, không có `reportImage`/`readBody` stream. Đây là lý do 2 báo cáo Kara 0.7.6 có crumbs rỗng.
- `/stats` total = 54. Bản cũ lưu để rollback ở scratchpad phiên (`worker-rollback/worker.old.js`, 3917 byte; không commit).

## Triển khai
- Nguồn: `cloudflare/crash-worker/worker.js` tại `main` (8987 byte, SHA-256 `5aff8b817ccf23b5…`), `node --test cloudflare/crash-worker/worker.test.js` 8/8.
- `PUT /accounts/<id>/workers/scripts/aowvn-monika-crash`, `main_module=worker.js`, `compatibility_date=2025-01-01` (giữ như cũ), `keep_bindings=[secret_text, kv_namespace]`. Phiên bản/deployment id: `32c145c4e8304cc79e06eb783c10f5e5`, `modified_on` 2026-10-07T08:24:22Z.
- Binding sau deploy (đọc lại `/settings`): secret `ADMIN_TOKEN` (không đổi, không in), KV `REPORTS` namespace `251f1a37…ebe4c`.

## Sau (đạt)
- `GET /stats` HTTP 200 bằng mã quản trị cũ, total 54 → không mất báo cáo.
- `POST /report` kind `pm-test` (crumbs 2 dòng, env, component, session.pid, fromGame) → 200; đọc lại `/reports/<id>` thấy **crumbs/env/component/pid/fromGame giữ nguyên**; `/stats` total 55 (KV `list` trễ ~1 phút mới thấy báo cáo mới).
- Báo cáo thử `r:1791361504197:fc99fc57` còn trong KV (tự hết hạn sau 60 ngày); bỏ qua khi thống kê.

## Chưa kiểm
- Ảnh JPEG thật từ app (chỉ test đơn vị Node có JPEG mẫu).
- Báo cáo thật từ máy sếp sau deploy.

## Rollback (nếu cần)
`PUT` lại `worker.old.js` với cùng `keep_bindings`; không đụng secret/KV.
