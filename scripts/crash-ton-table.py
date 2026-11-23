#!/usr/bin/env python3
"""Sinh bảng tồn issue `crash-moi` (docs/loi/TON-0811.md) từ snapshot issue.

Chỉ đọc dữ liệu issue bot đã ghi (số, tiêu đề, lượt quan sát, ngày). Không đọc
Worker, không suy nguyên nhân gốc: nhóm nguyên nhân ở đây suy từ `loại` trong
tiêu đề issue, còn nguyên nhân cụ thể phải lấy từ frame của Worker.

Ví dụ:
  gh issue list --label crash-moi --state open --limit 100 \
    --json number,title,body,createdAt,updatedAt,comments > issues.json
  python3 scripts/crash-ton-table.py --issues issues.json --out docs/loi/TON-0811.md
  # kèm bằng chứng V174 (tải artifact crash-insight.md của workflow):
  python3 scripts/crash-ton-table.py --issues issues.json \
    --insight docs/loi/v174-2026-11-08.md --out docs/loi/TON-0811.md
"""

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

# 12 hex đầu SHA-256 chứng thư ký APK phát hành chính thức (scripts/verify-apk-cert.py).
OFFICIAL_CERT = "c46902e97029"

# Ngày phát hành v0.8.10 (04/11/2026 UTC) — bản phát hành mới nhất. Issue cập nhật
# từ ngày này trở đi, không có bình luận, được coi là còn tái diễn sau bản mới nhất.
# (v0.8.11 đã cắt trong main nhưng chưa phát hành.)
DEFAULT_SINCE = "2026-11-05"

TITLE_RE = re.compile(r"^\[Crash\]\s+([^/]+?)\s*/\s*([^/]+?)\s*/\s*(\S+)")
COUNT_RE = re.compile(r"Số lần quan sát đã thông báo:\s*(\d+)")

# Nhóm nguyên nhân suy từ `loại` của báo cáo. Thứ tự khai báo là thứ tự in.
GROUPS = {
    "A": {
        "name": "Hệ thống giết / hết RAM / ANR",
        "types": ("killed", "lowmem", "anr"),
        "action": "Đo RAM tiến trình chính (ảnh feed/cache) và RAM đỉnh của lõi; giải phóng bitmap lúc pause, onTrimMemory; đo lại sau 2 bản",
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

# Kế hoạch tới hạn 15/11. Đổi ở đây, không sửa tay trong file markdown.
PLAN = (
    "09/11 (**xong**): V174 đã chạy — 918/1007 lượt trong cửa sổ đến từ tiến trình chính "
    "(`app:chính` 833, `net` 72), lõi chỉ 89. Ưu tiên nhóm A-app (RAM ảnh feed/cache), "
    "không phải lõi giả lập.",
    "10–11/11: PR nhóm A-app (Lỗi 2): đo RAM tiến trình chính, giải phóng bitmap/feed cache, "
    "onTrimMemory; chứng minh bằng test đơn vị + số lowmem sau 2 bản.",
    "10–11/11: PR nhóm A-lõi (Lỗi 1, Lỗi 3): melondsds 78 lượt trong cửa sổ; tách báo cáo "
    "từ chứng thư lạ `1e08a903aef9` (75 lượt) trước khi kết luận.",
    "12/11: PR nhóm D: nhóm duy nhất trong cửa sổ là `op:tai-feed` (10 lượt) — sửa luồng tải feed.",
    "13/11: PR nhóm B/C (native, core-error): symbolic bằng symbols đúng BuildId; lỗi upstream thì báo upstream.",
    "14/11: nhóm E/F/G/H: đóng có lý do (người dùng thoát, lỗi bắt được, trùng) hoặc comment chờ dữ liệu.",
    "15/11: issue còn mở phải có comment lý do + hướng tiếp; báo PM tổng kết.",
)

# Ghi chú dữ liệu đã kiểm bằng chứng (không phải nguyên nhân gốc).
NOTES = {
    302: "Cửa sổ V174 chỉ có một nhóm `unknown/resource`, thành phần `app:chính` (vượt hạn mức "
         "tài nguyên) → nhiều khả năng #302 là tiến trình chính; chưa quy tuyệt đối.",
    304: "Phần lớn `unknown/lowmem` trong cửa sổ V174 là `app:chính` (833/906 lượt) → nhiều khả năng "
         "tiến trình chính bị Android tắt vì RAM; chưa quy tuyệt đối cho #304.",
    306: "Trong cửa sổ V174, 75/78 lượt `melondsds/killed` mang chứng thư `1e08a903aef9`, khác chứng thư "
         "phát hành `c46902e97029` → phải tách trước khi coi là lỗi bản chính thức [CHƯA KIỂM].",
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


def parse_insight(text):
    """Đọc bảng markdown của V174 (crash-insight.md) thành danh sách dòng.

    Chỉ nhận dòng đủ 9 cột; dòng tiêu đề/ngăn cách bị bỏ. Không đoán thêm.
    """
    rows = []
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        parts = [part.strip() for part in line.split("|")]
        if len(parts) != 11:
            continue
        sig, core, kind, hits, apps, cert, _device, component, guess = parts[1:10]
        if not hits.isdigit():
            continue
        rows.append({
            "signature": "" if sig == "—" else sig,
            "core": "" if core == "—" else core,
            "kind": "" if kind == "—" else kind,
            "hits": int(hits),
            "apps": "" if apps == "—" else apps,
            "cert": "" if cert == "—" else cert,
            "component": "" if component == "—" else component,
            "guess": "" if guess == "—" else guess,
        })
    return rows


def component_bucket(component):
    if component.startswith("engine:"):
        return "lõi"
    if component.startswith(("op:", "pack:", "app:", "net", "ra")):
        return "app / thao tác"
    return "không rõ"


def insight_lines(rows, artifact):
    by_component = {}
    for row in rows:
        key = row["component"]
        item = by_component.setdefault(key, {"hits": 0, "cores": set(), "kinds": set(), "guess": ""})
        item["hits"] += row["hits"]
        if row["core"]:
            item["cores"].add(row["core"])
        if row["kind"]:
            item["kinds"].add(row["kind"])
        item["guess"] = item["guess"] or row["guess"]
    ordered = sorted(by_component.items(), key=lambda kv: (-kv[1]["hits"], kv[0]))

    out = ["## Bằng chứng V174 (100 báo cáo gần nhất)", ""]
    out.append(f"Nguồn: `{artifact}` (workflow V174). Cửa sổ chỉ 100 báo cáo mới nhất "
               "nên **không phủ hết** 99 issue; đây là bằng chứng về thành phần, "
               "không phải kết luận nguyên nhân.")
    out.append("")
    out.append("| Thành phần | Lõi / loại | Lượt | Giả thuyết của V174 |")
    out.append("|---|---|---:|---|")
    for component, item in ordered:
        cores = ",".join(sorted(item["cores"])) or "—"
        kinds = "/".join(sorted(item["kinds"])) or "—"
        out.append(f"| `{cell(component or 'không rõ')}` | {cell(cores)} / `{cell(kinds)}` | "
                   f"{item['hits']} | {cell(item['guess'] or '[CHƯA KIỂM]')} |")
    out.append("")
    totals = {}
    for component, item in by_component.items():
        bucket = component_bucket(component)
        totals[bucket] = totals.get(bucket, 0) + item["hits"]
    out.append("- Theo nhóm thành phần: "
               + " · ".join(f"**{name}** {hits} lượt" for name, hits in sorted(totals.items(), key=lambda kv: -kv[1])) + ".")
    foreign = {}
    for row in rows:
        if row["cert"] and not row["cert"].startswith(OFFICIAL_CERT):
            foreign[row["cert"]] = foreign.get(row["cert"], 0) + row["hits"]
    if foreign:
        listed = ", ".join(f"`{cert}` ({hits} lượt)" for cert, hits in sorted(foreign.items(), key=lambda kv: -kv[1]))
        out.append(f"- **Chứng thư khác bản phát hành** ({OFFICIAL_CERT}…): {listed}. "
                   "Đây là trường client gửi lên; phải đối chiếu tay, **không** kết luận bản giả.")
    else:
        out.append(f"- Mọi báo cáo trong cửa sổ mang chứng thư phát hành `{OFFICIAL_CERT}…` hoặc không gửi chứng thư.")
    out.append("")
    return out


def build(issues, since, insight=None, artifact=""):
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
    write(f"- `Tái diễn` = issue được bot cập nhật từ {since} (sau bản phát hành mới nhất "
          "v0.8.10, 04/11) và chưa có bình luận. Đây là dấu hiệu còn tái diễn, "
          "**không** phải bằng chứng nguyên nhân.")
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
    write("## Kế hoạch 7 ngày")
    write("")
    for line in PLAN:
        write(f"- {line}")
    write("")
    if insight:
        out.extend(insight_lines(insight, artifact))
    write("## Giới hạn bằng chứng")
    write("")
    write("- Không có frame/chữ ký đầy đủ trong thân issue: bot chỉ ghi hash `lõi + loại + "
          "tối đa 3 frame đầu` (`docs/CRASH-ISSUE-BOT.md`). Muốn biết frame phải đọc Worker.")
    if insight:
        write(f"- V174 đã chạy và artifact nằm ở `{artifact}`: chỉ 100 báo cáo mới nhất, "
              "nên thành phần ở trên là bằng chứng một phần, không phải toàn bộ 99 issue.")
    else:
        write("- `/reports` từng trả HTTP 500 khi KV lỗi tạm thời (lượt V174 02/11, có PR #451 "
              "đang sửa thành 503 + tự thử lại). V174 là `workflow_dispatch`; token của phiên này "
              "không có `actions:write` nên **cần PM chạy V174 một lần** để lấy `crash-insight.md`.")
    write("- Chưa có mapping R8 của bản đang chạy và chưa có gói symbols cho từng BuildId, "
          "nên nhóm B/G chưa retrace được.")
    write("")
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--issues", required=True, help="JSON danh sách issue")
    parser.add_argument("--out", required=True, help="File markdown đầu ra")
    parser.add_argument("--since", default=DEFAULT_SINCE, help="Ngày coi là còn tái diễn")
    parser.add_argument("--insight", help="Bảng markdown V174 (crash-insight.md), tuỳ chọn")
    args = parser.parse_args()

    data = json.loads(Path(args.issues).read_text(encoding="utf-8"))
    if isinstance(data, dict) and "issues" in data:
        data = data["issues"]
    if not isinstance(data, list):
        raise SystemExit("BLOCKED: --issues phải là mảng JSON hoặc {issues: [...]}")

    insight, artifact = None, ""

    if args.insight:
        path = Path(args.insight)
        insight = parse_insight(path.read_text(encoding="utf-8"))
        artifact = str(path).replace("\\", "/")
        if not insight:
            raise SystemExit("BLOCKED: --insight không có dòng dữ liệu nào")

    text = build(data, args.since, insight, artifact)
    text += f"\n<!-- sinh {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')} -->\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8", newline="\n")
    print(f"OK: {out} ({len(data)} issue)")


if __name__ == "__main__":
    main()
