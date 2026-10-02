# Kế hoạch: game Kirikiri (XP3) — Kirikiroid2-debloated

Nguồn nghiên cứu: https://github.com/enaix/Kirikiroid2-debloated (commit a216b63, 25/07/2026).

## Kết quả đọc repo
- Đây **không phải mã nguồn gốc** mà là bản **giải mã APK (apktool, smali)** của Kirikiroid2 và Kirikiroid2Yuri, đã vá: bỏ quảng cáo `cn.waps` + lấy device id, chạy được Android 14, đổi package để khỏi trùng bản gốc. APK dựng sẵn nằm ở **Releases** của repo (sandbox không đọc được trang Releases nên **chưa biết tên file / dung lượng APK** — cần sếp gửi link).
- Bản nên dùng: **Yuri** (`kiri2_yuri`, v1.4.1, versionCode 70, `minSdk 29`). Package `org.tvp.kirikiri2_yuri_debloated_10309`, activity `org.tvp.kirikiri2_yuri_10309.Kirikiroid2`.
- Lõi: `libgame.so` (cocos2d-x + engine Kirikiri Z) arm64 27,9 MB / armv7 17,8 MB, `libffmpeg.so` 10,4 / 8,6 MB, `libSDL2.so` ~1 MB. Chỉ có arm64-v8a và armeabi-v7a.
- Giấy phép: kiểu BSD sửa đổi (W.Dee + contributors) — cho phép phân phối lại nếu giữ nguyên thông báo bản quyền; kèm libjpeg-turbo. Không phải GPL.
- **Không có cách truyền đường dẫn game bằng Intent.** Activity chỉ xử lý dữ liệu `launch_ext` (mở hộp thoại chọn thẻ nhớ); còn lại người chơi chọn thư mục game trong trình duyệt file riêng của app. Cần quyền "Quản lý mọi tệp".

## Phương án
| | Cách | Ưu | Nhược |
|---|---|---|---|
| **A (đề xuất)** | App ngoài: Monika tải APK debloated (khi người chơi bấm game XP3 lần đầu, theo luật mạng ≤15 MB tự tải / lớn hơn hỏi), cài bằng bộ cài APK sẵn có, rồi mở app | APK Monika vẫn ~19 MB; không đụng giấy phép; giống luồng Symbian/EKA2L1 đã làm | Không mở thẳng đúng game (người chơi chọn thư mục trong Kirikiroid2); cần cho phép "cài app lạ" |
| B | Nhúng như gói tải thêm (`libgame.so` + `libffmpeg.so` + dex + assets) chạy trong tiến trình Monika | Trải nghiệm liền mạch | Phải dựng lại từ smali → dex, gộp `res/`+`assets/ui` (85 MB), khai báo activity cocos2d/SDL, theo dõi vá lỗi từng bản — rủi ro cao, nặng bảo trì |
| C | Tự build Kirikiroid2Yuri từ mã nguồn | Sạch, tùy biến được | Nguồn Kirikiroid2 thiếu thư mục `vendor/` (đã kiểm trước đó) → chưa build được |

## Việc làm (nếu duyệt A)
1. Sếp gửi link release APK (hoặc em ghim đúng tên file + SHA-256 khi có link).
2. Cấu hình `modules.kirikiri` (url, sha256, size) + cơ chế "app ngoài cần cài" dùng lại `apkinstall`.
3. Nhận diện `.xp3` / thư mục có `startup.tjs`, `data.xp3` → hệ "Kirikiri" ở Thư viện; bấm chơi → kiểm tra đã cài → cài nếu thiếu → mở Kirikiroid2.
4. Mẹo cho người chơi: hiển thị hướng dẫn 1 dòng "chọn thư mục game trong Kirikiroid2" + nút mở sẵn.
5. Nhật ký lỗi: nhãn `engine:kirikiri`; lỗi tải/cài ghi `pack:kirikiri`.


## Cập nhật 03/10/2026 — ĐÃ NHÚNG SÂU (phương án B, bản nhẹ)
- Dựng lại được từ mã nguồn bằng CI (`.github/workflows/build-kirikiri.yml`, Kirikiroid2Yuri @ 6e61ce3 + bản vá ở `kirikiri/patches/`). Gói `engines-kirikiri-13` (arm64, 17,1 MB) ở kho `aowvn-monika-packs`.
- Mã Java ở module `:kirikiri`; `KirikiriGameActivity` chạy ở tiến trình `:game`; `libkrkr2yuri.so` + tài nguyên (ui, font) là gói tải thêm theo luật mạng (PackManager). Không còn APK riêng; máy 32-bit/chưa có gói → quay về app ngoài.
- Emulator Test (Android 11 và 14, giả lập x86 chạy ARM qua lớp dịch): nạp được lib ngoài APK, màn chọn thư mục của Kirikiri vẽ ra đủ chữ và tài nguyên, không sập. **Chưa thử với game .xp3 thật trên máy thật.**
- Muốn cập nhật engine: chạy workflow "Build Kirikiri" (publish=true) → lấy sha256/size từ release `.sha256` → sửa `modules.kirikiri` trong config; đổi mã Java thì chép lại từ artifact `kirikiri-embed-inputs` (đã vá).
