#!/usr/bin/env python3
"""In % độ phủ (LINE/BRANCH/INSTRUCTION) theo gói và tổng từ báo cáo XML của Kover (V51)."""
import sys, xml.etree.ElementTree as ET

def pct(el, kind):
    for c in el.findall("counter"):
        if c.get("type") == kind:
            m, c_ = int(c.get("missed")), int(c.get("covered"))
            return (100.0 * c_ / (m + c_)) if m + c_ else None, c_, m + c_
    return None, 0, 0

def fmt(el):
    out = []
    for k in ("LINE", "BRANCH", "INSTRUCTION"):
        p, c, t = pct(el, k)
        out.append(f"{k[:4].lower()} " + ("n/a" if p is None else f"{p:5.1f}% ({c}/{t})"))
    return " | ".join(out)

root = ET.parse(sys.argv[1]).getroot()
print("| Gói | Độ phủ |\n|---|---|")
for pk in root.findall("package"):
    print(f"| `{pk.get('name').replace('/', '.')}` | {fmt(pk)} |")
print(f"| **TỔNG :app** | {fmt(root)} |")
