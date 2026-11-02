#!/usr/bin/env python3
"""V174: bảng chẩn đoán crash đọc từ Worker, xuất `crash-insight.md` + `crash-insight.csv`.

- Đọc Worker **đúng một request**: `GET {CRASH_URL}/reports` (Worker tự chặn 100 báo cáo mới nhất;
  đường này không có tham số phân trang nên không thể lặp vô hạn). Không gọi `/reports/<id>`.
- Gộp theo (lõi, loại, chữ ký) rồi cộng `count`; chỉ dùng các trường Worker trả ở danh sách:
  `k` loại · `c` lõi · `a` bản app · `component` · `fp` chữ ký · `count` · `t` thời điểm.
- KHÔNG đưa vào bảng: tên game (`g`), tiêu đề, chi tiết, log, vệt sự kiện, `device`, IP. Chỉ số liệu gộp.
- Không in token, không ghi token ra tệp.

Chạy: `CRASH_ADMIN_TOKEN=<mã> python3 scripts/crash-insight.py --out-dir .`
Offline: `python3 scripts/crash-insight.py --file listing.json --out-dir out`
"""
import argparse
import csv
import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

MAX_BYTES = 2 * 1024 * 1024   # danh sách 100 dòng metadata: dư sức trong 2 MB
MAX_ROWS = 100                # Worker chặn 100 báo cáo mới nhất cho /reports
DEFAULT_URL = "https://aowvn-monika-crash.aowvn-system.workers.dev"

# Cột bảng (đúng thứ tự PM chốt) → trường nguồn, để ghi rõ trường nào thiếu.
COLUMNS = ["chữ ký (băm ngắn)", "lõi", "loại", "số lượt", "bản app", "đời máy", "màn", "giả thuyết"]
MISSING = "—"
COLUMN_SOURCE = {
    "chữ ký (băm ngắn)": "fp",
    "lõi": "c",
    "loại": "k",
    "số lượt": "count",
    "bản app": "a",
    "đời máy": None,            # /reports không trả `device`; chỉ báo cáo chi tiết mới có
    "màn": "component",
    "giả thuyết": "k+component",
}

# Giả thuyết suy từ `loại` + `component` — KHÔNG phải kết luận, chỉ để xếp hàng chờ đọc chi tiết.
KIND_GUESS = {
    "anr": "treo tiến trình (ANR)",
    "native": "sập trong mã native của lõi",
    "java": "lỗi Java (cần đọc stack)",
    "lowmem": "máy hết RAM, Android tắt app",
    "killed": "hệ thống tắt tiến trình (nghi thiếu RAM/nền)",
    "handled": "lỗi app tự bắt, không sập",
    "resource": "vượt hạn mức tài nguyên",
    "initfail": "khởi tạo tiến trình thất bại",
    "unknown": "chưa đủ dữ liệu phân loại",
}
COMPONENT_GUESS = {
    "op:giai-nen": "giải nén lỗi (tệp hỏng/mật khẩu/bộ giải nén)",
    "op:nhap-game": "nhập game lỗi (mở/chép tệp)",
    "op:tai-xuong": "tải về lỗi (mạng/nguồn tải)",
    "op:cai-goi-engine": "cài gói engine lỗi",
    "op:mo-game": "mở game lỗi (tải lõi/chuẩn bị ROM)",
    "op:tai-feed": "tải feed lỗi",
    "op:tai-cau-hinh": "tải cấu hình lỗi",
    "op:va-rom": "vá ROM lỗi",
}


def text(value, limit=160):
    """Chuỗi an toàn cho bảng: bỏ ký tự điều khiển, cắt dài, rỗng → None."""
    if not isinstance(value, str):
        return None
    cleaned = "".join(ch for ch in value if ch.isprintable()).strip()
    return cleaned[:limit] or None


def number(value):
    return value if isinstance(value, int) and not isinstance(value, bool) and value > 0 else None


def validate_base(base):
    """Cùng luật với crash-issue-bot.py: https, *.workers.dev, không user/path/query."""
    url = urllib.parse.urlsplit(base or "")
    if url.scheme != "https" or not (url.hostname or "").endswith(".workers.dev"):
        raise ValueError("CRASH_URL must be https://<name>.workers.dev")
    if url.username or url.password or url.query or url.fragment or url.path not in ("", "/"):
        raise ValueError("CRASH_URL must not carry user/path/query")
    return base.rstrip("/")


def rows_of(payload):
    """Lấy danh sách báo cáo từ payload `/reports`; sai dạng → ValueError (không đoán)."""
    rows = payload.get("reports") if isinstance(payload, dict) else payload
    if not isinstance(rows, list):
        raise ValueError("payload has no 'reports' list")
    if len(rows) > MAX_ROWS:
        raise ValueError("Worker returned more than %d rows" % MAX_ROWS)
    return rows


def signature_of(row):
    """Chữ ký ngắn: lấy `fp` (vân tay Worker lưu cùng báo cáo) nếu đúng dạng hex."""
    fp = text(row.get("fp") or row.get("fingerprint"), 128)
    if fp and re.fullmatch(r"[0-9a-fA-F]{8,128}", fp):
        return fp.lower()[:12]
    return None


def guess(kind, component):
    if kind == "op-fail" and component in COMPONENT_GUESS:
        return COMPONENT_GUESS[component]
    if kind == "anr" and component and component.startswith("app:"):
        return "treo tiến trình chính (không có phiên game)"
    return KIND_GUESS.get(kind or "", "chưa đủ dữ liệu phân loại")


def group_rows(rows):
    """Gộp theo (lõi, loại, chữ ký, thành phần). Cộng `count`; liệt kê bản app; giữ thời điểm mới nhất."""
    groups = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("report row is not an object")
        kind = text(row.get("k") or row.get("kind"), 32)
        core = text(row.get("c") or row.get("core"), 60)
        component = text(row.get("component"), 160)
        signature = signature_of(row)
        key = (core or "", kind or "", signature or "", component or "")
        group = groups.setdefault(key, {
            "signature": signature, "core": core, "kind": kind, "component": component,
            "count": 0, "apps": set(), "newest": 0,
        })
        group["count"] += number(row.get("count")) or 1
        app = text(row.get("a") or row.get("app"), 40)
        if app:
            group["apps"].add(app)
        group["newest"] = max(group["newest"], number(row.get("t") or row.get("time")) or 0)
    return sorted(groups.values(), key=lambda g: (-g["count"], g["kind"] or "", g["signature"] or "", g["component"] or ""))


def apps_label(apps):
    if not apps:
        return MISSING
    ordered = sorted(apps)
    return ", ".join(ordered[:3]) + (" …" if len(ordered) > 3 else "")


def table_rows(groups):
    """8 cột đúng thứ tự; trường thiếu → `—`."""
    out = []
    for group in groups:
        out.append([
            group["signature"] or MISSING,
            group["core"] or MISSING,
            group["kind"] or MISSING,
            str(group["count"]),
            apps_label(group["apps"]),
            MISSING,                                  # Worker /reports không trả `device`
            group["component"] or MISSING,
            guess(group["kind"], group["component"]),
        ])
    return out


def missing_fields(rows):
    """Cột không có dữ liệu ở mọi dòng → mô tả trường nguồn (để ghi dòng cuối bảng)."""
    missing = []
    for column in COLUMNS:
        index = COLUMNS.index(column)
        if all(row[index] == MISSING for row in rows):
            source = COLUMN_SOURCE.get(column)
            if source is None:
                missing.append("%s (Worker `/reports` không trả `device`)" % column)
            else:
                missing.append("%s (trường `%s`)" % (column, source))
    return missing


def render_markdown(groups, scanned):
    rows = table_rows(groups)
    lines = [
        "# Bảng chẩn đoán crash (V174)",
        "",
        "Đọc **một lần** `GET /reports` của Worker (tối đa 100 báo cáo mới nhất); không gọi chi tiết từng báo cáo.",
        "Số dòng nhận được: %d · số nhóm (lõi + loại + chữ ký): %d." % (scanned, len(groups)),
        "",
        "| " + " | ".join(COLUMNS) + " |",
        "|" + "|".join(["---"] * len(COLUMNS)) + "|",
    ]
    for row in rows:
        lines.append("| " + " | ".join(cell.replace("|", "\\|") for cell in row) + " |")
    if not rows:
        lines.append("| " + " | ".join([MISSING] * len(COLUMNS)) + " |")
    lines += [
        "",
        "Ghi chú:",
        "- `giả thuyết` suy từ `loại` + `component`, **không phải kết luận** — cần mở báo cáo chi tiết mới xác nhận.",
        "- `màn` lấy từ trường `component` (Worker không có trường màn hình riêng).",
        "- `số lượt` là tổng `count` của các báo cáo cùng nhóm trong cửa sổ đã đọc.",
        "- Bảng không chứa tên game, đường dẫn, log hay mã máy.",
    ]
    missing = missing_fields(rows)
    if missing:
        lines.append("- **Thiếu trường:** " + "; ".join(missing) + " — đã ghi `%s`." % MISSING)
    return "\n".join(lines) + "\n"


def render_csv(groups):
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(COLUMNS)
    writer.writerows(table_rows(groups))
    return buffer.getvalue()


def fetch_listing(base, token):
    """Đúng một request, không theo redirect, có chặn dung lượng."""
    if not token or any(not 0x20 <= ord(ch) <= 0x7e for ch in token):
        raise ValueError("missing or invalid CRASH_ADMIN_TOKEN")
    request = urllib.request.Request(base + "/reports", headers={
        "Authorization": "Bearer " + token, "User-Agent": "aow-crash-insight-v174",
    })

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise ValueError("redirect blocked")

    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
            content = response.read(MAX_BYTES + 1)
    except urllib.error.HTTPError as error:
        raise ValueError("Worker returned HTTP %d" % error.code) from None
    except (OSError, urllib.error.URLError):
        raise ValueError("cannot reach Worker") from None
    if len(content) > MAX_BYTES:
        raise ValueError("response exceeds %d bytes" % MAX_BYTES)
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        raise ValueError("response is not JSON") from None


def build(payload):
    """payload → (markdown, csv, số dòng, số nhóm). Dùng chung cho mạng và tệp offline."""
    rows = rows_of(payload)
    groups = group_rows(rows)
    return render_markdown(groups, len(rows)), render_csv(groups), len(rows), len(groups)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out-dir", default=".", help="thư mục ghi crash-insight.md/.csv")
    parser.add_argument("--file", help="đọc payload /reports từ tệp (offline, không gọi mạng)")
    args = parser.parse_args()
    try:
        if args.file:
            # utf-8-sig: chấp nhận tệp có BOM (Windows) lẫn không BOM.
            with open(args.file, encoding="utf-8-sig") as handle:
                payload = json.load(handle)
        else:
            base = validate_base(os.environ.get("CRASH_URL") or DEFAULT_URL)
            payload = fetch_listing(base, os.environ.get("CRASH_ADMIN_TOKEN", "").strip())
        markdown, table, scanned, groups = build(payload)
        os.makedirs(args.out_dir, exist_ok=True)
        with open(os.path.join(args.out_dir, "crash-insight.md"), "w", encoding="utf-8") as handle:
            handle.write(markdown)
        with open(os.path.join(args.out_dir, "crash-insight.csv"), "w", encoding="utf-8") as handle:
            handle.write(table)
        # Log CI để ASCII: console Windows (cp1252) không in được tiếng Việt.
        print("OK: wrote crash-insight.md and crash-insight.csv (%d rows, %d groups)" % (scanned, groups))
    except (ValueError, OSError, json.JSONDecodeError) as error:
        # Chỉ in thông báo do script kiểm soát: không in token, không in nội dung báo cáo.
        print("BLOCKED: " + (str(error) if type(error) is ValueError else type(error).__name__), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
