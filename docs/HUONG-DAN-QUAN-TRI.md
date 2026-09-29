# Hướng dẫn quản trị Aow Monika (việc sếp cần làm)

## 1. Bật đồng bộ config tự động (làm 1 lần, ~3 phút)
Sau bước này, sửa `config/monika-config.json` rồi push lên `main` là Cloudflare tự cập nhật, không cần nhờ đẩy tay.

1. Vào https://dash.cloudflare.com/profile/api-tokens → **Create Token** → **Create Custom Token**.
2. Permissions: **Account** → **Workers KV Storage** → **Edit**. Account Resources: chọn đúng tài khoản AowVN. Bấm Continue → Create Token → **copy token** (chỉ hiện 1 lần).
3. GitHub repo `aowvn-monika` → **Settings → Secrets and variables → Actions → New repository secret**:
   Name = `CLOUDFLARE_API_TOKEN`, Secret = token vừa copy.
4. Thử: **Actions → "Sync config lên Cloudflare" → Run workflow** — chạy xanh là xong.

## 2. Dựng engine visual novel (ONScripter trước)
1. GitHub repo → **Actions → "Build engines (visual novel)" → Run workflow** (để mặc định `master`).
2. Chờ ~15–30 phút. Thành công: có Release mới tên `engines-onsyuri-<số>` chứa `onsyuri-web.zip`.
3. Xong báo lại tôi, kèm nếu lỗi thì copy phần log đỏ (hoặc ảnh chụp). Tôi sẽ nối engine vào app (tải khi người chơi có game ONScripter).
   Lần đầu dễ lỗi do emscripten đổi phiên bản; đó là chuyện thường, chỉ cần gửi log.

## 3. Xem báo lỗi người chơi gửi về
- Địa chỉ nhận: `https://aowvn-monika-crash.aowvn-system.workers.dev` (Cloudflare Worker, lưu 60 ngày, ẩn danh).
- Cần **mã quản trị** (tôi đã đưa sếp trong chat; hãy lưu vào trình quản lý mật khẩu — mất thì tôi dựng lại mã mới).
- Xem nhanh trên máy tính có bash:
  ```
  CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh            # thống kê theo lõi + báo cáo mới
  CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh "<id>"     # chi tiết 1 báo cáo
  ```
  Hoặc gửi cho tôi mã + bảo "xem báo lỗi", tôi đọc và tìm lõi/hệ máy hay lỗi.
- Người chơi bấm **Gửi báo lỗi** trong hộp thoại khi game sập (hoặc Cài đặt → Nhật ký lỗi) là báo về đây.

## 4. Quy trình sửa lõi khi có lỗi
1. Xem thống kê: lõi nào, giai đoạn nào (`loading-game` = lỗi nạp game/ROM; `first-frame` = sập lúc chơi).
2. Nếu lỗi do bản nightly mới của lõi: sửa `config` → đổi `version` của lõi (ép tải lại) hoặc trỏ `url` sang bản cũ; nếu cần bỏ hẳn lõi lỗi, đổi `core` của hệ đó sang `altCores` khác.
3. Push config lên `main` → (nếu đã làm mục 1) app nhận trong vài phút, không cần APK mới.

## 5. Đã quyết: làm sau theo hướng app ngoài
PS2, GameCube/Wii, Saturn, Arcade (Neo Geo/MAME), DOS.
