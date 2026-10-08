# Nước rút 08/10 → 11/10/2026 (hạn chót cuối tuần)

**Kết luận:** sếp đặt hạn chót dự án vào cuối tuần này và yêu cầu tăng tốc, tăng việc mạnh từ thứ Sáu 09/10. Sol và Luna có nhiều hạn mức nhất nên được giao việc trước và nhiều nhất. Đích: **bản phát hành ổn định vào Chủ nhật 11/10**. Mỗi lần phát hành vẫn cần sếp đồng ý.

## Mốc
| Mốc | Hạn (giờ VN) | Nội dung | Ai |
|---|---|---|---|
| M1 | Thứ Năm 08/10, 23:00 | Gộp hết PR đang mở có "Đạt" (#114, #117, #118, #122, #123, #124). Sol có bản kế hoạch V56 | PM, Luna, Luna Ultra |
| M2 | Thứ Sáu 09/10, 20:00 | Bản thử RC 0.7.7: nút báo lỗi Kirikiri, lớp chặn J2ME, các test mới. Sếp duyệt rồi mới phát hành (prerelease) | Sol chuẩn bị · Luna duyệt · sếp duyệt |
| M3 | Thứ Bảy 10/10, 20:00 | Kirikiri và RGSS chạy trên máy ARM thật qua Test Lab (V56). Ren'Py 8 sẵn sàng bật (V45), chờ sếp duyệt phát hành gói | Sol, Sonnet |
| M4 | Chủ nhật 11/10, 18:00 | Bản ổn định: CHANGELOG, tài liệu, rà soát trước phát hành xong. Sếp duyệt rồi mới phát hành | Cả đội |

## Luồng làm việc (một chu kỳ mỗi giờ)
| Phút | Ai | Làm gì |
|---|---|---|
| :40 | Sol | Đẩy mã, mở PR |
| :42 | Nova | Đẩy mã, mở PR |
| :45 | Haiku | Đẩy mã, mở PR |
| :50 | Haiku-2 | Đẩy mã, mở PR |
| :55 | Luna | Tiền duyệt PR của Sol, Sonnet, Nova |
| :58 | Luna Ultra | Tiền duyệt PR của Luna, Haiku, Haiku-2 |
| :12 | PM | Kiểm rủi ro, gộp PR "Đạt" + CI xanh, giao việc tiếp |

Mỗi người tự sửa lịch kiểm của mình sang đúng phút trên (update_trigger, không tạo lịch mới). Sonnet giữ lịch hiện có.

## Giới hạn để không tắc
- Mỗi người tối đa **2 PR mở** chờ duyệt. Riêng Sol được **3 PR** vì có nhiều hạn mức.
- Người duyệt xử lý PR cũ nhất trước. "Cần sửa" phải chỉ đúng tệp, dòng và lý do.
- PR nào chỉ còn chờ người duyệt quá 2 giờ thì người dự phòng duyệt thay:
  - PR của Sol, Sonnet, Nova: Luna Ultra duyệt thay Luna.
  - PR của Haiku, Haiku-2: Luna duyệt thay Luna Ultra.
  - PR của Luna: Haiku duyệt thay Luna Ultra.
- Không ai đụng gói hay tệp của người khác. Thấy trùng việc thì dừng, báo PM.

## Check CI nào bắt buộc (PM chốt 08/10, 15:20 giờ VN)
- **Bắt buộc xanh** trên đúng head: `build`, `coverage`, `CodeQL`/`analyze`, và mọi job test hay kiểm khác của PR.
- **Không chặn**: `digest` và `preview` (bản tin cho PM). Hai job này skipped, cancelled hay đỏ đều không tính, vì V63 cố ý cho chúng bỏ qua hoặc tự hủy khi có lượt mới.
- Người duyệt ghi rõ trạng thái hai job này trong mục 1 nhưng vẫn kết luận "Đạt" nếu mọi check bắt buộc đã xanh.
