# Ghi chú phát hành 0.7.7 — ứng viên RC

**Trạng thái:** chưa phát hành và chưa có tag `v0.7.7`. Nội dung dưới đây lấy từ các merge sau `v0.7.6` tới main `9dc9774`; không phải thông báo phát hành.

## Thay đổi người dùng có thể nhận thấy

- Màn chuẩn bị Kirikiri có nút gửi báo lỗi tại chỗ khi không tìm thấy lối vào; báo cáo kèm tóm tắt từng gói XP3 và thông báo cho biết lý do gửi thất bại. ([PR #107](https://github.com/aowvn-10diem/aowvn-monika/pull/107))
- Bước kiểm lối vào Kirikiri giới hạn thời gian chờ đọc kho ở 8 giây; nếu quá hạn, ứng dụng giữ lối vào đã chọn để tiếp tục thay vì chờ vô hạn. ([PR #96](https://github.com/aowvn-10diem/aowvn-monika/pull/96))
- Khi thư mục có nhiều gói XP3, ứng dụng xử lý từng chỉ mục lần lượt thay vì giữ mọi danh sách tên cùng lúc, giảm mức bộ nhớ tăng thêm trong lượt quét đó. ([PR #142](https://github.com/aowvn-10diem/aowvn-monika/pull/142))
- Khi không tìm thấy lối vào và chỉ mục đầy đủ cho thấy các gói `.xp3` có vẻ mã hóa, ứng dụng thông báo đây là khả năng chưa xác nhận và lõi Kirikiri hiện chưa hỗ trợ loại gói đó. ([PR #144](https://github.com/aowvn-10diem/aowvn-monika/pull/144))

## Chưa kiểm

- Kirikiri mở được game/ROM thật trên thiết bị thật, đủ 5 game, âm thanh, save/load, FPS và các thao tác của checklist vẫn **[CHƯA KIỂM]**.
