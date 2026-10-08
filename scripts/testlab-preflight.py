"""Read-only exact-head gate and conservative physical budget. Never prints auth values."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ENGINE_PREFIXES = ('app/src/main/java/vn/aow/monika/runner/',
                   'app/src/main/java/vn/aow/monika/library/',
                   'app/src/main/java/vn/aow/monika/pack/', 'kirikiri/', 'rgss/',
                   'renpy/', 'azahar/', 'libretrodroid/')


def require_first_physical_attempt(raw_attempt):
    # GitHub preserves RUN_ID on rerun. Never exclude that history and spend again.
    if raw_attempt is None or not raw_attempt.isdecimal() or int(raw_attempt) != 1:
        raise ValueError('Physical rerun forbidden: GITHUB_RUN_ATTEMPT must be 1; reconcile failed runs in ledger')


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
    if isinstance(history_ids, dict):
        for run, required in history_ids.items():
            recorded = sum(r['units'] for r in todays if r['run'] == run)
            if recorded < required:
                raise ValueError('Physical attempts exceed recorded units: reconcile quota ledger before spending')
    used = sum(r['units'] for r in todays)
    if used >= 5:
        raise ValueError('Daily physical quota exhausted (5); no retry')
    return used


def last_physical_baseline(rows):
    # A rejected/reserved request counts against budget but proves no engine was tested.
    tested = [r for r in rows if not r.get('purpose', '').startswith('[DỰ TRỮ]')]
    if not tested:
        raise ValueError('Physical ledger has no tested baseline')
    return tested[-1]['head']


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


def physical_history(repo, workflows, day, own):
    """Read every attempt; unknown/rejected requests reserve conservatively, not usage proof."""
    required = {}
    requests = 0
    one_device = 'Chạy K1–K8 trên một máy ARM thật (một cách instrumentation)'
    virtual = 'Chạy Robo máy ảo (không quota máy thật)'
    for workflow, records in workflows:
        if len(records) >= 100:
            raise ValueError('Physical history exceeded bounded page; reconcile ledger')
        for run in records:
            if run['id'] == own:
                continue  # caller already proved RUN_ATTEMPT=1
            if max(run['created_at'][:10], run.get('updated_at', run['created_at'])[:10]) < day:
                continue
            attempts = run.get('run_attempt')
            if not isinstance(attempts, int) or not 1 <= attempts <= 10:
                raise ValueError('Physical history attempt count unknown or above bound')
            for attempt in range(1, attempts + 1):
                requests += 1
                if requests > 40:
                    raise ValueError('Physical history API budget exceeded; reconcile ledger')
                data = gh(repo + f'/actions/runs/{run["id"]}/attempts/{attempt}/jobs?per_page=100')
                jobs = data['jobs']
                if len(jobs) >= 100 or data.get('total_count', len(jobs)) > len(jobs):
                    raise ValueError('Physical attempt jobs truncated; reconcile ledger')
                steps = [step for job in jobs for step in job.get('steps', [])]
                dates = [job['started_at'][:10] for job in jobs if job.get('started_at')]
                dates += [step['started_at'][:10] for step in steps if step.get('started_at')]
                if not dates:
                    if attempt == 1:
                        dates = [run['created_at'][:10]]
                    elif attempt == attempts and run.get('updated_at'):
                        dates = [run['updated_at'][:10]]
                    else:
                        raise ValueError('Physical attempt date unknown; reconcile ledger')
                if day not in dates:
                    continue
                physical = [s for s in steps if 'Chạy K1–K8' in s['name'] or s['name'] == 'Chạy robot test']
                started = [s for s in physical if s.get('started_at', '') and s['started_at'][:10] == day and s.get('conclusion') != 'skipped']
                if not started and any(s['name'] == virtual for s in steps):
                    continue  # explicit virtual workflow; old/unknown Robo remains reserved physical
                # Failed/blocked attempts also reserve. Old or unknown requests may select two models.
                units = 1 if any(s['name'] == one_device for s in steps) else 2
                required[run['id']] = required.get(run['id'], 0) + units
    return required


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--physical', action='store_true')
    args = parser.parse_args()
    if args.physical:
        try:
            require_first_physical_attempt(os.environ.get('GITHUB_RUN_ATTEMPT'))
        except ValueError as error:
            raise SystemExit('BLOCKED: ' + str(error))
    repo = 'repos/' + os.environ['GITHUB_REPOSITORY']
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
    try:
        baseline = last_physical_baseline(rows)
    except ValueError as error:
        raise SystemExit('BLOCKED: ' + str(error))
    subprocess.run(['git', 'merge-base', '--is-ancestor', baseline, sha], check=True, capture_output=True)
    changed = subprocess.check_output(['git', 'diff', '--name-only', baseline, sha], text=True).splitlines()
    day = dt.datetime.now(dt.timezone.utc).date().isoformat()
    # Current RUN_ID may be excluded only after proving this is its first attempt.
    # One bounded page each. Unknown/unrecorded started physical runs block, never disappear from budget.
    own = int(os.environ['GITHUB_RUN_ID'])
    workflows = []
    for workflow in ('test-lab-engine-games.yml', 'test-lab.yml'):
        records = gh(repo + '/actions/workflows/' + workflow + '/runs?event=workflow_dispatch&per_page=100')['workflow_runs']
        workflows.append((workflow, records))
    try:
        history = physical_history(repo, workflows, day, own)
        used = physical_gate(sha, runs, changed, rows, day, history)
    except ValueError as error:
        raise SystemExit('BLOCKED: ' + str(error))
    print(f'Physical quota: {used}/5 used/reserved; this run reserves ONE. Record run after completion, including failure.')


if __name__ == '__main__':
    main()
