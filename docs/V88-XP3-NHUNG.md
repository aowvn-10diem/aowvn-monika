# V88 — XP3 nhúng trong exe (v0.8.2)

`Xp3Index.read` nhận cả kho XP3 và file MZ. Với MZ, quét chữ ký 11 byte theo khối 64 KiB có overlap, kiểm ngắt luồng khi timeout; không đọc toàn bộ exe vào heap. Vị trí chỉ mục (v1 hoặc v2) được cộng với offset chữ ký. Giới hạn chỉ mục vẫn 64 MiB, không giữ danh sách tên của nhiều kho đồng thời.

Resolver chọn exe có `startup.tjs` ở gốc khi không có kho XP3 thường phù hợp, ưu tiên exe cùng tên kho đang chọn; vẫn ưu tiên kho thường đã có startup. Không coi cờ `protected archive` là mã hóa. XP3 nhúng có dấu hiệu tên băm và không lộ script dùng cảnh báo mã hóa/chưa hỗ trợ hiện có (heuristic, **chưa xác minh mã hóa nội dung**). Chỉ mục hỏng/không đọc đủ không chứng minh mã hóa hay thiếu startup; giữ hành vi cũ kèm lý do. Nhãn thử nghiệm A26 trong config giữ nguyên.

Dòng `exe …` trong chẩn đoán có XP3 nhúng có/không, offset, số mục, `startup.tjs@gốc`; trường chưa biết dùng `?`/`—`. Chỉ metadata, không nội dung game. Chữ ký lệch căn 16 byte được đọc để chẩn đoán nhưng không truyền cho engine hiện tại; màn chuẩn bị báo rõ giới hạn này.

## Kiểm engine hiện tại

- Gói đúng URL `modules.kirikiri`, <https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/engines-kirikiri-14/kirikiri-arm64.zip>, tải ngày 11/10/2026 UTC, SHA-256 khớp config: `2166672a84137fe74eb2d87a352d7280114ffca23b0cc76cecd9a108b3813580`.
- `SOURCE.txt` và `manifest.json` trong gói xác nhận commit [`6e61ce3b81416ceb2be3427a5f0471edefab7151`](https://github.com/YuriSizuku/Kirikiroid2Yuri/tree/6e61ce3b81416ceb2be3427a5f0471edefab7151).
- [`XP3Archive.cpp:231`](https://github.com/YuriSizuku/Kirikiroid2Yuri/blob/6e61ce3b81416ceb2be3427a5f0471edefab7151/src/core/base/XP3Archive.cpp#L231): `TVPGetXP3ArchiveOffset` nhận MZ, tìm chữ ký đầu tiên căn 16 byte từ offset 16. Chỉ mục và segment cộng offset archive (dòng 376, 496). Patch Intent của Monika gọi `startupFrom(path)` nguyên đường dẫn; app không chạy exe như chương trình Windows.
- **[CHƯA KIỂM]** chạy startup/gameplay của exe nhúng trên Android thật; chưa có exe Kara no Shoujo của sếp để xác nhận có archive, offset hoặc mã hóa nội dung. Không kết luận 5339 KB là bằng chứng XP3 nhúng.

## Kiểm thử

Fixture tự sinh MZ + XP3: chỉ mục thô/zlib, v1/v2, offset >4 MiB; chữ ký cắt qua biên khối; vị trí chỉ mục hỏng/overflow; MZ không có kho. Resolver: sáu xp3 thiếu startup và exe chứa startup, exe trực tiếp, tên băm so với marker protected, chỉ mục hỏng và chữ ký không căn 16 byte. Chẩn đoán: các trường metadata, không đường dẫn/nội dung game.

`./gradlew testDebugUnitTest --console=plain` đã thử: dừng khi tải Gradle 8.14.3 (`java.net.ConnectException: Connection refused`), chưa chạy test local; kết quả CI ghi trong PR.
