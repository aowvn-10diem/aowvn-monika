# V193-G3 — Runbook triển khai đường tải lõi/engine (bản 2, theo rà soát kiến trúc)

Tài liệu vận hành cho PM/sếp. Đặc tả gốc: `docs/dac-ta/V193-cong-bao-ve.md`.
**Chi tiết hạ tầng (bucket, khóa, lệnh Cloudflare từng bước) nằm ở kho nội bộ `aowvn-monika-noibo` — không đăng ở đây.**

> **Thứ tự bắt buộc (đã đảo so với bản 1):** dựng kho + Worker → **phát hành APK cầu nối** → đổi link trong config → đóng kho packs cũ **sau cùng**.
> Lý do: máy đang cài bản cũ vẫn tự làm mới config định kỳ và app kiểm host ở **mọi bước chuyển hướng**; nếu đẩy config sang đường mới trước khi máy có bản app biết host mới, bản đang cài sẽ lỗi tải ngay.
> Trong suốt quá trình, **link cũ và link mới cùng sống** cho tới bước 4.

Không ghi giá trị khóa/secret vào tài liệu, issue/PR hay log. Chỉ nêu tên biến và nơi đặt.

---

## 0. Điều kiện tiên quyết

| Hạng mục | Yêu cầu | Cách kiểm |
|---|---|---|
| Kho R2 | Bucket riêng cho gói, **private**; đã đồng bộ đủ gói hiện có | Liệt kê object trong bucket (kho nội bộ) |
| Khóa ký | Cặp khóa S3 **chỉ đọc** (`Object Read Only`), giới hạn đúng bucket gói, **tách khỏi** khóa CI đóng gói | Trang quản trị Cloudflare (kho nội bộ) |
| Worker | Đã deploy bản có `/pack` trên **custom domain thuộc `aow.vn`** (không dùng `*.workers.dev`, để đi qua WAF) | `curl -sS -o /dev/null -w '%{http_code}\n' https://<domain-aow-vn>/pack/<tên-gói>` |
| Env Worker | Secrets: `PACK_SIGN_KEY_ID`, `PACK_SIGN_SECRET`. Vars: `PACK_R2_ACCOUNT_ID`, `PACK_R2_BUCKET`, `PACK_TTL_SECONDS` (mặc định 300), `PACKS_ALLOW` hoặc `PACKS_MAP` | Xem mục 1 |
| Allowlist của app | Phải chứa **cả** host custom domain của Worker **và** host của URL R2 ký sẵn (`ALLOWED_DOWNLOAD_HOSTS` trong `app/src/main/java/vn/aow/monika/config/MonikaConfig.kt`) | `grep -n 'ALLOWED_DOWNLOAD_HOSTS' app/src/main/java/vn/aow/monika/config/MonikaConfig.kt` — nếu thiếu host R2 thì **phải phát hành app bổ sung trước bước 3** |
| Repo packs | Còn public (để link cũ sống tới bước 4) | Mở link release cũ bằng trình duyệt ẩn danh |

Nơi đặt env: Cloudflare Dashboard → Workers & Pages → Worker → Settings → Variables (secret dùng "Encrypt"). Giá trị khóa chỉ lấy từ kho nội bộ.

---

## 1. Bước 1 — Dựng kho + bật đường tải 1 bước có chữ ký

**Làm:** tạo/đồng bộ bucket private, tạo khóa đọc riêng, deploy Worker bản mới, đặt đủ secrets/vars ở mục 0.

**Kiểm (kỳ vọng):**

```bash
BASE="https://<domain-aow-vn>"
NAME="<tên-gói-đã-có-trong-PACKS_ALLOW|PACKS_MAP>"

# 1) Chỉ MỘT lần 302, Location là URL R2 đã ký (không phải chuyển hướng nội bộ nhiều bước)
curl -sS -o /dev/null -D - "$BASE/pack/$NAME" | sed -n '1p;/^location:/Ip'

# 2) Tải trong hạn: phải 200
SIGNED="$(curl -sS -o /dev/null -w '%{redirect_url}' "$BASE/pack/$NAME")"
curl -sS -o /dev/null -w '%{http_code}\n' "$SIGNED"

# 3) Sau khi hết hạn (chờ quá TTL): phải bị kho từ chối 403
sleep $((PACK_TTL_SECONDS + 5))
curl -sS -o /dev/null -w '%{http_code}\n' "$SIGNED"
```

- **Đạt:** (1) `302` + `location` trỏ thẳng host R2; (2) `200`; (3) `403`.
- **Dấu hiệu hỏng:**
  - `503` → thiếu secrets/vars (đóng cửa an toàn).
  - `403` ngay ở bước (1) → tên gói không có trong `PACKS_ALLOW`/`PACKS_MAP`.
  - `200 {"name":…}` → bản deploy chưa có `/pack`.
  - Tải được **sau** TTL → khóa/TTL cấu hình sai, dừng lại kiểm cấu hình.
  - Chuyển hướng nhiều bước hoặc trỏ về `*.workers.dev` → sai bản deploy/route.

**Kiểm thêm:** giới hạn tần suất cho riêng `/pack/*` (gọi liên tiếp vượt ngưỡng phải bị chặn, không ảnh hưởng `/report`).

**Lùi:** rollback Worker về bản deploy trước (endpoint tạm đóng), app vẫn dùng link cũ. Không cần đổi config app.

---

## 2. Bước 2 — Phát hành **APK cầu nối** và chờ độ phủ

**Làm:** phát hành bản app có allowlist chứa host custom domain + host R2 (mục 0). Có thể đặt `minVersionCode` **thấp** ở bước này (chưa ép chuyển hẳn).

**Kiểm (kỳ vọng):**

- Máy cài bản cầu nối: tải thử 1 lõi/engine chưa có → thành công, `sha256` khớp, chạy được.
- Máy bản cũ: vẫn tải được qua link cũ.
- Không có lỗi "host tải không nằm trong danh sách cho phép" trong log/Diagnostics.

**Lùi:** ngừng phát hành bản cầu nối, giữ bản trước; chưa đổi config nên người dùng không bị ảnh hưởng.

---

## 3. Bước 3 — Đổi link trong config + tăng `configVersion`

**Chỉ làm khi** bước 2 đã phát hành và có độ phủ cần thiết (PM quyết định).

**Làm:**

1. Trong `config/monika-config.json`, đổi URL tải lõi/engine/module sang `https://<domain-aow-vn>/pack/<tên-gói>`:
   - `cores.<id>.url`
   - `cores.<id>.artifacts.<abi>.url`
   - `cores.<id>.systemFiles`
   - `modules.<id>.url`
2. **Tăng `configVersion`** (luôn tăng, không giảm). Nếu chốt chuyển hẳn thì nâng `minVersionCode` tương ứng bản cầu nối.
3. Commit lên `main` → `sync-config.yml` tự chạy (hoặc `gh workflow run sync-config.yml`).

**Kiểm (kỳ vọng):**

```bash
curl -sS https://monika.aow.vn/config.json | grep -o '"configVersion": *[0-9]*'
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' "<URL-mới-trong-config>"
```

- **Đạt:** Pages trả đúng `configVersion` mới; URL mới trả `302` sang host R2.
- **Dấu hiệu hỏng:**
  - `configVersion` không đổi → workflow chưa chạy xong/fail.
  - URL mới `404`/`503` → bước 1 chưa đạt hoặc tên gói sai.
  - App bản cầu nối **không nhận** config → host chưa có trong allowlist (quay lại bước 2).

**Lùi (quan trọng):** `git revert` commit config **và tăng `configVersion` thêm một lần nữa** (không được giảm số), commit lên `main` để sync lại; kiểm Pages trả số mới và **link cũ hoạt động**. Không xoá link cũ trong lúc chuyển.

---

## 4. Bước 4 — Đóng kho packs cũ (sau cùng)

**Chỉ làm khi** bước 3 đã ổn định và PM chốt hết thời gian chuyển đổi.

**Làm:** chuyển `aowvn-monika-packs` sang private; tắt/ngừng đường tải cũ nếu còn.

**Kiểm (kỳ vọng):**

- Trình duyệt ẩn danh mở link cũ → `404` (đúng).
- App bản cầu nối tải lõi/engine/module thành công qua đường mới.
- App bản cũ (nếu còn) **không** tải được nữa → đây là lý do phải xong bước 2–3 trước.

**Lùi:** chuyển repo packs về **public** ngay (khôi phục tức thì cho bản cũ), rồi xử lý tiếp.

---

## 5. Xoay khóa ký (2 pha, không gián đoạn)

1. Tạo khóa đọc **v2** (cùng quyền, cùng bucket) và thêm vào Worker (song song v1).
2. Cập nhật secret Worker sang v2.
3. Chờ **quá TTL** (URL đã ký bằng v1 hết hạn).
4. Thu hồi v1.

Kiểm sau mỗi pha: `/pack/<tên>` vẫn `302` và tải được; không có `403` hàng loạt.

---

## 6. Kiểm sau triển khai (checklist)

- [ ] `/pack/<tên-hợp-lệ>` trả **đúng một `302`** sang host R2, không phải `503`/`403`.
- [ ] Tải trong hạn `200`; sau hạn `403`.
- [ ] Rate limit `/pack/*` hoạt động, `/report` không bị ảnh hưởng.
- [ ] `configVersion` trên `https://monika.aow.vn/config.json` đúng số vừa tăng.
- [ ] App bản cầu nối nhận config từ xa (không rơi về bản đóng gói).
- [ ] Tải thử 1 lõi + 1 engine/module: thành công, `sha256` khớp, chạy được.
- [ ] Bản app cũ vẫn tải được (trước bước 4).
- [ ] Không có báo cáo lỗi mới liên quan tải gói trong 24 giờ đầu.
- [ ] Kho packs (nếu đã private): người ngoài không mở được link cũ.

---

## 7. Bảng lùi nhanh

| Bước hỏng | Lùi | Kiểm lại |
|---|---|---|
| Worker `/pack` lỗi | Rollback deploy (endpoint đóng) | App dùng link cũ |
| APK cầu nối lỗi | Ngừng phát hành, giữ bản trước | Người dùng vẫn tải được |
| Config mới hỏng | `git revert` + **tăng** `configVersion` + sync | Pages trả số mới, link cũ chạy |
| Kho packs private gây hỏng | Chuyển repo về public | Link cũ mở lại được |
| Xoay khóa lỗi | Giữ v1 tới khi v2 chạy ổn | `/pack` vẫn 302 |

## 8. Ghi chú

- Không đổi `compileSdk`/`targetSdk`/AGP trong các bước này.
- Mọi thay đổi `config/monika-config.json` **phải** tăng `configVersion` và đi qua `sync-config.yml`.
- Chi tiết hạ tầng (bucket, khóa, route, lệnh Cloudflare, phân tích nội bộ) ở kho riêng tư `aowvn-monika-noibo`; không đăng ở tài liệu công khai.
