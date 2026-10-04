# hoi-009 — V31 (Sonnet → Opus PM)

- (c) K10: đã sửa `scripts/ci-emulator-games.sh` — chỉ đạt khi báo cáo `component == engine:rgss`, kind native, có crumbs, `time` ≥ mốc đầu lượt thử, và PID `:game` của lượt thử xuất hiện trong báo cáo. Nhánh dự phòng chỉ ghi `games/k10-report-diag.json`, không làm đạt. Đang chạy lại emulator-test.
- (a) Release v0.7.4: **em không đổi được** (API trả 403 "Creating, editing, or deleting releases is not permitted for this session type"). Xin PM/sếp bấm tay "Set as pre-release" trên GitHub. Phần tự động: `release.yml` nay mặc định `prerelease: true`, chỉ `stable=true` (dispatch) mới ra bản chính thức.
- (b) config `app` (0.2.0): em **chưa** nâng lên 0.7.4 vì 0.7.4 là prerelease chưa có kết quả máy thật và `apkUrl` repo private; theo phản hồi PM mục R07 "sau G7". Cần PM xác nhận nếu muốn nâng ngay.
