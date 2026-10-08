#!/usr/bin/env python3
"""V45: điền mục `modules.renpy8` và bật engine nhúng Ren'Py trong config từ gói đã dựng.

Không đăng gói, không gọi API ghi, không đụng secret. Chỉ đọc gói (file cục bộ hoặc link HTTPS công khai), tính SHA-256 và
kích thước thật rồi ghi vào config theo đúng khuôn các gói khác (`sha256ByAbi`, `sizeByAbi`, url có `{abi}`).

Dùng sau khi PM đăng gói (workflow "Build gói Ren'Py", publish = true):
  python3 scripts/renpy8-config.py --version 8.5.3-r17 --abis arm64-v8a \\
      --url 'https://github.com/aowvn-10diem/aowvn-monika-packs/releases/download/engines-renpy8-{abi}-17/renpy8-{abi}.zip' \\
      --fetch --write
Hoặc với gói đã tải về: thay `--fetch` bằng `--zip arm64-v8a=/đường/dẫn/renpy8-arm64-v8a.zip`.
Không có `--write` thì chỉ in config mới ra stdout. Ghi vào file thì tăng `configVersion` thêm 1.
"""
import argparse
import hashlib
import json
import re
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ALLOWED_ABIS = ("arm64-v8a", "armeabi-v7a")
MAIN_LIB = "librenpython.so"
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def zip_facts(path: Path, abi: str) -> dict:
    """SHA-256 + kích thước của gói, kèm kiểm manifest: đúng engine và đúng ABI."""
    data = path.read_bytes()
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        if MAIN_LIB not in names:
            raise ValueError(f"{path.name}: thiếu {MAIN_LIB}")
        if "manifest.json" not in names:
            raise ValueError(f"{path.name}: thiếu manifest.json")
        manifest = json.loads(archive.read("manifest.json"))
    if manifest.get("engine") != "renpy":
        raise ValueError(f"{path.name}: manifest.engine phải là 'renpy', thấy {manifest.get('engine')!r}")
    if manifest.get("abi") != abi:
        raise ValueError(f"{path.name}: manifest.abi={manifest.get('abi')!r}, mong {abi!r}")
    return {"sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}


def apply_renpy8(config: dict, *, version: str, url: str, abis: list, facts: dict, enable_engine: bool = True,
                 config_version: int = None) -> dict:
    """Trả config mới (bản sao). `facts` = {abi: {"sha256", "size"}}. configVersion +1, hoặc `config_version` nếu cho (phải lớn hơn bản cũ)."""
    if not abis or any(abi not in ALLOWED_ABIS for abi in abis):
        raise ValueError(f"abis phải là tập con không rỗng của {ALLOWED_ABIS}")
    if set(facts) != set(abis):
        raise ValueError("facts phải có đúng các ABI trong abis")
    if not url.startswith("https://"):
        raise ValueError("url gói phải là HTTPS")
    if len(abis) > 1 and "{abi}" not in url:
        raise ValueError("nhiều ABI thì url phải có {abi}")
    if not version.strip():
        raise ValueError("version không được trống (đổi version = app tải lại gói)")
    for abi, fact in facts.items():
        if not HEX64.match(fact["sha256"]):
            raise ValueError(f"{abi}: sha256 phải là 64 ký tự hex thường")
        if not isinstance(fact["size"], int) or fact["size"] <= 0:
            raise ValueError(f"{abi}: size phải là số nguyên dương")
    new = json.loads(json.dumps(config))
    old_version = int(config["configVersion"])
    if config_version is not None and config_version <= old_version:
        raise ValueError("config_version phải lớn hơn configVersion hiện tại")
    new["configVersion"] = config_version if config_version is not None else old_version + 1
    new.setdefault("modules", {})["renpy8"] = {
        "version": version,
        "url": url,
        "abis": list(abis),
        "sha256ByAbi": {abi: facts[abi]["sha256"] for abi in abis},
        "sizeByAbi": {abi: facts[abi]["size"] for abi in abis},
    }
    if enable_engine:
        systems = [s for s in new.get("systems", []) if s.get("id") == "renpy"]
        if len(systems) != 1:
            raise ValueError("config phải có đúng một hệ id='renpy'")
        # Giữ runner/externalApp (JoiPlay dự phòng khi máy không hỗ trợ ABI hoặc gói chưa dùng được).
        systems[0]["engine"] = "renpy"
    return new


def dump(config: dict) -> str:
    return json.dumps(config, indent=2, ensure_ascii=False) + "\n"


def fetch_zip(url: str, abi: str, directory: Path) -> Path:
    target = directory / f"renpy8-{abi}.zip"
    request = urllib.request.Request(url.replace("{abi}", abi), headers={"User-Agent": "monika-renpy8-config"})
    with urllib.request.urlopen(request, timeout=120) as response, target.open("wb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)
    return target


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", type=Path, default=Path("config/monika-config.json"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--abis", nargs="+", default=["arm64-v8a"])
    parser.add_argument("--zip", action="append", default=[], metavar="ABI=PATH", help="gói cục bộ cho từng ABI")
    parser.add_argument("--fetch", action="store_true", help="tải gói từ url (HTTPS công khai) để tính SHA-256/size")
    parser.add_argument("--config-version", type=int, help="đặt configVersion thay vì +1 (CI dùng số lớn để config từ xa không ghi đè)")
    parser.add_argument("--no-engine", action="store_true", help="chỉ điền modules.renpy8, chưa bật systems[renpy].engine")
    parser.add_argument("--write", action="store_true", help="ghi vào --config (mặc định chỉ in ra stdout)")
    parser.add_argument("--out", type=Path, help="ghi ra file khác thay vì --config")
    args = parser.parse_args(argv)

    config = json.loads(args.config.read_text(encoding="utf-8"))
    local = {}
    for item in args.zip:
        abi, _, path = item.partition("=")
        local[abi] = Path(path)
    facts = {}
    with tempfile.TemporaryDirectory() as temporary:
        for abi in args.abis:
            if abi in local:
                path = local[abi]
            elif args.fetch:
                path = fetch_zip(args.url, abi, Path(temporary))
            else:
                parser.error(f"thiếu gói cho {abi}: dùng --zip {abi}=PATH hoặc --fetch")
            facts[abi] = zip_facts(path, abi)
    new = apply_renpy8(config, version=args.version, url=args.url, abis=args.abis, facts=facts, enable_engine=not args.no_engine,
                         config_version=args.config_version)
    text = dump(new)
    if args.out:
        args.out.write_text(text, encoding="utf-8")
    elif args.write:
        args.config.write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    print(f"configVersion {config['configVersion']} -> {new['configVersion']}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
