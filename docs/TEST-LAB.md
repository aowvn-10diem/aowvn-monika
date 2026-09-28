# Firebase Test Lab — robot test app trên máy thật (cloud Google)

Workflow `.github/workflows/test-lab.yml`: build APK release (có R8) → robot của Google tự bấm khắp app
trên máy thật → báo crash, chụp màn hình, quay video.
Gói miễn phí (Spark) có giới hạn số lượt test máy thật mỗi ngày.

## Cài 1 lần

1. Vào https://console.firebase.google.com → **Add project** (tạo mới hoặc chọn project Google Cloud có sẵn). Gói Spark (miễn phí) là đủ.
2. Vào https://console.cloud.google.com (chọn đúng project đó):
   - **APIs & Services → Library** → bật **Cloud Testing API** và **Cloud Tool Results API**.
   - **IAM & Admin → Service Accounts → Create service account**, tên `monika-testlab`, vai trò **Editor** → Done.
   - Bấm vào tài khoản vừa tạo → **Keys → Add key → Create new key → JSON** → tải file .json về.
3. GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**:
   - `GCP_SA_KEY` = dán **toàn bộ nội dung** file .json.
   - `GCP_PROJECT_ID` = mã project (Project ID, dạng `ten-project-12345`).
4. Xóa file .json khỏi máy (đã nằm trong GitHub Secrets).

## Chạy

GitHub → **Actions → Test Lab (máy thật trên cloud) → Run workflow**.
- Để trống ô máy → tự chọn 2 máy thật có Android mới nhất.
- Muốn chọn máy: `model=<mã máy>,version=<API>` (nhiều máy cách nhau `;`). Danh sách mã máy in ở bước "Chọn máy" của lượt chạy trước.

Kết quả: link Firebase ở **Summary** của lượt chạy; ảnh/log/video ở **Artifacts → testlab-ket-qua**.

## Giới hạn cần biết

- Máy Test Lab ở nước ngoài → **aow.vn chặn** (Cloudflare) → màn đọc bài báo lỗi mạng. Đó là bình thường; mục đích là bắt crash và lỗi giao diện.
- Robot không chơi game thật (không có file game) → phần giả lập, J2ME vẫn phải test trên máy thật: `docs/TEST-MAY-THAT.md`.
