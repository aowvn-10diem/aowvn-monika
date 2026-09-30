#!/usr/bin/env python3
"""Kiểm tra mọi lõi libretro trong config/monika-config.json: link còn sống, đúng ABI, phụ thuộc, tùy chọn (khóa/giá trị) có thật trong lõi.
Chạy: python3 scripts/audit-cores.py [abi ...]   (mặc định arm64-v8a armeabi-v7a x86_64)
Cần mạng + readelf/strings (binutils). Thoát mã 1 nếu có lỗi chắc chắn."""
import json, subprocess, sys, tempfile, urllib.request, zipfile, io, re, os

ABIS = sys.argv[1:] or ["arm64-v8a", "armeabi-v7a", "x86_64"]
HERE = os.path.dirname(os.path.abspath(__file__))
OPTDUMP = None  # tệp chạy dò tùy chọn (dựng từ scripts/optdump.c nếu có gcc)

def build_optdump():
    """Khung libretro tí hon: nạp lõi Linux x86_64 (cùng mã nguồn với bản Android), cho nó khai tùy chọn rồi in ra. Cho kết quả CHÍNH XÁC."""
    global OPTDUMP
    out = os.path.join(tempfile.gettempdir(), "monika-optdump")
    lib = os.path.join(HERE, "libretro.h")
    try:
        if not os.path.exists(lib):
            urllib.request.urlretrieve("https://raw.githubusercontent.com/libretro/libretro-common/master/include/libretro.h", lib)
        subprocess.run(["gcc", "-I", HERE, "-o", out, os.path.join(HERE, "optdump.c"), "-ldl"], check=True, capture_output=True)
        OPTDUMP = out
    except Exception as e:
        print("Không dựng được optdump (bỏ kiểm chính xác, dùng kiểm chuỗi):", e)

ROM = {"gba": "https://raw.githubusercontent.com/jsmolka/gba-tests/master/ppu/hello.gba"}
def exact_defs(cid):
    """{khóa: (mặc định, [giá trị])} do chính lõi khai, hoặc None nếu không chạy được trên Linux."""
    if not OPTDUMP: return None
    try:
        data, _ = fetch(f"https://buildbot.libretro.com/nightly/linux/x86_64/latest/{cid}_libretro.so.zip")
        z = zipfile.ZipFile(io.BytesIO(data)); n = [x for x in z.namelist() if x.endswith(".so")][0]
        with tempfile.TemporaryDirectory() as td:
            so = os.path.join(td, "core.so"); open(so, "wb").write(z.read(n))
            rom = []
            if cid == "mgba":  # mGBA chỉ khai tùy chọn sau khi nạp game
                rp = os.path.join(td, "t.gba"); open(rp, "wb").write(fetch(ROM["gba"])[0]); rom = [rp]
            r = subprocess.run([OPTDUMP, so] + rom, capture_output=True, text=True, timeout=60)
    except Exception:
        return None
    defs = {}
    for m in re.finditer(r"^OPT (\S+) default=(\S*) values=(.*)$", r.stdout, re.M):
        defs[m.group(1)] = (m.group(2), m.group(3).split("|"))
    for m in re.finditer(r"^VAR (\S+) = [^;]*; (.*)$", r.stdout, re.M):
        vals = [x.strip() for x in m.group(2).split("|")]
        defs[m.group(1)] = (vals[0], vals)
    return defs or None
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

build_optdump()
rows = []
exact = {}
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
        for style in ((d.get("display") or {}).get("styles") or {}).values():
            opts += [(k, v) for k, v in (style.get("options") or {}).items()]
        if cid not in exact: exact[cid] = exact_defs(cid)
        defs = exact[cid]
        for k, v in opts:
            if defs is not None:
                # Đối chiếu với định nghĩa lõi tự khai (chính xác)
                if k not in defs: bad.append(f"khóa {k} lõi không khai")
                elif v not in defs[k][1]: bad.append(f"giá trị '{v}' của {k} không hợp lệ (lõi cho: {'|'.join(defs[k][1][:8])})")
            elif k not in strs: bad.append(f"khóa {k} không có trong lõi")
            elif v not in strs: warns.append(f"{cid}: giá trị '{v}' của {k} chưa xác nhận được (lõi không chạy được trên Linux)")
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
print(f"{len(rows)} lõi/ABI đã kiểm, {len(errors)} lỗi, {len(warns)} cảnh báo · kiểm chính xác bằng chính lõi: {sorted(c for c, v in exact.items() if v)}")
for e in errors: print("LỖI:", e)
for x in warns: print("CẢNH BÁO:", x)
sys.exit(1 if errors else 0)
