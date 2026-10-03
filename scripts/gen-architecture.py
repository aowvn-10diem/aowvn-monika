#!/usr/bin/env python3
"""Sinh docs/KIEN-TRUC.md từ mã nguồn: bản đồ package/module, phụ thuộc giữa chúng (đếm tham chiếu thật), file lớn, điểm vào.
Chạy lại sau mỗi đợt sửa lớn:  python3 scripts/gen-architecture.py
Phần viết tay (luồng chính, nơi sửa gì) nằm trong docs/KIEN-TRUC-tay.md và được ghép vào cuối."""
import os, re, collections, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "app/src/main/java/vn/aow/monika")
PKG = "vn.aow.monika"

def pkg_of(path):
    rel = os.path.relpath(path, BASE)
    parts = rel.split(os.sep)
    return parts[0] if len(parts) > 1 else "(gốc)"

files = [os.path.join(d, f) for d, _, fs in os.walk(BASE) for f in fs if f.endswith(".kt")]
info = collections.defaultdict(lambda: {"files": 0, "lines": 0})
text = {}
for f in files:
    s = open(f, encoding="utf-8").read()
    text[f] = s
    p = pkg_of(f)
    info[p]["files"] += 1
    info[p]["lines"] += s.count("\n") + 1

pkgs = set(info)
root_names = {os.path.splitext(os.path.basename(f))[0] for f in files if pkg_of(f) == "(gốc)"}
# Tên lớp/đối tượng khai báo ở gốc (AppGraph, Prefs, ...) để nhận ra tham chiếu không có tiền tố package.
root_decl = set()
for f in files:
    if pkg_of(f) == "(gốc)":
        root_decl |= set(re.findall(r"^(?:internal\s+|private\s+)?(?:data\s+|sealed\s+|enum\s+)?(?:class|object|interface)\s+(\w+)", text[f], re.M))

edges = collections.Counter()
for f, s in text.items():
    me = pkg_of(f)
    for m in re.finditer(r"vn\.aow\.monika\.(\w+)\.", s):
        t = m.group(1)
        if t in pkgs and t != me: edges[(me, t)] += 1
    for m in re.finditer(r"vn\.aow\.monika\.(\w+)\b(?!\.)", s):
        t = m.group(1)
        if t in root_decl and me != "(gốc)": edges[(me, "(gốc)")] += 1
    if me != "(gốc)":
        for name in root_decl:
            if re.search(r"\b%s\b" % re.escape(name), s) and "import vn.aow.monika.%s" % name in s:
                pass  # đã tính qua import ở trên

# Đếm thêm: dùng AppGraph/Prefs không cần import khi nằm cùng package gốc (bỏ qua), ở package khác thì phải import → đã tính.
def w(a, b): return edges.get((a, b), 0)

module_roles = {
    ":app": "Ứng dụng Monika (toàn bộ mã Kotlin bên dưới)",
    ":libretrodroid": "LibretroDroid nhúng, chạy lõi libretro và RetroAchievements (xem `docs/LIBRETRODROID.md`)",
    ":kirikiri": "Lớp Java Kirikiri (Kirikiroid2Yuri); native và tài nguyên tải qua gói engine",
    ":rgss": "Lớp Java SDL đã đổi gói cho mkxp-z (RPG Maker XP/VX/Ace); native tải qua gói engine",
    ":j2me": "Lõi game Java: JL-Mod nhúng + chỉnh sửa Monika (xem `docs/J2ME-LOADER.md`)",
    ":dexlib": "dx của AOSP: chuyển .jar → .dex cho game Java",
    ":loader": "Bộ nạp data chạy trong game đã chỉnh (Java thuần → .dex nhúng vào assets)",
}
settings = open(os.path.join(ROOT, "settings.gradle.kts"), encoding="utf-8").read()
modules = dict.fromkeys(
    module
    for include in re.finditer(r"^\s*include\s*\(([^)]*)\)", settings, re.M)
    for module in re.findall(r"[\"'](:[^\"']+)[\"']", include.group(1))
)

lines = ["# Kiến trúc Aow Monika (tự sinh — đừng sửa tay)", "",
         "> Sinh bởi `scripts/gen-architecture.py` từ mã nguồn. Phần luồng chính / nơi sửa gì: xem `docs/KIEN-TRUC-tay.md` (viết tay, cuối tệp này).", "",
         "## 1. Module Gradle", "",
         "| Module | Vai trò |", "|---|---|"]
lines += ["| `%s` | %s |" % (module, module_roles.get(module, "(chưa có mô tả)")) for module in modules]
lines += ["",
         "## 2. Package trong `:app`", "",
         "| Package | File | Dòng | Phụ thuộc vào (số tham chiếu) | Được dùng bởi |", "|---|---:|---:|---|---|"]
for p in sorted(pkgs, key=lambda x: -info[x]["lines"]):
    out = sorted(((b, c) for (a, b), c in edges.items() if a == p), key=lambda x: -x[1])
    inn = sorted(((a, c) for (a, b), c in edges.items() if b == p), key=lambda x: -x[1])
    lines.append("| `%s` | %d | %d | %s | %s |" % (p, info[p]["files"], info[p]["lines"],
                 ", ".join("%s(%d)" % x for x in out[:6]) or "—", ", ".join("%s(%d)" % x for x in inn[:6]) or "—"))
lines += ["", "## 3. Sơ đồ phụ thuộc (mũi tên = \"gọi tới\"; chỉ vẽ cạnh ≥ 3 tham chiếu)", "", "```mermaid", "flowchart LR"]
def node(p): return p.replace("(gốc)", "root").replace("-", "_")
for p in sorted(pkgs): lines.append('  %s["%s\\n%d dòng"]' % (node(p), p, info[p]["lines"]))
for (a, b), c in sorted(edges.items(), key=lambda x: -x[1]):
    if c >= 3: lines.append("  %s -->|%d| %s" % (node(a), c, node(b)))
lines += ["```", ""]
# Vòng phụ thuộc 2 chiều (khó tách module)
both = sorted({tuple(sorted((a, b))) for (a, b) in edges if (b, a) in edges})
lines += ["## 4. Phụ thuộc hai chiều (ứng viên phải gỡ trước khi tách module)", ""]
lines += ["- `%s` ⇄ `%s` (%d / %d)" % (a, b, w(a, b), w(b, a)) for a, b in both] or ["- Không có."]
lines += ["", "## 5. File lớn nhất (ứng viên tách nhỏ)", "", "| File | Dòng |", "|---|---:|"]
big = sorted(((s.count("\n") + 1, os.path.relpath(f, ROOT)) for f, s in text.items()), reverse=True)[:12]
lines += ["| `%s` | %d |" % (p, n) for n, p in big]
man = open(os.path.join(ROOT, "app/src/main/AndroidManifest.xml"), encoding="utf-8").read()
acts = re.findall(r'<(activity|service|receiver|provider)[^>]*android:name="([^"]+)"', man)
lines += ["", "## 6. Điểm vào (AndroidManifest)", ""] + ["- %s `%s`" % (k, n) for k, n in acts]
hand = os.path.join(ROOT, "docs/KIEN-TRUC-tay.md")
if os.path.exists(hand):
    lines += ["", "---", "", open(hand, encoding="utf-8").read()]
open(os.path.join(ROOT, "docs/KIEN-TRUC.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("OK docs/KIEN-TRUC.md —", len(pkgs), "package,", sum(v["lines"] for v in info.values()), "dòng,", len(edges), "cạnh")
