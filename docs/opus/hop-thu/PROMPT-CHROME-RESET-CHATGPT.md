# Prompt cho Claude in Chrome: dùng lượt đặt lại giới hạn ChatGPT (bản 09/10/2026)

Sếp dán nguyên khối dưới đây vào Claude in Chrome **khi PM báo "đội ChatGPT im lặng"**. Chỉ dùng **một lần**: sếp chỉ còn 1 lượt đặt lại.

```
Bạn là Claude in Chrome, làm việc trên trình duyệt của chủ dự án Aow Monika ("sếp"). Việc cần làm: kiểm tra giới hạn sử dụng ChatGPT; nếu và chỉ nếu **giới hạn hằng tuần** đã hết thì dùng lượt đặt lại.

## Làm
1. Mở https://chatgpt.com (sếp đã đăng nhập sẵn; nếu trang đòi đăng nhập thì DỪNG và báo sếp).
2. Bấm avatar ở góc dưới bên trái → Cài đặt → Mức sử dụng.
3. Đọc kỹ trang Mức sử dụng, chụp ảnh màn hình, rồi ghi lại nguyên văn các dòng về giới hạn (5 giờ, hằng tuần) và thời điểm đặt lại.
4. Quyết định:
   - **Giới hạn hằng tuần đã hết** (trang ghi rõ đã đạt hoặc hết giới hạn tuần): bấm "Đặt lại giới hạn sử dụng", chọn "Dùng lượt đặt lại", xác nhận. Chụp ảnh màn hình sau khi đặt lại.
   - **Chỉ hết giới hạn 5 giờ, hoặc giới hạn tuần còn**: KHÔNG bấm gì. Báo sếp giờ giới hạn 5 giờ đặt lại.
   - **Không rõ** (chữ khác mô tả, không thấy nút, không biết giới hạn nào đã hết): KHÔNG bấm. Báo sếp kèm ảnh.
5. Báo sếp 3 dòng: (1) trạng thái giới hạn đọc được; (2) đã bấm đặt lại hay chưa và vì sao; (3) ảnh chụp trước/sau.

## Cấm
- Bấm "Dùng lượt đặt lại" khi giới hạn hằng tuần chưa hết, hoặc bấm quá một lần.
- Đổi gói, nâng cấp, nhập thẻ thanh toán, đổi bất kỳ cài đặt nào khác.
- Đọc, chép hay gửi hội thoại, mật khẩu, token của sếp.
```

## PM phát hiện "đội ChatGPT im lặng" như thế nào
Chạy `python3 .claude/skills/pm-kiem-thu/scripts/im-lang.py`. Kết luận "im lặng" khi **cả Sol, Luna và Luna Ultra** đều không có hoạt động GitHub (commit lên nhánh `sol/`, `luna/`, `luna-ultra/` hay comment duyệt) trong **≥ 3 giờ**, mà vẫn còn việc trong hàng. Khi đó PM:
1. Thêm câu hỏi khẩn `RESET-GPT` lên trang tiến độ, kèm link prompt này.
2. Gửi thông báo lên điện thoại sếp, nếu phiên có công cụ thông báo.
3. Không tự mở trình duyệt. Phiên PM chạy trên cloud, không điều khiển được Chrome của sếp; sếp dán prompt này vào Claude in Chrome.
