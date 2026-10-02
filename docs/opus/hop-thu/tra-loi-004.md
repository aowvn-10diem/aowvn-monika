# Trả lời 004
Kết luận (1 dòng): **Giữ hướng tạm A** cho giai đoạn phát triển (R1–R6). Song song đó **sếp xin tác giả giấy phép (C)** cho các file build. Đây là **cổng trước R7**: chưa có giấy phép thì phải làm B (viết lại script dựng) trước khi phát hành gói `rgss`.

Lý do + đánh đổi:
- A thỏa luật "không chép file không giấy phép vào repo Monika". CI chỉ clone file về để dùng, repo Monika không chứa chúng.
- Nhưng phát hành gói `rgss` là **phân phối nhị phân GPL** (lõi mkxp-z là GPL-2.0-or-later). GPLv2 yêu cầu "mã nguồn tương ứng" gồm cả **các script dùng để biên dịch**. Script của bản port (`Makefile`, `*.mk`, `get_deps.sh`) không có giấy phép, nên mặc định thuộc quyền tác giả: Monika không được tự phân phối lại. Chỉ dẫn link sang repo bên thứ ba cũng yếu, vì repo đó có thể bị xóa. (Đây là nhận định kỹ thuật, không phải tư vấn pháp lý; chốt cuối là sếp.)
- C rẻ nhất: một issue hoặc thư xin thêm giấy phép (MIT hoặc GPL) cho phần build. Tác giả đồng ý thì A thành hướng chính thức.
- B (viết lại) tốn nhiều công. Chỉ làm khi C không có kết quả, và làm **sau R6**: nếu cổng R6 rơi sang dự phòng libretro thì B thành thừa.

Các bước (mỗi bước có cách kiểm):
1. Giữ `build-rgss.yml` dạng clone-và-dựng, **ghim đúng commit** `b668e08`. Kiểm: workflow in ra commit đã dựng; mọi file build lấy từ bản clone, không file nào nằm trong repo Monika.
2. Mọi sửa đổi của Monika trên mã GPL (vá `sed` SDL, bỏ OpenSSL…) để thành bản vá trong `engines/rgss/patches/` hoặc lệnh trong workflow, có chú thích `Aow Monika:`. Kiểm: `UPSTREAM.md` liệt kê đủ.
3. Ghi vào `engines/rgss/UPSTREAM.md` một dòng "Trạng thái giấy phép build: chờ tác giả (hỏi ngày …)".
4. PM thêm cổng G8 vào `KE-HOACH.md`: "Giấy phép build file bản port mkxp-z", hạn trước R7.

Điều chưa chắc [CHƯA KIỂM]:
- Tác giả `BookerRues9` hoặc `thehatkid` có trả lời không, và trả lời sau bao lâu.
- 10 file vá Android trên mã mkxp-z có phải là sửa đổi trên mã GPL (nên tự động theo GPL) hay không: cần đối chiếu với mkxp-z gốc khi viết `patches/`.

Cần sếp quyết: G8. Sếp (hoặc người sếp giao) liên hệ tác giả bản port để xin giấy phép cho `Makefile`, `*.mk`, `get_deps.sh` (và vỏ Java nếu tiện), trước R7.
