# `citra` còn lại — #373, #404, #458 (19/11/2026)

Phạm vi: 3 issue `citra` chưa đóng sau các lượt trước — **#458, #404** (`native`) và **#373** (`unknown`).
Người lập: phiên con **LB3**, 19/11/2026. Số liệu lấy từ khối `<!-- aow-crash-v100:… -->` trong thân issue; phiên này **không** có `CRASH_ADMIN_TOKEN` nên không đọc được `frames`/`build_id` gốc — mọi câu về nguyên nhân native ghi **[CHƯA KIỂM]**.

## 1. Số liệu

| Issue | Chữ ký (12 hex) | Loại | Lượt | Máy (`seen`) | Cập nhật cuối | Ghi chú |
|---|---|---|---:|---:|---|---|
| [#458](https://github.com/aowvn-10diem/aowvn-monika/issues/458) | `6476cb9d47e9` | native | 37 (was 29 ngày 13/11) | 10 | 16/11 | **đang tăng** |
| [#404](https://github.com/aowvn-10diem/aowvn-monika/issues/404) | `a47d68ae82bc` | native | 3 | 2 | 13/11 | cùng lớp với #458, mẫu nhỏ |
| [#373](https://github.com/aowvn-10diem/aowvn-monika/issues/373) | `a924b4c43381` | unknown | 1 | 1 | 27/10 | 1 lượt, không có khung |

Tổng nhóm: **41 lượt** trong 3 issue; `citra/native` = 40 lượt (#458 + #404).

## 2. Vì sao **không đóng hôm nay**

1. **#662 chỉ là công cụ giải ký hiệu**, chưa có gói symbols của citra: `fix(chẩn đoán): giải ký hiệu native đúng dạng khung tombstone…` (gộp 19/11). Có công cụ mà không có `symbols-citra-{abi}.zip` thì khung vẫn là địa chỉ trần — không quy được hàm nào. Không có căn cứ để nói #404 "trùng chữ ký" hay "cùng một gốc" với #458: hai hash khác nhau, chưa giải mã được khung nào.
2. **Chưa có bản chính thức chứa bất kỳ thay đổi nào cho nhóm này** (0.8.14 cắt trước lượt này) ⇒ luật A102 chưa cho đóng bằng "đã vá".
3. **#458 vẫn tăng**: 29 → 37 lượt trong 3 ngày, 10 máy. Đóng lúc này là bỏ tín hiệu.
4. **#373 là `unknown` 1 lượt / 1 máy**, mở 27/10, không khung, không `Thành phần` — không tái hiện, cũng không đủ để kết luận; đóng theo "hết hạn" phải chờ luật đóng issue tồn của PM.

## 3. Phần app đã kín (không còn việc ở đây)

| Việc từng nêu | Trạng thái trên `origin/main` 19/11 | Bằng chứng |
|---|---|---|
| `.cia` lọt vào lõi libretro | **đã chặn** | `runner/GameLauncher.kt:53` gọi `ciaBlockedReason(...)`; hàm ở `:139-148`, câu báo chỉ sang đường Azahar |
| Nhãn `engine:azahar` cho crash lõi citra libretro | **đã sửa** | `diag/Components.kt:25-34`: nhánh Azahar chỉ khi `!libretro`; còn lại rơi vào `engine:libretro:<lõi>` |
| Lõi citra không ghim bản / không có `sha256` | **còn**, thuộc `config/` | `config/monika-config.json` `cores.citra` chỉ có `version`, `url`, `abis`, `aspectRatio`, `lowLatencyAudio`; `version = "1"`, url buildbot `nightly/android/latest/{abi}/…`. `CoreManager.kt:38,45,53-57` so đúng chuỗi `version` ⇒ mọi bản nightly đều là "bản 1" |
| Cấu hình đồ hoạ citra chưa hạ (`options`/`perf`) | **còn**, thuộc `config/` | `cores.citra` không có khoá `options`/`perf` (khác `ppsspp`) |
| Symbols cho citra | **chưa có** | không có `symbols-citra-*` trong release; chỉ ppsspp/desmume/rgss có |

Ba việc còn lại đều là `config/` hoặc gói lõi — **không** phải mã app. Lượt này không mở PR lõi, không đụng `config/`.

## 4. Đặc tả gói symbols citra (việc cần dựng, để #458 giải mã được)

Mục tiêu: `scripts/crash-reports.sh <id> --symbols-dir … --symbolizer …` ghép được khung của `libcitra_libretro_android.so` như đã làm cho ppsspp.

1. **Ghim nguồn**: dựng `citra_libretro_android.so` từ một commit citra cố định (không dùng `nightly/latest`), đăng lên `aowvn-monika-packs` thành `cores-citra-<n>`.
2. **Cờ dựng**: `RelWithDebInfo` (hoặc `-g` + `-O2`), `-Wl,--build-id=sha1` để `.so` có BuildId — điều kiện duy nhất để `scripts/crash-report-detail.py` ghép khung (skill `bao-loi-diag`: chỉ ghép khung có PC + BuildId, không đoán địa chỉ).
3. **Đóng gói**: `symbols-citra-<gói>-<run>-arm64-v8a.zip` gồm `.so` chưa strip + `build-ids.tsv` (mức `debug` hoặc `symtab`; `dynamic-only` không phục hồi được tên hàm).
4. **Khai trong config** (cần PM duyệt, tăng `configVersion`, đồng bộ Cloudflare): `cores.citra.symbols` + `cores.citra.artifacts[abi].sha256` + `version` đổi theo gói (bỏ `"1"`).
5. **Đo lại**: chạy `crash-reports.sh` cho các `id` trong marker của #458. Có khung → hoặc sửa app-side (nếu khung nằm trong mã Java/JNI của mình), hoặc ghi đặc tả lõi gửi đội dựng engine/chờ Azahar.

Thay thế rẻ hơn nếu chưa dựng được lõi: chờ bản nightly tương ứng có `.so` kèm BuildId công khai — nhưng nightly trôi theo ngày nên **không tái hiện** được; chỉ dùng để đọc một lần.

## 5. Việc tiếp

1. PM duyệt hướng dựng gói `cores-citra-<n>` + symbols (mục 4). Đây là điều kiện cần để đóng #458.
2. Khi có symbols: chạy `scripts/crash-reports.sh` cho #458/#404/#373; nếu #404 cùng khung với #458 thì đóng **trùng** `not_planned` trỏ #458; nếu khác thì tách đặc tả riêng.
3. Sau bản chính thức có hạ cấu hình citra (`configVersion` mới): đo lại lượt `citra/native` trong 2 bản để biết có giảm.
4. #373: khi có luật đóng issue tồn, đóng `not_planned` (1 lượt, 1 máy, không khung, không tái diễn 3 tuần) kèm trỏ về tệp này.
