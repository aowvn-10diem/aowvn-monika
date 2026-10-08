"""V66 read-only release gate. No signing fields, tag writes, or release requests."""
import json
import os
import re
import subprocess


def validate(sha, on_main, tag_exists, create, runs, analyze):
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('SHA must be full40hex')
    if not on_main:
        raise ValueError('SHA outside main')
    if create and tag_exists:
        raise ValueError('Tag already exists; never overwrite/delete')
    for name in ('Build', 'Coverage', 'CodeQL (V41)'):
        candidates = [r for r in runs if r.get('name') == name and r.get('head_sha') == sha]
        if not candidates or max(candidates, key=lambda r: r['id']).get('conclusion') != 'success':
            raise ValueError(name + ' not SUCCESS on exact SHA')
    languages = ('java-kotlin', 'javascript-typescript')
    if (any(not any(j.get('name') == 'analyze (' + lang + ')' for j in analyze) for lang in languages)
            or any(j.get('conclusion') != 'success' for j in analyze)):
        raise ValueError('CodeQL/analyze not all SUCCESS')


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def gh(path):
    return json.loads(subprocess.run(['gh', 'api', path], check=True, capture_output=True, text=True).stdout)


def main():
    event, ref = os.environ['EVENT'], os.environ['REF']
    requested = os.environ.get('REQUESTED_SHA', '')
    create = bool(requested)
    if event == 'workflow_dispatch' and ref != 'refs/heads/main':
        raise SystemExit('BLOCKED: dispatch only main')
    if create and not re.fullmatch(r'[0-9a-f]{40}', requested):
        raise SystemExit('BLOCKED: SHA must be full40hex')
    tag = os.environ.get('REQUESTED_TAG', '') or (ref.removeprefix('refs/tags/') if event == 'push' else '')
    if create:
        sha = requested
        source = git('show', sha + ':app/build.gradle.kts')
        found = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', source)
        if not found:
            raise SystemExit('BLOCKED: app versionName not recognized')
        derived = 'v' + found[1]
        if tag and tag != derived:
            raise SystemExit('BLOCKED: tag must match selected app version')
        tag = derived
    else:
        if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?', tag):
            raise SystemExit('BLOCKED: invalid existing tag')
        sha = git('rev-parse', 'refs/tags/' + tag + '^{commit}')
    on_main = subprocess.run(['git', 'merge-base', '--is-ancestor', sha, 'origin/main'], capture_output=True).returncode == 0
    tag_exists = subprocess.run(['git', 'show-ref', '--verify', '--quiet', 'refs/tags/' + tag]).returncode == 0
    if not create and not tag_exists:
        raise SystemExit('BLOCKED: existing-tag mode needs a real tag')
    repo = 'repos/' + os.environ['GITHUB_REPOSITORY']
    runs = gh(repo + '/actions/runs?head_sha=' + sha + '&per_page=100')['workflow_runs']
    codeql = [r for r in runs if r['name'] == 'CodeQL (V41)' and r['head_sha'] == sha]
    latest = max(codeql, key=lambda r: r['id']) if codeql else None
    jobs = gh(repo + '/actions/runs/' + str(latest['id']) + '/jobs?per_page=100')['jobs'] if latest else []
    analyze = [j for j in jobs if 'analyze' in j['name'].lower()]
    try:
        validate(sha, on_main, tag_exists, create, runs, analyze)
    except ValueError as error:
        raise SystemExit('BLOCKED: ' + str(error))
    prerelease = os.environ.get('PRERELEASE', 'true').lower()
    if prerelease not in ('true', 'false'):
        raise SystemExit('BLOCKED: prerelease must be boolean')
    # Backward-compatible old stable input, still only PM may authorize a stable release.
    if os.environ.get('STABLE') == 'true':
        prerelease = 'false'
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        for key, value in dict(sha=sha, tag=tag, create=str(create).lower(), prerelease=prerelease).items():
            output.write(key + '=' + value + '\n')
    print('Exact main SHA + Build/Coverage/all CodeQL-analyze SUCCESS; tag policy PASS. No mutation performed.')


if __name__ == '__main__':
    main()
