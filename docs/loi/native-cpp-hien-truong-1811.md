# Hiện trường C++ native: `ppsspp` + `desmume` (18/11/2026)

Nhánh `loi/native-cpp`, nền `origin/main` `4c4757b1` (configVersion 65).
Phạm vi: `libretrodroid/src/main/cpp/**` — vùng này **không** PR mở nào chạm tại thời điểm làm.
Người lập: phiên con **Trưởng nhóm Lỗi**. Mọi kết luận chưa có khung đã giải mã đều ghi **[CHƯA KIỂM]**.

## 0. Kết luận trước

| Nhóm | Số issue | Lượt | Đã vá trong PR này? | Nguyên nhân đã chứng minh? |
|---|---:|---:|---|---|
| `ppsspp` / `native` (#316 114, #372 90, #520 1, #478 1) | 4 | 206 | **Có** — 7 chỗ deref `core` rỗng + 1 race VFS + 1 race biến môi trường | **Không.** Giả thuyết "thread nền của lõi gọi callback sau `deinitialize`" chỉ đúng một phần: đường callback **đã** có null-guard, nhưng dữ liệu chia sẻ phía sau thì chưa khoá. Nghi phạm mạnh nhất còn lại là `dlclose()` ngay sau `retro_deinit()` — **[CHƯA KIỂM]** |
| `desmume` / `native` (#369 71, #591 19, #331 3, #377 3, #562 1) | 5 | 97 | **Có** (dùng chung bản vá trên) | **Không.** Ứng viên 1 vẫn là `desmume_num_cores` > 1 ở mức `full` (cấu hình, không phải mã); ứng viên `0RGB1555` ghi tại chỗ: tin cậy **thấp**; `SET_MEMORY_MAPS` (`environment.cpp:371`) **đã loại trừ** |

- **Không đóng issue nào.** Không có khung + không tái hiện được trên máy thật ⇒ chưa đủ điều kiện đóng (xem §6).
- Phần đã xong từ trước, **không làm lại**: #644 (`ff775cb4`) vá `serializeState`/`unserializeState`/`serializeSRAM`; #601 đã vào main (config `ppsspp_vertex_cache = disabled` `config/monika-config.json:964`, `lite` = `480x272` `:969`).
- Bản vá C++ trong PR này **biên dịch được** cả `arm64-v8a` và `armeabi-v7a` bằng NDK 22.1.7171670 (§4). **[CHƯA KIỂM]** trên máy thật.

## 1. Giả thuyết `ppsspp`: thread nền của lõi gọi callback sau `deinitialize`/`unload`

### 1.1. Đường callback nào thật sự có thể sập

`destroy()` gỡ lõi và hạ toàn bộ con trỏ (`libretrodroid.cpp:497-526`):

| Bước | Dòng | Hệ quả nếu lõi còn thread |
|---|---|---|
| `retro_unload_game()` + `retro_deinit()` | `:513-516` | Lõi tự dọn; **hợp đồng libretro không bắt buộc lõi phải join hết thread nội bộ** |
| `video/core/audio/input = nullptr` | `:517-522` | Callback video/audio/input **an toàn**: đều kiểm null — `handleVideoRefresh` `:664-677`, `handleAudioCallback` `:679-684`, `handleSetInputState` `:686-696`, `handleGetCurrentFrameBuffer` `:698-703` |
| `Environment::deinitialize()` | `:524` | Xoá `systemDirectory`/`savesDirectory` (`environment.cpp:47-77`) — **không** có cờ "đã đóng", callback vẫn được nhận |
| `VFS::deinitialize()` | `:525` | `virtualFiles.clear()` — **không khoá** (xem §1.2) |
| `core = nullptr` (huỷ `Core`) | `:518` → `core.cpp:69-78` | **`dlclose(libHandle)`** — xem §1.3 |

⇒ Giả thuyết "callback sau `deinitialize` làm sập" **không đúng ở đường video/audio/input** (đã null-guard từ #606). Nó chỉ còn đúng ở hai chỗ dữ liệu chia sẻ bên dưới, và ở `dlclose`.

### 1.2. Hai lỗ hổng thật, đã vá trong PR này

1. **Race trên `VFS::virtualFiles`** — `vfs.cpp:119-122` cũ: `deinitialize()` gọi `clear()` không khoá, trong khi `virtualOpen()` (`:124-153`) duyệt cùng vector qua `findVirtualFile()` (`:160-167`). Lõi mở file bằng VFS đúng lúc ta đóng game ⇒ `clear()` huỷ `VFSFile` (kèm `std::string` tên tệp) trong khi luồng kia đang `strcmp` trên chính chuỗi đó ⇒ **đọc vùng nhớ đã giải phóng**, khung nằm trong `libretrodroid.so`.
   Đã vá: `mutable std::mutex virtualFilesLock` (`vfs.h:76-80`), khoá ở `initialize`/`deinitialize`/`virtualOpen` (`vfs.cpp:115`, `:120`, `:128`), giữ khoá suốt lời gọi để con trỏ `VFSFile*` không bị `clear()` vô hiệu.
2. **`variableValueBuffer` dùng chung giữa các luồng** — `environment.cpp:144-148` cũ trả `variableValueBuffer.c_str()` sau khi nhả `variablesLock`. Lõi gọi `RETRO_ENVIRONMENT_GET_VARIABLE` từ **hai** luồng nền (ppsspp: audio + GPU) thì luồng sau ghi đè/giải phóng chuỗi luồng trước đang đọc.
   Đã vá: `static thread_local std::string variableValueBuffer` (`environment.h:161-164`, định nghĩa `environment.cpp:34-36`) — mỗi luồng một vùng đệm, thời gian sống dài hơn (hết luồng), không đổi hành vi khi chỉ một luồng.
   *Tin cậy đây là nguyên nhân của 206 lượt: **thấp** — giá trị biến ngắn (SSO) nên hiếm khi `realloc`. Vá vì đúng về ngôn ngữ, không phải vì đã chứng minh.*

### 1.3. Nghi phạm mạnh nhất còn lại: `dlclose()` ngay sau `retro_deinit()` — **[CHƯA KIỂM]**

- `Core::close()` gọi `dlclose(libHandle)` (`core.cpp:69-74`), chạy trong `~Core()` (`:76-78`) khi `LibretroDroid::destroy()` đặt `core = nullptr` (`libretrodroid.cpp:518`).
- Trên bionic, `dlclose` **munmap** segment của `.so` khi refcount về 0. Nếu lõi còn một thread nội bộ (kể cả thread đang chạy dở hàm huỷ), lệnh kế tiếp của thread đó nằm trong vùng **đã unmap** ⇒ `SIGSEGV`/`SIGBUS` với PC **bên trong `.so` của lõi**, không có khung nào của `libretrodroid.so` phía trên.
- Khớp hình dạng dữ liệu: #316 trải **51 thiết bị** khác nhau (`docs/loi/ppsspp-native-1311.md` §1) ⇒ không phải lỗi driver của một máy; #369 trải 31 máy. Cùng một cơ chế giải thích được cả hai nhóm.
- Tiền lệ ngay trong mã của ta: oboe tự ghi chú **không** `dlclose` vì "thread_local … sẽ segfault nếu thư viện chứa nó bị dlclose" (`libretrodroid/src/main/cpp/oboe/src/aaudio/AAudioLoader.cpp:26-38`).
- **Chưa vá** vì chưa chứng minh được, và bỏ `dlclose` là đổi chính sách vòng đời tài nguyên (giữ `.so` tới khi tiến trình chết): chỉ nên làm sau khi có bằng chứng từ tombstone, hoặc làm sau cờ bật/tắt để A/B. Xem §5 thí nghiệm 3.

### 1.4. Bảy chỗ deref `core` rỗng còn lại (đã vá — cùng lớp lỗi #644)

#644 chỉ vá `serializeState`/`unserializeState`/`serializeSRAM`. Các lời gọi JNI cùng loại vẫn deref `core` mà không kiểm, trong khi `create()` có thể ném lỗi (`libretrodroid.cpp:357-359` khi thiếu GLES3, `core.cpp:41-42` khi `dlopen` hỏng) và `destroy()` đặt `core = nullptr`:

| Hàm | Dòng sau vá | Trước vá |
|---|---:|---|
| `unserializeSRAM` | `:173-178` | `core->retro_get_memory_size` trên con trỏ rỗng |
| `loadGameFromPath` | `:369-375` | `core->retro_get_system_info` |
| `loadGameFromBytes` | `:415-419` | như trên |
| `loadGameFromVirtualFiles` | `:446-451` | như trên |
| `step` | `:566-571` | `core->retro_run()` — GLThread chạy thêm một khung sau `destroy()` |
| `reset` | `:708-713` | `core->retro_reset()` ("chơi lại" bấm đúng lúc Activity đóng) |
| `resetCheat` / `setCheat` | `:744-749`, `:756-760` | `core->retro_cheat_*` |

Ba hàm `loadGame*` ném `std::runtime_error` giống nhánh lỗi sẵn có (`:403-404`), và JNI đã bắt `std::exception` (`libretrodroidjni.cpp:439-441`, `:455-457`, `:468-479`, `:490-515`) nên không thành `std::terminate`.

## 2. Giả thuyết `desmume`: ba ứng viên, mức tin cậy

| # | Ứng viên | Vị trí | Tin cậy | Vì sao |
|---|---|---|---:|---|
| 2.1 | JIT/đa luồng dựng hình của lõi khi `desmume_num_cores` > 1 | `config/monika-config.json:826` (`2`), `:831` (`lite` = `1`), `:834` (`full` = `4`) | **Trung bình–cao** | DeSmuME có nhiều báo cáo sập khi > 1 luồng 3D; #369 trải 31 máy nhưng #591 dồn 2 máy (một người chơi lặp lại) — khớp "máy yếu + full". **Không sửa được bằng mã trong repo**; đây là cấu hình, cần PM duyệt + tăng `configVersion` |
| 2.2 | Ghi tại chỗ trên bộ đệm của lõi khi pixel format `0RGB1555` | `renderers/es3/imagerendereres3.cpp:30-31` gọi `:116-124`; `es2/imagerendereres2.cpp:35-36` gọi `:105-113` | **Thấp** | `const void *data` bị cast `uint16_t*` rồi ghi đè **bộ đệm của lõi**. Chỉ chạy khi lõi đặt `SET_PIXEL_FORMAT = 0RGB1555` (`environment.cpp:268-272`). Nếu lõi đưa vùng chỉ-đọc (hoặc thread khác đang đọc) ⇒ `SIGSEGV`/dữ liệu rác. **Chưa biết DeSmuME dùng format nào** — cần logcat (§5 thí nghiệm 4) |
| 2.3 | Bản đồ bộ nhớ lõi bị giải phóng khi lõi đặt lại | `environment.cpp:371` → `:526-545` | **Đã loại trừ** | `SET_MEMORY_MAPS` đã **sao chép sâu** descriptor + chuỗi `addrspace` (`:529-541`), không giữ con trỏ của lõi. Tài liệu cũ `docs/ds/chan-doan-desmume-native.md` §3.6 nay không còn đúng |
| 2.4 | `dlclose` sau unload (dùng chung với ppsspp) | §1.3 | **Trung bình** | DeSmuME cũng tạo thread khi `num_cores` > 1 → cùng cơ chế |

## 3. Vì sao chưa vá "lõi native"

Nguyên tắc của đội: chỉ vá khi biết hàm/tệp. Không có `CRASH_ADMIN_TOKEN` trong phiên này nên **không** đọc được `frames`/`signal`/`fault_addr`/`BuildId` của báo cáo gốc; chữ ký chỉ là băm. Hai nhóm này đều chưa có bảng khung. Vá `ppsspp`/`desmume` lúc này là đoán, và mã lõi không nằm trong repo (tải từ `aowvn-monika-packs` / `buildbot nightly`).

## 4. Đã vá gì và kiểm chứng tới đâu

| Tệp | Thay đổi | Dòng |
|---|---|---:|
| `libretrodroid.cpp` | 7 chỗ kiểm `core` rỗng (bảng §1.4) | +47 dòng |
| `vfs/vfs.h`, `vfs/vfs.cpp` | khoá `virtualFilesLock` cho `initialize`/`deinitialize`/`virtualOpen` | +6/+6 |
| `environment.h`, `environment.cpp` | `variableValueBuffer` → `static thread_local` | +4/+3 |

Kiểm chứng đã chạy (máy Windows của repo, NDK `22.1.7171670` đúng `ndkVersion` trong `app/build.gradle.kts:36`):

```
cmake -G Ninja -DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -S libretrodroid/src/main/cpp -B <build>
ninja -C <build>
```

| ABI | Kết quả | Thời gian | Ghi chú |
|---|---|---:|---|
| `arm64-v8a` | `exit=0`, `liblibretrodroid.so` 14.494.104 B | 10 s (incremental, 47 s từ đầu) | không lỗi, không cảnh báo mới ở 5 tệp đã sửa |
| `armeabi-v7a` | `exit=0`, `liblibretrodroid.so` 10.370.536 B | 33 s | `thread_local` + `std::mutex` biên dịch được cả 32-bit |

**[CHƯA KIỂM]:** chạy trong game thật; `./gradlew assembleDebug` (không chạy để giữ ngân sách ổ đĩa 5 GB); hành vi khi lõi thật sự gọi callback sau `unload` (chưa dựng được tình huống đó).

## 5. Thí nghiệm cần làm trên máy thật (theo thứ tự rẻ → đắt)

1. **Lọc theo thời điểm chết** (rẻ nhất, quyết định cả hai nhóm): trên báo cáo có `kind=native`, xem `crumbs` quanh lúc chết có `Diagnostics.stage/end` hoặc dòng `Performing libretrodroid destroy` hay không. Nếu **phần lớn** 206+97 lượt chết *khi rời game* ⇒ nghi phạm §1.3 lên đầu; nếu chết *giữa màn chơi* ⇒ quay về §2.1/§2.2.
   ```bash
   CRASH_ADMIN_TOKEN=<mã> scripts/crash-reports.sh <id>   # xem kind, signal, frames, BuildId, crumbs
   ```
2. **Giải ký hiệu** (APK đã có `-Wl,--build-id=sha1`, `CMakeLists.txt:112`):
   ```bash
   gh release download cores-ppsspp-1 --repo aowvn-10diem/aowvn-monika-packs -p 'symbols-ppsspp-arm64-v8a.zip'
   scripts/crash-reports.sh <id> --symbols-dir <đã-giải-nén> --symbolizer <llvm-symbolizer>
   ```
   Đọc **tên thread** trong tombstone. Thread không phải `GLThread`/`main` ⇒ xác nhận nhánh "thread nền của lõi" (§1.1). Riêng `desmume` chưa ghim bản (`config:823` = `nightly/latest`, `version: "1"`) nên khung chỉ đối chiếu được từ bản APK mới, không giải ngược được báo cáo cũ.
3. **A/B `dlclose`**: dựng 2 APK thử (chỉ để đo, không phát hành): bản A như hiện tại, bản B sửa `Core::close()` (`core.cpp:69-74`) thành **không** gọi `dlclose` (giữ handle tới khi tiến trình `:game` chết; `RetroActivity` chạy trong tiến trình riêng — `app/src/play/AndroidManifest.xml:55` — nên chi phí chỉ là vùng `mmap` của một `.so`). Chơi/đóng game 50 lượt trên 2 máy có báo cáo, đếm số lần chết tiến trình. B hết sập ⇒ xác nhận §1.3, khi đó mới bàn chuyện bỏ `dlclose` vĩnh viễn.
4. **Xác định pixel format của DeSmuME**: logcat khi nạp ROM — `logcat -s Libretro\ Core | grep SET_PIXEL_FORMAT` (in ở `environment.cpp:269`). Chỉ khi thấy `0RGB1555` mới xét §2.2; nếu `RGB565`/`XRGB8888` thì loại hẳn.
5. **A/B `desmume_num_cores`** (không cần sửa mã): máy của #591 chạy `num_cores` `2` so với `1`, cùng ROM ≥ 30 phút, đếm số lần chết. Đây là thí nghiệm duy nhất có thể biến 97 lượt thành "xử lý bằng cấu hình".

## 6. Điều kiện đóng issue (chưa đóng cái nào)

- #316 / #372 / #478 / #520 và #369 / #591 / #331 / #377 / #562: chỉ đóng khi có **ít nhất một** trong hai:
  1. bảng khung (tên hàm + dòng) cho từng chữ ký, chỉ ra khung thuộc `.so` nào; hoặc
  2. kết quả A/B ở §5 (thí nghiệm 3 hoặc 5) cho thấy số lần chết giảm về 0 trên máy có báo cáo.
- Bản vá trong PR này **không** đóng issue nào: nó bịt các đường deref/race đọc-mã-thấy-được, nhưng chưa có bằng chứng nối chúng với 303 lượt.

## 7. Đề xuất (không tự làm — tệp khoá / cần PM duyệt)

**7.1. Cấu hình (PM duyệt, tăng `configVersion` 65 → 66, đồng bộ Cloudflare):**
- `cores.desmume.perf` mức thường: `desmume_num_cores` `2` → `1` (`config/monika-config.json:826`); mức `full` `4` → `2` (`:834`). Giữ `lite` = `1` (`:831`). Đây là thí nghiệm 5 chạy trên toàn bộ người chơi, rẻ nhất trong mọi phương án.
- Ghim bản lõi `desmume` thay `nightly/latest` (`:823`, `version: "1"`): không ghim thì báo cáo cũ không bao giờ đối chiếu được với một `.so` cụ thể.

**7.2. Cho PR đang mở (chỉ đề xuất bằng văn bản, không commit vào tệp của người khác):**
- `#656` (`GLRetroView.kt`, `RetroActivity.kt`): thêm vệt sự kiện quanh vòng đời — trước `destroy()`, sau `destroy()`, và mỗi lần `step()` bị bỏ qua. Cụ thể: `Diagnostics.crumb(ctx,"core","destroy-start")` / `"destroy-done"`; nhánh `step()` trả sớm (mới, `libretrodroid.cpp:567`) nên có log `LOGE` để đếm.
- `#655` (`Diagnostics.kt`): báo cáo `native` cần kèm **tên thread** chết (lấy từ `ApplicationExitInfo`/tombstone). Thiếu tên thread thì không phân biệt được "lõi sập trong `retro_run`" với "thread nền chết sau `unload`" — đúng chỗ đang bị chặn ở cả hai nhóm.
