# Trả lời Sol 002
Việc cho Sol (đọc dòng này là đủ): chọn A. Không giao Sonnet dựng lại bản debug. Ghi giới hạn `dynamic-only` cho thư viện dựng sẵn (RAPT `librenpython.so`) vào `ket-qua/D1.md`. Sửa thêm 1 điểm trong PR #6 (xem comment trên PR) rồi PM duyệt.
Kết luận (1 dòng): Giữ đúng ELF + BuildId, không hứa tên hàm/dòng khi upstream đã cắt ký hiệu. V20 phần 2 đóng được.

Lý do + đánh đổi:
- Dựng lại RAPT có debug tốn nhiều công, mà crash trong `librenpython.so` chủ yếu là lỗi của script Ren'Py. Lỗi script đi theo đường D2 (`traceback.txt`), không cần ký hiệu native.
- RGSS, Kirikiri và Azahar do CI tự dựng từ nguồn nên vẫn có `debug`/`symtab`. Đó là các phần cần ký hiệu nhất.
- Kho chứa ZIP ký hiệu: cứ để ở release của repo Monika, kể cả sau khi công khai mã (G5). Ký hiệu chỉ là tên hàm của mã GPL đã công khai, không chứa bí mật. Không cần kho riêng tư.

Các bước (mỗi bước có cách kiểm):
1. Ghi một dòng giới hạn vào `ket-qua/D1.md`. Kiểm: có chữ `dynamic-only` kèm tên thư viện.
2. Bỏ trạng thái "kẹt (SOL-002)" của V20, ghi "PR #6 (phần 1–2); phần 3–4 sau V19".

Điều chưa chắc [CHƯA KIỂM]: mức ký hiệu thật của từng gói, chỉ biết khi chạy lại workflow dựng gói. Ghi kết quả vào `D1.md` ở lần dựng gói kế tiếp.
