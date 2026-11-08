#!/usr/bin/env python3
"""Sinh bảng tồn issue `crash-moi` (docs/loi/TON-0811.md) từ snapshot issue.

Chỉ đọc dữ liệu issue bot đã ghi (số, tiêu đề, lượt quan sát, ngày). Không đọc
Worker, không suy nguyên nhân gốc: nhóm nguyên nhân ở đây suy từ `loại` trong
tiêu đề issue, còn nguyên nhân cụ thể phải lấy từ frame của Worker.

Ví dụ:
  gh issue list --label crash-moi --state open --limit 100 \
    --json number,title,body,createdAt,updatedAt,comments > issues.json
  python3 scripts/crash-ton-table.py --issues issues.json --out docs/loi/TON-0811.md
"""

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

# Ngày phát hành v0.8.9 (02/11/2026 UTC). Issue cập nhật từ ngày này trở đi,
# không có bình luận, được coi là còn tái diễn sau bản mới nhất.
DEFAULT_SINCE = "2026-11-03"

TITLE_RE = re.compile(r"^\[Crash\]\s+([^/]+?)\s*/\s*([^/]+?)\s*/\s*(\S+)")
COUNT_RE = re.compile(r"Số lần quan sát đã thông báo:\s*(\d+)")

# Nhóm nguyên nhân suy từ `loại` của báo cáo. Thứ tự khai báo là thứ tự in.
GROUPS = {
    "A": {
        "name": "Hệ thống giết / hết RAM / ANR",
        "types": ("killed", "lowmem", "anr"),
        "action": "Đo RAM đỉnh khi chạy lõi; giải phóng surface/bitmap lúc pause; kiểm onTrimMemory",
    },
    "B": {
        "name": "Native crash trong .so của lõi",
        "types": ("native",),
        "action": "Lấy symbols đúng BuildId rồi symbolic; lỗi nằm trong upstream thì báo upstream",
    },
    "C": {
        "name": "Lõi báo lỗi ROM/game",
        "types": ("core-error",),
        "action": "Chạy ROM mẫu trên emulator-test; ROM hỏng/không hỗ trợ thì đóng có lý do",
    },
    "D": {
        "name": "Thao tác người dùng thất bại",
        "types": ("op-fail",),
        "action": "Lấy bước + mã lỗi V112 từ Worker; sửa retry/thông điệp theo đúng bước",
    },
    "E": {
        "name": "Người dùng chủ động thoát / huỷ",
        "types": ("user",),
        "action": "Đối chiếu báo cáo; đóng not_planned kèm giải thích cho người báo",
    },
    "F": {
        "name": "Lỗi bắt được, không sập",
        "types": ("handled",),
        "action": "Kiểm tra đã có xử lý; đóng nếu là lỗi mong đợi",
    },
    "G": {
        "name": "Exception Java",
        "types": ("java",),
        "action": "Retrace bằng mapping đúng bản phát hành rồi sửa",
    },
    "H": {
        "name": "Chưa phân loại",
        "types": (),
        "action": "Chờ frame từ Worker rồi phân loại; chưa có bằng chứng thì không sửa",
    },
}

SESSIONS = (
    ("Lỗi 1", {"melondsds", "melonds", "desmume"}),
    ("Lỗi 2", {"unknown", "mgba"}),
    ("Lỗi 3", {"citra", "ppsspp"}),
)
SESSION_FALLBACK = "Lỗi 4"

# Ghi chú dữ liệu đã kiểm bằng chứng (không phải nguyên nhân gốc).
NOTES = {
    304: "Lượt 11.072, ngày 30/10 chỉ 2.048 (đúng 2^11); v0.8.8 đã chỉnh giới hạn gửi lại "
         "nên số này có thể bị thổi bởi lần gửi lặp → [CHƯA KIỂM].",
    535: "`melonds` là mã lõi riêng trong `config/monika-config.json` (cạnh `melondsds`), "
         "không phải trùng với các issue `melondsds`.",
    387: "Lõi `unknown` + `native`: bot không xác định được lõi, cần frame của Worker để quy hệ máy.",
}


def group_of(kind):
    for key, group in GROUPS.items():
        if kind in group["types"]:
            return key
    return "H"


def session_of(core):
    for name, cores in SESSIONS:
        if core in cores:
            return name
    return SESSION_FALLBACK


def field(obj, *names):
    for name in names:
        if name in obj and obj[name] is not None:
            return obj[name]
    return None


def day(value):
    if not value:
        return ""
    return str(value)[:10]


def comment_count(value):
    if value is None:
        return 0
    if isinstance(value, list):
        return len(value)
    if isinstance(value, dict):
        return int(value.get("totalCount") or value.get("total_count") or 0)
    return int(value)


def parse_issue(raw):
    title = str(field(raw, "title") or "")
    match = TITLE_RE.match(title)
    core, kind, short = ("unknown", "unknown", "")
    if match:
        core, kind, short = match.group(1), match.group(2), match.group(3)
    body = str(field(raw, "body") or "")
    count = COUNT_RE.search(body)
    return {
        "number": int(field(raw, "number") or 0),
        "core": core,
        "kind": kind,
        "short": short,
        "count": int(count.group(1)) if count else None,
        "created": day(field(raw, "createdAt", "created_at")),
        "updated": day(field(raw, "updatedAt", "updated_at")),
        "comments": comment_count(field(raw, "comments")),
    }


def cell(value):
    return str(value).replace("|", "\\|")


def build(issues, since):
    rows = [parse_issue(raw) for raw in issues]
    rows.sort(key=lambda row: (group_of(row["kind"]), -1 * (row["count"] or 0), row["number"]))
    total = sum(row["count"] or 0 for row in rows)

    out = []
    write = out.append
    write("# Tồn issue `crash-moi` — chốt 08/11/2026")
    write("")
    write("Sinh bằng `scripts/crash-ton-table.py` từ snapshot issue bot "
          "(`docs/loi/TON-0811-issues.json`). Không đoán nguyên nhân gốc.")
    write("")
    write(f"- Tổng: **{len(rows)} issue** đang mở, "
          f"**{total} lượt** quan sát đã thông báo.")
    write("- `Lượt` = số lần bot đã thông báo (không phải số người, không phải toàn bộ lịch sử).")
    write(f"- `Tái diễn` = issue được bot cập nhật từ {since} (sau v0.8.9) và chưa có bình luận. "
          "Đây là dấu hiệu còn tái diễn, **không** phải bằng chứng nguyên nhân.")
    write("- `Nhóm` suy từ `loại` trong tiêu đề issue. Nguyên nhân cụ thể của từng chữ ký "
          "phải lấy từ frame của Worker → chưa có thì ghi `[CHƯA KIỂM]`.")
    write("")
    write("## Nhóm nguyên nhân")
    write("")
    write("| Nhóm | Tên | Số issue | Lượt | Hành động |")
    write("|---|---|---:|---:|---|")
    for key, group in GROUPS.items():
        members = [row for row in rows if group_of(row["kind"]) == key]
        if not members:
            continue
        write(f"| {key} | {group['name']} | {len(members)} | "
              f"{sum(row['count'] or 0 for row in members)} | {cell(group['action'])} |")
    write("")
    write("## Phiên con phụ trách")
    write("")
    write("| Phiên | Nhóm lõi | Số issue | Lượt |")
    write("|---|---|---:|---:|")
    for name in ("Lỗi 1", "Lỗi 2", "Lỗi 3", "Lỗi 4"):
        members = [row for row in rows if session_of(row["core"]) == name]
        if not members:
            continue
        cores = sorted({row["core"] for row in members})
        write(f"| {name} | {', '.join('`' + c + '`' for c in cores)} | {len(members)} | "
              f"{sum(row['count'] or 0 for row in members)} |")
    write("")
    write("## Chi tiết từng issue")
    write("")
    write("| Issue | Lõi | Loại | Nhóm | Lượt | Mở | Tái diễn | Hành động | Phiên |")
    write("|---|---|---|---|---:|---|---|---|---|")
    for row in rows:
        key = group_of(row["kind"])
        persists = "có" if row["updated"] >= since and row["comments"] == 0 else "chưa"
        write(f"| [#{row['number']}](https://github.com/aowvn-10diem/aowvn-monika/issues/{row['number']}) "
              f"| `{cell(row['core'])}` | `{cell(row['kind'])}` | {key} | "
              f"{'' if row['count'] is None else row['count']} | {row['created']} | {persists} | "
              f"{cell(GROUPS[key]['action'])} | {session_of(row['core'])} |")
    write("")
    notes = [(row["number"], NOTES[row["number"]]) for row in rows if row["number"] in NOTES]
    if notes:
        write("## Ghi chú dữ liệu")
        write("")
        for number, note in notes:
            write(f"- #{number}: {note}")
        write("")
    write("## Giới hạn bằng chứng")
    write("")
    write("- Không có frame/chữ ký đầy đủ trong thân issue: bot chỉ ghi hash `lõi + loại + "
          "tối đa 3 frame đầu` (`docs/CRASH-ISSUE-BOT.md`). Muốn biết frame phải đọc Worker.")
    write("- Workflow V174 (`crash-insight.yml`) lần chạy gần nhất 02/11/2026 trả "
          "`BLOCKED: Worker returned HTTP 500`; token hiện có không có quyền `actions:write` "
          "nên chưa chạy lại được → mọi nguyên nhân cụ thể là `[CHƯA KIỂM]`.")
    write("- Chưa có mapping R8 của bản đang chạy và chưa có gói symbols cho từng BuildId, "
          "nên nhóm B/G chưa retrace được.")
    write("")
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--issues", required=True, help="JSON danh sách issue")
    parser.add_argument("--out", required=True, help="File markdown đầu ra")
    parser.add_argument("--since", default=DEFAULT_SINCE, help="Ngày coi là còn tái diễn")
    args = parser.parse_args()

    data = json.loads(Path(args.issues).read_text(encoding="utf-8"))
    if isinstance(data, dict) and "issues" in data:
        data = data["issues"]
    if not isinstance(data, list):
        raise SystemExit("BLOCKED: --issues phải là mảng JSON hoặc {issues: [...]}")

    text = build(data, args.since)
    text += f"\n<!-- sinh {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')} -->\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8", newline="\n")
    print(f"OK: {out} ({len(data)} issue)")


if __name__ == "__main__":
    main()
