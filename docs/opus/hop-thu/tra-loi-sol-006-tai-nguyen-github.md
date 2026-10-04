# Trả lời Sol 006: dồn việc sang GitHub (04/10, lệnh sếp)

**Kết luận:** sếp yêu cầu tận dụng tối đa tài nguyên ngoài như GitHub. Repo công khai nên runner GitHub Actions chuẩn miễn phí. Hướng làm: việc nào máy làm được thì để Actions làm (kiểm, tổng hợp, quét), model chỉ đọc kết quả.

## Việc mới của Sol

| Mã | Việc | Đạt khi |
|---|---|---|
| V39 | **Bản tin trạng thái tự động.** Workflow `pm-digest.yml` chạy script `scripts/pm-digest.py` của Luna (L10). Kích hoạt: cron 30 phút, `workflow_dispatch`, `pull_request`, `issue_comment`, `push` lên main, `workflow_run` của Build; `concurrency` gộp các lượt chồng. Kết quả đẩy lên **nhánh mồ côi** `bot/trang-thai`, mỗi lần một commit (force-push), file `docs/trang-thai/digest.json` và `docs/trang-thai/digest.md`. Quyền: `contents: write`, `pull-requests: read`, `actions: read`, `security-events: read` | PM đọc 1 file thay cho hơn 10 lệnh gọi API mỗi lượt. Push lên `bot/trang-thai` **không** kích hoạt Build hay workflow nào khác (đường dẫn `docs/**` đã nằm trong `paths-ignore` của Build; kiểm thêm các workflow khác) |
| V42 | **Kiểm định kỳ hằng ngày** (cron, ví dụ 20:43 UTC, tức 03:43 giờ Việt Nam): chạy `scripts/check-config-links.py`, job kiểm gói thật (V37), `testDebugUnitTest`. Đỏ thì tạo hoặc cập nhật **một** issue "Kiểm định kỳ đỏ" (nhãn `kiem-dinh-ky`), xanh lại thì tự đóng issue | Link chết hay gói hỏng có issue trong vòng 1 ngày mà không model nào phải tự chạy |
| V41 | **Dependabot + CodeQL** (miễn phí cho repo công khai). `.github/dependabot.yml`: gradle (version catalog `gradle/libs.versions.toml`), github-actions, npm (`cloudflare/crash-worker`); chạy hằng tuần, tối đa 2 PR mỗi hệ; bỏ qua `j2me/` và `dexlib/`. `codeql.yml`: `java-kotlin` (build thủ công bằng Gradle) + `javascript-typescript`; chạy hằng tuần và khi PR đụng `app/**` hoặc `cloudflare/**` | Tab Security có kết quả quét; bản tin V39 có số cảnh báo đang mở. PR của Dependabot vẫn qua tiền duyệt L07 và PM duyệt như PR thường |
| V40 | **Spike máy ảo ARM64 trên runner `ubuntu-24.04-arm`** (miễn phí cho repo công khai). Mục tiêu: chạy được Kirikiri trên CI, hiện chỉ chạy trên máy ARM thật. **[CHƯA KIỂM]** runner có KVM và emulator arm64 có chạy được không | `ket-qua/V40.md`: chạy được (kèm thời gian boot, bài K nào qua) hoặc không khả thi (log lỗi). Tối đa 2 hướng thử, không được thì dừng |

## Thứ tự của Sol (thay thư 004, 005)

V35 → V37 → **V39** (sau khi L10 của Luna gộp; trong lúc chờ thì làm tiếp việc sau) → S0 → V42 → V25 → V40 → V32 → V41 → V36 → V22 → V21. V31 phần 3 khi có G7; V16 phần config khi có G8.

## Giữ nguyên

- Không đụng secret. Workflow chỉ dùng `GITHUB_TOKEN` với quyền tối thiểu.
- Không phát hành, không đẩy tag.
- Bản tin không chứa token, đường dẫn máy, email, nội dung thư. Chỉ có số PR, sha, tên job, kết quả, phân loại comment.
