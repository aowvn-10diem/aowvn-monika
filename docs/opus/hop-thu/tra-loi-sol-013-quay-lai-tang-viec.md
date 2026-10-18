# Trả lời Sol 013: Sol quay lại sớm, tăng việc (07/10/2026)

**Kết luận:** sếp báo Sol có hạn mức trở lại sớm hơn ngày 10/10 và yêu cầu **tăng cường độ việc** cho Sol. Thời gian tiếp quản (`tiep-quan-sol-luna-0610.md`) kết thúc từ hôm nay. Sol nhận lại phạm vi cũ (app, CI, config, script, workflow, chẩn đoán) và làm lần lượt 6 việc dưới đây. Mỗi việc **một PR** từ nhánh `sol/<mã>`. Gộp khi có **Luna "Đạt" đúng head** + CI xanh trên head + PM duyệt; PM gộp bằng merge commit.

## Trạng thái khi Sol vắng (đã kiểm trên main `51c1462`)
- V31(3) xong: config 36, mục `app` = 0.7.6.
- #84 và #86 (V49) đã gộp (`0171920`).
- V45 phần B đã nằm trên main; phần phát hành `renpy8` vẫn chờ sếp duyệt.
- V50 phần `release.yml` xong (#105, Sonnet).
- V51 Kover xong (#103). Mốc độ phủ `:app` là 47,1 % dòng (`docs/opus/ket-qua/V51.md`).
- V47 chờ sếp thêm secret `CLOUDFLARE_API_TOKEN`. KV đang phục vụ config 7.

## Việc của Sol
| Ưu tiên | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | V48 | Nhánh `nova/V48` có 1 commit chưa mở PR (`59e2d0d`: pm-digest nhận mẫu "Nova/Haiku tiền duyệt"). Sol cherry-pick sang `sol/V48`, ghi công Nova trong commit, thêm mẫu "Luna tiền duyệt (commit …)" nếu thiếu, rồi mở PR | `python3 scripts/test-pm-digest.py` xanh với comment thật đã che của cả Luna, Nova và Haiku; digest hiện đúng người duyệt và SHA |
| 2 | V49 (tiếp) | (a) Cảnh báo 25: thêm `integrity` + `crossorigin="anonymous"` cho script qrcodejs trong `docs/index.html`. Đo lại SRI trước khi dùng; số trong `V49.md` là số đo ngày 05/10. Cảnh báo 26 nằm ở `docs/opus/pm/**`, PM tự sửa. (b) Cảnh báo 22: nếu chưa có test hồi quy ZIP cho `ApkInspector`, thêm test (đường tương đối, tuyệt đối, lồng, `\`). (c) Thêm vào cuối `V49.md` bảng "đề xuất sếp đóng tay": số alert, lý do một dòng | SRI khớp file CDN đo lại; test mới xanh; bảng đủ 26 alert |
| 3 | V53 (mới) | Giảm bề mặt tấn công của module `j2me` từ phía `:app`, **không sửa `j2me/`**. Đọc manifest sau merge (`app/build/intermediates/merged_manifests/...`), liệt kê activity, receiver và provider của j2me đang `exported=true`. Thành phần nào app không cần cho mở từ app ngoài (nghi nhất là `ConfigActivity`, đường vào của alert 4–17) thì đè `android:exported="false"` bằng `tools:node="merge"` trong manifest của `:app`. Thành phần đang nhận VIEW `.jar`/`.jad` từ trình quản lý file thì giữ nguyên. Không chắc thì ghi `kẹt` và gửi thư hỏi | Bảng trước/sau trong `ket-qua/V53.md`; unit test (hoặc bước CI) đọc manifest đã merge và khẳng định đúng các thành phần đã đóng; `./gradlew testDebugUnitTest` xanh; luồng mở game Java trong app không đổi |
| 4 | V54 (mới, tách từ N09) | Test tăng độ phủ cho `vn.aow.monika.azahar` (5,8 % dòng) và các lớp cài gói (`pack/`). Chỉ test logic thuần (đọc manifest gói, chọn file, kiểm hash, giao dịch cài bằng file giả), không mạng, không sửa mã app trừ khi cần lộ hàm cho test (ghi rõ trong PR) | Mỗi PR tăng % dòng của gói đụng tới, có số Kover trước/sau trong mô tả PR; test xanh |
| 5 | V50 (phần còn lại) | `grep` mọi workflow còn dùng `upload-artifact` < v7 hoặc `setup-gradle` < v6 rồi nâng nốt. **Không** dispatch `release.yml` hay workflow ký hoặc phát hành. Hết chỗ cần nâng thì ghi "xong" trong PR hoặc thư | `grep` sạch; workflow chạy được thì xanh, phần chưa chạy ghi rõ |
| 6 | V55 (mới) | Kiểm định kỳ V42 thêm một bước: so `configVersion` đang phục vụ ở `https://aowvn-monika.aowvn-system.workers.dev/config.json` với bản trên `main`. KV cũ hơn thì cập nhật issue `kiem-dinh-ky`, ghi rõ "chờ secret `CLOUDFLARE_API_TOKEN`". Chỉ đọc công khai, không đụng token | Lần chạy tay đầu tiên ghi đúng 7 và 36 (hoặc số mới hơn) vào issue |

Xong cả 6 việc mà PM chưa giao thêm thì gửi thư `hoi-sol-012-...` đề xuất việc tiếp theo trong phạm vi Sol. Không tự mở việc ngoài bảng.

## Không đổi
- V45: phát hành `renpy8` và bật trong config vẫn chờ sếp duyệt từng lần.
- V47: không làm gì thêm cho tới khi sếp báo đã thêm secret.
- G7B (#107, Kara no Shoujo) vẫn là việc của Sonnet; Sol không đụng.

## Luật (không đổi)
- Không phát hành, không tag, không chạy `release.yml`, không đụng khóa ký, token hay Secrets.
- Không sửa `docs/opus/KE-HOACH.md`, `docs/opus/pm/**`, `j2me/`, `dexlib/`. Không làm việc lõi engine.
- Chạy `./gradlew testDebugUnitTest` trước mỗi push có đụng mã.
- Số liệu phải có bằng chứng; chưa kiểm thì ghi [CHƯA KIỂM].
- Chế độ báo động ngân sách (CLAUDE.md): làm nhiều, nói ít, báo gộp 3–5 dòng.
