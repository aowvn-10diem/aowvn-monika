#!/usr/bin/env python3
"""In ra các phiên bản đã phát hành nhưng mục trong docs/CHANGELOG.md chưa có dấu <!-- bien-tap: vX.Y.Z -->.

Dùng cho skill bien-tap-changelog. Không in gì (thoát 0) khi không còn việc.
  python3 scripts/changelog-pending.py [--from 0.8.0] [--max 3] [--changelog docs/CHANGELOG.md]
Đọc tag bằng `git ls-remote` (dự phòng: API Releases). Thoát 1 khi không đọc được cả hai: lượt chạy phải báo lỗi, không được coi là "hết việc".
"""
import argparse, json, re, subprocess, sys, urllib.request

REPO = "aowvn-10diem/aowvn-monika"


def key(tag):
    return tuple(int(x) for x in tag.lstrip("v").split("."))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from", dest="floor", default="0.8.0", help="phiên bản thấp nhất cần biên tập")
    ap.add_argument("--max", type=int, default=3, help="tối đa bao nhiêu bản mỗi lượt")
    ap.add_argument("--changelog", default="docs/CHANGELOG.md")
    a = ap.parse_args()
    # Nguồn chính: tag trên git (phiên chạy định kỳ có thể bị proxy chặn api.github.com nhưng vẫn clone được).
    # Dự phòng: API Releases. Tag dạng vX.Y.Z chỉ được đẩy khi phát hành nên dùng được thay danh sách bản.
    tags = set()
    try:
        out = subprocess.run(
            ["git", "ls-remote", "--tags", f"https://github.com/{REPO}.git", "refs/tags/v*"],
            capture_output=True, text=True, timeout=60, check=True).stdout
        tags = {m.group(1) for m in re.finditer(r"refs/tags/(v\d+\.\d+\.\d+)(?:\^\{\})?$", out, re.M)}
    except Exception as e:
        print(f"# git ls-remote lỗi ({e}), thử API Releases", file=sys.stderr)
    if not tags:
        req = urllib.request.Request(
            f"https://api.github.com/repos/{REPO}/releases?per_page=100",
            headers={"Accept": "application/vnd.github+json", "User-Agent": "monika-changelog"},
        )
        try:
            rels = json.load(urllib.request.urlopen(req, timeout=30))
        except Exception as e:  # lỗi mạng/giới hạn API
            print(f"# không đọc được danh sách bản phát hành: {e}", file=sys.stderr)
            return 1
        tags = {r["tag_name"] for r in rels if not r.get("draft") and re.fullmatch(r"v\d+\.\d+\.\d+", r["tag_name"])}
    text = open(a.changelog, encoding="utf-8").read()
    done = set(re.findall(r"<!--\s*bien-tap:\s*(v\d+\.\d+\.\d+)\s*-->", text))
    floor = key("v" + a.floor)
    tags = sorted((t for t in tags if key(t) >= floor), key=key, reverse=True)
    for t in [t for t in tags if t not in done][: a.max]:
        print(t)
    return 0


if __name__ == "__main__":
    sys.exit(main())
