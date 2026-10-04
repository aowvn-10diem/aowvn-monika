# AowVN Monika

App Android "tất cả trong một" cho cộng đồng [aow.vn](https://www.aow.vn): đọc bài viết, nhận thông báo bài mới, tải game và chơi ngay trong app.

## Tính năng

| Mảng | Nội dung |
|---|---|
| Bài viết | Đọc feed Blogger của aow.vn, lọc theo nhãn, xem link tải trong bài |
| Thông báo | App tự kiểm tra bài mới định kỳ (mặc định 60 phút), lọc theo nhãn user chọn |
| Tải game | Host hỗ trợ tải thẳng (Pixeldrain) → tải nền rồi tự giải nén vào thư viện. Host khác → mở trình duyệt, rồi "Thêm game từ máy" |
| Giải nén | ZIP dùng zip4j; RAR, 7z và ZIP lạ dùng gói 7-Zip tải khi cần. Có hỗ trợ mật khẩu và thử các mật khẩu trong config |
| Giả lập nhúng sẵn | NDS, GBA, GBC, PS1, PSP, RPG Maker 2000/2003 (lõi tải khi cần), Kirikiri (gói engine tải khi cần) |
| Chạy dạng web | Flash (Ruffle), RPG Maker MV/MZ, TyranoScript, ONScripter |
| APK | Mở trình cài đặt Android |
| Java (J2ME) | J2ME Loader nhúng sẵn (Apache-2.0), phím ảo kiểu Monika — xem docs/J2ME-LOADER.md |
| App ngoài | Kirikiroid2 làm lựa chọn dự phòng cho Kirikiri; JoiPlay (Ren'Py, RPG Maker XP/VX/Ace): kiểm tra đã cài, link tải, hướng dẫn cài |

## Nguyên tắc: dễ cập nhật, dễ sửa

- **Gần như mọi thứ nằm trong [`config/monika-config.json`](config/monika-config.json)**: hệ máy, lõi giả lập, link app ngoài, hướng dẫn cài, host tải, chu kỳ thông báo. Sửa file này rồi push là app của user tự cập nhật, **không cần phát hành APK mới**. Hướng dẫn: [docs/CAP-NHAT.md](docs/CAP-NHAT.md).
- Config từ xa bị lỗi thì app bỏ qua và giữ bản đang chạy tốt.
- CI tự kiểm tra config (`ConfigTest`) mỗi lần push, sai đâu báo đó.
- Phiên bản thư viện gom ở [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Cấu trúc code

```
app/src/main/java/vn/aow/monika/
├── AppGraph.kt          # Tạo mọi thành phần (DI thủ công)
├── config/              # Đọc cấu hình (từ xa → cache → bản đóng gói)
├── feed/                # Đọc feed Blogger
├── notify/              # Kiểm tra bài mới + thông báo
├── download/            # Tách link tải, tải bằng DownloadManager
├── library/             # Thư mục game, giải nén, nhận diện loại game
├── runner/              # Trình chạy: libretro, web, apk, app ngoài
└── ui/                  # Giao diện Compose (3 tab)
```

## Build

```bash
./gradlew testDebugUnitTest   # test cấu hình + logic
./gradlew assembleDebug       # dựng APK debug
```

Mỗi lần push, GitHub Actions build APK debug (xem tab Actions → Artifacts). Tag `v*` kích hoạt workflow Release; workflow build và ký APK chính thức trên CI bằng GitHub Secrets đã cấu hình. Job dừng nếu thiếu cấu hình ký và chặn APK có chứng chỉ Android Debug. Trạng thái hiện hành xem [docs/opus/HANDOFF-SONNET.md](docs/opus/HANDOFF-SONNET.md). Nếu có secret `PIXELDRAIN_API_KEY`, workflow sẽ up APK lên Pixeldrain; up tay: `PIXELDRAIN_API_KEY=... scripts/pixeldrain-upload.sh file.apk`.

## Giấy phép

GPL-3.0 (do dùng [LibretroDroid](https://github.com/Swordfish90/LibretroDroid), GPL-3.0). Khi phát hành APK phải công khai mã nguồn.
