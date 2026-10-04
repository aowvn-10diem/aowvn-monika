#!/usr/bin/env python3
"""V25: chỉ tải ba nguồn PM đã giao TRONG CI; không đưa dữ liệu game ra artifact."""
import argparse
import configparser
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CASES = [('xp', 'The Reconstruction', 'the-reconstruction.zip', 1),
         ('vx', 'Star Stealing Prince', 'star-stealing-prince.zip', 2),
         ('ace', 'In Search of Immortality', 'in-search-of-immortality.zip', 3)]

def prepare(scratch, report, tag):
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise RuntimeError('Game chỉ được tải trong GitHub Actions')
    # URL chuẩn đã có trong hàng V25, không nhận URL tùy ý từ input.
    row = next(line for line in (ROOT / 'docs/opus/KE-HOACH.md').read_text().splitlines()
               if line.startswith('| V25 |'))
    source = re.search(r'https://rpgmakerweb\.tkool\.jp/[^` ]+\.zip', row)
    if not source or not source.group().endswith('/' + CASES[0][2]):
        raise RuntimeError('Nguồn G2 không khớp, cần PM cập nhật nguồn chuẩn')
    base = source.group().rsplit('/', 1)[0]
    spec = importlib.util.spec_from_file_location('v33', ROOT / 'scripts/ci-user-path.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    module.prepare(scratch, tag)  # Chỉ engine/TLS/config test; fixture XP tự sinh không dùng R6.
    report.mkdir(parents=True, exist_ok=True)
    records = []
    for case, title, filename, version in CASES:
        record = {'id': case, 'title': title, 'rgssVersion': version}
        archive = scratch / filename
        try:
            request = urllib.request.Request(base + '/' + filename, headers={'User-Agent': 'Monika-CI-V25'})
            with urllib.request.urlopen(request, timeout=120) as response, archive.open('wb') as out:
                record['http'] = response.status
                while data := response.read(1024 * 1024):
                    out.write(data)
            record['zipBytes'] = archive.stat().st_size
            record['zipSha256'] = hashlib.sha256(archive.read_bytes()).hexdigest()
            target = scratch / 'games' / case; target.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(archive) as z:
                record["archiveTopEntries"] = z.namelist()[:40]
                if sum(e.file_size for e in z.infolist()) > 3 * 1024**3:
                    raise ValueError('ZIP giải nén vượt 3 GiB')
                written = set()
                for e in z.infolist():
                    dest = (target / e.filename.replace('\\', '/')).resolve()
                    if not dest.is_relative_to(target.resolve()) or dest in written:
                        raise ValueError('ZIP có đường dẫn không hợp lệ hoặc trùng')
                    if e.is_dir():
                        dest.mkdir(parents=True, exist_ok=True); continue
                    written.add(dest)
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(e) as src, dest.open('wb') as out:
                        while data := src.read(1024 * 1024): out.write(data)
            inis = [p for p in target.rglob('*') if p.name.lower() == 'game.ini']
            if len(inis) != 1:
                raise ValueError('Cần đúng một Game.ini, nhận ' + str(len(inis)))
            directory = inis[0].parent
            ini = configparser.ConfigParser(interpolation=None, strict=False)
            ini.read_string(inis[0].read_bytes().decode('utf-8-sig', errors='replace'))
            record['rtpDeclared'] = {k: v for k, v in ini['Game'].items() if k.lower().startswith('rtp')}
            # Không thêm RTP hay sửa script game. Native tự đọc archive mã hóa nếu có.
            config = directory / 'mkxp.json'
            existing = json.loads(config.read_text()) if config.exists() else {}
            existing['rgssVersion'] = version
            config.write_text(json.dumps(existing))
            record['localGameDir'] = str(directory.relative_to(scratch))
            record['prepared'] = True
        except Exception as error:
            # Không đưa URL/response body vào log hay report.
            record.update(prepared=False, error=type(error).__name__ + ': ' + str(error)[:160])
        records.append(record)
    (scratch / 'cases.json').write_text(json.dumps(records, indent=2))
    public = [{k: v for k, v in r.items() if k != 'localGameDir'} for r in records]
    (report / 'sources.json').write_text(json.dumps(public, indent=2) + '\n')
    if not any(r['prepared'] for r in records):
        raise RuntimeError('Không chuẩn bị được game nào; xem sources.json')

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--scratch', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True); p.add_argument('--rgss-tag', required=True)
    args = p.parse_args(); prepare(args.scratch.resolve(), args.report.resolve(), args.rgss_tag)
