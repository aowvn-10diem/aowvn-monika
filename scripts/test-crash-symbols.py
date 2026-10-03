#!/usr/bin/env python3
"""Kiểm D1 bằng ELF thật; trên CI kiểm thêm R8 và llvm-symbolizer thật."""
import argparse
import csv
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent


def run(*args):
    return subprocess.run([str(arg) for arg in args], check=True,
                          capture_output=True, text=True).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-tools", action="store_true")
    args = parser.parse_args()
    jars = sorted((Path.home() / ".gradle/caches/modules-2/files-2.1/com.android.tools/r8").glob("*/*/r8-*.jar"))
    jar = Path(os.environ["R8_JAR"]) if os.environ.get("R8_JAR") else (jars[-1] if jars else None)
    symbolizer = shutil.which("llvm-symbolizer")
    if not symbolizer and os.environ.get("ANDROID_HOME"):
        found = sorted(Path(os.environ["ANDROID_HOME"]).glob("ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-symbolizer"))
        symbolizer = str(found[-1]) if found else None
    if args.require_tools and (jar is None or not symbolizer):
        raise SystemExit("Thiếu R8 JAR hoặc llvm-symbolizer cho kiểm D1 trên CI")
    with tempfile.TemporaryDirectory(prefix="monika-d1-test-") as directory:
        root = Path(directory)
        original = root / "original"; original.mkdir()
        wrong = root / "wrong"; wrong.mkdir()
        pack = root / "pack"; pack.mkdir()
        source = root / "fixture.c"
        source.write_text("int monika_fixture(void) { return 42; }\n")
        run("gcc", "-shared", "-fPIC", "-g", "-Wl,--build-id", source, "-o", original / "libfixture.so")
        other = root / "wrong.c"; other.write_text("int other_fixture(void) { return 7; }\n")
        run("gcc", "-shared", "-fPIC", "-g", "-Wl,--build-id", other, "-o", wrong / "libfixture.so")
        shutil.copyfile(original / "libfixture.so", pack / "libfixture.so")
        run("strip", "--strip-unneeded", pack / "libfixture.so")
        archive = root / "symbols.zip"
        run("python3", SCRIPTS / "package-native-symbols.py", "--pack", pack,
            "--source-root", wrong, "--source-root", original, "--abi", "fixture-host", "--output", archive)
        symbols = root / "symbols"
        with zipfile.ZipFile(archive) as z:
            z.extractall(symbols)
        with (symbols / "build-ids.tsv").open() as f:
            row = next(csv.DictReader(f, delimiter="\t"))
        assert row["level"] == "debug", row
        selected = symbols / row["symbols"]
        assert selected.read_bytes() == (original / "libfixture.so").read_bytes()
        assert ".debug_info" not in run("readelf", "-S", pack / "libfixture.so")
        print("Đạt: giữ ELF trước strip; BuildId khớp; bỏ ELF cùng tên nhưng khác BuildId.")
        pc = next(line.split()[0] for line in run("nm", "-D", selected).splitlines() if line.endswith(" monika_fixture"))
        report = root / "native.json"
        report.write_text(json.dumps({"kind":"native","detail":
            f"#00 pc {pc} /data/app/libfixture.so (BuildId: {row['build_id']})"}))
        if symbolizer:
            output = run("bash", SCRIPTS / "crash-reports.sh", "--file", report,
                         "--symbols-dir", symbols, "--symbolizer", symbolizer)
            assert "monika_fixture" in output and "fixture.c:" in output, output
            report.write_text(json.dumps({"kind":"native","detail":
                f"#00 pc {pc} /data/app/libfixture.so (BuildId: deadbeef)"}))
            output = run("bash", SCRIPTS / "crash-reports.sh", "--file", report,
                         "--symbols-dir", symbols, "--symbolizer", symbolizer)
            assert "Không có ký hiệu duy nhất" in output and "Native đã giải ký hiệu" not in output
            print("Đạt: llvm-symbolizer ra tên hàm/dòng; không dùng ký hiệu sai BuildId.")
        else:
            print("[CHƯA KIỂM] llvm-symbolizer thật: thiếu công cụ cục bộ; CI bắt buộc kiểm.")
        if jar:
            mapping = root / "mapping.txt"
            mapping.write_text("vn.aow.monika.diag.Fixture -> a:\n    1:1:void crash():12:12 -> a\n")
            report = root / "java.json"
            report.write_text(json.dumps({"kind":"java","app":"0.7.4 (39) release",
                "detail":"java.lang.RuntimeException: fixture\n    at a.a(Fixture.java:1)\n"}))
            output = run("bash", SCRIPTS / "crash-reports.sh", "--file", report,
                         "--mapping", mapping, "--r8-jar", jar)
            assert "vn.aow.monika.diag.Fixture.crash(Fixture.java:12)" in output, output
            print("Đạt: R8 retrace ra tên lớp/hàm và dòng gốc.")
        else:
            print("[CHƯA KIỂM] R8 retrace thật: thiếu JAR cục bộ; CI bắt buộc kiểm.")


if __name__ == "__main__":
    main()
