---
name: j2me-loader
description: Module J2ME Loader/JL-Mod nhúng trong Aow Monika (game Java .jar/.jad; module j2me/ + dexlib/, Apache-2.0). Dùng khi sửa chạy game Java, bàn phím ảo Monika, hoặc cập nhật lõi J2ME lên bản mới.
---

# J2ME Loader nhúng (runner `j2me`)

Game Java chạy bằng J2ME Loader nhúng ở module `j2me/` + `dexlib/` (Apache-2.0). Chi tiết đầy đủ, bảng sửa đổi và cách cập nhật: **`docs/J2ME-LOADER.md`** (đọc khi làm). Giấy phép gốc `j2me/LICENSE-J2ME-Loader`; bản sao trong APK `app/src/main/assets/licenses/j2me-loader.txt`.

## Luật
- **Chỉ sửa chỗ có chú thích `Aow Monika:`** (tìm: `grep -rn "Aow Monika" j2me/`). Giữ sửa đổi ít nhất có thể để cập nhật upstream dễ.
- Module giữ `build.gradle` riêng (Groovy) — **ngoại lệ** của quy tắc version catalog. Cần **NDK 22.1.7171670**.
- Nguồn gốc: CLAUDE.md cũ ghi bản gốc commit `9b0fa48` (J2ME Loader); theo `docs/J2ME-LOADER.md` từ 0.5.1 lõi là **JL-Mod** (`woesss/JL-Mod`, commit `f723a19`, bản `0.87.1-monika`). Tin tài liệu J2ME-LOADER.md; [CHƯA KIỂM] khớp với mã hiện tại. Dữ liệu game từ lõi cũ: [CHƯA KIỂM] tương thích Room DB/thư mục làm việc.
- Đã bỏ: Location API, màn quyên góp, ACRA (không gửi báo lỗi ra ngoài), DocumentProvider.

## Luồng chạy
`GameLauncher` nhánh `j2me` → `J2meRuntime.openGameIntent()` → `installer/MonikaLaunchActivity` (màn "chuẩn bị game" kiểu Monika: lần đầu cài, chuyển .jar → .dex vài giây, rồi tự chạy) → `MicroActivity` trong tiến trình riêng `:midlet`. Hộp thoại thoát: "Thoát về Aow Monika?".

## Phím ảo kiểu Monika
`VirtualKeyboard.MONIKA_STYLE` (đặt `false` để quay về giao diện gốc), `TYPE_MONIKA` (trái bàn số 3×4; phải L ↑ R / ← OK → / ↓ Menu; màu theo nhóm phím); mặc định phím trong `ProfileModel` (than kính mờ, nhấn cam hồng, độ mờ 140, bật rung). Chuỗi giao diện tiếng Việt làm mặc định (`values-vi` đã xóa; app chỉ giữ tài nguyên `vi`).

## Cập nhật lên bản mới
1. Clone bản mới, chép đè `app/src/main` → `j2me/src/main`, `dexlib/src` → `dexlib/src`.
2. Làm lại các sửa đổi trong bảng của `docs/J2ME-LOADER.md` (đối chiếu dấu `Aow Monika:` của bản cũ).
3. So `dependencies` trong `app/build.gradle` bản mới với `j2me/build.gradle`.
4. `./gradlew assembleDebug` + chạy thử 1 game .jar. Việc lõi J2ME là của Sonnet.
