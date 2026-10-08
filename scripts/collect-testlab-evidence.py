#!/usr/bin/env python3
"""Download only the fresh V56 evidence directory, never Test Lab APK/game inputs."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

MAX_FILE = 8 * 1024 * 1024
MAX_TOTAL = 30 * 1024 * 1024
NAMES = re.compile(r'(?:metadata\.json|summary\.json|K[1-8](?:-final)?\.png|K[1-8]-(?:pid|crash)\.log|K[1-8]-\d+-tombstone\.pb|s[03]-monika-(?:ready|touch|audio|load)\.txt)')


def allowed(uri):
    return (uri.startswith('gs://') and '/v56/' in uri and
            '..' not in uri.split('/') and bool(NAMES.fullmatch(uri.rsplit('/', 1)[-1])))


def scrub(text):
    text = re.sub(r'https?://\S+', '<url>', text)
    text = re.sub(r'(?:/data/(?:data|user)/|/storage/|/sdcard/)[^\n]*', '<private-path>', text)
    text = re.sub(r'(?i)\b(token|password|secret|authorization|api[_-]?key)\s*[:=]\s*[^\s,;]+', r'\1=<hidden>', text)
    return re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '<email>', text)


def scrub_json(value):
    if isinstance(value, str):
        return scrub(value)
    if isinstance(value, list):
        return [scrub_json(item) for item in value]
    if isinstance(value, dict):
        return {key: scrub_json(item) for key, item in value.items()}
    return value


def collect(result, stderr, output):
    output.mkdir(parents=True, exist_ok=True)
    # Bucket is private transport metadata; never print/upload result.json, stderr or URI lists.
    transport = ''.join(p.read_text(errors='replace') if p.exists() else '' for p in (result, stderr))
    match = re.search(r'storage/browser/([A-Za-z0-9._-]+)', transport)
    if not match:
        match = re.search(r'gs://([A-Za-z0-9._-]+)/', transport)
    report = {'collection': 'BLOCKED', 'files': 0, 'bytes': 0,
              'reason': 'Test Lab results bucket unavailable; inspect masked workflow logs'}
    total = 0
    if match:
        prefix = f'gs://{match.group(1)}/v56-{os.environ["GITHUB_RUN_ID"]}-{os.environ["GITHUB_RUN_ATTEMPT"]}/'
        listed = subprocess.run(['gsutil', 'ls', '-r', prefix + '**'], capture_output=True, text=True)
        if listed.returncode == 0:
            for uri in listed.stdout.splitlines():
                if not allowed(uri):
                    continue
                relative = uri[len(prefix):]
                if not uri.startswith(prefix) or any(x in ('', '.', '..') for x in relative.split('/')):
                    continue
                dest = output / relative
                dest.parent.mkdir(parents=True, exist_ok=True)
                with dest.open('wb') as target:
                    process = subprocess.Popen(['gsutil', 'cat', uri], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                    size = 0
                    while data := process.stdout.read(65536):
                        size += len(data)
                        if size > MAX_FILE or total + size > MAX_TOTAL:
                            process.kill()
                            break
                        target.write(data)
                    process.stdout.close()
                    code = process.wait()
                if code or size > MAX_FILE or total + size > MAX_TOTAL:
                    dest.unlink(missing_ok=True)
                    report['reason'] = 'one evidence file failed download or exceeded byte budget'
                    continue
                if dest.suffix == '.json':
                    try:
                        dest.write_text(json.dumps(scrub_json(json.loads(dest.read_text())), ensure_ascii=False, indent=2))
                    except (ValueError, UnicodeError):
                        dest.unlink(missing_ok=True)
                        continue
                elif dest.suffix in ('.log', '.txt'):
                    dest.write_text(scrub(dest.read_text(errors='replace')))
                exported_size = dest.stat().st_size
                if exported_size > MAX_FILE or total + exported_size > MAX_TOTAL:
                    dest.unlink(missing_ok=True)
                    report['reason'] = 'redacted evidence exceeded byte budget'
                    continue
                total += exported_size
                report['files'] += 1
            report['bytes'] = total
            summaries = list(output.rglob('summary.json'))
            if len(summaries) == 2:
                devices = []
                try:
                    for summary in summaries:
                        value = json.loads(summary.read_text())
                        assert value['synthetic'] is True
                        assert [r['game'] for r in value['rows']] == [f'K{n}' for n in range(1, 9)]
                        # Complete summaries alone do not prove images/logs actually arrived.
                        required = ['metadata.json']
                        for row in value['rows']:
                            game = row['game']
                            assert row['status'] in ('PASS', 'FAIL', 'BLOCKED')
                            required += [game + '-final.png', game + '-pid.log', game + '-crash.log']
                            if row['status'] == 'PASS' and game in ('K3', 'K5', 'K7', 'K8'):
                                required.append(game + '.png')
                            tombstone = row.get('tombstone', '')
                            if tombstone.endswith('.pb'):
                                assert NAMES.fullmatch(tombstone)
                                required.append(tombstone)
                        assert all((summary.parent / name).is_file() for name in required)
                        devices.append({'model': value['model'], 'sdk': value['sdk'], 'abi': value['abi'],
                                        'statuses': [r['status'] for r in value['rows']]})
                    report.update(collection='COLLECTED', devices=devices)
                    report.pop('reason', None)
                except (KeyError, ValueError, AssertionError):
                    report['reason'] = 'two complete K1–K8 synthetic summaries required'
    (output / 'collection.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'V56 collection: {report["collection"]}; files={report["files"]}; bytes={report["bytes"]}')
    if report['collection'] != 'COLLECTED':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--result', type=Path, required=True)
    parser.add_argument('--stderr', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    collect(args.result, args.stderr, args.output)
