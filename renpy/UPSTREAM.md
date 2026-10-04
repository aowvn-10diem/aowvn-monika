# Nguồn gốc `renpy/` (module `:renpy`)

- Nguồn: `https://www.renpy.org/dl/8.5.3/renpy-8.5.3-rapt.zip`, SHA-256 `8a12be34a2f5238d125ff6dd76a56772fd8e44838af864f81bca82c1059b00e6` (68.554.524 byte).
- Lấy từ `rapt/prototype/renpyandroid/src/main/java`: `org/renpy/android/*`, `org/jnius/*`, `org/kamranzafar/jtar/*`, `org/libsdl/app/*` (SDL 2.0.20), cùng `src/main/res/xml/file_paths.xml`. Bỏ `NativeInvocationHandler.class` và `SDLActivity.java.orig`.
- **Không** lấy: `jniLibs/*/librenpython.so` (gói `renpy8` tải thêm), `renpyiap` (mua hàng trong app), `asset-delivery` / `slf4j` / `kotlin` / `androidx.activity` (không có mã nào dùng sau bản vá).
- Giấy phép: **chưa kiểm kê** (mục G5/G8 của sếp). Chưa xuất bản gói nào dựng từ module này.

## Bản vá của Aow Monika (`patches/`, đã áp sẵn vào `src/`)
`0001-bo-play-asset-delivery.patch` — `PythonSDLActivity`: bỏ Play Asset Delivery (`AssetPackManager`, `onStateUpdate`, `checkPack` luôn đúng), thêm `Constants.java` cố định (`store = "none"`, không asset pack; RAPT sinh file này theo từng game). Mã sửa đều có chú thích `Aow Monika:`.

`0002-tach-setup-moi-truong-python.patch` — `PythonSDLActivity.preparePython()`: phần giải nén `private` + đặt biến môi trường tách thành `protected setupPythonEnvironment(...)` (mặc định giữ nguyên hành vi RAPT) để `RenpyGameActivity` ghi đè bằng gói tải thêm.

Kiểm bản vá tái lập: tải đúng zip trên, chép `rapt/prototype/renpyandroid/src/main/java` ra thư mục `a/`, bỏ `*.class`/`*.orig`, rồi áp lần lượt `renpy/patches/*.patch` (`patch -p1 -d a < …`) phải ra đúng `renpy/src/main/java` (trừ `Constants.java` do patch tạo).

## Đổi manifest
`AndroidManifest.xml` bỏ thuộc tính `package` (dùng `namespace` trong `build.gradle`) và bỏ quyền `WRITE_EXTERNAL_STORAGE`. `compileSdk` hạ từ 36 về 35 (U8); `minSdk` 26 như app.

## Việc kế (P3 trở đi)
Khai báo `PythonSDLActivity` trong manifest app (tiến trình `:game`), `RenPyFileProvider` (`<authorities>` = `${applicationId}.fileprovider`), `RenpyGameActivity` trong `runner/`, `EngineRoutes`, `EnginePrepActivity`, gói `renpy8` (xem `docs/opus/ket-qua/P0.md`).
