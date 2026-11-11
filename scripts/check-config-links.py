#!/usr/bin/env python3
"""Probe URLs configured by Monika and write docs/opus/ket-qua/L01-lien-ket.md."""

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from http.client import HTTPException
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen
import json
import argparse
import sys
import re
import socket


ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "config/monika-config.json"
GRADLE_PATH = ROOT / "app/build.gradle.kts"
REPORT_PATH = ROOT / "docs/opus/ket-qua/L01-lien-ket.md"
TIMEOUT_SECONDS = 15
USER_AGENT = "AowVN-Monika-config-link-check/1.0"
CORE_URL_PATH = re.compile(r"^cores\.[^.]+\.url$")
# V42: hai URL này là URL GỐC (tiền tố) của VietQR, không phải endpoint độc lập:
# gọi thẳng vào chúng luôn 404/400. Phải dựng URL ĐẦY ĐỦ đúng như app dựng (DonationLogic.kt)
# rồi mới kiểm — nếu không dựng được (cấu hình donate thiếu/sai) thì ghi rõ lý do bỏ qua.
DONATE_BASE_PATHS = ("account.donate.qrBaseUrl", "account.donate.payUrl")


def donate_derived_urls(config):
    """Dựng URL đầy đủ cho 2 URL gốc VietQR, bám đúng điều kiện của DonationLogic.kt.

    Trả {đường_dẫn_cấu_hình: url_đầy_đủ}; thiếu/sai cấu hình thì không có khoá đó (bị bỏ qua, có ghi lý do).
    """
    donate = ((config or {}).get("account") or {}).get("donate") or {}
    bank = str(donate.get("bankId") or "")
    account = str(donate.get("accountNo") or "")
    if not re.fullmatch(r"[A-Za-z0-9]+", bank) or not re.fullmatch(r"[0-9]{4,32}", account):
        return {}
    template = str(donate.get("template") or "")
    amount = donate.get("minAmount")
    if not isinstance(amount, int) or amount <= 0:
        return {}
    memo = str(donate.get("memo") or "")
    name = str(donate.get("accountName") or "")
    out = {}
    qr_base = str(donate.get("qrBaseUrl") or "")
    if qr_base.startswith("https://") and re.fullmatch(r"[A-Za-z0-9]+", template):
        out["account.donate.qrBaseUrl"] = qr_base.rstrip("/") + f"/{bank}-{account}-{template}.png?" + urlencode(
            {"amount": amount, "addInfo": memo, "accountName": name}
        )
    pay_base = str(donate.get("payUrl") or "")
    if pay_base.startswith("https://"):
        out["account.donate.payUrl"] = pay_base + "?" + urlencode(
            {
                "app": bank,
                "ba": f"{account}@{bank}",
                "am": amount,
                "tn": memo,
                "bn": name,
                "url": str(donate.get("returnUrl") or ""),
            }
        )
    return out


def redact_url(base):
    """Hiện URL gốc (không tham số) để báo cáo/artifact không lộ số tài khoản hay tên người nhận."""
    return base + ("…" if base.endswith("/") else "?…")


def supported_abis():
    text = GRADLE_PATH.read_text(encoding="utf-8")
    match = re.search(r"include\(([^)]*)\)", text)
    if not match:
        raise RuntimeError(f"Không tìm thấy danh sách ABI trong {GRADLE_PATH.relative_to(ROOT)}")
    abis = re.findall(r'"([^"]+)"', match.group(1))
    if not abis:
        raise RuntimeError(f"Danh sách ABI rỗng trong {GRADLE_PATH.relative_to(ROOT)}")
    return abis


def collect_urls(value, abis, path="", parent=None):
    records = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}" if path else key
            records.extend(collect_urls(child, abis, child_path, value))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            records.extend(collect_urls(child, abis, f"{path}[{index}]", value))
    elif isinstance(value, str) and value.startswith(("http://", "https://")):
        size_by_abi = parent.get("sizeByAbi", {}) if isinstance(parent, dict) else {}
        configured_size = parent.get("size") if isinstance(parent, dict) else None
        if "{abi}" in value:
            declared_abis = None
            if CORE_URL_PATH.fullmatch(path) and isinstance(parent, dict):
                declared_abis = parent.get("abis")
            if declared_abis is not None and (
                not isinstance(declared_abis, list)
                or not all(isinstance(abi, str) for abi in declared_abis)
            ):
                raise ValueError(f"{path.rsplit('.', 1)[0]}.abis phải là danh sách chuỗi")

            for abi in abis:
                record = {
                    "source": f"{path} [{abi}]",
                    "url": value.replace("{abi}", abi),
                    "configured_size": size_by_abi.get(abi, configured_size)
                    if isinstance(size_by_abi, dict) else configured_size,
                }
                if declared_abis is not None and abi not in declared_abis:
                    record["skip_reason"] = (
                        f"Bỏ qua: ABI {abi} không khai trong "
                        f"{path.rsplit('.', 1)[0]}.abis"
                    )
                records.append(record)
        else:
            records.append({"source": path, "url": value, "configured_size": configured_size})
    return records


def is_aow_domain(url):
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    return host == "aow.vn" or host.endswith(".aow.vn")


def response_size(headers):
    content_range = headers.get("Content-Range", "")
    match = re.search(r"/([0-9]+)$", content_range)
    if match:
        return int(match.group(1))
    length = headers.get("Content-Length")
    return int(length) if length and length.isdigit() else None


def request_head(url):
    """HEAD trước; nếu máy chủ từ chối HEAD (4xx/5xx) thì thử GET `Range: bytes=0-0` rồi mới kết luận.

    V42: `dl.vietqr.io/pay` trả 404 cho HEAD nhưng 200 cho GET ⇒ chỉ dựa vào HEAD là báo hỏng oan.
    GET cũng lỗi thì đó mới là lỗi thật.
    """
    request = Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.status, response.headers, "HEAD", None
    except HTTPError as error:
        headers = error.headers
        status = error.code
        error.close()
        if status < 400:
            return status, headers, "HEAD", None
    except (URLError, TimeoutError, socket.timeout, HTTPException, OSError) as error:
        return None, {}, "HEAD", error

    request = Request(
        url,
        method="GET",
        headers={"User-Agent": USER_AGENT, "Range": "bytes=0-0"},
    )
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            response.read(1)
            return response.status, response.headers, "GET range 0-0", None
    except HTTPError as error:
        headers = error.headers
        status = error.code
        error.close()
        return status, headers, "GET range 0-0", None
    except (URLError, TimeoutError, socket.timeout, HTTPException, OSError) as error:
        return None, {}, "GET range 0-0", error


def probe(record):
    url = record["url"]
    parsed = urlparse(url)
    if record.get("skip_reason"):
        return {**record, "status": None, "method": "—", "remote_size": None,
                "note": record["skip_reason"], "checked": False}
    if is_aow_domain(url):
        return {**record, "status": None, "method": "—", "remote_size": None,
                "note": "Không kiểm được từ máy ngoài VN; không coi là hỏng", "checked": False}
    if re.search(r"\{[^}]+\}|\$[1-9][0-9]*", url):
        return {**record, "status": None, "method": "—", "remote_size": None,
                "note": "Mẫu URL còn tham số; chưa gửi yêu cầu", "checked": False}
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        return {**record, "status": None, "method": "—", "remote_size": None,
                "note": "URL không hợp lệ; chưa gửi yêu cầu", "checked": False}

    status, headers, method, error = request_head(url)
    remote_size = response_size(headers)
    if error:
        note = f"Lỗi kết nối: {type(error).__name__}: {str(error)[:100]}"
    elif status is not None and status >= 400:
        note = f"HTTP {status}"
    else:
        note = "Đã nhận phản hồi HTTP"
    return {**record, "status": status, "method": method, "remote_size": remote_size,
            "note": note, "error": error is not None, "checked": True}


def fmt_size(size):
    return f"{size:,} B" if isinstance(size, int) else "—"


def fmt_cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ")


def sort_key(row):
    if row.get("checked") and (row.get("error") or (row.get("status") or 0) >= 400):
        return 0, row["source"], row["url"]
    if not row.get("checked"):
        return 1, row["source"], row["url"]
    return 2, row["source"], row["url"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, default=REPORT_PATH)
    parser.add_argument('--json-output', type=Path)
    parser.add_argument('--fail-on-error', action='store_true')
    args = parser.parse_args()
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    abis = supported_abis()
    records = collect_urls(config, abis)
    # V42: URL gốc VietQR phải kiểm bằng URL đầy đủ như app dựng; không dựng được thì bỏ qua có lý do.
    derived = donate_derived_urls(config)
    for record in records:
        if record["source"] not in DONATE_BASE_PATHS:
            continue
        full = derived.get(record["source"])
        if full:
            record["display"] = redact_url(record["url"])
            record["url"] = full
        else:
            record["skip_reason"] = (
                "URL gốc cần tham số (không gọi thẳng được) và cấu hình donate thiếu/không hợp lệ "
                "nên không dựng được URL đầy đủ"
            )

    with ThreadPoolExecutor(max_workers=6) as executor:
        results = list(executor.map(probe, records))
    results.sort(key=sort_key)

    checked = sum(row.get("checked", False) for row in results)
    errors = sum(
        row.get("checked", False)
        and (row.get("error") or (row.get("status") or 0) >= 400)
        for row in results
    )
    skipped = len(results) - checked
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        "# L01 — Kiểm tra liên kết trong cấu hình",
        "",
        f"Kiểm tra lúc {now}. Nguồn: `config/monika-config.json`; ABI lấy từ `app/build.gradle.kts`: {', '.join(abis)}. Với `cores.<id>.abis`, chỉ kiểm ABI được khai; ABI còn lại được ghi là bỏ qua.",
        f"Tổng {len(results)} mục: {checked} đã gửi yêu cầu, {errors} phản hồi lỗi/kết nối, {skipped} mục không kiểm.",
        "",
        "URL `aow.vn` được bỏ qua vì máy chạy ngoài Việt Nam; không tính là hỏng. Chỉ gửi HEAD; nếu máy chủ từ chối HEAD (4xx/5xx), gửi GET `Range: bytes=0-0` và đọc tối đa 1 byte.",
        "Hai URL gốc VietQR (`account.donate.qrBaseUrl`, `account.donate.payUrl`) được kiểm bằng **URL đầy đủ dựng như app** (`DonationLogic.kt`); tham số (số tài khoản, tên người nhận) không in ra báo cáo.",
        "Mẫu URL còn tham số (ví dụ `$1`) được ghi nhận nhưng không gửi đi.",
        "",
        "| Nguồn cấu hình | URL | HTTP | Phương thức | Kích thước phản hồi | Kích thước config | So sánh | Ghi chú |",
        "|---|---|---:|---|---:|---:|---|---|",
    ]
    for row in results:
        expected = row.get("configured_size")
        actual = row.get("remote_size")
        if expected is None:
            comparison = "—"
        elif actual is None:
            comparison = "không có số đo phản hồi"
        else:
            comparison = "khớp" if actual == expected else f"lệch {actual - expected:+,} B"
        status = row.get("status")
        status_text = str(status) if status is not None else "—"
        lines.append(
            "| " + " | ".join(fmt_cell(value) for value in (
                row["source"], f'`{row.get("display") or row["url"]}`', status_text, row["method"],
                fmt_size(actual), fmt_size(expected), comparison, row["note"],
            )) + " |"
        )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Đã ghi {args.report}: {len(results)} URL, {errors} phản hồi lỗi/kết nối, {skipped} mục không kiểm.")

    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        # V42: không ghi URL đầy đủ có tham số (số tài khoản/tên người nhận) vào artifact.
        public = [{**row, "url": row.get("display") or row["url"]} for row in results]
        args.json_output.write_text(json.dumps(dict(checked=checked, errors=errors, skipped=skipped, results=public), ensure_ascii=False, indent=2) + "\n")
    if args.fail_on_error and errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
