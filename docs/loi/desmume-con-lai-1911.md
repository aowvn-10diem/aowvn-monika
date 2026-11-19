# `desmume`: phần còn lại sau #644/#663 (19/11/2026)

Nhánh `loi2/desmume`, nền `origin/main` `45e1c754` (configVersion 66, app 0.8.14; 0.8.15 là Pre-release).
Chỉ **tài liệu**: không sửa mã, không đóng issue, không đụng tệp khoá.

Tài liệu này **không làm lại** hai kết luận đã có, chỉ trích dẫn và đi tiếp phần còn thiếu:

- `docs/ds/chan-doan-desmume-native.md` (PR #616, 13/11): 5 ứng viên §3.1–3.6, cách đo §4.
- `docs/loi/native-cpp-hien-truong-1811.md` §2 (PR #663, 18/11): chốt lại 4 ứng viên, loại trừ `SET_MEMORY_MAPS`.

## 1. Sáu issue, kết luận hôm nay

| Issue | Loại | Lượt | Bản vá đã phủ | Kết luận 19/11 |
|---|---|---:|---|---|
| [#369](https://github.com/aowvn-10diem/aowvn-monika/issues/369) `a093f6541c51` | native | 71 (31 máy) | #644 + #663 | **Chờ 0.8.15 chính thức** — không đóng |
| [#591](https://github.com/aowvn-10diem/aowvn-monika/issues/591) `3ad0c6cc6d47` | native | 19 (2 máy) | #644 + #663 | **Chờ 0.8.15 chính thức** — không đóng |
| [#331](https://github.com/aowvn-10diem/aowvn-monika/issues/331) `c8765c891f30` | native | 3 (3 máy) | #644 + #663 | Soạn sẵn câu đóng (§6), **chưa đăng** |
| [#377](https://github.com/aowvn-10diem/aowvn-monika/issues/377) `ce10c8fe6467` | native | 3 (2 máy) | #644 + #663 | Soạn sẵn câu đóng (§6), **chưa đăng** |
| [#562](https://github.com/aowvn-10diem/aowvn-monika/issues/562) `10f53afc0448` | native | 1 (1 máy) | #644 + #663 | Soạn sẵn câu đóng (§6), **chưa đăng** |
| [#368](https://github.com/aowvn-10diem/aowvn-monika/issues/368) `751363ed54f2` | op-fail | 1 (1 máy) | **Không** — ngoài `libretrodroid` | Chờ #671 + cần `op`/`bước` từ token |

`desmume/native` = 5 issue · 97 lượt · tách 5 chữ ký; `desmume/op-fail` = 1 issue · 1 lượt.
Tổng `desmume` = 98 lượt, khớp bảng tồn 19/11 (`docs/loi/TON-1911.md` dòng 53: `desmume | 6 | 98`).

## 2. Vì sao hôm nay không đóng issue nào

Luật A102 cần **hai** điều: bản vá đã ở bản **chính thức** và số lượt **không tăng** sau bản vá.

| Mốc | Thời điểm | Chứa gì |
|---|---|---|
| `v0.8.13` | 2026-11-13T08:46Z | không có vá `libretrodroid` nào của nhóm này |
| #644 `ff775cb4` | 2026-11-14T02:24Z → **có trong `v0.8.14`** | `serializeState` / `unserializeState` / `serializeSRAM` |
| `v0.8.14` | 2026-11-17T00:49Z | bản chính thức đang phát hành |
| #663 `694b208c` | gộp 2026-11-19T00:38Z → **chỉ có trong `v0.8.15`** | 7 chỗ deref `core` rỗng + race VFS + race biến môi trường |
| `v0.8.15` | 2026-11-19T04:48Z | **Pre-release** |

Kiểm bằng lệnh, không suy đoán:

```bash
git merge-base --is-ancestor ff775cb4 v0.8.14   # 0 = có  → #644 đã phát hành
git merge-base --is-ancestor 694b208c v0.8.14   # 1 = không
git merge-base --is-ancestor 694b208c v0.8.15   # 0 = có  → #663 chỉ ở Pre-release
```

Ba phần vá (`reset`, `resetCheat`/`setCheat`, `step`, `loadGame*`, `unserializeSRAM`) **chỉ** nằm trong 0.8.15 Pre-release, nên nhóm này thuộc diện "chờ bản chính thức" — chính thức sớm nhất 20/11 12:40 (giờ VN).

## 3. Bằng chứng số lượt (có giới hạn, ghi rõ)

| Issue | `count` trong thân | `updatedAt` của issue | Diễn giải |
|---|---:|---|---|
| #369 | 71 | 2026-11-13T06:06Z | lượt cuối trước cả `v0.8.13` |
| #591 | 19 | 2026-11-13T06:07Z | như trên; 19 lượt dồn 2 khoá `seen` |
| #331 | 3 | 2026-10-22T13:54Z = `createdAt` | **chưa từng tăng** sau lần tạo |
| #377 | 3 | 2026-10-27T19:29Z = `createdAt` | như trên |
| #562 | 1 | 2026-11-09T08:06Z = `createdAt` | như trên |
| #368 | 1 | 2026-10-26T20:38Z = `createdAt` | như trên |

**Giới hạn phải nói rõ:** `scripts/crash-issue-bot.py:123` bỏ qua lần chạy có `delta < 5`, nên "thân issue không đổi" chỉ chứng minh **dưới 5 lượt mới mỗi lần chạy bot**, không chứng minh tuyệt đối 0 lượt.
Bằng chứng mạnh hơn là phép trừ của `TON-1911.md` §2: 23 issue đã đóng (508 lượt) + 792 lượt còn mở = **1 300**, trùng khít tổng bản 18/11 ⇒ trong cửa sổ 18/11 16:17Z → 19/11 07:40Z **không lượt nào được cộng**. Cửa sổ đó phủ trọn `v0.8.14` (17/11).
**[CHƯA KIỂM]** Không có `CRASH_ADMIN_TOKEN` trong phiên này nên không đọc được `app` (bản app của từng báo cáo), `frames`, `signal`, `fault_addr`, `BuildId` — không biết lượt cuối của #369/#591 đến từ bản nào.

## 4. #369 (71 lượt): đã chẩn đoán tới hàm, còn thiếu gì

Kết luận cũ, giữ nguyên:

1. Ứng viên số một **không phải mã trong repo**: `desmume_num_cores` > 1 (`config/monika-config.json:826` = `"2"`, `:834` (`full`) = `"4"`, `:831` (`lite`) = `"1"`) — DeSmuME dựng hình 3D đa luồng, nhiều máy sập khi > 1.
2. Ứng viên phía app **đã vá**: bộ đệm 0 byte khi lưu state, `memcpy` SRAM từ con trỏ rỗng, gọi JNI sau `destroy()` — #644 + #663.
3. `SET_MEMORY_MAPS` **đã loại trừ** (`environment.cpp` sao chép sâu descriptor + chuỗi `addrspace`).

Bảng phủ của #644/#663 trên `45e1c754` (đọc mã, không đo):

| Hàm | Dòng | Kiểm gì | PR |
|---|---|---|---|
| `unserializeState` | 159–169 | `!core`, `data == nullptr`, `size == 0` | #644 |
| `unserializeSRAM` | 171–196 | `!core`, dữ liệu rỗng, `retro_get_memory_data == nullptr`, `size > sramSize` | #663 |
| `serializeSRAM` | 198–220 | `!core`, `size == 0`, `sramState == nullptr` | #644 |
| `serializeState` | 727–749 | `!core`, `retro_serialize_size() == 0` | #644 |
| `setControllerType` / `reset` / `resetCheat` / `setCheat` | 148–157 / 715–725 / 751–761 / 763–774 | `!core` | #663 |
| `loadGameFromPath` / `FromBytes` / `FromVirtualFiles` | 372 / 416 / 448 | `!core` → ném `std::runtime_error` | #663 |
| `step` | 573–593 | `!core` | #663 |
| `destroy` | 498–526 | `if (core)` trước `retro_unload_game`/`retro_deinit` | #606 |
| `VFS::deinitialize` / `variableValueBuffer` | `vfs/vfs.cpp`, `environment.h:161` | khoá vector, `thread_local` | #663 |

Ba chỗ **chưa phủ** (§5). Đã quét toàn bộ 30 lời gọi `core->` của tệp: mọi hàm gọi được từ JNI đều đã kiểm `core`; chỗ duy nhất còn lại là `afterGameLoad()` (`:784-786`, gọi nội bộ ở `:410` **sau** `retro_load_game` thành công, cùng hàm nạp nên `core` chắc chắn khác rỗng) — không phải lỗi, không cần vá.

## 5. Phần chưa phủ, và vì sao chưa vá

### 5.1. Ghi tại chỗ vào bộ đệm của lõi khi pixel format `0RGB1555`

`renderers/es3/imagerendereres3.cpp:116-124` và `renderers/es2/imagerendereres2.cpp:105-113`: `const void *data` bị cast thành `uint16_t*` rồi **ghi đè ngay trên bộ đệm của lõi**. Chỉ chạy khi lõi đặt `SET_PIXEL_FORMAT = 0RGB1555` (`environment.cpp:268-272`).

- Tin cậy **thấp** cho `desmume` (chưa biết DeSmuME dùng `RGB565` hay `0RGB1555`); nếu là `RGB565` thì nhánh này không chạy và không thể là nguyên nhân.
- **Chưa vá** vì: (a) chưa có bằng chứng pixel format; (b) `libretrodroid/src/main/cpp/**` **không có khung test native** trong repo (chỉ có test JVM/Robolectric ở `app/src/test`), nên sửa đường dựng hình sẽ **không có test hồi quy** và không kiểm được bằng máy ảo.
- Cách đo trước khi vá: `adb logcat -s Libretro\ Core | grep SET_PIXEL_FORMAT` khi nạp ROM `.nds`. Thấy `0RGB1555` mới xét.
- Nếu đúng: sửa nhỏ — sao chép sang bộ đệm riêng (`std::vector<uint16_t>` giữ theo renderer) rồi đổi trên bản sao, không ghi vào `data` của lõi.

### 5.2. `dlclose` ngay sau `retro_deinit()`

`core.cpp:78-80`: `diag::mark("dlclose-before")` → `dlclose(libHandle)` → `mark("dlclose-done")`. DeSmuME tạo thread dựng hình khi `num_cores` > 1; nếu lõi chưa join thread của nó khi `retro_deinit()`, `dlclose()` munmap đoạn mã mà thread đó đang chạy ⇒ `SIGSEGV` trong `.so` của lõi, đúng hình dạng báo cáo `desmume / native`.

- Không làm lại phần đo: PR #684 (đã gộp) vừa thêm đúng hai mốc `dlclose-before`/`dlclose-done` + danh sách thread; chờ báo cáo mới đọc `crumbs` quanh mốc đó.
- **Chưa vá** vì không join được thread của lõi; phương án duy nhất là **không `dlclose`** (giữ handle tới khi tiến trình chết) — đổi hành vi toàn bộ lõi và rò một handle mỗi lần đổi game, phải PM duyệt.

### 5.3. Cấu hình và ghim lõi (không phải mã)

- `desmume_num_cores` `2` → `1` (mức thường, `config:826`), `4` → `2` (`:834`), giữ `lite` = `1` (`:831`).
- `desmume.version` vẫn `"1"` trỏ `nightly/latest`, **không** `sha256` (`config:821-835`) ⇒ lõi trôi: cùng một chữ ký có thể đến từ nhiều bản `.so`. Muốn ghép khung với bản lõi phải ghim trước.
- `config/monika-config.json` là tệp khoá trong lượt này ⇒ **chỉ đề xuất**, cần PM duyệt + tăng `configVersion` + đồng bộ Cloudflare.

## 6. Câu comment đóng soạn sẵn (chưa đăng, chờ PM duyệt luật đóng issue tồn)

Chỉ dùng khi **cả hai** điều kiện đã đúng: 0.8.15 đã **chính thức**, và đã qua ≥ 7 ngày không có lượt mới.

> Bản vá đã phát hành: #644 (`serializeState`/`unserializeState`/`serializeSRAM`, trong v0.8.14) và #663 (`libretrodroid`: 7 chỗ gọi lõi khi `core` rỗng, khoá VFS, `variableValueBuffer` → `thread_local`, trong v0.8.15). Chữ ký `<sig>` không có lượt mới từ `<ngày>` (N lượt, M máy). Đóng `completed` theo luật đóng issue tồn (≤3 lượt, >7 ngày không tái diễn). **Mở lại nếu tái diễn trên bản ≥ 0.8.15**, kèm bản app trong báo cáo.

Ghi chú riêng cho #562 (1 lượt, 1 máy): cùng câu trên; nếu mở lại mà vẫn 1 lượt/1 máy thì đó là ca của một thiết bị, không phải hồi quy.

## 7. #591 và #368

- **#591** (`3ad0c6cc6d47`, 19 lượt / 2 khoá `seen`): nằm trong **cùng nhóm chữ ký** với #369 và **trong phạm vi #663** — không có đường mã riêng. 19 lượt dồn 2 máy nghĩa là một người chơi lặp lại sau khi cập nhật; đây là ca tốt nhất để A/B `num_cores` (§5.3) vì biết đúng thiết bị.
- **#368** (`751363ed54f2`, `op-fail`, 1 lượt): **ngoài phạm vi #663** — `op-fail` do `Diagnostics.op` phía Kotlin ghi, không đi qua `libretrodroid.cpp`. `docs/loi/op-fail-con-lai-1311.md` §2 xếp #368 vào nhóm "báo cáo phát sinh trong phiên game", ứng viên `mo-game`/`cai-goi-engine` **[CHƯA KIỂM]**. Lõi `desmume` xuất hiện trong báo cáo nghĩa là **đã có phiên chơi**, nên không phải lỗi tải/cài engine.
  Cần để chẩn đoán: trường `op` + `bước` của báo cáo (`scripts/crash-reports.sh <id>` khi có token). Nếu `op = cai-goi-engine` thì #671 (kiểm ELF/ABI ở nhánh tải) là bản vá tương ứng, và chỉ đóng được **sau khi #671 gộp + phát hành**.

## 8. Việc cần PM quyết

1. **A/B `desmume_num_cores`** (§5.3) trên máy của #591: `2` so với `1`, cùng ROM ≥ 30 phút, đếm số lần chết tiến trình. Rẻ nhất, không cần sửa mã, và là ứng viên số một cho 97 lượt.
2. **Ghim lõi `desmume`** (bỏ `nightly/latest`): nếu không ghim thì mọi bảng khung sau này vẫn không đối chiếu được với một `.so` cụ thể.
3. **Luật đóng issue tồn** (≤3 lượt, >7 ngày): duyệt thì mới đăng câu ở §6 cho #331/#377/#562.
4. **Không `dlclose`** (§5.2): chấp nhận rò handle để bịt đường sập do thread của lõi hay không.

## 9. Cách kiểm tài liệu này

- Docs-only: theo `CLAUDE.md`, bỏ `testDebugUnitTest` (CI cũng bỏ qua `docs/**`).
- Mọi `tệp:dòng` đọc từ `origin/main` `45e1c754`; mọi kết luận mốc phát hành kiểm bằng `git merge-base --is-ancestor` (§2), không suy từ tên tag.
- Số lượt đọc từ khối `<!-- aow-crash-v100:… -->` trong thân 6 issue, đối chiếu `TON-1911.md` (98 lượt) và `TON-1811.md` (97 lượt native).
