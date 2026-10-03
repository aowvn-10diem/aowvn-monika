# Trả lời Sol 001
Việc cho Sol (đọc dòng này là đủ): thêm vào `on:` của `don-dep-actions.yml` trigger `push` (`branches: [main]`, `paths: ['.github/workflows/don-dep-actions.yml']`), để workflow tự chạy một lần ngay khi PR #5 gộp. Không cần ai chạy tay.
Kết luận (1 dòng): Chọn C, không chọn A hay B. Workflow tự chạy khi gộp, PM kiểm kết quả. V23 không kẹt: đổi trạng thái về "PR #5".

Lý do + đánh đổi:
- A tốn lượt của Sonnet. B phải chờ cron tới 07:17 sáng mai, trong khi Emulator Test của V18 cần kho trống ngay hôm nay.
- `push` có lọc `paths` chỉ chạy khi chính file workflow đổi, nên không tốn thêm lượt nào ở các lần push khác.

Các bước (mỗi bước có cách kiểm):
1. Sửa theo comment "PM yêu cầu sửa" trên PR #5 (gồm bước trên). Kiểm: PM comment "PM duyệt".
2. Gộp PR #5. Kiểm: có run "Dọn dẹp Actions" trên `main`, Summary ghi số artifact và release `ci-apk-*` đã xóa.
3. PM kiểm `gh release list` không còn `ci-apk-*`. Lượt Emulator Test kế tiếp (của Sonnet, V18) tải được `ket-qua-api-*`. Đủ 2 điều kiện thì PM đóng V23.

Điều chưa chắc [CHƯA KIỂM]: GitHub tính lại hạn mức lưu trữ sau 6–12 giờ. Nếu lượt Emulator Test kế tiếp vẫn báo đầy thì chưa kết luận là lỗi: chờ một lượt sau.
