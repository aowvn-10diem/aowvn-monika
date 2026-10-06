#!/usr/bin/env bash
# V45 B: official pinned engine only, CI artifact only, no publish/config/signing.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'CI only'; exit 1; }
WORK=$(realpath -m "${1:?scratch directory}")
ABI=${2:?ABI}
case "$WORK" in "$RUNNER_TEMP"/*) ;; *) echo 'Scratch must be inside RUNNER_TEMP'; exit 1;; esac
case "$ABI" in arm64-v8a|armeabi-v7a) ;; *) exit 1;; esac
REPO=$PWD
V=8.5.3
SDK_SHA=ff57648f9c04f27e381c48af6d8e3ee3cdec296bed4d3831f47f09b0a71b505e
RAPT_SHA=8a12be34a2f5238d125ff6dd76a56772fd8e44838af864f81bca82c1059b00e6
mkdir -p "$WORK"; cd "$WORK"
for f in sdk rapt; do curl -fsSL --retry 2 -o "renpy-$V-$f.zip" "https://www.renpy.org/dl/$V/renpy-$V-$f.zip"; done
echo "$SDK_SHA  renpy-$V-sdk.zip" | sha256sum -c -
echo "$RAPT_SHA  renpy-$V-rapt.zip" | sha256sum -c -
unzip -q "renpy-$V-sdk.zip"
unzip -q "renpy-$V-rapt.zip" -d "renpy-$V-sdk"
SDK="$WORK/renpy-$V-sdk"
cd "$SDK"
chmod +x renpy.sh lib/*/* 2>/dev/null || true
cat > the_question/.android.json <<'JSON'
{"package":"vn.aow.renpy.pack","name":"Pack","icon_name":"Pack","version":"1.0","numeric_version":1,"orientation":"sensorLandscape","permissions":["VIBRATE","INTERNET"],"store":"none","update_icons":true,"update_always":true,"heap_size":"3","update_keystores":false}
JSON
# Only engine bootstrap + private runtime are retained, no example game/assets.
xvfb-run -a ./renpy.sh launcher distribute the_question --package android --no-archive --packagedest "$WORK/dist" > "$WORK/distribute.log" 2>&1
cd "$WORK"
mkdir -p pack/private evidence
cp "$SDK/rapt/prototype/renpyandroid/src/main/jniLibs/$ABI/librenpython.so" pack/
cp dist/the_question.py pack/private/main.py
cp -r dist/renpy dist/lib pack/private/
find pack/private \( -name '*.rpy' -o -name '*.pyo' -o -name '*.pyx' -o -name '*.pxd' -o -name '*~' -o -name '*.bak' -o -name '*.swp' \) -delete
find pack/private -name '.*' -prune -exec rm -rf {} +
rm -rf pack/private/include
cp "$REPO/packs/renpy/environment.txt" pack/private/environment.txt
# RAPT's private runtime is source-free. PEP3147 __pycache__ files alone are
# not imported without .py sources; use the legacy sourceless .pyc layout.
python3 "$REPO/scripts/renpy-bytecode-layout.py" pack/private
test -s pack/private/renpy/bootstrap.pyc
python3 - "$WORK" "$SDK" "$ABI" "$SDK_SHA" "$RAPT_SHA" <<'PY'
import hashlib,json,re,shutil,subprocess,sys,zipfile
from pathlib import Path
work,sdk=map(Path,sys.argv[1:3]); abi,sdksha,raptsha=sys.argv[3:]
pack=work/'pack'; libs=sdk/'rapt/prototype/renpyandroid/src/main/jniLibs'/abi
system={'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libz.so','libEGL.so','libGLESv1_CM.so','libGLESv2.so','libGLESv3.so','libOpenSLES.so','libjnigraphics.so','libvulkan.so','libaaudio.so','libmediandk.so','libnativewindow.so','libcamera2ndk.so','libstdc++.so','libsync.so','libneuralnetworks.so','libOpenMAXAL.so','libamidi.so','libbinder_ndk.so'}
pending=[pack/'librenpython.so']; seen=set(); needed=set(); elfs=[]
while pending:
 lib=pending.pop()
 if lib.name in seen: continue
 seen.add(lib.name)
 dynamic=subprocess.check_output(['readelf','-d',str(lib)],text=True)
 deps=re.findall(r'\(NEEDED\).*\[([^\]]+)\]',dynamic)
 needed.update(deps)
 for dep in deps:
  if dep in system: continue
  if not re.fullmatch(r'[A-Za-z0-9_.+-]+\.so',dep): raise RuntimeError('Invalid dependency name')
  src=libs/dep
  if not src.is_file(): raise RuntimeError('Missing non-system dependency '+dep)
  dest=pack/dep
  if not dest.exists(): shutil.copy2(src,dest)
  pending.append(dest)
for lib in sorted(pack.rglob('*.so')):
 header=lib.read_bytes()[:20]
 expected=(2,183) if abi=='arm64-v8a' else (1,40)
 if header[:4]!=b'\x7fELF' or len(header)<20 or header[5]!=1 or (header[4],int.from_bytes(header[18:20],'little'))!=expected:
  raise RuntimeError('ELF ABI mismatch: '+str(lib.relative_to(pack)))
 elfs.append({'file':str(lib.relative_to(pack)),'class':header[4],'machine':int.from_bytes(header[18:20],'little')})
(pack/'needed.txt').write_text('\n'.join(sorted(needed))+'\n')
with zipfile.ZipFile(pack/'empty.zip','w'): pass
(pack/'NOTICE.txt').write_text("Ren'Py 8.5.3 — https://www.renpy.org/doc/html/license.html (MIT and LGPL components).\nOfficial SDK/RAPT unmodified native runtime; Monika environment.txt/empty.zip. CI only, not published.\n")
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
files={str(p.relative_to(pack)):{'size':p.stat().st_size,'sha256':sha(p)} for p in sorted(pack.rglob('*')) if p.is_file()}
# PackTransaction manifest.files describes required non-empty files. Python may
# have legitimate empty __init__.py files: preserve them and record separately.
manifest={'engine':'renpy','version':'8.5.3','abi':abi,'sdkSha256':sdksha,'raptSha256':raptsha,'mainLib':'librenpython.so',
          'files':{k:v for k,v in files.items() if v['size']>0},'emptyFiles':[k for k,v in files.items() if v['size']==0]}
(work/'evidence/files.json').write_text(json.dumps(files,indent=2)+'\n')
(pack/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
out=work/('renpy8-'+abi+'.zip')
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(pack.rglob('*')):
  if p.is_file():z.write(p,str(p.relative_to(pack)))
(work/'evidence/measurements.json').write_text(json.dumps({'abi':abi,'version':'8.5.3','zipBytes':out.stat().st_size,'zipSha256':sha(out),'needed':sorted(needed),'elf':elfs,'fileCount':len(files)+1,'sdkSha256':sdksha,'raptSha256':raptsha},indent=2)+'\n')
print((work/'evidence/measurements.json').read_text())
PY
