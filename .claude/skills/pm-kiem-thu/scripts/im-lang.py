#!/usr/bin/env python3
"""Kiểm đội ChatGPT (Sol, Luna, Luna Ultra) có đang im lặng không.

In giờ hoạt động GitHub gần nhất của từng người và kết luận IM_LANG nếu cả ba im >= --gio giờ (mặc định 3).
Mã thoát: 0 = còn hoạt động, 2 = im lặng (PM thêm câu hỏi RESET-GPT, xem PROMPT-CHROME-RESET-CHATGPT.md).
"""
import argparse, datetime, json, subprocess, sys

REPO = "aowvn-10diem/aowvn-monika"
WHO = {"sol": ("sol/", ("Sol ", "[Sol")), "luna": ("luna/", ("Luna tiền duyệt", "[Luna →", "Luna review")),
       "luna-ultra": ("luna-ultra/", ("Luna Ultra", "[Luna Ultra"))}


def gh(path):
    out = subprocess.run(["gh", "api", path], capture_output=True, text=True)
    if out.returncode:
        sys.exit(f"gh api {path} lỗi: {out.stderr.strip()[:200]}")
    return json.loads(out.stdout)


def ts(s):
    return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gio", type=float, default=3.0)
    a = ap.parse_args()
    now = datetime.datetime.now(datetime.timezone.utc)
    since = (now - datetime.timedelta(hours=24)).strftime("%Y-%m-%dT%H:%M:%SZ")
    last = {k: None for k in WHO}
    seen = set()
    refs = [b["name"] for b in gh(f"repos/{REPO}/branches?per_page=100")]
    refs += [p["head"]["ref"] + "@" + p["head"]["sha"] for p in gh(f"repos/{REPO}/pulls?state=all&sort=updated&direction=desc&per_page=40")]
    for r in refs:
        name, _, sha = r.partition("@")
        for k, (pre, _) in WHO.items():
            if name.startswith(pre) and not (k == "luna" and name.startswith("luna-ultra/")):
                key = sha or name
                if key in seen:
                    continue
                seen.add(key)
                c = gh(f"repos/{REPO}/commits/{sha or name}")["commit"]["committer"]["date"]
                last[k] = max(filter(None, [last[k], ts(c)]))
    for c in gh(f"repos/{REPO}/issues/comments?since={since}&per_page=100&sort=updated&direction=desc"):
        first = (c.get("body") or "").lstrip()[:40]
        for k in ("luna-ultra", "luna", "sol"):
            if first.startswith(WHO[k][1]):
                last[k] = max(filter(None, [last[k], ts(c["created_at"])]))
                break
    quiet = True
    for k, t in last.items():
        h = (now - t).total_seconds() / 3600 if t else None
        print(f"{k}: {'không thấy trong 24 giờ' if h is None else f'{h:.1f} giờ trước'}")
        if h is not None and h < a.gio:
            quiet = False
    print("IM_LANG" if quiet else "CON_HOAT_DONG")
    sys.exit(2 if quiet else 0)


if __name__ == "__main__":
    main()
