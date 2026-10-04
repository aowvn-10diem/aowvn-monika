#!/usr/bin/env python3
"""V22: chỉ engine packs và XP/K3 tự sinh, không game/ROM mạng."""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--scratch',type=Path,required=True);p.add_argument('--rgss-tag',required=True);args=p.parse_args()
out=args.scratch.resolve()
spec=importlib.util.spec_from_file_location('v33',ROOT/'scripts/ci-user-path.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.prepare(out,args.rgss_tag)
cfgfile=ROOT/'app/src/debug/assets/monika-config.json';cfg=json.loads(cfgfile.read_text())
for e in cfg['engines']:
    if e['system']=='rgss':e['errorPatterns']=['monika-ci']
cfgfile.write_text(json.dumps(cfg,ensure_ascii=False))
script=out/'game/Data/Scripts.rxdata'
subprocess.run(['ruby','-rzlib','-e','File.binwrite(ARGV[0], Marshal.dump([[1,"Main",Zlib::Deflate.deflate(STDIN.read)]]))',str(script)],input=b'raise "monika-ci"\n',check=True)
(out/'game/Game.ini').write_text('[Game]\nTitle=Monika-V22\nScripts=Data\\Scripts.rxdata\nRTP1=\n')
with zipfile.ZipFile(out/'web/rgss.zip') as z:z.extractall(out/'rgss-pack')
url=cfg['modules']['kirikiri']['url'].replace('{abi}','arm64-v8a')
with urllib.request.urlopen(url,timeout=120) as src,(out/'kirikiri.zip').open('wb') as dest:
    while chunk:=src.read(65536):dest.write(chunk)
with zipfile.ZipFile(out/'kirikiri.zip') as z:z.extractall(out/'kirikiri-pack')
k3=out/'k3';k3.mkdir()
(k3/'startup.tjs').write_text('''class CiWindow extends Window {
  var base;
  function CiWindow() {
    super.Window(); setInnerSize(640, 360);
    base = new Layer(this, null); base.setImageSize(640, 360); base.setSizeToImageSize();
    base.fillRect(0, 0, 640, 360, 0xFFF28C28); base.visible = true;
    var a = []; a.add("ready"); a.save(System.exePath + "monika-ready.txt");
  }
}
var win = new CiWindow(); win.visible = true;
''')
print('Đã tạo XP raise và K3 màu cam; chỉ gói engine được tải.')
