# Hướng dẫn cập nhật và sửa lỗi AowVN Monika

## A. Việc KHÔNG cần phát hành app mới (chỉ sửa `config/monika-config.json`)

Sửa file, **tăng `configVersion` lên 1**, commit và push lên `main`. App của user sẽ lấy config mới khi mở app hoặc khi bấm "Cập nhật cấu hình" ở tab Trình chạy.

> App đọc config tại `https://aowvn-monika.aowvn-system.workers.dev/config.json` (Cloudflare Worker + KV, repo giữ private). Workflow `sync-config.yml` tự đẩy file lên khi push main; cần secret `CLOUDFLARE_API_TOKEN` (quyền Workers KV Storage: Edit). Mã Worker: `cloudflare/worker.js`.

### 1. Thêm/sửa link tải app ngoài (Kirikiroid2, JoiPlay...)

```json
{ "id": "kirikiroid2", "downloadUrl": "https://link-chinh-thuc/kirikiroid2.apk", ... }
```

- **Cách khuyên dùng:** để `downloadUrl` là trang aow.vn (vd. `https://www.aow.vn/p/tai-gia-lap-joiplay.html`). App tự đọc trang, hiện các nút tải theo đúng chữ trên nút trong trang. Link Pixeldrain → tải + cài ngay trong app. Sếp chỉ cần sửa trang trên Blogger, không cần đụng config.
- Link kết thúc bằng `.apk` hoặc link Pixeldrain → app tự tải, tải xong bấm thông báo để cài.
- Link trang web → app mở trình duyệt.
- `guide`: các bước hướng dẫn, mỗi dòng là 1 bước.
- `packageNames`: tên gói để app biết đã cài hay chưa. Xem tên gói: cài app rồi vào Cài đặt > Ứng dụng, hoặc xem trong link Google Play (`id=...`).

### 2. Thêm host tải

- Chỉ mở trình duyệt: `{ "name": "Tên", "host": "tenmien.com", "mode": "browser" }`
- Tải thẳng trong app: cần biết quy luật link trực tiếp, ví dụ Pixeldrain:
  `{ "host": "pixeldrain.com", "mode": "direct", "pattern": "/u/([A-Za-z0-9]+)", "directUrl": "https://pixeldrain.com/api/file/$1?download" }`
  (`$1` = phần bắt được trong ngoặc của `pattern`.)

### 3. Nâng cấp lõi giả lập

Đổi `version` của lõi (ví dụ `"1"` → `"2"`), app sẽ tải lại lõi mới nhất lần chơi tới. Đổi lõi của 1 hệ: sửa `core` trong `systems` (ví dụ NDS từ `desmume` sang `melonds`).

### 4. Thêm hệ máy mới dùng libretro

1. Thêm lõi vào `cores` (link lấy ở `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/`, thay `arm64-v8a` bằng `{abi}`).
2. Thêm hệ vào `systems` với `"runner": "libretro"`, `core`, `extensions`, `labels` (nhãn trên blog).

### 5. Nhận diện game dạng thư mục

Thêm quy tắc vào `engines`: `markers` = file đặc trưng của engine, `entry` = file mở để chạy.

### 6. Mật khẩu file nén

Thêm mật khẩu aow.vn hay dùng vào `archivePasswords` (ví dụ `["aowvn.org"]`). App tự thử lần lượt khi giải nén; sai hết thì hỏi user.

### 7. Báo có bản app mới

Sửa khối `app`: `latestVersionCode`, `latestVersionName`, `apkUrl`, `changelog`. User thấy thẻ "Có bản mới" ở tab Trình chạy.

### 8. Mục "Màn hình" trong Cài đặt (NDS)
`cores.<id>.screen` (hiện chỉ `melondsds`) quyết định mục **Cài đặt → Màn hình**: `layoutKey` + `layouts[]` (bố cục: `id`, `label`, `value` gửi cho lõi, `usesRatio` nếu bố cục cần tỉ lệ), `ratioKey` + `ratios[]`, `gapKey` + `gapMax` (khoảng cách hai màn, điểm ảnh). Mặc định nằm ở `cores.<id>.options` (tùy chọn lõi); lựa chọn của người chơi lưu cùng nơi với bảng "Tùy chọn giả lập" và có hiệu lực khi mở game tiếp theo. Khóa/giá trị phải có thật trong lõi (đổi lõi thì chạy `python3 scripts/audit-cores.py`). Thêm hệ máy mới có nhiều màn hình: chỉ cần thêm khối `screen`, không sửa mã.

## B. Phát hành bản app mới

1. Tăng `versionCode` và `versionName` trong `app/build.gradle.kts`.
2. Push tag: `git tag v0.2.0 && git push origin v0.2.0`.
3. GitHub Actions build APK đã ký và đăng lên Releases.
4. Cập nhật khối `app` trong config (mục A.7) để user được nhắc.

### Tạo khóa ký lần đầu (chỉ làm 1 lần, GIỮ KỸ, mất là user phải gỡ app cài lại)

```bash
# Đã tạo sẵn khóa chung AowVN (alias: aowvn). Chỉ tạo mới nếu mất khóa:
keytool -genkeypair -storetype PKCS12 -keystore aowvn-release.jks -keyalg RSA -keysize 4096 -validity 36500 -alias aowvn
base64 -w0 aowvn-release.jks   # copy kết quả
```

Vào GitHub repo > Settings > Secrets and variables > Actions, thêm 4 secret:
`MONIKA_KEYSTORE_BASE64`, `MONIKA_KEYSTORE_PASSWORD`, `MONIKA_KEY_ALIAS` (= aowvn), `MONIKA_KEY_PASSWORD`.

## C. Sửa lỗi thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Cách sửa |
|---|---|---|
| Tab Bài viết báo lỗi HTTP 403 | Cloudflare chặn (user ở nước ngoài, bị rule "Chặn toàn cầu") | Chủ ý theo cấu hình Cloudflare hiện tại |
| Game báo "Chưa nhận diện" | Đuôi file/engine chưa có trong config | Thêm `extensions` hoặc quy tắc `engines` |
| "Không tải được lõi giả lập" | Link buildbot đổi/tạm lỗi | Kiểm tra link trong `cores`, đổi `version` để tải lại |
| Game hiện "Chưa giải nén" | Sai/thiếu mật khẩu, file nén chia nhiều phần (part1, part2), hoặc 7z có mật khẩu | Bấm "Giải nén" và nhập mật khẩu; thêm mật khẩu hay dùng vào `archivePasswords`; file chia phần/7z có mật khẩu thì giải nén bằng ZArchiver |
| Push config xong CI đỏ | Config sai (lõi/app ngoài không tồn tại, trùng id...) | Đọc thông báo lỗi của `ConfigTest` trong tab Actions |
