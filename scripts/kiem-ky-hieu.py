#!/usr/bin/env python3
"""Kiểm ký hiệu lõi native (ELF) — chỉ thư viện chuẩn, không cần readelf.

Nhận tệp `.so` hoặc `.zip` chứa `.so`; in bảng: tệp, kiến trúc, build_id,
có `.symtab`, có `.debug_info`, có `.dynsym`, kích thước, kết luận.

Mã thoát:
  0 — mọi `.so` có build_id VÀ có `.symtab` (giải được ký hiệu)
  1 — có `.so` thiếu build_id hoặc thiếu `.symtab` (không chẩn đoán được)
  2 — sai cách dùng / không đọc được tệp / không phải ELF

Dùng:
  python scripts/kiem-ky-hieu.py out/core/*.so
  python scripts/kiem-ky-hieu.py citra_libretro_android.so.zip
  python scripts/kiem-ky-hieu.py --json core-a.zip > manifest.json
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
import zipfile
from pathlib import Path

ELF_MAGIC = b"\x7fELF"
SHT_SYMTAB = 2
SHT_NOTE = 7
SHT_DYNSYM = 11
PT_NOTE = 4
NT_GNU_BUILD_ID = 3
SHT_NULL = 0

MACHINES = {
    3: "x86",
    8: "mips",
    20: "ppc",
    21: "ppc64",
    22: "s390",
    40: "arm",
    62: "x86_64",
    183: "arm64",
    243: "riscv",
}

DAT = "DAT"
THIEU = "THIEU_KY_HIEU"


class Elfloi(ValueError):
    """Tệp không phải ELF đọc được."""


def _doc_muc(data, bits, e, shoff, shentsize, index):
    """Đọc một mục bảng section header: (tên_off, loại, đầu, dài)."""
    base = shoff + index * shentsize
    can = 40 if bits == 32 else 64
    if base + can > len(data):
        raise Elfloi(f"bảng section header vượt quá tệp (mục {index})")
    if bits == 64:
        name, loai, _co, _dia, dau, dai = struct.unpack_from(e + "IIQQQQ", data, base)
    else:
        name, loai, _co, _dia, dau, dai = struct.unpack_from(e + "IIIIII", data, base)
    return name, loai, dau, dai


def _build_id_trong_note(blob, e):
    """Trả danh sách build_id (hex) trong một khối note; bỏ qua note hỏng."""
    ids = []
    vi_tri = 0
    while vi_tri + 12 <= len(blob):
        namesz, descsz, loai = struct.unpack_from(e + "III", blob, vi_tri)
        vi_tri += 12
        if namesz > len(blob) - vi_tri:
            break
        ten = blob[vi_tri:vi_tri + namesz]
        vi_tri += namesz + (-namesz) % 4
        if descsz > len(blob) - vi_tri:
            break
        mo_ta = blob[vi_tri:vi_tri + descsz]
        vi_tri += descsz + (-descsz) % 4
        if loai == NT_GNU_BUILD_ID and ten.rstrip(b"\x00") == b"GNU":
            ids.append(mo_ta.hex())
    return ids


def _quet_tho(data):
    """Dự phòng: tìm mẫu note GNU build-id khi không đọc được bảng mục/đoạn."""
    ids = []
    dau = b"GNU\x00\x14\x00\x00\x00\x03\x00\x00\x00"
    vi_tri = data.find(dau)
    while vi_tri != -1:
        mo_ta = data[vi_tri + len(dau):vi_tri + len(dau) + 20]
        if len(mo_ta) == 20:
            ids.append(mo_ta.hex())
        vi_tri = data.find(dau, vi_tri + 1)
    return ids


def doc_elf(data: bytes) -> dict:
    """Đọc thông tin cần cho chẩn đoán từ nội dung một ELF."""
    if len(data) < 52 or data[:4] != ELF_MAGIC:
        raise Elfloi("không phải ELF (thiếu bốn byte \\x7fELF)")
    ei_class, ei_data = data[4], data[5]
    if ei_class not in (1, 2):
        raise Elfloi(f"ELF lạ: e_ident[EI_CLASS] = {ei_class}")
    if ei_data not in (1, 2):
        raise Elfloi(f"ELF lạ: e_ident[EI_DATA] = {ei_data}")
    bits = 32 if ei_class == 1 else 64
    e = "<" if ei_data == 1 else ">"
    thong_tin = {
        "arch": f"máy {struct.unpack_from(e + 'H', data, 18)[0]}",
        "bits": bits,
        "endian": "LE" if ei_data == 1 else "BE",
        "build_id": "",
        "symtab": False,
        "debug_info": False,
        "dynsym": False,
        "section_header": False,
    }
    may = struct.unpack_from(e + "H", data, 18)[0]
    thong_tin["arch"] = MACHINES.get(may, thong_tin["arch"])
    if bits == 64:
        phoff, shoff = struct.unpack_from(e + "QQ", data, 32)
        phentsize, phnum, shentsize, shnum, shstrndx = struct.unpack_from(e + "HHHHH", data, 54)
    else:
        phoff, shoff = struct.unpack_from(e + "II", data, 28)
        phentsize, phnum, shentsize, shnum, shstrndx = struct.unpack_from(e + "HHHHH", data, 42)

    note_ranges, ten_muc = [], []
    if shoff and shentsize:
        thong_tin["section_header"] = True
        if shnum == 0:  # SHN_XINDEX: số mục thật nằm ở sh_size của mục 0
            try:
                shnum = _doc_muc(data, bits, e, shoff, shentsize, 0)[3]
            except Elfloi:
                shnum = 0
        if shnum > 100000:
            raise Elfloi(f"bảng section header vô lý: {shnum} mục")
        muc = []
        for index in range(shnum):
            muc.append(_doc_muc(data, bits, e, shoff, shentsize, index))
        shstr = b""
        if 0 < shstrndx < len(muc):
            _ten, _loai, dau, dai = muc[shstrndx]
            shstr = data[dau:dau + dai]
        for name_off, loai, dau, dai in muc:
            if loai == SHT_NULL:
                continue
            ten = ""
            if shstr and name_off < len(shstr):
                het = shstr.find(b"\x00", name_off)
                ten = shstr[name_off:het if het != -1 else None].decode("latin-1")
            ten_muc.append(ten)
            if loai == SHT_NOTE:
                note_ranges.append((dau, dai))
            elif loai == SHT_SYMTAB or ten == ".symtab":
                thong_tin["symtab"] = True
            elif loai == SHT_DYNSYM or ten == ".dynsym":
                thong_tin["dynsym"] = True
            elif ten in (".debug_info", ".zdebug_info"):
                thong_tin["debug_info"] = True

    if phoff and phentsize and phnum:
        for index in range(phnum):
            base = phoff + index * phentsize
            if base + phentsize > len(data):
                break
            if bits == 64:
                loai, _co, dau, _dia, _fi, filesz = struct.unpack_from(e + "IIQQQQ", data, base)
            else:
                loai, dau, _dia, _fi, filesz, _co = struct.unpack_from(e + "IIIIII", data, base)
            if loai == PT_NOTE:
                note_ranges.append((dau, filesz))

    ids = []
    for dau, dai in note_ranges:
        if 0 <= dau and dai and dau + dai <= len(data):
            ids.extend(_build_id_trong_note(data[dau:dau + dai], e))
    if not ids:
        ids = _quet_tho(data)
    thong_tin["build_id"] = ids[0] if ids else ""
    return thong_tin


def doc_tep(path: Path):
    """Sinh (tên hiển thị, thông tin) cho một trường hợp .so hoặc .zip."""
    if path.suffix.lower() == ".zip":
        if not path.is_file():
            raise FileNotFoundError(f"không thấy tệp: {path}")
        with zipfile.ZipFile(path) as archive:
            ten_so = [n for n in archive.namelist() if n.lower().endswith(".so")]
            if not ten_so:
                raise Elfloi(f"{path.name}: ZIP không có tệp .so nào")
            for ten in sorted(ten_so):
                yield f"{path.name}:{ten}", doc_elf(archive.read(ten)), archive.getinfo(ten).file_size
        return
    if not path.is_file():
        raise FileNotFoundError(f"không thấy tệp: {path}")
    data = path.read_bytes()
    yield path.name, doc_elf(data), len(data)


def ket_luan(thong_tin) -> str:
    return DAT if thong_tin["build_id"] and thong_tin["symtab"] else THIEU


def _co_khong(value: bool) -> str:
    return "có" if value else "không"


def in_bang(hang) -> None:
    tieu_de = ["tệp", "kiến trúc", "build_id", ".symtab", ".debug_info", ".dynsym",
               "kích thước", "kết luận"]
    o = [[h["ten"], h["info"]["arch"], h["info"]["build_id"] or "(thiếu)",
          _co_khong(h["info"]["symtab"]), _co_khong(h["info"]["debug_info"]),
          _co_khong(h["info"]["dynsym"]), f"{h['size']:,} B".replace(",", "."),
          h["ket_luan"]] for h in hang]
    rong = [max(len(tieu_de[i]), *(len(r[i]) for r in o)) if o else len(tieu_de[i])
            for i in range(len(tieu_de))]
    dong = lambda r: "  ".join(r[i].ljust(rong[i]) for i in range(len(r))).rstrip()
    print(dong(tieu_de))
    print("  ".join("-" * w for w in rong))
    for r in o:
        print(dong(r))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="Kiểm ký hiệu giải lỗi trong .so/.zip lõi native (chỉ thư viện chuẩn).")
    parser.add_argument("tep", nargs="+", type=Path, help="tệp .so hoặc .zip chứa .so")
    parser.add_argument("--json", action="store_true", help="in JSON thay cho bảng")
    args = parser.parse_args(argv)

    hang, loi = [], []
    for path in args.tep:
        try:
            for ten, thong_tin, size in doc_tep(path):
                hang.append({"ten": ten, "info": thong_tin, "size": size,
                             "ket_luan": ket_luan(thong_tin)})
        except (FileNotFoundError, Elfloi, zipfile.BadZipFile) as error:
            loi.append(f"{path}: {error}")

    if args.json:
        print(json.dumps({"tep": [{"ten": h["ten"], **h["info"], "size": h["size"],
                                   "ket_luan": h["ket_luan"]} for h in hang],
                          "loi": loi}, ensure_ascii=False, indent=2))
    else:
        if hang:
            in_bang(hang)
        for dong_loi in loi:
            print(f"LỖI: {dong_loi}", file=sys.stderr)

    if hang and not args.json:
        du = sum(1 for h in hang if h["ket_luan"] == DAT)
        print(f"\nTổng: {len(hang)} tệp .so · đủ ký hiệu {du} · thiếu {len(hang) - du}"
              f" · lỗi {len(loi)}")

    if loi or not hang:
        return 2
    return 0 if all(h["ket_luan"] == DAT for h in hang) else 1


if __name__ == "__main__":
    raise SystemExit(main())
