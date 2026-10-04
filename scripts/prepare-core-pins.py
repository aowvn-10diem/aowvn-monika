#!/usr/bin/env python3
"""Chuẩn bị ảnh chụp GB/GBA/NES; không phát hành hoặc sửa config."""
import argparse
import hashlib
import json
import pathlib
import urllib.request
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--config', default='config/monika-config.json')
parser.add_argument('--out', required=True)
a = parser.parse_args()
out = pathlib.Path(a.out)
out.mkdir(parents=True, exist_ok=True)
config = json.loads(pathlib.Path(a.config).read_text())
records = []
for core in ['gambatte', 'mgba', 'fceumm']:
    definition = config['cores'][core]
    for abi in definition.get('abis') or ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86']:
        source = definition['url'].replace('{abi}', abi)
        archive = out / f'{core}-{abi}.zip'
        with urllib.request.urlopen(source, timeout=120) as response, archive.open('wb') as output:
            while chunk := response.read(65536):
                output.write(chunk)
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        with zipfile.ZipFile(archive) as z:
            expected = f'{core}_libretro_android.so'
            assert expected in z.namelist() and z.getinfo(expected).file_size > 0, f'{core}/{abi}: thiếu SO chính'
        records.append(dict(core=core, abi=abi, sourceUrl=source, file=archive.name,
                            version='sha256-' + digest[:16], sha256=digest, size=archive.stat().st_size,
                            fixedUrl='[CHƯA KIỂM — cần nơi lưu ảnh chụp bất biến]'))
        print(f'{core}/{abi}: {digest} ({archive.stat().st_size} byte)')
(out / 'candidates.json').write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n')
