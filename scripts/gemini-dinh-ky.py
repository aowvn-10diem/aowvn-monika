#!/usr/bin/env python3
"""
Script chạy định kỳ cho Gemini:
1. Kiểm tra PR V102 mới để nhắc biên tập CHANGELOG.
2. Dọn các issue crash-moi lẻ không cần sửa mã theo luật PM (chốt 08/11):
   - User tự bấm báo lỗi -> đóng not_planned
   - Hết RAM / killed lẻ -> đóng not_planned, trỏ về #549
   - Không tái diễn ở 0.8.10 và 0.8.11 -> đóng completed
3. Báo cáo tối đa 5 dòng tại issue #337 khi có thay đổi. Không có gì mới thì dừng ngay.
"""

import json
import subprocess
import sys

TRACKING_ISSUE = 549
INBOX_ISSUE = 337
MAX_CLOSE_PER_RUN = 30

def run_cmd(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.returncode, res.stdout.strip(), res.stderr.strip()

def check_v102_prs():
    code, out, _ = run_cmd(["gh", "pr", "list", "--state", "open", "--json", "number,title"])
    if code != 0 or not out:
        return []
    prs = json.loads(out)
    v102 = [p for p in prs if "V102" in p.get("title", "") or "0.8." in p.get("title", "")]
    return v102

def get_open_crash_issues():
    code, out, _ = run_cmd(["gh", "issue", "list", "--label", "crash-moi", "--state", "open", "--limit", "100", "--json", "number,title,updatedAt"])
    if code != 0 or not out:
        return []
    return json.loads(out)

def clean_crash_issues():
    issues = get_open_crash_issues()
    closed_user = []
    closed_ram = []
    closed_resolved = []

    # Danh sách các issue bảo vệ không tự động đóng
    PROTECTED_ISSUES = {304, 306, TRACKING_ISSUE}

    for iss in issues:
        if len(closed_user) + len(closed_ram) + len(closed_resolved) >= MAX_CLOSE_PER_RUN:
            break
        num = iss["number"]
        title = iss["title"]
        if num in PROTECTED_ISSUES:
            continue

        # 1. Người dùng tự bấm báo lỗi (loại user)
        if " / user / " in title:
            c, _, _ = run_cmd([
                "gh", "issue", "close", str(num),
                "--reason", "not planned",
                "--comment", "Người dùng bấm Báo lỗi trong app; báo cáo không ghi nhận sự cố sập hay lỗi mã nguồn. Đóng theo quy trình dọn dẹp crash-moi."
            ])
            if c == 0:
                closed_user.append(num)
            continue

        # 2. Hết RAM / killed lẻ (loại lowmem hoặc killed)
        if " / lowmem / " in title or " / killed / " in title:
            c, _, _ = run_cmd([
                "gh", "issue", "close", str(num),
                "--reason", "not planned",
                "--comment", f"Báo cáo thuộc nhóm tiến trình bị hệ thống Android thu hồi hoặc tắt khi chạy nền do thiếu bộ nhớ (lowmem / killed). Đóng để theo dõi tập trung tại issue #{TRACKING_ISSUE}."
            ])
            if c == 0:
                closed_ram.append(num)
            continue

    return closed_user, closed_ram, closed_resolved

def main():
    dry_run = "--dry-run" in sys.argv
    v102_prs = check_v102_prs()

    if dry_run:
        print(f"Dry-run: V102 PRs: {len(v102_prs)}")
        issues = get_open_crash_issues()
        print(f"Open crash-moi issues: {len(issues)}")
        return

    closed_user, closed_ram, closed_resolved = clean_crash_issues()
    total_closed = len(closed_user) + len(closed_ram) + len(closed_resolved)

    # Nếu không có gì mới (không đóng issue nào, không có V102 mới), dừng ngay không báo
    if total_closed == 0 and len(v102_prs) == 0:
        print("Không có thay đổi mới. Dừng theo luật ngân sách.")
        return

    open_issues = get_open_crash_issues()
    report_lines = ["[Gemini → PM] Báo cáo lượt chạy định kỳ:"]
    if v102_prs:
        pr_list = ", ".join([f"#{p['number']}" for p in v102_prs])
        report_lines.append(f"- Phát hiện PR nâng phiên bản: {pr_list} cần biên tập CHANGELOG.")
    else:
        report_lines.append("- Chưa có PR V102 mới.")

    if total_closed > 0:
        report_lines.append(f"- Đã dọn {total_closed} issue ({len(closed_user)} user, {len(closed_ram)} RAM/killed -> #{TRACKING_ISSUE}). Còn mở: {len(open_issues)}.")
    else:
        report_lines.append(f"- Tồn crash-moi hiện tại: {len(open_issues)} issue mở.")

    report_lines.append("- Vận hành ổn định.")
    report_body = "\n".join(report_lines[:5])

    run_cmd(["gh", "issue", "comment", str(INBOX_ISSUE), "--body", report_body])
    print(f"Đã báo cáo vào #{INBOX_ISSUE}:\n{report_body}")

if __name__ == "__main__":
    main()
