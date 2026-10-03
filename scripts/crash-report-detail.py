#!/usr/bin/env python3
"""In báo cáo, retrace Java và giải ký hiệu native bằng BuildId (V20/D1)."""
import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path


def note(message):
    print("[CHƯA KIỂM] " + message)


def mapping_for(report, explicit, work):
    if explicit:
        return explicit
    match = re.fullmatch(r"(\d+\.\d+\.\d+(?:[.-][\w.-]+)?) \(\d+\) release",
                         report.get("app", ""))
    if not match or not shutil.which("gh"):
        note("Chưa có mapping đúng bản release; dùng --mapping.")
        return None
    tag = "v" + match.group(1)
    name = "mapping-" + tag + ".txt"
    result = subprocess.run(["gh", "release", "download", tag, "--repo",
        "aowvn-10diem/aowvn-monika", "--pattern", name, "--dir", str(work)],
        capture_output=True)
    if result.returncode:
        note(f"Release {tag} chưa tải được {name}; không dùng mapping bản khác.")
        return None
    return work / name


def retrace(report, args, work):
    if report.get("kind") != "java":
        return
    mapping = mapping_for(report, args.mapping, work)
    if mapping is None:
        return
    jar = args.r8_jar or (Path(os.environ["R8_JAR"]) if os.environ.get("R8_JAR") else None)
    if jar is None:
        jars = sorted((Path.home() / ".gradle/caches/modules-2/files-2.1/com.android.tools/r8").glob("*/*/r8-*.jar"))
        jar = jars[-1] if jars else None
    if jar is None or not jar.is_file() or not shutil.which("java"):
        note("Thiếu Java/R8 JAR; đặt R8_JAR hoặc --r8-jar đúng phiên bản compiler của mapping.")
        return
    if not mapping.is_file():
        note("Không tìm thấy mapping được chỉ định.")
        return
    trace = work / "stack.txt"
    trace.write_text(report.get("detail", ""), encoding="utf-8")
    result = subprocess.run(["java", "-cp", str(jar),
        "com.android.tools.r8.retrace.Retrace", str(mapping), str(trace)],
        capture_output=True, text=True)
    if result.returncode:
        note("R8 retrace thất bại; kiểm phiên bản R8 và mapping. Giữ báo cáo gốc ở trên.")
        return
    print("\n-- Java đã retrace --")
    print(result.stdout.rstrip())


def symbolize(report, args):
    if report.get("kind") != "native":
        return
    tool = args.symbolizer or shutil.which("llvm-symbolizer")
    root = args.symbols_dir
    if root is None or not tool:
        note("Native: cần --symbols-dir (ZIP ký hiệu đã giải nén) và llvm-symbolizer.")
        return
    index = root / "build-ids.tsv"
    if not index.is_file():
        note("Thiếu build-ids.tsv trong thư mục ký hiệu.")
        return
    with index.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    # Dạng khung của tombstone Android; không đoán PC hoặc dùng ký hiệu khác BuildId.
    frame = re.compile(r"#\d+\s+pc\s+([0-9a-fA-F]+)\s+(\S+).*?\(BuildId:\s*([0-9a-fA-F]+)\)", re.I)
    count = 0
    for line in report.get("detail", "").splitlines():
        match = frame.search(line)
        if not match:
            continue
        pc, file, build_id = match.groups()
        name = Path(file).name
        matches = [row for row in rows if row["build_id"].lower() == build_id.lower() and row["file"] == name]
        if len(matches) != 1:
            note(f"Không có ký hiệu duy nhất cho {name}, BuildId {build_id}.")
            continue
        row = matches[0]
        obj = (root / row["symbols"]).resolve()
        if not obj.is_relative_to(root.resolve()) or not obj.is_file():
            note("Đường dẫn ký hiệu không hợp lệ.")
            continue
        if row.get("level") == "dynamic-only":
            note(f"{name}: upstream chỉ có ký hiệu động; có thể không ra tên hàm/dòng.")
        result = subprocess.run([str(tool), "--obj=" + str(obj),
            "--inlines", "--demangle", "0x" + pc], capture_output=True, text=True)
        if result.returncode:
            note(f"Giải ký hiệu thất bại cho {name}.")
            continue
        print("\n-- Native đã giải ký hiệu --")
        print(line)
        print(result.stdout.rstrip())
        count += 1
    if not count:
        note("Chưa giải được khung native; cần rel_pc/file_name/BuildId từ tombstone đúng cấu trúc (V20 sau V19).")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path, required=True)
    parser.add_argument("--mapping", type=Path)
    parser.add_argument("--r8-jar", type=Path)
    parser.add_argument("--symbols-dir", type=Path)
    parser.add_argument("--symbolizer")
    args = parser.parse_args()
    report = json.loads(args.file.read_text(encoding="utf-8"))
    session = report.get("session") or {}
    print(report.get("title", "Báo cáo"))
    print("Máy:", report.get("device", ""))
    print("Bản app:", report.get("app", ""))
    print("Lõi:", session.get("core"), "|", session.get("coreInfo"))
    print("Game:", session.get("game"), "| Hệ:", session.get("system"), "| Giai đoạn:", session.get("stage"))
    print("Lý do:", report.get("reason", ""))
    print("\n" + report.get("detail", ""))
    print("\n-- log --\n" + "\n".join(report.get("log") or []))
    with tempfile.TemporaryDirectory(prefix="monika-crash-") as directory:
        work = Path(directory)
        retrace(report, args, work)
        symbolize(report, args)


if __name__ == "__main__":
    main()
