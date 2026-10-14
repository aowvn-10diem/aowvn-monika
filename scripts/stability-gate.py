#!/usr/bin/env python3
"""Read-only V103 gate. Offline fixtures never contact Worker. No release mutation."""
import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def signature(report):
    # V100 protocol: core + kind + first three Java/native frames, ignoring paths/PC/lines.
    session = report.get('session') or {}
    if not isinstance(session, dict) or not isinstance(report.get('log', []), list):
        raise ValueError('invalid report')
    def token(v):
        return v if isinstance(v, str) and re.fullmatch(r'[a-zA-Z0-9_.-]{1,60}', v) else 'unknown'
    core, kind = token(session.get('core', '')), token(report.get('kind', ''))
    frames = []
    detail = str(report.get('detail', '')) + '\n' + '\n'.join(report.get('log', []))
    for line in detail.splitlines():
        java = re.search(r'\bat\s+([\w.$]+)\([^)]*\)', line)
        native = re.search(r'#\d+\s+pc\s+[0-9a-fA-F]+\s+(?:\S*/)?([^/\s]+\.so)(?:\s+\(([^)]*)\))?', line)
        if java: frames.append(java.group(1))
        elif native: frames.append(native.group(1) + ':' + re.sub(r'\+(?:0[xX][0-9a-fA-F]+|\d+)\b', '', native.group(2) or ''))
        if len(frames) == 3: break
    digest = hashlib.sha256(json.dumps([core, kind, frames], separators=(',', ':')).encode()).hexdigest()
    return digest, core, kind


def report_version(app):
    # Diagnostics.appLine(): VERSION_NAME (VERSION_CODE) debug|release; old fixtures use semver.
    match = re.fullmatch(r'(\d+\.\d+\.\d+)(?: \(\d+\) (?:debug|release))?', app) if isinstance(app, str) else None
    if not match: raise ValueError('invalid report app')
    return match[1]


def gate(data, version, hours, now):
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or not 1 <= hours <= 168 or type(now) is not int or now < 0:
        raise ValueError('invalid gate input')
    if not isinstance(data, dict) or data.get('complete') is not True or not isinstance(data.get('records'), list):
        raise ValueError('history incomplete')
    cutoff = now - hours * 3600000
    groups, ids = {}, set()
    for row in data['records']:
        if not isinstance(row, dict) or not isinstance(row.get('id'), str):
            raise ValueError('invalid record')
        rid, r = row['id'], row['report']
        match = re.fullmatch(r'r:(\d{13}):[a-fA-F0-9-]{8,36}', rid)
        if not match or rid in ids or not isinstance(r, dict): raise ValueError('invalid record')
        ids.add(rid)
        seen = int(match[1])  # Worker receipt clock, not untrusted device report.time.
        if seen > now: raise ValueError('future receipt')
        if report_version(r.get('app')) != version: continue
        key, core, kind = signature(r)
        count = r.get('count', 1)
        if type(count) is not int or not 1 <= count <= 1000000: raise ValueError('invalid count')
        g = groups.setdefault(key, {'signature': key, 'core': core, 'kind': kind, 'first_seen_ms': seen, 'reports': 0, 'count': 0})
        g['first_seen_ms'] = min(g['first_seen_ms'], seen); g['reports'] += 1; g['count'] += count
    if not groups: raise ValueError('no evidence for version')
    new = sorted((g for g in groups.values() if g['first_seen_ms'] >= cutoff), key=lambda g: g['signature'])
    return {'version': version, 'hours': hours, 'checked_at_ms': now, 'allowed': not new,
            'scope': 'complete retained Worker history (60-day TTL), not a device/gameplay or release approval',
            'new_signatures': new}


class Worker:
    def __init__(self, origin, token):
        parsed = urllib.parse.urlsplit(origin)
        if parsed.scheme != 'https' or not (parsed.hostname or '').endswith('.workers.dev') or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/') or not token:
            raise ValueError('invalid Worker configuration')
        self.origin, self.token, self.calls, self.bytes = origin.rstrip('/'), token, 0, 0

    def get(self, path):
        self.calls += 1
        if self.calls > 250: raise ValueError('API budget')
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs): raise ValueError('redirect blocked')
        req = urllib.request.Request(self.origin + path, headers={'Authorization': 'Bearer ' + self.token})
        try:
            with urllib.request.build_opener(NoRedirect).open(req, timeout=15) as response:
                raw = response.read(1024*1024+1)
        except (urllib.error.URLError, OSError): raise ValueError('Worker unavailable') from None
        self.bytes += len(raw)
        if len(raw) > 1024*1024 or self.bytes > 8*1024*1024: raise ValueError('response budget')
        return json.loads(raw)


def collect(worker, version):
    if not isinstance(version, str) or not re.fullmatch(r'\d+\.\d+\.\d+', version): raise ValueError('invalid version')
    index, seen, cursors, cursor = [], set(), set(), None
    for _ in range(50):
        page = worker.get('/report-index' + ('?cursor=' + urllib.parse.quote(cursor, safe='') if cursor else ''))
        if not isinstance(page, dict) or type(page.get('complete')) is not bool or not isinstance(page.get('reports'), list) or len(page['reports']) > 100:
            raise ValueError('invalid index')
        for row in page['reports']:
            if not isinstance(row, dict): raise ValueError('invalid index row')
            rid = row.get('id')
            if not isinstance(rid, str) or not re.fullmatch(r'r:\d{13}:[a-fA-F0-9-]{8,36}', rid) or rid in seen or not isinstance(row.get('app'), str) or not row['app']:
                raise ValueError('invalid/ambiguous index')
            seen.add(rid)
            if report_version(row['app']) == version: index.append((rid, row['app']))
        if len(index) > 200: raise ValueError('target report budget')
        if page['complete']: break
        cursor = page.get('cursor')
        if not isinstance(cursor, str) or not cursor or len(cursor) > 2048 or cursor in cursors:
            raise ValueError('invalid/repeated cursor')
        cursors.add(cursor)
    else: raise ValueError('history exceeds budget')
    records = []
    for rid, index_app in index:
        report = worker.get('/reports/' + urllib.parse.quote(rid, safe=''))
        if not isinstance(report, dict) or report.get('app') != index_app or report_version(report.get('app')) != version:
            raise ValueError('index/detail mismatch')
        records.append({'id': rid, 'report': report})
    return {'complete': True, 'records': records}


def main():
    p = argparse.ArgumentParser(); p.add_argument('--fixture'); p.add_argument('--version', required=True)
    p.add_argument('--hours', type=int, default=24); p.add_argument('--now-ms', type=int); p.add_argument('--out', default='stability-gate.json')
    a = p.parse_args()
    try:
        if not re.fullmatch(r'\d+\.\d+\.\d+', a.version) or not 1 <= a.hours <= 168: raise ValueError('invalid gate input')
        if a.now_ms is not None and not a.fixture: raise ValueError('clock override is offline-only')
        data = json.loads(Path(a.fixture).read_text()) if a.fixture else collect(Worker(os.environ.get('CRASH_WORKER_URL', ''), os.environ.get('CRASH_ADMIN_TOKEN', '')), a.version)
        result = gate(data, a.version, a.hours, a.now_ms if a.now_ms is not None else int(time.time()*1000))
        Path(a.out).write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
        print('ALLOWED' if result['allowed'] else 'BLOCKED: new signatures; see sanitized output')
        return 0 if result['allowed'] else 1
    except (ValueError, KeyError, TypeError, OSError):
        print('BLOCKED: missing/incomplete evidence, API or configuration', file=sys.stderr); return 2


if __name__ == '__main__': sys.exit(main())
