# Tổng hợp tình trạng crash — chốt 13/11/2026

Ngày lập: 13/11/2026 · nhánh `chan-doan/tong-hop-1311` · nền `origin/main` `f75b2e6d`
Nguồn số: khối `<!-- aow-crash-v100:… -->` trong thân **74 issue**. Nhãn `crash-moi` đang mở **75** issue —
[#549](https://github.com/aowvn-10diem/aowvn-monika/issues/549) (issue theo dõi `lowmem`) **không** mang khối
`aow-crash-v100`, nên **74 là số issue có dữ liệu**, không phải đếm thiếu.

> **Số liệu 13/11, 74 issue có khối / 1 285 lượt / 390 khoá máy** (75 issue mang nhãn).
> Đếm trực tiếp từ khối `aow-crash-v100` của 74 issue — không lấy lại số của bảng cũ.

Tài liệu này **thay** `docs/loi/TON-0811.md` (PR **#542**, chốt 08/11, 99 issue, **14 291 lượt**). Số cũ
không còn so được với hiện tại: số issue giảm 99 → 74, cách gom nhóm đổi (theo **lõi + loại** thay vì nhóm
chữ A–H), và một phần lượt cũ thuộc các issue đã đóng.

**Chỉ tài liệu.** Không sửa mã, không đụng `config/`, không đóng issue nào.

---

## 1. Cách đọc số (đọc trước khi trích dẫn bất kỳ con số nào)

| Trường | Nghĩa | **Không** dùng để |
|---|---|---|
| `count` (cột **Lượt**) | **Cộng dồn lịch sử** của chữ ký, bot chỉ cộng thêm (`scripts/crash-issue-bot.py:124`), không cửa sổ thời gian, không reset | Suy ra **tỉ lệ crash hiện tại** hay "nhóm đang tăng" |
| `seen` (cột **Khoá máy**) | Số khoá **ẩn danh**, mỗi khoá ≈ một lần gửi báo cáo (`crash-issue-bot.py:76`), **không** phải số người, không phải model máy | Nói "N người dùng bị" |
| Phạm vi dữ liệu | Mỗi lần đọc Worker chỉ lấy tối đa **100 bản ghi mới nhất** | Nói "toàn bộ lịch sử" |

Hệ quả đã ghi ở `docs/loi/lowmem-1311.md` §2.1 (đã gộp, #621): issue **đã đóng** vẫn được bot PATCH và cộng
tiếp (`crash-issue-bot.py:187-220`), nên `count` của một chữ ký **chỉ có thể tăng**. Con số lớn **không**
đồng nghĩa nhóm còn tái diễn mạnh; muốn biết mức tăng phải đọc `(+N)` / `time` của báo cáo gốc, cần
`CRASH_ADMIN_TOKEN`.

Vì vậy tài liệu này **không** xếp hạng theo "mức nguy hiểm" và **không** suy ra nguyên nhân từ số.

---

## 2. Bảng theo lõi (74 issue có khối / 1 285 lượt / 390 khoá máy)

| Lõi | Issue | Lượt | Ghi chú |
|---|---:|---:|---|
| `melondsds` | 12 | 456 | lớn nhất; 421/456 là nhóm `anr` |
| `unknown` | 22 | 296 | 236/296 là **một** issue `resource` (#302) |
| `ppsspp` | 5 | 214 | 206/214 là `native` |
| `citra` | 9 | 121 | 57 là `core-error`, 32 là `native` |
| `desmume` | 10 | 105 | 97 là `native` |
| `pcsx_rearmed` | 2 | 55 | cả hai là `core-error` |
| `kirikiroid2-yuri` | 1 | 13 | `native`, lõi **đã ghim + có ký hiệu** |
| `snes9x` | 3 | 8 | ba loại trên cùng một lõi |
| `mgba` | 4 | 7 | lõi đã ghim |
| `gambatte` | 3 | 5 | lõi đã ghim |
| `melonds` | 2 | 4 | **không** phải lõi NDS mặc định |
| `mkxp-z` | 1 | 1 | lõi đã ghim, **không** có ký hiệu |

Đọc bảng: hai lõi `melondsds` + `unknown` chiếm **752/1 285 lượt (59%)**, nhưng phần lớn là **cộng dồn** của
hai chữ ký lớn (`melondsds/anr` 421 và `unknown/resource` 236). Sáu lõi dưới 15 lượt (`snes9x`, `mgba`,
`gambatte`, `melonds`, `mkxp-z`, `kirikiroid2-yuri` cộng lại 38 lượt) **không đủ mẫu** để kết luận nguyên nhân.

---

## 3. Mười nhóm lớn nhất và trạng thái

`Nhóm` = cặp **lõi / loại** (đúng cách bot băm chữ ký: `lõi + loại + ≤3 frame đầu`).

| # | Nhóm | Issue | Lượt | Trạng thái | Căn cứ |
|---:|---|---:|---:|---|---|
| 1 | `melondsds` / `anr` | 9 | 421 | **Chỉ có tài liệu** — mã đã bọc hạn mọi lời gọi JNI từ UI (#606, #619, đã gộp); **chưa đo** được chặng chờ GLThread | `docs/loi/melondsds-anr-1313.md` (#633 đã gộp) |
| 2 | `unknown` / `resource` | 1 | 236 | **Bị chặn** — #302 là tiến trình `:midlet` (game Java) **chưa từng có phiên**, nên không có lõi để ghi | `docs/loi/unknown-native-1311.md` §3.2(d), §4.4 (#622 đã gộp) |
| 3 | `ppsspp` / `native` | 4 | 206 | **Bị chặn ở dữ liệu** — lõi đã ghim + **có** gói ký hiệu, thiếu `CRASH_ADMIN_TOKEN` để giải mã khung | `docs/loi/ppsspp-native-1311.md` §2(d), §3 (#620 đã gộp) |
| 4 | `desmume` / `native` | 5 | 97 | **Chỉ có tài liệu** — gom 5 chữ ký, ứng viên theo `tệp:dòng` | `docs/ds/chan-doan-desmume-native.md` (#616 đã gộp; tệp nằm ngoài `docs/loi/`) |
| 5 | `citra` / `core-error` | 2 | 57 | **Đã sửa một phần** — #637 (đã gộp) chặn `.cia` vào lõi libretro; phần còn lại chỉ có tài liệu | `docs/loi/citra-con-lai-1313.md` (**chưa lên main**, PR #635 mở) + #637 |
| 6 | `pcsx_rearmed` / `core-error` | 2 | 55 | **Bị chặn** — thân issue **không mang mã lỗi** (`0/1/-1`), cần `reason`/`crumbs`; V198 (tự dựng lõi + ký hiệu) chưa triển khai | `docs/loi/loi-khac-1313.md` §3, §7.1 (#636 đã gộp) |
| 7 | `unknown` / `op-fail` | 13 | 33 | **Chỉ có tài liệu + một phần đã sửa** — #618 (đã gộp) chuyển lỗi chọn nhầm ROM/bản vá thành vệt; 14 issue **không có** dòng `Thành phần` nên chưa quy được op | `docs/loi/op-fail-1311.md`, `docs/loi/op-fail-con-lai-1311.md` (#618, #626 đã gộp) |
| 8 | `citra` / `native` | 2 | 32 | **Bị chặn** — lõi citra trỏ buildbot `nightly/latest`, `version = "1"`, **không** `sha256`, **không** symbols ⇒ không retrace, không tái hiện | `docs/loi/citra-native-1311.md` §3(1), §4 (#622 đã gộp) |
| 9 | `unknown` / `anr` | 7 | 26 | **Bị chặn** — `anr` không có `session.core`; sửa nhãn ở bot (#632) **chưa gộp** | `docs/loi/unknown-anr-1313.md` (**chưa lên main**, PR #634 mở) |
| 10 | `melondsds` / `core-error` | 1 | 21 | **Chỉ có tài liệu** — burst 21 lượt / 6 lần gửi trong cửa sổ 09/11, không tái diễn; chưa tách được mã `0/1/-1` | `docs/loi/loi-khac-1313.md` §3.2, §7.2 (#636 đã gộp) |

Mười nhóm này là **1 184/1 285 lượt (92%)**. 101 lượt còn lại nằm ở **22 nhóm đuôi**, và **không đều** —
đừng đọc là "toàn nhóm ≤ 3 lượt":

| Nhóm đuôi (11–14) | Issue | Lượt | Ghi chú |
|---|---:|---:|---|
| `citra` / `killed` | 1 | 18 | thuộc phạm vi #635 (**chưa** lên main) |
| `melondsds` / `unknown` | 2 | 14 | `unknown` không có `reason` (API < 30) — `loi-khac-1313.md` §5 |
| `citra` / `anr` | 3 | 13 | thuộc phạm vi #635 (**chưa** lên main) |
| `kirikiroid2-yuri` / `native` | 1 | 13 | lõi đã ghim **và có** ký hiệu ⇒ giải mã được ngay |

Bốn nhóm trên là **58/101 lượt**; rồi `ppsspp/unknown` 8, `desmume/anr` 7, `snes9x/anr` 5, `mgba/core-error` 4
(cộng 24 lượt). **14 nhóm cuối chỉ 1–3 lượt (19 lượt)** — nhóm này mới thật sự không đủ mẫu, không mở việc
riêng cho từng chữ ký.

Hệ quả cho việc lọc theo tài liệu: hai nhóm `citra/anr` + `citra/killed` (31 lượt) **chưa** được tài liệu nào
trên `main` phủ — chúng nằm trong `docs/loi/citra-con-lai-1313.md` (PR #635, đang mở). Đọc bảng §3 mà bỏ qua
hai nhóm này sẽ thấy `citra` nhẹ hơn thực tế.

Ghi chú về **cách đếm `citra`**: #627 (đã gộp) sửa `Components.kt` để dấu hiệu lõi libretro **thắng** dấu hiệu
Azahar, nên crash của `citra_libretro` không còn bị gắn nhãn `engine:azahar`. Số trong bảng này là **sau** #627;
báo cáo do bản app **trước** #627 gửi lên vẫn mang nhãn cũ, nên khi lọc Worker theo thành phần phải nhớ mốc này.

---

## 4. Đối chiếu việc đã gộp và việc còn mở

### 4.1 Đã gộp

| PR | Nội dung | Ảnh hưởng tới bảng trên |
|---|---|---|
| **#627** | `Components.kt`: nhãn `engine:libretro:citra` không bị `engine:azahar` ăn nhầm | Đổi **cách đếm** `citra`; số cũ trước mốc bị lẫn |
| **#637** | Chặn `.cia` ở lõi libretro 3DS (`GameLauncher.ciaBlockedReason`) | Nhắm nhóm 5 (`citra/core-error`); **chưa** có số sau bản phát hành |
| **#616** | Chẩn đoán `desmume/native` | Nhóm 4 |
| **#620** | Chẩn đoán `ppsspp/native` | Nhóm 3 |
| **#621** | Chẩn đoán `lowmem` + ghi rõ số trên issue đóng là cộng dồn | §1 (cách đọc số) |
| **#618**, **#626** | Chẩn đoán `op-fail` + `expected` cho `PatchFlow` | Nhóm 7 |
| **#622** | Chẩn đoán `citra/native`, `unknown/native` | Nhóm 8, 2 |
| **#633** | Chẩn đoán `melondsds/anr` (9 chữ ký còn lại) | Nhóm 1 |
| **#636** | Chẩn đoán "lõi khác" 8 lõi / 19 issue | Nhóm 6, 10 và các lõi nhỏ |
| **#574** | Nhả bộ đệm tiến trình chính khi ra nền / thiếu RAM | **Không** tính vào nhóm 1: chạy ở tiến trình chính, còn ANR `melondsds` mang thành phần `engine:libretro:melondsds` = tiến trình `:game` (`docs/loi/melondsds-anr-1313.md` §3) |
| **#606**, **#619** | Bọc hạn mọi lời gọi JNI phát từ UI Thread (`EmulationCall`) | Đã gộp vào main; là mốc để đo lại nhóm 1 |

### 4.2 Còn mở (đừng coi là đã sửa)

| PR | Nội dung | Vì sao quan trọng cho bảng này |
|---|---|---|
| **#632** | Bot lấy `core` **cấp ngoài** cho báo cáo `anr`/`lowmem` không có phiên | **Chưa gộp**: `scripts/crash-issue-bot.py` trên `main` vẫn là `core = token(session.get("core", ""))` ⇒ nhóm 9 vẫn mang nhãn `unknown` |
| **#634** | Chẩn đoán `unknown/anr` | Tài liệu nhóm 9 **chưa có trên main** |
| **#635** | Chẩn đoán `citra` còn lại (`core-error`/`anr`/`killed`/`unknown`) | Tài liệu nhóm 5 **chưa có trên main** |
| **#623** | Chẩn đoán `ppsspp/anr` (nhóm trống) | **Chưa có trên main** |
| **#624** | `stage = "paused"` + chẩn đoán `melondsds/killed` | **Chưa có trên main**; là mốc chờ của mảnh (b) ở §5 |
| **#638** | Gắn phiên `Diagnostics` cho tiến trình `:midlet` | Mảnh còn lại của #302 (§5b) |
| **#639** | Thông điệp chặn `.cia` đúng lối cài | Nối tiếp #637, chưa gộp |

### 4.3 Phân loại theo yêu cầu

- **Đã sửa (mã, đã gộp):** #627 (nhãn), #637 (chặn `.cia` ở launcher), #618 (nhiễu `va-rom`), #606/#619 (bọc
  hạn lời gọi JNI). Chưa nhóm nào có **số đo sau bản phát hành**.
- **Chỉ có tài liệu:** nhóm 1, 4, 6, 10 và phần lớn nhóm 3, 7 (tài liệu đã gộp nhưng chưa có bản sửa).
- **Bị chặn:** nhóm 2 (thiếu phiên J2ME), 3 (thiếu token), 8 (thiếu ghim bản + ký hiệu), 9 (thiếu #632),
  một phần 5 và 6 (thiếu mã lỗi trong báo cáo).

---

## 5. Còn chờ quyết định

### (a) ANR lúc mở/đóng game — **đo trước**, không sửa `step()`/`coreLock`

Nhóm 1 (421 lượt) **không** được sửa bằng cách thu hẹp `coreLock` hay bỏ `fpsSync->wait()` ra ngoài khoá:
`docs/loi/melondsds-anr-1313.md` §6 đã chỉ ra hướng đó **không** giảm thời gian chờ của Main Thread (nó chờ cả
`onDrawFrame`) mà mở **use-after-free** trên `fpsSync` (con trỏ bị `destroy()` giải phóng) ⇒ đổi một ANR hiếm
thành một SIGSEGV chắc chắn hơn.

Việc đúng theo thứ tự:

1. Chạy **phép đo crumb** ở `docs/loi/melondsds-anr-1313.md` **§5.2** — `t1→t2` (chờ GLThread), `t7→t8`
   (chờ ghi SRAM 1 500 ms), `t0→t1` (framework, chỉ API 29+).
2. **Trước khi đo phải có cầu nối chẩn đoán** `:libretrodroid` → app (`Diag.sink`) vì `t1/t2/t3` nằm trong
   `GLRetroView`, module không thấy lớp app. **Trên `main` hôm nay chưa có cầu nối này** (không có
   `libretrodroid/.../Diag.kt`) ⇒ hiện **chưa đo được** `t1→t2`; ghi `[CHƯA ĐO ĐƯỢC]`, không trình bày như
   đã có số.
3. Chỉ sau khi có số mới quyết định có đụng vòng đời `GLSurfaceView`/`RetroActivity` hay không (phương án B
   trong §7 của tài liệu đó là thay đổi **hành vi vòng đời**, cần PM duyệt riêng).

Không đóng issue nào của nhóm 1 với lý do "đã sửa ANR"; chỉ xét sau khi bản có #606+#619 qua **2 bản** và
không cộng lượt.

### (b) `unknown` / `resource` #302 (236 lượt) — cần **đủ hai mảnh**

#302 là tiến trình `:midlet` (game Java), **chưa từng** gọi `Diagnostics.begin` nên không có phiên, không có
lõi — `unknown` đúng theo thiết kế hiện tại, **không** phải bot gán nhầm. Muốn có lõi phải làm **cả hai**:

1. **Phiên cho J2ME** — PR **#638** (gắn phiên `Diagnostics` cho `:midlet`). Chưa gộp.
2. **Đọc phiên chết cho `native`/`resource`/`initfail`** — thay đổi `Diagnostics.kt:867` (dòng
   `val deathSession = if (kind == "anr" || kind == "lowmem") …`) + `enrichDeathReport` (`Diagnostics.kt:917-918`:
   `if (r.kind != "lowmem" && r.kind != "anr") return r`) — xem `docs/loi/unknown-native-1311.md` §4 mục 1–2.
   Hiện nằm ở **nhánh local**, chờ **#624** để không xung đột cùng tệp. Chưa gộp.
   *(Tài liệu gốc `unknown-native-1311.md` §3.2 dẫn `:856`; trên `f75b2e6d` dòng 856 là nhánh
   `ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"`, còn dòng cần sửa là `:867`.)*

Thiếu một trong hai thì #302 vẫn `unknown`: mảnh 2 một mình vẫn trả `null` vì `:midlet` không có tệp phiên;
mảnh 1 một mình chỉ ghi được phiên cho tiến trình **đang sống**, không cứu được báo cáo chết do
`ApplicationExitInfo`. Đây là **quyết định thiết kế** (thêm phiên cho một module không phụ thuộc `:app`), không
phải bug nhỏ — cần PM chốt trước khi làm.

### (c) Ghim 4 lõi còn trỏ buildbot `nightly/latest`

Theo `docs/loi/loi-khac-1313.md` §4: `snes9x`, `pcsx_rearmed`, `melonds`, `melondsds` **không** có `artifacts`
+ `sha256`, `version = "1"` ⇒ `isReady()` chỉ so chuỗi `"1"` nên máy đã cài **không bao giờ** tải lại, còn máy
mới cài nhận bản khác; không có gói ký hiệu nên `native` của các lõi này **không** giải mã được. `citra` cũng
chưa ghim nhưng đã có tài liệu riêng (`docs/loi/citra-native-1311.md` §3(1)).

Đây là việc **`config/`** (thêm `artifacts`/`sha256`, tăng `configVersion`, đồng bộ Cloudflare) + đóng gói lõi
theo V198 — **cần PM duyệt**, không tự làm trong tài liệu này. Hệ quả nếu không ghim: mọi so sánh "trước/sau
bản sửa" cho nhóm 3, 6, 8, 10 đều **không tái lập được**.

---

## 6. Liên kết tài liệu chẩn đoán theo nhóm

| Nhóm | Tài liệu | Trên `main`? |
|---|---|---|
| `melondsds` / `anr` | `docs/loi/melondsds-anr-1313.md` (#633) · `docs/loi/melondsds-anr-1311.md` (#619) | **Có** |
| `melondsds` / `killed` | `docs/loi/melondsds-killed-1311.md` | **Không** — PR **#624** đang mở |
| `melondsds` / `core-error` | `docs/loi/loi-khac-1313.md` §7.2 | **Có** |
| `unknown` / `resource`, `native` | `docs/loi/unknown-native-1311.md` | **Có** |
| `unknown` / `anr` | `docs/loi/unknown-anr-1313.md` | **Không** — PR **#634** đang mở |
| `unknown` / `op-fail` | `docs/loi/op-fail-1311.md` · `docs/loi/op-fail-con-lai-1311.md` | **Có** |
| `unknown` / `lowmem` | `docs/loi/lowmem-1311.md` | **Có** |
| `ppsspp` / `native` | `docs/loi/ppsspp-native-1311.md` | **Có** |
| `ppsspp` / `anr` | `docs/loi/ppsspp-anr-1311.md` | **Không** — PR **#623** đang mở |
| `citra` / `native` | `docs/loi/citra-native-1311.md` | **Có** |
| `citra` / `core-error`, `anr`, `killed`, `unknown` | `docs/loi/citra-con-lai-1313.md` | **Không** — PR **#635** đang mở |
| `desmume` / `native` | `docs/ds/chan-doan-desmume-native.md` (ngoài `docs/loi/`) | **Có** |
| `pcsx_rearmed`, `snes9x`, `melonds`, `mgba`, `gambatte`, `kirikiroid2-yuri`, `mkxp-z` | `docs/loi/loi-khac-1313.md` | **Có** |

---

## 7. Giới hạn của tài liệu này

- **Không** có `CRASH_ADMIN_TOKEN` trong lượt lập: không đọc được `reason`, `frames`, `detail`, `component`,
  `stage`, `device` của bất kỳ báo cáo nào. Mọi câu về nguyên nhân trong các tài liệu dẫn ở §6 đều là **đọc
  mã**, và giữ nguyên nhãn `[CHƯA KIỂM]` như bản gốc.
- **Không** tự suy nguyên nhân mới. Tài liệu này chỉ tổng hợp, đối chiếu trạng thái và nêu việc còn chờ.
- **Không** có bản phát hành nào sau mốc #606+#619 đã được đo lại ⇒ chưa nhóm nào được chứng minh là "hết".
- Số liệu là **ảnh chụp 13/11**; `count` cộng dồn nên lần đọc sau sẽ lớn hơn — **không** so trực tiếp hai ảnh
  chụp khác ngày để nói "tăng/giảm" khi chưa tách theo `(+N)`.
- Việc **đóng #542** (bảng tồn 08/11) và cách xử lý nhánh `loi/ton-0811-bang-ton` thuộc PM, không thuộc
  tài liệu này.
