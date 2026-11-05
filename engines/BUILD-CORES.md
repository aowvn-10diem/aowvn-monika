# Dựng lõi từ nguồn — V198, bước chuẩn bị

Theo [đặc tả đã duyệt](../docs/dac-ta/V198-tu-dung-loi.md) và [PM cho làm PR mã đầu](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6625028499).

Nền lock/provenance + workflow chuẩn bị đã gộp ở #488; hiện có recipe FCEUmm, Gambatte, mGBA và PPSSPP, hai ABI ARM mà APK hiện dựng. NDK `22.1.7171670` dùng cùng phiên bản sẵn có của module `libretrodroid`.

| Core | Commit nguồn (từ manifest snapshot V32, đã đối chiếu API/nguồn) | Giấy phép |
|---|---|---|
| FCEUmm | `7a542dab1e87679921962a9f056186eca425c0c2` | GPL-2.0-or-later + giấy phép NTSC kèm theo |
| Gambatte | `d9d6cd06382d1ced30de34d56d3609452323dab1` | GPL-2.0-only (`libgambatte/src/gambatte.cpp` ghi version 2; không dùng nhãn or-later của FCEUmm) |
| mGBA | `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` | MPL-2.0; kèm giấy phép BSD của inih trong gói và toàn bộ nguồn tương ứng |
| PPSSPP | `9366aa7dc2d2fb1f49cde97ffbf5ab434d4d6ff9` | GPL-2.0-or-later + các thông báo bên thứ ba kèm gói/nguồn |

`inventory` liệt kê đủ 22 core, 5 module và Ren'Py/Ruffle/dữ liệu PPSSPP để phát hiện bỏ sót khi config thay đổi. **Chỉ FCEUmm/Gambatte/mGBA/PPSSPP có recipe/commit được dựng**; 18 core còn lại, toàn bộ module và phần web/phụ trợ chưa có recipe mới. Đây chưa phải hoàn thành chuyển mọi lõi. Muốn thêm core/ABI phải bổ sung lock và recipe trong PR riêng; CLI từ chối trước mạng nếu không có recipe.

Workflow `build-cores.yml` chạy test offline, xuất matrix từ lock rồi dựng bằng `ndk-build` (FCEUmm/Gambatte) hoặc CMake/Ninja (mGBA/PPSSPP). Quyền chỉ `contents:read`, không secrets, không job đăng release/config hay job đổi quyền kho. Nguồn checkout đúng SHA, không tự tải submodule/phụ thuộc chưa ghim. Cache NDK/CMake ràng phiên bản/ABI/lock; đầu ra native không lấy từ cache. Tối đa hai job đồng thời, 25 phút/job; lưu artifact 3 ngày.

Chạy offline:

```sh
python3 scripts/test-prepare-core-build.py
python3 scripts/prepare-core-build.py --plan
```

Dựng thật (Linux, cần NDK đúng lock):

```sh
python3 scripts/prepare-core-build.py --core gambatte --abi arm64-v8a \
  --ndk /duong-dan/android-sdk/ndk/22.1.7171670 --out /thu-muc-moi/gambatte-arm64

python3 scripts/prepare-core-build.py --core mgba --abi armeabi-v7a \
  --ndk /duong-dan/android-sdk/ndk/22.1.7171670 \
  --cmake /duong-dan/android-sdk/cmake/3.22.1/bin/cmake --out /thu-muc-moi/mgba-armv7
```

mGBA dùng target `mgba_libretro` trong CMakeLists.txt của đúng commit nguồn; không có Android.mk. Ghim gói SDK CMake `3.22.1`, chuỗi binary `3.22.1-g37088a8` và Ninja `1.10.2` cùng thư mục; sai/thiếu công cụ dừng trước khi checkout. Dùng toolchain NDK, API 26, `c++_static`, `RelWithDebInfo`; tắt giao diện desktop/phụ thuộc tùy chọn (video, Lua, tệp nén) vì app đã giải nén ROM trước khi nạp. Gói cài được strip, ELF gốc còn ký hiệu được giữ riêng; BuildId phải khớp. Không đổi tùy chọn lõi hay dữ liệu game trong app.

PPSSPP theo [PM](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6646860130): core arm64 buildbot kiểm ngày 05/11 có chuỗi phiên bản `9366aa7`, giải được SHA đầy đủ như bảng; kho nguồn đúng là `hrydgard/ppsspp` theo rules của libretro-super. Recipe ghim 17 submodule (kể cả lồng), đối chiếu từng gitlink/URL/SHA trước khi tải; 9 module frontend/test/Apple không dùng được để trống, gặp gitlink ngoài danh sách thì dừng. OpenXR SDK vẫn cần header dù tắt VR. FFmpeg được biên dịch từ nguồn vendored trong commit này, không liên kết gói FFmpeg dựng sẵn.

Dựng PPSSPP bằng lệnh mẫu mGBA ở trên, thay `--core ppsspp` và dùng thư mục kết quả mới. Giữ NDK/API/CMake/Ninja đã ghim, target `ppsspp_libretro`, C++17, thư viện C++ tĩnh, RelWithDebInfo; tắt frontend/test/VR/Discord và cache compiler. ELF nguồn phải có cả `.debug_info`/`.symtab`; thiếu thì dừng, không nhận ký hiệu dynamic-only làm kết quả. Thư viện cài được strip riêng, symbols giữ BuildId tương ứng. Danh sách thư viện hệ thống đồ họa chỉ áp dụng PPSSPP; kiểm phụ thuộc của ba core cũ giữ nguyên.

Nguồn PPSSPP gồm archive cây chính và từng submodule, prefix theo đúng đường trong cây; giải các archive cùng thư mục là có nguồn tương ứng. ZIP cài mang 62 tệp thông báo giấy phép, SOURCE, manifest/hash và NEEDED; nguồn/recipe/lock/symbols ở các artifact riêng của cùng job. Chạy game, tùy chọn, save/rollback và giải lại crash cũ bằng symbols mới [CHƯA KIỂM]; không coi việc ghim nguồn là đã sửa nguyên nhân crash.

Mỗi artifact có ZIP cài với .so ở gốc, manifest hash/kích thước, SOURCE, LICENSES, NEEDED; nguồn tương ứng `git archive`, recipe + lock, ký hiệu ELF cùng BuildId và provenance/hash cho các tệp. Kiểm ELF class/machine/ET_DYN, phụ thuộc NDK cho phép, đủ 25 hàm libretro, BuildId; không sửa tracked source khi dựng. Các recipe không có bản vá/phụ thuộc binary tải ngoài; chỉ PPSSPP có submodule nguồn đã ghim như trên. Repo, ID recipe và tệp/nhãn giấy phép được ràng với từng core trong mã; đường Android.mk cố định của Gambatte nằm ở `libgambatte/libretro/jni/`, không nhận đường/lệnh build tự do từ lock.

Hash trong artifact phục vụ đối chiếu, **chưa được đưa vào config**. Chưa thử game/save trên máy thật [CHƯA KIỂM]; chưa đủ điều kiện thay lõi của người chơi. PR sau cần kiểm tùy chọn, định dạng save và rollback. Cổng V193 phải có đường tải hoạt động trước khi PM/sếp cho chuyển kho riêng; Sol 2 không kích hoạt đăng gói.

PSP chưa được chuyển khỏi URL hiện tại trong config. Sau CI/review, PM cần đăng ZIP, nguồn và symbols vào phiên bản bất biến của packs/cổng tải đã chốt; Sol 2 không phát hành/tạo tag. Khi có URL thật, tải kiểm hash lại rồi làm PR config riêng dùng `cores.ppsspp.artifacts[abi]` và version khác `1`, tăng configVersion theo main, giữ minVersionCode. Không điền URL giả hoặc `latest` vào bản vá để giả lập việc đã chuyển kho.
