# Nhóm `unknown / op-fail`: hiện trạng và nghi phạm

Số dòng dẫn chiếu cập nhật theo `main` `f75b2e6d`.

Ngày 13/11/2026 · nhánh `loi/op-fail` · nền `main` `c3a8a9c4`
Nguồn: `gh issue list --label crash-moi --state open` (75 issue) + `docs/CRASH-ISSUE-BOT.md` + thân/ bình luận issue.

## 1. `op-fail` là gì trong mã

- `Diagnostics.op` / `opSuspend` bọc một thao tác người dùng (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:435,442`); lỗi không phải "mong đợi" → `opFail` (`:458-504`).
- Báo cáo có `kind=op-fail`, `component="op:<tên>"` (`:496`), `detail` gồm `thao tác` / `bước` / `mã lỗi` / `đối tượng` / stack đã lọc link, kèm 30 vệt sự kiện.
- Tên thao tác chuẩn nằm trong `Diagnostics.Op` (`:403-412`): `tai-xuong`, `giai-nen`, `nhap-game`, `va-rom`, `cai-goi-engine`, `mo-game`, `tai-cau-hinh`, `tai-feed`.
- Lỗi bị bỏ qua (chỉ ghi vệt): huỷ coroutine, lỗi mạng (`isOffline`), và lỗi khai báo qua `expected` (`:461-471`). Chống spam: cùng `thao tác|bước|mã|đuôi thông điệp` chỉ 1 báo cáo / 10 phút (`:472-481`).

Hệ quả khi đọc issue: "Lõi: `unknown`" **không** có nghĩa là lạ — báo cáo `op-fail` cố ý không mang lõi; bot băm `lõi + loại + ≤3 khung đầu` nên mọi lỗi cùng một đường gọi (cùng stack) gộp chung một issue, bất kể là lỗi app hay lựa chọn của người chơi.

## 2. Hiện trạng nhóm (13/11)

Con số ~1.105 lượt của nhóm là **di sản của hai issue đã đóng**:

| Issue | Thành phần | Lượt | Trạng thái |
|---|---|---:|---|
| #305 | `op:tai-feed` | 1054 | đã đóng 11/11 bởi #603 |
| #512 | `op:tai-feed` | 449 | đã đóng 11/11 bởi #603 |

16 issue `op-fail` còn **mở**, tổng 36 lượt:

| Issue | Lượt | Mới | Ghi chú |
|---|---:|---:|---|
| #393 | 20 | +7 | **không có dòng `Thành phần`** (issue tạo bằng bot cũ) → chưa biết `op:` nào. Cập nhật cuối 05/11. |
| #401 | 2 | +2 | — |
| #319, #366, #368, #371, #376, #395, #407, #464, #465, #525, #531, #564, #608, #614 | 1 mỗi issue | +1 | |
| #614 | 1 | +1 | `op:va-rom` (issue mới nhất, 12/11) |

Tổng 36 lượt còn mở nghĩa là nhóm **không còn sinh hàng loạt**; phần lớn là chữ ký đơn lẻ chưa gom được vì thiếu `Thành phần`.

## 3. Nghi phạm, xếp theo số lượt có thể giải thích

| # | Nghi phạm | Vị trí | Vì sao | Cách kiểm |
|---|---|---|---|---|
| 1 | Di sản `op:tai-feed` | `app/src/main/java/vn/aow/monika/feed/FeedRepository.kt:159-160`, phân loại 4xx ở `:169-172` | #603 (gộp 11/11) đã đổi: 4xx và bài bị gỡ chỉ ghi vệt, chỉ 5xx mới báo, mỗi đường đọc có `bước` riêng | Nếu lượt còn tăng trên bản ≥ 0.8.13 thì là **5xx thật**; đọc `bước` (`tai-danh-sach`/`tai-bai`/`tai-chi-muc`/`tai-trang`) + `mã lỗi` HTTP. [CHƯA KIỂM] |
| 2 | `op:va-rom` báo lỗi chọn nhầm | `app/src/main/java/vn/aow/monika/patch/PatchFlow.kt:25,30,34,59` | ROM quá lớn, không mở được bản vá, sai định dạng, sai ROM/bản vá cụt (= `PatchException` của `RomPatcher`) đều gọi `opFail` trực tiếp, không có `expected` như `Importer` (`Importer.kt:36-37`) → mỗi lần người chơi chọn nhầm thành 1 báo cáo `op:va-rom` | Đã sửa trong nhánh này (mục 4). Kiểm lại #614 sau bản có bản sửa. |
| 3 | `op:cai-goi-engine` báo **hai lần** cho một lỗi | `app/src/main/java/vn/aow/monika/pack/PackManager.kt:173` và `app/src/{github,play}/java/vn/aow/monika/runner/EnginePrepActivity.kt:145,142` | Hai nơi cùng gọi `opFail` cho cùng một lần cài gói, `bước` khác nhau (`pack:<id>` vs `pack:<id> (sau N lần thử)`) nên khoá chống spam khác nhau → 2 báo cáo cho 1 sự cố | Đọc báo cáo: hai `op:cai-goi-engine` cùng `đối tượng` trong vài phút ⇒ xác nhận. [CHƯA KIỂM] |
| 4 | `op:mo-game` | `app/src/main/java/vn/aow/monika/runner/RetroActivity.kt:295`, `app/src/main/java/vn/aow/monika/azahar/AzaharActivity.kt:206` | Chỉ khai báo `expected = isOffline`; lỗi nạp native/gói engine thành báo cáo (đúng), nhưng lỗi do ROM hỗ trợ kém cũng vào đây | Đọc `bước` trong detail; nếu là `nap-native`/`giai-nen-loi` thì là lỗi thật cần theo. |
| 5 | Tên `op:` nằm ngoài `Op` | `app/src/play/java/vn/aow/monika/play/PlayLinks.kt:19,28`, `app/src/play/java/vn/aow/monika/ui/MainActivity.kt:74`, `app/src/play/java/vn/aow/monika/MonikaApp.kt:22` | Dùng chuỗi rời `"mo-lien-ket"`, `"chia-se-hoi-nhom"`, `"doc-bao-loi"`, `"doc-thu-vien"` → không có trong `Op`, không được `DiagnosticsOpTest` phủ, và mỗi tên mới tự sinh một nhóm đơn lẻ | `grep "Thành phần: op:"` trên báo cáo; nếu thấy 4 tên này thì đúng là chúng. Chỉ ảnh hưởng flavor `play`. |

`#393` (20 lượt, +7) chưa quy được nghi phạm nào: issue không có `Thành phần`, và phiên này **không có `CRASH_ADMIN_TOKEN`** nên không đọc được `frames`/`detail`. Cần một lượt có token: `scripts/crash-reports.sh` cho từng báo cáo của chữ ký `5de0a80e6cb8…`, đọc `thao tác`/`bước`/`mã lỗi` rồi đối chiếu bảng trên. [CHƯA KIỂM]

## 4. Đã sửa trong nhánh này

- `Diagnostics.opFail` nhận thêm `expected: (Throwable) -> Boolean = { false }` (`Diagnostics.kt:453-471`), cùng quy ước với `op`/`opSuspend`: lỗi khai báo trước chỉ để lại vệt `(mong đợi, không báo)`, không lập báo cáo. Tham số thêm ở cuối nên mọi chỗ gọi cũ giữ nguyên.
- `PatchFlow` (nghi phạm 2) dùng `inputFail(...)` cho 4 chỗ chọn nhầm ROM/bản vá (`PatchFlow.kt:19-20,25,30,34,59`); lỗi môi trường vẫn báo: `tao-thu-muc` (`:44`), `doi-ten-file` (`:53`), `doc-ghi` (`:62`), `het-bo-nho` (`:65`).
- Test: `DiagnosticsOpTest` 23/23 PASS — thêm `opFailSkipsDeclaredExpectedErrorsButStillReportsRealOnes`, `wrongOrCorruptPatchIsCoveredByTheExpectedPredicate`; đổi `patchRejectedRomOrPatchAlsoLeavesOnlyACrumb` (trước đây khoá hành vi cũ: chọn nhầm vẫn sinh báo cáo).

## 5. Việc tiếp

1. Lượt có token: đọc 20 báo cáo của #393, ghi `thao tác`/`bước` vào issue; nếu rơi vào nghi phạm 3/4 thì mở PR nhỏ tương ứng.
2. Cân nhắc gộp báo cáo `op:cai-goi-engine` (nghi phạm 3) về một chỗ gọi duy nhất, hoặc dùng chung `bước` để khoá chống spam trùng nhau.
3. Chuyển 4 tên `op:` của flavor `play` vào `Diagnostics.Op` để `DiagnosticsOpTest` phủ và tên thao tác không trôi (không đổi giá trị chuỗi, chỉ gom về một nguồn).
4. Không đóng #393 cho tới khi có bảng `thao tác`/`bước`; #305/#512 giữ nguyên trạng thái đã đóng.
