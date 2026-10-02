# LibretroDroid nhúng (GPLv3)

Module `:libretrodroid` là **bản chép nguyên của LibretroDroid 0.14.0** (https://github.com/Swordfish90/LibretroDroid, tag `0.14.0`, GPLv3), thay cho bản AAR từ JitPack. Lý do: thêm RetroAchievements trong game (cần đọc bộ nhớ lõi + chạy `rcheevos` mỗi khung hình) — API gốc không có.

## Nguồn đã chép
| Phần | Nguồn | Commit |
|---|---|---|
| `libretrodroid/` (gốc) | Swordfish90/LibretroDroid | tag 0.14.0 |
| `src/main/cpp/oboe/` | google/oboe (nhánh 1.5-stable), bỏ `samples/ tests/ docs/ apks/` | `b15f5e39c01a7ada306d959e5129620b145fb8b4` |
| `src/main/cpp/libretro/libretro-common/` | libretro/libretro-common | `b0c348ea5543c4d7fb0bc479258aa6988b20c0c9` |

## Sửa so với bản gốc
- `build.gradle`: bỏ `maven-publish`; `compileSdk 35`, `minSdk 26`, `ndkVersion 22.1.7171670` (cùng NDK với `:j2me`), CMake 3.22.1, chỉ dựng `arm64-v8a` + `armeabi-v7a`; `lifecycle-runtime-ktx` lên 2.8.7.
- `AndroidManifest.xml`: bỏ thuộc tính `package` (đã có `namespace`).
- CI: các workflow cài thêm `cmake;3.22.1` cạnh NDK 22.1.
- Quy ước: mọi sửa trong mã nguồn đặt chú thích `Aow Monika:` để dễ gộp khi nâng cấp bản gốc.

## Kế hoạch tiếp
Xem `docs/plan-retroachievements.md` giai đoạn 2 (mục 2.2 rcheevos → 2.7). **Bước 2.1 (commit này) chỉ nhúng, chưa sửa native**; phải xanh build + Emulator Test trước khi sang 2.2.
