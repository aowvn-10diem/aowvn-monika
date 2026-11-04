# Dựng lõi từ nguồn — V198, bước chuẩn bị

Theo [đặc tả đã duyệt](../docs/dac-ta/V198-tu-dung-loi.md) và [PM cho làm PR mã đầu](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6625028499).

Nền lock/provenance + workflow chuẩn bị đã gộp ở #488; hiện có recipe FCEUmm và Gambatte, hai ABI ARM mà APK hiện dựng. NDK `22.1.7171670` dùng cùng phiên bản sẵn có của module `libretrodroid`.

| Core | Commit nguồn (từ manifest snapshot V32, đã đối chiếu API/nguồn) | Giấy phép |
|---|---|---|
| FCEUmm | `7a542dab1e87679921962a9f056186eca425c0c2` | GPL-2.0-or-later + giấy phép NTSC kèm theo |
| Gambatte | `d9d6cd06382d1ced30de34d56d3609452323dab1` | GPL-2.0-only (`libgambatte/src/gambatte.cpp` ghi version 2; không dùng nhãn or-later của FCEUmm) |

`inventory` liệt kê đủ 22 core, 5 module và Ren'Py/Ruffle/dữ liệu PPSSPP để phát hiện bỏ sót khi config thay đổi. **Chỉ FCEUmm/Gambatte có recipe/commit được dựng**; 20 core còn lại, toàn bộ module và phần web/phụ trợ chưa có recipe mới. Đây chưa phải hoàn thành chuyển mọi lõi. Muốn thêm core/ABI phải bổ sung lock và recipe trong PR riêng; CLI từ chối trước mạng nếu không có recipe.

Workflow `build-cores.yml` chạy test offline, xuất matrix từ lock rồi dựng bằng `ndk-build`. Quyền chỉ `contents:read`, không secrets, không job đăng release/config hay job đổi quyền kho. Nguồn checkout đúng SHA, không tự tải submodule/phụ thuộc chưa ghim. NDK cache ràng phiên bản/ABI/lock; đầu ra native không lấy từ cache. Tối đa hai job đồng thời, 25 phút/job; lưu artifact 3 ngày.

Chạy offline:

```sh
python3 scripts/test-prepare-core-build.py
python3 scripts/prepare-core-build.py --plan
```

Dựng thật (Linux, cần NDK đúng lock):

```sh
python3 scripts/prepare-core-build.py --core gambatte --abi arm64-v8a \
  --ndk /duong-dan/android-sdk/ndk/22.1.7171670 --out /thu-muc-moi/gambatte-arm64
```

Mỗi artifact có ZIP cài với .so ở gốc, manifest hash/kích thước, SOURCE, LICENSES, NEEDED; nguồn tương ứng `git archive`, recipe + lock, ký hiệu ELF cùng BuildId và provenance/hash cho các tệp. Kiểm ELF class/machine/ET_DYN, phụ thuộc NDK cho phép, đủ 25 hàm libretro, BuildId; không sửa tracked source khi dựng. Các recipe hiện không có bản vá/submodule/phụ thuộc tải ngoài. Repo, ID recipe và tệp/nhãn giấy phép được ràng với từng core trong mã; đường Android.mk cố định của Gambatte nằm ở `libgambatte/libretro/jni/`, không nhận đường/lệnh build tự do từ lock.

Hash trong artifact phục vụ đối chiếu, **chưa được đưa vào config**. Chưa thử game/save trên máy thật [CHƯA KIỂM]; chưa đủ điều kiện thay lõi của người chơi. PR sau cần kiểm tùy chọn, định dạng save và rollback. Cổng V193 phải có đường tải hoạt động trước khi PM/sếp cho chuyển kho riêng; Sol 2 không kích hoạt đăng gói.
