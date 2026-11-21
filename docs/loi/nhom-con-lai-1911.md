# Nhóm con còn lại — 20 issue (19/11/2026)

Phạm vi: `unknown/anr` (7), `unknown/native` (1), lõi lẻ `mgba`/`gambatte`/`snes9x`/`pcsx_rearmed`/`mkxp-z`/`kirikiroid2-yuri`/`melonds` (12).
Tổng **111 lượt** (26 `unknown/anr` + 1 `unknown/native` + 84 lõi lẻ). Không issue nào trong nhóm được tạo sau 12/11 (issue crash mới nhất toàn repo: #613).
Lượt của cả 20 issue **không tăng** so với `TON-1811.md` (18/11) và `TONG-HOP-1311.md` (13/11).

## 0. Mốc phát hành dùng để đóng issue

| Bản | Loại | Phát hành (UTC) | Tag | Ghi chú |
|---|---|---|---|---|
| v0.8.13 | chính thức | 13/11 08:46 | `v0.8.13` | |
| v0.8.14 | chính thức | 17/11 00:49 | `v0.8.14` (`7b4d0e26`) | |
| v0.8.15 | chính thức | 19/11 04:48 | `v0.8.15` (`8466ed7c`, cắt 11:13 +0700) | nhãn chính thức đặt ngay trong ngày 19/11 (sau lần phát hành 04:48Z); tag **không** chứa `#683` |

**Phát hiện chặn:** `#683` (ANR có ngữ cảnh tiến trình) gộp lúc **17:14 +0700 19/11**, sau mốc cắt tag `v0.8.15` (11:13 +0700 cùng ngày).
`git merge-base --is-ancestor 45e1c754 v0.8.15` → **không** là tổ tiên; `git tag --contains 45e1c754` → **rỗng**.
⇒ 0.8.15 **đã lên chính thức ngày 19/11** từ đúng tag `8466ed7c`, mà tag đó **không** chứa `#683` ⇒ bản vá ANR không có trong 0.8.15 chính thức; phải chờ bản kế tiếp.

Tương tự, `ecdccd33` (gắn lõi cho `native`/`resource` từ phiên chết, 13/11) **chỉ có ở v0.8.15**, không có ở v0.8.14.

Phiên này **không có `CRASH_ADMIN_TOKEN`** ⇒ không đọc được `reason`/`frames`/`device`/`cert` của báo cáo gốc. Mọi kết luận dưới đây dựa trên thân issue + mã nguồn, đúng như các lượt trước.

## 1. Bảng quyết định

| Issue | Lõi / loại | Lượt | Bản vá đã có | Trong bản chính thức? | Quyết định hôm nay |
|---|---|---:|---|---|---|
| #413 | unknown / anr | 15 | `#683` (ngữ cảnh tiến trình) | **Không** (chỉ `main`) | Giữ mở, chờ bản có #683 |
| #534 | unknown / anr | 5 | `#683` | **Không** | Giữ mở, chờ |
| #399 | unknown / anr | 2 | `#683` | **Không** | Giữ mở, chờ |
| #398 | unknown / anr | 1 | `#683` | **Không** | Giữ mở, chờ |
| #414 | unknown / anr | 1 | `#683` | **Không** | Giữ mở, chờ |
| #415 | unknown / anr | 1 | `#683` | **Không** | Giữ mở, chờ |
| #419 | unknown / anr | 1 | `#683` | **Không** | Giữ mở, chờ |
| #387 | unknown / native | 1 | `ecdccd33` (lõi từ phiên chết) | **Có** (v0.8.15 chính thức) | Giữ mở — chờ lệnh trưởng nhóm + đo lượt sau bản vá |
| #322 | pcsx_rearmed / core-error | 37 | `#668` (PS1 hai tệp rời) + `#649` (báo thiếu tệp đĩa) | `#649` ở 0.8.14; `#668` ở **0.8.15 chính thức** | Giữ mở — chờ lệnh trưởng nhóm + đo lượt sau bản vá |
| #578 | pcsx_rearmed / core-error | 18 | như #322 | như trên | Giữ mở — cùng điều kiện #322 |
| #403 | mgba / core-error | 4 | `#686` (mã lỗi/lõi/hệ) | **Đã gộp** vào `main` 19/11 12:14Z (`fafd13fe`), **chưa vào tag nào** | Giữ mở, chờ 0.8.16 vì `#686` vào sau mốc cắt tag |
| #418 | snes9x / core-error | 1 | `#686` | **Đã gộp** vào `main` 19/11 12:14Z (`fafd13fe`), **chưa vào tag nào** | Giữ mở, chờ 0.8.16 vì `#686` vào sau mốc cắt tag |
| #448 | snes9x / native | 2 | không (thiếu gói ký hiệu, lõi `version=1`) | — | Giữ mở |
| #563 | melonds / native | 3 | `#644` (chặn con trỏ rỗng khi lưu SRAM, 0.8.14) là **ứng viên**, chưa chứng minh khớp chữ ký | Có ở 0.8.14 | Giữ mở, chờ thêm dữ liệu |
| #385 | kirikiroid2-yuri / native | 13 | không (cần giải mã khung) | — | Giữ mở, bế tắc token |
| #613 | mkxp-z / native | 1 | không (chưa có gói ký hiệu RGSS) | — | Giữ mở |
| #464 | mgba / op-fail | 1 | `#665` (bớt báo sai op-fail) | **0.8.15 chính thức** | Giữ mở — chờ lệnh trưởng nhóm |
| #465 | gambatte / op-fail | 1 | `#665` | **0.8.15 chính thức** | Giữ mở — cùng điều kiện #464 |
| #513 | gambatte / unknown | 2 | không (`unknown` = máy API < 30) | — | Giữ mở |
| #378 | mgba / unknown | 1 | không (như trên) | — | Giữ mở |

**Đóng hôm nay: 0.** Không issue nào trong nhóm thoả A102 *ở thời điểm lập bảng*: các bản vá khớp chữ ký khi đó đang ở `main` hoặc chỉ mới vào 0.8.15 (chính thức 19/11, xem §0), nên chưa có mốc đo lượt sau bản vá.

## 2. `unknown/anr` — 7 issue, 26 lượt

- Chữ ký khác nhau hoàn toàn (7 mã băm) ⇒ **không phải issue trùng**; dùng chung một nguyên nhân *không chẩn đoán được*, không dùng chung một lỗi.
- `unknown` sinh ở `Diagnostics.collect` (`Diagnostics.kt:306`) khi `exitInfo == null` (máy API < 30) hoặc khi tiến trình chết không có phiên (`Diagnostics.kt:365`, nhánh `null -> "unknown"`; tệp đã đổi **+41/−2** từ `02bd03cc` do `#686`, số dòng lấy theo `origin/main` `3d8e1bcf`).
- `#683` gộp 19/11 thêm `Diagnostics.startProcess` + `writeDeath(... PROCESS_KIND = "app")` cho tiến trình chính ⇒ ANR ở tiến trình chính **sau bản có #683** sẽ mang `core=app`, không còn `unknown`. Đây là bản vá đúng cho cả 7 issue.
- Điều kiện đóng (A102): bản **chính thức** đầu tiên chứa `#683` + không tăng lượt. Hiện `#683` chưa vào tag nào.

## 3. Lõi lẻ

- `pcsx_rearmed` (#322 37 + #578 18): hai chữ ký khác nhau, cùng lõi/loại. `#649` (0.8.14) chỉ đổi **thông điệp** khi thiếu tệp đĩa/BIOS; `#668` (0.8.15, chính thức 19/11) sửa **nhập game PS1 hai tệp rời** — đúng nghi phạm "bộ đĩa nhiều tệp" của `loi-khac-1313.md` §7.1. Đo lại sau khi có lệnh trưởng nhóm.
- `melondsds` / `core-error` (**#567** — không thuộc nhóm này, của Lỗi A): bản vá đã gộp ở `main` `3d8e1bcf` (PR #693, commit `d62bc382`, 20/11 23:13 +0700), **chưa vào tag nào**. Nội dung đáng đối chiếu: mã `1` của `melondsds` = tệp game không dùng được (lõi dựng màn hình lỗi riêng và trả `true` cho lỗi BIOS/NAND), nay app kiểm header `.nds` trước khi nạp lõi. **Không đổi kết luận #322/#578**: khác lõi, và `pcsx_rearmed` không có bước kiểm tệp tương đương.
- `mgba`/`gambatte` `unknown` (#378, #513): máy API < 30 không có `ApplicationExitInfo` ⇒ không có dữ liệu để chẩn đoán, không phải lỗi mới.
- `#464`/`#465` (`op-fail`, 1 lượt): lỗi tải/cài gói lõi, không phải lõi hỏng; `#665` (0.8.15) giảm báo sai lớp này.
- `#448` (`snes9x`), `#563` (`melonds`): lõi `version=1` (buildbot nightly) và **không** có gói ký hiệu ⇒ không giải mã được khung. `#563` có ứng viên `#644` (0.8.14) nhưng chưa có bằng chứng khớp chữ ký ⇒ không đóng.
- `#613` (`mkxp-z`): **không** thuộc nhóm lõi trôi bản của buildbot — engine **ghim** `engines-rgss-6` (`sha256`, `arm64-v8a`); cái thiếu là **ZIP ký hiệu** (`symbols-rgss-*` không tồn tại trong 54 release) ⇒ cùng bế tắc "không có ký hiệu" nhưng khác nguyên nhân. Đặc tả: `docs/loi/mkxp-z-thieu-ky-hieu-1911.md`.
- `#385` (`kirikiroid2-yuri`, 13 lượt): lõi đã ghim và **có** `symbols-kirikiroid-16-arm64-v8a.zip`; chỉ thiếu `CRASH_ADMIN_TOKEN` để giải mã. Đây là việc rẻ nhất còn lại của cả nhóm.

## 4. Việc cần PM

1. **Cắt bản kế tiếp (0.8.16) có `#683`** (tag `v0.8.15` đang phát không chứa bản vá) — nếu không, cả 7 issue `unknown/anr` phải chờ thêm một bản nữa.
2. Duyệt và đăng giúp comment soạn sẵn trong `docs/loi/comment-cho-duyet-1911.md` cho 20 issue — phiên không tự đăng.
3. Cấp `CRASH_ADMIN_TOKEN` cho một phiên để giải mã #385 (đã đủ lõi ghim + ký hiệu).

## 5. Mốc toạ độ (rà 21/11/2026)

Rà mọi tham chiếu tệp/dòng của 3 tệp `docs/loi/*1911.md` theo `origin/main` `3d8e1bcf`, so với mốc `02bd03cc`:

| Toạ độ | Trạng thái từ `02bd03cc` | Đã kiểm |
|---|---|---|
| `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:306` (`collect`) và `:365` (`null -> "unknown"`) | **Đổi** +41/−2 (`#686`, `fafd13fe`) ⇒ đã cập nhật số dòng | `collect` ở 306, nhánh `null -> "unknown"` ở 365 |
| `.github/workflows/build-rgss.yml` **dòng 174–200** | Không đổi (208 dòng) | 174 comment V24/G8 · 179 `package-native-symbols.py` · 181 `--output` · 196 `--draft` |
| `.github/workflows/build-kirikiri.yml` | Không đổi | 190/192 dựng ZIP · 217–218 `[ -s "$SYM" ]` bắt buộc ký hiệu |
| `libretrodroid/src/main/cpp/CMakeLists.txt` | Không đổi | `-Wl,--build-id=sha1` ở dòng 114 |
| `scripts/package-native-symbols.py` (workflow gọi qua `self/scripts/…` trong bản checkout engine) | Không đổi | `--pack`/`--source-root`/`--abi`/`--output`/`--readelf` còn đủ |
| `scripts/crash-reports.sh` | Không đổi | còn retrace theo mapping + ghép native theo BuildId |

`app/src/main/java/vn/aow/monika/runner/RetroActivity.kt` (+18) và `CoreLoadError.kt` (+14/−2) đổi từ `02bd03cc` do `#693`, nhưng **không** tệp nào trong nhóm này viện dẫn hai tệp đó ⇒ không có toạ độ phải sửa.
