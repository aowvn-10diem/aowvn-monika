# Bảo vệ repo công khai — kế hoạch (lệnh sếp 31/10: "tất cả, ưu tiên chỗ config")

Trạng thái: **KẾ HOẠCH**, chưa áp dụng. Người viết: Nova. Người duyệt: PM + sếp.

## Sự thật cần nhớ
1. Repo công khai + GPLv3 (LibretroDroid, EKA2L1/Azahar…) ⇒ ai cũng **được phép** fork và build. Không có cách "chặn" hợp pháp ở mức mã nguồn; chỉ có thể (a) không để bí mật trong repo, (b) làm bản fork **không dùng được dịch vụ của mình**, (c) giữ thương hiệu/chứng thư.
2. Mọi thứ nằm trong APK (kể cả `config/monika-config.json` đóng vào assets) đều trích ra được. "Ẩn" thật chỉ có khi dữ liệu **không nằm trong repo/APK** mà chỉ được Worker/KV trả về.
3. Đã kiểm 31/10: không có keystore/`.jks`/`.env`/`local.properties`/`google-services` nào bị track; không còn URL dịch vụ cứng trong mã Kotlin (config-first giữ được). Khóa ký chỉ qua env `MONIKA_KEYSTORE*` → bản fork tự build sẽ ký debug, **khác chứng thư**, không cài đè được bản chính thức.
4. Chưa có: `SECURITY.md`, `.github/CODEOWNERS`, chính sách thương hiệu.

## Phân loại khóa trong `config/monika-config.json` (chỉ tên khóa, không in giá trị)
| Khóa | Loại | Hướng xử lý |
|---|---|---|
| `systems`, `documentExtensions`, `coreOptionText`, `j2meScreen`, `prefetch*`, `saveTransfer` | Công khai, vô hại | Giữ trong repo |
| `app`, `feed`, `community`, `forum`, `webPlayers`, `externalApps` | Link công khai của cộng đồng | Giữ trong repo |
| `engines`, `cores`, `modules` (URL release GitHub) | Công khai theo bản chất (cần tải được) | Giữ; nếu muốn bản fork không dùng chung kho gói → chuyển host sang domain của mình (P1c) |
| `account.apiKey`, `account.databaseUrl` (Firebase web) | Khóa web **công khai theo thiết kế** nhưng phải khóa quyền | Sếp vào Firebase Console: giới hạn theo package + SHA-1 của chứng thư chính thức, bật App Check, siết Rules. Không phải việc sửa file |
| `account.donate` | Thông tin ngân hàng thật | Chuyển sang Worker/KV (chỉ trả khi app hỏi); gỡ khỏi file trong repo (P1a) |
| `crash.endpoint` | Địa chỉ Worker thu báo cáo | Giữ trong APK (bắt buộc), nhưng Worker phải kiểm: kích thước, tần suất, `User-Agent`/phiên bản; token quản trị luôn là secret (đã vậy) |
| `archivePasswords`, `downloadHosts` | Dữ liệu vận hành | Chuyển lớp "overlay" sang Worker/KV (P1b) |

## Ưu tiên số 1 (lệnh sếp 31/10 tối): bản người khác tự build phải KHÔNG dùng được dịch vụ của AowVN
Mục tiêu: bản fork không xem được bài/diễn đàn trong app, không có tải Pixeldrain tự động, không tự tải lõi giả lập.

Hiện trạng (đã kiểm trong mã/config, 31/10): app gọi **thẳng** các địa chỉ công khai — feed `www.aow.vn/feeds/posts/default`, diễn đàn `forum.aowvn.org/api/...`, Pixeldrain `pixeldrain.com/api/file/<id>`, lõi libretro từ `buildbot.libretro.com`, gói engine/module từ release `aowvn-monika-packs`. Vì công khai nên bản fork chạy y hệt bản chính thức; không có cổng nào để chặn.

Thiết kế "cổng" (một Worker, một chữ ký):
| Cổng | Việc | Cách |
|---|---|---|
| G0 | Chữ ký yêu cầu | OkHttp client duy nhất ở `AppGraph.kt` thêm header `X-Monika-Sig` = HMAC(ts + đường dẫn) bằng `MONIKA_GATE_SECRET`. Secret nạp lúc build **release** từ GitHub Secret vào `BuildConfig` (không có trong repo). Bản fork không có secret ⇒ Worker trả 403. Thêm kiểm hash chứng thư và rate limit |
| G1 | Bài viết + diễn đàn | Worker `/feed`, `/forum/*` lấy từ Blogger/Flarum rồi trả lại (cache); `feed.url`, `forum.baseUrl` trong config trỏ về Worker |
| G2 | Pixeldrain tự động | `downloadHosts[].directUrl` trỏ `/pd/<id>`; Worker xác thực rồi 302 sang Pixeldrain (dùng khóa API Pixeldrain đặt ở secret Worker, không phải ở app) |
| G3 | Lõi + engine + module | Repo `aowvn-monika-packs` chuyển **riêng tư**; Worker stream/302 có chữ ký ngắn hạn; lõi libretro mirror vào kho riêng (giữ `sha256` trong config) |

Giới hạn nói thẳng: (1) bài trên aow.vn và file Pixeldrain vẫn mở được bằng trình duyệt — cổng chỉ chặn **trải nghiệm trong app**; (2) người biết dịch ngược bản APK chính thức vẫn trích được secret — giảm bằng xoay secret mỗi bản, rate limit, và khóa theo chứng thư; (3) bản đã cài đang gọi thẳng nên phải **giữ đường cũ** cho tới khi ép cập nhật bằng `minVersionCode`, nếu không người dùng cũ hỏng.

Ghi chú vận hành: tạo secret (GitHub + Cloudflare), đổi repo packs sang riêng tư và triển khai Worker là việc của sếp/chủ repo — phiên Nova không có quyền Cloudflare/secret. Mọi trường mới trong `MonikaConfig.kt` phải có mặc định; tăng `configVersion` + đồng bộ Cloudflare theo thứ tự: Worker lên trước → config mới sau → ép bản cũ lên sau cùng.

## Quyết định đã chốt (PM + sếp, 01/11) và ước lượng tải
- **[A70]** Repo packs chuyển riêng tư. Thứ tự: Worker → config → sếp đổi quyền repo.
- **[A71]** Ép cập nhật từ thứ Hai 02/11/2026 (`minVersionCode`).
- Mã G3 và việc ép cập nhật (V157) giao DeepSeek 1 (#337). **PR này chỉ là tài liệu thiết kế**, không chứa mã.
- **P1a (overlay):** phần nhạy cảm đi kèm lần tải config hiện có, không thêm lượt gọi Worker. Offline dùng bản overlay lấy thành công lần cuối; chưa từng có thì tắt đúng tính năng đó. Giá trị không nằm trong APK hay repo.
- **[A77]** (sếp chốt 02/11) Sau bản 0.8.9, ưu tiên là **bảo vệ repo**. Tài liệu này là khung để các việc thi công bám theo; thi công do DeepSeek 1/2 làm, Sonnet chỉ rà thiết kế và chỉ ra rủi ro.
- **Hướng thi công đang mở:** V181 ([#443](https://github.com/aowvn-10diem/aowvn-monika/pull/443), nháp) — cấu hình qua GitHub Pages trước, Worker làm dự phòng. Khi V181 vào main, phần "tải" của P1a/G1 trong tài liệu này cần đối chiếu lại với thiết kế đó (Nova rà khi PM hỏi).
- **G0 chỉ là rào thấp:** secret HMAC nằm trong APK release nên dịch ngược lấy được ([A66]). Nó chặn bản fork lười, không chặn người cố tình.

### Ước lượng lượt gọi Worker (giả định, cần số đo thật)
Giả định mỗi người dùng/ngày (lấy từ `feed.pollMinutes=60`, `forum.limit=6`): thăm dò feed nền ~24, mở app/xem bài ~5, diễn đàn ~5, giải link Pixeldrain ~3, config ~2 ⇒ **~40 lượt/người/ngày ⇒ ~40.000 lượt/ngày cho 1000 người dùng hàng ngày**. Chỉ số 1000 người đồng thời cùng lúc là đỉnh vài nghìn lượt/phút, không phải tổng ngày.

Cách giảm (theo luật tiết kiệm Worker 26/10):
| Biện pháp | Tác dụng |
|---|---|
| Cache biên (Cache API / `Cache-Control`) cho `/feed`, `/forum/*`: 5–10 phút | Blogger/Flarum chỉ bị gọi ~1 lần/vài phút, không phụ thuộc số người |
| Thăm dò nền: giãn `pollMinutes` hoặc dùng `If-None-Match` (Worker trả 304 rẻ) | Cắt phần lớn 24 lượt nền |
| Pixeldrain: Worker chỉ 302, không stream | Không tốn băng thông Worker |
| G3 lõi/engine: link có chữ ký TTL ngắn, tệp lớn qua R2/CDN, không qua Worker | Tránh trần CPU/băng thông |
| Config + overlay: một lần tải, cache cục bộ theo `configVersion` | Không thêm lượt |

[CHƯA KIỂM] Hạn mức gói Cloudflare hiện tại của sếp (số lượt/ngày, CPU/lượt) và số người dùng hàng ngày thật — cần sếp/PM cung cấp trước khi chốt Worker.

## Thứ tự ưu tiên
| # | Việc | Rủi ro | Ai | Ghi chú |
|---|---|---|---|---|
| P0 | **Cổng G0–G3** (mục trên): Worker + chữ ký + trỏ config về Worker. Đây là việc duy nhất làm bản fork "không chạy được" | Rủi ro cao: đổi đường tải của mọi người dùng, cần giữ đường cũ + thử máy thật | G3: DeepSeek 1 (PM phân 01/11, kèm V157). G0–G2 (chữ ký, Worker feed/forum/Pixeldrain): chờ PM phân công, thi công do DeepSeek; Nova chỉ rà thiết kế. Sếp tạo secret, đổi repo packs |  |
| P1a | **Tách config**: `config/` trong repo chỉ còn phần công khai + `bootstrap`; phần nhạy cảm (donate, archivePasswords, downloadHosts…) nằm ở KV, Worker hợp nhất khi phục vụ | Trung bình: lần chạy đầu offline mất mục donate/mật khẩu nén → cần mặc định an toàn trong `MonikaConfig.kt` | DeepSeek 1 (PM phân 01/11, [#119](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6553841261)) → Luna duyệt. Đối chiếu thiết kế V181 (#443) | Phải tăng `configVersion` + đồng bộ Cloudflare (`sync-config.yml`); thử trên máy thật |
| P1b | Test config (`ConfigTest`) chấp nhận khóa vắng ở asset nhưng có ở overlay | Thấp | cùng PR P1a | |
| P1c | (Tùy chọn) đưa URL gói/engine về domain riêng qua Worker redirect | Thấp | sau P1a | Không bắt buộc |
| P2 | Worker: kiểm chứng thư APK (hash do app gửi), giới hạn tần suất, từ chối UA lạ; đổi giá trị nhạy cảm sang secret của Worker | Thấp–TB | Chờ PM phân công (thi công: DeepSeek); Nova chỉ rà | Chỉ chặn được "bản fork lười", không chặn người cố tình |
| P3 | Workflow: 10/16 file dùng secrets chưa có `if: github.repository == 'aowvn-10diem/aowvn-monika'` (`periodic-check, publish-cores, publish-pack, real-3ds, real-games, release, sync-config, telegram-bao-sep, test-lab-engine-games, test-lab`). Fork vốn không nhận secrets nên rủi ro thấp; thêm guard là phòng thủ sâu | Thấp (guard sai tên repo làm `release.yml` không chạy → thử bằng `workflow_dispatch` trước) | Chờ PM phân công (thi công: DeepSeek); Nova chỉ rà | PR riêng, sau khi sếp OK |
| P3b | Rà `pull_request` + secrets: `telegram-bao-sep`, `test-lab-engine-games` chạy trên PR; xác nhận không in secret ra log và không dùng `pull_request_target` | Thấp | Chờ PM phân công (thi công: DeepSeek); Nova chỉ rà | |
| P4 | Thêm `SECURITY.md`, `.github/CODEOWNERS` (cần tên tài khoản duyệt từ sếp), chính sách thương hiệu: tên "Aow Monika", logo, `applicationId` không được dùng cho bản fork | Thấp | Chờ PM phân công (thi công: DeepSeek); sếp duyệt tên người duyệt cho CODEOWNERS | Luật thương hiệu không thay được GPL |
| P5 | Phần **không thuộc GPL** (Worker, dữ liệu, CI vận hành, script nội bộ) có thể chuyển sang repo **riêng tư**; app (GPL) vẫn công khai | Cao (đổi cấu trúc, CI, `sync-config`) | Sếp quyết | Chỉ làm nếu P1–P4 chưa đủ |
| P6 | GitHub: bật Secret scanning + Push protection, bảo vệ nhánh `main`, tắt Actions cho fork nếu không cần | Thấp | Sếp (cài đặt repo) | Không sửa được từ phiên này |

## Việc ngoài tầm Nova (sếp tự làm)
- Firebase Console: giới hạn khóa (xem bảng trên).
- Cloudflare: đặt secret mới, bật rate limit/WAF cho Worker.
- Xoay vòng token nếu từng lộ (chưa thấy dấu hiệu lộ trong repo).

## Lộ trình đề xuất
1. Duyệt tài liệu này (PR docs, không chạy test).
2. P1a/P1b theo thiết kế PM chốt (chốt: phần nào vào overlay, mặc định khi offline).
3. P3 + P4 song song (không đụng mã ứng dụng).
4. P2 sau khi P1 chạy ổn. P5 chỉ khi sếp muốn.
