#!/usr/bin/env python3
"""Tự kiểm cảnh báo phụ thuộc khi API Dependabot trả 403.

Đọc SBOM của GitHub dependency graph, đối chiếu cơ sở dữ liệu OSV (Maven),
in bảng gói + phiên bản + mã cảnh báo + mức để so với docs/bao-mat/PHU-THUOC-0811.md.

    gh api repos/aowvn-10diem/aowvn-monika/dependency-graph/sbom > sbom.json
    python scripts/kiem-phu-thuoc.py sbom.json

Phương án dự phòng theo docs/bao-mat/PHU-THUOC-0811.md mục 1. Không in cách
khai thác, chỉ tên gói / mã / mức.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import urllib.request

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"
BATCH = 1000
SEVERITY_ORDER = ("CRITICAL", "HIGH", "MODERATE", "LOW", "UNKNOWN")


def maven_components(sbom: dict) -> list[tuple[str, str, str]]:
    """Trả [(tên, phiên bản, purl)] cho các gói Maven trong SBOM."""
    out: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for pkg in sbom.get("sbom", {}).get("packages", []):
        purl = ""
        for ref in pkg.get("externalRefs") or []:
            locator = ref.get("referenceLocator") or ""
            if locator.startswith("pkg:maven/"):
                purl = locator
                break
        if not purl:
            continue
        name = pkg.get("name") or ""
        version = pkg.get("versionInfo") or ""
        if not name or not version or purl in seen:
            continue
        seen.add(purl)
        out.append((name, version, purl))
    return out


def _post_json(url: str, payload: dict) -> dict:
    body = json.dumps(payload).encode()
    req = urllib.request.Request(
        url, data=body, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())


def _get_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=60) as resp:
        return json.loads(resp.read().decode())


def query_osv(
    components: list[tuple[str, str, str]],
    post=_post_json,
    get=_get_json,
) -> list[tuple[str, str, str, str, str]]:
    """Trả [(tên, phiên bản, mã cảnh báo, mức, bản vá)] đã sắp xếp."""
    found: list[tuple[str, str, str, str, str]] = []
    for start in range(0, len(components), BATCH):
        chunk = components[start : start + BATCH]
        payload = {
            "queries": [{"package": {"purl": purl}} for _, _, purl in chunk]
        }
        results = post(OSV_BATCH, payload).get("results") or []
        for (name, version, purl), res in zip(chunk, results):
            for vuln in (res or {}).get("vulns") or []:
                vuln_id = vuln.get("id") or ""
                if not vuln_id:
                    continue
                detail = get(OSV_VULN + urllib.parse.quote(vuln_id))
                severity = (
                    (detail.get("database_specific") or {}).get("severity")
                    or _worst(detail.get("severity"))
                    or "UNKNOWN"
                )
                found.append(
                    (name, version, vuln_id, severity, _fixed(detail, purl))
                )
    return sorted(found, key=lambda r: (SEVERITY_ORDER.index(r[3]) if r[3] in SEVERITY_ORDER else 99, r[0]))


def _worst(entries) -> str:
    """OSV để mức CVSS thay vì nhãn; quy về nhãn gần nhất nếu cần."""
    best, best_score = "", -1.0
    for entry in entries or []:
        if (entry.get("type") or "").upper() != "CVSS_V3":
            continue
        score = _cvss_score(entry.get("score") or "")
        if score > best_score:
            best_score, best = score, _label(score)
    return best


def _cvss_score(vector: str) -> float:
    raw = vector.rsplit("/", 1)[-1]
    try:
        return float(raw)
    except ValueError:
        return -1.0


def _label(score: float) -> str:
    if score >= 9.0:
        return "CRITICAL"
    if score >= 7.0:
        return "HIGH"
    if score >= 4.0:
        return "MODERATE"
    if score > 0:
        return "LOW"
    return "UNKNOWN"


def _fixed(detail: dict, purl: str) -> str:
    """Bản vá đầu tiên cùng hệ sinh thái Maven (rỗng nếu chưa có)."""
    for affected in detail.get("affected") or []:
        pkg = affected.get("package") or {}
        if (pkg.get("ecosystem") or "").lower() != "maven":
            continue
        for rng in affected.get("ranges") or []:
            for event in rng.get("events") or []:
                if event.get("fixed"):
                    return str(event["fixed"])
    return ""


def summarize(rows: list[tuple[str, str, str, str, str]]) -> str:
    counts = {level: 0 for level in SEVERITY_ORDER}
    for row in rows:
        counts[row[3] if row[3] in counts else "UNKNOWN"] += 1
    combos = {(row[0], row[1]) for row in rows}
    codes = {row[2] for row in rows}
    head = (
        f"{len(rows)} cảnh báo / {len(combos)} tổ hợp / {len(codes)} mã — "
        + ", ".join(f"{counts[lvl]} {lvl.lower()}" for lvl in SEVERITY_ORDER if counts[lvl])
    )
    lines = [head, "", "| Gói | Phiên bản | Mã | Mức | Bản vá |", "|---|---|---|---|---|"]
    for name, version, code, severity, fixed in rows:
        lines.append(f"| `{name}` | {version} | {code} | {severity} | {fixed} |")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("sbom", help="tệp JSON SBOM tải bằng gh api .../dependency-graph/sbom")
    ap.add_argument("--codes", help="tệp một mã cảnh báo mỗi dòng để chỉ in mã ngoài danh sách")
    args = ap.parse_args(argv)

    # Bảng mã Windows mặc định là cp1252, không in được tiếng Việt.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    try:
        # Đọc bytes: json.loads tự nhận UTF-8/UTF-16, cần vì `gh api > tệp` trên
        # PowerShell ghi kèm BOM UTF-16.
        with open(args.sbom, "rb") as fh:
            sbom = json.loads(fh.read())
    except (OSError, json.JSONDecodeError) as exc:
        print(f"không đọc được SBOM {args.sbom}: {exc}", file=sys.stderr)
        return 2

    components = maven_components(sbom)
    if not components:
        print("SBOM không có gói Maven nào", file=sys.stderr)
        return 2

    rows = query_osv(components)
    print(summarize(rows))

    if args.codes:
        with open(args.codes, encoding="utf-8") as fh:
            known = {line.strip() for line in fh if line.strip()}
        new_codes = sorted({row[2] for row in rows} - known)
        gone_codes = sorted(known - {row[2] for row in rows})
        print(f"\nmã ngoài danh sách ({len(new_codes)}): {', '.join(new_codes) or 'không có'}")
        print(f"mã đã hết ({len(gone_codes)}): {', '.join(gone_codes) or 'không có'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
