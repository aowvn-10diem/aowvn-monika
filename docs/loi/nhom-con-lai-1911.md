# Nhóm con còn lại — 20 issue (19/11/2026)

Phạm vi: `unknown/anr` (7), `unknown/native` (1), lõi lẻ `mgba`/`gambatte`/`snes9x`/`pcsx_rearmed`/`mkxp-z`/`kirikiroid2-yuri`/`melonds` (12).
Tổng **110 lượt**. Không issue nào trong nhóm được tạo sau 12/11 (issue crash mới nhất toàn repo: #613).
Lượt của cả 20 issue **không tăng** so với `TON-1811.md` (18/11) và `TONG-HOP-1311.md` (13/11).

## 0. Mốc phát hành dùng để đóng issue

| Bản | Loại | Phát hành (UTC) | Tag | Ghi chú |
|---|---|---|---|---|
| v0.8.13 | chính thức | 13/11 08:46 | `v0.8.13` | |
| v0.8.14 | chính thức | 17/11 00:49 | `v0.8.14` (`7b4d0e26`) | |
| v0.8.15 | **thử** | 19/11 04:48 | `v0.8.15` (`8466ed7c`, cắt 11:13 +0700) | chính thức dự kiến 12:40 VN 20/11 |

**Phát hiện chặn:** `#683` (ANR có ngữ cảnh tiến trình) gộp lúc **17:14 +0700 19/11**, sau mốc cắt tag `v0.8.15` (11:13 +0700 cùng ngày).
`git merge-base --is-ancestor 45e1c754 v0.8.15` → **không** là tổ tiên; `git tag --contains 45e1c754` → **rỗng**.
⇒ Nếu 0.8.15 lên chính thức từ tag hiện tại thì **bản vá ANR không có trong 0.8.15**; phải cắt lại tag hoặc chờ bản sau.

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
| #387 | unknown / native | 1 | `ecdccd33` (lõi từ phiên chết) | **Không** (chỉ `v0.8.15` thử) | Giữ mở, chờ 0.8.15 chính thức |
| #322 | pcsx_rearmed / core-error | 37 | `#668` (PS1 hai tệp rời) + `#649` (báo thiếu tệp đĩa) | `#649` ở 0.8.14; `#668` ở **0.8.15 thử** | Giữ mở, chờ 0.8.15 chính thức |
| #578 | pcsx_rearmed / core-error | 18 | như #322 | như trên | Giữ mở, chờ |
| #403 | mgba / core-error | 4 | `#686` (mã lỗi/lõi/hệ) | **Chưa gộp** (PR mở) | Giữ mở, chờ #686 |
| #418 | snes9x / core-error | 1 | `#686` | **Chưa gộp** | Giữ mở, chờ #686 |
| #448 | snes9x / native | 2 | không (thiếu gói ký hiệu, lõi `version=1`) | — | Giữ mở |
| #563 | melonds / native | 3 | `#644` (chặn con trỏ rỗng khi lưu SRAM, 0.8.14) là **ứng viên**, chưa chứng minh khớp chữ ký | Có ở 0.8.14 | Giữ mở, chờ thêm dữ liệu |
| #385 | kirikiroid2-yuri / native | 13 | không (cần giải mã khung) | — | Giữ mở, bế tắc token |
| #613 | mkxp-z / native | 1 | không (chưa có gói ký hiệu RGSS) | — | Giữ mở |
| #464 | mgba / op-fail | 1 | `#665` (bớt báo sai op-fail) | **0.8.15 thử** | Giữ mở, chờ 0.8.15 chính thức |
| #465 | gambatte / op-fail | 1 | `#665` | **0.8.15 thử** | Giữ mở, chờ |
| #513 | gambatte / unknown | 2 | không (`unknown` = máy API < 30) | — | Giữ mở |
| #378 | mgba / unknown | 1 | không (như trên) | — | Giữ mở |

**Đóng hôm nay: 0.** Không issue nào trong nhóm thoả A102 (đã vá ở **bản chính thức** 0.8.13/0.8.14 **và** lượt không tăng): các bản vá khớp chữ ký đều đang ở `main` hoặc ở 0.8.15 **thử**.

## 2. `unknown/anr` — 7 issue, 26 lượt

- Chữ ký khác nhau hoàn toàn (7 mã băm) ⇒ **không phải issue trùng**; dùng chung một nguyên nhân *không chẩn đoán được*, không dùng chung một lỗi.
- `unknown` sinh ở `Diagnostics.collect` khi `exitInfo == null` (máy API < 30) hoặc khi tiến trình chết không có phiên (`Diagnostics.kt`, nhánh `null -> "unknown"`).
- `#683` gộp 19/11 thêm `Diagnostics.startProcess` + `writeDeath(... PROCESS_KIND = "app")` cho tiến trình chính ⇒ ANR ở tiến trình chính **sau bản có #683** sẽ mang `core=app`, không còn `unknown`. Đây là bản vá đúng cho cả 7 issue.
- Điều kiện đóng (A102): bản **chính thức** đầu tiên chứa `#683` + không tăng lượt. Hiện `#683` chưa vào tag nào.

## 3. Lõi lẻ

- `pcsx_rearmed` (#322 37 + #578 18): hai chữ ký khác nhau, cùng lõi/loại. `#649` (0.8.14) chỉ đổi **thông điệp** khi thiếu tệp đĩa/BIOS; `#668` (0.8.15) sửa **nhập game PS1 hai tệp rời** — đúng nghi phạm "bộ đĩa nhiều tệp" của `loi-khac-1313.md` §7.1. Chờ 0.8.15 chính thức rồi đo lại.
- `mgba`/`gambatte` `unknown` (#378, #513): máy API < 30 không có `ApplicationExitInfo` ⇒ không có dữ liệu để chẩn đoán, không phải lỗi mới.
- `#464`/`#465` (`op-fail`, 1 lượt): lỗi tải/cài gói lõi, không phải lõi hỏng; `#665` (0.8.15) giảm báo sai lớp này.
- `#448` (`snes9x`), `#563` (`melonds`), `#613` (`mkxp-z`): lõi `version=1` (buildbot nightly) và **không** có gói ký hiệu ⇒ không giải mã được khung. `#563` có ứng viên `#644` (0.8.14) nhưng chưa có bằng chứng khớp chữ ký ⇒ không đóng.
- `#385` (`kirikiroid2-yuri`, 13 lượt): lõi đã ghim và **có** `symbols-kirikiroid-16-arm64-v8a.zip`; chỉ thiếu `CRASH_ADMIN_TOKEN` để giải mã. Đây là việc rẻ nhất còn lại của cả nhóm.

## 4. Việc cần PM

1. **Cắt lại `v0.8.15`** (hoặc chấp nhận 0.8.16) để `#683` vào bản chính thức — nếu không, cả 7 issue `unknown/anr` phải chờ thêm một bản.
2. Gộp `#686` (core-error đọc được mã lỗi/lõi/hệ) — điều kiện để đóng #403/#418 và tách mã cho #322/#578.
3. Cấp `CRASH_ADMIN_TOKEN` cho một phiên để giải mã #385 (đã đủ lõi ghim + ký hiệu).
