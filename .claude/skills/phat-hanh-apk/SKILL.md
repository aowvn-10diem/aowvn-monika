---
name: phat-hanh-apk
description: Build, ký, kiểm chữ ký và gửi APK Aow Monika (release R8, 1 APK universal, Pixeldrain, engine Azahar). Dùng khi sếp/PM bảo phát hành, build APK, gửi APK, hoặc trước khi báo "thiếu khóa/secret".
---

# Phát hành APK — đọc hết trước khi báo "thiếu" bất cứ thứ gì

Phát hành mặc định qua `release.yml` trên CI (lần thật đầu là V43). GitHub Secrets ký app đã có (sếp xác nhận tự xem ở Settings → Secrets: 4 secret `MONIKA_KEYSTORE_*` / `MONIKA_KEY_*`, `PACKS_TOKEN`, `PIXELDRAIN_API_KEY`). `release.yml` chưa chép engine 3DS (bước 3 vẫn phải xử lý riêng). Bước 1 và 4 dưới đây là cách cũ (build/ký/upload trên máy phiên), chỉ dùng khi CI hỏng. Sau khi build xong, vẫn kiểm chứng thư bằng `scripts/verify-apk-cert.py` (bước 2).

**Luật cứng:** không commit keystore/mật khẩu ký/token; không in chúng ra log hay chat. Mỗi phiên bản chỉ phát hành **1 APK: bản universal** (`AowVN-Monika-<tag>.apk`, quyết định của sếp). Bản chưa có kết quả máy thật luôn là prerelease.

## Cấu hình build (R8 + tách APK)
Bản release bật R8, build tách APK theo chip (arm64-v8a, armeabi-v7a, universal; bỏ x86) nhưng chỉ phát hành universal. Thêm thư viện có JNI/reflection → thêm luật `-keep` vào `app/proguard-rules.pro`, rồi kiểm lớp còn trong dex bằng `build-tools/*/dexdump`. Kiểm mapping R8 không đổi tên lớp hệ thống: `python3 scripts/check-r8-mapping.py app/build/outputs/mapping/release/mapping.txt`.

## Các bước
1. **Khóa ký** nằm trong thư mục scratchpad của phiên: `<scratchpad>/keystore/` gồm `aowvn-release.jks`, `aowvn-release.jks.base64` và `THONG-TIN-KHOA.txt` (các dòng `ALIAS:`, `STORE PASSWORD:`, `KEY PASSWORD:`). Nạp vào biến môi trường **mà không in ra**:
   ```bash
   K=<scratchpad>/keystore; f=$K/THONG-TIN-KHOA.txt; v(){ grep -m1 "^$1:" "$f" | sed -E "s/^$1:[[:space:]]*//"; }
   MONIKA_KEYSTORE=$K/aowvn-release.jks MONIKA_KEYSTORE_PASSWORD="$(v 'STORE PASSWORD')" \
   MONIKA_KEY_ALIAS="$(v ALIAS)" MONIKA_KEY_PASSWORD="$(v 'KEY PASSWORD')" ./gradlew assembleRelease
   ```
   Scratchpad nằm trong `/tmp`, sẽ **mất khi container bị thu hồi**. Mất khóa thì mọi bản sau phải gỡ app cài lại, nên đừng tạo khóa mới. Hỏi chủ repo, và nhắc chủ repo đưa khóa vào GitHub Secrets (`docs/CAP-NHAT.md`: 4 secret `MONIKA_KEYSTORE_BASE64`, `MONIKA_KEYSTORE_PASSWORD`, `MONIKA_KEY_ALIAS`, `MONIKA_KEY_PASSWORD`). Không dán khóa vào chat/repo.
2. **Kiểm chữ ký** sau khi build: `$SDK/build-tools/*/apksigner verify --print-certs` (lấy SDK trong `local.properties`, vì `$ANDROID_HOME` trống), hoặc `python3 scripts/verify-apk-cert.py <apk>`. SHA-256 đúng là `c46902e9…d45ab20c` (đầy đủ trong `scripts/verify-apk-cert.py`). Khác số này thì chủ repo phải gỡ app cũ, **không được gửi**.
3. **Engine 3DS (Azahar)**: repo private nên app không tải được asset trong Releases. Phải chép `azahar-android-arm64.zip` của release `engine-azahar-*` mới nhất vào `app/src/main/assets/engines/azahar.zip` trước khi build (thư mục này đã gitignore). Tải asset bằng API kèm `$GH_TOKEN` (`Accept: application/octet-stream`). Link `browser_download_url` không kèm token sẽ trả "Not Found". [CHƯA KIỂM] còn đúng sau khi repo công khai và gói nằm ở repo `aowvn-monika-packs` (config `modules.azahar.url` đã trỏ repo packs) — kiểm trước khi làm bước này.
4. **Pixeldrain**: key do chủ repo đưa và đồng ý dùng lại cho mọi lần upload. Key lưu ở `<scratchpad>/keystore/pixeldrain.key`, dùng bằng `PIXELDRAIN_API_KEY=$(cat <scratchpad>/keystore/pixeldrain.key) scripts/pixeldrain-upload.sh <apk>` (in link `pixeldrain.com/u/{id}`). APK > 30 MB không gửi qua chat được. Key cũng được chủ repo đưa vào GitHub Secret `PIXELDRAIN_API_KEY` để CI dùng. Không có file đó thì hỏi lại chủ repo. **Đừng lục lịch sử hội thoại** để lấy key vì bị chặn, và cũng không nên.
5. **Đẩy tag bị proxy của phiên chặn** (`git push origin v…` báo "remote end hung up"). Push nhánh `main` vẫn bình thường. Release trên CI chạy tay bằng `workflow_dispatch` (tham số `tag`).
6. Lỗi "auto mode classifier gave no verdict" là **sự cố máy chủ kiểm duyệt**, không phải lỗi lệnh. Đừng suy ra thiếu công cụ hay quyền. Chuyển sang Read/Edit/Grep, rồi thử lại Bash sau.
7. Token GitHub của phiên **không tạo được Secrets** (API trả 403). Secret phải do chủ repo tự thêm ở Settings → Secrets and variables → Actions.

## Trước/sau khi build
- Tăng `versionCode`, `versionName` trong `app/build.gradle.kts`; tăng `configVersion` nếu sửa `config/monika-config.json`. Muốn user thấy thẻ "Có bản mới": sửa khối `app` trong config (`latestVersionCode`, `latestVersionName`, `apkUrl`, `changelog`) — xem `docs/CAP-NHAT.md` mục A.7, B.
- `./gradlew testDebugUnitTest` phải xanh trước khi build; đừng nối `test; commit && push` mà không kiểm mã thoát test.
- Workflow `release.yml` có hai cửa chặn (V30): chặn khi thiếu cấu hình ký/tag sai, và chặn APK ký bằng chứng chỉ "Android Debug" rồi ghi SHA-256. Thử cửa chặn bằng dữ liệu giả: `python3 scripts/test-release-guards.py`.
- Chữ ký, `configVersion`, `modules.*` phải đúng chỗ — `ConfigTest` chặn.
- Không đẩy tag/phát hành khi PM chưa báo; ghi CHANGELOG mỗi tag (việc của Luna).
