---
name: dung-engine
description: Dựng, đóng gói, kiểm và đăng gói engine native cho Aow Monika (Azahar 3DS, OnscripterYuri, Kirikiri, mkxp-z/RGSS, Ren'Py, 7-Zip) bằng GitHub Actions. Dùng khi sửa lõi engine, thêm gói, hoặc gói tải về báo hỏng (needed.txt, ELF/ABI, manifest).
---

# Dựng và kiểm gói engine

Nguyên tắc: engine **không nhét vào APK**; dựng trên CI từ mã nguồn mở, đưa lên Releases, Monika tải khi người chơi có game (`modules.<id>` trong `config/monika-config.json`; cài bằng `app/src/main/java/vn/aow/monika/pack/`). Việc máy làm được thì để Actions làm, model chỉ đọc kết quả. Lỗi bên trong engine (Kirikiri, mkxp-z, Azahar, Ren'Py, J2ME) là việc lõi của Sonnet.

## Workflow (`.github/workflows/`)
| Workflow | Việc |
|---|---|
| `build-engines.yml` | `only` = all/onsyuri/azahar. Job `onsyuri-web` (OnscripterYuri bản wasm, release `engines-onsyuri-<run>`) và `azahar-android` (Gradle `assembleVanillaRelease` của Azahar/AzaharPlus, chỉ arm64, trích `libcitra-android.so`, release `engine-azahar-<run>`) |
| `build-kirikiri.yml` | Kirikiroid2Yuri (cocos2d-x + Kirikiri Z), gói `kirikiri-arm64.zip` |
| `build-rgss.yml` | mkxp-z Android, đổi gói JNI của SDL sang `vn.aow.monika.rgss.sdl` để sống chung với `org.libsdl.app` của Ren'Py; CI clone đúng commit ghim (`engines/rgss/UPSTREAM.md`). Đầu vào `abi`, `port_ref`, `publish`, `private_only`. Không chạy với publish trên ref cũ |
| `build-renpy-pack.yml` | Gói Ren'Py: lấy `librenpython.so` từ RAPT chính thức + `private/` bằng `launcher distribute --package android --no-archive` (RAPT không chứa `private/`); ghim SHA-256 SDK + RAPT |
| `mirror-pack.yml`, `publish-pack.yml` | Chép gói sang repo phụ công khai `aowvn-10diem/aowvn-monika-packs` (cần secret `PACKS_TOKEN`), kiểm link tải thẳng không đăng nhập, in SHA-256 để dán vào `modules.<tên>` |
| `check-packs.yml` (V37) | Chạy khi PR đụng `pack/`, `azahar/`, `config/`, workflow dựng gói: tải mọi gói/ABI trong `modules` và chạy đúng `PackTransaction` của app (công cụ `tools/pack-check`, không cần SDK). ONS bản `08f744b` là `KNOWN_FAILURE` (V36) |
| `native-check.yml`, `renpy-module.yml`, `emulator-test.yml`, `test-lab.yml` | Biên dịch nhanh `:libretrodroid`; module `:renpy` + APK có cả hai SDLActivity; Emulator Test (máy ảo Android 11/14, Maestro); Firebase Test Lab (`docs/TEST-LAB.md`) |

Azahar: workflow tự lấy `ndkVersion` từ `src/src/android/app/build.gradle.kts` của Azahar rồi `sdkmanager "ndk;<ver>" "cmake;3.31.6" "platforms;android-35"`. Bỏ `libVkLayer_*.so`. Nhúng tầng JNI của Azahar, giao diện là của Monika.

## Bố cục gói (đừng sai — app kiểm)
- Gói là ZIP; file chính ở **gốc** gói (ONS cần `onsyuri.wasm` ở gốc — lỗi V36 vì ZIP từng để trong `onsyuri/`).
- `manifest.json` (nếu có): `abi` khớp, `loadOrder[]` đủ file, `files{tên:{size,sha256}}` đúng.
- `needed.txt`: tạo bằng `llvm-readelf -d <lib chính> | grep NEEDED | sed 's/.*\[\(.*\)\]/\1/'`. Mọi dòng phải là thư viện hệ thống công khai của NDK (libc, libm, libdl, liblog, libandroid, libz, libEGL, libGLESv1_CM/2/3, libOpenSLES, libjnigraphics, libvulkan, libaaudio, libmediandk, libnativewindow, libcamera2ndk, libstdc++, libsync, libneuralnetworks, libOpenMAXAL, libamidi, libbinder_ndk) **hoặc** có mặt trong gói. `libc++_shared.so` phải nằm trong gói.
- Mọi `.so` là ELF đúng ABI: arm64-v8a = machine 183, class 64-bit; armeabi-v7a = machine 40, class 32-bit.
- `jni-symbols.txt` (Azahar): `llvm-nm -D --defined-only | grep '^Java_'` — hợp đồng JNI khớp lớp Kotlin nhúng. `SOURCE.txt` ghi repo + commit + giấy phép.
- ZIP không được vượt thư mục (`../`) hay trùng tên.

## Kiểm gói thật
- `scripts`: `val-goi-that.py` của PM nằm ở `.claude/skills/pm-kiem-thu/scripts/val-goi-that.py` (đặt zip cạnh script hoặc `PACK_DIR`).
- App cài **có giao dịch** (V35): tải vào thư mục tạm riêng, kiểm đủ file/ABI/hash rồi mới đổi tên thay bản cũ; lỗi giữa chừng giữ bản cũ; khóa theo gói (mutex + khóa file). Hash có khai báo luôn được kiểm.
- Sau khi đăng gói: điền `modules.<id>` (`version`, `url`, `sha256`/`sha256ByAbi`, `size`/`sizeByAbi`, `abis`), **tăng `configVersion`** (config từ xa thấp hơn bản trong APK bị bỏ qua), đồng bộ Cloudflare (`sync-config.yml`), chạy `./gradlew testDebugUnitTest` (`ConfigTest` chặn sai chỗ).
- Quy tắc mạng tải gói: ≤ 15 MB tự tải (kể cả 4G, trừ Tiết kiệm dữ liệu); lớn hơn thì hỏi; Wi-Fi tải ngay (`pack/PackPolicy.kt`).

## Ký hiệu native (V20/D1)
Workflow dựng giữ ELF tốt nhất cùng **BuildId** với ELF trong gói: `scripts/package-native-symbols.py` → ZIP `symbols-<gói>-<run>-<abi>.zip` (có `build-ids.tsv`; mức `debug`/`symtab`/`dynamic-only`), release prerelease; các bước này `continue-on-error`. Dùng ở `bao-loi-diag`.

## Bài học (đừng lặp)
- YAML: tên bước có `: ` (hai chấm + cách) làm workflow hỏng im lặng. Kiểm `python3 -c "import yaml;yaml.safe_load(open(f))"` trước khi push.
- `get_deps.sh` của bản port không có quyền thực thi → chạy `bash`; thiếu `make_xxd.sh` (README bỏ sót).
- `list_workflow_runs` bỏ qua `per_page` → dùng `workflow_runs_filter`; log CI dùng `get_job_logs` với `tail_lines` 75–130; artifact/log qua `gh api` bị chặn redirect.
- Hạn mức lưu trữ Actions từng đầy (artifact APK debug 83 MB): mọi `upload-artifact` có retention ngắn + `continue-on-error`.
- **Không phát hành gói GPL mới (`rgss`, `renpy`, `symbian`) lên repo packs công khai** khi cổng chưa xong — luật vẫn còn hiệu lực (KE-HOACH G5/G8): G5 repo đã công khai từ 03/10, còn chờ sếp chọn file LICENSE; G8 giấy phép script dựng mkxp-z còn chờ tác giả trả lời. Bản chưa có kết quả máy thật luôn là prerelease.
- Game kiểm thử tự sinh (không cần máy thật): CI tự viết game mẫu + file dấu kiểm bằng adb (`scripts/ci-emulator-games.sh`, phương án `docs/opus/2026-10-03-kiem-thu-chan-doan.md`); máy ảo CI là x86_64 chạy `.so` arm64 qua native bridge nên không bắt lỗi riêng ARM thật.
