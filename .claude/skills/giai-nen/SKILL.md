---
name: giai-nen
description: Giải nén game trong Aow Monika (zip, rar/rar5, 7z, mật khẩu, nhiều phần; 7-Zip native tải theo gói). Dùng khi sửa/gỡ lỗi nhập game, ZipExtractor, SevenZipNative, SevenZipExtractor, hoặc tạo file nén mẫu để test.
---

# Giải nén

Thứ tự thử (mã ở `app/src/main/java/vn/aow/monika/library/`):
1. `.zip` → **zip4j** (`ZipExtractor.kt`, Java thuần, mật khẩu ZipCrypto/AES).
2. rar / 7z (và zip lạ) → **7-Zip native** (`SevenZipNative.kt`): RAR/RAR5 có mật khẩu, nhiều phần. Thư viện `.so` **KHÔNG nằm trong APK** mà tải khi cần từ gói `modules.sevenzip` (config; cài bằng `app/src/main/java/vn/aow/monika/pack/`: `PackManager`, `SimpleModule`, `PackTransaction`).
3. 7z thuần Java (`SevenZipExtractor.kt`, Commons Compress; test `SevenZipTest` với file mẫu `app/src/test/resources/7z/`).

**Không còn libarchive** (đã bỏ). Ghi chú lịch sử: bản libarchive 1.1.6 là bản cuối dùng được (1.1.7 đòi compileSdk 37); libarchive từng lỗi với 7z có mật khẩu nên mới có `SevenZipExtractor`. Cách dùng cũ: giải nén bằng `readOpenFd` + `readDataIntoFd`, `readNextHeader` trả 0 khi hết file. [CHƯA KIỂM] còn dòng mã libarchive nào trong repo hay không.

Hỗ trợ: zip/rar/rar5/7z, file chia nhiều phần, % tiến độ (thông báo + màn Thư viện). Mật khẩu file nén aow.vn: `aowvn.org` (ghi cuối bài; config `archivePasswords`, app thử lần lượt rồi hỏi user).

## Test
- Test chạy `.so` Linux **cùng bản 16.02** (Gradle tự giải nén vào `build/sevenzip-natives`).
- File mẫu RAR tạo bằng `rar` với `LC_ALL=C.UTF-8` (không thì tên tiếng Việt hỏng).
- Gói `sevenzip` thật kiểm bằng job V37 (`.github/workflows/check-packs.yml`); sau khi đổi gói phải cập nhật `modules.sevenzip` (version, `sha256ByAbi`, `sizeByAbi`) và tăng `configVersion`.
- Chưa test trên máy thật (mọi thứ mới build + unit test).
