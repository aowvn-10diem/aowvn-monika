# J2ME Loader nhúng trong Aow Monika

Game Java (.jar/.jad) chạy bằng **J2ME Loader** (Nikita Shakarun & cộng sự, Apache-2.0)
được nhúng dưới dạng module thư viện:

| Module | Nguồn gốc |
|---|---|
| `j2me/` | `app/` của **JL-Mod** https://github.com/woesss/JL-Mod (fork của J2ME Loader), commit `f723a19`, bản `0.87.1-monika` |
| `dexlib/` | `dexlib/` của JL-Mod (dx của AOSP, chuyển .jar → .dex) |

Từ 0.5.1 lõi là **JL-Mod** thay cho J2ME Loader gốc (`9b0fa48`): thêm skin/màn hình ảo cho Canvas, `screenPadding`,
cú pháp bàn phím ảo mới, MMAPI native (Oboe + EAS + TinySoundFont), cài đặt game viết lại (`AppInstaller`/`AppListModel`).
Đã bỏ khỏi bản nhúng: Location API, màn quyên góp, ACRA, DocumentProvider. `TinySoundFont` (`tsf.h`, `tml.h`, MIT) nằm ở
`j2me/src/main/cpp/mmapi_tsf/TinySoundFont/` (JL-Mod dùng submodule, ở đây chép thẳng vào repo).
Dữ liệu game đã cài từ lõi cũ: [CHƯA KIỂM] khả năng tương thích Room DB/thư mục làm việc — kiểm trên máy thật khi nâng cấp.

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
| `AndroidManifest.xml` (MainActivity) | Bỏ intent-filter mở file .jar/.jad/.kjx | File từ ngoài vào Monika trước, nhận diện đúng hệ máy |
| `res/values/strings.xml` | Tiếng Việt làm mặc định, dịch lại cho dễ hiểu (xóa `values-vi`); app chỉ giữ tài nguyên `vi` | Máy đặt ngôn ngữ nào cũng hiện tiếng Việt |
| `installer/MonikaLaunchActivity.java` (mới) + `J2meRuntime.openGameIntent` | Màn "chuẩn bị game" kiểu Monika: tự cài lần đầu, tự chạy; không qua danh sách app/hộp thoại cài | Game Java mở như 1 phần của Monika |
| `Config.startApp`, `ConfigActivity` | Game mới: tạo cấu hình mặc định rồi chạy luôn (không bắt vào màn cài đặt riêng) | Bớt bước thừa |
| `MicroActivity` + chuỗi `monika_*` | Hộp thoại thoát: "Thoát về Aow Monika?" / Chơi tiếp / Cài đặt game | Thoát là về Monika |
| `MicroActivity`, `res/layout/activity_micro.xml`, `J2meRuntime.init` (V77) | Bỏ thanh công cụ trên đầu màn chơi (cả Canvas lẫn Form/List; bỏ mặc định `pref_actionbar_switch`). Thay bằng MỘT nút menu nổi `monika_menu_button` (góc trên phải, tránh tai thỏ/thanh trạng thái) mở menu Monika; menu này gom đủ mục của menu J2ME gốc (thoát, lưu log, khóa xoay, bàn phím hệ thống, chụp màn hình, giới hạn FPS, tùy chọn phím ảo). Không đổi bàn phím ảo | Menu nằm một chỗ, thanh trên đầu không chiếm chỗ màn game |
| `VirtualKeyboard` `TYPE_MONIKA`, `ProfileModel.vkType` | Bàn phím mặc định: trái bàn số 3×4, phải L ↑ R / ← OK → / ↓ Menu; màu theo nhóm phím | Giống cảm giác điện thoại, dễ bấm |
| `res/values*/colors.xml`, `styles.xml`, `drawable/bg_*.xml`, `font/manrope.ttf` | Bảng màu Monika, nút viên thuốc gradient, thẻ/hộp thoại bo lớn, font Manrope | Giao diện đồng bộ Monika |

Mọi chỗ sửa đều có chú thích `Aow Monika:` → tìm nhanh bằng `grep -rn "Aow Monika" j2me/`.

## Cập nhật lên bản J2ME Loader mới

1. Clone bản mới, chép đè `app/src/main` → `j2me/src/main`, `dexlib/src` → `dexlib/src`.
2. Làm lại các sửa đổi trong bảng trên (tìm dấu `Aow Monika:` trong bản cũ để đối chiếu).
3. So `dependencies` trong `app/build.gradle` bản mới với `j2me/build.gradle`.
4. `./gradlew assembleDebug` + chạy thử 1 game .jar.

Tắt phong cách phím Monika (quay về giao diện gốc): `VirtualKeyboard.MONIKA_STYLE = false`.
