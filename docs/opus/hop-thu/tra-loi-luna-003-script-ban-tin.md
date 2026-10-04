# Trả lời Luna 003: L10 script bản tin trạng thái (04/10)

**Kết luận:** sếp muốn dùng tối đa GitHub. Luna viết script tổng hợp trạng thái repo. Sol (V39) cho script chạy định kỳ trên GitHub Actions để PM chỉ đọc 1 file. Làm L10 **trước** L08.

## L10 — `scripts/pm-digest.py`

- Chạy bằng Python 3 chuẩn, chỉ dùng thư viện có sẵn (`urllib`, `json`). Đọc `GITHUB_TOKEN`, `GITHUB_REPOSITORY` từ biến môi trường. Gọi GitHub REST API, chỉ đọc.
- Ghi 2 file vào thư mục đưa qua tham số `--out`: `digest.json` (đầy đủ) và `digest.md` (tối đa 40 dòng, cho người đọc).
- Nội dung `digest.json`:
  - `generated_at`; `main`: sha + 10 commit gần nhất (sha 7 ký tự, tiêu đề, giờ).
  - `open_prs`: số, tiêu đề, nhánh, sha head, `mergeable_state`, kết quả từng check-run trên head (tên → kết quả), và tối đa 3 comment gần nhất đã phân loại:
    - `pm_duyet`: mở đầu "PM duyệt";
    - `pm_sua`: mở đầu "PM yêu cầu sửa";
    - `luna_tien_duyet`: kèm kết luận Đạt / Cần sửa / Cần PM xem và sha được nhắc;
    - `khac`.
  - `merged_24h`: PR gộp trong 24 giờ.
  - `branches`: các nhánh `sol/*`, `luna/*` kèm giờ commit cuối.
  - `failed_runs_24h`: workflow đỏ trên main trong 24 giờ (tên, link).
  - `releases`: 10 bản gần nhất (tag, prerelease, draft).
  - `code_scanning_open`: số cảnh báo đang mở. Không có quyền thì ghi `null`, không làm lỗi script.
- Không ghi thân comment, email, token hay đường dẫn máy. Chỉ ghi phân loại và sha.
- Test: `scripts/test-pm-digest.py` dùng dữ liệu API giả (file JSON mẫu), chạy không cần mạng. Kiểm phân loại comment và giới hạn 40 dòng của `digest.md`.

**Đạt khi:** chạy `python3 scripts/test-pm-digest.py` xanh; chạy thật với token đọc in ra đủ các mục. PR có script nên **cần "PM duyệt"**, không tự gộp.

Thứ tự của Luna: sửa HANDOFF sau #25 → **L10** → L07 (thường trực) → L08 → L09 (thường trực) + CHANGELOG mỗi tag.
