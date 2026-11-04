# Dựng lõi từ nguồn — V198, bước chuẩn bị

Theo [đặc tả đã duyệt](../docs/dac-ta/V198-tu-dung-loi.md) và [PM cho làm PR mã đầu](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6625028499).

PR này đưa nền lock/provenance + workflow chuẩn bị vào dùng bằng một recipe FCEUmm, hai ABI ARM mà APK hiện dựng. Commit nguồn `7a542dab1e87679921962a9f056186eca425c0c2` lấy từ manifest snapshot V32, đã đối chiếu API/nguồn tại commit đó; không lấy HEAD để đoán phiên bản binary. NDK `22.1.7171670` dùng cùng phiên bản sẵn có của module `libretrodroid`.

`inventory` liệt kê đủ 22 core, 5 module và Ren'Py/Ruffle/dữ liệu PPSSPP để phát hiện bỏ sót khi config thay đổi. **Chỉ `cores.fceumm` có recipe/commit được dựng trong PR này**; 21 core còn lại, toàn bộ module và phần web/phụ trợ chưa có recipe mới. Đây chưa phải hoàn thành chuyển mọi lõi. Muốn thêm core/ABI phải bổ sung lock và recipe trong PR riêng; CLI từ chối trước mạng nếu không có recipe.

Workflow `build-cores.yml` chạy test offline, xuất matrix từ lock rồi dựng bằng `ndk-build`. Quyền chỉ `contents:read`, không secrets, không job đăng release/config hay job đổi quyền kho. Nguồn checkout đúng SHA, không tự tải submodule/phụ thuộc chưa ghim. NDK cache ràng phiên bản/ABI/lock; đầu ra native không lấy từ cache. Tối đa hai job đồng thời, 25 phút/job; lưu artifact 3 ngày.

Chạy offline:

```sh
python3 scripts/test-prepare-core-build.py
python3 scripts/prepare-core-build.py --plan
```

Dựng thật (Linux, cần NDK đúng lock):

```sh
python3 scripts/prepare-core-build.py --core fceumm --abi arm64-v8a \
  --ndk /duong-dan/android-sdk/ndk/22.1.7171670 --out /thu-muc-moi/fceumm-arm64
```

Mỗi artifact có ZIP cài với .so ở gốc, manifest hash/kích thước, SOURCE, LICENSES, NEEDED; nguồn tương ứng `git archive`, recipe + lock, ký hiệu ELF cùng BuildId và provenance/hash cho các tệp. Kiểm ELF class/machine/ET_DYN, phụ thuộc NDK cho phép, đủ 25 hàm libretro, BuildId; không sửa tracked source khi dựng. Recipe hiện không có bản vá/submodule/phụ thuộc tải ngoài.

Hash trong artifact phục vụ đối chiếu, **chưa được đưa vào config**. Chưa thử game/save trên máy thật [CHƯA KIỂM]; chưa đủ điều kiện thay lõi của người chơi. PR sau cần kiểm tùy chọn, định dạng save và rollback. Cổng V193 phải có đường tải hoạt động trước khi PM/sếp cho chuyển kho riêng; Sol 2 không kích hoạt đăng gói.
