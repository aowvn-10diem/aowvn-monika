#!/usr/bin/env python3
"""V49 metadata only; optional reviewed alert-22 dismissal on explicit main dispatch."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request

REPOSITORY = "aowvn-10diem/aowvn-monika"
API = "https://api.github.com/repos/" + REPOSITORY
APK_SOURCE = "app/src/main/java/vn/aow/monika/apkinstall/ApkInspector.kt"
APK_SOURCE_SHA256 = "98bbe9890834d2ab9c0cbb0498fb15d1dbf883238ad73dec0467ab452141a05a"


def dismiss_reviewed_22(request, source_bytes):
    """Only the reviewed basename-flattening false positive; changed code fails closed."""
    if hashlib.sha256(source_bytes).hexdigest() != APK_SOURCE_SHA256:
        raise RuntimeError("Reviewed APK source changed; dismissal requires a new review")
    route = "/code-scanning/alerts/22"
    alert = request("GET", route)
    if alert.get("number") != 22 or (alert.get("rule") or {}).get("id") != "java/zipslip":
        raise RuntimeError("Alert does not match reviewed rule")
    location = (alert.get("most_recent_instance") or {}).get("location") or {}
    if location.get("path") != APK_SOURCE or location.get("start_line") != 104:
        raise RuntimeError("Alert location changed; dismissal requires a new review")
    if alert.get("state") in {"fixed", "dismissed"}:
        return False
    if alert.get("state") != "open":
        raise RuntimeError("Unexpected alert state")
    result = request("PATCH", route, {
        "state": "dismissed", "dismissed_reason": "false positive",
        "dismissed_comment": "V49: extractApks uses only entry.name.substringAfterLast('/') under a UUID private work directory. On Android/Linux backslash is an ordinary filename character. Parent/absolute ZIP paths cannot reach the output directory join. Regression ApkInspectorTest.apkEntriesCannotEscapePrivateWorkDirectory covers relative/absolute/nested/backslash names. Exact reviewed source SHA256 98bbe9890834d2ab9c0cbb0498fb15d1dbf883238ad73dec0467ab452141a05a.",
    })
    if result.get("state") != "dismissed":
        raise RuntimeError("GitHub did not confirm dismissal")
    return True


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
    parser.add_argument("--dismiss-reviewed-22", action="store_true")
    args = parser.parse_args()
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        parser.error("Unexpected repository")
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        parser.error("Missing workflow token")
    tracked = set(subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0"))
    def request(method, route, payload=None):
        request = urllib.request.Request(
            API + route, method=method,
            data=json.dumps(payload).encode() if payload is not None else None,
            headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                     "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "monika-v49"},
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    def get_page(page):
        return request("GET", f"/code-scanning/alerts?state=open&per_page=100&page={page}")
    try:
        alerts = collect(get_page, tracked)
        before_count = len(alerts)
        dismissed = False
        if args.dismiss_reviewed_22:
            # No write from a PR, branch or fork even if invoked by hand.
            if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch" or os.environ.get("GITHUB_REF") != "refs/heads/main":
                raise RuntimeError("Dismissal is allowed only by explicit main workflow dispatch")
            dismissed = dismiss_reviewed_22(request, Path(APK_SOURCE).read_bytes())
            alerts = collect(get_page, tracked)
        data = {"repository": REPOSITORY, "commit": subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip(),
                "open_count": len(alerts), "before_count": before_count, "dismissed_22": dismissed, "alerts": alerts}
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "inventory.json").write_text(json.dumps(data, indent=2) + "\n")
        print(f"Read {len(alerts)} open alerts; dismissed_22={dismissed}")
    except urllib.error.HTTPError as error:
        print(f"CodeQL inventory unavailable: HTTP {error.code}")
        return 1
    except (urllib.error.URLError, json.JSONDecodeError, RuntimeError):
        print("CodeQL inventory failed; no complete count claimed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
