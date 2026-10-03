# PLAN v3 — Tích hợp RPG Maker vào Monika (bản gốc sếp giao 03/10/2026) — SAO LƯU

> **Đã bị thay thế** bởi `docs/opus/2026-10-03-nhung-renpy-rgss.md` (Opus đọc repo thật, sửa giả định sai về kiến trúc Monika; thứ tự Q3: RPG Maker trước, Ren'Py sau). File này giữ lại **bảng nguồn đã xác minh** (repo, commit, license) và ràng buộc license để khỏi mất. Mục "lệnh giao agent" cũ không còn hiệu lực (xem phương án Opus + `KE-HOACH.md`).
> Chỗ ghi **[chưa kiểm]** = kiến thức chung, phải xác minh với mã nguồn/game thật.

## Quyết định của chủ dự án
| # | Quyết định |
|---|---|
| D1 | Nền tảng đích: Android |
| D2 | Monika là mã nguồn mở (GPL-3.0-or-later, vì liên kết LibretroDroid GPL-3.0) |
| D3 | Bản đầu **phải có** XP / VX / VX Ace trên Android |
| D4 | XP/VX/Ace ưu tiên native; core libretro chỉ dự phòng |
| D5 | Viết lại toàn bộ vỏ Android của bản port, không dùng mã vỏ cũ |

## Phạm vi
RPG Maker 2000/2003 → EasyRPG Player · XP/VX/VX Ace → mkxp-z · MV/MZ → HTML5 trong WebView · RPG Maker 95: không (không có runtime mã mở xác minh được) · Unite: không (dự án Unity).

## Đính chính danh sách cũ
- EasyRPG Player chỉ chạy 2000/2003 (không 95/XP/VX/Ace). mkxp và mkxp-z chạy XP, VX, VX Ace.
- Các repo **không tồn tại**: `Enterbrain/RGDTools`, `Tuso/Tuso`, `CelestiAlts/Celestialts`, `EasyRPG/MobileUi`, `EasyRPG/Cherry`, `EasyRPG/libretro-easyrpg`, `mkxp-z/mkxp-z-libretro`, `jjwallace/RPGMaker.js`.
- Core libretro EasyRPG: `libretro/easyrpg-libretro`. mkxp-z upstream (nhánh `dev`) không có thư mục libretro hay android.

## Repo nguồn đã xác minh
### Runtime
| Repo | Engine | Commit cuối | License | Vai trò |
|---|---|---|---|---|
| https://github.com/EasyRPG/Player | 2000, 2003 | 2026-09-20 | GPL-3.0 | Lõi 2000/2003. `builds/android` là app Android hoàn chỉnh (`EasyRpgPlayerActivity`, `EasyRpgSurface`, `button_mapping/`, `game_browser/GameScanner`, `SafFile`); có `builds/libretro` |
| https://github.com/EasyRPG/liblcf | 2000, 2003 | 2026-08-03 | MIT | Đọc LDB/LMT/LMU/LSD |
| https://github.com/libretro/easyrpg-libretro | 2000, 2003 | 2026-09-05 | GPL-3.0 | EasyRPG thành core libretro |
| https://github.com/EasyRPG/RTP | 2000, 2003 | 2025-04-05 | CC-BY 4.0 | RTP thay thế miễn phí |
| https://github.com/EasyRPG/buildscripts | — | 2026-08-03 | theo thư mục | Script build toolchain Android EasyRPG |
| https://github.com/mkxp-z/mkxp-z | XP, VX, Ace | 2026-10-01 | GPL-2.0-or-later | Lõi XP/VX/Ace, nhánh `dev`, meson; `gfx_backend=gles`, `use_miniffi`, `cjk_fallback_font`, `mri_version` (mặc định 3.1) |
| https://github.com/thehatkid/mkxp-z-android | XP, VX, Ace | 2023-07-09 | không có license ở gốc | Điểm xuất phát port Android; `app/jni/*.mk` (ruby, openal, physfs, SDL2_sound, pixman, openssl, libiconv, ogg/vorbis/theora, uchardet, `get_deps.sh`) |
| https://github.com/white-axe/mkxp-z | XP, VX, Ace | 2026-10-01 (nhánh `libretro` 2026-06-30) | GPL-2.0-or-later | Core libretro chính thức của mkxp-z (dự phòng L) |
| https://github.com/BookerRues9/mkxp-z-android-reworked | XP (README) | 2026-05-25 | lõi GPL; vỏ không license | Port native mới nhất, có CI (**đã dựng được arm64 -O2: docs/opus/ket-qua/R0.md, R1.md**) |
| https://github.com/Asukate/mkxp-z-android | XP, VX, Ace | 2026-05-26 | như trên | Port native khác cùng gốc |
| https://github.com/libretro/libretro-core-info | — | 2026-09-15 | MIT [chưa kiểm] | `mkxp-z_libretro.info` |
| https://github.com/Ancurio/mkxp | XP, VX, Ace | 2023-10-12 | GPL-2.0-or-later | Bản gốc (chỉ đọc) |
| https://github.com/pulsejet/mkxp-web | XP, VX, Ace | 2023-04-27 | GPL-2.0 | mkxp → WebAssembly (dự phòng B) |
| https://github.com/Admenri/urge | RGSS | 2026-10-02 | MIT | URGE Core, CMake (dự phòng C, chưa có android) |
| https://github.com/rpgtkoolmv/corescript | MV | 2019-01-07 | MIT | Tham khảo API shim |
| https://github.com/AltimitSystems/mv-android-client | MV | 2019-08-20 | Apache-2.0 | Client WebView MV |
| https://github.com/pixijs/pixijs | MV, MZ | 2026-10-01 | MIT | Render MV/MZ |
| https://github.com/bakustarver/rpgmakermlinux-cicpoffs | MV, MZ | 2026-06-30 | GPL-3.0 | Ý tưởng: nâng Pixi 5, text hooker, xử lý hoa/thường |
### Nền tích hợp
LibretroDroid (Swordfish90, GPL-3.0, 2026-05-24) · Lemuroid (GPL-3.0) · RetroArch (GPL-3.0).
### Công cụ giải mã / dịch (việt hóa)
uuksu/RPGMakerDecrypter (MIT) · savannstm/rpgm-archive-decrypter (WTFPL) · luxrck/rgssad (MIT) · Petschko/RPG-Maker-MV-Decrypter + Java-… (MIT) · **savannstm/rvpacker-txt-rs (WTFPL, lõi cài patch việt hóa)** · savannstm/rpgmtranslate · hyrious/rvdata2-textconv · EasyRPG/Tools · EasyRPG/TestGame (GPL-3.0, game mẫu).
### Tham khảo
nwjs/nw.js · EasyRPG/Editor · Astrabit-ST/Luminol · ynoproject/{ynoengine,ynoclient,ynoserver} · elizagamedev/mkxp-oneshot · Speak2Erase/ModShot-Core · biud436/MV · zh99998/OpenRGSS.

## Ràng buộc license
| Thành phần | License | Việc phải làm |
|---|---|---|
| EasyRPG Player, easyrpg-libretro, LibretroDroid | GPL-3.0 | Monika GPL-3.0-or-later, công khai mã nguồn kèm mọi bản sửa |
| mkxp-z | GPL-2.0-or-later | Dùng dưới GPL-3.0; giữ header bản quyền |
| thehatkid/mkxp-z-android | không license | Lõi theo GPL; vỏ Java/Gradle chưa rõ → hỏi tác giả hoặc viết lại vỏ, chỉ tham khảo `.mk` (cổng G8 của sếp) |
| liblcf, URGE, pixijs, corescript, decrypter MIT | MIT | Giữ thông báo bản quyền |
| mv-android-client | Apache-2.0 | Giữ NOTICE |
| rvpacker-txt-rs, rpgm-archive-decrypter | WTFPL | Không ràng buộc |
| EasyRPG RTP | CC-BY 4.0 | Đóng gói kèm được, ghi công |
| RTP gốc XP/VX/Ace, core script MZ | độc quyền [chưa kiểm] | **Cấm đóng gói kèm**; người chơi tự cung cấp |
Bắt buộc có màn hình "Giấy phép mã nguồn mở" liệt kê đủ và link repo Monika. (Tóm tắt kỹ thuật, không phải tư vấn pháp lý.)

## Giá trị riêng của Monika (module M1–M12, ý tưởng cải tiến)
M1 nhận diện engine · M2 metadata (liblcf, `Game.ini`, `package.json`) · M3 web host MV/MZ + shim NW.js · M4 giải nén archive RGSS · M5 giải mã tài nguyên MV/MZ · M6 cài patch việt hóa một chạm · M7 font tiếng Việt cả 3 backend · M8 phím ảo (dùng lại của Monika) · M9 save thống nhất + sao lưu · M10 chẩn đoán "vì sao game không chạy" · M11 truy cập bộ nhớ (nhập vào thư mục riêng, SAF) · M12 port Android mkxp-z.
Cải tiến: không cần cấu hình (tự chọn backend, tìm RTP, sinh `mkxp.json`) · tiếng Việt hiển thị đúng ngay · patch việt hóa một chạm có sao lưu/gỡ · MV mượt hơn trên máy yếu (nâng Pixi, bật theo game) · tự xử lý tên file sai hoa/thường · thông báo lỗi tiếng Việt nói rõ thiếu gì.

## Đặc tả đáng giữ
- Nhận diện engine (theo thứ tự, không phân biệt hoa thường): `js/rmmz_core.js`→MZ; `js/rpg_core.js`→MV; `Game.rgss3a`/`Data/*.rvdata2`→VX Ace; `Game.rgss2a`/`*.rvdata`→VX; `Game.rgssad`/`*.rxdata`→XP; `RPG_RT.ldb`+`RPG_RT.lmt`→2000/2003. Quét sâu ≤ 3 cấp, nhận game trong `.zip`, kết quả có `confidence` + `evidence`. (Monika hiện đã có luật `engines` trong config cho các hệ này.)
- Save: 2000/2003 `Save*.lsd`; RGSS `Save*.rxdata/.rvdata/.rvdata2` trong thư mục game; MV/MZ mặc định localStorage → shim phải ghi ra file thật (`.rpgsave`/`.rmmzsave`).
- Mã hóa: archive RGSS XOR khóa xoay (`.rgssad/.rgss2a` v1, `.rgss3a` v3); tài nguyên MV/MZ 16 byte header giả + XOR 16 byte đầu bằng `encryptionKey` trong `data/System.json`. Lõi runtime tự đọc archive; M4/M5 chỉ phục vụ patch việt hóa/metadata.
- Bộ nhớ Android: nhập game vào thư mục riêng của app (mặc định, resume được, có tiến độ); chơi tại chỗ qua SAF (game lớn); không xin All Files Access trừ khi sếp yêu cầu.
- `rmweb`: phục vụ file qua `WebViewAssetLoader` (không phân biệt hoa/thường), shim `fs`, `path`, `nw.Window`, `nw.App`, `process.*`, log API thiếu shim; xử lý chặn tự phát âm thanh; giữ sáng màn hình; tạm dừng khi xuống nền.
- `rgss`: `gfx_backend=gles`, `use_miniffi=true`, tự sinh `mkxp.json` (phiên bản RGSS, đường dẫn game/RTP, font fallback Việt), không đóng gói RTP gốc, chỉ `arm64-v8a` ở bản đầu (sau đó v7a là điều kiện R7).

## Rủi ro chính
mkxp-z native Android (cam kết XP, lõi 2.4 vs upstream SDL3, vỏ không license) — cổng quyết định bằng game thật, dự phòng L → B → C · biên dịch chéo Ruby (build trên Linux) · GPL-3.0 tương thích thư viện Monika · game gọi Win32API (MiniFFI một phần) · plugin MV gọi Node/NW.js sâu · hạn chế bộ nhớ ngoài · thiếu RTP · APK tăng (→ gói tải theo nhu cầu, đã làm) · vượt phạm vi.
