#!/usr/bin/env python3
"""V21: RGSS tự sinh, chỉ gói engine; không game mạng/Worker deploy."""
import argparse,importlib.util,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--scratch',type=Path,required=True);p.add_argument('--pack-file',type=Path,required=True);a=p.parse_args()
spec=importlib.util.spec_from_file_location('v33',Path(__file__).with_name('ci-user-path.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
out=a.scratch.resolve();m.prepare(out,'engines-rgss-7',pack_file=a.pack_file.resolve())
(out/'game/Game.ini').write_text('[Game]\nTitle=Monika-User-Report\nScripts=Data\\Scripts.rxdata\nRTP1=\n')
code='''bitmap=Bitmap.new(640,360)
bitmap.fill_rect(0,0,640,360,Color.new(242,140,40))
sprite=Sprite.new; sprite.bitmap=bitmap
loop do
  Graphics.update
  Input.update
end
'''
subprocess.run(['ruby','-rzlib','-e','File.binwrite(ARGV[0],Marshal.dump([[1,"Main",Zlib::Deflate.deflate(STDIN.read)]]))',str(out/'game/Data/Scripts.rxdata')],input=code.encode(),check=True)
