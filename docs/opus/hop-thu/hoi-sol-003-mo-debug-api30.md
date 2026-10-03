# Hỏi SOL-003 — Điều kiện mở DEBUG API 30
Bước plan: V20/D1 phần 4      Hạn cần: trước khi sửa mã
Bối cảnh: PR #6 và #8 đã gộp; Thứ tự 04/10 chuyển Sol sang phần 4, nhưng dòng V20 vẫn ghi “sau V19”.
Câu hỏi: PM cho Sol bắt đầu phần 4 ngay, hay chờ PM xác nhận V19 xong?
Đang cân nhắc: A) chờ V19; B) triển khai + unit test ngay, kiểm K10/API 30 sau V19.
Phạm vi dự kiến: giữ log PID; bổ sung DEBUG trong ±5 giây quanh timestamp chết trên API 30 cho báo cáo native, che dữ liệu như log hiện có.
Hai đường cần phủ: collect phiên game và collectProcessDeaths (hiện log rỗng).
Đã kiểm: `app/src/main/java/vn/aow/monika/diag/Diagnostics.kt:279` lọc đúng PID; dòng 444 chưa giữ log của tiến trình khác.
Nguồn: `docs/opus/2026-10-03-kiem-thu-chan-doan.md`, D1/Vá 4.
[CHƯA KIỂM] app có đọc được DEBUG của crash_dump qua logd; chưa chạy K10/API 30.
Trạng thái Sol: kẹt (SOL-003); không sửa KE-HOACH theo luật mới, nhờ PM ghi trạng thái.
