#!/usr/bin/env python3
"""Kiểm bảng tồn issue: `.md` và `.json` phải cùng số và cùng tên khoá.

Sinh ra sau một lỗi thật (PR #685): `docs/loi/TON-1911.md:237` ghi khoá
`cho_ban_on_dinh.tong_issue` trong khi JSON dùng `so_issue` — số vẫn khớp nên
mọi phép so số đều xanh, chỉ tên khoá sai. Script này so **cả hai**:

1. **Tên khoá**: mọi đoạn trong dấu `…` của `.md` trông như một đường dẫn khoá
   (chữ thường + `_`, có thể có `.`) và bắt đầu bằng một khoá cấp 1 của JSON
   phải trỏ tới khoá **có thật**.
2. **Con số**: bảng tổng quan, bảng nhóm lõi/loại, bảng theo lõi, danh sách
   issue đã đóng, mục chờ 0.8.15, phụ lục A — cộng lại phải bằng số trong JSON.

Chạy: `python scripts/kiem-bang-ton.py` (mặc định đọc `docs/loi/TON-1911.*`).
Mã thoát: 0 = khớp hết, 1 = có dòng sai (in rõ dòng nào).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAC_DINH_MD = ROOT / "docs" / "loi" / "TON-1911.md"
MAC_DINH_JSON = ROOT / "docs" / "loi" / "TON-1911.json"

# Một đường dẫn khoá: `tong`, `dong_1911.comment_dong.so_issue`, ...
DUONG_DAN_KHOA = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$")
DAI_TOI_DA = 46


def doc_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def doc_md(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def khoa_trong_md(md: str, du_lieu: dict) -> list[str]:
    """Các đoạn `…` trông như đường dẫn khoá và bắt đầu bằng khoá cấp 1 của JSON."""
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


def co_khoa(du_lieu: dict, duong_dan: str) -> bool:
    hien_tai = du_lieu
    for phan in duong_dan.split("."):
        if not isinstance(hien_tai, dict) or phan not in hien_tai:
            return False
        hien_tai = hien_tai[phan]
    return True


def kiem_khoa(md: str, du_lieu: dict) -> tuple[list[tuple[str, str, str]], int]:
    """Trả về (các dòng bảng, số dòng sai)."""
    dong = []
    sai = 0
    for duong_dan in khoa_trong_md(md, du_lieu):
        ok = co_khoa(du_lieu, duong_dan)
        sai += 0 if ok else 1
        dong.append((duong_dan, "có" if ok else "**không**", "đúng" if ok else "**SAI**"))
    return dong, sai


def _so(chuoi: str) -> int:
    return int(re.sub(r"[\s.,]", "", chuoi))


def _hien(gia_tri) -> str:
    """Rút gọn giá trị để bảng không bị một dòng dài nuốt cả log."""
    if isinstance(gia_tri, (list, tuple, dict)):
        chuoi = f"{len(gia_tri)} mục"
    else:
        chuoi = str(gia_tri)
    return chuoi if len(chuoi) <= DAI_TOI_DA else chuoi[: DAI_TOI_DA - 1] + "…"


class KetQua:
    def __init__(self) -> None:
        self.dong: list[tuple[str, str, str, str]] = []
        self.sai = 0

    def so(self, ten: str, trong_md, trong_json) -> None:
        ok = trong_md is not None and trong_md == trong_json
        self.sai += 0 if ok else 1
        self.dong.append((ten, _hien(trong_md), _hien(trong_json), "đúng" if ok else "**SAI**"))

    def ds(self, ten: str, md_list, js_list) -> None:
        ok = list(md_list) == list(js_list)
        self.sai += 0 if ok else 1
        self.dong.append((ten, f"{len(md_list)} mục", f"{len(js_list)} mục", "đúng" if ok else "**SAI**"))


def kiem_so(md: str, du_lieu: dict) -> tuple[list[tuple[str, str, str, str]], int]:
    """So từng bảng của `.md` với JSON."""
    tong = du_lieu["tong"]
    kq = KetQua()

    def bat(mau: str):
        m = re.search(mau, md)
        return _so(m.group(1)) if m else None

    kq.so("Issue đang mở", bat(r"Issue đang mở \| \*\*(\d+)\*\*"), tong["issue_mo"])
    kq.so("Nhãn crash-moi", bat(r"Issue nhãn `crash-moi` \| \*\*(\d+)\*\*"), tong["nhan_crash"])
    kq.so("Không nhãn", bat(r"Issue \*\*không\*\* nhãn \| \*\*(\d+)\*\*"), tong["khong_nhan"])
    kq.so("Có khối aow-crash-v100", bat(r"có khối `aow-crash-v100` \| \*\*(\d+)\*\*"), tong["co_khoi"])
    kq.so("Tổng lượt", bat(r"\*\*Tổng lượt\*\* \| \*\*(\d+)\*\*"), tong["tong_luot"])
    kq.so("Số nhóm", bat(r"Nhóm lõi/loại \| \*\*(\d+)\*\*"), tong["so_nhom"])

    # Bảng nhóm lõi/loại (4.1 có cột #, 4.2 không có)
    rows = re.findall(r"\| (?:\d+ \| )?`([a-z0-9_-]+)` / `([a-z0-9_-]+)` \| (\d+) \| (\d+) \|", md)
    md_nhom = {(l, t): (int(i), int(u)) for l, t, i, u in rows}
    js_nhom = {(g["loi"], g["loai"]): (g["issue"], g["luot"]) for g in du_lieu["nhom"]}
    kq.so("Nhóm: số dòng bảng", len(md_nhom), len(js_nhom))
    kq.ds("Nhóm: đúng cặp lõi/loại", sorted(md_nhom), sorted(js_nhom))
    kq.ds("Nhóm: đúng (issue, lượt) từng dòng", [md_nhom[k] for k in sorted(md_nhom)],
          [js_nhom[k] for k in sorted(js_nhom)])
    kq.so("Nhóm: tổng issue", sum(i for i, _ in md_nhom.values()), sum(i for i, _ in js_nhom.values()))
    kq.so("Nhóm: tổng lượt", sum(u for _, u in md_nhom.values()), sum(u for _, u in js_nhom.values()))

    # Bảng theo lõi
    rows = re.findall(r"\| `([a-z0-9_-]+)` \| (\d+) \| (\d+) \|", md)
    md_loi = {l: (int(i), int(u)) for l, i, u in rows}
    js_loi = {c["loi"]: (c["issue"], c["luot"]) for c in du_lieu["theo_loi"]}
    kq.ds("Theo lõi: danh sách lõi", sorted(md_loi), sorted(js_loi))
    kq.ds("Theo lõi: đúng (issue, lượt)", [md_loi[k] for k in sorted(md_loi)],
          [js_loi[k] for k in sorted(js_loi)])
    kq.so("Theo lõi: tổng issue", sum(i for i, _ in md_loi.values()), tong["co_khoi"])
    kq.so("Theo lõi: tổng lượt", sum(u for _, u in md_loi.values()), tong["tong_luot"])

    # 23 issue đã đóng
    d1911 = du_lieu["dong_1911"]
    so_md = [int(n) for n in re.findall(r"\| \[(\d+)\]\(https://github\.com/[^)]+/issues/\1\)", md)]
    so_js = [x["number"] for x in d1911["issues"]]
    kq.ds("Đã đóng: danh sách issue", sorted(so_md), sorted(so_js))
    kq.so("Đã đóng: số issue", len(so_md), d1911["so_issue"])
    kq.so("Đã đóng: cộng lượt các issue", sum(x["luot"] for x in d1911["issues"]), d1911["tong_luot"])

    # Chờ bản ổn định 0.8.15
    cho = du_lieu["cho_ban_on_dinh"]
    kq.so("Chờ 0.8.15: số issue", bat(r"\*\*(\d+) issue / \d+ lượt"), cho["so_issue"])
    kq.so("Chờ 0.8.15: tổng lượt", bat(r"\*\*\d+ issue / (\d+) lượt"), cho["tong_luot"])
    kq.so("Chờ 0.8.15: cộng lượt các issue", sum(x["luot"] for x in cho["issues"]), cho["tong_luot"])

    # Comment đóng + PM duyệt
    cd = d1911.get("comment_dong") or {}
    if cd:
        kq.so("Comment đóng: số issue = tổng", cd.get("so_issue"), cd.get("tong_issue"))
        kq.so("Comment đóng: .md ghi '23/23'", "23/23" in md, True)
    pm = d1911.get("pm_duyet") or {}
    if pm:
        kq.so("PM duyệt: id comment có trong .md", str(pm.get("comment_id")) in md, True)

    # Phụ lục A
    a = du_lieu["phu_luc_a"]
    kq.so("Phụ lục A: số issue", bat(r"\*\*(\d+) issue, \d+ lượt\*\*"), a["so_issue"])
    kq.so("Phụ lục A: tổng lượt", bat(r"\*\*\d+ issue, (\d+) lượt\*\*"), a["tong_luot"])
    kq.so("Phụ lục A: cộng lượt các issue", sum(x["luot"] for x in a["issues"]), a["tong_luot"])

    return kq.dong, kq.sai


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
    ap = argparse.ArgumentParser(description="Kiểm `.md` ↔ `.json` của bảng tồn issue")
    ap.add_argument("--md", default=str(MAC_DINH_MD))
    ap.add_argument("--json", default=str(MAC_DINH_JSON))
    args = ap.parse_args(argv)

    md_path, json_path = Path(args.md), Path(args.json)
    if not md_path.is_file() or not json_path.is_file():
        print(f"THIẾU TỆP: {md_path} / {json_path}", file=sys.stderr)
        return 1

    md = doc_md(md_path)
    du_lieu = doc_json(json_path)

    dong_khoa, sai_khoa = kiem_khoa(md, du_lieu)
    in_bang("1. Khoá nêu trong .md (đối chiếu khoá thật của .json)",
            ("khoá trong .md", "có trong .json", "đúng/sai"), dong_khoa)

    dong_so, sai_so = kiem_so(md, du_lieu)
    in_bang("2. Con số: .md ↔ .json", ("chỉ số", "trong .md", "trong .json", "đúng/sai"), dong_so)

    print(f"\nKhoá: {len(dong_khoa)} dòng, sai {sai_khoa} · Số: {len(dong_so)} dòng, sai {sai_so}")
    if sai_khoa or sai_so:
        print("FAIL: .md và .json lệch (xem dòng **SAI** ở trên)")
        return 1
    print("PASS: khoá và số của .md khớp .json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
