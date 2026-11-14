# Nhóm nhỏ còn lại (23 issue, 69 lượt): một bảng gộp và ba điều kiện để chẩn đoán tiếp

Ngày 14/11/2026 · nhánh `chan-doan/nhom-nho-1314` · mã đọc trên `origin/main` **`15f210a6`** (configVersion 63).
Phạm vi: 23 issue `crash-moi` thuộc các nhóm nhỏ **chưa có tài liệu riêng**. Chỉ tài liệu — **không sửa mã**,
không đụng `config/`, workflow, `Diagnostics.kt`.

Số liệu lấy từ GitHub API ngày 14/11: **75 issue mở** nhãn `crash-moi`; **74** issue mang khối
`<!-- aow-crash-v100:… -->`, **#549** là issue theo dõi `lowmem` (không có khối). Bảng §1 phủ **23/74**; §6 đối
chiếu phần còn lại.

## 0. Cách đọc bảng

- `Lượt` = `count` trong khối `aow-crash-v100` — **cộng dồn lịch sử**, không phải số người và không phải mức tăng
  hiện tại (đã giải thích ở `docs/loi/lowmem-1311.md` §1).
- `Máy` = số khoá trong `seen`, tức **xấp xỉ số lần gửi báo cáo**, không phải số thiết bị (`scripts/crash-issue-bot.py:76`).
  Số thiết bị/đời máy thật chỉ có trong Worker ⇒ cần `CRASH_ADMIN_TOKEN`.
- `Tái diễn?` = bot PATCH thân issue **sau** lần tạo, chỉ xảy ra khi `delta ≥ 5` (`crash-issue-bot.py:118`).
  `(+N)` là lượt mới của lần thông báo cuối; **`N == count` nghĩa là tạo issue đã mang đủ N lượt** (burst đầu),
  không phải tái diễn. Vì ngưỡng 5, "không tái diễn" chỉ có nghĩa "chưa đủ 5 lượt mới" — với **19/23** issue ở đây
  (≤ 4 lượt) đây là **giới hạn thật**, không phải bằng chứng đã hết.
- `Thành phần` = dòng `Thành phần:` trong thân issue. **22/23 issue không có** ⇒ không quy được thành phần từ dữ
  liệu công khai (cùng giới hạn `docs/loi/op-fail-con-lai-1311.md` §3).

**Trạng thái các tài liệu được dẫn** (kiểm bằng `git ls-tree origin/main -- docs/loi docs/ds` hôm nay):

| Tài liệu | Trạng thái |
|---|---|
| `docs/loi/lowmem-1311.md`, `op-fail-1311.md`, `op-fail-con-lai-1311.md` (PR #626), `unknown-native-1311.md`, `melondsds-anr-1311.md` (PR #619), `melondsds-anr-1313.md` (PR #633), `citra-native-1311.md` (PR #622), `loi-khac-1313.md`, `ppsspp-native-1311.md` (PR #620), `docs/ds/chan-doan-desmume-native.md` | **đã ở `main`** |
| `docs/loi/ppsspp-anr-1311.md` | **chưa gộp** — PR **#623** đang mở |
| `docs/loi/citra-con-lai-1313.md` | **chưa gộp** — PR **#635** đang mở |
| `docs/loi/unknown-anr-1313.md` | **chưa gộp** — PR **#634** đang mở |

## 1. Bảng duy nhất (23 issue, 69 lượt)

| Issue | Lõi / loại | Lượt | Máy | Thành phần | Tái diễn? |
|---|---|---:|---:|---|---|
| [#385](https://github.com/aowvn-10diem/aowvn-monika/issues/385) | `kirikiroid2-yuri` / `native` | 13 (+5) | 8 | — | **Có** (gộp 02/11) |
| [#397](https://github.com/aowvn-10diem/aowvn-monika/issues/397) | `melondsds` / `unknown` | 13 (+12) | 7 | — | **Có** (gộp 02/11) |
| [#416](https://github.com/aowvn-10diem/aowvn-monika/issues/416) | `ppsspp` / `unknown` | 8 (+5) | 3 | — | **Có** (gộp 03/11) |
| [#459](https://github.com/aowvn-10diem/aowvn-monika/issues/459) | `snes9x` / `anr` | 5 (+5) | 3 | — | Không (5 lượt ngay lần tạo) |
| [#315](https://github.com/aowvn-10diem/aowvn-monika/issues/315) | `desmume` / `anr` | 4 (+4) | 3 | — | Không (4 lượt ngay lần tạo) |
| [#403](https://github.com/aowvn-10diem/aowvn-monika/issues/403) | `mgba` / `core-error` | 4 (+4) | 3 | — | Không (4 lượt ngay lần tạo) |
| [#563](https://github.com/aowvn-10diem/aowvn-monika/issues/563) | `melonds` / `native` | 3 (+3) | 2 | — | Không (3 lượt ngay lần tạo) |
| [#448](https://github.com/aowvn-10diem/aowvn-monika/issues/448) | `snes9x` / `native` | 2 (+2) | 2 | — | Không |
| [#513](https://github.com/aowvn-10diem/aowvn-monika/issues/513) | `gambatte` / `unknown` | 2 (+2) | 2 | — | Không |
| [#402](https://github.com/aowvn-10diem/aowvn-monika/issues/402) | `gambatte` / `anr` | 2 (+2) | 2 | — | Không |
| [#482](https://github.com/aowvn-10diem/aowvn-monika/issues/482) | `melondsds` / `unknown` | 1 (+1) | 1 | — | Không |
| [#522](https://github.com/aowvn-10diem/aowvn-monika/issues/522) | `desmume` / `anr` | 1 (+1) | 1 | — | Không |
| [#400](https://github.com/aowvn-10diem/aowvn-monika/issues/400) | `desmume` / `anr` | 1 (+1) | 1 | — | Không |
| [#333](https://github.com/aowvn-10diem/aowvn-monika/issues/333) | `desmume` / `anr` | 1 (+1) | 1 | — | Không |
| [#535](https://github.com/aowvn-10diem/aowvn-monika/issues/535) | `melonds` / `anr` | 1 (+1) | 1 | — | Không |
| [#516](https://github.com/aowvn-10diem/aowvn-monika/issues/516) | `mgba` / `anr` | 1 (+1) | 1 | — | Không |
| [#465](https://github.com/aowvn-10diem/aowvn-monika/issues/465) | `gambatte` / `op-fail` | 1 (+1) | 1 | — | Không |
| [#464](https://github.com/aowvn-10diem/aowvn-monika/issues/464) | `mgba` / `op-fail` | 1 (+1) | 1 | — | Không |
| [#418](https://github.com/aowvn-10diem/aowvn-monika/issues/418) | `snes9x` / `core-error` | 1 (+1) | 1 | — | Không |
| [#378](https://github.com/aowvn-10diem/aowvn-monika/issues/378) | `mgba` / `unknown` | 1 (+1) | 1 | — | Không |
| [#613](https://github.com/aowvn-10diem/aowvn-monika/issues/613) | `mkxp-z` / `native` | 1 (+1) | 1 | `engine:rgss` | Không |
| [#368](https://github.com/aowvn-10diem/aowvn-monika/issues/368) | `desmume` / `op-fail` | 1 (+1) | 1 | — | Không |
| [#387](https://github.com/aowvn-10diem/aowvn-monika/issues/387) | `unknown` / `native` | 1 (+1) | 1 | — | Không |

Tổng: **69 lượt / 23 issue**. Ba issue tái diễn (#385, #397, #416) chiếm **34/69 lượt**.

## 2. Theo họ: cùng lớp với tài liệu nào, và khác chỗ nào

### 2.1 `native` — 5 issue, 20 lượt (#385, #563, #448, #613, #387)

Cùng lớp `docs/loi/loi-khac-1313.md` §4 (đã ở `main`) và `docs/loi/ppsspp-native-1311.md` (đã ở `main`, gộp từ
PR #620): chết trong `.so` của lõi, chữ ký = `module:symbol` của ≤ 3 khung (`crash-issue-bot.py:44-50`), và muốn
biết khung thì phải có gói ký hiệu **khớp BuildId**. Khác biệt của từng issue:

- **#385 là ca giàu bằng chứng nhất của họ `native` ở đây**: lõi ghim
  (`modules.kirikiri.version = 6e61ce3-aow3` + `sha256`) và release `engines-kirikiri-16` **có**
  `symbols-kirikiri-16-arm64-v8a.zip` (kiểm hôm nay bằng `gh release view … -R aowvn-10diem/aowvn-monika-packs`);
  trong 74 issue, chỉ `kirikiri` và `ppsspp` (từ 05/11) có cả hai vế. Vì vậy đây là issue **nên giải mã trước
  tất cả**; xem lệnh §5.1.
- **#448 (`snes9x`), #563 (`melonds`)**: hai lõi này vẫn trôi bản (`version = "1"`, buildbot nightly —
  `config/monika-config.json:996` và `:839`) và **không** có gói ký hiệu nào trong `aowvn-monika-packs` ⇒ không
  giải mã được, không phải "chưa ai làm". Không mở việc mới (đúng kết luận #4 mục 7.4 / #5 mục 7.3 của
  `loi-khac-1313.md`).
- **#613 (`mkxp-z`)**: lõi ghim (`engines-rgss-6`) nhưng release chỉ có `rgss-arm64-v8a.zip` + `.sha256`; phần
  `symbols-rgss-*` vẫn là **bản nháp** (cổng giấy phép G8) ⇒ vẫn không retrace được. Bổ sung so với §7.5 của
  `loi-khac-1313.md`: đây là issue **duy nhất** trong 23 issue có dòng `Thành phần`, và `Components.kt:30` lấy
  `engine:rgss` từ `session.kind == "rgss"` — nghĩa là báo cáo **có phiên**, tức lỗi xảy ra **sau** khi engine đã
  vào game, không phải lúc nạp gói.
- **#387 (`unknown` + `native`)**: xem `docs/loi/unknown-native-1311.md` §3(e) — chết trước khi có phiên nên
  không có lõi; **không** sửa được bằng ký hiệu (không biết `.so` nào), chỉ bằng `CRASH_ADMIN_TOKEN`.

### 2.2 `anr` — 8 issue, 16 lượt

Bốn issue `anr` còn lại của nhóm nhỏ (#459 `snes9x`, #402 `gambatte`, #516 `mgba`, #535 `melonds`) **cùng lớp** `docs/loi/melondsds-anr-1313.md` §4 (đã ở `main`; Main Thread chờ **Java monitor của
GLThread**) và `docs/loi/ppsspp-anr-1311.md` §2 (PR #623, **chưa gộp**): tất cả chạy `RetroActivity` trong `:game`
trên cùng `GLRetroView`. Điểm **mới** — nhóm `desmume/anr` (4 issue, 7 lượt) **chưa từng có tài liệu**:

- `docs/ds/chan-doan-desmume-native.md` §1 chỉ đếm "`desmume`/`anr` 4 issue / 7 lượt — chưa có gì"; **#522 chưa
  xuất hiện ở bất kỳ tệp nào** trong `docs/` (đối chiếu bằng grep số issue trên `origin/main`).
- Cơ chế **không** có gì riêng so với các tài liệu ANR đã dẫn (`ppsspp-anr-1311.md` §2 — PR #623 **chưa gộp**;
  `melondsds-anr-1313.md` §4 — đã ở `main`): ANR là Main Thread bị chặn, còn `desmume_num_cores`
  (`config:826` mặc định `2`, `:831` lite `1`, `:834` full `4`) là **luồng native bên trong lõi**, không giữ Java
  monitor. Nó chỉ có thể **làm dài** đơn vị việc của GLThread (nhiều luồng dựng hình 3D tranh CPU) — tức làm nặng
  nhánh §4.1 của `melondsds-anr-1313.md`, không phải một nguyên nhân mới. **[CHƯA KIỂM]**
- Mốc thời gian: cả 4 issue mở **trước** `#606` (11/11 23:16) và **không** có `(+N)` nào sau đó. Nhưng cả 4 đều
  ≤ 4 lượt, dưới ngưỡng PATCH 5 ⇒ **không được** nói "đã hết" (khác #350/#318 có 216/177 lượt nên im lặng là
  bằng chứng mạnh hơn).
- Việc đúng: **không sửa mã** cho 4 issue này; xếp chung hàng đợi "chờ v0.8.13 qua 2 bản" như §6 `loi-khac-1313.md`,
  và chỉ khi đó mới cần `adb bugreport` (cách đọc: `docs/loi/ppsspp-anr-1311.md` mục 5 — PR #623 **chưa gộp**). Riêng
  **#522** (1 lượt, 06/11) dưới mọi ngưỡng kết luận — ghi nhận, không hành động.

### 2.3 `core-error` — 2 issue, 5 lượt (#403 `mgba`, #418 `snes9x`)

Cùng lớp `docs/loi/loi-khac-1313.md` §3: **một cửa duy nhất** `RetroActivity → Diagnostics.recordCoreFailure`, mã
`0/1/2/-1`, thân issue **không** mang mã. Khác biệt:

- **#403 (`mgba`)**: lõi **đã ghim** (`cores-gb-gba-nes-1` + `sha256`) ⇒ giả thuyết "lõi trôi bản" của #622 **bị
  loại**, chỉ còn hai nhánh: ROM `.gba` hỏng/đổi tên (mã `1`, xem §3.4 tài liệu đó) hoặc `-1` từ `afterGameLoad`.
  Đây là 4 lượt/3 lần gửi — cần mã lỗi mới tách được, không cần symbols.
- **#418 (`snes9x`)**: 1 lượt, lõi trôi bản; dưới ngưỡng kết luận. Giữ nguyên trạng thái.

### 2.4 `unknown` — 5 issue, 25 lượt (#397, #482, #416, #513, #378)

Cùng lớp `docs/loi/loi-khac-1313.md` §5: `kind = "unknown"` chỉ sinh khi `exitInfo == null`
(`Diagnostics.kt:230-241`), tức API < 30 hoặc Android không còn bản ghi; **lõi có mặt** vì phiên do tiến trình
`:game` ghi. Điểm **mới** ở đây là **#416 (`ppsspp`, 8 lượt/3 báo cáo)** — không nằm trong bảng của tài liệu nào
(trước bản này nó chỉ được nhắc ở `docs/yeu-cau/BACKLOG-NGUOI-DUNG.md`):

- Từ 05/11 `ppsspp` **đã ghim**: `version = "cores-ppsspp-1"` + `artifacts` hai ABI có `sha256`
  (`config:958-985`), và release `cores-ppsspp-1` **có** `symbols-ppsspp-{arm64-v8a,armeabi-v7a}.zip`.
  Nghĩa là **không** còn "trôi bản", và nếu báo cáo của #416 thật ra là **native trên Android ≤ 10** thì nó nằm
  cùng quần thể #316 (114 lượt/51 báo cáo) + #372 (90/12) và **giải mã được**.
- Phép tách rẻ nhất: đọc `device`/`env` (SDK) của 3 báo cáo #416. Nếu `sdk < 30` ⇒ #416 là "native bị mất nhãn",
  không phải lỗi mới; nếu `sdk ≥ 30` ⇒ Android đã xoá `ApplicationExitInfo` và cần xem `stage`/`crumbs`.
  **[CHƯA KIỂM]**
- #397/#482 (`melondsds`) vẫn đúng như §5: lõi trôi bản + không symbols ⇒ dù có token cũng chỉ đọc được `reason`,
  không đọc được khung.

### 2.5 `op-fail` — 3 issue, 3 lượt (#368, #464, #465)

Cùng lớp `docs/loi/op-fail-con-lai-1311.md` §3–§4: 14/16 issue `op-fail` **không có** `Thành phần`; op nào báo
cũng chỉ suy được từ `bước` trong báo cáo gốc. Khác biệt duy nhất: **#368 (`desmume`)** — lõi này **không** khai
`abis` và **không** có `artifacts` (`config:821`), tức tải buildbot nightly; hai op khả dĩ là `tai-xuong` (ZIP hỏng/
404) hoặc `cai-goi` (`PackManager.kt:173`). #464/#465 đã nằm trong bảng #626 — bảng §1 chỉ nhắc lại, **không
triage lại**.

## 3. Ba điều kiện cần để chẩn đoán tiếp

**A. `CRASH_ADMIN_TOKEN` + `scripts/crash-reports.sh`** — đọc `reason`, `detail`, `log`, `device`, `env`, `cert`,
`stage` của báo cáo gốc. Không có token thì **23/23 issue** chỉ đọc được thân issue (đúng những gì bảng §1 có).
Riêng nhóm này, token là điều kiện **đủ** cho: mã lỗi `core-error` (#403, #418), `op`/`bước` (#368, #464, #465),
SDK/`reason` của `unknown` (#397, #482, #416, #513, #378), và `trace`/`stage` của `anr` (#315, #333, #400, #522,
#459, #402, #516, #535).

**B. Gói ký hiệu khớp BuildId** — phần **BuildId đã xong**: `-Wl,--build-id=sha1` được gộp trong PR **#601**
(13/11 08:15Z), có trên `main` tại `libretrodroid/src/main/cpp/CMakeLists.txt:112` **và** trong tag `v0.8.13`
⇒ APK **từ v0.8.13 trở đi** mang BuildId cho `libretrodroid.so`; rào chắn "không có BuildId" chỉ còn đúng với báo
cáo từ APK **≤ v0.8.12**. Phần **còn thiếu là gói symbols**: hiện **chỉ có** cho `kirikiri`
(`symbols-kirikiri-16-arm64-v8a.zip`, đính ở release `engines-kirikiri-16`) và `ppsspp`
(`symbols-ppsspp-{arm64-v8a,armeabi-v7a}.zip` trong `cores-ppsspp-1`); **không có** cho `mgba`/`gambatte`
(`cores-gb-gba-nes-1` không kèm symbols), cho `mkxp-z` (`symbols-rgss-*` mới chỉ là bản nháp theo cổng G8), cho
`snes9x`/`desmume`/`melonds`/`melondsds` (lấy từ buildbot nightly, không có pack nào), và cho `azahar` (workflow
`build-engines.yml:217-231` có bước phát hành `symbols-azahar-*` nhưng hiện **không** có release nào như vậy).
Thiếu B thì **không thể** kết luận khung cho: #385 (nếu chưa tải symbols), #448, #563, #613. `crash-reports.sh`
chỉ ghép khung **có PC + BuildId** — symbols có mà BuildId lệch (báo cáo cũ trước v0.8.13, hoặc lõi trôi bản) thì
vẫn rỗng, phải kiểm lúc chạy.

**C. Lõi ghim bản** — `desmume` (`config:821`), `snes9x` (`:996`), `melonds` (`:839`), `melondsds` (`:1129`) đều
`version = "1"` ⇒ hai máy cùng nhãn có thể chạy hai `.so` khác nhau. Thiếu C thì **không thể** (i) tái hiện đúng
bản, (ii) nói một bản sửa có tác dụng, (iii) biết báo cáo cũ thuộc bản nào: #448, #459, #418, #535, #563, #368,
#397, #482, và 4 issue `desmume/anr` (#315, #333, #400, #522).

| Issue | A chưa thoả | B chưa thoả | C chưa thoả | Kết luận tối đa khi cả ba chưa thoả |
|---|---|---|---|---|
| #385 | ✗ | ✗ (symbols có, chưa chạy) | — | "có symbols, chưa giải mã" (không nêu khung) |
| #397, #482 | ✗ | ✗ | ✗ | "chưa biết loại chết" |
| #416 | ✗ | ✗ | — (đã ghim) | "unknown, không rõ native hay ANR" |
| #513, #378 | ✗ | ✗ | — (đã ghim) | "chưa biết loại chết" |
| #387 | ✗ | — (không có lõi) | — | "không biết lõi nào" |
| #403 | ✗ | — | — (đã ghim) | "core-error, chưa biết mã" |
| #418 | ✗ | ✗ | ✗ | "core-error 1 lượt" |
| #448, #563 | ✗ | ✗ | ✗ | "native, không biết khung" |
| #613 | ✗ | ✗ | — (đã ghim) | "native trong engine rgss" |
| #459, #402, #516, #535 | ✗ | — (ANR cần trace, không cần symbols) | — / ✗ (#459, #535) | "ANR, chưa biết khung chờ" |
| #315, #333, #400, #522 | ✗ | ✗ | ✗ | "ANR desmume, dưới ngưỡng mẫu" |
| #368, #464, #465 | ✗ | — | ✗ (#368) | "op-fail, chưa biết op" |

## 4. Mới so với các tài liệu trước (không đọc lại phần đã có)

1. **`ppsspp` đã ghim và có symbols** (`cores-ppsspp-1`, phát hành 05/11 15:44Z) — bảng ghim bản của
   `loi-khac-1313.md` §4 chỉ xét 6 lõi và **không** có `ppsspp`, nên đây là bổ sung mới; hệ quả là #416/#316/#372
   **không còn** thuộc diện "lõi trôi bản" và có symbols để ghép khung.
2. **`cores-gb-gba-nes-1` không có symbols** ⇒ `mgba`/`gambatte` "ghim bản" **không** đồng nghĩa "retrace được"
   (#403 chỉ cần mã lỗi, không cần symbols).
3. **`desmume/anr` lần đầu có tài liệu** (4 issue, 7 lượt) và **#522 chưa từng xuất hiện** trong `docs/`.
4. **#416 chưa từng có trong bảng nào**; giả thuyết "native mất nhãn trên Android ≤ 10" là mới, kiểm bằng `device.sdk`.
5. Xác nhận lại bằng dữ liệu hôm nay: 22/23 issue không có `Thành phần`; `engine:rgss` (#613) là ca duy nhất có.
6. **[Đính chính điều kiện B]** `-Wl,--build-id=sha1` (PR **#601**, gộp 13/11 08:15Z) **đã có trong v0.8.13**
   (`CMakeLists.txt:112` ở cả `main` và tag) ⇒ APK hiện hành **đã** mang BuildId; rào chắn còn lại **chỉ** là
   **gói symbols** cho `mgba`/`gambatte`, `mkxp-z`, và 4 lõi buildbot (`snes9x`/`desmume`/`melonds`/`melondsds`).

## 5. Việc tiếp theo (rẻ → đắt)

1. **Giải mã #385** — rẻ nhất, đủ điều kiện nhất:
   ```bash
   gh release download engines-kirikiri-16 -R aowvn-10diem/aowvn-monika-packs \
     -p 'symbols-kirikiri-16-arm64-v8a.zip'
   CRASH_ADMIN_TOKEN=… scripts/crash-reports.sh --by-fp          # lấy id của chữ ký 3ea3929f2471
   CRASH_ADMIN_TOKEN=… scripts/crash-reports.sh <id> \
     --symbols-dir ./symbols-kirikiri-16-arm64-v8a --symbolizer "$(command -v llvm-symbolizer)"
   ```
   Khung nằm trong `libkrkr2yuri.so` ⇒ mở PR mã; khung nằm trong `libretrodroid.so`/`libGLES*` ⇒ thuộc lớp
   `docs/loi/ppsspp-native-1311.md`, không phải lỗi engine.
2. **Tách #416** bằng `device.sdk` của 3 báo cáo (mục 2.4). Nếu `sdk < 30`: ghi vào #416 là "native bị mất nhãn",
   **không** mở việc mới; đồng thời chạy symbols `cores-ppsspp-1` nếu tombstone có BuildId.
3. **Đọc `reason` của 5 issue `core-error`+`unknown`** (#403, #418, #397, #482, #513) để điền cột mã lỗi/loại chết
   vào bảng §1 — chỉ cần A.
4. **#368**: đọc `bước` của báo cáo duy nhất; nếu là `tai-xuong` thì đây là lỗi mạng/404 phía buildbot, không phải
   op của người dùng.
5. **Không** đóng issue nào trong 23 issue này bằng suy luận: 16 issue ≤ 2 lượt **không** đủ mẫu, và ngưỡng PATCH 5
   che mất tái diễn nhỏ.

## 6. Đối chiếu phủ 74/74 issue `crash-moi` đang mở

Mỗi issue mở được **gán chủ sở hữu** cho một tài liệu (chia không trùng, không thiếu — kiểm bằng danh sách số
issue). Nhiều issue còn được **nhắc** ở tài liệu khác (ví dụ #464/#465 ở cả `loi-khac-1313.md` và
`op-fail-con-lai-1311.md`, #614 ở 4 tệp) — cột dưới đây là nơi chẩn đoán sâu nhất, không phải nơi duy nhất có tên.

| Tài liệu | Issue | Số |
|---|---|---:|
| `docs/loi/citra-native-1311.md` (đã ở `main`; + `citra-con-lai-1313.md` — PR #635 **chưa gộp**) | 301, 328, 336, 373, 404, 458, 460, 607, 610 | 9 |
| `docs/loi/melondsds-anr-1313.md` | 314, 318, 350, 439, 440, 529, 530, 533, 597 | 9 |
| `docs/ds/chan-doan-desmume-native.md` | 331, 369, 377, 562, 591 | 5 |
| `docs/loi/ppsspp-native-1311.md` | 316, 372, 478, 520 | 4 |
| `docs/loi/unknown-native-1311.md` | 302, 319, 366, 371, 376, 387, 393, 395, 398, 399, 401, 407, 413, 414, 415, 419, 525, 531, 534, 564, 608, 614 | 22 |
| `docs/loi/loi-khac-1313.md` | 322, 378, 385, 397, 402, 403, 418, 448, 459, 464, 465, 482, 513, 516, 535, 563, 567, 578, 613 | 19 |
| **`docs/loi/nhom-nho-1314.md` (tệp này)** | 315, 333, 368, 400, 416, 522 | 6 |
| **Tổng** | | **74** |

(#549 là issue theo dõi `lowmem`, không có khối `aow-crash-v100` ⇒ không tính vào 74.)

Trước bản này, **5 issue** (315, 333, 400, 416, 522) **không** xuất hiện trong bất kỳ tệp `docs/loi/*` hay
`docs/ds/chan-doan-*` nào; chúng nằm ở đây. Vì vậy, với nghĩa "mỗi issue `crash-moi` đang mở có ít nhất một tài liệu
chẩn đoán trong `docs/`, và bảng §1 không trùng số với 6 tài liệu kia":

**đã phủ 74/74 issue `crash-moi`.**

Lưu ý: "phủ" ở đây là **phủ tài liệu**, không phải "đã tìm ra nguyên nhân" — 23 issue trong tệp này vẫn `[CHƯA KIỂM]`
theo bảng §3.

## 7. Giới hạn bằng chứng

- Phiên này **không** có `CRASH_ADMIN_TOKEN` ⇒ không đọc được `reason`, `detail`, `log`, `device`, `cert` của bất
  kỳ báo cáo nào. Mọi cột `Máy`, `Thành phần`, `Tái diễn?` đều suy từ **thân issue**, không từ báo cáo gốc.
- Các con số về release (`cores-ppsspp-1`, `engines-kirikiri-16`, asset symbols) đo hôm nay 14/11/2026; release có
  thể đổi, **không** dùng lại số này cho lượt sau mà không kiểm.
- Lõi trôi bản nghĩa là cùng một chữ ký có thể đến từ nhiều `.so`; "tái diễn" của #385/#397/#416 vì thế **không**
  chứng minh cùng một lỗi trong cùng một binary.
- Nhận định "`desmume_num_cores` chỉ làm nặng nhánh §4.1" là suy luận từ mã + cấu hình, **chưa** có số đo
  (`gfxinfo`/`atrace`) nào. **[CHƯA KIỂM]**
