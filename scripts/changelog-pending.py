#!/usr/bin/env python3
"""In ra các phiên bản đã phát hành nhưng mục trong docs/CHANGELOG.md chưa có dấu <!-- bien-tap: vX.Y.Z -->.

Dùng cho skill bien-tap-changelog. Không in gì (thoát 0) khi không còn việc.
  python3 scripts/changelog-pending.py [--from 0.8.0] [--max 3] [--changelog docs/CHANGELOG.md]
"Đã phát hành" = có GitHub Release đã publish (không draft). Không dùng tag git: release.yml tạo tag
trước khi build APK và tạo Release, nên tag có thể còn đó dù bản chưa phát hành.
Cần repos/{owner}/{repo}/releases của API (phiên phải gắn với repo). Không đọc được thì thoát 1:
đây là lỗi chạy, không được coi là "hết việc".
"""
import argparse, json, re, sys, urllib.request

REPO = "aowvn-10diem/aowvn-monika"
TAG = re.compile(r"v\d+\.\d+\.\d+")


def key(tag):
    return tuple(int(x) for x in tag.lstrip("v").split("."))


def published_tags(releases):
    """Tag của các bản đã publish (bỏ draft, bỏ tag không phải vX.Y.Z)."""
    return {r["tag_name"] for r in releases if not r.get("draft") and TAG.fullmatch(r.get("tag_name", ""))}


def done_tags(text):
    return set(re.findall(r"<!--\s*bien-tap:\s*(v\d+\.\d+\.\d+)\s*-->", text))


def pending(tags, text, floor="0.8.0", limit=3):
    """Phiên bản đã publish, từ `floor` trở lên, chưa có dấu biên tập; mới nhất trước."""
    low, done = key("v" + floor), done_tags(text)
    wanted = sorted((t for t in tags if key(t) >= low and t not in done), key=key, reverse=True)
    return wanted[:limit]


def fetch_releases():
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/releases?per_page=100",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "monika-changelog"},
    )
    return json.load(urllib.request.urlopen(req, timeout=30))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from", dest="floor", default="0.8.0", help="phiên bản thấp nhất cần biên tập")
    ap.add_argument("--max", type=int, default=3, help="tối đa bao nhiêu bản mỗi lượt")
    ap.add_argument("--changelog", default="docs/CHANGELOG.md")
    a = ap.parse_args()
    try:
        tags = published_tags(fetch_releases())
    except Exception as e:
        print(f"# không đọc được danh sách bản phát hành: {e}", file=sys.stderr)
        return 1
    with open(a.changelog, encoding="utf-8") as f:
        text = f.read()
    for t in pending(tags, text, a.floor, a.max):
        print(t)
    return 0


if __name__ == "__main__":
    sys.exit(main())
