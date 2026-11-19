# Nhóm `ppsspp` / `native` — chốt 19/11: số lượt đứng từ 06/11, #663 chỉ có trong 0.8.15 Pre-release

Tiếp theo `docs/loi/ppsspp-native-1311.md` (13/11) và `docs/loi/native-cpp-hien-truong-1811.md` (#663). Tệp này trả lời ba câu: **số lượt còn tăng không**, **#316/#372 có cùng gốc với #663 không**, và **#663 còn hở đường nào**. Kèm câu comment đóng sẵn cho ba issue lẻ (chưa đóng — chờ PM duyệt luật đóng).

Người lập: phiên **LB2** (nhóm Lỗi B), 19/11/2026. Mọi kết luận đọc từ **thân issue + git + config**; **không** có khung native (mục 4).

## 1. Số lượt và ngày dừng

| Issue | Chữ ký (12 hex) | Loại | Lượt | Khoá `seen` | Lượt lớn nhất / một khoá | Báo cáo đầu | Báo cáo mới nhất | Im lặng (19/11) |
|---|---|---|---:|---:|---:|---|---|---:|
| [#316](https://github.com/aowvn-10diem/aowvn-monika/issues/316) | `3f181942717a` | native | 114 | 100 | 6 | 20/10 07:43 | 20/10 | **30 ngày** |
| [#372](https://github.com/aowvn-10diem/aowvn-monika/issues/372) | `7af5e6657638` | native | 90 | 12 | 13 | 27/10 06:24 | 27/10 | **23 ngày** |
| [#478](https://github.com/aowvn-10diem/aowvn-monika/issues/478) | `4d2cb114a7aa` | native | 1 | 1 | 1 | 03/11 15:18 | 03/11 | **16 ngày** |
| [#520](https://github.com/aowvn-10diem/aowvn-monika/issues/520) | `4e65d6ad0246` | native | 1 | 1 | 1 | 06/11 03:29 | 06/11 | **13 ngày** |
| **native, cộng** | | | **206** | | | | | |
| [#416](https://github.com/aowvn-10diem/aowvn-monika/issues/416) | `4b0d4a9a9b0f` | unknown | 8 | 3 | 5 | 01/11 14:21 | 03/11 | **16 ngày** |

Cột "lượt" là `count` trong khối `aow-crash-v100` của thân issue; ở cả 5 issue **`count` bằng đúng tổng các khoá `seen`** ⇒ chưa từng cộng thêm lượt nào sau lần tạo (riêng #416 được cộng một lần `+5` ngày 03/11). #372 có **12 khoá nhưng 90 lượt** (một khoá 13, một khoá 12…): 12 lần cài/báo cáo, mỗi lần gộp nhiều lần chết cùng dấu vân tay — **không phải 90 thiết bị**.

## 2. "Số đứng" là tín hiệu thật, nhưng có hai giới hạn

**Tín hiệu thật:** bot `scripts/crash-issue-bot.py` chạy mỗi giờ trên `main`, và cùng ngày 19/11 các chữ ký khác vẫn được cập nhật: #322 `pcsx_rearmed/core-error` và #302 `unknown/resource` (tạo 20/10) cập nhật 19/11 06:48, #578 cập nhật 19/11 06:48. Worker và bot còn sống ⇒ 5 chữ ký `ppsspp` đứng số vì **không còn báo cáo mới**, không phải mất đường gửi.

**Giới hạn 1 — ngưỡng bot:** `scripts/crash-issue-bot.py:217` bỏ qua khi `delta < 5` (`elif delta < 5 and not rewrite: continue`). "Số không tăng" nghĩa là **dưới 5 lượt mới kể từ lần thông báo trước**, không chắc là 0. Với #316 (114) và #372 (90) mức nhiễu này không đổi kết luận; với #478/#520 (1 lượt) thì đó là giới hạn thật.

**Giới hạn 2 — mốc dừng nằm TRƯỚC bản vá:** cả 5 issue ngừng nhận báo cáo **trước** 0.8.13 (13/11) và trước 0.8.15 (19/11). Vì vậy "lượt không tăng sau bản vá" **không** chứng minh bản vá có tác dụng — đúng ra là báo cáo đã hết từ trước. Không được dùng vế này để đóng issue.

## 3. Mốc thời gian khớp: lõi PSP được ghim + băm ngày 05/11

`git show 87366517` (05/11/2026 15:58 UTC, `configVersion` 60 → 62):

| | Trước 05/11 | Sau 05/11 |
|---|---|---|
| Nguồn lõi ppsspp | `https://buildbot.libretro.com/nightly/android/latest/{abi}/ppsspp_libretro_android.so.zip` | `.../releases/download/cores-ppsspp-1/ppsspp-{abi}.zip` |
| Kiểm toàn vẹn | **không có hash** — tệp đổi mỗi đêm | `sha256` từng ABI (`336f6bfe…` arm64-v8a, `194c8b3c…` armeabi-v7a) |
| Đối chiếu gói ký hiệu | không thể | `cores-ppsspp-1` có `symbols-ppsspp-{abi}.zip` + `build-ids.tsv` |

206/206 lượt `native` nằm trong 20/10 → 06/11 03:29; lượt cuối (#520) cách mốc ghim **11,5 giờ** (máy chưa kịp nhận config 62); sau đó 13 ngày không có lượt nào. Giả thuyết khớp nhất — **chưa có khung để chứng minh từng chữ ký**: lõi lấy từ URL nightly không kiểm hash thì máy có thể cài đúng bản nightly hỏng/sai ABI, và báo cáo `native` khi đó không thể đối chiếu gói ký hiệu (không có BuildId). Đây là cùng một gốc với việc #671 đang mở rộng `validateElf`/`build_id` cho các lõi buildbot còn lại (`desmume`, `snes9x`, `melonds`, `melondsds`) — ppsspp đã được ghim trước.

Ngoài ra 11/11 `a0387ae4` (#601, trong **0.8.13** chính thức) hạ cấu hình đồ hoạ: `options.ppsspp_vertex_cache = disabled` (mọi máy), `perf.lite` thêm `ppsspp_auto_frameskip = enabled` + `ppsspp_internal_resolution = 480x272`, `perf.full` giữ `960x544` — đúng như `config/monika-config.json` hiện tại (`:958-980`, `configVersion` 66).

## 4. #316/#372 có cùng gốc với #663 không — chưa chứng minh

#663 (gộp 19/11, **chỉ có trong 0.8.15 Pre-release**; bản chính thức sớm nhất 20/11) bịt:

- 7 lời gọi JNI deref `core` mà không kiểm (`unserializeSRAM`, `loadGameFromPath/Bytes/VirtualFiles`, `step`, `reset`, `resetCheat`/`setCheat`) — `libretrodroid.cpp:173-178`, `:369-375`, `:415-419`, `:446-451`, `:566-571`, `:708-713`, `:744-760`.
- Race `VFS::virtualFiles` (`virtualFilesLock`, `vfs/vfs.h:78-79`, `vfs/vfs.cpp:114-129`).
- Race `variableValueBuffer` → `static thread_local` (`environment.h`, `environment.cpp:34-36`).

Ba đường đó **có thể** là đường chết của `ppsspp/native` (báo cáo tới sau khi rời game / đổi game), nhưng **không** có khung nào để nối chữ ký `3f181942717a` hay `7af5e6657638` với một dòng cụ thể. Kết luận trung thực: **cùng nhóm, chưa cùng gốc**; #663 là bịt các đường *có thể*, không phải chứng minh nguyên nhân.

## 5. #663 còn hở gì (đã đọc mã, không sửa)

| Vùng | Trạng thái sau #663 / #684 | Ghi chú |
|---|---|---|
| `LibretroDroid::destroy()` → `core = nullptr` → `Core::~Core()` → `dlclose` (`libretrodroid.cpp:498-536`, `core.cpp:69-78`) | **còn hở** | `dlclose` chạy **ngay** sau `retro_deinit()`; `Environment::deinitialize()` / `VFS::deinitialize()` chạy **sau** đó (`:534-535`). Lõi còn thread nền (ppsspp: `Emu`/`GPU`/`Audio`) thì thread chạy mã đã unmap → SIGSEGV trong `.so`. Đây là nghi phạm số một còn lại, và **chỉ đo được** bằng vệt #684 (`dlclose-before` + tên thread). Chưa vá: mọi cách vá (giữ handle, join, sleep) đều đổi hành vi bộ nhớ/thời gian mà chưa có bằng chứng. |
| Dựng lại surface/EGL (`GLRetroView.kt:412-419` → `libretrodroid.cpp:231-279`) | **đã vá ở 0.8.13** (`25ca0688`): huỷ HW context cũ trước khi dựng `Video` mới, không nạp lại game | Đường "khoá/mở máy, đổi độ phân giải" — ứng viên cũ cho #316/#372/#520 — đã được phủ. |
| `GLRetroView.kt:391` (`onDrawFrame` → `lifecycle?.coroutineScope?.launch { emit(FrameRendered) }` mỗi khung) | **không liên quan crash native** | `lifecycle = null` ở `onDestroy()` (`:155`) nên sau destroy không còn emit; đây là cấp phát mỗi khung (rác GC), không phải đường SIGSEGV. Không sửa trong lượt này. |
| VFS: handle đã cấp (`retro_vfs_file_handle*`) sau `deinitialize` | **đủ** | #663 khoá vector + giữ khoá suốt `virtualOpen`; handle do lõi giữ, `deinitialize` không free handle nên `read`/`close` sau đó không đọc bộ nhớ đã giải phóng. |
| Thread sinh **sau** `dlopen-done` | **chỉ đo được** | #684 ghi `thr=` + tên thread ở 6 mốc, đánh dấu `*` cho thread sinh sau `dlopen-done`. Cần báo cáo mới rơi vào cửa sổ unload mới trả lời được. |

⇒ **Không có phần nào sửa được ở app trong lượt này mà không vá mù.** Việc đúng là chờ dữ liệu #684 (0.8.15+) rồi mới quyết định: giữ handle (không `dlclose`) hay mở issue upstream `hrydgard/ppsspp` (commit ghim `9366aa7dc2d2fb1f49cde97ffbf5ab434d4d6ff9`).

## 6. Chốt chặn dữ liệu (vì sao vẫn chưa có khung)

- Thân issue `crash-moi` chỉ có **băm chữ ký** (`lõi + loại + tối đa 3 frame đầu`), không chép stack/BuildId/bản app.
- Phiên này **không có** `CRASH_ADMIN_TOKEN`.
- Đường thay thế đã thử: `gh workflow run crash-insight.yml` (workflow đọc Worker, xuất bảng có cột **bản app / chứng thư / đời máy**) → **403 Resource not accessible by personal access token**; artifact lần chạy 08/11 đã hết hạn lưu 3 ngày.
- Từ 0.8.15+, báo cáo mới **sẽ** có `bid=` nhờ #684, và #671 ghi `build_id` vào `info.txt` khi cài lõi ⇒ lần tái diễn đầu tiên là ghép được gói ký hiệu.

## 7. Điều kiện đóng / mở lại (đề xuất, chờ PM)

- **Đóng `completed`** chỉ khi: có bản **chính thức** chứa #663 (≥ 0.8.16 hoặc 0.8.15 được thăng cấp) **và** ít nhất 14 ngày sau đó số lượt không tăng **và** không có báo cáo cùng chữ ký trên bản đó. Không đóng hôm nay: 0.8.15 còn là Pre-release, mốc dừng 06/11 nằm trước bản vá nên chưa nói được gì.
- **Mở lại / tạo issue mới** khi có báo cáo `ppsspp/native` (hoặc `unknown`) trên 0.8.13+; kèm mã bản app, `bid=`, 3 frame đầu, và tên thread ở `dlclose-before` nếu có.
- **Ba issue lẻ** (#478 1 lượt, #520 1 lượt, #416 8 lượt — đều > 7 ngày): chờ PM duyệt luật "đóng issue tồn ≤ 3 lượt và > 7 ngày". Câu comment soạn sẵn ở mục 8.

## 8. Câu comment đóng sẵn cho #478 / #520 / #416 (CHƯA đăng, chờ PM)

Dùng chung khung; chỉ thay số liệu từng issue:

> **[Lỗi B — đề xuất đóng, chờ PM duyệt]**
>
> Chữ ký `<sig>` (ppsspp `/` `<kind>`), **<N> lượt**, báo cáo mới nhất **<ngày>** — im lặng **<X> ngày** tính đến 19/11, trong khi các chữ ký khác vẫn được bot cập nhật hằng ngày (#322, #302 ngày 19/11) nên đây là hết báo cáo thật, không phải mất đường gửi.
>
> Chưa kết luận được nguyên nhân: báo cáo `native` không mang khung/BuildId/bản app, và phiên này không có `CRASH_ADMIN_TOKEN` (chạy `crash-insight.yml` bị 403). Vì vậy đề xuất đóng theo **luật tồn ≤ 3 lượt / > 7 ngày**, không phải theo kết luận nguyên nhân.
>
> Mở lại nếu có báo cáo cùng chữ ký trên **0.8.13 trở lên**, kèm mã bản app + `bid=` + 3 frame đầu. Theo dõi nhóm tại `docs/loi/ppsspp-native-1911.md`.

Riêng **#416** (`unknown`, 8 lượt) thêm một câu: báo cáo `unknown` là lần chết không có tombstone/`ApplicationExitInfo` đọc được, nên càng không thể quy nguyên nhân; nếu tái diễn, ưu tiên xem `Diagnostics` vệt sự kiện của phiên đó trước.

## 9. Việc cần PM quyết

1. **Quyền đọc dữ liệu**: cho phép chạy `crash-insight.yml` (Actions write) **hoặc** cấp `CRASH_ADMIN_TOKEN` cho một phiên chẩn đoán. Không có một trong hai thì mọi kết luận về nhóm này mãi chỉ là "số lượt đứng".
2. **Ngưỡng bot**: `delta < 5` (`scripts/crash-issue-bot.py:217`) làm câu "lượt không tăng" không đo chính xác. Đề xuất hạ còn `1`, **hoặc** thêm dòng "báo cáo mới nhất: <ngày>" vào thân issue. Đổi hành vi bot nên cần PM duyệt — chưa làm.
3. **Luật đóng issue tồn**: duyệt để đóng #478/#520/#416 theo mục 8 (comment đã soạn, chưa đăng).
