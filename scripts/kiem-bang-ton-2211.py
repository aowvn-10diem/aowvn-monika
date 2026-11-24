#!/usr/bin/env python3
"""Kiểm bảng chốt chiến dịch dọn issue lỗi: `.md` và `.json` phải cùng số.

Bảng 19/11 (`scripts/kiem-bang-ton.py`) không dùng lại được: regex của nó bám
cấu trúc *bảng tồn theo lõi* (`tong.co_khoi`, `nhom`, `theo_loi`, `phu_luc_a`).
Bảng chốt chiến dịch trả lời câu khác — còn gì mở, đã đóng được gì, bằng chứng
ở đâu — nên có script riêng, cùng nguyên tắc: so **cả tên khoá lẫn con số**.

Chạy: `python scripts/kiem-bang-ton-2211.py`
Mã thoát: 0 = khớp hết, 1 = có dòng sai (in rõ dòng nào).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

# Windows: console mặc định cp1252 sẽ ném UnicodeEncodeError khi in tiếng Việt qua pipe.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
MAC_DINH_MD = ROOT / "docs" / "loi" / "TON-2211.md"
MAC_DINH_JSON = ROOT / "docs" / "loi" / "TON-2211.json"

# Một đường dẫn khoá: `tong`, `con_mo.crash`, ...
DUONG_DAN_KHOA = re.compile(r"^[a-z][a-zA-Z0-9_]*(\.[a-z][a-zA-Z0-9_]*)*$")

# Bảng 1 — nhãn cột trái -> đường dẫn trong JSON
BANG_TONG = [
    ("Issue đang mở", ("tong", "issue_mo")),
    ("Issue mở nhãn", ("tong", "nhan_crash_mo")),
    ("Issue mở **không** nhãn", ("tong", "khong_nhan")),
    ("Issue mở có khối", ("tong", "co_khoi_mo")),
    ("Tổng lượt còn mở", ("tong", "tong_luot_mo")),
    ("Issue đóng trong chiến dịch", ("tong", "dong_chien_dich")),
    ("… trong đó nhãn", ("tong", "dong_crash_moi")),
    ("Issue tạo mới trong chiến dịch", ("tong", "issue_moi")),
]


class KetQua:
    def __init__(self) -> None:
        self.dong: list[tuple[str, str, str, str]] = []
        self.sai = 0

    def so(self, ten: str, trong_md, trong_json) -> None:
        ok = trong_md is not None and trong_md == trong_json
        self.sai += 0 if ok else 1
        self.dong.append((ten, "-" if trong_md is None else str(trong_md), str(trong_json), "đúng" if ok else "**SAI**"))

    def ds(self, ten: str, trong_md, trong_json) -> None:
        ok = list(trong_md) == list(trong_json)
        self.sai += 0 if ok else 1
        self.dong.append((ten, f"{len(trong_md)} mục", f"{len(trong_json)} mục", "đúng" if ok else "**SAI**"))


def khoa_trong_md(md: str, du_lieu: dict) -> list[str]:
    cap1 = set(du_lieu)
    ung_vien = set()
    for span in re.findall(r"`([^`\n]+)`", md):
        span = span.strip()
        if not DUONG_DAN_KHOA.match(span):
            continue
        if span.split(".", 1)[0] not in cap1:
            continue
        ung_vien.add(span)
    return sorted(ung_vien)


def co_khoa(du_lieu, duong_dan: str) -> bool:
    hien_tai = du_lieu
    for phan in duong_dan.split("."):
        if not isinstance(hien_tai, dict) or phan not in hien_tai:
            return False
        hien_tai = hien_tai[phan]
    return True


def lay(du_lieu: dict, duong_dan: tuple[str, str]):
    return du_lieu[duong_dan[0]][duong_dan[1]]


def kiem_so(md: str, du_lieu: dict) -> KetQua:
    kq = KetQua()

    # --- Bảng 1: từng dòng nhãn -> **số**
    dong1 = {}
    for nhan, so in re.findall(r"^\| ([^|\n]+?) \| \*\*(\d+)\*\* \|", md, re.M):
        # Bỏ phần `…` trong nhãn (ví dụ "Issue mở nhãn `crash-moi`") để so theo nhãn cột.
        dong1[re.sub(r"\s*`[^`]*`", "", nhan).strip()] = int(so)
    for nhan, duong_dan in BANG_TONG:
        kq.so(f"Bảng 1: {nhan}", dong1.get(nhan), lay(du_lieu, duong_dan))

    # --- Bảng 2: đóng theo ngày
    ngay_md = {
        n: (int(a), int(b), int(c), int(l))
        for n, a, b, c, l in re.findall(
            r"^\| (\d\d/\d\d) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|", md, re.M)
    }
    ngay_js = {
        d["ngay"][8:10] + "/" + d["ngay"][5:7]: (d["dong"], d["completed"], d["not_planned"], d["lo"])
        for d in du_lieu["dong_theo_ngay"]
    }
    kq.ds("Bảng 2: danh sách ngày", sorted(ngay_md), sorted(ngay_js))
    kq.ds("Bảng 2: (đóng, completed, not_planned, lô) từng ngày",
          [ngay_md[k] for k in sorted(ngay_md)], [ngay_js[k] for k in sorted(ngay_js)])

    tong2 = re.search(r"^\| \*\*Tổng\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \|",
                      md, re.M)
    if tong2:
        a, b, c, l = (int(x) for x in tong2.groups())
        kq.so("Bảng 2: tổng đóng = cộng theo ngày", a, sum(v[0] for v in ngay_js.values()))
        kq.so("Bảng 2: tổng completed = cộng theo ngày", b, sum(v[1] for v in ngay_js.values()))
        kq.so("Bảng 2: tổng not_planned = cộng theo ngày", c, sum(v[2] for v in ngay_js.values()))
        kq.so("Bảng 2: tổng lô = cộng theo ngày", l, sum(v[3] for v in ngay_js.values()))
        kq.so("Bảng 2: tổng đóng = tong.dong_chien_dich", a, du_lieu["tong"]["dong_chien_dich"])
        kq.so("Bảng 2: completed = tong.dong_ban_va", b, du_lieu["tong"]["dong_ban_va"])
        kq.so("Bảng 2: not_planned = tong.dong_thieu_du_lieu", c, du_lieu["tong"]["dong_thieu_du_lieu"])
    else:
        kq.so("Bảng 2: dòng **Tổng**", None, "có")

    # --- Mục 3: số issue còn mở
    m3 = re.search(r"## 3\. Còn mở — (\d+) `crash-moi` \+ (\d+) không nhãn", md)
    kq.so("Mục 3: số crash-moi ở tiêu đề", int(m3.group(1)) if m3 else None,
          len(du_lieu["con_mo"]["crash"]))
    kq.so("Mục 3: số không nhãn ở tiêu đề", int(m3.group(2)) if m3 else None,
          len(du_lieu["con_mo"]["khong_nhan"]))
    so_dong3 = re.findall(r"^\| \[#(\d+)\]", md, re.M)
    so_js3 = [str(x["number"]) for x in du_lieu["con_mo"]["crash"]] + \
             [str(x["number"]) for x in du_lieu["con_mo"]["khong_nhan"]]
    kq.ds("Mục 3: danh sách issue", sorted(so_dong3, key=int), sorted(so_js3, key=int))
    kq.so("Mục 3: tổng issue = crash + không nhãn", len(so_dong3),
          len(du_lieu["con_mo"]["crash"]) + len(du_lieu["con_mo"]["khong_nhan"]))
    kq.so("Mục 3: tổng = tong.issue_mo", len(so_dong3), du_lieu["tong"]["issue_mo"])

    # --- Bảng 4: đóng theo bản phát hành
    ten_ban = ["0.8.13", "0.8.14", "0.8.15", "0.8.16", "không bản"]
    js_ban = {d["ban"]: d for d in du_lieu["dong_theo_ban"]}
    for ten in ten_ban:
        khoa = "khong" if ten == "không bản" else ten
        d = js_ban.get(khoa)
        m = re.search(r"^\| " + re.escape(ten) + r" \| [^|]+ \| (\d+) \| (\d+) \| (\d+) \| ([^|]+) \|", md, re.M)
        if not m or not d:
            kq.so(f"Bảng 4: {ten} có dòng", bool(m), bool(d))
            continue
        so, ban_va, thieu, cot_pr = int(m.group(1)), int(m.group(2)), int(m.group(3)), m.group(4)
        kq.so(f"Bảng 4: {ten} số issue", so, d["so_issue"])
        kq.so(f"Bảng 4: {ten} vì bản vá", ban_va, d["ban_va"])
        kq.so(f"Bảng 4: {ten} vì thiếu dữ liệu", thieu, d["thieu_du_lieu"])
        kq.so(f"Bảng 4: {ten} bản vá + thiếu dữ liệu = số issue", ban_va + thieu, so)
        # PR: mọi `#N (k)` trong cột phải khớp JSON
        for so_pr, so_issue in re.findall(r"#(\d+) \((\d+)\)", cot_pr):
            co = [p for p in d["pr"] if p["so"] == int(so_pr)]
            kq.so(f"Bảng 4: {ten} PR #{so_pr} số issue",
                  int(so_issue), co[0]["so_issue"] if co else None)
        # và mọi PR trong JSON phải được nêu trong cột
        thieu_pr = [p["so"] for p in d["pr"] if f"#{p['so']}" not in cot_pr]
        kq.ds(f"Bảng 4: {ten} PR nêu trong .md", thieu_pr, [])

    tong4 = re.search(r"^\| \*\*Tổng\*\* \| \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \|", md, re.M)
    if tong4:
        a, b, c = (int(x) for x in tong4.groups())
        kq.so("Bảng 4: tổng issue = cộng theo bản", a, sum(d["so_issue"] for d in du_lieu["dong_theo_ban"]))
        kq.so("Bảng 4: tổng bản vá = cộng theo bản", b, sum(d["ban_va"] for d in du_lieu["dong_theo_ban"]))
        kq.so("Bảng 4: tổng thiếu dữ liệu = cộng theo bản", c, sum(d["thieu_du_lieu"] for d in du_lieu["dong_theo_ban"]))
        kq.so("Bảng 4: tổng = tong.dong_chien_dich", a, du_lieu["tong"]["dong_chien_dich"])
        kq.so("Bảng 4: bản vá = tong.dong_ban_va", b, du_lieu["tong"]["dong_ban_va"])
        kq.so("Bảng 4: thiếu dữ liệu = tong.dong_thieu_du_lieu", c, du_lieu["tong"]["dong_thieu_du_lieu"])
    else:
        kq.so("Bảng 4: dòng **Tổng**", None, "có")

    # --- Dòng "Số liệu chốt"
    m_chot = re.search(r"Số liệu chốt: (\d{4}-\d{2}-\d{2}), (\d{2}:\d{2})Z", md)
    kq.so("Dòng chốt: ngày", m_chot.group(1) if m_chot else None, du_lieu["chot"])
    kq.so("Dòng chốt: giờ UTC", m_chot.group(2) if m_chot else None, du_lieu["gio_utc"])
    kq.so("Dòng chốt: chuỗi trong kiem_cheo.so_dong_chot",
          du_lieu["kiem_cheo"]["so_dong_chot"],
          f'Số liệu chốt: {du_lieu["chot"]}, {du_lieu["gio_utc"]}Z')

    # --- Số lượt còn mở = cộng lượt của issue crash còn mở
    kq.so("Tổng lượt còn mở = cộng lượt issue crash",
          du_lieu["tong"]["tong_luot_mo"], sum(x["luot"] for x in du_lieu["con_mo"]["crash"]))
    kq.so("Số issue có khối còn mở = số issue crash có lượt > 0",
          du_lieu["tong"]["co_khoi_mo"], sum(1 for x in du_lieu["con_mo"]["crash"] if x["luot"] > 0))
    return kq


def in_bang(tieu_de: str, cot: tuple[str, ...], dong: list[tuple]) -> None:
    print(f"\n{tieu_de}")
    rong = [len(c) for c in cot]
    for d in dong:
        for i, o in enumerate(d):
            rong[i] = max(rong[i], len(str(o)))
    print("|" + "|".join(f" {c:<{rong[i]}} " for i, c in enumerate(cot)) + "|")
    print("|" + "|".join("-" * (r + 2) for r in rong) + "|")
    for d in dong:
        print("|" + "|".join(f" {str(o):<{rong[i]}} " for i, o in enumerate(d)) + "|")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Kiểm `.md` ↔ `.json` của bảng chốt chiến dịch dọn issue lỗi")
    ap.add_argument("--md", default=str(MAC_DINH_MD))
    ap.add_argument("--json", default=str(MAC_DINH_JSON))
    args = ap.parse_args(argv)

    md_path, json_path = Path(args.md), Path(args.json)
    if not md_path.is_file() or not json_path.is_file():
        print(f"THIẾU TỆP: {md_path} / {json_path}", file=sys.stderr)
        return 1

    md = md_path.read_text(encoding="utf-8")
    du_lieu = json.loads(json_path.read_text(encoding="utf-8"))

    khoa = khoa_trong_md(md, du_lieu)
    dong_khoa = [(k, "có" if co_khoa(du_lieu, k) else "**không**", "đúng" if co_khoa(du_lieu, k) else "**SAI**")
                 for k in khoa]
    sai_khoa = sum(1 for d in dong_khoa if d[2] == "**SAI**")
    in_bang("1. Khoá nêu trong .md (đối chiếu khoá thật của .json)",
            ("khoá trong .md", "có trong .json", "đúng/sai"), dong_khoa)

    kq = kiem_so(md, du_lieu)
    in_bang("2. Con số: .md ↔ .json", ("chỉ số", "trong .md", "trong .json", "đúng/sai"), kq.dong)

    print(f"\nKhoá: {len(dong_khoa)} dòng, sai {sai_khoa} · Số: {len(kq.dong)} dòng, sai {kq.sai}")
    if sai_khoa or kq.sai:
        print("FAIL: .md và .json lệch (xem dòng **SAI** ở trên)")
        return 1
    print("PASS: khoá và số của .md khớp .json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
