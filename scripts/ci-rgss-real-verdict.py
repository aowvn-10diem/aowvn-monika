#!/usr/bin/env python3
"""Cổng R6 mức 1: tiến trình + dialog + ảnh; không suy âm thanh/lưu-tải/FPS."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageStat


def dialog_error(path):
    text = ' '.join(node.get('text', '') for node in ET.parse(path).iter('node'))
    return bool(re.search(r'error\s+occurr?ed|error\s+occured|SyntaxError|RuntimeError|LoadError|NameError|NoMethodError', text, re.I))


def classify(out, case):
    try:
        pid = (out / f'{case}-pid.txt').read_text().strip()
        alive = bool(re.fullmatch(r'[0-9]+(?:\s+[0-9]+)*', pid)) and all(int(p) > 0 for p in pid.split())
        error = any(dialog_error(out / f'{case}-{phase}.xml') for phase in ('before-key', 'after-60s'))
        with Image.open(out / f'{case}-after-60s.png') as image:
            deviation = ImageStat.Stat(image.convert('L')).stddev[0]
        seconds = int((out / f'{case}-observed-seconds.txt').read_text().strip())
        passed = alive and not error and deviation >= 10 and seconds >= 60
        return dict(game=case, verdict='ĐẠT mức 1' if passed else 'HỎNG', verdictCode='PASS_LEVEL_1' if passed else 'FAIL', process='com.aow.monika:game',
                    pid=pid, alive=alive, errorDialog=error, brightnessStddev=round(deviation, 3),
                    threshold=10, observedSeconds=seconds, limits='Không chứng minh âm thanh, lưu-tải hoặc FPS game')
    except Exception as error:
        return dict(game=case, verdict='LỖI BẰNG CHỨNG', verdictCode='EVIDENCE_ERROR', error=type(error).__name__)


def verdicts(out, cases, expect_fail):
    ids = [case['id'] for case in cases]
    if not expect_fail <= set(ids):
        raise ValueError('expect_fail chứa case không khai báo')
    rows = []
    for case in cases:
        row = classify(out, case['id']) if case['prepared'] else dict(game=case['id'], verdict='LỖI BẰNG CHỨNG', verdictCode='EVIDENCE_ERROR', error='not_prepared')
        row['expectedCode'] = 'FAIL' if case['id'] in expect_fail else 'PASS_LEVEL_1'
        row['expected'] = 'HỎNG' if case['id'] in expect_fail else 'ĐẠT mức 1'
        row['matchesExpectation'] = row['verdictCode'] == row['expectedCode']
        if row['verdictCode'] == 'PASS_LEVEL_1' and row['expectedCode'] == 'FAIL':
            row['note'] = 'Game đã đạt; PM bỏ case này khỏi expect_fail sau khi kiểm bằng chứng'
        rows.append(row)
    return dict(level=1, expectFail=sorted(expect_fail), matchesExpectation=all(row['matchesExpectation'] for row in rows), games=rows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--dialog', type=Path)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--cases', type=Path)
    parser.add_argument('--expect-fail', default='')
    args = parser.parse_args()
    if args.dialog:
        raise SystemExit(0 if dialog_error(args.dialog) else 1)
    result = verdicts(args.out, json.loads(args.cases.read_text()), {v.strip() for v in args.expect_fail.split(',') if v.strip()})
    (args.out / 'verdict.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    summary = '\n'.join(f"{row['game']}: {row['verdict']} (kỳ vọng {row['expected']})" for row in result['games'])
    (args.out / 'summary.txt').write_text(summary + '\n')
    print(summary)
    raise SystemExit(0 if result['matchesExpectation'] else 1)
