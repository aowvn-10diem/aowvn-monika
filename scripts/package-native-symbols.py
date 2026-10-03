#!/usr/bin/env python3
"""Giữ ELF tốt nhất cùng BuildId với thư viện thực sự đóng gói (V20/D1)."""
import argparse
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


def elf_info(path, readelf):
    notes = subprocess.run([readelf, "-n", str(path)], check=True,
                           capture_output=True, text=True).stdout
    match = re.search(r"Build ID:\s*([0-9a-fA-F]+)", notes)
    sections = subprocess.run([readelf, "-S", str(path)], check=True,
                              capture_output=True, text=True).stdout
    level = "debug" if ".debug_info" in sections else (
        "symtab" if ".symtab" in sections else "dynamic-only")
    return (match.group(1).lower() if match else "", level)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, action="append", default=[])
    parser.add_argument("--readelf", default="readelf")
    parser.add_argument("--abi", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    libraries = sorted(args.pack.rglob("*.so"))
    if not libraries:
        parser.error("Gói không có thư viện .so")
    candidates = {}
    for root in args.source_root:
        if not root.exists():
            continue
        for path in root.rglob("*.so"):
            candidates.setdefault(path.name, []).append(path)
    rank = {"dynamic-only": 0, "symtab": 1, "debug": 2}
    rows = ["build_id\tfile\tabi\tsymbols\tlevel"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Không để lại ZIP dở nếu đọc ELF hoặc đóng gói lỗi.
    with tempfile.TemporaryDirectory(dir=args.output.parent) as tmp:
        output = Path(tmp) / "symbols.zip"
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            for deployed in libraries:
                build_id, level = elf_info(deployed, args.readelf)
                if not build_id:
                    raise SystemExit(f"Thiếu BuildId: {deployed.name}; không ghép theo tên file")
                best = deployed
                for candidate in candidates.get(deployed.name, []):
                    try:
                        other_id, other_level = elf_info(candidate, args.readelf)
                    except subprocess.CalledProcessError:
                        continue  # Có thể là ELF cho ABI khác.
                    if other_id == build_id and rank[other_level] > rank[level]:
                        best, level = candidate, other_level
                relative = f"libs/{build_id}/{deployed.name}"
                archive.write(best, relative)
                rows.append(f"{build_id}\t{deployed.name}\t{args.abi}\t{relative}\t{level}")
                print(f"{build_id} {deployed.name} {level}")
                if level == "dynamic-only":
                    print(f"::warning::{deployed.name}: nguồn chỉ có ELF đã cắt ký hiệu; tên hàm/dòng [CHƯA KIỂM]")
            archive.writestr("build-ids.tsv", "\n".join(rows) + "\n")
            archive.writestr("README.txt",
                "V20/D1: BuildId ghép đúng ELF được đóng gói.\n"
                "debug: có DWARF; symtab: có bảng tên, chưa chắc có dòng; "
                "dynamic-only: không phục hồi được ký hiệu đã mất từ upstream.\n"
                "Giải nén, truyền thư mục cho crash-reports.sh --symbols-dir.\n")
        output.replace(args.output)


if __name__ == "__main__":
    main()
