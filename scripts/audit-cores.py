#!/usr/bin/env python3
"""Kiểm tra mọi lõi libretro trong config/monika-config.json: link còn sống, đúng ABI, phụ thuộc, tùy chọn (khóa/giá trị) có thật trong lõi.
Chạy: python3 scripts/audit-cores.py [abi ...]   (mặc định arm64-v8a armeabi-v7a x86_64)
Cần mạng + readelf/strings (binutils). Thoát mã 1 nếu có lỗi chắc chắn."""
import json, subprocess, sys, tempfile, urllib.request, zipfile, io, re, os

ABIS = sys.argv[1:] or ["arm64-v8a", "armeabi-v7a", "x86_64"]
cfg = json.load(open(os.path.join(os.path.dirname(__file__), "..", "config", "monika-config.json")))
errors, warns = [], []

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "monika-audit"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read(), r.headers.get("Last-Modified", "")

def so_from_zip(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    names = [n for n in z.namelist() if n.endswith(".so")]
    if not names: raise ValueError("gói không có .so")
    return z.read(names[0])

def run(cmd, path):
    return subprocess.run(cmd + [path], capture_output=True, text=True).stdout

rows = []
for cid, d in cfg["cores"].items():
    abis = d.get("abis") or ABIS
    for abi in ABIS:
        if abi not in abis:
            continue
        url = d["url"].replace("{abi}", abi)
        if not url.startswith("http"):
            continue
        try:
            data, lm = fetch(url)
            so = so_from_zip(data)
        except Exception as e:
            errors.append(f"{cid}/{abi}: không tải được ({e})"); rows.append((cid, abi, "LỖI", str(e))); continue
        with tempfile.NamedTemporaryFile(suffix=".so", delete=False) as f:
            f.write(so); p = f.name
        needed = re.findall(r"Shared library: \[(.+?)\]", run(["readelf", "-d"], p))
        strs = run(["strings", "-a"], p)
        missing_libs = [n for n in needed if n not in ("libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libGLESv2.so", "libGLESv3.so", "libEGL.so", "libandroid.so", "libOpenSLES.so", "libstdc++.so", "libc++_shared.so", "libvulkan.so", "libmediandk.so", "libnativewindow.so")]
        # Tùy chọn trong config: khóa phải có thật trong lõi; giá trị phải nằm trong chuỗi của lõi.
        bad = []
        opts = list((d.get("options") or {}).items())
        for tier, tier_opts in (d.get("perf") or {}).items():
            opts += [(k, v) for k, v in tier_opts.items()]
        for k, v in opts:
            if k not in strs: bad.append(f"khóa {k} không có trong lõi")
            elif not re.search(r"(^|[^A-Za-z0-9_])" + re.escape(v) + r"([^A-Za-z0-9_]|$)", strs, re.M): bad.append(f"giá trị '{v}' của {k} không thấy trong lõi")
        ident = run(["readelf", "-n"], p)
        api = re.search(r"API level:\s*(\d+)", ident)
        note = []
        if missing_libs: note.append("cần thêm: " + ",".join(missing_libs)); warns.append(f"{cid}/{abi}: phụ thuộc {missing_libs}")
        if bad: note += bad; errors += [f"{cid}/{abi}: {b}" for b in bad]
        if api and int(api.group(1)) > 26: note.append(f"NDK API {api.group(1)} (> minSdk 26)"); warns.append(f"{cid}/{abi}: NDK API {api.group(1)}")
        rows.append((cid, abi, "ok" if not bad else "LỖI", f"{len(so)//1024}KB · {lm[:16]} · " + "; ".join(note)))
        os.unlink(p)

w = max(len(r[0]) for r in rows) + 1
for cid, abi, st, note in rows:
    print(f"{cid:<{w}} {abi:<12} {st:<5} {note}")
print()
print(f"{len(rows)} lõi/ABI đã kiểm, {len(errors)} lỗi, {len(warns)} cảnh báo")
for e in errors: print("LỖI:", e)
for x in warns: print("CẢNH BÁO:", x)
sys.exit(1 if errors else 0)
