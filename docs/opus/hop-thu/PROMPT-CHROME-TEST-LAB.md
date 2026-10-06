Bạn là Claude in Chrome, làm việc trên trình duyệt của chủ dự án Aow Monika. Việc cần làm: bật Firebase Test Lab miễn phí cho repo `aowvn-10diem/aowvn-monika`. Tài liệu gốc: https://github.com/aowvn-10diem/aowvn-monika/blob/main/docs/TEST-LAB.md. Đọc tài liệu đó trước và làm đúng thứ tự A → B → C. Mở tab mới, không đụng các tab đang mở.

## Làm
1. **A. Tạo project Firebase** tại https://console.firebase.google.com: tên `aow-monika-test`, tắt Google Analytics, giữ gói **Spark** (miễn phí). **Không** nâng lên Blaze, không nhập thẻ thanh toán. Ghi lại **Project ID** (dạng `aow-monika-test-xxxxx`).
2. **B. Google Cloud** (https://console.cloud.google.com, chọn đúng project vừa tạo):
   - Bật **Cloud Testing API** và **Cloud Tool Results API**.
   - Tạo service account tên `monika-testlab`, vai trò **Basic → Editor**.
   - Vào tab Keys → Add key → JSON → Create. Trình duyệt tải file `.json` về máy.
   - **Không mở, không đọc, không chép nội dung file `.json`.**
3. **C. GitHub Secrets** (https://github.com/aowvn-10diem/aowvn-monika/settings/secrets/actions):
   - Tạo secret `GCP_PROJECT_ID` có giá trị là Project ID ở bước A.
   - **Dừng lại** ở secret `GCP_SA_KEY`. Báo chủ dự án tự làm: tạo secret tên `GCP_SA_KEY`, mở file `.json` vừa tải bằng Notepad, Ctrl+A, Ctrl+C, dán vào ô, bấm Add secret, rồi xóa file `.json` khỏi máy.
4. Sau khi chủ dự án báo đã thêm `GCP_SA_KEY`: mở https://github.com/aowvn-10diem/aowvn-monika/actions/workflows/test-lab.yml → Run workflow → nhánh `main`, để trống ô máy. Đợi xong rồi báo kết quả: link lượt chạy, xanh hay đỏ, dòng lỗi nếu đỏ.

## Dừng và hỏi chủ dự án nếu
- Gặp màn hình đòi thẻ thanh toán hoặc nâng gói.
- Gặp lỗi "Service account key creation is disabled".
- Trang yêu cầu đăng nhập hoặc xác minh 2 bước.
- Bất kỳ bước nào khác với tài liệu.

## Cấm
- Đọc, chép, gửi hay in nội dung khóa JSON, token hay mật khẩu.
- Sửa hay xóa secret khác trong repo. Bấm Delete trên bất cứ thứ gì.
- Đổi cài đặt repo ngoài trang Secrets.
