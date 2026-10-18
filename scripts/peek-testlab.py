#!/usr/bin/env python3
"""Read bounded scrubbed evidence of one existing run; no device dispatch."""
import argparse
import importlib.util
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('r3ds_collect', HERE / 'collect-3ds-evidence.py')
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)
safe = collector.safe


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--attempt', default='1')
    args = parser.parse_args()
    for value in (args.run_id, args.attempt):
        if not re.fullmatch(r'[1-9][0-9]{0,19}', value):
            raise SystemExit('run-id/attempt phải là số nguyên dương')
    budget = safe.Budget()
    try:
        buckets = budget.read(['gsutil', 'ls'], safe.MAX_LIST).decode('utf-8').splitlines()
        if len(buckets) > 20 or any(not re.fullmatch(r'gs://[A-Za-z0-9._-]+/', bucket) for bucket in buckets):
            raise safe.EvidenceError('invalid bucket listing')
        output = []
        for bucket in buckets:
            prefix = f'{bucket}real3ds-{args.run_id}-{args.attempt}/'
            listed = budget.read(['gsutil', 'ls', '-r', prefix + '**'], safe.MAX_LIST, missing_ok=True)
            for uri in safe.objects(listed, prefix):
                name = uri.rsplit('/', 1)[-1]
                raw = budget.read(['gsutil', 'cat', uri])
                data = safe.redact(raw, name, collector.v56.scrub, collector.v56.scrub_json)
                output.append(budget.export(data).decode('utf-8'))
        if not output:
            raise safe.EvidenceError('no permitted evidence')
        # Validate the whole batch before printing; never print private URI/path lists.
        print('3DS evidence: COLLECTED')
        for text in output:
            print(text, end='')
    except (safe.EvidenceError, OSError, UnicodeError):
        print('3DS evidence: BLOCKED')
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
