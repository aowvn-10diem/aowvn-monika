#!/usr/bin/env python3
"""Nhóm bản ghi đã đọc theo fingerprint+app, thời gian quan sát trong tập dữ liệu."""
import datetime
import json
import sys

def group(data):
    records = data.get('reports', []) if isinstance(data, dict) else data
    groups = {}
    for r in records:
        fp = r.get('fingerprint', r.get('fp', '')) or ('không-fp:' + str(r.get('id', '')))
        app = r.get('app', r.get('a', ''))
        stamp = r.get('time', r.get('t', 0))
        key = (fp, app)
        g = groups.setdefault(key, {'fp': fp, 'app': app, 'reports': 0, 'count': 0, 'first': stamp, 'last': stamp})
        g['reports'] += 1; g['count'] += max(1, r.get('count', 1))
        g['first'] = min(g['first'], stamp); g['last'] = max(g['last'], stamp)
    return sorted(groups.values(), key=lambda g: (-g['count'], g['app'], g['fp']))

if __name__ == '__main__':
    records = json.load(open(sys.argv[1])) if len(sys.argv) > 1 else json.load(sys.stdin)
    print('== Theo fingerprint + app (lần đầu/cuối trong tập đã đọc, UTC) ==')
    for g in group(records):
        date = lambda t: datetime.datetime.fromtimestamp(t / 1000, datetime.timezone.utc).isoformat()
        print(g['count'], '|', g['app'], '|', g['fp'], '|', date(g['first']), '→', date(g['last']), '|', g['reports'], 'báo cáo')
