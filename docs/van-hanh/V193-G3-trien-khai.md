# V193-G3 — Runbook triển khai đường tải lõi/engine có chữ ký hạn ngắn

Tài liệu vận hành cho PM/sếp. Đặc tả gốc: `docs/dac-ta/V193-cong-bao-ve.md` (mục 7 — thứ tự 4 bước).
Mã liên quan: Worker `cloudflare/crash-worker/` (endpoint `/pack`), app đọc `config/monika-config.json`.

> **Nguyên tắc:** làm đúng thứ tự 4 bước. **Chưa xong bước 3 thì không chuyển repo packs sang riêng tư.**
> Trong suốt quá trình chuyển, **link cũ và link mới cùng sống** để bản app đang cài vẫn tải được.

Không ghi giá trị secret vào tài liệu này, vào issue/PR hay vào log. Chỉ dùng tên biến và nơi đặt.

---

## 0. Điều kiện tiên quyết

| Hạng mục | Yêu cầu | Cách kiểm |
|---|---|---|
| Worker | Đã deploy bản `worker.js` có endpoint `/pack` | Gọi thử một tên gói hợp lệ: trả **302** (đã cấu hình) hoặc **503** (thiếu env). Nếu trả **200** kèm `{"name":"aowvn-monika-crash"}` thì bản deploy **chưa có** `/pack` |
| Env Worker | `PACKS_TOKEN` (secret), `PACKS_HOSTS`, `PACKS_BASE`, `PACKS_ALLOW`; `ADMIN_TOKEN` vẫn còn; `PACK_TTL_SECONDS` tuỳ chọn | Xem mục 1 |
| Host `/pack` | **Phải nằm trong allowlist của app** (`ALLOWED_DOWNLOAD_HOSTS` trong `app/src/main/java/vn/aow/monika/config/MonikaConfig.kt`) | `grep -n 'monika.aow.vn' app/src/main/java/vn/aow/monika/config/MonikaConfig.kt`. `*.workers.dev` **không** nằm trong danh sách ⇒ nên đưa `/pack` lên `monika.aow.vn` (route Cloudflare) hoặc phát hành app có host đó **trước** bước 2 |
| Repo packs | Còn public (để bước 2 kiểm được bằng link mới lẫn link cũ) | Mở một link release bất kỳ bằng trình duyệt ẩn danh |

Nơi đặt env: Cloudflare Dashboard → Workers & Pages → Worker `aowvn-monika-crash` → Settings → Variables (secret thì dùng "Encrypt"). Không dán giá trị vào chat/PR.

Giá trị cần đặt (chỉ mô tả, **không** ghi giá trị):

- `PACKS_TOKEN`: **cùng giá trị** với secret `PACKS_TOKEN` mà GitHub Actions của repo packs đang dùng.
- `PACKS_HOSTS`: danh sách host được phép nhận chuyển hướng tải, phẩy ngăn cách (ví dụ host CDN/R2 đã chốt).
- `PACKS_BASE`: URL gốc CDN/R2 chứa gói (`https://…`, host phải nằm trong `PACKS_HOSTS`).
- `PACKS_ALLOW`: danh sách tên gói cho phép, phẩy ngăn cách — **đúng từng tên tệp** sẽ dùng trong config.
- `PACK_TTL_SECONDS`: tuỳ chọn; bỏ trống thì dùng mặc định.

---

## 1. Bước 1 — Bật đường tải có chữ ký (Worker)

**Làm:** deploy `cloudflare/crash-worker/` bản mới nhất (người có quyền Cloudflare), đặt đủ env ở mục 0.

**Kiểm (kỳ vọng):**

```bash
BASE="https://aowvn-monika-crash.aowvn-system.workers.dev"
NAME="<tên-gói-đã-có-trong-PACKS_ALLOW>"

# 1) Xin URL tải: phải 302, Location là URL có hạn trên chính Worker
curl -sS -o /dev/null -D - "$BASE/pack/$NAME" | sed -n '1p;/^location:/Ip'

# 2) Theo URL có hạn: phải 302 tiếp sang host CDN/R2 (không tải nội dung qua Worker)
SIGNED="$(curl -sS -o /dev/null -w '%{redirect_url}' "$BASE/pack/$NAME")"
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' "$SIGNED"
```

- **Đạt:** bước 1 trả `302` + `location: …/pack/$NAME?…`; bước 2 trả `302` + host trong `PACKS_BASE`.
- **Dấu hiệu hỏng:**
  - `503` → thiếu `PACKS_TOKEN`/`PACKS_BASE`/`PACKS_HOSTS` (hoặc rỗng).
  - `403` → tên gói không có trong `PACKS_ALLOW`.
  - `200 {"name":…}` → bản deploy chưa có `/pack`.
  - `302` nhưng bước 2 trả `403` → `PACKS_BASE` sai scheme/host hoặc host không khớp `PACKS_HOSTS`.

**Lùi:** đặt `PACKS_ALLOW` rỗng (endpoint trả `503`, app tự dùng link cũ) **hoặc** rollback Worker về bản deploy trước. Không cần đổi config app ở bước này.

---

## 2. Bước 2 — Đổi link trong config + tăng `configVersion` + đồng bộ Cloudflare

**Làm:**

1. Sửa `config/monika-config.json`, thay URL tải của **lõi/engine/module** bằng URL `/pack/<tên-gói>` trên host đã nằm trong allowlist của app (xem mục 0). Các trường cần đổi:
   - `cores.<id>.url`
   - `cores.<id>.artifacts.<abi>.url`
   - `cores.<id>.systemFiles`
   - `modules.<id>.url`
2. **Tăng `configVersion`** (luôn tăng, không giảm).
3. Commit lên `main` → workflow `sync-config.yml` tự chạy (hoặc chạy tay `gh workflow run sync-config.yml`). Workflow đẩy config lên KV và nhánh `trang-web` (Pages).

**Kiểm (kỳ vọng):**

```bash
# Pages đã nhận bản mới (so số với configVersion vừa tăng)
curl -sS https://monika.aow.vn/config.json | grep -o '"configVersion": *[0-9]*'

# Một URL mới trong config phải trả 302 (qua /pack), không phải 404 của GitHub
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' "<URL-mới-trong-config>"
```

- **Đạt:** Pages trả đúng `configVersion` mới; URL mới trả `302` sang host CDN/R2.
- **Dấu hiệu hỏng:**
  - `configVersion` không đổi → `sync-config.yml` chưa chạy xong hoặc fail; xem log workflow.
  - URL mới trả `404`/`503` → bước 1 chưa đạt hoặc tên gói sai `PACKS_ALLOW`.
  - App bản mới **không nhận** config (vẫn dùng bản đóng gói) → host `/pack` chưa nằm trong allowlist của app, hoặc config vi phạm kiểm tra (xem mục "Kiểm sau triển khai").

**Lùi (quan trọng):**

```bash
# Cách 1: revert commit config rồi đồng bộ lại
git revert <sha-commit-config>        # hoặc khôi phục file rồi commit
# BẮT BUỘC: tăng configVersion thêm 1 lần nữa (không được giảm số)
# rồi commit lên main để sync-config.yml chạy lại
```

- Sau khi revert: kiểm lại Pages trả `configVersion` mới và URL **cũ** hoạt động.
- **Không** xoá link cũ trong lúc chuyển: bản app đang cài (chưa cập nhật) vẫn dùng link cũ.

---

## 3. Bước 3 — Phát hành bản app dùng link mới

**Làm:** phát hành theo quy trình phát hành hiện có (tag/release), có thể đặt `minVersionCode` nếu cần ép cập nhật. Chỉ sau bước này mới được tính đến bước 4.

**Kiểm (kỳ vọng):**

- Máy thử cài bản mới → mở app → tải thử một lõi/engine **chưa có sẵn** trong máy: tải xong, `sha256` khớp, chạy được.
- Máy đang cài **bản cũ** vẫn tải được lõi (qua link cũ).
- Log/`Diagnostics` không có lỗi "host tải không nằm trong danh sách cho phép".

**Lùi:** giữ bản phát hành cũ cho người dùng; nếu bản mới lỗi, đánh dấu ngừng phát hành và hướng dẫn quay lại bản trước (không xoá asset cũ).

---

## 4. Bước 4 — Chuyển repo `aowvn-monika-packs` sang riêng tư

**Chỉ làm khi:** bước 3 đã phát hành và đã có thời gian chuyển đổi cho bản đang cài (PM quyết định).

**Làm:** đổi visibility repo packs sang private.

**Kiểm (kỳ vọng):**

- Trình duyệt ẩn danh mở link release cũ → `404` (đúng, đã riêng tư).
- App bản mới tải lõi/engine/module thành công (đi qua `/pack`).
- App bản cũ (nếu còn) **không** tải được nữa → đây là lý do phải xong bước 3 trước.

**Lùi:** chuyển repo packs về **public** ngay (khôi phục tức thì cho bản cũ), rồi xử lý tiếp.

---

## 5. Kiểm sau triển khai (checklist)

- [ ] `/pack/<tên-hợp-lệ>` trả **302**, không phải `503`/`403`.
- [ ] URL trong config (Pages) trả **302** sang host CDN/R2.
- [ ] `configVersion` trên `https://monika.aow.vn/config.json` đúng số vừa tăng.
- [ ] App bản mới **nhận** config từ xa (không rơi về bản đóng gói) — kiểm bằng cách xem giá trị `configVersion` app đang dùng (màn Cài đặt/Chẩn đoán) hoặc log.
- [ ] Tải thử 1 lõi + 1 engine/module: thành công, `sha256` khớp, chạy được.
- [ ] Bản app **cũ** vẫn tải được (link cũ còn sống) — nếu repo packs đã private thì bỏ qua mục này.
- [ ] Không có báo cáo lỗi mới liên quan tải gói trong 24 giờ đầu.
- [ ] Repo packs (nếu đã private): người ngoài không mở được link cũ.

---

## 6. Bảng lùi nhanh

| Bước hỏng | Lùi trong 1 lệnh | Kiểm lại |
|---|---|---|
| Worker `/pack` lỗi | `PACKS_ALLOW` = rỗng (hoặc rollback deploy) | `/pack` trả 503, app dùng link cũ |
| Config mới hỏng | `git revert` commit config + **tăng** `configVersion` + sync | Pages trả số mới, URL cũ chạy |
| App mới lỗi | Ngừng phát hành, giữ bản cũ | Người dùng vẫn tải được bằng bản cũ |
| Repo packs private gây hỏng | Chuyển repo về public | Link cũ mở lại được |

## 7. Ghi chú

- Không đổi `compileSdk`/`targetSdk`/AGP trong các bước này.
- Mọi thay đổi `config/monika-config.json` **phải** tăng `configVersion` và đi qua `sync-config.yml`.
- Chi tiết kỹ thuật/nội bộ khác: xem kênh nội bộ của PM, không đăng ở tài liệu công khai.
