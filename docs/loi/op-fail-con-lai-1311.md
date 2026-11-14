# `op-fail` còn lại: 16 issue, op nào báo sai, op nào báo thiếu

Số dòng dẫn chiếu cập nhật theo `main` `f75b2e6d`.

Ngày 13/11/2026 · nhánh `deepseek5/op-fail-triage` · nền `main` `f24ece45` (`c3a8a9c4` là tổ tiên; 5 commit docs/CI xen giữa)
Nguồn: 75 issue mở nhãn `crash-moi` (GitHub API, 13/11), lọc tiêu đề `op-fail`; thân issue; mã tại `origin/main`; PR #603, #618.

## 1. Kết luận ngắn

- Còn **16 issue `op-fail`, tổng 36 lượt**. **#393 là issue lớn nhất (20 lượt) nhưng chưa kết luận được là tái diễn**: thân issue được sửa lần cuối **05/11 16:32** (chính lần gộp +7); `updatedAt` 13/11 là do bình luận, không phải bot gộp. Bot chỉ PATCH khi `delta ≥ 5` (`scripts/crash-issue-bot.py:117-119`) nên từ 05/11 có thể còn dưới 5 báo cáo mới chưa hiện — **[CHƯA KIỂM]**. 15 issue còn lại mới 1–2 lượt.
- Chỉ **2 issue có dòng `Thành phần`**: #614 `op:va-rom`, #608 `op:tai-xuong`. **14 issue không có** ⇒ không quy được op từ dữ liệu công khai; mọi phép gán op cho 14 issue đó là suy đoán.
- Đọc mã: **6 op báo sai** (lỗi môi trường/người dùng thành `op-fail`) cộng **1 nhóm 3 tên op của flavor play**; **5 chỗ báo thiếu** (lỗi thật không thành báo cáo). Không sửa mã trong lượt này; đề xuất ở mục 8.

## 2. Bảng 16 issue

`Lượt` = “Số lần quan sát đã thông báo” trong thân issue (cộng dồn lịch sử, không phải số người). `Tái diễn?` = bot đã PATCH thân issue sau lần tạo (chỉ PATCH khi `delta ≥ 5`, `scripts/crash-issue-bot.py:117-119`). Không dùng `updatedAt` làm mốc: bình luận cũng đổi mốc này.

| Issue | Lõi | Thành phần trong thân | Lượt | Tái diễn? |
|---|---|---|---:|---|
| [#393](https://github.com/aowvn-10diem/aowvn-monika/issues/393) | unknown | — | 20 (+7) | **[CHƯA KIỂM]** (14 báo cáo; lần gộp cuối 05/11 16:32, `updatedAt` 13/11 do bình luận) |
| [#401](https://github.com/aowvn-10diem/aowvn-monika/issues/401) | unknown | — | 2 (+2) | Không (2 báo cáo ngay lần tạo) |
| [#614](https://github.com/aowvn-10diem/aowvn-monika/issues/614) | unknown | `op:va-rom` | 1 (+1) | Chưa; xem mục 5, ý 1 |
| [#608](https://github.com/aowvn-10diem/aowvn-monika/issues/608) | unknown | `op:tai-xuong` | 1 (+1) | Chưa |
| [#564](https://github.com/aowvn-10diem/aowvn-monika/issues/564) | unknown | — | 1 (+1) | Chưa |
| [#531](https://github.com/aowvn-10diem/aowvn-monika/issues/531) | unknown | — | 1 (+1) | Chưa |
| [#525](https://github.com/aowvn-10diem/aowvn-monika/issues/525) | unknown | — | 1 (+1) | Chưa |
| [#465](https://github.com/aowvn-10diem/aowvn-monika/issues/465) | gambatte | — | 1 (+1) | Chưa |
| [#464](https://github.com/aowvn-10diem/aowvn-monika/issues/464) | mgba | — | 1 (+1) | Chưa |
| [#407](https://github.com/aowvn-10diem/aowvn-monika/issues/407) | unknown | — | 1 (+1) | Chưa |
| [#395](https://github.com/aowvn-10diem/aowvn-monika/issues/395) | unknown | — | 1 (+1) | Chưa |
| [#376](https://github.com/aowvn-10diem/aowvn-monika/issues/376) | unknown | — | 1 (+1) | Chưa |
| [#371](https://github.com/aowvn-10diem/aowvn-monika/issues/371) | unknown | — | 1 (+1) | Chưa |
| [#368](https://github.com/aowvn-10diem/aowvn-monika/issues/368) | desmume | — | 1 (+1) | Chưa |
| [#366](https://github.com/aowvn-10diem/aowvn-monika/issues/366) | unknown | — | 1 (+1) | Chưa |
| [#319](https://github.com/aowvn-10diem/aowvn-monika/issues/319) | unknown | — | 1 (+1) | Chưa |

Tổng 36 lượt. Ba issue có lõi thật (`gambatte`, `mgba`, `desmume`) chứng tỏ báo cáo phát sinh **trong phiên game**; ứng viên là `mo-game` hoặc `cai-goi-engine` — **[CHƯA KIỂM]**.

## 3. Vì sao 14 issue không có `Thành phần`

- Bot ghi dòng `Thành phần` từ `report.component` (`scripts/crash-issue-bot.py:35-36,77-79,126-131`); Worker lưu và trả `component` (`cloudflare/crash-worker/worker.js:502-503,563,823`). Không có dòng đó ⇒ **mọi báo cáo trong nhóm có `component` rỗng**.
- `opFail` luôn đặt `component = "op:$name"` (`app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:496`). Vì sao báo cáo op-fail lại rỗng: **chưa biết** — khả năng là báo cáo do bản app cũ gửi trước khi trường này được lưu, hoặc hàng cũ trong Worker. **[CHƯA KIỂM]** (không có `CRASH_ADMIN_TOKEN` để đọc `/reports/<id>`).
- Hệ quả cho triage: 14 issue chỉ quy được op **sau khi** đọc `detail` của báo cáo (`thao tác`, `bước`, `mã lỗi`). Đừng suy op chỉ từ tên lõi: `op-fail` mang `session` của phiên app lúc báo (`Diagnostics.kt:40-47,493`), nên lõi `gambatte`/`mgba`/`desmume` chỉ nói báo cáo phát sinh trong phiên game, không nói op nào.

## 4. Điểm phát báo của từng op

Tên op nằm ở `Diagnostics.Op` (`Diagnostics.kt:403-411`); `op`/`opSuspend` lọc `expected` (`:435`, `:442`), `opFail` **không có** tham số `expected` trên main (`:454` — **dẫn chiếu cũ, nội dung đã đổi từ `f75b2e6d`**: trên main hiện tại `opFail` đã nhận `expected` ở `:458`).

| `op:` | Điểm phát (`tệp:dòng`) | `expected` hiện có | Đánh giá |
|---|---|---|---|
| `tai-xuong` | `BrowserDownloads.kt:184` (`tai-ve`), `:271` (`luu-file`); ném 4xx ở `:145` | không | **Sai** với 4xx/URL hỏng |
| `giai-nen` | `Importer.kt:33-38`, gọi `:62`, `:87`, `:134` | `PasswordException`, `NeedsExtractorPackException` | Đúng; còn nhiễu “file người dùng hỏng” |
| `nhap-game` | `Importer.kt:120-127` (`mo-file`, `chep-file`) | không | **Thiếu**: các nhánh copy/move ngoài `copyUri` |
| `va-rom` | `PatchFlow.kt:17,22,26,36,45,50,53,56` | không | **Sai** ở 4 chỗ chọn nhầm; #618 sửa |
| `cai-goi-engine` | `PackManager.kt:173`; `EnginePrepActivity` (github:145, play:142) | không | **Sai** với 4xx; **trùng chéo**; thiếu ở `open()` |
| `mo-game` | `RetroActivity.kt:295`, `AzaharActivity.kt:206` | `isOffline` | **Sai một phần** (ABI/native); **thiếu** phần nạp ROM sau `:343` |
| `tai-cau-hinh` | `ConfigRepository.kt:74-97`, ném HTTP ở `:107` | không | **Sai** với 4xx (deploy/CDN) |
| `tai-feed` | `FeedRepository.kt:187`; lọc ở `:196-198` | 4xx/`expected`/`isOffline` | **Đúng** từ #603 |
| `mo-lien-ket`, `chia-se-hoi-nhom` (play) | `PlayLinks.kt:19`, `:28` | không | **Sai** khi máy không có app nhận; tên ngoài `Op` |
| `doc-bao-loi` (play) | `MainActivity.kt` play:74 | không | **Sai**: việc nội bộ, không phải thao tác người dùng |

## 5. Op báo sai (lỗi môi trường/người dùng bị báo thành `op-fail`)

1. **`va-rom` — chọn nhầm ROM/bản vá (chắc chắn).** `PatchFlow.kt:17` ROM quá lớn, `:22` không mở được bản vá, `:26` sai định dạng, `:50` `PatchException` (ROM/bản vá cụt, sai cặp). Cả 4 gọi `opFail` trực tiếp; người chơi chỉ cần chọn nhầm là có báo cáo. #614 (12/11) nhiều khả năng là một trong 4 nhánh — **[CHƯA KIỂM]** vì không đọc được `bước`.
2. **`tai-xuong` — 4xx và URL hỏng.** `BrowserDownloads.kt:145` ném `IOException("Máy chủ trả lỗi ${r.code}")` cho **mọi** mã không 2xx, rồi `:184` gọi `opFail`; link chết 404/403 (host xoá file, link hết hạn) thành “thao tác lỗi”. `IllegalArgumentException` do URL sai cũng vào đây. #608 (1 lượt) có thể thuộc nhóm này — **[CHƯA KIỂM]**.
3. **`tai-cau-hinh` — 4xx chỉ khi cả hai nguồn lỗi.** `ConfigRepository.kt:78-89` thử Pages trước (`tai-pages`, `:76`), chỉ chuyển Worker (`tai-worker`, `:85`) khi Pages lỗi; `fetchText` ném ở `:107` (`check(response.isSuccessful)`). Vì vậy 4xx chỉ thành `op-fail` khi **cả Pages lẫn Worker** đều lỗi; `opSuspend` chỉ bỏ qua lỗi mạng. Ngưỡng 6 giờ chỉ áp khi lần trước **thành công**: `checkedFile` chỉ được ghi ở nhánh thành công (`:95`), nên sau một lần lỗi, mỗi lần mở app là thử lại **ngay** (chỉ `opFail` chặn trùng trong 10 phút, `Diagnostics.kt:415`).
4. **`cai-goi-engine` — 4xx + hai đường cùng báo.** `PackManager.kt:173` báo ở lần thử cuối với bước `pack:<id> (sau N lần thử)`; `EnginePrepActivity` (github:145 / play:142) báo ngay với bước `pack:<id>`. Khoá chống spam gồm cả `bước` (`Diagnostics.kt:472`), nên cùng một sự cố có thể thành **2 báo cáo**. Ngoài ra 4xx khi tải gói (link cũ) vẫn báo.
5. **`mo-game` — lỗi máy/ABI.** `AzaharActivity.kt:206` chỉ khai báo `isOffline`; `UnsatisfiedLinkError` khi native không khớp máy/ABI vào thẳng `op-fail`. Tương tự, `RetroActivity.kt:295` báo 404 lõi (config trỏ link cũ) thành “người dùng mở game lỗi”. Có ích để theo dõi, nhưng **không phải thao tác người dùng**.
6. **`giai-nen` — file người dùng hỏng (cân nhắc).** `Importer.kt:33-38` đã bỏ qua “cần mật khẩu” và “cần gói 7-Zip”, nhưng `IOException("File nén không hợp lệ…")`/`ZipException` do file hỏng vẫn báo. Đây là nhiễu hợp lý nếu muốn biết codec hỏng; nếu không, hạ thành vệt.
7. **Tên op play ngoài `Op`.** `PlayLinks.kt:19,28` và `MainActivity.kt` play:74 dùng chuỗi rời `"mo-lien-ket"`, `"chia-se-hoi-nhom"`, `"doc-bao-loi"` ⇒ không được `DiagnosticsOpTest` phủ, mỗi tên tự sinh nhóm riêng; `ActivityNotFoundException` (máy không có trình duyệt/app nhận) bị báo thành lỗi app. `doc-bao-loi` còn là việc nội bộ (đọc/gửi báo cáo), không phải thao tác người dùng.

## 6. Op báo thiếu (lỗi thật không thành `op-fail`)

1. **`nhap-game` — nhánh copy ngoài `copyUri`.** `Importer.kt:56-57` (file không phải nén), `:70` (giữ file nén sau lỗi), `:81` (gom phần rar), `:90` (chép `.monika.json`) đều không nằm trong `Diagnostics.op`. `ImportWorker.kt:42-46` không bắt ngoại lệ ⇒ lỗi đĩa/quyền làm **worker chết im lặng**, không thông báo, không báo cáo.
2. **`nhap-game` — mất file khi đổi tên.** `Importer.kt:140` bỏ qua kết quả `renameTo`, rồi `:141` xoá `.dang-giai-nen` ⇒ file giải nén xong có thể bị xoá mà không có vệt/báo cáo nào.
3. **`mo-game` — chỉ bọc bước tải lõi.** `RetroActivity.kt:295` kết thúc trước khi tạo `GLRetroView` (`:343`). Lỗi nạp ROM/lõi ở `:367-373` đi vào `recordCoreFailure` (kind khác), không phải `op-fail` ⇒ ai lọc `op:` sẽ không thấy. `AzaharActivity.kt:206` bọc `setUp()` nên phủ tốt hơn Retro.
4. **`cai-goi-engine` — mở engine không báo.** `EnginePrepActivity.kt:180-182` gọi `route?.open` ngoài mọi `op`; `ActivityNotFoundException`/`SecurityException` khi mở engine không thành báo cáo.
5. **`doc-bao-loi` — flavor github nuốt lỗi.** `MainActivity.kt` github:84-89 dùng `runCatching {}` trơ; play:74 lại bọc `op` ⇒ cùng việc, github không có dữ liệu, play tạo nhiễu.

## 7. Điểm đáng chú ý của #393 (issue lớn nhất)

- 20 lượt / 14 báo cáo; lần gộp cuối **05/11 16:32** (chính là `+7`). `updatedAt` 13/11 là do bình luận, không phải bot gộp. Vì bot chỉ PATCH khi `delta ≥ 5`, từ 05/11 có thể còn dưới 5 báo cáo mới chưa hiện ⇒ **không kết luận được đang tái diễn** — **[CHƯA KIỂM]**. Số 20 lượt cũng không phải di sản của #305/#512 (hai issue `op:tai-feed` đã đóng 11/11 bởi #603).
- Không có `Thành phần`; cùng một chữ ký gồm 14 báo cáo nghĩa là **cùng stack, khác nguồn**. Cần `detail` mới biết là op nào.
- Ứng viên theo mã (chưa kiểm): `mo-game` (`RetroActivity.kt:295`, `AzaharActivity.kt:206`) hoặc `cai-goi-engine` (`PackManager.kt:173`) vì đây là hai op có thể chạy trong phiên game; `tai-cau-hinh`/`tai-feed` khó hơn vì sẽ có `component` nếu bản app đủ mới.

## 8. Đề xuất sửa cho lượt sau (`tệp:dòng`, không gộp)

Ưu tiên theo lượng nhiễu giảm được; mỗi mục là một PR nhỏ.

1. **`BrowserDownloads.kt:145`**: thay `IOException` bằng `HttpStatusException(code)`; tại `:181-186` chỉ gọi `opFail` khi `code >= 500`, 4xx ghi `Diagnostics.crumb`. Thêm nhánh `IllegalArgumentException` (URL hỏng) → vệt. Test: 404 không sinh báo cáo, 500 sinh đúng 1.
2. **`ConfigRepository.kt:107`**: `check` → `ConfigHttpException(code)`; truyền `expected = { it is ConfigHttpException && it.code < 500 }` vào `opSuspend` (`:74`). Giữ báo cáo cho 5xx/parse để phát hiện config hỏng thật.
3. **`Diagnostics.Op` (`Diagnostics.kt:403-411`)**: thêm `OPEN_LINK`, `SHARE_POST`, `READ_CRASH`; thay 3 chuỗi ở `PlayLinks.kt:19,28`, `MainActivity.kt` play:74; thêm 3 tên vào danh sách test `DiagnosticsOpTest.kt:58`; `expected = { it is ActivityNotFoundException }` cho hai op mở/chia sẻ.
4. **`PackManager.kt:173`**: đổi bước báo cáo thành `"pack:$id"` (bỏ hậu tố “sau N lần thử”; số lần để ở vệt) để trùng khoá chống spam với `EnginePrepActivity` (github:145 / play:142) ⇒ một sự cố = một báo cáo.
5. **`Importer.kt:56-57,70,81,90`**: bọc các nhánh copy/move bằng `Diagnostics.op(Op.IMPORT, safeName)`; **`:140`** kiểm `renameTo` và báo `Op.EXTRACT`/`Op.IMPORT` khi thất bại, **không xoá** `.dang-giai-nen` nếu còn file.
6. **`RetroActivity.kt:343-373`**: hoặc bọc bước nạp game trong `Diagnostics.op(OPEN_GAME)` với `step("nap-game")`, hoặc đổi tên/ghi chú `OPEN_GAME` thành “tải lõi” để triage không đọc nhầm. Chọn một, không làm cả hai.
7. **`MainActivity.kt` github:84-89**: thay `runCatching {}` bằng `Diagnostics.op(Op.READ_CRASH)` sau khi thêm `expected` mạng, để hai flavor cùng dữ liệu.
8. **#618**: gộp trước, rồi xác nhận #614 không tái diễn. Nếu `op:va-rom` còn xuất hiện sau bản có #618, đọc `bước` để biết nhánh nào chưa được `expected`.

## 9. Cần dữ liệu để đóng 14 issue

Không có `CRASH_ADMIN_TOKEN` trong phiên này ([CHƯA KIỂM] toàn bộ op của 14 issue). Khi có token:

1. Với từng chữ ký, lấy danh sách `id` trong marker của issue rồi chạy `scripts/crash-reports.sh <id>`.
2. Đọc `thao tác`, `bước`, `mã lỗi` trong `detail`; đối chiếu bảng mục 4 để gán op.
3. Ghi kết quả vào chính issue (một bình luận/issue), không mở issue mới; chỉ mở PR sửa khi xác định được nhánh báo sai.
4. Ưu tiên #393 trước (20/36 lượt), sau đó #401.

## 10. Giới hạn

- Lượt này **docs-only**, không sửa mã, không đóng issue.
- Số lượt là số bot cộng dồn, không phải số người; phạm vi mỗi lần đọc là tối đa 100 bản ghi mới nhất của Worker.
- Các kết luận đánh dấu **[CHƯA KIỂM]** chưa có dữ liệu báo cáo gốc; không dùng chúng làm căn cứ đóng issue.
