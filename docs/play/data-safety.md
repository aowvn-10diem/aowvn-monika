# Data safety — nháp trả lời cho Play Console

> **Nháp, chờ sếp duyệt.** Các ô đánh dấu [chờ sếp] hoặc [CHƯA KIỂM] phải được xử lý trước khi nộp. Cách phân loại theo biểu mẫu Play có thể thay đổi, cần đối chiếu biểu mẫu hiện hành.

## A. Dữ liệu thu thập hoặc chia sẻ (tiếng Việt)

| Loại dữ liệu | Có thu thập? | Chia sẻ với bên thứ ba? | Bắt buộc hay tùy chọn | Ghi chú cho Play |
|---|---|---|---|---|
| Thông tin thiết bị (hãng, model, phiên bản Android, CPU, phần cứng) | Có | Không | Tự động, tắt được | Gửi kèm báo lỗi tới máy chủ AowVN |
| Nhật ký và chẩn đoán lỗi (nhật ký kỹ thuật, vệt thao tác, trạng thái máy) | Có | Không | Tự động (mặc định bật), tắt được | Đã lọc đường dẫn riêng tư, token, khóa, email |
| Tên game đang chạy khi lỗi | Có | Không | Tự động, cùng báo lỗi | Chỉ khi lỗi xảy ra trong lúc chơi |
| Mô tả và ảnh do người dùng gửi khi báo lỗi | Có | Không | Tùy chọn (người dùng chủ động gửi) | Chỉ gửi khi người dùng bấm gửi |
| Email, tên, ảnh đại diện, mã người dùng (đăng nhập Google) | Có, nếu đăng nhập | [CHƯA KIỂM] | Tùy chọn | Chờ sếp quyết (xem README) |
| Vị trí, danh bạ, ảnh và video cá nhân, âm thanh | Không | Không | — | Ứng dụng không xin các quyền này |
| Thông tin thanh toán | Không | Không | — | Ủng hộ chuyển khoản ngoài ứng dụng; chờ sếp quyết ẩn ở bản Play |
| Quảng cáo, định danh quảng cáo | Không | Không | — | Không có SDK quảng cáo |

Ghi chú về mã hóa và xóa: dữ liệu gửi đi qua HTTPS [CHƯA KIỂM: xác nhận toàn bộ endpoint đều HTTPS]. Người dùng có thể yêu cầu xóa báo lỗi [chờ sếp: kênh và thời hạn].

Phần dịch màn hình: dữ liệu chữ được gửi tới nhà cung cấp người dùng tự chọn, không qua máy chủ AowVN. Khai theo cách Play yêu cầu về dữ liệu đi qua bên thứ ba do người dùng khởi tạo. [CHƯA KIỂM: cách khai đúng]

## B. Data collected and shared (English)

| Data type | Collected? | Shared with third parties? | Required or optional | Notes for Play |
|---|---|---|---|---|
| Device information (manufacturer, model, Android version, CPU, hardware) | Yes | No | Automatic; can be turned off | Sent with error reports to AowVN's server |
| Diagnostics and logs (technical logs, action trail, device state) | Yes | No | Automatic (on by default); can be turned off | Private paths, tokens, keys, and emails are removed first |
| Name of the game running when the error occurred | Yes | No | Automatic, with error reports | Only if the error happened during play |
| Description and screenshot a user sends with a report | Yes | No | Optional (user sends it) | Sent only when the user presses send |
| Email, name, profile photo, user ID (Google sign-in) | Yes, if signed in | [UNVERIFIED] | Optional | Owner to decide (see README) |
| Location, contacts, photos and videos, audio | No | No | — | The app does not request these |
| Financial information | No | No | — | Donations are by bank transfer outside the app; owner to decide whether to hide them in the Play build |
| Advertising or ad ID | No | No | — | No ad SDK |

Encryption and deletion: data is sent over HTTPS [UNVERIFIED: confirm every endpoint]. Users can request deletion of error reports [owner to decide: channel and retention].

On-screen translation: recognised text goes to the provider the user chose, not through AowVN's server. Declare it the way Play requires for user-initiated third-party sharing. [UNVERIFIED: correct declaration]
