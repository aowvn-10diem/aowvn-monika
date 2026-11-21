# Nhóm `ppsspp` / `native` — chốt 19/11: chưa đủ căn cứ đóng, #663 chỉ có trong 0.8.15 Pre-release

Tiếp theo `docs/loi/ppsspp-native-1311.md` (13/11) và `docs/loi/native-cpp-hien-truong-1811.md` (#663). Tệp này trả lời ba câu: **số lượt còn tăng không**, **#316/#372 có cùng gốc với #663 không**, và **#663 còn hở đường nào**. Kèm câu comment đóng sẵn cho ba issue lẻ (chưa đóng — chờ PM duyệt luật đóng).

Người lập: phiên **LB2** (nhóm Lỗi B), 19/11/2026. Mọi kết luận đọc từ **thân issue + git + config**; **không** có khung native (mục 4).

**Mốc toạ độ:** mọi `tệp:dòng` trong tệp này tính theo **`02bd03cc`**. Đã đối chiếu lại từng tham chiếu với `origin/main` **`3d8e1bcf`** (21/11): **9 tệp được dẫn không đổi một dòng nào** — #693 chỉ chạm `RetroActivity.kt` và `CoreLoadError.kt`, hai tệp này **không** được dẫn ở đây. Chỗ nào lấy theo mốc `61413abd` (bản #663 gộp) thì ghi rõ ngay cạnh.

## 1. Số lượt và lần thông báo cuối

| Issue | Chữ ký (12 hex) | Loại | Lượt | Khoá `seen` | Lượt lớn nhất / một khoá | Issue tạo (thông báo đầu) | Delta lần thông báo cuối | Ngày báo cáo cuối |
|---|---|---|---:|---:|---:|---|---|---|
| [#316](https://github.com/aowvn-10diem/aowvn-monika/issues/316) | `3f181942717a` | native | 114 | 51 | 6 | 20/10 07:43 | **+5** | **[CHƯA KIỂM]** |
| [#372](https://github.com/aowvn-10diem/aowvn-monika/issues/372) | `7af5e6657638` | native | 90 | 12 | 13 | 27/10 06:24 | +90 | **[CHƯA KIỂM]** |
| [#478](https://github.com/aowvn-10diem/aowvn-monika/issues/478) | `4d2cb114a7aa` | native | 1 | 1 | 1 | 03/11 15:18 | +1 | **[CHƯA KIỂM]** |
| [#520](https://github.com/aowvn-10diem/aowvn-monika/issues/520) | `4e65d6ad0246` | native | 1 | 1 | 1 | 06/11 03:29 | +1 | **[CHƯA KIỂM]** |
| **native, cộng** | | | **206** | | | | | |
| [#416](https://github.com/aowvn-10diem/aowvn-monika/issues/416) | `4b0d4a9a9b0f` | unknown | 8 | 3 | 5 | 01/11 14:21 | +5 | **[CHƯA KIỂM]** |

Cột "lượt" là `count` trong khối `aow-crash-v100` của thân issue. `(+N)` là **delta của lần thông báo cuối**, không phải mức tăng hiện tại và cũng không phải mốc thời gian: #316 ghi `114 (+5)` nghĩa là **đã có** một lần cộng 5 lượt sau lần tạo, chỉ không biết lần đó ngày nào. `count` luôn bằng tổng các khoá `seen` vì bot tính `delta` rồi gộp `seen` và cộng vào `count` (`scripts/crash-issue-bot.py:212`, `:219`, `:223`) — đó là **hệ quả cách ghi**, không suy ra được đã cộng hay chưa cộng. Ngày báo cáo cuối thật của cả 5 issue: **[CHƯA KIỂM — thiếu token Worker]**. #372 ghi `90 (+90)`: `+delta` chỉ bằng `count` ở **lần tạo** (bot khởi tạo `count` = 0, `delta` = tổng `seen` của lô đầu, `count = count + delta` — `scripts/crash-issue-bot.py:211-223`), nên **cả 90 lượt đến trong một lần thông báo ngày 27/10**, chia cho 12 dấu vân tay (13 + 12 + … + 2 = 90); số khoá `seen` **không** dùng để suy ra số lần hay số thiết bị.

## 2. "Số đứng" chỉ đo được ở mức đã thông báo — hai giới hạn

**Đối chứng bot còn sống:** bot `scripts/crash-issue-bot.py` chạy mỗi giờ trên `main`, và cùng ngày 19/11 các chữ ký khác vẫn được cập nhật: #322 `pcsx_rearmed/core-error` và #578 `pcsx_rearmed/core-error` cập nhật 19/11 **10:43**, #302 `unknown/resource` (tạo 20/10) cập nhật 19/11 06:48. Worker và bot còn sống. Nhưng với 5 chữ ký `ppsspp`, thứ đo được chỉ là **`count` không tăng ở mức đã thông báo** — chưa phân biệt được "hết báo cáo" với "có lượt mới nhưng dưới ngưỡng 5", và không biết lượt cuối là ngày nào.

**Giới hạn 1 — ngưỡng bot:** `scripts/crash-issue-bot.py:217` (theo `02bd03cc`) bỏ qua khi `delta < 5` (`elif delta < 5 and not rewrite: continue`). "Số không tăng" nghĩa là **dưới 5 lượt mới kể từ lần thông báo trước**, không chắc là 0. #316 đang ghi `(+5)` cho thấy đúng là có lần cộng sau lần tạo, nên đây không phải giới hạn lý thuyết; với #478/#520 (1 lượt) nó là giới hạn thật.

**Giới hạn 2 — không biết mốc dừng so với bản vá:** ngày báo cáo cuối của cả 5 issue là **[CHƯA KIỂM]** (mục 1), nên **không** khẳng định được báo cáo đã hết trước hay sau 0.8.13 (13/11) và 0.8.15 (19/11). Vì vậy không được dùng vế "lượt không tăng sau bản vá" để đóng issue.

## 3. Mốc thời gian khớp: lõi PSP được ghim + băm ngày 05/11

`git show 87366517` (05/11/2026 15:58 UTC, `configVersion` 60 → 62):

| | Trước 05/11 | Sau 05/11 |
|---|---|---|
| Nguồn lõi ppsspp | `https://buildbot.libretro.com/nightly/android/latest/{abi}/ppsspp_libretro_android.so.zip` | `.../releases/download/cores-ppsspp-1/ppsspp-{abi}.zip` |
| Kiểm toàn vẹn | **không có hash** — tệp đổi mỗi đêm | `sha256` từng ABI (`336f6bfe…` arm64-v8a, `194c8b3c…` armeabi-v7a) |
| Đối chiếu gói ký hiệu | không thể | `cores-ppsspp-1` có `symbols-ppsspp-{abi}.zip` + `build-ids.tsv` |

Các issue được tạo trong khoảng 20/10 → 06/11 (mục 1); **ngày báo cáo cuối là [CHƯA KIỂM — thiếu token Worker]**, nên không nói được lượt cuối cách mốc ghim bao lâu, cũng không nói được "sau đó N ngày không có lượt nào" (#316 đang ghi `+5` ở lần thông báo cuối). Giả thuyết khớp nhất — **chưa có khung để chứng minh từng chữ ký**: lõi lấy từ URL nightly không kiểm hash thì máy có thể cài đúng bản nightly hỏng/sai ABI, và báo cáo `native` khi đó không thể đối chiếu gói ký hiệu (không có BuildId). Đây là cùng một gốc với việc #671 đang mở rộng `validateElf`/`build_id` cho các lõi buildbot còn lại (`desmume`, `snes9x`, `melonds`, `melondsds`) — ppsspp đã được ghim trước.

Ngoài ra 11/11 `a0387ae4` (#601, trong **0.8.13** chính thức) hạ cấu hình đồ hoạ: `options.ppsspp_vertex_cache = disabled` (mọi máy), `perf.lite` thêm `ppsspp_auto_frameskip = enabled` + `ppsspp_internal_resolution = 480x272`, `perf.full` giữ `960x544` — đúng như `config/monika-config.json` hiện tại (`config/monika-config.json:958-980`, `configVersion` 66, theo `02bd03cc`).

## 4. #316/#372 có cùng gốc với #663 không — chưa chứng minh

#663 (gộp 19/11, **chỉ có trong 0.8.15 Pre-release**; bản chính thức sớm nhất 20/11) bịt:

> Mốc số dòng trong tệp này: **head `02bd03cc`**, đã đối chiếu lại vẫn đúng trên `3d8e1bcf` (`libretrodroid.cpp` không đổi trong khoảng này). Chỗ nào lấy theo mốc `61413abd` (bản #663 gộp) thì ghi rõ ngay cạnh, vì #684 đã làm dòng trôi.

- 8 khối kiểm `core` trước khi gọi JNI (7 chỗ, vì `resetCheat` và `setCheat` tách hai khối), theo head `libretrodroid.cpp`: `unserializeSRAM` `:175` (guard của hàm, thân hàm `:171`; **`:202` là guard của `serializeSRAM`** do #644 thêm, không phải hàm này), `loadGameFromPath/Bytes/VirtualFiles` `:372`/`:416`/`:448`, `step` `:577`, `reset` `:719`, `resetCheat`/`setCheat` `:755`/`:766`. Theo mốc `61413abd` (bản #663 gộp) lần lượt là `:173-178`, `:369-375`, `:415-419`, `:446-451`, `:566-571` (**mốc 5**, `step`), `:708-713` (**mốc 6**, `reset`), `:744-760`; trên head mốc 5–6 đã trôi tới `:577`/`:719`, còn toạ độ cũ `:566-571`/`:708-713` nay rơi vào cuối `pause()` và cuối `handleGetCurrentFrameBuffer`.
- Race `VFS::virtualFiles` (`virtualFilesLock` `vfs/vfs.h:79`, chú thích `:76-78`; `vfs/vfs.cpp:114-129`).
- Race `variableValueBuffer` → `static thread_local` (`environment.h:164`, `environment.cpp:34-36`).

Ba đường đó **có thể** là đường chết của `ppsspp/native` (báo cáo tới sau khi rời game / đổi game), nhưng **không** có khung nào để nối chữ ký `3f181942717a` hay `7af5e6657638` với một dòng cụ thể. Kết luận trung thực: **cùng nhóm, chưa cùng gốc**; #663 là bịt các đường *có thể*, không phải chứng minh nguyên nhân.

## 5. #663 còn hở gì (đã đọc mã, không sửa)

| Vùng | Trạng thái sau #663 / #684 | Ghi chú |
|---|---|---|
| `LibretroDroid::destroy()` → `core = nullptr` → `Core::~Core()` → `dlclose` (`libretrodroid.cpp:498-536`; `core.cpp:74-83` và `dlclose` `:79` theo head, mốc `61413abd` là `:69-78`) | **còn hở** | `dlclose` chạy **ngay** sau `retro_deinit()`; `Environment::deinitialize()` / `VFS::deinitialize()` chạy **sau** đó (`:534-535`). Lõi còn thread nền (ppsspp: `Emu`/`GPU`/`Audio`) thì thread chạy mã đã unmap → SIGSEGV trong `.so`. Đây là nghi phạm số một còn lại, và **chỉ đo được** bằng vệt #684 (`dlclose-before` + tên thread). Chưa vá: mọi cách vá (giữ handle, join, sleep) đều đổi hành vi bộ nhớ/thời gian mà chưa có bằng chứng. |
| Dựng lại surface/EGL (`GLRetroView.kt:412-419`, theo `02bd03cc` → `libretrodroid.cpp:231-279`) | **đã vá ở 0.8.13** (`25ca0688`): huỷ HW context cũ trước khi dựng `Video` mới, không nạp lại game | Đường "khoá/mở máy, đổi độ phân giải" — ứng viên cũ cho #316/#372/#520 — đã được phủ. |
| `GLRetroView.kt:391` (cùng mốc `02bd03cc`; `onDrawFrame` → `lifecycle?.coroutineScope?.launch { emit(FrameRendered) }` mỗi khung) | **không liên quan crash native** | `lifecycle = null` ở `onDestroy()` (`:155`) nên sau destroy không còn emit; đây là cấp phát mỗi khung (rác GC), không phải đường SIGSEGV. Không sửa trong lượt này. |
| VFS: handle đã cấp (`retro_vfs_file_handle*`) sau `deinitialize` | **đủ** | #663 khoá vector + giữ khoá suốt `virtualOpen`; handle do lõi giữ, `deinitialize` không free handle nên `read`/`close` sau đó không đọc bộ nhớ đã giải phóng. |
| Thread sinh **sau** `dlopen-done` | **chỉ đo được** | #684 ghi `thr=` + tên thread ở 6 mốc, đánh dấu `*` cho thread sinh sau `dlopen-done`. Cần báo cáo mới rơi vào cửa sổ unload mới trả lời được. |

⇒ **Không có phần nào sửa được ở app trong lượt này mà không vá mù.** Việc đúng là chờ dữ liệu #684 (**0.8.16 trở đi** — v0.8.15 cắt trước #684) rồi mới quyết định: giữ handle (không `dlclose`) hay mở issue upstream `hrydgard/ppsspp` (commit ghim `9366aa7dc2d2fb1f49cde97ffbf5ab434d4d6ff9`).

## 6. Chốt chặn dữ liệu (vì sao vẫn chưa có khung)

- Thân issue `crash-moi` chỉ có **băm chữ ký** (`lõi + loại + tối đa 3 frame đầu`), không chép stack/BuildId/bản app.
- Phiên này **không có** `CRASH_ADMIN_TOKEN`.
- Đường thay thế đã thử: `gh workflow run crash-insight.yml` (workflow đọc Worker, xuất bảng có cột **bản app / chứng thư / đời máy**) → **403 Resource not accessible by personal access token**; artifact lần chạy 08/11 đã hết hạn lưu 3 ngày.
- Từ **0.8.16 trở đi**, báo cáo mới **sẽ** có `bid=` nhờ #684, và #671 ghi `build_id` vào `info.txt` khi cài lõi ⇒ lần tái diễn đầu tiên là ghép được gói ký hiệu. (v0.8.15 `8466ed7c`, cắt 19/11 11:13 +07:00, gộp trước #684 17:14 +07:00 nên **không có** `diag.cpp` và `git grep dlclose-before v0.8.15` = 0 dòng.)

## 7. Điều kiện đóng / mở lại (đề xuất, chờ PM)

- **Đóng `completed`** chỉ khi: có bản **chính thức** chứa #663 (≥ 0.8.16 hoặc 0.8.15 được thăng cấp) **và** ít nhất 14 ngày sau đó số lượt không tăng **và** không có báo cáo cùng chữ ký trên bản đó. Không đóng hôm nay: 0.8.15 còn là Pre-release, và **ngày báo cáo cuối là [CHƯA KIỂM]** nên chưa nói được báo cáo đã hết trước hay sau bản vá.
- **Mở lại / tạo issue mới** khi có báo cáo `ppsspp/native` (hoặc `unknown`) trên 0.8.13+; kèm mã bản app, 3 frame đầu, và — **chỉ từ 0.8.16 trở đi** — `bid=` cùng tên thread ở `dlclose-before`.
- **Ba issue lẻ** (#478 1 lượt, #520 1 lượt, #416 8 lượt — đều > 7 ngày): chờ PM duyệt luật "đóng issue tồn ≤ 3 lượt và > 7 ngày". Câu comment soạn sẵn ở mục 8.

## 8. Câu comment đóng sẵn cho #478 / #520 / #416 (CHƯA đăng, chờ PM)

Dùng chung khung; chỉ thay số liệu từng issue:

> **[Lỗi B — đề xuất đóng, chờ PM duyệt]**
>
> Chữ ký `<sig>` (ppsspp `/` `<kind>`), **<N> lượt** đã thông báo, issue tạo **<ngày tạo>**. Số đã thông báo **không tăng ở mức ghi nhận**; bot chỉ cập nhật khi có ≥ 5 lượt mới và ngày báo cáo cuối là **[CHƯA KIỂM — thiếu token Worker]**, nên đề xuất đóng dựa trên luật tồn, không dựa trên kết luận "đã hết báo cáo".
>
> Chưa kết luận được nguyên nhân: báo cáo `native` không mang khung/BuildId/bản app, và phiên này không có `CRASH_ADMIN_TOKEN` (chạy `crash-insight.yml` bị 403). Vì vậy đề xuất đóng theo **luật tồn ≤ 3 lượt / > 7 ngày**, không phải theo kết luận nguyên nhân.
>
> Mở lại nếu có báo cáo cùng chữ ký trên **0.8.13 trở lên**, kèm mã bản app + 3 frame đầu (`bid=` chỉ có từ **0.8.16 trở đi**). Theo dõi nhóm tại `docs/loi/ppsspp-native-1911.md`.

Riêng **#416** (`unknown`, 8 lượt) thêm một câu: báo cáo `unknown` là lần chết không có tombstone/`ApplicationExitInfo` đọc được, nên càng không thể quy nguyên nhân; nếu tái diễn, ưu tiên xem `Diagnostics` vệt sự kiện của phiên đó trước.

## 9. Việc cần PM quyết

1. **Quyền đọc dữ liệu**: cho phép chạy `crash-insight.yml` (Actions write) **hoặc** cấp `CRASH_ADMIN_TOKEN` cho một phiên chẩn đoán. Không có một trong hai thì mọi kết luận về nhóm này mãi chỉ là "số lượt đứng".
2. **Ngưỡng bot**: `delta < 5` (`scripts/crash-issue-bot.py:217`) làm câu "lượt không tăng" không đo chính xác. Đề xuất hạ còn `1`, **hoặc** thêm dòng "báo cáo mới nhất: <ngày>" vào thân issue. Đổi hành vi bot nên cần PM duyệt — chưa làm.
3. **Luật đóng issue tồn**: duyệt để đóng #478/#520/#416 theo mục 8 (comment đã soạn, chưa đăng).
