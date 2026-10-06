---
name: kiem-phat-hanh
description: Kiểm một bản Aow Monika vừa phát hành trên GitHub Releases (lượt release.yml xanh, APK đúng chứng thư, prerelease, SHA-256) rồi báo PM 3 dòng. Dùng sau khi sếp tạo release hoặc PM báo "kiểm bản vX.Y.Z". Không dùng để phát hành.
---

# Kiểm bản vừa phát hành

Chỉ đọc và kiểm. **Không** dispatch `release.yml`, không đẩy tag, không chạy lại run đỏ (mỗi lần phát hành cần sếp đồng ý riêng).

## Các bước
1. `get_release_by_tag` với tag `vX.Y.Z`: ghi `prerelease`, `target_commitish`, danh sách asset. Chưa có asset APK thì xem lượt mới nhất của `release.yml`. Run đang chạy thì dừng, lượt sau kiểm lại. Run đỏ thì lấy đúng dòng lỗi (grep `error|FAILED`) gửi PM, dừng.
2. Tải APK về scratchpad: `curl -sSL -o "$SCRATCH/app.apk" <browser_download_url>`.
3. `sha256sum "$SCRATCH/app.apk"`. Đối chiếu với `SHA256SUMS-vX.Y.Z.txt` và trường `digest` của asset.
4. `python3 scripts/verify-apk-cert.py "$SCRATCH/app.apk"`. Phải in khớp `c46902e9…d45ab20c` và thoát 0.
5. Báo PM đúng 3 dòng:
   ```
   vX.Y.Z: run <ID> xanh, prerelease=<true|false>, target <sha7>
   APK: <browser_download_url> (<số byte> byte)
   SHA-256 <hash> · chứng thư khớp
   ```
6. Xóa APK khỏi scratchpad.

## Sau khi kiểm đạt (PM giao riêng)
Cập nhật khối `app` trong `config/monika-config.json` (versionCode/versionName đọc từ `app/build.gradle.kts` trên tag, `apkUrl` = link APK, changelog ngắn, ghi rõ bản thử nếu prerelease), tăng `configVersion` và test ConfigTest. Làm thành một PR riêng.
