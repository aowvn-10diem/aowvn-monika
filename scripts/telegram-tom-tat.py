"""V177: đọc GitHub và dựng tin sáng; phần gửi dùng cơ chế Telegram hiện có."""
from datetime import datetime, timedelta, timezone
import importlib.util
from pathlib import Path
import re
import sys


def script_module(name):
    spec = importlib.util.spec_from_file_location(
        'daily_' + name.replace('-', '_'), Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


digest = script_module('pm-digest')
telegram = script_module('telegram-bao-sep')
TRUSTED_ASSOCIATIONS = {'OWNER', 'MEMBER', 'COLLABORATOR'}
VERDICT = re.compile(
    r'^[ \t*#`]*Kết luận[ \t*`]*:[ \t*`]*(Đạt|Cần sửa|Cần PM xem)\b',
    re.MULTILINE | re.IGNORECASE)
SHA_HEADER = re.compile(r'\b(?:commit|head)\s+`?([0-9a-f]{7,40})\b', re.IGNORECASE)
CANONICAL = {value.lower(): value for value in ('Đạt', 'Cần sửa', 'Cần PM xem')}


def latest_verdict(comments, reviews, head):
    """Chỉ kết luận của người có quyền, ghim đúng head; sửa comment tính theo updated_at."""
    found = []
    for item in [*comments, *reviews]:
        if item.get('author_association') not in TRUSTED_ASSOCIATIONS:
            continue
        native = item.get('commit_id')
        if native:
            if native != head or item.get('state') in ('PENDING', 'DISMISSED'):
                continue
        else:
            header = str(item.get('body') or '').lstrip().splitlines()
            match = SHA_HEADER.search(header[0]) if header else None
            if not match or not head.startswith(match.group(1).lower()):
                continue
        body = str(item.get('body') or '')
        verdicts = VERDICT.findall(body)
        if verdicts:
            status = CANONICAL[verdicts[-1].lower()]
        else:
            status = {'APPROVED': 'Đạt', 'CHANGES_REQUESTED': 'Cần sửa'}.get(
                item.get('state')) if native else None
        if not status:
            continue
        at = max(digest.timestamp(item.get(key))
                 for key in ('updated_at', 'submitted_at', 'created_at'))
        found.append({'status': status, 'at': at, 'url': item.get('html_url', '')})
    return max(found, key=lambda item: item['at']) if found else {
        'status': 'chưa duyệt head', 'at': None, 'url': '',
    }


def collect_snapshot(api, now):
    """Dùng helper API chỉ đọc, phân trang cùng repo; lỗi API làm hỏng cả lượt."""
    now = now.astimezone(timezone.utc)
    cutoff = now - timedelta(hours=24)
    opened = []
    for pr in api.list_all('/pulls?state=open&per_page=100'):
        head = pr.get('head') or {}
        sha = str(head.get('sha') or '')
        if not re.fullmatch('[0-9a-f]{40}', sha) or not isinstance(pr.get('number'), int):
            raise RuntimeError('GitHub trả PR thiếu số hoặc head hợp lệ.')
        number = pr['number']
        verdict = latest_verdict(
            api.list_all(f'/issues/{number}/comments?per_page=100'),
            api.list_all(f'/pulls/{number}/reviews?per_page=100'), sha)
        opened.append({
            'number': number, 'head': sha, 'branch': head.get('ref', ''),
            'status': verdict['status'], 'review_url': verdict['url'],
            'url': pr.get('html_url', ''),
        })
    merged = []
    for pr in api.list_recent(
            '/pulls?state=closed&sort=updated&direction=desc&per_page=100',
            cutoff, 'updated_at'):
        at = digest.parse_time(pr.get('merged_at'))
        if at and cutoff <= at <= now:
            merged.append({'number': pr['number'], 'at': at, 'url': pr.get('html_url', '')})
    merged.sort(key=lambda item: item['at'], reverse=True)
    releases = []
    for release in api.list_all('/releases?per_page=100'):
        if release.get('draft'):
            continue
        at = digest.parse_time(release.get('published_at'))
        if not at:
            raise RuntimeError('GitHub trả bản phát hành thiếu thời điểm công bố.')
        if cutoff <= at <= now:
            releases.append({
                'tag': release['tag_name'], 'at': at,
                'prerelease': bool(release.get('prerelease')),
                'url': release.get('html_url', ''),
            })
    releases.sort(key=lambda item: item['at'], reverse=True)
    return {'at': now, 'open': opened, 'merged': merged, 'releases': releases}


def one_line(value, limit):
    text = re.sub(r'[\x00-\x1f\x7f-\x9f]', ' ', str(value))
    text = ' '.join(text.split())
    return text if len(text) <= limit else text[:limit - 1] + '…'


def compact(label, values, limit):
    prefix = f'{label} ({len(values)}): '
    if not values:
        return prefix + 'không có'
    shown = []
    for value in values:
        candidate = shown + [value]
        left = len(values) - len(candidate)
        suffix = f'; +{left} ở link' if left else ''
        if len(prefix + '; '.join(candidate) + suffix) > limit:
            break
        shown = candidate
    left = len(values) - len(shown)
    return prefix + '; '.join(shown) + (f'; +{left} ở link' if left else '')


def render_summary(snapshot, team_line):
    """team_line do bộ đo hoạt động cung cấp; không suy ra agent đã dừng từ một PR."""
    date = snapshot['at'].astimezone(timezone(timedelta(hours=7))).strftime('%d/%m/%Y')
    opened = sorted(snapshot['open'], key=lambda item: (
        {'Cần sửa': 0, 'Cần PM xem': 1, 'chưa duyệt head': 2, 'Đạt': 3}[item['status']],
        -item['number']))
    shown = opened[:5]
    remaining = len(opened) - len(shown)
    lines = [
        f'[PM → sếp] Tin sáng {date}',
        compact('Gộp 24h', ['#' + str(item['number']) for item in snapshot['merged']], 100),
        f'Chờ duyệt: {len(opened)}' + (f'; còn {remaining} ở link' if remaining else ''),
        *[f"#{item['number']}@{item['head'][:7]}: {item['status']}" for item in shown],
        one_line(team_line, 95),
        compact('Phát hành 24h', [
            one_line(item['tag'], 30) + (' (thử)' if item['prerelease'] else '')
            for item in snapshot['releases']], 80),
    ]
    text = '\n'.join(lines)
    # Bot hiện có sẽ thêm một dòng URL: tổng tối đa 15 dòng, không bị cắt 600 ký tự.
    if len(lines) > 14 or len(text) > telegram.MAX_CHARS:
        raise RuntimeError('Tin sáng vượt giới hạn dòng/ký tự; không gửi bản bị cắt.')
    return text
