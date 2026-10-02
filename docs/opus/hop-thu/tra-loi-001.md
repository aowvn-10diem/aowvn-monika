# Trả lời 001
Kết luận (1 dòng): Chọn **A**: dùng `sed` khi dựng, sửa **3 chỗ trong 2 file C** của SDL `release-2.26.3` (`adf31f6`) và đổi gói 9 file Java bằng script. Không hạ SDL về 2.0.20.

Lý do + đánh đổi:
- Tên gói chỉ nằm ở 3 chỗ. Đã đọc toàn bộ cây SDL 2.26.3; ngoài 3 chỗ này chỉ còn một dòng chú thích ở `src/video/android/SDL_androidevents.c:103`, không cần sửa.
  - `src/core/android/SDL_android.c:53`: `#define SDL_JAVA_PREFIX org_libsdl_app`. Mọi tên hàm `Java_*_SDLActivity_*`, `SDLAudioManager`, `SDLControllerManager`, `SDLInputConnection` đều dựng từ macro này (dòng 54–59).
  - `SDL_android.c:521–524`: `JNI_OnLoad` gọi `register_methods(env, "org/libsdl/app/<Lớp>", …)` cho 4 lớp. **Đây là chỗ nguy hiểm nhất.** Nếu quên sửa thì `FindClass` tìm thấy lớp `org.libsdl.app.*` của Ren'Py (SDL 2.0.20) có sẵn trong APK, và `RegisterNatives` gắn hàm native 2.26.3 vào lớp 2.0.20, sai lặng lẽ. Còn nếu APK không có lớp đó thì `FindClass` để lại exception chưa xóa, vì `register_methods` (dòng 500–507) không gọi `ExceptionClear`.
  - `src/hidapi/android/hid.cpp:45`: một `#define SDL_JAVA_PREFIX` riêng cho `HIDDeviceManager`.
- Phía Java: bản port dùng **đúng** 9 file Java của SDL 2.26.3; 8 file giống hệt, chỉ `SDLActivity.java` khác 4 chỗ (xem bước 3). Ngoài dòng `package`, chỉ có một chuỗi cần đổi: `HIDDeviceManager.java:30` `"org.libsdl.app.USB_PERMISSION"`. Mã riêng của mkxp-z (`binding/android-binding.cpp`, `filesystem.cpp`) chỉ `FindClass` lớp của Android, không đụng gói SDL.
- Không chọn B: SDL 2.0.20 của Ren'Py có bản vá riêng của renpy-build, và lớp Java đi kèm RAPT. Hạ xuống buộc RGSS phụ thuộc phiên bản Ren'Py, trong khi R làm trước P. Bản port cũng dựng trên 2.26.3 (`app/jni/get_deps.sh:65`).
- Tên gói mới `vn.aow.monika.rgss.sdl` không có dấu `_` trong từng đoạn, nên tên JNI không phải thoát thành `_1`. Giữ nguyên quy tắc này khi đặt tên.

Các bước (mỗi bước có cách kiểm):
1. Trong script dựng R1, sau khi clone SDL:
   `sed -i 's/^#define SDL_JAVA_PREFIX .*/#define SDL_JAVA_PREFIX vn_aow_monika_rgss_sdl/' src/core/android/SDL_android.c src/hidapi/android/hid.cpp`
   `sed -i 's#"org/libsdl/app/#"vn/aow/monika/rgss/sdl/#g' src/core/android/SDL_android.c`
   Kiểm ngay sau `sed`: lệnh `grep -rnE 'org_libsdl_app|"org/libsdl/app' src` chỉ còn đúng dòng chú thích `SDL_androidevents.c:103`. Còn thêm dòng nào thì cho build dừng.
2. Kiểm sau khi dựng `libSDL2.so`:
   - `llvm-nm -D libSDL2.so | grep '^Java_'`: mọi dòng bắt đầu bằng `Java_vn_aow_monika_rgss_sdl_`.
   - `strings libSDL2.so | grep -c 'org/libsdl/app'` bằng 0.
   Ghi cả hai kết quả vào artifact.
3. Java (R2): chép 9 file từ `android-project/app/src/main/java/org/libsdl/app/` của tag 2.26.3 sang `vn/aow/monika/rgss/sdl/`. Đổi `package org.libsdl.app;` và chuỗi `USB_PERMISSION` sang gói mới. Bản port sửa `SDLActivity.java` ở 4 chỗ:
   - `getMainSharedObject`: `libmkxp-z.so`.
   - `getLibraries`: `SDL2`, `SDL2_image`, `SDL2_ttf`, `SDL2_sound`, `openal`, `ruby`, `mkxp-z`.
   - `onStart`: bỏ `resumeNativeThread` vì luồng SDL được khởi từ `MainActivity`.
   - Padding hộp thoại: bỏ qua được.

   Ba chỗ đầu thì `RgssGameActivity` ghi đè lại, không sửa thẳng file SDL.

   Kiểm: `dexdump` của APK release thấy cả `org.libsdl.app.SDLActivity` lẫn `vn.aow.monika.rgss.sdl.SDLActivity`. Thêm luật `-keep class vn.aow.monika.rgss.sdl.** { *; }`.
4. Kiểm lúc chạy (R6, Emulator Test): logcat **không** có dòng `SDL: Failed to register methods of`.

Điều chưa chắc [CHƯA KIỂM]:
- Với lớp đã đổi gói, `FindClass` trong `JNI_OnLoad` có tìm thấy lớp khi `.so` nạp bằng `System.load(<đường dẫn gói>)` không. Bình thường là thấy, vì class loader của lớp gọi `System.load` là class loader của app. Nếu không thấy thì `RgssGameActivity` phải nạp `libSDL2.so` từ chính lớp đó (U4 trong phương án).
- Khi `JNI_OnLoad` trả về mà còn exception treo, ART xử lý thế nào: chưa thử. Bước 1 và 2 loại trường hợp này từ gốc.

Cần sếp quyết: không có.
