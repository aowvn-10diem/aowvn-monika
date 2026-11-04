#!/usr/bin/env python3
"""V197: dựng issue ghim "Tiến độ Monika" từ dữ liệu GitHub, không gọi AI.

Đọc: release, PR mở (kèm kết luận duyệt trên đúng head), PR gộp 7 ngày, số issue crash-moi.
Ghi: thân 1 issue (tạo nếu chưa có). Chạy trên Actions bằng GITHUB_TOKEN; dùng --dry để in ra.
"""
import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.request

TITLE = "📊 Tiến độ Monika"
VN = dt.timezone(dt.timedelta(hours=7))
DOI = (("deepseek", "DeepSeek"), ("sol2/", "Sol 2"), ("sol/", "Sol"), ("nova/", "Nova"),
       ("luna", "Luna"), ("haiku", "Haiku"), ("claude/", "PM"), ("docs/opus", "PM"), ("dependabot", "Dependabot"))
VERDICT = re.compile(r"Kết luận:?\**:?\s*(Đạt|Cần sửa|Cần PM xem)")
HEAD = re.compile(r"(?:commit|head)\s+([0-9a-f]{7,40})", re.I)
KHONG_CHAN = ("preview", "links", "digest")  # check thông tin, đỏ không chặn gộp
NGUOI = re.compile(r"^\s*(\[Duyệt DeepSeek|Luna [0-9A-Za-z]+|\[[^\]]+\])")


def doi(branch):
    b = branch.lower()
    for k, v in DOI:
        if b.startswith(k):
            return v
    return "Khác"


def gon(title):
    """Bỏ tiền tố [viec-V123]/[bao-mat-V123] nhưng giữ mã việc ở đầu."""
    m = re.match(r"^\[(?:[a-z-]+-)?(V\d+[a-z]?)\]\s*", title)
    if m:
        return f"{m.group(1)} · {title[m.end():]}"
    return title


def cong_bat_buoc(files):
    """Các check bắt buộc phải có trên head, theo tệp PR đổi (khớp paths của build/coverage/script-tests)."""
    can = set()
    if any(not (f.startswith("docs/") or f.endswith(".md")) for f in files):
        can |= {"build", "coverage"}
    if any(f.startswith("scripts/") or f == ".github/workflows/script-tests.yml" for f in files):
        can.add("n04-tests")
    return can


def ci_trang_thai(check_runs, bat_buoc=()):
    """Trả "do", "cho" hoặc "xanh" cho các check trên head (bỏ qua preview, links, digest).

    - "do": có check thất bại hoặc quá giờ.
    - "cho": chưa có check nào, còn check đang chạy/xếp hàng, hoặc bị hủy (chưa chứng minh xanh).
    - "xanh": mọi check đã xong, không check nào đỏ/hủy.
    Tính theo danh sách loại trừ để job test mới (vd `offline`) tự được tính.
    """
    if not check_runs and not bat_buoc:
        return "cho"
    chan = [c for c in check_runs if c.get("name") not in KHONG_CHAN]
    if any(c.get("conclusion") in ("failure", "timed_out") for c in chan):
        return "do"
    if any(c.get("status") != "completed" or c.get("conclusion") in (None, "cancelled", "action_required")
           for c in chan):
        return "cho"
    if any(c.get("status") != "completed" for c in check_runs):
        return "cho"
    if set(bat_buoc) - {c.get("name") for c in chan}:
        return "cho"  # thiếu cổng bắt buộc theo phạm vi PR
    return "xanh"


def ci_do(check_runs):
    return ci_trang_thai(check_runs) == "do"


def trang_thai(pr, comments, ci, allowed=("aowvn-10diem",)):
    """Trả (nhãn, số Đạt) theo kết luận mới nhất của từng người duyệt trên đúng head.

    Chỉ tính comment do tài khoản được phép viết (các agent duyệt đăng bằng tài khoản chủ repo).
    """
    if pr.get("draft"):
        return "📝 Nháp", 0
    if pr.get("mergeable_state") == "dirty":
        return "⚠️ Xung đột với main", 0
    head = pr["head"]["sha"]
    moi = {}
    for c in comments:
        if (c.get("user") or {}).get("login") not in allowed:
            continue
        body = c.get("body") or ""
        v = VERDICT.search(body)
        h = HEAD.search(body.split("\n", 1)[0])
        if not v or not h or not head.startswith(h.group(1)[:7]):
            continue
        n = NGUOI.match(body)
        moi[n.group(1) if n else body[:24]] = v.group(1)
    dat = sum(1 for x in moi.values() if x == "Đạt")
    if any(x != "Đạt" for x in moi.values()):
        return "🔧 Cần sửa", dat
    if ci == "do":
        return "🔴 CI đỏ", dat
    if ci != "xanh":
        return f"⏳ Chờ CI ({dat}/2 Đạt)", dat
    luna = any(k.startswith("Luna") and x == "Đạt" for k, x in moi.items())
    if dat >= 2 and luna:
        return "✅ Chờ gộp", dat
    if dat >= 2:
        return "👀 Chờ Luna duyệt", dat
    return f"👀 Chờ duyệt ({dat}/2 Đạt)", dat


def dung_than(now, releases, open_rows, merged, crash_open):
    vn = now.astimezone(VN)
    out = [f"# {TITLE}", "",
           f"_Tự cập nhật mỗi giờ từ GitHub, không dùng AI. Lần cuối: {vn:%H:%M %d/%m/%Y} giờ VN._", ""]
    stable = next((r for r in releases if not r.get("prerelease") and not r.get("draft")), None)
    test = next((r for r in releases if r.get("prerelease") and not r.get("draft")), None)
    out.append("## Bản phát hành")
    for nhan, r in (("Ổn định", stable), ("Bản thử", test)):
        if r:
            d = dt.datetime.fromisoformat(r["published_at"].replace("Z", "+00:00")).astimezone(VN)
            out.append(f"- {nhan}: [{r['tag_name']}]({r['html_url']}) ({d:%d/%m})")
    if test and stable and test["published_at"] < stable["published_at"]:
        out.pop()
    out += ["", f"## Đang làm ({len(open_rows)} PR)", "", "| PR | Việc | Đội | Trạng thái |", "|---|---|---|---|"]
    for r in open_rows:
        out.append(f"| #{r['n']} | {r['t']} | {r['doi']} | {r['tt']} |")
    out += ["", "## Đã xong 7 ngày qua", ""]
    by_day = {}
    for m in merged:
        d = dt.datetime.fromisoformat(m["merged_at"].replace("Z", "+00:00")).astimezone(VN)
        by_day.setdefault(d.strftime("%d/%m"), []).append(m)
    if not by_day:
        out.append("_Chưa có PR gộp._")
    for day in sorted(by_day, key=lambda s: (s[3:], s[:2]), reverse=True):
        out.append(f"**{day}** ({len(by_day[day])})")
        for m in by_day[day]:
            out.append(f"- #{m['number']} {gon(m['title'])} · {doi(m['head']['ref'])}")
        out.append("")
    out += ["## Lỗi người dùng", f"- Issue crash-moi đang mở: **{crash_open}**", ""]
    return "\n".join(out)


class Api:
    def __init__(self, repo, token):
        self.base = f"https://api.github.com/repos/{repo}"
        self.token = token

    def call(self, path, method="GET", data=None):
        url = path if path.startswith("http") else self.base + path
        req = urllib.request.Request(url, method=method, data=None if data is None else json.dumps(data).encode())
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("Authorization", f"Bearer {self.token}")
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read() or b"null")


def thu(api, now, allowed):
    releases = api.call("/releases?per_page=10")
    prs = [p for p in api.call("/pulls?state=open&per_page=50") if not p["user"]["login"].startswith("dependabot") and p["head"]["ref"] != "docs/opus-tra-loi"]
    rows = []
    for p in sorted(prs, key=lambda p: p["number"]):
        comments = api.call(f"/issues/{p['number']}/comments?per_page=100")
        runs = api.call(f"/commits/{p['head']['sha']}/check-runs?per_page=100").get("check_runs", [])
        p = api.call(f"/pulls/{p['number']}")  # danh sách không có mergeable_state
        files = [f["filename"] for f in api.call(f"/pulls/{p['number']}/files?per_page=100")]
        tt, _ = trang_thai(p, comments, ci_trang_thai(runs, cong_bat_buoc(files)), allowed)
        rows.append({"n": p["number"], "t": gon(p["title"]).replace("|", "/"), "doi": doi(p["head"]["ref"]), "tt": tt})
    since = now - dt.timedelta(days=7)
    closed = api.call("/pulls?state=closed&sort=updated&direction=desc&per_page=100")
    merged = [p for p in closed if p.get("merged_at") and p["merged_at"] >= since.strftime("%Y-%m-%dT%H:%M:%SZ")]
    merged.sort(key=lambda p: p["merged_at"], reverse=True)
    crash = api.call("https://api.github.com/search/issues?q=" + urllib.request.quote(
        f"repo:{api.base.split('/repos/')[1]} is:issue is:open label:crash-moi") + "&per_page=1").get("total_count", 0)
    return releases, rows, merged, crash


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    repo, token = os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_TOKEN"]
    api = Api(repo, token)
    now = dt.datetime.now(dt.timezone.utc)
    allowed = tuple(x.strip() for x in os.environ.get("TIEN_DO_REVIEWERS", repo.split("/")[0]).split(",") if x.strip())
    body = dung_than(now, *thu(api, now, allowed))
    if a.dry:
        print(body)
        return 0
    mine = [i for i in api.call("/issues?state=open&creator=github-actions%5Bbot%5D&per_page=100") if i["title"] == TITLE]
    if mine:
        api.call(f"/issues/{mine[0]['number']}", "PATCH", {"body": body})
        print(f"Đã cập nhật #{mine[0]['number']}")
    else:
        n = api.call("/issues", "POST", {"title": TITLE, "body": body})["number"]
        print(f"Đã tạo #{n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
