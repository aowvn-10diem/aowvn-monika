# Trả lời Luna 005: Luna quay lại sớm, tăng việc (07/10/2026)

**Kết luận:** sếp báo Luna có hạn mức trở lại sớm hơn ngày 10/10 và yêu cầu **tăng cường độ việc** cho Luna. Thời gian tiếp quản (`tiep-quan-sol-luna-0610.md`) kết thúc từ hôm nay. Việc gấp nhất là **tiền duyệt #107 và #106**: hai PR này đã chờ hơn 12 giờ vì Nova và Haiku vắng.

## Việc của Luna
| Ưu tiên | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | L07 | **Ngay lượt đầu:** tiền duyệt **#107** (G7B, Sonnet, head `c364421`), rồi **#106** (N08, Haiku, head `dd141bd`). Dùng khuôn 5 mục trong `tra-loi-luna-002-phan-viec-moi.md`. Lấy SHA đầu PR mới nhất trước khi viết; head đã đổi thì duyệt head mới | Mỗi PR có một comment "Luna tiền duyệt (commit <sha>)" đúng head, kết luận "Đạt" hoặc "Cần sửa" kèm dòng cụ thể |
| 2 | L07 (thường trực) | Từ nay L07 phủ PR của **Sol, Sonnet, Haiku và Nova**. Nova và Haiku thôi duyệt chéo, trừ khi duyệt PR của Luna | Không PR nào chờ quá 1 lượt kiểm thư của Luna mà chưa có tiền duyệt |
| 3 | L09 | Nhận lại việc đồng bộ tài liệu từ Haiku. Đối chiếu `README.md`, `docs/opus/HANDOFF-SONNET.md`, `docs/KIEN-TRUC-tay.md` với các PR gộp từ 06/10: #86, #87, #92, #98, #103, #105; sửa chỗ nói sai | PR tài liệu nhỏ; mỗi chỗ sửa trỏ tới PR hoặc commit nguồn |
| 4 | N07 | Nhận từ Haiku: rà thông tin cũ trong `docs/*.md` và `README.md` (phiên bản 0.7.6, `configVersion` 36, gói engine, phân vai mới: Sol và Luna quay lại) | Bảng "sai → sửa → bằng chứng" trong mô tả PR |
| 5 | N04 | Nhận từ Nova (Nova chưa mở nhánh): test cho script chưa có test: `verify-apk-cert.py`, `prepare-core-pins.py`, `gen-architecture.py`, `crash-report-detail.py`, `cheats-index.py`. Dữ liệu tự sinh, không mạng, không khóa thật | Mỗi script có `scripts/test-<tên>.py` chạy xanh |
| 6 | N09 (phần Luna) | Test tăng độ phủ cho `vn.aow.monika.account` (14,6 % dòng), `achievements` (22,1 %), `community` (39,5 %). Chỉ thêm test, không sửa mã app. Lớp nào không test được mà không sửa mã thì ghi `kẹt` và gửi thư | Mỗi PR có số Kover trước/sau (`docs/opus/ket-qua/V51.md` là mốc) và tăng % dòng của gói đụng tới |

## Duyệt PR của Luna
- PR của Luna cần **Nova "Đạt" đúng head** + CI xanh + PM duyệt.
- Nova vắng quá 2 giờ thì Sonnet tiền duyệt thay.
- Luna không tự gộp PR có đụng test hoặc script; PR chỉ sửa tài liệu theo L09 thì giữ luật cũ (tự gộp sau bảng tự kiểm).

## Luật (không đổi)
- Không viết "PM duyệt". Không gộp PR của người khác. Không sửa mã app, `docs/opus/KE-HOACH.md` hay `docs/opus/pm/**`.
- Không phát hành, không đụng khóa ký, token hay Secrets.
- Số liệu phải có bằng chứng; chưa kiểm thì ghi [CHƯA KIỂM].
- Chế độ báo động ngân sách (CLAUDE.md): làm nhiều, nói ít, báo gộp 3–5 dòng.
