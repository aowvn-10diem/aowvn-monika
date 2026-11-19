# `unknown / op-fail` — vì sao không đọc được bước, và bản vá 19/11

Phạm vi: 12 issue `crash-moi` đang mở có **lõi `unknown` + loại `op-fail`** — #319 #366 #371 #376 #393 #395 #401 #407 #525 #531 #564 #608 (32 lượt).
Người lập: phiên con **LB3** (nhóm citra + op-fail), 19/11/2026. Số liệu lấy từ khối `<!-- aow-crash-v100:… -->` trong thân issue, **không** đọc được báo cáo gốc (phiên này không có `CRASH_ADMIN_TOKEN`).

## 1. Bảng issue (thân issue, 19/11)

| Issue | Chữ ký | Lượt | `Thành phần` trong issue | Kết luận |
|---|---|---:|---|---|
| #393 | `5de0a80e6cb8` | 20 (14 báo cáo, +7 ngày 13/11) | không có | báo cáo cũ: không biết `op:` nào → **chưa phân loại được** |
| #401 | `ecfae69e1e11` | 2 | không có | như trên, mẫu quá nhỏ |
| #608 | `b5223f983ea3` | 1 | `op:tai-xuong` | **đã chỉ ra được**: tải xuống nhận mã HTTP lỗi — vá ở mục 3 |
| #319 #366 #371 #376 #395 #407 #525 #531 #564 | — | 1 mỗi issue, 1 khoá `seen` | không có | 1 lượt / 1 máy, không có `op`, không tái diễn đủ ngưỡng để bot cập nhật |

Tổng: **32 lượt**, trong đó 20 lượt là #393. Chỉ **1/12** issue nêu được thao tác (#608). Đây là lý do cả nhóm không đóng được bằng dữ liệu: không có `op`, không có `bước`, không có mã lỗi trong thân issue.

## 2. Vì sao thân issue thiếu `op`/`bước` — đọc mã, không đoán

- Báo cáo `op-fail` **luôn** mang `component = "op:<tên>"` (`Diagnostics.kt:570` `opFail`, dòng `component = "op:$name"`), và **luôn** mang bước nếu chỗ gọi đã gọi `Diagnostics.OpScope.step()` (`Diagnostics.kt:511`).
- Dòng `Thành phần` chỉ được bot ghi từ PR #545 (09/11). 11/12 issue mở **trước** mốc đó ⇒ thiếu dòng này là chuyện của **bot cũ**, không phải app nuốt mất `op`.
- `bước` trong `detail` bị ghi `-` khi chỗ gọi không gọi `step()`. Rà toàn bộ 12 chỗ gọi `Diagnostics.op/opSuspend` trên `origin/main` (19/11):

| Tệp:dòng gọi | `step()` ngay trong khối | Ghi chú |
|---|---|---|
| `config/ConfigRepository.kt:108` | `:110` `tai-pages` | tải cấu hình |
| `runner/EnginePrepActivity.kt:183` (github) | `:184` `mo-engine` | mở core/engine |
| `runner/EnginePrepActivity.kt:180` (play) | `:181` `mo-engine` | như trên |
| `runner/RetroActivity.kt:317` | `:318` | mở game libretro |
| `azahar/AzaharActivity.kt:206` | `:207` | mở engine Azahar |
| `library/Importer.kt:36` (`extractOp`) | `:37` `giai-nen:<đuôi>` | giải nén |
| `library/Importer.kt:127` (`copyUri`) | `:128` `mo-file`, `:130` `chep-file` | nhập tệp người dùng chọn |
| `library/Importer.kt:59` (`importFile`, tệp thường) | `:60` `nhap-tep-thuong`, `:62` `chep-file` | **thêm trong PR này** |
| `play/PlayLinks.kt:21,33` | **không** | tên `mo-lien-ket`, `chia-se-hoi-nhom` nằm ngoài `Diagnostics.Op` |
| `play/ui/MainActivity.kt:74` | **không** | `doc-bao-loi`, ngoài `Op` |
| `play/MonikaApp.kt:34` | **không** | `doc-thu-vien`, ngoài `Op` |

Ba chỗ cuối là thao tác phụ của flavor `play`; chúng tạo issue riêng chứ không nằm trong nhóm này (nhóm này là tải/giải nén/nhập/mở core). Muốn gom hết phải mở `Diagnostics.Op` — tệp đang thuộc PR #686, nên lượt này **không** đụng.

## 3. Bản vá trong PR này (đo được, có test)

1. **`download/BrowserDownloads.kt`** — mọi mã HTTP ≠ 2xx đều ném `IOException` chung nên tải trúng 404 (link chết, tệp đã bị xoá) vẫn thành báo cáo `op:tai-xuong` (#608), còn người chơi chỉ thấy "Máy chủ trả lỗi 404".
   - Nay: `HttpStatusException(code)` (`:46`), ném ở `:157`; `opFail(..., expected = { it is HttpStatusException && !it.serverSide })` (`:196-200`).
   - 4xx → **chỉ ghi vệt** (chuyện của nguồn tải); 5xx → vẫn đúng **một** báo cáo (sự cố thật).
   - Câu báo cho người chơi nêu mã và việc cần làm: *"Máy chủ trả lỗi 404 — liên kết có thể đã hỏng hoặc tệp đã bị xoá"*.
2. **`library/Importer.kt`** — hai chỗ mất dữ liệu/thông tin:
   - Nhánh **tệp thường** (không phải file nén) chép vào thư mục game **không có** `Diagnostics.op`: hỏng (nguồn biến mất, hết dung lượng, thiếu quyền) thì không có báo cáo nào, chỉ một câu lỗi chung. Nay bọc `Diagnostics.op(Op.IMPORT, src.name)` với `step("nhap-tep-thuong")` / `step("chep-file")` (`:58-68`); lỗi vẫn ném lại cho giao diện như cũ.
   - **`extractInPlace`** (`:137-158`): trước đây `archive.delete()` → `forEach { renameTo }` bỏ qua kết quả → `work.deleteRecursively()`. `renameTo` hỏng là tệp vừa giải nén **bị xoá** cùng thư mục tạm, file nén cũng đã xoá ⇒ mất dữ liệu người chơi. Nay `moveOut()` chuyển trước, hỏng thì ném lỗi có `bước: chuyen-ra-thu-muc-game` và **giữ nguyên** thư mục tạm + file nén; chỉ xoá khi mọi tệp đã sang thư mục game.

Test hồi quy:

| Test | Kiểm điều gì |
|---|---|
| `app/src/test/.../download/BrowserDownloadsHttpTest.kt` | 404 → `FAILED`, câu báo có `404`, **0** báo cáo `op:tai-xuong`; 503 → **đúng 1** báo cáo |
| `app/src/test/.../library/ImporterOpStepTest.kt` | nhập tệp thường hỏng → đúng 1 báo cáo `op:nhap-game`, `detail` có `bước: chep-file` |
| `app/src/test/.../library/ImporterExtractMoveTest.kt` | `renameTo` hỏng → thư mục tạm + file nén **còn nguyên**, người chơi nhận lỗi, 1 báo cáo `op:giai-nen` có `bước: chuyen-ra-thu-muc-game`; ca thành công vẫn dọn tạm + xoá file nén |

Cả ba đỏ khi bỏ bản vá (đã kiểm ngược bằng cách chạy trước khi sửa: `ImporterOpStepTest` không có báo cáo nào; `BrowserDownloadsHttpTest` ca 404 thấy 1 báo cáo; ca `renameTo` hỏng mất tệp trong thư mục tạm).

## 4. Vì sao **chưa** đóng issue nào

- Nhóm này chưa có bản chính thức chứa bản vá (0.8.14 đã cắt trước lượt này) ⇒ theo luật A102 chưa được đóng bằng "đã vá".
- Không có `detail` của báo cáo gốc ⇒ không chứng minh được #393 (20 lượt) thuộc `op:` nào; đóng nó bây giờ là đoán.
- Chờ luật đóng issue tồn của PM. Comment soạn sẵn cho từng issue 1 lượt đã gửi kèm lượt này (không đóng).

## 5. Việc tiếp (rẻ → đắt)

1. Lượt sau có `CRASH_ADMIN_TOKEN`: `scripts/crash-reports.sh <id>` cho 14 `id` trong marker của #393, đọc `thao tác`/`bước`/`mã lỗi` — đây là issue duy nhất còn lớn.
2. Sau bản phát hành chứa PR này: nếu #608 không tăng lượt → đóng `completed`; nếu tăng, xem `detail` có `mã lỗi: HttpStatusException` và mã ≥ 500 không.
3. Mở `Diagnostics.Op` cho `mo-lien-ket`/`chia-se-hoi-nhom`/`doc-bao-loi`/`doc-thu-vien` + thêm `step()` — chỉ sau khi #686 gộp, để không tranh `Diagnostics.kt`.
4. `Importer.extractInPlace` đã vá trong PR này (giữ thư mục tạm khi `renameTo` hỏng, báo lỗi có bước). Việc còn lại: nếu PM muốn tự phục hồi thay vì báo lỗi, thêm đường **chép rồi mới xoá** cho hệ tệp không hỗ trợ đổi tên — cần quyết định về dung lượng (chép đôi một lúc).
