#!/usr/bin/env python3
"""Export bounded scrubbed text/JSON evidence, never a ROM/APK/protobuf/URI listing."""
import argparse
import importlib.util
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


v56 = load('v56_collect', 'collect-testlab-evidence.py')
safe = load('r3ds_safe', 'r3ds-safe-evidence.py')


def collect(result, stderr, output, prefix_name):
    output.mkdir(parents=True, exist_ok=True)
    report = {'collection': 'BLOCKED', 'files': 0, 'bytes': 0}
    written = []
    budget = safe.Budget()
    try:
        if not re.fullmatch(r'real3ds-[1-9][0-9]{0,19}-[1-9][0-9]{0,19}', prefix_name):
            raise safe.EvidenceError('invalid run scope')
        transport = ''
        for path in (result, stderr):
            if path.exists():
                with path.open('rb') as stream:
                    data = stream.read(safe.MAX_LIST + 1)
                if len(data) > safe.MAX_LIST:
                    raise safe.EvidenceError('transport metadata too large')
                transport += data.decode('utf-8', errors='replace')
        match = re.search(r'storage/browser/([A-Za-z0-9._-]+)', transport) or re.search(r'gs://([A-Za-z0-9._-]+)/', transport)
        if not match:
            raise safe.EvidenceError('results bucket unavailable')
        prefix = f'gs://{match.group(1)}/{prefix_name}/'
        listed = budget.read(['gsutil', 'ls', '-r', prefix + '**'], safe.MAX_LIST)
        uris = safe.objects(listed, prefix)
        if not uris:
            raise safe.EvidenceError('no permitted evidence')
        for index, uri in enumerate(uris):
            name = uri.rsplit('/', 1)[-1]
            raw = budget.read(['gsutil', 'cat', uri])
            data = budget.export(safe.redact(raw, name, v56.scrub, v56.scrub_json))
            dest = output / f'evidence-{index}-{name}'
            dest.write_bytes(data)
            written.append(dest)
        report.update(collection='COLLECTED', files=len(written), bytes=budget.exported)
    except (safe.EvidenceError, OSError) as error:
        for path in written:
            path.unlink(missing_ok=True)
        report['reason'] = str(error) if isinstance(error, safe.EvidenceError) else 'evidence I/O failed'
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
