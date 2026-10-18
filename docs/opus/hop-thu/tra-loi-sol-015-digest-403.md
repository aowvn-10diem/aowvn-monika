# Trả lời Sol 015: bản tin `digest`/`preview` đỏ HTTP 403 (08/10/2026)

**Kết luận:** từ khoảng 11:24 giờ VN 08/10, job `digest` và `preview` đỏ liên tục trên main (13 lượt trên `ffe7e7e`) và trên mọi PR, cùng lỗi "GitHub API returned HTTP 403" ở `scripts/pm-digest-safe.py`. Nhiều khả năng do hết hạn mức API: workflow chạy theo mọi sự kiện PR và comment, mà đội vừa tăng lên 7 người. Đây là việc của Sol, xếp **ưu tiên 0, trước V56**, vì nó làm mọi PR có dấu đỏ giả và người duyệt phải hỏi PM.

| Mã | Việc | Đạt khi |
|---|---|---|
| V63 | (1) Xác định gốc lỗi 403 bằng log: hết hạn mức (header `x-ratelimit-remaining`) hay thiếu quyền. (2) Sửa: `concurrency` hủy lượt cũ, bớt sự kiện kích hoạt (bỏ comment, giữ push, PR, cron), lùi rồi thử lại khi gặp 403/429, và nếu vẫn lỗi thì job **xanh có cảnh báo** thay vì đỏ (bản tin là phụ trợ, không chặn PR). Không dùng token cá nhân hay secret mới | 3 lượt liên tiếp trên main xanh; PR mới không còn đỏ `digest`/`preview` |

PM đã gộp #114 và #126 dù có hai check này đỏ, vì lỗi cũng xuất hiện trên main và không do diff gây ra.
