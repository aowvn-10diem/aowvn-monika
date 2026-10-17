#!/usr/bin/env python3
"""Chỉ chạy trong CI (real-3ds.yml): đọc secret TEST_GAME_3DS, tải game 3DS về thư mục tạm.

Định dạng secret (cùng quy ước V124): `đuôi | link | [ghi chú] | [mk=mật khẩu]`, hoặc chỉ một link https.
Link dạng pixeldrain.com/u/<id> tự đổi sang API tải trực tiếp. Gói zip/7z/rar được giải bằng 7z.
Không in link, mật khẩu hay tên game: chỉ in đuôi và dung lượng.
"""
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.request

GAME_EXT = {'3ds', 'cci', 'cia', 'cxi', '3dsx'}
ARCHIVE_EXT = {'zip', '7z', 'rar'}


def parse(text):
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        cols = [c.strip() for c in line.split('|')]
        if cols[0].startswith('https://'):
            cols = [''] + cols
        if len(cols) < 2 or not cols[1].startswith('https://'):
            continue
        password = next((c[3:] for c in cols[2:] if c.startswith('mk=')), '')
        return cols[0].lower().lstrip('.'), cols[1], password
    raise SystemExit('secret TEST_GAME_3DS trống hoặc sai định dạng (cần: đuôi | link | ... hoặc một link https)')


def direct(link):
    m = re.match(r'https://pixeldrain\.com/u/([A-Za-z0-9]+)', link)
    return f'https://pixeldrain.com/api/file/{m.group(1)}?download' if m else link


def main(out_dir):
    secret = os.environ.get('TEST_GAME_3DS', '')
    if not secret.strip():
        raise SystemExit('Không có secret TEST_GAME_3DS (hoặc rỗng): kiểm tên secret với sếp')
    ext, link, password = parse(secret)
    for value in (link, password):
        if value:
            print(f'::add-mask::{value}')
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    raw = out / 'download.bin'
    request = urllib.request.Request(direct(link), headers={'User-Agent': 'monika-ci'})
    with urllib.request.urlopen(request, timeout=60) as response, raw.open('wb') as target:
        while chunk := response.read(1 << 20):
            target.write(chunk)
    print(f'Đã tải {raw.stat().st_size} byte (đuôi khai báo: {ext or "không"})')
    if ext in GAME_EXT:
        final = raw.with_name('monika-3ds.' + ext)
        raw.rename(final)
    else:
        packed = raw.with_name('pack.' + (ext if ext in ARCHIVE_EXT else 'zip'))
        raw.rename(packed)
        args = ['7z', 'x', '-y', f'-o{out / "x"}', str(packed)] + ([f'-p{password}'] if password else [])
        if subprocess.run(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            raise SystemExit('giải nén lỗi (sai mật khẩu hoặc gói hỏng)')
        found = sorted((p for p in (out / 'x').rglob('*') if p.is_file() and p.suffix.lower().lstrip('.') in GAME_EXT),
                       key=lambda p: -p.stat().st_size)
        if not found:
            raise SystemExit('trong gói không có .3ds/.cci/.cia/.cxi/.3dsx')
        final = out / ('monika-3ds' + found[0].suffix.lower())
        found[0].rename(final)
        packed.unlink()
    print(f'Game sẵn sàng: đuôi {final.suffix[1:]}, {final.stat().st_size} byte')


if __name__ == '__main__':
    main(sys.argv[1])
