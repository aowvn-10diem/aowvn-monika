#!/usr/bin/env python3
"""Tự kiểm cảnh báo phụ thuộc khi API Dependabot trả 403.

Đọc SBOM của GitHub dependency graph, đối chiếu cơ sở dữ liệu OSV (Maven),
in bảng gói + phiên bản + mã cảnh báo + mức để so với docs/bao-mat/PHU-THUOC-0811.md.

    gh api repos/aowvn-10diem/aowvn-monika/dependency-graph/sbom > sbom.json
    python scripts/kiem-phu-thuoc.py sbom.json --codes docs/ma-canh-bao.txt

Mã thoát: 0 đọc được hết, 2 lỗi đầu vào (SBOM/danh sách mã), 3 lỗi gọi OSV.
Phương án dự phòng theo docs/bao-mat/PHU-THUOC-0811.md mục 1. Không in cách
khai thác, chỉ tên gói / mã / mức.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"
BATCH = 1000
RETRIES = 3
RETRY_DELAY = 1.0
SEVERITY_ORDER = ("CRITICAL", "HIGH", "MODERATE", "LOW", "UNKNOWN")
CVSS_TYPES = {"CVSS_V3", "CVSS_V4"}

_V3_WEIGHTS = {
    "AV": {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2},
    "AC": {"L": 0.77, "H": 0.44},
    "UI": {"N": 0.85, "R": 0.62},
    "C": {"H": 0.56, "L": 0.22, "N": 0.0},
    "I": {"H": 0.56, "L": 0.22, "N": 0.0},
    "A": {"H": 0.56, "L": 0.22, "N": 0.0},
}
_PR_UNCHANGED = {"N": 0.85, "L": 0.62, "H": 0.27}
_PR_CHANGED = {"N": 0.85, "L": 0.68, "H": 0.5}


class OsvError(RuntimeError):
    """Lỗi gọi OSV: mạng, HTTP lạ, hoặc phản hồi không khớp số gói đã hỏi."""


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


def _decode(raw: bytes, url: str) -> dict:
    try:
        return json.loads(raw.decode())
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise OsvError(f"OSV trả JSON hỏng cho {url}") from exc


def _call(url: str, timeout: int, payload: dict | None = None) -> dict:
    """GET/POST JSON, thử lại khi 429 hoặc 5xx; lỗi khác ném OsvError."""
    data = None if payload is None else json.dumps(payload).encode()
    headers = {} if payload is None else {"Content-Type": "application/json"}
    request = urllib.request.Request(url, data=data, headers=headers)
    last_error = "không rõ"
    for attempt in range(RETRIES):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as resp:
                return _decode(resp.read(), url)
        except urllib.error.HTTPError as exc:
            if exc.code != 429 and not 500 <= exc.code < 600:
                raise OsvError(f"OSV trả HTTP {exc.code} cho {url}") from exc
            last_error = f"HTTP {exc.code}"
        except urllib.error.URLError as exc:
            last_error = str(exc.reason)
        except TimeoutError:
            last_error = "quá thời gian chờ"
        if attempt + 1 < RETRIES:
            time.sleep(RETRY_DELAY * (attempt + 1))
    raise OsvError(f"không gọi được {url} sau {RETRIES} lần: {last_error}")


def _post_json(url: str, payload: dict) -> dict:
    return _call(url, timeout=120, payload=payload)


def _get_json(url: str) -> dict:
    return _call(url, timeout=60)


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
        # Thiếu kết quả mà vẫn chạy tiếp sẽ in "0 cảnh báo" — đúng kiểu âm tính
        # giả nguy hiểm nhất với công cụ an ninh, nên dừng hẳn.
        if len(results) != len(chunk):
            raise OsvError(
                f"OSV trả {len(results)} kết quả cho {len(chunk)} gói đã hỏi"
            )
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
    return sorted(
        found,
        key=lambda r: (
            SEVERITY_ORDER.index(r[3]) if r[3] in SEVERITY_ORDER else 99,
            r[0],
        ),
    )


def _worst(entries) -> str:
    """OSV để nguyên vector CVSS; quy về nhãn gần nhất."""
    best, best_score = "", -1.0
    for entry in entries or []:
        if (entry.get("type") or "").upper() not in CVSS_TYPES:
            continue
        score = _cvss_score(entry.get("score") or "")
        if score > best_score:
            best_score, best = score, _label(score)
    return best


def _vector_fields(vector: str) -> tuple[str, dict[str, str], float | None]:
    """Tách vector CVSS thành (phiên bản, chỉ số, điểm ghi kèm nếu có)."""
    text = (vector or "").strip()
    if not text.upper().startswith("CVSS:"):
        return "", {}, None
    fields = text.split("/")
    version = fields[0].split(":", 1)[1]
    metrics: dict[str, str] = {}
    score: float | None = None
    for field in fields[1:]:
        key, sep, value = field.partition(":")
        if not sep:
            # Một số nguồn ghi điểm ngay sau vector (".../A:H/9.8").
            try:
                score = float(field)
            except ValueError:
                return version, {}, None
            continue
        metrics[key.upper()] = value.upper()
    return version, metrics, score


def _cvss_score(vector: str) -> float:
    """Điểm base CVSS; -1.0 khi không tính được.

    OSV trả `severity[].score` là vector trần, không kèm điểm, nên phải tự
    tính. Hỗ trợ 3.0/3.1 (đúng công thức chuẩn) và 4.0 (xấp xỉ, xem
    `_v4_as_v3`).
    """
    version, metrics, score = _vector_fields(vector)
    if score is not None:
        return score
    if not version or not metrics:
        return -1.0
    if version.startswith("4"):
        metrics = _v4_as_v3(metrics)
        if not metrics:
            return -1.0
    elif not version.startswith("3"):
        return -1.0
    return _v3_base_score(metrics)


def _v4_as_v3(metrics: dict[str, str]) -> dict[str, str]:
    """Quy chỉ số CVSS 4.0 về bộ 3.1 để tính điểm xấp xỉ.

    Chỉ dùng khi OSV thiếu `database_specific.severity`. Vector 4.0 không mang
    điểm sẵn và bảng tra 4.0 quá lớn để chép lại, nên ở đây bỏ chỉ số tác động
    dây chuyền (SC/SI/SA) — điểm có thể thấp hơn bản 4.0 chính thức khi có tác
    động dây chuyền, không bao giờ cao hơn.
    """
    mapped = {
        "AV": metrics.get("AV", ""),
        "AC": metrics.get("AC", ""),
        "PR": metrics.get("PR", ""),
        "UI": "N" if metrics.get("UI") == "N" else "R",
        "C": metrics.get("VC", ""),
        "I": metrics.get("VI", ""),
        "A": metrics.get("VA", ""),
        "S": "U",
    }
    if metrics.get("AT") == "P":
        # Có điều kiện tấn công làm giảm khả năng khai thác, tương đương AC:H.
        mapped["AC"] = "H"
    return mapped if all(mapped.values()) else {}


def _v3_base_score(metrics: dict[str, str]) -> float:
    scope = metrics.get("S", "U")
    try:
        weights = {name: _V3_WEIGHTS[name][metrics[name]]
                   for name in ("AV", "AC", "UI", "C", "I", "A")}
        privilege = (_PR_CHANGED if scope == "C" else _PR_UNCHANGED)[metrics["PR"]]
    except KeyError:
        return -1.0
    iss = 1 - (1 - weights["C"]) * (1 - weights["I"]) * (1 - weights["A"])
    if scope == "C":
        impact = 7.52 * (iss - 0.029) - 3.25 * (iss - 0.02) ** 15
    else:
        impact = 6.42 * iss
    if impact <= 0:
        return 0.0
    exploitability = (
        8.22 * weights["AV"] * weights["AC"] * privilege * weights["UI"]
    )
    raw = impact + exploitability
    if scope == "C":
        raw *= 1.08
    return _roundup(min(raw, 10.0))


def _roundup(value: float) -> float:
    """Làm tròn lên 1 chữ số thập phân theo quy ước CVSS."""
    scaled = int(round(value * 100000))
    if scaled % 10000 == 0:
        return scaled / 100000
    return (scaled // 10000 + 1) / 10


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


def _known_codes(path: str) -> set[str] | None:
    """Đọc danh sách mã đã biết; None khi không đọc được."""
    try:
        with open(path, encoding="utf-8") as fh:
            return {line.strip() for line in fh if line.strip()}
    except OSError as exc:
        print(f"không đọc được danh sách mã {path}: {exc}", file=sys.stderr)
        return None


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("sbom", help="tệp JSON SBOM tải bằng gh api .../dependency-graph/sbom")
    ap.add_argument("--codes", help="tệp một mã cảnh báo mỗi dòng để chỉ in mã ngoài danh sách")
    args = ap.parse_args(argv)

    # Bảng mã Windows mặc định là cp1252, không in được tiếng Việt.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    known: set[str] | None = None
    if args.codes:
        known = _known_codes(args.codes)
        if known is None:
            return 2

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

    try:
        rows = query_osv(components)
    except OsvError as exc:
        print(f"lỗi OSV: {exc}", file=sys.stderr)
        return 3
    print(summarize(rows))

    if known is not None:
        codes = {row[2] for row in rows}
        new_codes = sorted(codes - known)
        gone_codes = sorted(known - codes)
        print(f"\nmã ngoài danh sách ({len(new_codes)}): {', '.join(new_codes) or 'không có'}")
        print(f"mã đã hết ({len(gone_codes)}): {', '.join(gone_codes) or 'không có'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
