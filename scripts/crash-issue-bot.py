#!/usr/bin/env python3
"""V100: plan offline; scheduled apply reads only the Worker's latest 100 records.

Only safe core/kind/signature/counts leave this process. Never copy a report to GitHub.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

MARKER = "aow-crash-v100:"
LABEL = "crash-moi"
MAX_BYTES = 1024 * 1024
MAX_WRITES = 20


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def token(value):
    return value if isinstance(value, str) and re.fullmatch(r"[a-zA-Z0-9_.-]{1,60}", value) else "unknown"


def frames(report):
    text = str(report.get("detail", "")) + "\n" + "\n".join(report.get("log", []))
    result = []
    for line in text.splitlines():
        java = re.search(r"\bat\s+([\w.$]+)\([^)]*\)", line)
        native = re.search(r"#\d+\s+pc\s+[0-9a-fA-F]+\s+(?:\S*/)?([^/\s]+\.so)(?:\s+\(([^)]*)\))?", line)
        if java:
            result.append(java.group(1))
        elif native:
            # Remove addresses/offsets, retain module+symbol; never emit paths/stack text.
            symbol = re.sub(r"\+(?:0[xX][0-9a-fA-F]+|\d+)\b", "", native.group(2) or "")
            result.append(native.group(1) + ":" + symbol)
        if len(result) == 3:
            break
    return result


def group_reports(records):
    groups = {}
    ids = set()
    for row in records:
        if not isinstance(row, dict) or not isinstance(row.get("id"), str) or row["id"] in ids:
            raise ValueError("invalid or duplicate report ID")
        ids.add(row["id"])
        report = row.get("report")
        if not isinstance(report, dict) or not isinstance(report.get("log", []), list):
            raise ValueError("missing report detail")
        session = report.get("session") or {}
        if not isinstance(session, dict):
            raise ValueError("invalid session")
        core = token(session.get("core", ""))
        kind = token(report.get("kind", ""))
        signature = digest(json.dumps([core, kind, frames(report)], separators=(",", ":")))
        count = report.get("count", 1)
        if type(count) is not int or not 1 <= count <= 1000000:
            raise ValueError("invalid report count")
        group = groups.setdefault(signature, {"signature": signature, "core": core, "kind": kind, "seen": {}})
        group["seen"][digest(row["id"])[:32]] = count
    return groups


def read_state(issue):
    body = issue.get("body") or ""
    matches = re.findall(r"<!-- " + MARKER + r"(.*?) -->", body)
    if not matches:
        return None
    if len(matches) != 1:
        raise ValueError("ambiguous issue state")
    state = json.loads(matches[0])
    if not re.fullmatch(r"[a-f0-9]{64}", state.get("signature", "")) or type(state.get("count")) is not int or state["count"] < 1:
        raise ValueError("invalid issue state")
    if not isinstance(state.get("seen"), dict) or len(state["seen"]) > 1000:
        raise ValueError("invalid seen state")
    for key, value in state["seen"].items():
        if not re.fullmatch(r"[a-f0-9]{32}", key) or type(value) is not int or not 1 <= value <= 1000000:
            raise ValueError("invalid seen report")
    return state


def plan(records, issues, owners):
    for role in ("Sol", "Sonnet"):
        if not re.fullmatch(r"[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,38})", owners.get(role, "")):
            raise ValueError("configure GitHub assignee for " + role)
    existing = {}
    for issue in issues:
        if "pull_request" in issue:
            continue
        state = read_state(issue)
        if state:
            if state["signature"] in existing:
                raise ValueError("duplicate signature issues")
            existing[state["signature"]] = (issue, state)
    actions = []
    for signature, group in sorted(group_reports(records).items()):
        previous, state = existing.get(signature, (None, {"count": 0, "seen": {}}))
        delta = sum(max(0, value - state["seen"].get(key, 0)) for key, value in group["seen"].items())
        if previous and delta < 5:
            continue
        seen = {**state["seen"], **group["seen"]}
        # Keep current window first, then recent saved insertion order, capped at 1000.
        old_keys = [k for k in seen if k not in group["seen"]][-max(0, 1000 - len(group["seen"])):]
        seen = {k: seen[k] for k in old_keys + list(group["seen"])}
        saved = {"signature": signature, "count": state["count"] + delta, "seen": seen}
        role = "Sonnet" if any(x in group["core"].lower() for x in ("azahar", "kirikiri", "mkxp")) else "Sol"
        section = ("<!-- " + MARKER + json.dumps(saved, separators=(",", ":")) + " -->\n"
                   + f"Lõi: `{group['core']}` · Loại: `{group['kind']}` · Chữ ký: `{signature}`\n"
                   + f"Số lần quan sát đã thông báo: {saved['count']} (+{delta}). Phụ trách: {role}.\n"
                   + "Phạm vi: tối đa 100 bản ghi mới nhất Worker trả về; không suy toàn bộ lịch sử. "
                   + "Chữ ký băm lõi + loại + tối đa 3 frame đầu; không chép stack, game, ảnh, URL hoặc thiết bị.\n")
        old_body = previous.get("body") or "" if previous else ""
        if previous:
            begin = old_body.find("<!-- " + MARKER)
            end_marker = "<!-- /aow-crash-v100 -->"
            end = old_body.find(end_marker, begin)
            if end < 0:
                raise ValueError("missing end marker")
            body = old_body[:begin] + section + end_marker + old_body[end + len(end_marker):]
        else:
            body = section + "<!-- /aow-crash-v100 -->"
        if len(body.encode()) > 60000:
            raise ValueError("issue state too large")
        actions.append({"number": previous["number"] if previous else None,
                        "title": f"[Crash] {group['core']} / {group['kind']} / {signature[:12]}",
                        "body": body, "labels": [LABEL], "assignees": [owners[role]]})
    if len(actions) > MAX_WRITES:
        raise ValueError("mutation budget exceeded; no writes")
    return actions


class Client:
    def __init__(self, base, auth):
        self.base, self.auth = base, auth

    def call(self, path, method="GET", data=None):
        raw = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(self.base + path, data=raw, method=method,
                                        headers={"Authorization": "Bearer " + self.auth,
                                                 "Content-Type": "application/json", "User-Agent": "aow-crash-v100"})
        try:
            class NoRedirect(urllib.request.HTTPRedirectHandler):
                def redirect_request(self, *args, **kwargs):
                    raise ValueError("redirect blocked")
            with urllib.request.build_opener(NoRedirect).open(request, timeout=15) as response:
                content = response.read(MAX_BYTES + 1)
                if response.status == 204:
                    return {}  # GitHub GET /assignees/{login}: assignable has no JSON body.
        except urllib.error.HTTPError as error:
            raise ValueError("HTTP request failed: " + str(error.code)) from None
        except (OSError, urllib.error.URLError):
            raise ValueError("network request failed") from None
        except ValueError as error:
            # HTTP libraries may include header values in ValueError messages.
            reason = "redirect blocked" if str(error) == "redirect blocked" else "invalid HTTP request configuration"
            raise ValueError(reason) from None
        if len(content) > MAX_BYTES:
            raise ValueError("response exceeds budget")
        return json.loads(content)


def live(worker, github, repo, owners):
    # Validate mapping before reading private reports, not after creating an issue.
    plan([], [], owners)
    for owner in set(owners.values()):
        github.call(f"/repos/{repo}/assignees/{owner}")
    label = github.call(f"/repos/{repo}/labels/{LABEL}")
    if not isinstance(label, dict) or label.get("name") != LABEL:
        raise ValueError("configure crash-moi label before activation")
    issues = []
    for page in range(1, 6):
        rows = github.call(f"/repos/{repo}/issues?state=all&labels={LABEL}&per_page=100&page={page}")
        if not isinstance(rows, list):
            raise ValueError("invalid issue collection")
        issues.extend(rows)
        if len(rows) < 100:
            break
    else:
        raise ValueError("issue pagination budget exceeded; no writes")
    listing = worker.call("/reports")
    rows = listing.get("reports") if isinstance(listing, dict) else None
    if not isinstance(rows, list) or len(rows) > 100:
        raise ValueError("invalid Worker report window")
    records = []
    for row in rows:
        report_id = row.get("id", "")
        if not re.fullmatch(r"r:\d{13}:[a-f0-9-]{1,36}", report_id):
            raise ValueError("invalid Worker report ID")
        records.append({"id": report_id, "report": worker.call("/reports/" + urllib.parse.quote(report_id, safe=""))})
    actions = plan(records, issues, owners)  # All reads/validation finish before first mutation.
    for action in actions:
        number = action.pop("number")
        if number:
            # Preserve title, labels, assignee and closed/open state chosen by maintainers.
            github.call(f"/repos/{repo}/issues/{number}", "PATCH", {"body": action["body"]})
        else:
            github.call(f"/repos/{repo}/issues", "POST", action)
    return len(actions)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", help="offline JSON {records, issues, owners}; no network/writes")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    try:
        if args.fixture:
            if args.apply:
                raise ValueError("fixture cannot apply")
            data = json.load(open(args.fixture, encoding="utf-8"))
            print(json.dumps(plan(data["records"], data.get("issues", []), data["owners"]), ensure_ascii=False))
            return
        if not args.apply:
            raise ValueError("choose offline fixture or explicitly authorized apply")
        auth = os.environ.get("CRASH_ADMIN_TOKEN", "").strip()
        if any(not 0x20 <= ord(char) <= 0x7e for char in auth):
            raise ValueError("CRASH_ADMIN_TOKEN has invalid characters")
        gh_auth = os.environ.get("GITHUB_TOKEN", "")
        repo = os.environ.get("GITHUB_REPOSITORY", "")
        base = os.environ.get("CRASH_URL", "https://aowvn-monika-crash.aowvn-system.workers.dev")
        url = urllib.parse.urlsplit(base)
        if not auth or not gh_auth or not re.fullmatch(r"[\w.-]+/[\w.-]+", repo):
            raise ValueError("missing runtime credentials/repository")
        if url.scheme != "https" or not (url.hostname or "").endswith(".workers.dev") or url.username or url.password or url.query or url.fragment or url.path not in ("", "/"):
            raise ValueError("invalid Worker endpoint")
        count = live(Client(base.rstrip("/"), auth), Client("https://api.github.com", gh_auth), repo,
                     {"Sol": os.environ.get("CRASH_ASSIGNEE_SOL", ""), "Sonnet": os.environ.get("CRASH_ASSIGNEE_SONNET", "")})
        print("Issue mutations:", count)  # Never print reports, headers, credentials or signed URLs.
    except (ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        # Exact ValueError messages are controlled by this script; subclasses may contain private input.
        reason = str(error) if type(error) is ValueError else type(error).__name__
        print("BLOCKED: " + reason, file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
