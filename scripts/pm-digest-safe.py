#!/usr/bin/env python3
"""V39: wrapper L10, scrub metadata rồi xuất; chỉ được force ref bot/trang-thai."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.error
import urllib.request

spec = importlib.util.spec_from_file_location('digest', Path(__file__).with_name('pm-digest.py'))
digest = importlib.util.module_from_spec(spec); spec.loader.exec_module(digest)
BOT_REF = 'refs/heads/bot/trang-thai'
REPOSITORY = 'aowvn-10diem/aowvn-monika'
# Public repository ID measured from GitHub metadata/Link; never an input.
REPOSITORY_ID = 1392088306
SENSITIVE_KEYS = {'body', 'email', 'path', 'token', 'authorization', 'password', 'secret', 'author', 'login'}

class DigestUnavailable(RuntimeError):
    """A bounded data-source failure; never use it to bypass input/publish guards."""

def retry_http(call, sleep=None):
    """Three attempts on 403/429 only; at most two 30s waits, no blind mutation retry."""
    wait = sleep or time.sleep
    for attempt in range(3):
        try:
            return call()
        except digest.GitHubAPIError as error:
            if error.status_code not in (403, 429) or attempt == 2:
                raise
            reset_wait = max(0, (error.reset or 0) - int(time.time())) if error.remaining == 0 else 0
            required_wait = max(error.retry_after or 0, reset_wait)
            if required_wait > 30:
                # Respect server backoff without holding a runner until the next quota window.
                raise
            delay = max(5 if attempt == 0 else 15, required_wait)
            print('::warning::Digest API retry ' + json.dumps(error.diagnostic()) + f'; wait={delay}s', file=sys.stderr)
            wait(delay)

def safe_text(value):
    # Giữ URL run công khai đúng repo; không query/redirect/token.
    if re.fullmatch(r'https://github\.com/aowvn-10diem/aowvn-monika/actions/runs/[0-9]+', value): return value
    value = re.sub(r'[\w.+-]+@[\w-]+(?:\.[\w-]+)+', '<email>', value)
    value = re.sub(r'(?i)\b(?:Bearer|Basic)\s+\S+', '<ẩn>', value)
    value = re.sub(r'(?i)\b(?:token|password|passwd|secret|authorization|key)\s*[:=]\s*\S+', '<ẩn>', value)
    value = re.sub(r'\b(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)\b', '<ẩn>', value)
    value = re.sub(r'(?:https?|file|content)://[^\s<>]+', '<url>', value)
    value = re.sub(r'(?:[A-Za-z]:[\\/]|(?<!\w)/)[^\r\n\"\'<>]+', '<đường-dẫn>', value)
    value = re.sub(r'\b(?:[\w.-]+/)+[\w.-]+\.[A-Za-z0-9]{1,8}\b', '<đường-dẫn>', value)
    return ' '.join(value.splitlines())[:240]

def sanitize(value, field=''):
    if isinstance(value, dict): return {k: sanitize(v, k) for k, v in value.items() if k.lower() not in SENSITIVE_KEYS}
    if isinstance(value, list): return [sanitize(v, field) for v in value]
    if isinstance(value, str):
        if field == 'repository': return REPOSITORY
        if field in {'branch', 'name'} and re.fullmatch(r'(?:sol|sonnet|luna)/[A-Za-z0-9_.-]+', value): return value
        return safe_text(value)
    return value

class BoundedDigestAPI(digest.GitHubAPI):
    def __init__(self, repository, token):
        super().__init__(repository, token)
        self.cache = {}
        self.reads = 0

    def _request_json(self, route):
        key = self._url(route)  # Security guards still fail hard, before soft transport handling.
        if key in self.cache:
            return self.cache[key]
        def request():
            if self.reads >= 120:
                raise DigestUnavailable('API read budget 120 exhausted; no partial digest published')
            self.reads += 1
            try:
                data, headers = super(BoundedDigestAPI, self)._request_json(route)
                if self.reads == 1:
                    print('Digest API quota hints: ' + json.dumps(digest.GitHubAPIError(200, headers=headers).diagnostic()), file=sys.stderr)
                return data, headers
            except RuntimeError as error:
                if str(error) in {'GitHub API request failed.', 'GitHub API returned invalid JSON.'}:
                    raise DigestUnavailable(str(error)) from None
                raise
        self.cache[key] = retry_http(request)
        return self.cache[key]

    def _url(self, route):
        if route.startswith('https://'):
            parsed = urllib.parse.urlsplit(route)
            prefix = '/repositories/' + str(REPOSITORY_ID) + '/'
            if parsed.netloc == 'api.github.com' and parsed.path.startswith(prefix):
                suffix = parsed.path[len(prefix):]
                # Không chấp nhận đường vượt repo hoặc thành phần URL bị che bằng encoding.
                if parsed.fragment or '%' in parsed.path or any(p in {'.', '..'} for p in suffix.split('/')):
                    raise RuntimeError('Pagination URL không hợp lệ')
                route = 'https://api.github.com' + self.repository_path + '/' + suffix
                if parsed.query: route += '?' + parsed.query
        return super()._url(route)

    def list_all(self, route, field=None):
        # L10 cần chỉ 10 commit/release mới nhất; hai endpoint này không đi tiếp lịch sử.
        if route in {'/commits?sha=main&per_page=10', '/releases?per_page=10'}:
            data, _ = self._request_json(route)
            if not isinstance(data, list): raise RuntimeError('Danh sách commit/release không hợp lệ')
            return data[:10]
        return super().list_all(route, field)

class GitDB:
    def __init__(self, token): self.token = token
    def request(self, method, route, payload=None):
        # Không nhận repo/ref từ input; caller chỉ dùng routes nội bộ kiểm bên dưới.
        url = 'https://api.github.com/repos/' + REPOSITORY + route
        request = urllib.request.Request(url, method=method, data=None if payload is None else json.dumps(payload).encode(),
            headers={'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json', 'Content-Type':'application/json', 'X-GitHub-Api-Version':'2022-11-28'})
        def send():
            try:
                with urllib.request.urlopen(request, timeout=30) as response: return json.load(response)
            except urllib.error.HTTPError as error:
                if method == 'GET' and error.code == 404: return None
                raise digest.GitHubAPIError.from_http(error) from None
            except urllib.error.URLError:
                # Unknown outcome on POST/PATCH: do not create duplicate commits by retrying blindly.
                raise DigestUnavailable('GitDB không truy cập được; không retry mutation mù') from None
            except json.JSONDecodeError:
                raise DigestUnavailable('GitDB trả JSON không hợp lệ; không retry mutation mù') from None
        return retry_http(send)

def publish(client, data):
    safe = sanitize(data)
    tree = client.request('POST', '/git/trees', {'tree': [
        {'path':'docs/trang-thai/digest.json', 'mode':'100644', 'type':'blob', 'content':json.dumps(safe,ensure_ascii=False,indent=2)+'\n'},
        {'path':'docs/trang-thai/digest.md', 'mode':'100644', 'type':'blob', 'content':digest.render_markdown(safe)},
    ]})
    # Không base_tree/parents: một root commit, không giữ lịch sử hay file của main.
    commit = client.request('POST', '/git/commits', {'message':'bot: cập nhật trạng thái PM', 'tree':tree['sha'], 'parents':[]})
    route = '/git/refs/heads/bot/trang-thai'
    if client.request('GET', '/git/ref/heads/bot/trang-thai') is None:
        client.request('POST', '/git/refs', {'ref': BOT_REF, 'sha':commit['sha']})
    else:
        client.request('PATCH', route, {'sha':commit['sha'], 'force':True})
    return commit['sha']

def main(argv=None):
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--fixture',type=Path);p.add_argument('--publish',action='store_true');args=p.parse_args(argv)
    try:
        if args.publish and args.fixture: raise RuntimeError('Không đăng fixture lên bot')
        token=os.environ.get('GITHUB_TOKEN','')
        if not args.fixture and (not token or os.environ.get('GITHUB_REPOSITORY')!=REPOSITORY): raise RuntimeError('Thiếu quyền hoặc repo không đúng')
        data=json.loads(args.fixture.read_text()) if args.fixture else digest.build_digest(BoundedDigestAPI(REPOSITORY,token),REPOSITORY)
        data=sanitize(data);args.out.mkdir(parents=True,exist_ok=True)
        (args.out/'digest.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        (args.out/'digest.md').write_text(digest.render_markdown(data))
        if args.publish: print('Đã đăng root commit ' + publish(GitDB(token),data)[:7] + ' vào bot/trang-thai')
        else: print('Đã chuẩn bị digest; chưa đăng bot')
        return 0
    except (digest.GitHubAPIError, DigestUnavailable) as error:
        # Ancillary job stays green with an explicit warning; preserve last complete bot snapshot.
        hint = error.diagnostic() if isinstance(error, digest.GitHubAPIError) else {'kind': 'unavailable', 'detail': str(error)}
        hint = sanitize(hint)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out/'warning.json').write_text(json.dumps(hint, ensure_ascii=False, indent=2)+'\n')
        (args.out/'warning.md').write_text('# CẢNH BÁO: digest không cập nhật được\nKhông coi job xanh là dữ liệu mới. Giữ bot ở snapshot đầy đủ gần nhất.\n'+json.dumps(hint, ensure_ascii=False)+'\n')
        print('::warning::Digest không cập nhật được; giữ snapshot bot cũ: '+json.dumps(hint, ensure_ascii=False), file=sys.stderr)
        return 0
    except (RuntimeError,OSError) as error:
        print(safe_text(str(error)),file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
