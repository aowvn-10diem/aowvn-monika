#!/usr/bin/env python3
"""Duy trì một issue của V42; API input JSON, không lấy nội dung log/token."""
import json
import os
import subprocess

MARKER = '<!-- monika-periodic-check -->'
LABEL = 'kiem-dinh-ky'


def reconcile(api, repo, jobs, run_url, sha, remote_config=None):
    base = f'repos/{repo}'
    groups = api('GET', f'{base}/issues?labels={LABEL}&state=all&per_page=100', paginate=True)
    issues = [i for group in groups for i in group if 'pull_request' not in i and MARKER in i.get('body', '')]
    # Giữ issue cũ nhất làm định danh ổn định, không phụ thuộc thứ tự API.
    issues.sort(key=lambda item: item['number'])
    issue = issues[0] if issues else None
    failed = {name: job['result'] for name, job in jobs.items() if job['result'] != 'success'}
    config_note = ""
    if remote_config is not None:
        # Only whitelisted metadata; no raw response/body/error prose can enter issue.
        remote = remote_config.get('remote_version')
        main = remote_config.get('main_version')
        remote = remote if type(remote) is int else '[CHƯA KIỂM]'
        main = main if type(main) is int else '[CHƯA KIỂM]'
        config_note = f'\nConfig công khai: KV **{remote}**, main **{main}**.\n'
        status = remote_config.get('status')
        if status != 'current':
            failed['remote-config'] = status if status in {'stale', 'unknown'} else 'unknown'
            config_note += 'KV cũ hoặc chưa đọc được: chờ đồng bộ/kiểm tra sync-config; secret CLOUDFLARE_API_TOKEN [CHƯA KIỂM], không đọc giá trị hoặc lặp yêu cầu owner.\n'
    if not failed:
        if issue and remote_config is not None:
            api('PATCH', f"{base}/issues/{issue['number']}", {'body': MARKER + '\nKiểm định kỳ đạt.\n' + config_note + f'\nRun: {run_url}\nCommit: `{sha}`\n'})
        for item in issues:
            if item['state'] == 'open':
                api('PATCH', f"{base}/issues/{item['number']}", {'state': 'closed', 'state_reason': 'completed'})
        return
    body = MARKER + '\nKiểm định kỳ chưa đạt.\n\n' + '\n'.join(f'- {name}: {job["result"]}' for name, job in jobs.items())
    body += config_note
    body += f'\n\nRun: {run_url}\nCommit: `{sha}`\nXem bảng link/gói và báo cáo unit trong artifact của run. Issue tự đóng khi các nhóm kiểm và config từ xa đạt.\n'
    data = {'title': 'Kiểm định kỳ đỏ', 'body': body, 'state': 'open'}
    if issue:
        api('PATCH', f"{base}/issues/{issue['number']}", data)
        # Chỉ đóng bản trùng do bot sở hữu; không đụng issue khác cùng nhãn.
        for duplicate in issues[1:]:
            if duplicate['state'] == 'open':
                api('PATCH', f"{base}/issues/{duplicate['number']}", {'state': 'closed', 'state_reason': 'not_planned'})
    else:
        labels = api('GET', f'{base}/labels?per_page=100', paginate=True)
        if not any(label['name'] == LABEL for group in labels for label in group):
            api('POST', f'{base}/labels', {'name': LABEL, 'color': 'b60205', 'description': 'Kết quả kiểm định kỳ V42'})
        data.pop('state')
        data['labels'] = [LABEL]
        api('POST', f'{base}/issues', data)


def github_api(method, endpoint, data=None, paginate=False):
    command = ['gh', 'api', '--method', method, endpoint]
    if paginate:
        command += ['--paginate', '--slurp']
    if data is not None:
        command += ['--input', '-']
    result = subprocess.run(command, input=json.dumps(data) if data is not None else None, text=True, capture_output=True, check=True)
    return json.loads(result.stdout)


if __name__ == '__main__':
    repo = os.environ['GITHUB_REPOSITORY']
    run_url = os.environ.get('GITHUB_SERVER_URL', 'https://github.com') + f"/{repo}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    reconcile(github_api, repo, json.loads(os.environ['JOB_RESULTS']), run_url, os.environ['GITHUB_SHA'], json.loads(os.environ.get('REMOTE_CONFIG_REPORT', 'null')))
