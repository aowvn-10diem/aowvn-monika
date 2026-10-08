#!/usr/bin/env python3
"""Đo năng suất từng agent từ GitHub rồi ghi vào state.json (trường `productivity`, `overall`).

Chạy từ gốc repo: python3 .claude/skills/pm-kiem-thu/scripts/nang-suat.py [--since 2026-10-07T23:30:00Z]
Chỉ đọc qua `gh api repos/...` (search API bị proxy chặn). Không cần token riêng.
"""
import argparse, json, statistics, subprocess, sys, datetime

REPO = "aowvn-10diem/aowvn-monika"
STATE = "docs/opus/pm/bang-tien-do/state.json"
# tiền tố nhánh → agent (thứ tự quan trọng: luna-ultra trước luna, haiku2 trước haiku)
PREFIX = [("luna-ultra/", "luna-ultra"), ("luna/", "luna"), ("haiku2/", "haiku-2"), ("haiku/", "haiku"),
          ("sol/", "sol"), ("nova/", "nova"), ("sonnet/", "sonnet"), ("pm/", "opus"), ("docs/opus", "opus")]
# dòng đầu comment duyệt → agent duyệt
REVIEW = [("Luna Ultra", "luna-ultra"), ("Luna ", "luna"), ("Haiku tiền duyệt", "haiku"), ("Nova tiền duyệt", "nova"),
          ("Sonnet tiền duyệt", "sonnet")]
NAMES = {"sol": "Sol", "luna": "Luna", "luna-ultra": "Luna Ultra", "haiku": "Haiku", "haiku-2": "Haiku-2",
         "nova": "Nova", "sonnet": "Sonnet", "opus": "PM (Opus)"}


def gh(path):
    out = subprocess.run(["gh", "api", path], capture_output=True, text=True)
    if out.returncode:
        sys.exit(f"gh api {path} lỗi: {out.stderr.strip()[:200]}")
    return json.loads(out.stdout)


def pages(path, limit=10):
    sep = "&" if "?" in path else "?"
    for n in range(1, limit + 1):
        batch = gh(f"{path}{sep}per_page=100&page={n}")
        if not batch:
            return
        yield from batch
        if len(batch) < 100:
            return


def agent_of_branch(ref):
    for p, a in PREFIX:
        if ref.startswith(p):
            return a
    return None


def ts(s):
    return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--since", default="2026-10-07T23:30:00Z")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    since = ts(a.since)
    st = {k: {"opened": 0, "merged": 0, "open": 0, "reviews": 0, "changes": 0, "hours": []} for k in NAMES}
    pm_merges = 0
    for pr in pages(f"repos/{REPO}/pulls?state=all&sort=created&direction=desc", 6):
        created = ts(pr["created_at"])
        merged = pr.get("merged_at")
        if created < since and not (merged and ts(merged) >= since):
            continue
        who = agent_of_branch(pr["head"]["ref"])
        if merged and ts(merged) >= since:
            pm_merges += 1
        if not who:
            continue
        s = st[who]
        if created >= since:
            s["opened"] += 1
        if merged and ts(merged) >= since:
            s["merged"] += 1
            s["hours"].append((ts(merged) - created).total_seconds() / 3600)
        if pr["state"] == "open":
            s["open"] += 1
    for c in pages(f"repos/{REPO}/issues/comments?since={a.since}", 10):
        first = (c.get("body") or "").lstrip().split("\n", 1)[0]
        if "duyệt" not in first:
            continue
        for p, who in REVIEW:
            if first.startswith(p):
                st[who]["reviews"] += 1
                if "Cần sửa" in (c.get("body") or ""):
                    st[who]["changes"] += 1
                break
    agents = []
    for k, s in st.items():
        if k == "opus":
            continue
        med = round(statistics.median(s["hours"]), 1) if s["hours"] else None
        agents.append({"id": k, "name": NAMES[k], "opened": s["opened"], "merged": s["merged"], "open": s["open"],
                       "reviews": s["reviews"], "changesAsked": s["changes"], "medianHours": med})
    agents.sort(key=lambda x: (x["merged"] + x["reviews"] / 3), reverse=True)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    prod = {"since": a.since, "at": now, "pmMerges": pm_merges, "agents": agents,
            "basis": "PR theo tiền tố nhánh; lượt duyệt theo dòng đầu comment 'X tiền duyệt'/'duyệt lần hai'. Không tính commit trực tiếp hay thư."}
    with open(STATE) as f:
        state = json.load(f)
    tasks = state.get("tasks", [])
    done = sum(1 for t in tasks if t.get("status") == "xong")
    ov = state.get("overall", {})
    ov.update({"done": done, "total": len(tasks), "pct": round(100 * done / len(tasks)) if tasks else 0, "at": now})
    state["overall"] = ov
    state["productivity"] = prod
    if a.dry_run:
        print(json.dumps({"overall": ov, "productivity": prod}, ensure_ascii=False, indent=2))
        return
    with open(STATE, "w") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)
    print(f"ok: {pm_merges} PR gộp, {done}/{len(tasks)} việc xong")


if __name__ == "__main__":
    main()
