# Nguồn gốc `renpy/` (module `:renpy`)

- Nguồn: `https://www.renpy.org/dl/8.5.3/renpy-8.5.3-rapt.zip`, SHA-256 `8a12be34a2f5238d125ff6dd76a56772fd8e44838af864f81bca82c1059b00e6` (68.554.524 byte).
- Lấy từ `rapt/prototype/renpyandroid/src/main/java`: `org/renpy/android/*`, `org/jnius/*`, `org/kamranzafar/jtar/*`, `org/libsdl/app/*` (SDL 2.0.20), cùng `src/main/res/xml/file_paths.xml`. Bỏ `NativeInvocationHandler.class` và `SDLActivity.java.orig`.
- **Không** lấy: `jniLibs/*/librenpython.so` (gói `renpy8` tải thêm), `renpyiap` (mua hàng trong app), `asset-delivery` / `slf4j` / `kotlin` / `androidx.activity` (không có mã nào dùng sau bản vá).
- Kiểm kê giấy phép V52b (08/10/2026): module `:renpy` được đưa vào APK (`app/build.gradle.kts:177`) nhưng là tập hợp nhiều nguồn. Jtar Java có header Apache-2.0 (ví dụ `renpy/src/main/java/org/kamranzafar/jtar/TarEntry.java:2-14`); SDL 2.0.20 có license zlib tại [file license của đúng release](https://github.com/libsdl-org/SDL/blob/release-2.0.20/LICENSE.txt); trang [license Ren’Py](https://www.renpy.org/doc/html/license.html#license) liệt kê pyjnius MIT và nói phần lớn Ren’Py MIT, một số phần LGPL. Trang đó không ánh xạ license tới từng file `org/renpy/android`; vì vậy license chính xác của các class đó, kể cả patch vào `PythonSDLActivity`, còn **[CHƯA KIỂM]**. RAPT archive SHA-256 ở trên không chứa file LICENSE/COPYING/NOTICE; việc notices đi kèm APK cũng **[CHƯA KIỂM]**.

## Đối chiếu nguồn Java với repo Ren'Py

Ngày 30/10/2026, tải lại archive chính thức RAPT 8.5.3 (`renpy-8.5.3-rapt.zip`, 68.554.524 byte; SHA-256 `8a12be34a2f5238d125ff6dd76a56772fd8e44838af864f81bca82c1059b00e6`), kết quả hash khớp dòng nguồn ở trên. Đối chiếu thư mục `rapt/prototype/renpyandroid/src/main/java/org/renpy/android` với cùng đường dẫn tại repo chính thức [`renpy/renpy-build` commit `cff4919`](https://github.com/renpy/renpy-build/commit/cff4919ae0ff9976470dbc1d78a1a3ec7fb6432f) (26/10/2026): archive có 10 tệp Java, repo có 12; `diff -qr` chỉ báo `Age.java` và `Constants.java` là tệp chỉ có trong repo, không báo cặp tệp khác nhau. Ví dụ [`AssetExtract.java`](https://github.com/renpy/renpy-build/blob/cff4919ae0ff9976470dbc1d78a1a3ec7fb6432f/rapt/prototype/renpyandroid/src/main/java/org/renpy/android/AssetExtract.java) và [`PythonSDLActivity.java`](https://github.com/renpy/renpy-build/blob/cff4919ae0ff9976470dbc1d78a1a3ec7fb6432f/rapt/prototype/renpyandroid/src/main/java/org/renpy/android/PythonSDLActivity.java). Vì vậy, 10 tệp Java `org/renpy/android` có trong archive khớp byte với các đường dẫn tương ứng tại commit này.

Đối chiếu xác nhận nội dung nguồn tương ứng, nhưng tự nó không chứng minh archive 8.5.3 được dựng từ commit Git này. Các tệp vẫn không nêu giấy phép riêng; giấy phép chính xác của nhóm class `org/renpy/android` và patch của Monika còn **[CHƯA KIỂM]**.

## Bản vá của Aow Monika (`patches/`, đã áp sẵn vào `src/`)
`0001-bo-play-asset-delivery.patch` — `PythonSDLActivity`: bỏ Play Asset Delivery (`AssetPackManager`, `onStateUpdate`, `checkPack` luôn đúng), thêm `Constants.java` cố định (`store = "none"`, không asset pack; RAPT sinh file này theo từng game). Mã sửa đều có chú thích `Aow Monika:`.

`0002-tach-setup-moi-truong-python.patch` — `PythonSDLActivity.preparePython()`: phần giải nén `private` + đặt biến môi trường tách thành `protected setupPythonEnvironment(...)` (mặc định giữ nguyên hành vi RAPT) để `RenpyGameActivity` ghi đè bằng gói tải thêm.

Kiểm bản vá tái lập: tải đúng zip trên, chép `rapt/prototype/renpyandroid/src/main/java` ra thư mục `a/`, bỏ `*.class`/`*.orig`, rồi áp lần lượt `renpy/patches/*.patch` (`patch -p1 -d a < …`) phải ra đúng `renpy/src/main/java` (trừ `Constants.java` do patch tạo).

## Đổi manifest
`AndroidManifest.xml` bỏ thuộc tính `package` (dùng `namespace` trong `build.gradle`) và bỏ quyền `WRITE_EXTERNAL_STORAGE`. `compileSdk` hạ từ 36 về 35 (U8); `minSdk` 26 như app.

## Việc kế (P3 trở đi)
Khai báo `PythonSDLActivity` trong manifest app (tiến trình `:game`), `RenPyFileProvider` (`<authorities>` = `${applicationId}.fileprovider`), `RenpyGameActivity` trong `runner/`, `EngineRoutes`, `EnginePrepActivity`, gói `renpy8` (xem `docs/opus/ket-qua/P0.md`).
