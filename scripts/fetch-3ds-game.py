#!/usr/bin/env python3
"""Chỉ chạy trong CI (real-3ds.yml): đọc secret TEST_GAME_3DS, tải game 3DS về thư mục tạm.

Định dạng secret (cùng quy ước V124): `đuôi | link | [ghi chú] | [mk=mật khẩu]`, hoặc chỉ một link https.
Link dạng pixeldrain.com/u/<id> tự đổi sang API tải trực tiếp. Gói zip/7z/rar được giải bằng 7z.
Không in link, mật khẩu hay tên game: chỉ in đuôi và dung lượng.
"""
import json
import os
import shutil
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

GAME_EXT = {'3ds', 'cci', 'cia', 'cxi', '3dsx'}
ARCHIVE_EXT = {'zip', '7z', 'rar'}
MIN_BYTES = 1_000_000  # ROM 3DS nhỏ nhất vẫn hàng MB; nhỏ hơn thường là trang HTML/JSON lỗi


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


PIXELDRAIN = re.compile(r'https://(?:www\.)?pixeldrain\.(?:com|net|dev)/(u|l|d)/([A-Za-z0-9]+)')


def candidates(link):
    """Các URL thử lần lượt. /d/<id> là trang file-manager mới: thử API filesystem rồi API file [CHƯA KIỂM từng dạng]."""
    m = PIXELDRAIN.match(link)
    if not m:
        return [link]
    kind, ident = m.groups()
    if kind == 'd':
        return [f'https://pixeldrain.com/api/filesystem/{ident}?attach', f'https://pixeldrain.com/api/file/{ident}?download']
    if kind == 'u':
        return [f'https://pixeldrain.com/api/file/{ident}?download']
    with urllib.request.urlopen(urllib.request.Request(f'https://pixeldrain.com/api/list/{ident}',
                                                         headers={'User-Agent': 'monika-ci'}), timeout=60) as r:
        files = json.load(r).get('files', [])
    if not files:
        raise SystemExit('danh sách pixeldrain trống')
    return [f'https://pixeldrain.com/api/file/{max(files, key=lambda f: f.get("size", 0))["id"]}?download']


def valid_game(path):
    """Kiểm cấu trúc theo đuôi (không tin đuôi một mình): .3ds/.cci = NCSD@0x100, .cxi = NCCH@0x100, .3dsx = '3DSX'@0, .cia = header 0x2020."""
    ext = path.suffix.lower().lstrip('.')
    with path.open('rb') as f:
        head = f.read(4)
        f.seek(0x100)
        at_100 = f.read(4)
    if ext in ('3ds', 'cci'):
        return at_100 == b'NCSD'
    if ext == 'cxi':
        return at_100 == b'NCCH'
    if ext == '3dsx':
        return head == b'3DSX'
    if ext == 'cia':
        return int.from_bytes(head, 'little') == 0x2020
    return False


def sniff(path):
    """Đoán loại theo nội dung, không tin đuôi khai báo: game / zip / rar / 7z / khác."""
    with path.open('rb') as f:
        head = f.read(8)
        f.seek(0x100)
        ncsd = f.read(4) == b'NCSD'
    if ncsd:
        return 'game'
    if head.startswith(b'Rar!'):
        return 'rar'
    if head.startswith(b'PK'):
        return 'zip'
    if head.startswith(b'7z\xbc\xaf'):
        return '7z'
    return 'other'


def shape(url):
    """Chỉ giữ hostname; userinfo, port, path, query và fragment không được đưa vào log."""
    try:
        return urllib.parse.urlsplit(url).hostname or 'không rõ'
    except ValueError:
        return 'không rõ'


def download(url, raw):
    request = urllib.request.Request(url, headers={'User-Agent': 'monika-ci'})
    try:
        with urllib.request.urlopen(request, timeout=60) as response, raw.open('wb') as target:
            while chunk := response.read(1 << 20):
                target.write(chunk)
            return response.geturl(), response.headers.get('Content-Type', '?')
    except urllib.error.HTTPError as error:
        body = re.sub(r'https?://\S+', '<url>', error.read(300).decode('utf-8', 'replace'))
        raise SystemExit(f'Máy chủ trả HTTP {error.code}: {body}')


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
    final_url, content_type, size = '', '?', 0
    print(f'Dạng link khai báo: {shape(link)}')
    for tried in candidates(link):
        try:
            final_url, content_type = download(tried, raw)
        except SystemExit as error:
            print(f'Thử {shape(tried)}: {error}')
            continue
        size = raw.stat().st_size
        print(f'Thử {shape(tried)} -> {shape(final_url)}: {size} byte, {content_type}')
        if size >= MIN_BYTES:
            break
    print(f'Đã tải {size} byte (đuôi khai báo: {ext or "không"}, content-type: {content_type}, máy chủ cuối: {shape(final_url)})')
    if size < MIN_BYTES:
        # Link không trỏ thẳng tới file game (trang web/lỗi/JSON): in đầu nội dung đã làm sạch để sếp biết link sai kiểu gì.
        head = raw.read_bytes()[:400].decode('utf-8', 'replace')
        head = re.sub(r'https?://\S+', '<url>', re.sub(r'[^\x20-\x7e\n]', ' ', head))
        print('Nội dung đầu (làm sạch):', head.replace('\n', ' ')[:300])
        raise SystemExit(f'Link không trả về file game (chỉ {size} byte < {MIN_BYTES}): cần link tải TRỰC TIẾP tới .3ds/.cci/.cia/.zip')
    kind = sniff(raw)
    print(f'Loại theo nội dung: {kind}')
    if kind == 'game' or (kind == 'other' and ext in GAME_EXT):  # .cia/.cxi/.3dsx không có NCSD: tin đuôi khai báo
        final = raw.with_name('monika-3ds.' + (ext if ext in GAME_EXT else '3ds'))
        raw.rename(final)
    elif kind in ARCHIVE_EXT:
        packed = raw.with_name('pack.' + kind)
        raw.rename(packed)
        seven = shutil.which('7zz') or shutil.which('7z')
        if not seven:
            raise SystemExit('runner không có 7zz/7z')
        args = [seven, 'x', '-y', f'-o{out / "x"}', str(packed)] + ([f'-p{password}'] if password else [])
        if subprocess.run(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            raise SystemExit('giải nén lỗi (sai mật khẩu, gói hỏng hoặc 7z không hỗ trợ định dạng này)')
        names = [p.suffix.lower().lstrip('.') for p in (out / 'x').rglob('*') if p.is_file()]
        print('Loại tệp trong gói:', sorted(set(names)))
        found = sorted((p for p in (out / 'x').rglob('*') if p.is_file() and p.suffix.lower().lstrip('.') in GAME_EXT),
                       key=lambda p: -p.stat().st_size)
        if not found:
            raise SystemExit('trong gói không có .3ds/.cci/.cia/.cxi/.3dsx')
        final = out / ('monika-3ds' + found[0].suffix.lower())
        found[0].rename(final)
        packed.unlink()
    else:
        raise SystemExit('tệp tải về không nhận ra được (không phải game/zip/rar/7z)')
    if final.stat().st_size < MIN_BYTES:
        raise SystemExit(f'Tệp game cuối chỉ {final.stat().st_size} byte (< {MIN_BYTES}): không phải ROM thật, dừng.')
    if not valid_game(final):
        raise SystemExit(f'File .{final.suffix[1:]} không có header 3DS hợp lệ (NCSD/NCCH/3DSX/CIA): không phải ROM hoặc chưa xác thực được, dừng.')
    print(f'Game sẵn sàng: đuôi {final.suffix[1:]}, {final.stat().st_size} byte')


if __name__ == '__main__':
    main(sys.argv[1])
