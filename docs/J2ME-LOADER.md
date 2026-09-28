# J2ME Loader nhúng trong Aow Monika

Game Java (.jar/.jad) chạy bằng **J2ME Loader** (Nikita Shakarun & cộng sự, Apache-2.0)
được nhúng dưới dạng module thư viện:

| Module | Nguồn gốc |
|---|---|
| `j2me/` | `app/` của https://github.com/nikita36078/J2ME-Loader, commit `9b0fa48` (06/02/2026) |
| `dexlib/` | `dexlib/` của cùng repo (dx của AOSP, chuyển .jar → .dex) |

Giấy phép gốc: `j2me/LICENSE-J2ME-Loader`. Bản sao đi kèm APK: `app/src/main/assets/licenses/j2me-loader.txt`.

## Luồng chạy

Monika mở game Java → `J2meRuntime.openGameIntent()` → màn hình của J2ME Loader
(`ru.playsoftware.j2meloader.MainActivity`): lần đầu **cài** (chuyển .jar thành .dex, vài giây),
sau đó chạy. Game chạy trong tiến trình riêng `:midlet` (như bản gốc).

## Những gì Aow Monika đã sửa so với bản gốc (giữ ít nhất có thể)

| File | Sửa gì | Lý do |
|---|---|---|
| `j2me/build.gradle`, `dexlib/build.gradle` | Chuyển sang `com.android.library`, bỏ flavor/ký, thêm `BuildConfig` + `app_name` | Bản gốc là app |
| `j2me/src/main/AndroidManifest.xml` | Bỏ thuộc tính `<application>` + launcher, gán `AppTheme` cho activity, provider dùng `J2meFileProvider` | Tránh xung đột khi gộp với app chính |
| `J2meRuntime.java` (mới) | Khởi tạo thay `EmulatorApplication`, không bật ACRA | Thư viện không có Application riêng; không gửi báo lỗi ra ngoài |
| `util/J2meFileProvider.java` (mới) | Lớp con FileProvider | Tránh trùng khai báo với app chính |
| `ProfileModel.java` | Mặc định phím: than kính mờ, nhấn cam hồng, độ mờ 140, bật rung | Phong cách Monika |
| `VirtualKeyboard.java` | `MONIKA_STYLE`: phím bo tròn hẳn, có khối 3D, viền sáng, chữ đậm, nhấn = lún + gradient | Tối ưu nút bấm |
| `CanvasWrapper.java` | Thêm hàm vẽ bo góc số thực, shader, độ dày viền, chữ đậm | Phục vụ vẽ phím mới |
| `ContextHolder.java` | Rung "tick" hệ thống (Android 10+), máy cũ 15ms | Rung giòn, đỡ tốn pin |

Mọi chỗ sửa đều có chú thích `Aow Monika:` → tìm nhanh bằng `grep -rn "Aow Monika" j2me/`.

## Cập nhật lên bản J2ME Loader mới

1. Clone bản mới, chép đè `app/src/main` → `j2me/src/main`, `dexlib/src` → `dexlib/src`.
2. Làm lại các sửa đổi trong bảng trên (tìm dấu `Aow Monika:` trong bản cũ để đối chiếu).
3. So `dependencies` trong `app/build.gradle` bản mới với `j2me/build.gradle`.
4. `./gradlew assembleDebug` + chạy thử 1 game .jar.

Tắt phong cách phím Monika (quay về giao diện gốc): `VirtualKeyboard.MONIKA_STYLE = false`.
