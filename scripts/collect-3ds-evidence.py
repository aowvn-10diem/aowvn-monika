#!/usr/bin/env python3
"""Thu bằng chứng test 3DS thật từ Test Lab (thư mục r3ds): log/crumb/exit/tombstone, KHÔNG PNG, KHÔNG game, KHÔNG APK.

Dùng lại scrub của collect-testlab-evidence.py. Không in bucket, không in tên game.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('v56_collect', HERE / 'collect-testlab-evidence.py')
v56 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v56)

INTERESTING = re.compile(r'FATAL|Fatal signal|SIGSEGV|SIGABRT|tombstone|AzaharActivity|vn\.aow|com\.aow|Monika|azahar|citra|R3DS|'
                         r'am_crash|am_proc_died|lowmemorykiller|reboot|ANR|Instrumentation|INSTRUMENTATION|Process crashed', re.I)
SYSTEM_FILES = ('logcat', 'instrumentation.results', 'test_result_1.xml')
MAX_FILE = 4 * 1024 * 1024
MAX_TOTAL = 20 * 1024 * 1024
NAMES = re.compile(r'(?:summary\.json|run\.json|game-meta\.txt|t\d+\.txt|R[1-3]-(?:exit|crumbs)\.txt|R[1-3]-(?:pid|crash)\.log|R[1-3]-\d+-tombstone\.pb)')


def allowed(uri):
    return uri.startswith('gs://') and '/r3ds/' in uri and '..' not in uri.split('/') and bool(NAMES.fullmatch(uri.rsplit('/', 1)[-1]))


def collect(result, stderr, output, prefix_name):
    output.mkdir(parents=True, exist_ok=True)
    transport = ''.join(p.read_text(errors='replace') if p.exists() else '' for p in (result, stderr))
    match = re.search(r'storage/browser/([A-Za-z0-9._-]+)', transport) or re.search(r'gs://([A-Za-z0-9._-]+)/', transport)
    report = {'collection': 'BLOCKED', 'files': 0, 'bytes': 0, 'reason': 'không tìm thấy bucket kết quả Test Lab'}
    total = 0
    if match:
        prefix = f'gs://{match.group(1)}/{prefix_name}/'
        listed = subprocess.run(['gsutil', 'ls', '-r', prefix + '**'], capture_output=True, text=True)
        report['reason'] = 'gsutil ls lỗi' if listed.returncode else 'không có tệp bằng chứng'
        for uri in listed.stdout.splitlines() if listed.returncode == 0 else []:
            if not allowed(uri):
                continue
            dest = output / uri[len(prefix):].replace('/', '__')
            data = subprocess.run(['gsutil', 'cat', uri], capture_output=True).stdout
            if len(data) > MAX_FILE or total + len(data) > MAX_TOTAL:
                continue
            if dest.suffix == '.json':
                try:
                    data = json.dumps(v56.scrub_json(json.loads(data)), ensure_ascii=False, indent=2).encode()
                except ValueError:
                    continue
            elif dest.suffix in ('.log', '.txt'):
                data = v56.scrub(data.decode(errors='replace')).encode()
            dest.write_bytes(data)
            total += len(data)
            report['files'] += 1
        names = [u[len(prefix):] for u in listed.stdout.splitlines() if u.startswith(prefix) and not u.endswith('/')]
        (output / 'bucket-listing.txt').write_text(v56.scrub('\n'.join(names[:200])) + '\n')
        for index, uri in enumerate(u for u in listed.stdout.splitlines() if u.rsplit('/', 1)[-1] in SYSTEM_FILES):
            text = subprocess.run(['gsutil', 'cat', uri], capture_output=True).stdout.decode(errors='replace')
            lines = [l for l in text.splitlines() if INTERESTING.search(l)]
            kept = '\n'.join(lines[-300:]) if uri.endswith('logcat') else text[:60000]
            (output / f'testlab-{index}-{uri.rsplit("/", 1)[-1]}.txt').write_text(v56.scrub(kept)[:200000] + '\n')
            report['files'] += 1
        report['bytes'] = total
        if report['files']:
            report['collection'] = 'COLLECTED'
            report.pop('reason', None)
    (output / 'collection.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'3DS collection: {report["collection"]}; files={report["files"]}; bytes={report["bytes"]}')
    if report['collection'] != 'COLLECTED':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--result', type=Path, required=True)
    parser.add_argument('--stderr', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--results-dir', required=True)
    args = parser.parse_args()
    collect(args.result, args.stderr, args.output, args.results_dir)
