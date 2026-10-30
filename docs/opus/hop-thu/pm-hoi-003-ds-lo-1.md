# PM-003 · Giao lô 1 cho DS-worker (Deepseek API) · 30/10/2026

Gửi: Deepseek API. Nguồn việc: `docs/opus/KE-HOACH.md`, mục "DS-worker". Chỉ làm việc có nhãn `ds-ok` ở đó; muốn thêm việc thì hỏi PM bằng `hoi-<số>-ds-*.md`.

## #381 (hạ tầng)
- PM đã nhờ Luna 3 + Luna Ultra rà ở #119; PM ghi "PM duyệt" trên PR khi đủ Đạt.
- PM đề nghị trước khi gộp: `ds.py` từ chối gửi file nhạy cảm qua `files`/`-i` (đuôi `.jks`, `.keystore`, `.p12`, `.pem`, `.env`, tên `local.properties`, `keystore.properties`, `google-services.json`, mọi đường dẫn ngoài repo). Hiện mọi đường dẫn đều được đọc và gửi cho bên thứ ba.

## Lô 1
| Mã | Tier | Đầu ra | PR |
|---|---|---|---|
| DS-01 | simple | `j2me/src/main/res/values-vi/strings.xml` đủ 41 chuỗi thiếu | `[viec-DS-01]`, cần 2 Đạt (Luna 3 + Luna Ultra) |
| DS-02 | simple | `docs/ds/ra-soat-chuoi-ui.md` (chỉ báo cáo) | `[viec-DS-02]`, docs-only, 1 Đạt |
| DS-03 | hard | test mới trong `app/src/test` cho `ChtFormat`, `ConsoleSaveFormats` | `[viec-DS-03]`, cần 2 Đạt; CI chạy gradle thay cloud |

Lô 1 chạy được trước khi #381 gộp (dùng `ds.py` trên nhánh của bạn), miễn không commit `tools/` vào PR việc.

## Báo cáo
Trả lời ở `pm-tra-loi-003.md` (commit `[tra-loi-pm]`), tối đa 5 dòng theo mục 8 lệnh sếp: việc xong / token mới so với trần / cache % / tỉ lệ sửa / vướng. Ngưỡng dừng và báo sếp: hơn 30% đầu ra phải sửa tay, hoặc cache dưới 80% khi tiền tố đã ≥ 20 nghìn token.
