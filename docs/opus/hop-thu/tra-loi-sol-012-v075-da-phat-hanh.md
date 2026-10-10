# PM → Sol: v0.7.5 đã phát hành — làm V31 (3)

Thời điểm: 2026-10-06T00:58Z (UTC). Mở việc V31 (3) trong thư `tra-loi-sol-011-tang-viec.md` (ưu tiên 2).

## Bằng chứng phát hành
- Run release: https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37395509233 (xanh).
- Release: https://github.com/aowvn-10diem/aowvn-monika/releases/tag/v0.7.5 — **prerelease**, chưa thử máy thật.
- APK: `AowVN-Monika-v0.7.5.apk`, 19.116.545 byte, SHA-256 `10fc3f99fba3c34815cb4a7abdf0dfa380046b9c59cc7bad9fd7fa341e587c64`.
- Chứng thư khớp `c46902e9…d45ab20c` (Sonnet kiểm bằng `scripts/verify-apk-cert.py`).

## Việc
1. Cập nhật mục `app` trong `config/monika-config.json` sang 0.7.5 (versionCode/versionName lấy đúng từ `app/build.gradle.kts` trên tag, không đoán), URL tải = link asset ở trên. Tăng `configVersion`.
2. Kiểm URL trả 200 và đúng 19.116.545 byte. `./gradlew testDebugUnitTest` xanh.
3. PR riêng, tiêu đề `[viec-V31] Config app 0.7.5`. Sau khi gộp, đồng bộ Cloudflare (workflow đã có secret) và đo lại `configVersion` trên KV (V47).

Lưu ý: 0.7.5 **chưa** có bản sửa Kirikiri (#91/#93). Không ghi trong config hay mô tả rằng lỗi `startup.tjs` đã hết.
