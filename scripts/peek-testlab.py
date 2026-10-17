#!/usr/bin/env python3
"""Đọc bằng chứng còn trong bucket kết quả Test Lab của một lượt real-3ds đã chạy (không tốn máy/quota).

In danh sách tệp và các dòng logcat liên quan (đã scrub); không in tên bucket, URL, đường dẫn riêng tư.
"""
import argparse
import importlib.util
from pathlib import Path
import re
import subprocess

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('v56_collect', HERE / 'collect-testlab-evidence.py')
v56 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v56)

INTERESTING = re.compile(r'FATAL|Fatal signal|SIGSEGV|SIGABRT|tombstone|AzaharActivity|vn\.aow|com\.aow|Monika|azahar|citra|r3ds|R3DS|'
                         r'am_crash|am_proc_died|lowmemorykiller|system_server.*(died|crash)|reboot|ANR|Instrumentation|INSTRUMENTATION', re.I)


def run(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--attempt', default='1')
    args = parser.parse_args()
    prefix_name = f'real3ds-{args.run_id}-{args.attempt}'
    buckets = [line.strip() for line in run('gsutil', 'ls').stdout.splitlines() if line.strip().startswith('gs://')]
    for bucket in buckets:
        listed = run('gsutil', 'ls', '-r', f'{bucket}{prefix_name}/**')
        uris = [u for u in listed.stdout.splitlines() if u.startswith('gs://') and not u.endswith(':') and not u.endswith('/')]
        if not uris:
            continue
        base = f'{bucket}{prefix_name}/'
        print(f'Tìm thấy {len(uris)} tệp cho {prefix_name}:')
        for uri in uris[:80]:
            print('  ', v56.scrub(uri[len(base):]))
        for uri in uris:
            name = uri.rsplit('/', 1)[-1]
            if name not in ('logcat', 'instrumentation.results', 'test_result_1.xml'):
                continue
            text = run('gsutil', 'cat', uri).stdout
            lines = [l for l in text.splitlines() if INTERESTING.search(l)]
            print(f'== {v56.scrub(uri[len(base):])} ({len(text)} byte, {len(lines)} dòng liên quan; in tối đa 120 dòng cuối)')
            for line in lines[-120:]:
                print('   ', v56.scrub(line)[:300])
        return
    print('Không thấy dữ liệu cho', prefix_name, 'trong các bucket của dự án')


if __name__ == '__main__':
    main()
