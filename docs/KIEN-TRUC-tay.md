# Hướng dẫn đọc nhanh (viết tay — cập nhật khi đổi luồng)

## A. Nhìn nhanh phụ thuộc
- Gần như mọi package "phụ thuộc `ui`" thực chất chỉ dùng **`ui.theme`** (bộ giao diện chung: nút, sheet, màu, icon). Đây là chỗ cắt rẻ nhất: tách `ui.theme` thành module riêng `:theme` thì các vòng `runner/azahar/browser/cheats/apkinstall ⇄ ui` biến mất (kiểm bằng cách chạy lại `scripts/gen-architecture.py`, mục 4).
- `(gốc)` = `AppGraph`, `Prefs`, `MonikaApp`, `CrashReporter`: nơi mọi thứ cắm vào nhau. Khi tách module thì đây là `:core`.
- `library` ⇄ `ui` (3 / 20): thẻ game và màn hình Thư viện; chấp nhận được, `ui` là tầng trên cùng.

## B. Luồng chính (đi theo file)
1. **Mở game**: `ui/screens/LibraryScreen` → `runner/GameLauncher.launch` chọn theo `system.runner` trong config:
   - `libretro` → `RetroActivity` (tiến trình `:game`), riêng 3DS có engine `azahar/`.
   - `web` → `WebGameActivity` · `apk` → `apkinstall/ApkInstallFlow` · `j2me` → `:j2me` (`J2meRuntime.openGameIntent` → `MonikaLaunchActivity`) · `external` → app ngoài.
2. **Lõi libretro**: `runner/CoreManager.ensureCore` tải `.so` theo config (ABI lấy theo thư viện native của app) → `RetroActivity` dựng `GLRetroView`.
   Tùy chọn lõi gộp theo thứ tự: config → mức máy (`EmuTier`: lite/mid/full) → kiểu hiển thị → lựa chọn của người chơi.
3. **Config trước tiên**: `config/monika-config.json` (hệ máy, lõi, link, tùy chọn). Sửa file + push `main` là app cập nhật, không cần APK. Kiểm lõi/tùy chọn: `python3 scripts/audit-cores.py`.
4. **Lỗi/crash**: `diag/Diagnostics` (phiên chơi → báo cáo) · `RetroActivity.onCoreFailure` (lõi báo lỗi/không lên hình → hỏi đổi lõi) · `ui/CrashUi` (hộp thoại) · gửi về máy chủ báo lỗi (`crash.endpoint`, xem `scripts/crash-reports.sh`).
5. **Phát hành**: Actions → Release (`workflow_dispatch`, nhập tag) → build ký → GitHub Release + Pixeldrain. Kiểm giả lập trên máy ảo: workflow Emulator Test.

## C. Muốn sửa X thì mở file nào
| Việc | Nơi sửa |
|---|---|
| Thêm/đổi lõi, tùy chọn hiệu năng, hệ máy | `config/monika-config.json` (+ chạy `audit-cores.py`) |
| Kiểu hiển thị GBA (LCD/Sắc nét/Mượt) | `runner/DisplayStyles.kt` + khối `display` của lõi trong config |
| Hiệu năng theo sức máy | `runner/EmuTier.kt` + `perf` trong config |
| Giao diện tay cầm trong game | `runner/GamePadOverlay.kt` |
| Game Java | `:j2me` (xem `docs/J2ME-LOADER.md`) |
| Cài game Android (APK/OBB/ADB) | `apkinstall/` |
| Trình duyệt, tải file | `browser/`, `download/` |
| Thư viện game, tìm kiếm | `library/`, `ui/screens/LibraryScreen.kt` |
| Màu, nút, sheet chung | `ui/theme/` |

## D. Quy ước để việc sửa gọn và chẩn đoán lỗi nhanh
- Mỗi thư mục trong `vn/aow/monika/` là **một thành phần**; code ở thành phần này không gọi thẳng nội bộ thành phần khác ngoài API công khai của nó.
- Log/báo lỗi gắn nhãn thành phần theo tên thư mục (`runner`, `apkinstall`, `browser`, `j2me`…). Tag log của game: `MonikaGame`.
- Thêm thành phần mới → tạo thư mục mới, chạy lại `python3 scripts/gen-architecture.py`, commit cả `docs/KIEN-TRUC.md`.

## E. Engine nhúng (Kirikiri, RPG Maker)
Luồng chung: `EngineRoutes` → `EnginePrepActivity` (chuẩn bị) → `PackManager` (tải gói) → `*GameActivity` (chơi) + `*Overlay` (menu/phím).

| Bước | File | Việc |
|---|---|---|
| 1. Nhận diện | `runner/GameLauncher.kt` | Kiểm `system.engine` → gọi `EnginePrepActivity.start()` |
| 2. Chuẩn bị | `runner/EnginePrepActivity.kt` | Màn tải gói, xin quyền (API 30+), xin đọc file |
| 3. Gói tải thêm | `pack/PackManager.kt` | Tải `.zip` từ `modules.<id>` + SHA-256 + giải nén → `filesDir/packs/<id>` |
| 4. Mở activity | `runner/EngineRoutes.kt` | Chọn `*GameActivity` và gọi `.start(activity, game, title, key)` |
| 5. Nạp thư viện | `runner/*GameActivity.kt` | Tự tìm thư viện (`files/packs/<id>/*.so`) + gọi `System.loadLibrary` hoặc `System.load()` |
| 6. Giao diện | `runner/*Overlay.kt` | Phím ảo + menu Monika (đóng/mở bằng nút hoặc Back) |

**Thêm engine mới:**
1. Tạo entry ở `EngineRoutes.all` với `packId` khớp `modules.<packId>` trong config.
2. Viết `*GameActivity` (tiến trình `:game`, nạp gói từ `PackManager`) + `*Overlay` (UI menu/phím).
3. Gói `.so` + tài nguyên nằm ở repo `aowvn-monika-packs`, release theo `modules.<packId>.size` + `.sha256`.
4. Cập nhật `config/monika-config.json` → tăng `configVersion` → push `main` → app tự cập nhật.
