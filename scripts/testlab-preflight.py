"""Read-only exact-head gate and conservative physical budget. Never prints auth values."""
import argparse
import datetime as dt
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ENGINE_PREFIXES = ('app/src/main/java/vn/aow/monika/runner/',
                   'app/src/main/java/vn/aow/monika/library/',
                   'app/src/main/java/vn/aow/monika/pack/', 'kirikiri/', 'rgss/',
                   'renpy/', 'azahar/', 'libretrodroid/')


def physical_gate(sha, checks, changed, rows, day, history_ids):
    if not any(r.get('head_sha') == sha and r.get('name') == 'Build'
               and r.get('conclusion') == 'success' for r in checks):
        raise ValueError('Build must be SUCCESS on exact SHA')
    if not any(p.startswith(ENGINE_PREFIXES) or p == 'config/monika-config.json' for p in changed):
        raise ValueError('No engine/runner/library/pack delta since last physical test')
    todays = [r for r in rows if r['day'] == day]
    known = {r['run'] for r in todays}
    if set(history_ids) - known:
        raise ValueError('Unrecorded physical run: reconcile quota ledger before spending')
    used = sum(r['units'] for r in todays)
    if used >= 5:
        raise ValueError('Daily physical quota exhausted (5); no retry')
    return used


def ledger():
    rows = []
    for line in (ROOT / 'docs/opus/ket-qua/test-lab-quota.md').read_text().splitlines():
        fields = [s.strip() for s in line.split('|')[1:-1]]
        if len(fields) != 7 or not fields[0].startswith('20'):
            continue
        day, run, workflow, model, purpose, head, units = fields
        rows.append(dict(day=day, run=int(run), workflow=workflow, model=model,
                         purpose=purpose, head=head, units=int(units)))
    return rows


def gh(path):
    result = subprocess.run(['gh', 'api', path], check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--physical', action='store_true')
    args = parser.parse_args()
    repo = 'repos/' + __import__('os').environ['GITHUB_REPOSITORY']
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    runs = gh(repo + '/actions/runs?head_sha=' + sha + '&per_page=100')['workflow_runs']
    if not args.physical:
        if not any(r['name'] == 'Build' and r['head_sha'] == sha and r['conclusion'] == 'success' for r in runs):
            raise SystemExit('BLOCKED: exact-head Build not SUCCESS')
        print('Virtual Robo: exact-head Build SUCCESS; physical quota untouched')
        return
    rows = ledger()
    if not rows:
        raise SystemExit('BLOCKED: physical ledger has no baseline')
    baseline = rows[-1]['head']
    subprocess.run(['git', 'merge-base', '--is-ancestor', baseline, sha], check=True, capture_output=True)
    changed = subprocess.check_output(['git', 'diff', '--name-only', baseline, sha], text=True).splitlines()
    day = dt.datetime.now(dt.timezone.utc).date().isoformat()
    # One bounded page each. Unknown/unrecorded started physical runs block, never disappear from budget.
    history = []
    own = int(__import__('os').environ['GITHUB_RUN_ID'])
    for workflow in ('test-lab-engine-games.yml', 'test-lab.yml'):
        records = gh(repo + '/actions/workflows/' + workflow + '/runs?event=workflow_dispatch&per_page=100')['workflow_runs']
        if len(records) == 100 and records[-1]['created_at'][:10] >= day:
            raise SystemExit('BLOCKED: daily history exceeded bounded page')
        for run in records:
            if run['created_at'][:10] != day or run['id'] == own:
                continue
            jobs = gh(repo + '/actions/runs/' + str(run['id']) + '/jobs?per_page=100')['jobs']
            physical_started = any(step.get('started_at') and step.get('conclusion') != 'skipped'
                                   and ('Chạy K1–K8' in step['name'] or step['name'] == 'Chạy robot test')
                                   for job in jobs for step in job.get('steps', []))
            # New virtual Robo opts out via an explicit step name; old Robo is conservatively physical.
            if physical_started:
                history.append(run['id'])
    try:
        used = physical_gate(sha, runs, changed, rows, day, history)
    except ValueError as error:
        raise SystemExit('BLOCKED: ' + str(error))
    print(f'Physical quota: {used}/5 used/reserved; this run reserves ONE. Record run after completion, including failure.')


if __name__ == '__main__':
    main()
