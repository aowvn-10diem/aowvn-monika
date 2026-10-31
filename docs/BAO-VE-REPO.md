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

## Thứ tự ưu tiên
| # | Việc | Rủi ro | Ai | Ghi chú |
|---|---|---|---|---|
| P1a | **Tách config**: `config/` trong repo chỉ còn phần công khai + `bootstrap`; phần nhạy cảm (donate, archivePasswords, downloadHosts…) nằm ở KV, Worker hợp nhất khi phục vụ | Trung bình: lần chạy đầu offline mất mục donate/mật khẩu nén → cần mặc định an toàn trong `MonikaConfig.kt` | PM chốt thiết kế → Sonnet làm → Luna duyệt | Phải tăng `configVersion` + đồng bộ Cloudflare (`sync-config.yml`); thử trên máy thật |
| P1b | Test config (`ConfigTest`) chấp nhận khóa vắng ở asset nhưng có ở overlay | Thấp | cùng PR P1a | |
| P1c | (Tùy chọn) đưa URL gói/engine về domain riêng qua Worker redirect | Thấp | sau P1a | Không bắt buộc |
| P2 | Worker: kiểm chứng thư APK (hash do app gửi), giới hạn tần suất, từ chối UA lạ; đổi giá trị nhạy cảm sang secret của Worker | Thấp–TB | Sonnet | Chỉ chặn được "bản fork lười", không chặn người cố tình |
| P3 | Workflow: 10/16 file dùng secrets chưa có `if: github.repository == 'aowvn-10diem/aowvn-monika'` (`periodic-check, publish-cores, publish-pack, real-3ds, real-games, release, sync-config, telegram-bao-sep, test-lab-engine-games, test-lab`). Fork vốn không nhận secrets nên rủi ro thấp; thêm guard là phòng thủ sâu | Thấp (guard sai tên repo làm `release.yml` không chạy → thử bằng `workflow_dispatch` trước) | Nova | PR riêng, sau khi sếp OK |
| P3b | Rà `pull_request` + secrets: `telegram-bao-sep`, `test-lab-engine-games` chạy trên PR; xác nhận không in secret ra log và không dùng `pull_request_target` | Thấp | Nova | |
| P4 | Thêm `SECURITY.md`, `.github/CODEOWNERS` (cần tên tài khoản duyệt từ sếp), chính sách thương hiệu: tên "Aow Monika", logo, `applicationId` không được dùng cho bản fork | Thấp | Nova soạn, sếp duyệt | Luật thương hiệu không thay được GPL |
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
