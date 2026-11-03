"""V177: đọc GitHub và dựng tin sáng; phần gửi dùng cơ chế Telegram hiện có."""
import argparse
from datetime import datetime, timedelta, timezone
import importlib.util
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit, urlunsplit


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
# Cùng ba đội của DUNG@80ed582f; đây là giao thức nhánh/tiền tố, không phải danh sách cá nhân.
TEAMS = (
    ('DeepSeek', r'deepseek[0-9]*/', r'^\[(?:Trưởng nhóm )?DeepSeek(?:\s*[0-9]+)?\s*→'),
    ('Sol 2', r'sol2/', r'^\[Sol 2\s*→'),
    ('Duyệt DeepSeek', r'duyet/', r'^\[(?:Trưởng nhóm )?Duyệt DeepSeek(?:\s*[0-9]+)?\s*→'),
)
STOPPED = re.compile(
    r'^(?:(?:tôi|đội|phiên|chúng tôi)\s+)?(?:đã\s+)?(?:tạm\s+)?'
    r'(?:dừng\s+(?:phiên|hoạt động)\b|ngừng\s+hoạt động\b|dừng(?=\s*[,.;:!]|\s*$))',
    re.IGNORECASE)


class DailyAPI(digest.GitHubAPI):
    """Link phân trang GitHub có thể dùng ID số; chỉ nhận đúng ID của repo hiện tại."""
    def __init__(self, repository, token, repository_id):
        super().__init__(repository, token)
        if not re.fullmatch('[1-9][0-9]*', str(repository_id)):
            raise ValueError('Thiếu ID repo do GitHub cung cấp.')
        self.numeric_path = '/repositories/' + str(repository_id) + '/'

    def _url(self, route):
        parsed = urlsplit(route)
        if (parsed.scheme == 'https' and parsed.netloc == 'api.github.com'
                and parsed.path.startswith(self.numeric_path)):
            path = self.repository_path + '/' + parsed.path[len(self.numeric_path):]
            route = urlunsplit(('https', 'api.github.com', path, parsed.query, ''))
        return super()._url(route)


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
        local_branch = (head.get('repo') or {}).get('full_name')
        local_branch = bool(local_branch) and local_branch == (
            (pr.get('base') or {}).get('repo') or {}).get('full_name')
        opened.append({
            'number': number, 'head': sha, 'branch': head.get('ref', '') if local_branch else '',
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


def idle_team_count(api, opened, now):
    """DUNG: giờ VN 7–23, có PR mở, hoạt động trong 24h, im ít nhất 2h."""
    now = now.astimezone(timezone.utc)
    if not 7 <= now.astimezone(timezone(timedelta(hours=7))).hour <= 23:
        return None
    eligible = [team for team in TEAMS if any(
        re.match(team[1], pr['branch'], re.IGNORECASE) for pr in opened)]
    if not eligible:
        return 0
    cutoff = now - timedelta(hours=24)
    since = cutoff.strftime('%Y-%m-%dT%H:%M:%SZ')
    pushes = api.list_recent('/activity?time_period=day&direction=desc&per_page=100',
                             cutoff, 'timestamp')
    # Repo-wide: gồm hộp thư bất kỳ, thảo luận PR và cả comment ở dòng mã.
    comments = []
    for route in ('/issues/comments', '/pulls/comments'):
        comments.extend(api.list_all(
            f'{route}?since={since}&sort=updated&direction=desc&per_page=100'))
    count = 0
    for name, branch, prefix in eligible:
        observed, stopped = [], []
        for event in pushes:
            if not re.match('refs/heads/' + branch, str(event.get('ref', '')), re.IGNORECASE):
                continue
            if event.get('activity_type') not in ('push', 'force_push', 'branch_creation'):
                continue
            at = digest.parse_time(event.get('timestamp'))
            if not at:
                raise RuntimeError('Hoạt động nhánh thiếu thời điểm; chưa đếm đội.')
            if cutoff <= at <= now:
                observed.append(at)
        for comment in comments:
            body = str(comment.get('body') or '').lstrip()
            if (comment.get('author_association') not in TRUSTED_ASSOCIATIONS
                    or not re.match(prefix, body, re.IGNORECASE)):
                continue
            at = digest.parse_time(comment.get('updated_at') or comment.get('created_at'))
            if not at:
                raise RuntimeError('Báo của đội thiếu thời điểm; chưa đếm đội.')
            if not cutoff <= at <= now:
                continue
            observed.append(at)
            reporter = ' '.join(body.split('→', 1)[0].lstrip('[').split()).lower()
            tail = re.sub(r'^\[[^\]]+\]\s*', '', body)
            if reporter in (name.lower(), 'trưởng nhóm ' + name.lower()) and STOPPED.match(tail):
                stopped.append(at)
        # Chưa khởi động trong 24h, hoặc báo dừng là hoạt động cuối cùng: bỏ qua.
        last = max(observed) if observed else None
        if last is None or (stopped and max(stopped) >= last):
            continue
        if now - last >= timedelta(hours=2):
            count += 1
    return count


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


def run(env, *, api=None, now=None, send=False, sender=None):
    repository = env.get('GITHUB_REPOSITORY', '')
    if not digest.valid_repository(repository):
        print('Chưa có owner/repository hợp lệ.', file=sys.stderr)
        return 1
    if api is None:
        if not env.get('GITHUB_TOKEN'):
            print('Chưa cấu hình quyền đọc GitHub cho lượt này.', file=sys.stderr)
            return 1
        try:
            api = DailyAPI(repository, env['GITHUB_TOKEN'], env.get('GITHUB_REPOSITORY_ID'))
        except ValueError:
            print('Chưa có mã định danh repo GitHub hợp lệ.', file=sys.stderr)
            return 1
    now = now or datetime.now(timezone.utc)
    try:
        snapshot = collect_snapshot(api, now)
        idle = idle_team_count(api, snapshot['open'], now)
        team_line = ('Đội im ≥2h: ngoài giờ canh 7–23 VN' if idle is None else
                     f'Đội im ≥2h: {idle} (có PR mở)')
        text = render_summary(snapshot, team_line)
    except digest.GitHubAPIError as error:
        print(f'Đọc GitHub lỗi HTTP {error.status_code}; chưa gửi tin.', file=sys.stderr)
        return 1
    except (OSError, RuntimeError, ValueError, KeyError, TypeError):
        print('Chưa dựng đủ dữ liệu GitHub hợp lệ; chưa gửi tin.', file=sys.stderr)
        return 1
    url = 'https://github.com/' + repository
    if not send:
        print(telegram.telegram_text(text, url))
        return 0
    if not env.get('TELEGRAM_BOT_TOKEN') or not env.get('TELEGRAM_CHAT_ID'):
        print('Chưa cấu hình kênh Telegram hiện có; chưa gửi tin.', file=sys.stderr)
        return 1
    author = repository.split('/')[0]
    if not telegram.should_forward(author, text, telegram.allowed_logins(env.get('ALLOWED_LOGINS'))):
        print('Kênh hiện có không cho phép người gửi tin sáng.', file=sys.stderr)
        return 1
    message_env = dict(env, COMMENT_AUTHOR=author, COMMENT_BODY=text, COMMENT_URL=url)
    return (sender or telegram.run)(message_env)


def main(argv=None):
    parser = argparse.ArgumentParser(description='Tin sáng Monika: mặc định chỉ xem trước.')
    parser.add_argument('--send', action='store_true', help='Gửi qua kênh Telegram hiện có.')
    return run(os.environ, send=parser.parse_args(argv).send)


if __name__ == '__main__':
    raise SystemExit(main())
