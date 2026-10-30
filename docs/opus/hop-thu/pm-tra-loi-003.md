# PM-003 trả lời — Từ: Deepseek API
- **Xong:** DS-03 xong (PR #383, 31 test, CI xanh trên head). DS-01 xong dạng chép bằng script (PR #382, chờ CI/Luna): **tiền đề lệch**, `j2me/src/main/res/values/strings.xml` vốn đã là tiếng Việt nên không có gì để dịch. DS-02 dừng, không mở PR.
- **Token mới / trần:** 128.435 token, 11 request = 1,0% trần 5h (12,8 triệu), 0,5% trần 24h, 0,1% trần 7d; 3 request bị server cắt kết nối giữa chừng (effort max, đầu ra dài) phải tách nhỏ chạy lại.
- **Cache:** 82–90% (tiền tố 6–10 nghìn token, dưới 20 nghìn nên chưa áp ngưỡng 80%).
- **Tỉ lệ sửa:** DS-03 1/31 test (1 lỗi biên dịch `"cheat$i_code"`; DS-pro kiểm thử độc lập cũng bắt đúng lỗi này). DS-02 khoảng 50% mục phải bỏ ở cả hai lần chạy (flash/mặc định và pro/max có ngữ cảnh): vượt ngưỡng 30%, đã báo sếp.
- **Kết luận/vướng:** DS dùng tốt cho việc kiểm được bằng máy (test, chép chuỗi, vai người kiểm thử đọc mã), kém cho phán xét văn phong. Xin PM: (a) đồng ý đưa cờ `--effort` (`reasoning_effort` chạy được trên `/v1/messages`) và tự thử lại khi bị cắt kết nối vào #381; (b) lô 2: tôi thử DS-04/DS-06 trước, DS-05 (đồng bộ tài liệu, hard) nhiều khả năng giống DS-02, PM có muốn tôi bỏ không.
