#!/usr/bin/env python3
"""V22: chỉ engine packs và XP tự sinh và RGSS màu cam (SOL-010 B), không game/ROM mạng."""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--scratch',type=Path,required=True);p.add_argument('--rgss-tag',required=True);p.add_argument('--pack-file',type=Path,required=True);args=p.parse_args()
out=args.scratch.resolve()
spec=importlib.util.spec_from_file_location('v33',ROOT/'scripts/ci-user-path.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.prepare(out,args.rgss_tag,pack_file=args.pack_file.resolve())
cfgfile=ROOT/'app/src/debug/assets/monika-config.json';cfg=json.loads(cfgfile.read_text())
for e in cfg['engines']:
    if e['system']=='rgss':e['errorPatterns']=['monika-ci']
cfgfile.write_text(json.dumps(cfg,ensure_ascii=False))
script=out/'game/Data/Scripts.rxdata'
subprocess.run(['ruby','-rzlib','-e','File.binwrite(ARGV[0], Marshal.dump([[1,"Main",Zlib::Deflate.deflate(STDIN.read)]]))',str(script)],input=b'raise "monika-ci"\n',check=True)
(out/'game/Game.ini').write_text('[Game]\nTitle=Monika-V22\nScripts=Data\\Scripts.rxdata\nRTP1=\n')
with zipfile.ZipFile(out/'web/rgss.zip') as z:z.extractall(out/'rgss-pack')
control=out/'control';(control/'Data').mkdir(parents=True)
(control/'Game.ini').write_text('[Game]\nTitle=Monika-V22-Control\nScripts=Data\\Scripts.rvdata\nRTP1=\n')
code = """bitmap=Bitmap.new (640,360)
class MonikaEvalNative
  OFFSET = 7
  eval("def forward(arg, *rest); collect (arg, *rest); end")
  def collect(arg, *rest); [arg + OFFSET, rest]; end
  def local_value
    value = 4
    eval("value + OFFSET")
  end
end
control_eval = MonikaEvalNative.new
raise "monika-eval-context" unless control_eval.forward(2,3,4) == [9,[3,4]] && control_eval.local_value == 11
bitmap.fill_rect(0,0,640,360,Color.new(242,140,40))
sprite=Sprite.new
sprite.bitmap=bitmap
File.write("monika-ready.txt","RGSS-control-ruby18:#{MonikaRuby18.applied_count}")
loop do
  Graphics.update
  Input.update
end
"""
subprocess.run(['ruby','-rzlib','-e','File.binwrite(ARGV[0], Marshal.dump([[1,"Main",Zlib::Deflate.deflate(STDIN.read)]]))',str(control/'Data/Scripts.rvdata')],input=code.encode(),check=True)
print('Đã tạo XP raise và RGSS control màu cam; K3 [CHƯA KIỂM] theo SOL-010 B.')
