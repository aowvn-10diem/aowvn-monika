#!/usr/bin/env python3
"""Read CodeQL metadata for V49 without source snippets, alert text or writes."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request

REPOSITORY = "aowvn-10diem/aowvn-monika"
API = "https://api.github.com/repos/" + REPOSITORY


def summarize(alert, tracked):
    rule = alert.get("rule") or {}
    instance = alert.get("most_recent_instance") or {}
    location = instance.get("location") or {}
    path = location.get("path")
    rule_id = rule.get("id")
    severity = rule.get("security_severity_level")
    number = alert.get("number")
    if not isinstance(number, int) or isinstance(number, bool) or number <= 0:
        raise RuntimeError("Invalid alert number")
    if not isinstance(rule_id, str) or not re.fullmatch(r"[A-Za-z0-9_./-]{1,160}", rule_id):
        raise RuntimeError("Invalid rule id")
    line = location.get("start_line")
    return {
        "number": number, "rule": rule_id,
        "severity": severity if severity in {"critical", "high", "medium", "low"} else None,
        "path": path if path in tracked else None,
        "path_status": "tracked" if path in tracked else "untracked",
        "line": line if isinstance(line, int) and not isinstance(line, bool) and line > 0 else None,
        "url": f"https://github.com/{REPOSITORY}/security/code-scanning/{number}",
    }


def collect(get_page, tracked):
    found = {}
    for page in range(1, 21):
        alerts = get_page(page)
        if not isinstance(alerts, list):
            raise RuntimeError("Unexpected CodeQL response")
        for alert in alerts:
            item = summarize(alert, tracked)
            found[item["number"]] = item
        if len(alerts) < 100:
            return [found[n] for n in sorted(found)]
    raise RuntimeError("CodeQL pagination budget exhausted; inventory incomplete")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        parser.error("Unexpected repository")
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        parser.error("Missing workflow token")
    tracked = set(subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0"))
    def get_page(page):
        request = urllib.request.Request(
            API + f"/code-scanning/alerts?state=open&per_page=100&page={page}",
            headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "monika-v49-read-only"},
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    try:
        alerts = collect(get_page, tracked)
        data = {"repository": REPOSITORY, "commit": subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip(),
                "open_count": len(alerts), "alerts": alerts}
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "inventory.json").write_text(json.dumps(data, indent=2) + "\n")
        print(f"Read {len(alerts)} open alerts; no mutations performed")
    except urllib.error.HTTPError as error:
        print(f"CodeQL inventory unavailable: HTTP {error.code}")
        return 1
    except (urllib.error.URLError, json.JSONDecodeError, RuntimeError):
        print("CodeQL inventory failed; no complete count claimed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
