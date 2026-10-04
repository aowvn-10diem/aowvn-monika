#!/usr/bin/env python3
"""Chuẩn bị snapshot V32 cùng provenance từ binary; script không phát hành."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request
import zipfile

REPO = 'aowvn-10diem/aowvn-monika-packs'
SOURCES = {
    'gambatte': ('libretro/gambatte-libretro', 'GPL-2.0', rb'v0\.5\.0(?:-netlink)? ([a-f0-9]{7,40})\x00'),
    'mgba': ('libretro/mgba', 'MPL-2.0', rb'0\.\d+(?:\.\d+)?-\d+-([a-f0-9]{7,40})\x00'),
    'fceumm': ('libretro/libretro-fceumm', 'GPL-2.0', rb'\(SVN\) ([a-f0-9]{7,40})\x00'),
}
ABIS = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86_64': (2, 62), 'x86': (1, 3)}

def binary_commit(core, data):
    commits = set(m.decode('ascii') for m in re.findall(SOURCES[core][2], data))
    if len(commits) != 1:
        raise ValueError(core + ': binary thiếu commit nguồn duy nhất; không dùng HEAD để đoán')
    return commits.pop()

def check_elf(data, abi):
    elf_class, machine = ABIS[abi]
    if len(data) < 20 or data[:4] != b'\x7fELF' or data[4] != elf_class or data[5] != 1 or int.from_bytes(data[18:20], 'little') != machine:
        raise ValueError(abi + ': ELF sai ABI')

def resolve_commit(repo, short):
    # gh dùng GITHUB_TOKEN đọc nguồn công khai, không in credential.
    data = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/commits/{short}']))
    sha = data['sha']
    if not re.fullmatch('[a-f0-9]{40}', sha) or not sha.startswith(short):
        raise ValueError('Commit không khớp version nhúng trong binary')
    return sha

def prepare(config_path, output, tag, offline=None):
    if not re.fullmatch(r'cores-gb-gba-nes-[1-9][0-9]*', tag):
        raise ValueError('Chỉ tag cores-gb-gba-nes-<số dương>')
    config = json.loads(config_path.read_text())
    output.mkdir(parents=True, exist_ok=True)
    records = []; resolved = {}
    for core in SOURCES:
        definition = config['cores'][core]
        for abi in ABIS:
            url = definition['url'].replace('{abi}', abi)
            expected_url = f'https://buildbot.libretro.com/nightly/android/latest/{abi}/{core}_libretro_android.so.zip'
            if url != expected_url:
                raise ValueError('Nguồn khác buildbot được giao; cần review')
            archive = output / f'{core}-{abi}.zip'
            if offline:
                archive.write_bytes((offline / archive.name).read_bytes())
            else:
                with urllib.request.urlopen(url, timeout=120) as src, archive.open('wb') as dest:
                    while chunk := src.read(65536): dest.write(chunk)
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            with zipfile.ZipFile(archive) as z:
                expected = core + '_libretro_android.so'
                if z.namelist() != [expected] or z.getinfo(expected).file_size > 100 * 1024**2:
                    raise ValueError(core + '/' + abi + ': ZIP sai cây hoặc vượt giới hạn')
                data = z.read(expected)
            check_elf(data, abi)
            short = binary_commit(core, data)
            repo, license_id, _ = SOURCES[core]
            key = (repo, short)
            if key not in resolved: resolved[key] = resolve_commit(repo, short)
            records.append(dict(core=core, abi=abi, file=archive.name, sourceUrl=url,
                sha256=digest, size=archive.stat().st_size, version=tag + '-' + digest[:16],
                url=f'https://github.com/{REPO}/releases/download/{tag}/{archive.name}',
                sourceRepo='https://github.com/' + repo, sourceCommit=resolved[key], license=license_id))
    (output / 'manifest.json').write_text(json.dumps(records, indent=2) + '\n')
    notes = '# GB/GBA/NES: ' + tag + '\n\nZIP từ buildbot, hash/byte đo trên chính các ZIP này. Commit nguồn lấy từ version nhúng binary rồi giải thành SHA đầy đủ, không suy từ HEAD. Mỗi tag mới; workflow từ chối sửa release đã có.\n\n'
    notes += '| Core | ABI | Byte | SHA-256 ZIP | Nguồn / commit | License |\n|---|---|---:|---|---|---|\n'
    for r in records:
        notes += f"| {r['core']} | {r['abi']} | {r['size']} | `{r['sha256']}` | {r['sourceRepo']}/commit/{r['sourceCommit']} | {r['license']} |\n"
    notes += '\nNguồn đầy đủ ở các commit trên; không thay code core. gambatte/FCEUmm GPL-2.0; mGBA MPL-2.0. manifest.json chứa URL/version/hash/size từng ABI. Snapshot cũ được giữ để quay cấu hình về; cấu hình app được review ở PR riêng.\n'
    (output / 'notes.md').write_text(notes)
    print('Đã kiểm ' + str(len(records)) + ' ZIP/ABI/provenance; chưa phát hành.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=Path('config/monika-config.json'))
    parser.add_argument('--out', type=Path, required=True); parser.add_argument('--tag', required=True)
    parser.add_argument('--offline', type=Path, help='chỉ test các ZIP đã tải, vẫn giải commit nguồn công khai')
    args = parser.parse_args(); prepare(args.config, args.out, args.tag, args.offline)
