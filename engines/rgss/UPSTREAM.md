# Nguồn của gói `rgss` (RPG Maker XP/VX/Ace chạy bằng mkxp-z)

| Thành phần | Nguồn | Giấy phép | Ghi chú |
|---|---|---|---|
| Lõi mkxp-z 2.4 + bản vá Android | `BookerRues9/mkxp-z-android-reworked` @ `b668e08` (25/05/2026), dẫn xuất từ `thehatkid/mkxp-z-android` và `mkxp-z/mkxp-z` | GPL-2.0-or-later (lõi: `app/jni/mkxp-z/COPYING`) | Là sửa đổi trên mã GPL. Monika công khai commit nguồn + bản vá trong `engines/rgss/patches/` (nếu có) |
| Vỏ Java `com.hatkid.mkxpz.*`, `Makefile`, `*.mk`, `get_deps.sh` của bản port | cùng repo | **Không có file giấy phép** | **Monika KHÔNG chép các file này vào repo.** CI chỉ clone đúng commit để dựng; vỏ Java Android do Monika viết lại (`RgssGameActivity`). Hỏi tác giả giấy phép: việc của sếp |
| SDL 2.26.3, SDL_image 2.6.3, SDL_ttf 2.20.2 | libsdl-org | zlib | Lớp native đổi gói JNI sang `vn.aow.monika.rgss.sdl` (xem `docs/opus/hop-thu/tra-loi-001.md`) |
| SDL_sound 2.0.1, PhysicsFS 3.2.0 | icculus | zlib | |
| OpenAL Soft 1.23.0 | kcat | LGPL-2.0 | liên kết động (`libopenal.so`) |
| Ruby 3.1 nhánh `mkxp-z-3.1` | `mkxp-z/ruby` | Ruby/BSD-2 | |
| libogg 1.3.5, libvorbis 1.3.7, libtheora 1.1.1 | xiph.org | BSD-3 | |
| pixman 0.42.2, uchardet 0.0.8, libiconv 1.17, OpenSSL 1.1.1t | — | MIT / MPL / LGPL / Apache-2.0 | OpenSSL 1.1.1t hết hỗ trợ: cân nhắc bỏ (thư 003) |

Dựng: `.github/workflows/build-rgss.yml`. Kiểm sau dựng: tên hàm JNI của `libSDL2.so` phải bắt đầu `Java_vn_aow_monika_rgss_sdl_` và không còn chuỗi `org/libsdl/app`.

**Trạng thái giấy phép build:** chờ tác giả bản port (xin giấy phép cho `Makefile`, `*.mk`, `get_deps.sh`; cổng G8, việc của sếp, trước R7). Chưa có thì phải viết lại script dựng riêng (hướng B) trước khi phát hành gói `rgss`.

**Sửa đổi của Aow Monika trên mã nguồn (đều nằm trong lệnh `sed` của `build-rgss.yml`, chú thích `Aow Monika:`):** (1) đổi gói JNI của SDL sang `vn.aow.monika.rgss.sdl`; (2) bỏ OpenSSL (`-DMKXPZ_SSL`, thư viện tĩnh, ext Ruby `openssl`); (3) `-O0` → `-O2`, `APP_OPTIM release`; (4) chạy `make_xxd.sh` trước ndk-build.
