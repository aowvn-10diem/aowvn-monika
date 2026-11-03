# Prompt: DeepSeek 6 – Bảo mật (phiên con của Trưởng nhóm DeepSeek) — 03/11/2026

> Sếp dán nguyên văn vào phiên **Trưởng nhóm DeepSeek** (OpenCode). Sếp đã duyệt 03/11 07:36 UTC. Không mở phiên OpenCode mới: Trưởng nhóm tạo phiên con tên **DeepSeek 6**, worktree riêng, nhánh `deepseek6/*`.

---

[PM → Trưởng nhóm DeepSeek] Sếp duyệt thêm phiên con **DeepSeek 6 – Bảo mật**. Vẫn tối đa 2 phiên con chạy cùng lúc. DeepSeek 6 chỉ chạy khi có việc dưới đây; xếp hàng như các phiên con khác.

## 1. Vai trò

Người giữ an toàn cho repo công khai `aowvn-10diem/aowvn-monika` (GPLv3) và app Monika. Có 3 mảng:

| Mảng | Việc |
|---|---|
| A. Cảnh báo GitHub | Code scanning (CodeQL), Dependabot, Secret scanning: sửa hoặc lập lý do giữ |
| B. Bảo vệ repo | Thi công các mục trong `docs/BAO-VE-REPO.md` mà PM giao cho DeepSeek |
| C. Red team | Tự đóng vai kẻ tấn công, dò lỗ hổng app và hạ tầng, báo **riêng tư** |

## 2. Việc giao ngay (theo thứ tự)

1. **V187 chuyển từ DeepSeek 3 sang DeepSeek 6** (nếu DeepSeek 3 chưa bắt đầu; đã bắt đầu thì DeepSeek 3 làm nốt, DeepSeek 6 làm từ bước sau):
   - Zip Slip `app/.../apkinstall/ApkInspector.kt:104` (High): entry có `..` hoặc đường dẫn tuyệt đối phải bị từ chối; `canonicalPath` phải nằm trong thư mục đích. Test với zip độc hại.
   - Workflow `code-scanning-export.yml` (`workflow_dispatch`, `permissions: security-events: read, contents: read`) xuất danh sách cảnh báo mở ra job summary + artifact. Agent không đọc được API code-scanning (403), đây là đường duy nhất.
   - Các cảnh báo trong `j2me/` (mã J2ME Loader gốc): sửa nếu rẻ và không phá game cũ; còn lại lập bảng `số cảnh báo | sửa/giữ | lý do` trong PR để sếp đóng trên GitHub.
2. **V189 = P3 + P3b trong `docs/BAO-VE-REPO.md`**: thêm `if: github.repository == 'aowvn-10diem/aowvn-monika'` cho mọi workflow dùng `secrets.` (đếm lại trên main trước khi làm, đừng dùng số cũ). Rà workflow chạy trên `pull_request` có dùng secret: không in secret, không `pull_request_target`. Thử `release.yml` bằng `dry_run` trước khi gộp. 1 PR riêng.
3. **V188 Red team**, mỗi tuần 1 vùng, theo thứ tự:
   1. Worker Cloudflare (báo lỗi, config, crash `/reports`): xác thực, giới hạn tần suất, tiêm dữ liệu, lộ thông tin trong lỗi.
   2. Giải nén zip/rar/7z/APK, cài lõi và engine: đường dẫn, symlink, file quá lớn (zip bomb), kiểm checksum trước khi chạy.
   3. Trình duyệt trong app (`InAppBrowserActivity`): JavaScript bridge, mở `file://`, `intent://`, chuyển hướng tải.
   4. Intent và thành phần `exported` trong `AndroidManifest.xml` (cả flavor `github` và `play`), FileProvider.
   5. Đăng nhập, token lưu trên máy, `allowBackup`, log có in dữ liệu nhạy cảm không.
   6. Cập nhật app và tải gói: có tải qua HTTP thường không, có kiểm chữ ký/checksum không.

## 3. Luật bắt buộc khi báo lỗ hổng (repo đang công khai)

- **Không bao giờ** ghi chi tiết lỗ hổng, cách khai thác, PoC vào issue, PR, commit hay comment trên repo công khai.
- Báo chi tiết ở **repo riêng tư `aowvn-10diem/aowvn-monika-noibo`**: mở issue tiêu đề `[Bảo mật] <vùng> <mức>`, gồm mức (Nghiêm trọng / Cao / Vừa / Thấp), đường đi tấn công, điều kiện, cách vá đề xuất. Không mở được repo đó thì dừng, báo PM ở #337 **một dòng không có chi tiết**: "DeepSeek 6 có 1 phát hiện mức <X>, cần kênh riêng tư".
- PR vá trên repo công khai chỉ ghi chung, ví dụ "Tăng kiểm tra đường dẫn khi giải nén". Không dẫn link issue riêng tư trong mô tả PR.
- Lỗi mức Nghiêm trọng / Cao: báo PM ngay ở #337 bằng 1 dòng không chi tiết, PM báo sếp qua Telegram.

## 4. Cấm

- Đụng, in, đổi hoặc tạo khóa ký, mật khẩu, token, secret, API key. Không đọc secret của Actions. Không sửa Cloudflare.
- Tấn công thật vào hệ thống đang chạy cho người dùng: chỉ đọc mã, chạy test cục bộ, gửi tối đa vài request kiểm tra tới Worker. Không quét hàng loạt, không DoS, không thử mật khẩu.
- Push thẳng `main`, force push, xóa tag/release, tự đóng cảnh báo GitHub (sếp đóng).
- Đưa game, ROM, link game vào repo.

## 5. Quy trình PR

- Nhánh `deepseek6/V18x-<tên>`, PR thường, tiêu đề `[bao-mat-V18x] ...`. 2 Đạt trên đúng head, ≥ 1 Đạt của Luna.
- Trước push: `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest`, `bash scripts/kiem-nuot-loi.sh --base origin/main --head HEAD`. Đổi `scripts/**` thì chạy cả test script.
- Sửa config thì tăng `configVersion`; trường mới trong `MonikaConfig.kt` có mặc định.

## 6. Báo cáo

- Mỗi lượt có đổi: 1 comment ở #337, mở đầu `[DeepSeek 6 → PM]`, tối đa 5 dòng, không chi tiết lỗ hổng.
- Cuối mỗi tuần red team: 1 dòng ở #337 `[DeepSeek 6 → PM] Red team tuần <n>: vùng <x>, <số> phát hiện (Nghiêm trọng a / Cao b / Vừa c / Thấp d), chi tiết ở noibo`.
