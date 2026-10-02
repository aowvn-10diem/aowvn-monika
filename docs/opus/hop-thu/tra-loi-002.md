# Trả lời 002
Kết luận (1 dòng): Kênh đã thông. **Duyệt A**: giữ thứ tự giao hàng E0 → R → P (Q3 sếp đã chốt). Riêng **P0/P1 được chạy song song**, vì chỉ dùng CI và không đụng app.

Lý do + đánh đổi:
- R là đường găng. Đổi thứ tự giao hàng là việc của sếp (Q3), PM không tự đổi.
- P0 (spike) và P1 (workflow ráp gói) không sửa `:app`, nên không giành chỗ với R. Chạy sớm thì có số thật sớm (bố cục `private/`, dung lượng gói).
- P2 trở đi phải chờ R3 qua Emulator Test, vì R3 là nơi đầu tiên kiểm đường "SDL + nạp `.so` từ gói + `:game`" (phương án câu 5, mục 2.6).

Các bước (mỗi bước có cách kiểm):
1. **R0 (V01):** làm tiếp. Ngưỡng dừng là 5 nguyên nhân hỏng khác nhau; đã dùng 2, còn 3. Khi ra được `libmkxp-z.so` thì ghi bảng kích thước vào `docs/opus/ket-qua/R0.md`, rồi hỏi sếp 1 game XP để chạy thử (Q5). Kiểm: artifact có `.so` cùng bảng kích thước.
2. **R1 (V02):** áp `tra-loi-001.md`. Kiểm: các lệnh `grep`/`nm`/`strings` ghi trong thư đó.
3. **P0 (V03):** làm tiếp song song, ghi kết quả vào `docs/opus/ket-qua/P0.md`. Khi CI rảnh thì làm P1. Kiểm: bảng đường dẫn và kích thước thật.
4. Từ nay theo dõi việc ở `docs/opus/KE-HOACH.md`. Mỗi commit làm việc ghi tiền tố `[viec-<mã>]`, sửa cột "Trạng thái" của dòng mình trong cùng commit đó.
5. Mỗi lượt kiểm tra: gộp nhánh `docs/opus-tra-loi` vào `main`. Nhánh này chỉ chứa `docs/opus/**`, không cần chạy test.

Điều chưa chắc [CHƯA KIỂM]:
- Dung lượng gói `renpy8` sau khi nén (mới có số `.so` chưa nén: arm64 35,9 MB, armv7 31,4 MB). Lớn hơn 15 MB thì vẫn đúng luật mạng: app hỏi "4G hay đợi Wi-Fi".

Cần sếp quyết: không có việc mới. Các cổng của sếp đã liệt kê ở mục 4 của `KE-HOACH.md`.
