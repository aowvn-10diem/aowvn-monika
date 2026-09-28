# Trang web phụ trợ (dán vào Blogger aow.vn)

| File | Dán vào | Dùng cho |
|---|---|---|
| `device-login.html` | Trang mới, chế độ HTML, đường dẫn `device-login` → `aow.vn/p/device-login.html` | Đăng nhập Google cho app |

## device-login: 2 chế độ

**A. App Android Aow Monika** — `?app=monika&state=<16–64 ký tự ngẫu nhiên>`
1. App mở trang bằng Chrome Custom Tab, kèm `state` ngẫu nhiên (app tự nhớ).
2. Người dùng đăng nhập Google (popup; bị chặn popup thì chuyển trang), bấm **Cho phép**.
3. Trang chuyển về `aowmonika://login#state=..&uid=..&id_token=..&refresh_token=..&name=..&email=..&photo=..`
4. App kiểm `state` khớp → lưu `refresh_token` (mã hóa), gọi Firebase REST với `id_token`
   (`https://aowvn-xemtrang.firebaseio.com/...json?auth=<id_token>`), hết hạn 1 giờ thì đổi token mới qua
   `https://securetoken.googleapis.com/v1/token?key=<apiKey>` (grant_type=refresh_token).
Token không được lưu lên database; nằm sau dấu `#` nên không gửi lên máy chủ nào.
Chỉ app có trong danh sách `APPS` của trang mới nhận được token.

**B. Desktop / Patch C#** — `?code=AOW-7842` (tài liệu tích hợp chương 2.2)
Ghi `/deviceAuth/{code}` = `{status: 'APPROVED', uid, idToken, approvedAt}` nếu mã đang `PENDING` và chưa hết hạn.

## Điều kiện phía Firebase (sếp kiểm tra)
- Authentication → Settings → Authorized domains có `www.aow.vn` (trang web đang đăng nhập được thì đã có).
- Rules cho phép người đã đăng nhập ghi `deviceAuth/{code}` (chế độ B).
