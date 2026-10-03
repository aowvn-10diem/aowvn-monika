# Firebase Test Lab — chạy app trên máy thật (cloud Google)

Workflow `.github/workflows/test-lab.yml` dựng APK release (có R8), rồi để robot của Google tự bấm khắp app trên **máy Android thật**. Kết quả: báo crash, chụp màn hình, quay video.
Sau này (cổng G9 trong `docs/opus/KE-HOACH.md`) workflow này còn chạy các game kiểm thử tự sinh trên máy ARM thật, theo `docs/opus/2026-10-03-kiem-thu-chan-doan.md`.

Workflow cần 2 GitHub Secret: `GCP_SA_KEY` và `GCP_PROJECT_ID`. Chưa có 2 secret này thì workflow đỏ ở bước đăng nhập Google.

## Cài 1 lần (khoảng 20 phút, nên làm trên máy tính)

Bước C2 phải mở một file `.json` rồi chép toàn bộ nội dung, làm trên điện thoại rất khó.

### A. Tạo project Firebase (miễn phí)

1. Mở https://console.firebase.google.com, đăng nhập Gmail.
2. Bấm **Create a project** (hoặc **Add project**).
3. Đặt tên, ví dụ `aow-monika-test`, rồi bấm **Continue**.
4. Màn Google Analytics: **tắt** (không cần), bấm **Create project** và đợi khoảng 30 giây.
5. Lấy **Project ID** (khác với tên project):
   - bấm bánh răng ⚙ cạnh "Project Overview" → **Project settings** → tab **General**;
   - chép dòng **Project ID** (dạng `aow-monika-test-1a2b3`) để dùng ở bước C3.
6. Gói mặc định là **Spark** (miễn phí, không cần thẻ). **Không** nâng lên Blaze.

### B. Bật API và tạo tài khoản dịch vụ (Google Cloud)

Project Firebase cũng chính là một project Google Cloud, cùng Project ID.

1. Mở https://console.cloud.google.com. Ở thanh trên cùng, bấm ô chọn project và chọn đúng project vừa tạo.
2. Bật 2 API. Với mỗi link dưới đây, mở link, kiểm lại tên project ở thanh trên, rồi bấm **Enable**:
   - Cloud Testing API: https://console.cloud.google.com/apis/library/testing.googleapis.com
   - Cloud Tool Results API: https://console.cloud.google.com/apis/library/toolresults.googleapis.com
3. Tạo tài khoản dịch vụ:
   - menu ☰ → **IAM & Admin** → **Service Accounts** → **+ Create service account**;
   - Service account name: `monika-testlab`, bấm **Create and continue**;
   - ô **Select a role**: chọn **Basic → Editor**, bấm **Continue**, rồi **Done**.
   - Quyền Editor chỉ có hiệu lực trong project test này. Project này không chứa gì khác nên rủi ro thấp.
4. Tạo khóa JSON:
   - trong danh sách, bấm vào email `monika-testlab@…`;
   - tab **Keys** → **Add key** → **Create new key** → chọn **JSON** → **Create**;
   - trình duyệt tải về một file `.json`. **Giữ kín file này**: không gửi qua chat, không commit vào repo.
   - Nếu báo "Service account key creation is disabled": tài khoản đang thuộc một tổ chức Google Workspace có chặn tạo khóa. Dừng ở đây và báo PM.

### C. Đưa vào GitHub Secrets

1. Mở https://github.com/aowvn-10diem/aowvn-monika/settings/secrets/actions, bấm **New repository secret**.
2. Secret thứ nhất:
   - Name: `GCP_SA_KEY`
   - Secret: mở file `.json` bằng Notepad (Windows) hoặc TextEdit (Mac), bấm Ctrl+A rồi Ctrl+C, dán vào. Nội dung phải bắt đầu bằng `{` và kết thúc bằng `}`.
   - bấm **Add secret**.
3. Secret thứ hai: bấm **New repository secret** lần nữa.
   - Name: `GCP_PROJECT_ID`
   - Secret: Project ID đã chép ở bước A5 (không phải tên project).
   - bấm **Add secret**.
4. Xóa file `.json` khỏi máy, kể cả trong thùng rác. Bản trong GitHub Secrets là đủ.

Token của các phiên Claude **không tạo được Secrets** (API trả 403), nên bước C phải do chủ repo tự làm.

## Chạy

1. Mở https://github.com/aowvn-10diem/aowvn-monika/actions/workflows/test-lab.yml → **Run workflow** → nhánh `main`.
2. Ô máy:
   - để trống: tự chọn 2 máy thật có Android mới nhất;
   - muốn chọn máy cụ thể: ghi `model=<mã máy>,version=<API>`, nhiều máy cách nhau bằng `;`. Danh sách mã máy in ở bước "Chọn máy" của lượt chạy trước.
3. Bấm **Run workflow**. Thời gian gồm khoảng 10 phút dựng APK, cộng thời gian robot chạy trên mỗi máy (mặc định 5 phút).
4. Kết quả:
   - link Firebase nằm ở trang **Summary** của lượt chạy;
   - ảnh, log, video nằm ở **Artifacts → testlab-ket-qua**.

Chạy xong lần đầu, nhắn PM một câu "đã chạy Test Lab". PM tự đọc kết quả trên GitHub.

## Lỗi hay gặp

| Thông báo | Nguyên nhân | Cách sửa |
|---|---|---|
| Đỏ ở bước `google-github-actions/auth`, có chữ `credentials_json` | Secret `GCP_SA_KEY` trống, dán thiếu, hoặc sai tên | Làm lại bước C2. Tên phải đúng `GCP_SA_KEY` |
| `Cloud Testing API has not been used in project …` hoặc `… is disabled` | Chưa bật API, hoặc bật nhầm project | Làm lại bước B2, kiểm tên project ở thanh trên, đợi vài phút rồi chạy lại |
| `PERMISSION_DENIED` / `does not have permission` | Tài khoản dịch vụ thiếu quyền | Kiểm bước B3: vai trò **Editor**, đúng project |
| `Project … not found` | `GCP_PROJECT_ID` ghi tên project thay vì Project ID | Làm lại bước C3 |
| Báo hết hạn mức (quota) | Gói Spark giới hạn số lượt test mỗi ngày | Chạy lại hôm sau. Số lượt cụ thể xem trong Firebase → **Usage and billing** [CHƯA KIỂM] |

## Giới hạn cần biết

- Máy Test Lab ở nước ngoài, nên **aow.vn chặn** (Cloudflare) và màn đọc bài báo lỗi mạng. Đó là bình thường: mục đích là bắt crash và lỗi giao diện.
- Robot không chơi game thật (không có file game). Phần giả lập và J2ME vẫn phải thử trên máy thật theo `docs/TEST-MAY-THAT.md`, cho tới khi có bài Game Loop (G9).
- Khóa lộ ra ngoài: vào bước B4 → tab **Keys**, xóa khóa cũ, tạo khóa mới, rồi cập nhật lại `GCP_SA_KEY`.
