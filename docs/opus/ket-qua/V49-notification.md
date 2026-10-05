# V49 — Nhóm notification, alert 24

Inventory run [37337885924](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37337885924), artifact 11357381319: alert **24**, `java/android/implicit-pendingintents`, Notifier.kt. Không coi FLAG_IMMUTABLE là đủ để chọn đích của intent implicit.

Đã đối chiếu mọi caller: bài mới/import/prefetch dùng activity Monika explicit, thông báo cài xong game dùng component launcher cụ thể của game. Downloader nhánh app/plugin truyền `Installer.installIntent` = ACTION_VIEW APK implicit + quyền đọc URI; BrowserDownloads khi lưu Downloads/SAF cũng dùng ACTION_VIEW implicit. Đây là đường thật để harden, không đóng false positive.

Notifier đưa intent implicit vào activity chuyển tiếp **không exported** của Monika. PendingIntent chỉ trỏ component explicit của activity này; sau khi người dùng bấm, activity mở đúng intent xem/cài cũ rồi kết thúc. Intent/component explicit (kể cả launcher game ngoài app) giữ đường cũ. Sao chép intent, giữ FLAG_IMMUTABLE/UPDATE_CURRENT; không bỏ quyền đọc URI ở intent được chuyển tiếp. Không nhận forwarding intent từ ứng dụng ngoài.

Giả định: activity chuyển tiếp không giao diện giữ trình chọn handler hiện có, tránh chọn handler mặc định thay người dùng (PM phản đối thì đổi). Không thay lưu dữ liệu/giao thức, không phát hành hoặc cài APK vào máy phiên.

Regression Robolectric API28/34 kiểm PendingIntent explicit, manifest không exported, giữ URI/MIME/quyền đọc khi chuyển tiếp và giữ launcher game ngoài app. Kiểm local: đọc tất cả caller, diffcheck PASS; root testDebugUnitTest thiếu SDK. Build/CodeQL đúng head và thao tác thông báo trên máy thật **[CHƯA KIỂM]**. Không tự đóng alert trước khi CodeQL xác nhận hoặc PM đánh giá.
