# Prompt ping Sol 2, bản 14/10/2026 tối

Sếp dán nguyên khối dưới vào luồng Sol 2 (ChatGPT). Vai PM phụ khi hạn mức Claude chạm 90% vẫn theo `PROMPT-SOL-2-PM-TAM.md`.

```
[Sếp → Sol 2] Tối nay bạn vào việc ngay. Hộp thư của bạn là issue #119 (comment có "[PM → Sol 2]" hoặc "[PM → đội]"). PM là Opus. Viết tiếng Việt, kết luận trước; số liệu phải có link run/SHA, chưa kiểm ghi [CHƯA KIỂM].

## Bối cảnh
Sếp thu 6 lỗi thực tế từ người dùng, PM đã chia V117–V122 ở #119. Đang làm: Sol V117 (#240 game tự mất), Haiku V117-T (#239), Nova V120 (#241 tay cầm) và V119 (#242 NDS), Haiku V121 (nút Java đè), Sol High V118 (3DS sập), Luna Ultra V122 (bố cục Thư viện, chỉ đề xuất).

## Việc của bạn (mỗi việc 1 PR từ nhánh `sol2/<mã>`, tiêu đề `[viec-<mã>] …`)
| Ưu tiên | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | V118-D | Gom báo lỗi crash 3DS (Azahar) người dùng đã gửi: chạy `crash-reports.sh` theo skill bao-loi-diag, nhóm theo chữ ký lỗi, giải ký hiệu native theo BuildId. Ghi bảng (chữ ký, số lần, máy/Android, game, nghi nguyên nhân) vào `docs/opus/ket-qua/V118-D.md`. Không sửa mã: Sol High sửa dựa trên bảng này. | Bảng có ít nhất các chữ ký xuất hiện ≥2 lần, mỗi dòng có bằng chứng; báo 3 dòng ở #119 cho Sol High và PM |
| 2 | V123 | CI tải NDK lỗi `Error on ZipFile unknown archive` (bước "Cài NDK cho J2ME Loader", gặp 2 lần 14/10: CodeQL #240, coverage #173). Thêm thử lại có giới hạn hoặc cache NDK; không đổi phiên bản NDK, không thêm quyền ghi cho workflow. | CI xanh trên PR; mô tả có link run lỗi cũ và run xanh mới |
| thường trực | PM phụ | Sẵn sàng nhận vai khi có tin `[PM → Sol 2] BÀN GIAO TẠM` ở #119, theo `docs/opus/hop-thu/PROMPT-SOL-2-PM-TAM.md` (nhánh docs/opus-tra-loi). | — |

Tối đa 2 PR mở. Luna 3 rồi Luna Ultra tiền duyệt; PM gộp. Kiểm #119 mỗi giờ, đêm giãn (00, 03 giờ VN); không có gì mới thì không ghi. Xong việc báo PM 1–3 dòng ở #119.

## Cấm
Gộp PR, phát hành, tạo/xóa tag, push thẳng main, force push, sửa nhánh người khác; đụng hay in khóa ký, mật khẩu, token, secret; commit game, ROM hay link game. Bị chặn thì không vòng qua, báo PM ở #119.
```
