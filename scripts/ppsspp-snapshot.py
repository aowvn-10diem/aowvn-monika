#!/usr/bin/env python3
"""V198: chuẩn bị snapshot PPSSPP; chỉ PM dispatch từ main mới được đăng."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = "aowvn-10diem/aowvn-monika"
PACKS = REPO + "-packs"
ABIS = ("arm64-v8a", "armeabi-v7a")


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def tag_for(number):
    if not re.fullmatch(r"[1-9][0-9]{0,8}", number):
        raise ValueError("Cần số snapshot mới, nguyên dương (tối đa 9 chữ số)")
    return "cores-ppsspp-" + number


def guard():
    if (os.environ.get("GITHUB_REPOSITORY"), os.environ.get("GITHUB_EVENT_NAME"),
            os.environ.get("GITHUB_REF")) != (REPO, "workflow_dispatch", "refs/heads/main"):
        raise ValueError("Chỉ PM dispatch từ main của repo chính")
    if not re.fullmatch(r"[0-9a-f]{40}", os.environ.get("GITHUB_SHA", "")):
        raise ValueError("Thiếu SHA builder đầy đủ")


def gh(*args, missing_ok=False):
    result = subprocess.run(["gh", *args], capture_output=True, text=True)
    if result.returncode:
        if missing_ok and re.search(r"\bHTTP 404\b", result.stderr):
            return False
        # Không in lỗi chứa URL chuyển hướng hay thông tin xác thực.
        raise ValueError("GitHub từ chối hoặc lỗi mạng; dừng, không thử đường khác")
    return True


def unused(tag):
    for endpoint in ("releases/tags/", "git/ref/tags/"):
        if gh("api", f"repos/{PACKS}/{endpoint}{tag}", missing_ok=True):
            raise ValueError("Snapshot đã có release/tag; phải dùng số mới")


def metadata(path):
    return {"sha256": sha(path), "size": path.stat().st_size}


def files_for(core, abi):
    return {f"ppsspp-{abi}.zip", f"symbols-ppsspp-{abi}.zip", "BUILD-RECIPE.py",
            "sources.lock.json", f"ppsspp-{core['commit']}-source.tar.gz", *(
                f"ppsspp-{s['path'].replace('/', '_')}-{s['commit']}-source.tar.gz"
                for s in core["submodules"])}


def verify_files(directory, expected):
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError("Thiếu thư mục ABI")
    if {p.name for p in directory.iterdir()} != expected:
        raise ValueError("Thiếu/thừa tệp nguồn, ký hiệu hoặc checksum")
    if any(p.is_symlink() or not p.is_file() for p in directory.iterdir()):
        raise ValueError("Chỉ nhận tệp thường")


def prepare(inputs, output, number):
    tag = tag_for(number)
    lock_path = ROOT / "engines/sources.lock.json"
    lock = json.loads(lock_path.read_text())
    core = lock["cores"]["ppsspp"]
    if output.exists():
        raise ValueError("Không ghi đè thư mục snapshot")
    # Kiểm đủ cả hai ABI trước khi tạo kết quả.
    validated = []
    for abi in ABIS:
        directory = inputs / ("source-build-ppsspp-" + abi)
        expected = files_for(core, abi)
        verify_files(directory, expected | {"provenance.json", "SHA256SUMS"})
        checksums = (directory / "SHA256SUMS").read_text().splitlines()
        if len(checksums) != len(expected) + 1:
            raise ValueError("Danh sách checksum không đầy đủ")
        sums = dict(row.split("  ", 1)[::-1] for row in checksums)
        if set(sums) != expected | {"provenance.json"} or any(
                sums[n] != sha(directory / n) for n in sums):
            raise ValueError("Checksum artifact sai")
        p = json.loads((directory / "provenance.json").read_text())
        wanted = {"core": "ppsspp", "abi": abi, "commit": core["commit"],
                  "repository": core["repository"], "recipe": core["recipe"],
                  "builderCommit": os.environ["GITHUB_SHA"], "lockSha256": sha(lock_path),
                  "recipeSha256": sha(ROOT / "scripts/prepare-core-build.py"),
                  "toolchain": lock["toolchain"], "submodules": core["submodules"]}
        if any(p.get(k) != v for k, v in wanted.items()):
            raise ValueError("Nguồn/builder/toolchain không khớp main hiện tại")
        if set(p["artifacts"]) != expected or any(
                p["artifacts"][n] != metadata(directory / n) for n in expected):
            raise ValueError("Provenance không khớp tệp thật")
        if sha(directory / "sources.lock.json") != sha(lock_path) or sha(
                directory / "BUILD-RECIPE.py") != wanted["recipeSha256"]:
            raise ValueError("Thiếu đúng lock/recipe nguồn tương ứng")
        with zipfile.ZipFile(directory / f"ppsspp-{abi}.zip") as archive:
            manifest = json.loads(archive.read("manifest.json"))
            if any(manifest.get(k) != v for k, v in wanted.items()) or manifest.get("buildId") != p["buildId"]:
                raise ValueError("Manifest core không khớp provenance")
        with zipfile.ZipFile(directory / f"symbols-ppsspp-{abi}.zip") as archive:
            rows = archive.read("build-ids.tsv").decode().splitlines()
            relative = f"libs/{p['buildId']}/ppsspp_libretro_android.so"
            expected_row = f"{p['buildId']}\tppsspp_libretro_android.so\t{abi}\t{relative}\tdebug"
            if rows[1:] != [expected_row] or not archive.read(relative).startswith(b"\x7fELF"):
                raise ValueError("Ký hiệu thiếu debug hoặc sai BuildId/ABI")
        validated.append((abi, directory, p, expected))
    output.mkdir(parents=True)
    manifest = {"tag": tag, "builderCommit": os.environ["GITHUB_SHA"],
                "sourceCommit": core["commit"], "artifacts": {}}
    lines = [f"# {tag}", "", "Bản thử; game/máy thật [CHƯA KIỂM].", "",
             f"Nguồn: {core['repository']} @ {core['commit']}",
             f"Builder: https://github.com/{REPO}/commit/{os.environ['GITHUB_SHA']}", "",
             "| ABI | ZIP (byte) | SHA256 ZIP cài | BuildId |", "|---|---:|---|---|"]
    for abi, directory, provenance, expected in validated:
        pack_name, symbols_name = f"ppsspp-{abi}.zip", f"symbols-ppsspp-{abi}.zip"
        for name in (pack_name, symbols_name):
            shutil.copyfile(directory / name, output / name)
        source_name = f"sources-ppsspp-{abi}.zip"
        with zipfile.ZipFile(output / source_name, "w", zipfile.ZIP_STORED) as archive:
            for name in sorted((expected - {pack_name, symbols_name}) | {"provenance.json", "SHA256SUMS"}):
                archive.write(directory / name, name)
        entry = {**metadata(output / pack_name), "buildId": provenance["buildId"],
                 "url": f"https://github.com/{PACKS}/releases/download/{tag}/{pack_name}",
                 "symbols": {"file": symbols_name, **metadata(output / symbols_name)},
                 "sources": {"file": source_name, **metadata(output / source_name)}}
        manifest["artifacts"][abi] = entry
        lines.append(f"| {abi} | {entry['size']} | {entry['sha256']} | {entry['buildId']} |")
    shutil.copyfile(ROOT / "scripts/package-native-symbols.py", output / "package-native-symbols.py")
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (output / "notes.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (output / "SHA256SUMS").write_text("".join(f"{sha(p)}  {p.name}\n" for p in sorted(output.iterdir())))
    print("\n".join(lines))
    return manifest


def publish(output, number):
    guard()
    tag = tag_for(number)
    manifest = json.loads((output / "manifest.json").read_text())
    expected = {f"{prefix}ppsspp-{abi}.zip" for prefix in ("", "symbols-", "sources-") for abi in ABIS}
    expected |= {"manifest.json", "notes.md", "package-native-symbols.py"}
    verify_files(output, expected | {"SHA256SUMS"})
    rows = (output / "SHA256SUMS").read_text().splitlines()
    sums = dict(row.split("  ", 1)[::-1] for row in rows)
    if len(rows) != len(expected) or set(sums) != expected or any(sums[n] != sha(output / n) for n in expected):
        raise ValueError("Snapshot bị đổi sau bước kiểm")
    if manifest["tag"] != tag or manifest["builderCommit"] != os.environ["GITHUB_SHA"]:
        raise ValueError("Sai snapshot hoặc builder")
    unused(tag)
    # Tạo mới bản nháp, tải đủ tệp rồi mới hiển thị prerelease; tuyệt đối không clobber.
    # Nếu tải lỗi, giữ nguyên nháp và bỏ số này, không sửa/xóa để dùng lại.
    gh("release", "create", tag, *(str(p) for p in sorted(output.iterdir())),
       "--repo", PACKS, "--draft", "--prerelease", "--title", tag,
       "--notes-file", str(output / "notes.md"))
    gh("release", "edit", tag, "--repo", PACKS, "--draft=false", "--prerelease", "--latest=false")
    print((output / "notes.md").read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("check", "prepare", "publish"))
    parser.add_argument("--number", required=True)
    parser.add_argument("--input", type=Path, default=Path("artifacts"))
    parser.add_argument("--out", type=Path, default=Path("snapshot"))
    args = parser.parse_args()
    guard()
    tag = tag_for(args.number)
    if args.mode == "check":
        unused(tag)
    elif args.mode == "prepare":
        prepare(args.input, args.out, args.number)
    else:
        publish(args.out, args.number)


if __name__ == "__main__":
    main()
