# Chẩn đoán nhóm `desmume` / `native` (chưa sửa mã)

Ngày 13/11/2026 · nhánh `loi/crash-cpp` · nền `main` `ab8a2135`
Nguồn: 75 issue mở nhãn `crash-moi` (`gh issue list --label crash-moi --state open --limit 100`), đọc thân issue.

## 1. Vì sao chọn nhóm này

| Nhóm (lõi / loại) | Issue | Tổng lượt quan sát | PR đã có |
|---|---:|---:|---|
| `melondsds` / `anr` | 9 | 421 | mã: PR #606 (đã gộp, `25ca0688`, `ac2944c0`); đặc tả: PR #559 |
| `ppsspp` / `native` | 4 | 206 | mã: cùng PR #606 (khoá `environment.cpp`, `libretrodroid.cpp`) |
| **`desmume` / `native`** | **5** | **97** | **không có PR mã**; PR #601 chỉ hạ `desmume_internal_resolution` |
| `citra` / `native` | 2 | 32 | phiên Lỗi 2 (`AzaharActivity`) |
| `desmume` / `anr` | 4 | 7 | — |

`desmume / native` là nhóm C++/GL **lớn nhất còn tái diễn mà chưa có PR sửa mã**. PR #601 đang mở chỉ đổi độ phân giải trong dựng hình, không đụng đường nạp/lưu trạng thái nên không thể là bản sửa của nhóm này.

| Issue | Chữ ký (12 ký tự) | Lượt | Mới | Mở ngày | Số máy (hash thiết bị) |
|---|---|---:|---:|---|---:|
| [#369](https://github.com/aowvn-10diem/aowvn-monika/issues/369) | `a093f6541c51` | 71 | +8 | 26/10 | 31 |
| [#591](https://github.com/aowvn-10diem/aowvn-monika/issues/591) | `3ad0c6cc6d47` | 19 | +19 | 10/11 | 2 |
| [#331](https://github.com/aowvn-10diem/aowvn-monika/issues/331) | `c8765c891f30` | 3 | +3 | 22/10 | 3 |
| [#377](https://github.com/aowvn-10diem/aowvn-monika/issues/377) | `ce10c8fe6467` | 3 | +3 | 27/10 | 2 |
| [#562](https://github.com/aowvn-10diem/aowvn-monika/issues/562) | `10f53afc0448` | 1 | +1 | 09/11 | 1 |

#369 trải trên 31 máy khác nhau ⇒ không phải lỗi của một ROM/một máy; #591 dồn vào 2 máy ⇒ có thể là một người chơi lặp lại sau khi cập nhật.

## 2. Giới hạn bằng chứng của lượt này

- Phiên này **không có** `CRASH_ADMIN_TOKEN`, nên không đọc được `frames`, `signal`, `fault_addr`, `BuildId` của từng báo cáo. Chữ ký chỉ là băm (`lõi + loại + tối đa 3 khung đầu`), không suy ra được khung nào. Mọi kết luận về khung dưới đây đều là **[CHƯA KIỂM]**.
- Lõi `desmume` tải từ `buildbot.libretro.com/nightly/android/latest/...` (`config/monika-config.json:823`), `version` chỉ là `"1"` ⇒ **không ghim bản lõi**. Cùng một chữ ký có thể đến từ nhiều bản `.so` khác nhau; ngược lại, lỗi do bản nightly mới cũng không phân biệt được với lỗi cũ.
- `main` hiện chưa có cờ `-Wl,--build-id=sha1` (`libretrodroid/src/main/cpp/CMakeLists.txt:109` chỉ có `max-page-size`); cờ này nằm trong PR #601 đang mở. Vì vậy APK đã phát hành **không có `BuildId`** ⇒ báo cáo cũ không giải được ký hiệu, chỉ đọc được tên `.so` trong tombstone.

## 3. Ứng viên nguyên nhân, xếp theo mức khớp với `desmume`

Mỗi mục ghi: dấu hiệu nếu đúng · vị trí mã · cách kiểm.

### 3.1. Lõi `desmume` tự sập (JIT ARM / dựng hình đa luồng) — khả năng cao nhất
- Dấu hiệu: khung đầu tiên trong tombstone thuộc `desmume_libretro_android.so`, không có khung `libretrodroid.so` phía trên.
- Cấu hình liên quan: `desmume_num_cores` = `2` (mặc định), `4` (mức `full`), `1` (mức `lite`) — `config/monika-config.json:826`, `:834`, `:831`. Đây là số luồng dựng hình 3D của DeSmuME; nhiều máy gặp sập khi > 1.
- Cách kiểm: A/B trên 2 máy có báo cáo (ưu tiên máy của #591): `num_cores` `1` so với `2`, chơi cùng một ROM ≥ 30 phút, đếm số lần chết tiến trình. Nếu tắt đa luồng hết sập ⇒ xử lý bằng cấu hình (hạ `num_cores` theo mức máy), không cần sửa mã.

### 3.2. Cấp bộ đệm 0 byte rồi đưa cho lõi ghi (lưu state)
- Vị trí: `libretrodroid/src/main/cpp/libretrodroid.cpp:661-670` —
  `size = core->retro_serialize_size(); auto data = new int8_t[size]; core->retro_serialize(data, size);`
  và đường JNI `libretrodroidjni.cpp:195-215`.
- Vì sao khớp: lõi trả `retro_serialize_size() == 0` khi chưa sẵn sàng (đang nạp, vừa `retro_reset`, hoặc sau khi gỡ game). Ta vẫn cấp bộ đệm 0 byte rồi gọi `retro_serialize`. Lõi **không** kiểm tham số `size` (không bắt buộc theo API libretro) sẽ ghi toàn bộ state vào vùng nhớ 0 byte ⇒ hỏng heap ⇒ `SIGSEGV`/`SIGABRT` với khung nằm trong `.so` của lõi, tức báo cáo bị gắn nhãn `desmume / native` dù lỗi bắt nguồn từ phía ta.
- Cách kiểm: trong báo cáo, tìm `abort message`/`signal 6` và `fault addr` gần vùng heap; đối chiếu logcat quanh thời điểm chết có dòng `Performing libretrodroid destroy` / thao tác lưu state hay không. Kiểm chắc chắn bằng một bản dựng thử có `LOGW` in `retro_serialize_size()` mỗi lần lưu state.
- Nếu đúng: sửa nhỏ — `core` rỗng hoặc `size == 0` thì trả `{nullptr, 0}` (JNI trả mảng rỗng), chỉ gọi `retro_serialize` khi `size > 0` và kiểm giá trị trả về.

### 3.3. Gọi JNI vào lõi đã `destroy()` (con trỏ rỗng)
- Vị trí: `libretrodroid.cpp:158-161` (`unserializeState`), `:185-193` (`serializeSRAM`), `:655-659` (`reset`) — ba hàm này **không** kiểm `core` trước khi gọi, khác với `pause/resume/destroy` đã được vá ở PR #606.
- Vì sao khớp: nút "chơi lại"/tải state bấm đúng lúc Activity đang đóng, hoặc lưu state trong `onPause` sau khi view đã `destroy()` ⇒ deref rỗng. Tombstone khi đó có `fault addr` rất thấp (0x0 … 0x100), khung trong `libretrodroid.so`.
- Cách kiểm: xem `fault addr` + 3 khung đầu; nếu có `libretrodroid.so` thì loại trừ 3.1.
- Nếu đúng: sửa nhỏ — thêm kiểm `if (!core) return …;` ở ba hàm trên (không đổi hành vi khi lõi còn sống).

### 3.4. `serializeSRAM` sao chép từ con trỏ rỗng
- Vị trí: `libretrodroid.cpp:185-193` — `memcpy(data, retro_get_memory_data(SAVE_RAM), size)` không kiểm nguồn.
- Vì sao khớp: một số lõi trả `retro_get_memory_size(SAVE_RAM) > 0` nhưng `retro_get_memory_data` vẫn `nullptr` trước khi firmware/băng save được khởi tạo.
- Cách kiểm: như 3.3 (`fault addr` thấp, khung `memcpy` trong `libretrodroid.so`).
- Nếu đúng: sửa nhỏ — trả `{nullptr, 0}` khi nguồn rỗng.

### 3.5. Đổi `0RGB1555` tại chỗ trên bộ đệm của lõi
- Vị trí: `libretrodroid/src/main/cpp/renderers/es3/imagerendereres3.cpp:116-124` — `const void *data` bị cast thành `uint16_t*` rồi ghi đè.
- Vì sao ít khả năng: chỉ chạy khi lõi đặt `SET_PIXEL_FORMAT` = `0RGB1555`; DeSmuME thường dùng `RGB565`. Nếu đúng thì đây là ghi đè dữ liệu lõi đang dùng ở luồng khác ⇒ dữ liệu rác, hiếm khi sập.
- Cách kiểm: logcat có `Called SET_PIXEL_FORMAT` + giá trị; nếu là `0RGB1555` mới xét.
- Nếu đúng: sửa nhỏ — sao chép sang bộ đệm riêng trước khi đổi, không ghi vào `data` của lõi.

### 3.6. Bản đồ bộ nhớ của lõi bị giải phóng khi lõi đặt lại
- Vị trí: `environment.cpp:523-542` — `SET_MEMORY_MAPS` lần hai `clear()` hai vector mà `memoryMap`/`rc_client` vẫn giữ con trỏ.
- Chỉ ảnh hưởng khi bật RetroAchievements cho NDS; không phải đường mặc định. Ghi lại để loại trừ, không xử lý trong lượt này.

## 4. Cách kiểm chung (khi có `CRASH_ADMIN_TOKEN`)

```bash
# 1. Một báo cáo: khung, tín hiệu, fault addr, BuildId
scripts/crash-reports.sh <id>

# 2. Giải ký hiệu native (chỉ APK có BuildId sha1, tức từ PR #601 trở đi)
scripts/crash-reports.sh <id> \
  --symbols-dir /path/symbols-desmume-<run>-<abi> \
  --symbolizer /path/llvm-symbolizer

# 3. Nhóm theo khung thay vì theo băm, để tách 5 chữ ký ở trên
scripts/crash-report-groups.py --help
```

Đọc kết quả theo bảng quyết định:

| Khung đầu thuộc | Kết luận | Việc tiếp theo |
|---|---|---|
| `desmume_libretro_android.so` | lỗi trong lõi (3.1) | A/B `desmume_num_cores`; cân nhắc ghim bản lõi |
| `libretrodroid.so` (`memcpy`, `retro_serialize`) | lỗi phía ta (3.2/3.4) | PR mã nhỏ như mô tả |
| `libretrodroid.so` (`reset`, `unserializeState`) + `fault addr` thấp | gọi sau `destroy()` (3.3) | PR mã nhỏ như mô tả |
| `libGLESv2`/`libEGL` | đường dựng hình | mở lại nhánh `ppsspp`/`citra` |

## 5. Đề xuất (không tự làm trong lượt này)

1. Không đóng #369/#591/#331/#377/#562 cho tới khi có bảng khung ở mục 4.
2. Chạy A/B `desmume_num_cores = 1` (mục 3.1) trên 2 máy có báo cáo — rẻ nhất, không cần sửa mã, và là ứng viên số một.
3. Nếu bảng khung chỉ vào `libretrodroid.so`, mở PR mã nhỏ cho 3.2/3.3/3.4 (mỗi thay đổi ≤ 10 dòng, không đổi hành vi khi lõi còn sống).
4. Cân nhắc ghim bản `.so` của `desmume` (thay `nightly/latest` bằng bản có ngày cụ thể) để lần sau đối chiếu được đúng bản lõi — hiện `version: "1"` không cho biết báo cáo đến từ bản nào.
