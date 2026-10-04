#!/usr/bin/env python3
"""Sửa state.json của trang tiến độ (docs/opus/pm/bang-tien-do/state.json). Giờ luôn lấy từ `date -u` thật.

Mỗi lệnh con sửa 1 thứ và luôn cập nhật `updatedAt`:
  task   CODE [--status dang|xong|lap|cho|ket|sep] [--note T] [--owner T] [--module ID] [--title T]
         (CODE chưa có thì cần --title để tạo mới)
  module ID  BƯỚC TRẠNG_THÁI [--note T]     BƯỚC = build|auto|phone|release; trạng thái: xong|dang|cho|sep|loi|na
  log    "nội dung"                         thêm dòng log mới nhất lên đầu, giờ = bây giờ
  meta   [--main SHA] [--headline T] [--app T] [--next-check-min N]   nextCheck = bây giờ + N phút
  show                                      in updatedAt, nextCheck, số việc theo trạng thái
Tùy chọn chung: --state đường/dẫn/state.json   --dry-run
Sau khi sửa: đẩy ArtifactData `board/state` (có if_version) rồi mới commit; không chạy song song.
"""
import argparse, collections, json, subprocess, sys

DEFAULT = 'docs/opus/pm/bang-tien-do/state.json'
STEPS = {'build', 'auto', 'phone', 'release'}


def utc(plus_min=0):
    cmd = ['date', '-u', '+%Y-%m-%dT%H:%M:%SZ']
    if plus_min:
        cmd[1:1] = ['-d', f'+{plus_min} minutes']
    return subprocess.check_output(cmd, text=True).strip()


def find(items, key, val):
    return next((x for x in items if x.get(key) == val), None)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--state', default=DEFAULT)
    ap.add_argument('--dry-run', action='store_true')
    sub = ap.add_subparsers(dest='cmd', required=True)
    t = sub.add_parser('task'); t.add_argument('code')
    for f in ('status', 'note', 'owner', 'module', 'title'):
        t.add_argument('--' + f)
    m = sub.add_parser('module'); m.add_argument('id'); m.add_argument('step'); m.add_argument('status'); m.add_argument('--note')
    l = sub.add_parser('log'); l.add_argument('text')
    e = sub.add_parser('meta')
    for f in ('main', 'headline', 'app'):
        e.add_argument('--' + f)
    e.add_argument('--next-check-min', type=int)
    sub.add_parser('show')
    a = ap.parse_args()

    s = json.load(open(a.state, encoding='utf-8'))
    now = utc()
    if a.cmd == 'show':
        c = collections.Counter(x['status'] for x in s['tasks'])
        print('updatedAt', s['updatedAt'], '| nextCheck', s.get('nextCheck'), '| main', s.get('mainSha'))
        print('việc theo trạng thái:', dict(c)); print('log mới nhất:', s['log'][0] if s['log'] else None)
        return
    if a.cmd == 'task':
        x = find(s['tasks'], 'code', a.code)
        if x is None:
            if not a.title:
                sys.exit(f'LỖI: chưa có việc {a.code}; thêm --title để tạo mới.')
            x = {'code': a.code, 'title': a.title, 'owner': a.owner or '', 'status': a.status or 'cho', 'note': a.note or ''}
            s['tasks'].append(x)
        for f in ('status', 'note', 'owner', 'module', 'title'):
            if getattr(a, f) is not None:
                x[f] = getattr(a, f)
    elif a.cmd == 'module':
        if a.step not in STEPS:
            sys.exit(f'LỖI: bước phải thuộc {sorted(STEPS)}')
        x = find(s['modules'], 'id', a.id)
        if x is None:
            sys.exit(f'LỖI: không có module {a.id}; có: ' + ', '.join(y['id'] for y in s['modules']))
        x['steps'][a.step] = a.status
        if a.note is not None:
            x['note'] = a.note
    elif a.cmd == 'log':
        s['log'].insert(0, {'t': now, 'text': a.text})
    elif a.cmd == 'meta':
        if a.main: s['mainSha'] = a.main
        if a.headline: s['headline'] = a.headline
        if a.app: s['app'] = a.app
        if a.next_check_min: s['nextCheck'] = utc(a.next_check_min)
    s['updatedAt'] = now
    out = json.dumps(s, ensure_ascii=False, indent=1) + '\n'
    if a.dry_run:
        print(f'(dry-run) updatedAt={now}, {len(out)} byte; chưa ghi'); return
    open(a.state, 'w', encoding='utf-8').write(out)
    print('Đã ghi', a.state, '| updatedAt', now)


main()
