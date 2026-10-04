# Trả lời Sol 004: phân việc mới (04/10, lệnh sếp)

**Kết luận:** sếp yêu cầu dùng Sol và Luna tối đa để tiết kiệm hạn mức. Từ nay **Sonnet chỉ code lõi engine** (sửa lỗi bên trong Kirikiri, mkxp-z, Azahar, Ren'Py, J2ME Loader). Mọi việc app, CI, config, cài gói, chẩn đoán chuyển sang Sol.

## Việc chuyển từ Sonnet sang Sol

| Mã | Việc | Ghi chú |
|---|---|---|
| V25 | R6: chạy 5 game RPG Maker thật trên Emulator Test API 34 | Link game đã có trong dòng V25. Game chỉ tải lúc chạy: không commit, không đưa lên artifact. Lỗi nằm trong engine mkxp-z thì viết thư `hoi-sol-*` kèm log, PM chuyển cho Sonnet |
| V32 | Ghim phiên bản + sha256 theo ABI cho lõi GB/GBA/NES, có đường quay về bản tốt | Tôn trọng `cores.<id>.abis` |
| V22 | `engines.<id>.errorPatterns` (mặc định rỗng) + phát hiện màn đen bằng PixelCopy + `crash-reports.sh --by-fp` | Phần chẩn đoán, đúng mảng của Sol |
| V21 | Nút "Báo lỗi game này" (ảnh PixelCopy, chọn loại lỗi bằng nút, mô tả, vệt, logcat 60 giây) | A1 sếp đã chốt. Đọc giới hạn ảnh của Worker trước |
| V31 phần 3 | Mục `app` trong config (latestVersionCode/URL) đúng bản mới nhất | Làm sau khi sếp trả lời G7 trên trang tiến độ |
| V16 phần config | Đăng ký gói rgss vào config | Chỉ làm sau G8 (giấy phép) |

## Việc mới

| Mã | Việc | Đạt khi |
|---|---|---|
| V37 | **CI kiểm gói thật**: job tải mọi gói trong `modules` của config theo từng ABI khai báo, chạy đúng hàm kiểm của app (`PackTransaction.validate` + unzip) trên file thật. Chạy khi PR đụng `pack/`, `azahar/`, `config/` hoặc file engine, và chạy tay được | Bắt được lỗi như V35 (Azahar `libcamera2ndk.so`) mà không cần PM tải tay. Gói ONS hiện hỏng thì job báo rõ tên gói, chưa sửa xong V36 thì đánh dấu "biết trước", không làm đỏ cả job |

## Thứ tự

V35 (sửa theo comment PM) → **V37** → V25 → V32 → V36 → V22 → V21. V31 phần 3 khi có G7; V16 phần config khi có G8.

## Luật thêm

- Lỗi nằm bên trong engine (native, mã nguồn engine, script dựng engine): **không tự sửa**. Viết thư `hoi-sol-<số>-loi-engine.md` (log, bước tái hiện, commit), đánh dấu việc "kẹt (SOL-<số>)", làm việc khác. PM giao Sonnet.
- Luna sẽ "tiền duyệt" PR của Sol (L07): một comment kiểm phạm vi, CI, test. Sửa theo các mục Luna ghi "Cần sửa"; vẫn chỉ gộp khi có "PM duyệt".
