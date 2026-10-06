#!/usr/bin/env python3
"""Tạo bản tin chỉ đọc về trạng thái repository GitHub."""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path


API_ROOT = "https://api.github.com"
RED_CONCLUSIONS = {"failure", "timed_out", "action_required", "startup_failure", "stale"}
MAX_MARKDOWN_LINES = 40


class GitHubAPIError(RuntimeError):
    def __init__(self, status_code: int):
        super().__init__(f"GitHub API returned HTTP {status_code}.")
        self.status_code = status_code


class GitHubAPI:
    def __init__(self, repository: str, token: str):
        self.repository_path = f"/repos/{repository}"
        self.token = token

    def _url(self, route: str) -> str:
        if route.startswith("https://"):
            parsed = urllib.parse.urlsplit(route)
            if parsed.netloc != "api.github.com" or not parsed.path.startswith(self.repository_path + "/"):
                raise RuntimeError("GitHub returned a pagination URL outside this repository API.")
            return route
        suffix = route if route.startswith("/") else "/" + route
        return API_ROOT + self.repository_path + suffix

    def _request_json(self, route: str):
        request = urllib.request.Request(
            self._url(route),
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "User-Agent": "aowvn-monika-pm-digest",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response), response.headers
        except urllib.error.HTTPError as error:
            raise GitHubAPIError(error.code) from None
        except urllib.error.URLError:
            raise RuntimeError("GitHub API request failed.") from None
        except json.JSONDecodeError:
            raise RuntimeError("GitHub API returned invalid JSON.") from None

    @staticmethod
    def _next_link(link_header: str | None) -> str | None:
        if not link_header:
            return None
        for part in re.split(r",\s*(?=<)", link_header):
            match = re.match(r'<([^>]+)>\s*;\s*rel="?([^";]+)', part)
            if match and match.group(2) == "next":
                return match.group(1)
        return None

    def list_all(self, route: str, field: str | None = None) -> list:
        items = []
        next_route: str | None = route
        while next_route:
            data, headers = self._request_json(next_route)
            if isinstance(data, list):
                page = data
            elif isinstance(data, dict) and field:
                page = data.get(field, [])
            else:
                raise RuntimeError("GitHub API returned an unexpected list response.")
            if not isinstance(page, list):
                raise RuntimeError("GitHub API returned an unexpected list field.")
            items.extend(page)
            next_route = self._next_link(headers.get("Link"))
        return items

    def list_recent(self, route: str, cutoff: datetime, time_field: str, field: str | None = None) -> list:
        """Paginate a newest-first endpoint only until its records cross cutoff."""
        items = []
        next_route: str | None = route
        while next_route:
            data, headers = self._request_json(next_route)
            if isinstance(data, list):
                page = data
            elif isinstance(data, dict) and field:
                page = data.get(field, [])
            else:
                raise RuntimeError("GitHub API returned an unexpected list response.")
            if not isinstance(page, list):
                raise RuntimeError("GitHub API returned an unexpected list field.")
            past_cutoff = False
            for item in page:
                at = parse_time(item.get(time_field))
                if at and at < cutoff:
                    past_cutoff = True
                    break
                items.append(item)
            if past_cutoff:
                break
            next_route = self._next_link(headers.get("Link"))
        return items

    def fetch_commit(self, sha: str) -> dict:
        data, _ = self._request_json(f"/commits/{urllib.parse.quote(sha, safe='')}")
        if not isinstance(data, dict):
            raise RuntimeError("GitHub API returned an unexpected commit response.")
        return data

    def latest_completed_workflow_run(self, workflow: str) -> dict | None:
        # One record only: keep a failed sync visible even after the 24h window.
        route = f"/actions/workflows/{urllib.parse.quote(workflow, safe='')}/runs?branch=main&status=completed&per_page=1"
        data, _ = self._request_json(route)
        if not isinstance(data, dict) or not isinstance(data.get("workflow_runs"), list):
            raise RuntimeError("GitHub API returned an unexpected workflow response.")
        runs = data["workflow_runs"]
        return runs[0] if runs else None


def parse_time(value: str | None) -> datetime | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def timestamp(value: str | None) -> datetime:
    return parse_time(value) or datetime.min.replace(tzinfo=timezone.utc)


def classify_comment(comment: dict) -> dict:
    body = comment.get("body") if isinstance(comment.get("body"), str) else ""
    text = body.lstrip()
    result = {"type": "khac"}
    if text.startswith("PM duyệt"):
        result["type"] = "pm_duyet"
    elif text.startswith("PM yêu cầu sửa"):
        result["type"] = "pm_sua"
    elif text.startswith("Luna tiền duyệt") or text.startswith("Luna review L07"):
        result["type"] = "luna_tien_duyet"
        # Read the review header only: later prose may mention an older commit.
        header = text.splitlines()[0]
        label = "commit" if text.startswith("Luna tiền duyệt") else "head"
        sha = re.search(rf"\b{label}\s+`?([0-9a-f]{{7,40}})\b(?![0-9a-f])", header, re.IGNORECASE)
        verdict = re.search(r"^Kết luận:\s*(Đạt|Cần sửa|Cần PM xem)", body, re.MULTILINE | re.IGNORECASE)
        if sha:
            result["sha"] = sha.group(1).lower()
        if verdict:
            result["conclusion"] = verdict.group(1)
    comment_time = comment.get("submitted_at") or comment.get("created_at") or comment.get("updated_at")
    if comment_time:
        result["at"] = comment_time
    return result


def recent_comments(api: GitHubAPI, number: int) -> list[dict]:
    sources = (
        f"/issues/{number}/comments?per_page=100",
        f"/pulls/{number}/comments?per_page=100",
        f"/pulls/{number}/reviews?per_page=100",
    )
    found = []
    for route in sources:
        for comment in api.list_all(route):
            body = comment.get("body")
            if not isinstance(body, str) or not body.strip():
                continue
            item = classify_comment(comment)
            item["_sort_time"] = timestamp(item.get("at"))
            found.append(item)
    found.sort(key=lambda item: item["_sort_time"], reverse=True)
    return [{key: value for key, value in item.items() if key != "_sort_time"} for item in found[:3]]


def check_results(api: GitHubAPI, sha: str | None) -> dict[str, str]:
    if not sha:
        return {}
    runs = api.list_all(f"/commits/{urllib.parse.quote(sha, safe='')}/check-runs?per_page=100", "check_runs")
    results = {}
    for run in runs:
        name = str(run.get("name") or "unnamed")
        key = name
        suffix = 2
        while key in results:
            key = f"{name}#{suffix}"
            suffix += 1
        results[key] = str(run.get("conclusion") or run.get("status") or "unknown")
    return results


def short_title(value: str | None) -> str:
    if not isinstance(value, str) or not value:
        return ""
    return value.splitlines()[0].strip()


def build_digest(api: GitHubAPI, repository: str, now: datetime | None = None) -> dict:
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    cutoff = now - timedelta(hours=24)

    commits = api.list_all("/commits?sha=main&per_page=10")[:10]
    main_commits = []
    for commit in commits:
        details = commit.get("commit", {})
        committer = details.get("committer") or details.get("author") or {}
        main_commits.append({
            "sha": str(commit.get("sha", ""))[:7],
            "title": short_title(details.get("message")),
            "at": committer.get("date"),
        })

    open_prs = []
    for pr in api.list_all("/pulls?state=open&per_page=100"):
        head = pr.get("head") or {}
        sha = head.get("sha")
        number = pr.get("number")
        open_prs.append({
            "number": number,
            "title": pr.get("title", ""),
            "branch": head.get("ref"),
            "head_sha": sha,
            "mergeable_state": pr.get("mergeable_state"),
            "check_runs": check_results(api, sha),
            "comments": recent_comments(api, int(number)),
        })

    merged = []
    closed_prs = api.list_recent(
        "/pulls?state=closed&sort=updated&direction=desc&per_page=100",
        cutoff,
        "updated_at",
    )
    for pr in closed_prs:
        merged_at = pr.get("merged_at")
        parsed = parse_time(merged_at)
        if parsed and cutoff <= parsed <= now:
            merged.append({
                "number": pr.get("number"),
                "title": pr.get("title", ""),
                "merged_at": merged_at,
                "merge_commit_sha": pr.get("merge_commit_sha"),
            })
    merged.sort(key=lambda pr: timestamp(pr.get("merged_at")), reverse=True)

    branches = []
    for branch in api.list_all("/branches?per_page=100"):
        name = branch.get("name", "")
        if not name.startswith(("sol/", "sonnet/", "luna/")):
            continue
        commit = branch.get("commit") or {}
        sha = commit.get("sha")
        details = {}
        if sha:
            details = api.fetch_commit(sha).get("commit") or {}
        committer = details.get("committer") or details.get("author") or {}
        branches.append({
            "name": name,
            "commit_sha": sha,
            "last_commit_at": committer.get("date"),
        })
    branches.sort(key=lambda branch: timestamp(branch.get("last_commit_at")), reverse=True)

    cutoff_text = cutoff.replace(microsecond=0).isoformat().replace("+00:00", "Z")
    runs_query = urllib.parse.urlencode({
        "branch": "main",
        "per_page": 100,
        "created": f">={cutoff_text}",
    })
    runs = api.list_all(f"/actions/runs?{runs_query}", "workflow_runs")
    failed_runs = []
    for run in runs:
        created = parse_time(run.get("created_at"))
        conclusion = run.get("conclusion")
        if (
            run.get("head_branch") == "main"
            and created
            and cutoff <= created <= now
            and conclusion in RED_CONCLUSIONS
        ):
            failed_runs.append({"name": run.get("name", ""), "url": run.get("html_url", "")})
    failed_runs.sort(key=lambda run: run.get("name", ""))

    releases = []
    for release in api.list_all("/releases?per_page=10")[:10]:
        releases.append({
            "tag": release.get("tag_name", ""),
            "prerelease": bool(release.get("prerelease")),
            "draft": bool(release.get("draft")),
        })

    try:
        code_scanning_open = len(api.list_all("/code-scanning/alerts?state=open&per_page=100"))
    except GitHubAPIError as error:
        if error.status_code not in (403, 404):
            raise
        code_scanning_open = None

    try:
        sync_run = api.latest_completed_workflow_run("sync-config.yml")
    except GitHubAPIError as error:
        if error.status_code not in (403, 404):
            raise
        sync_run = None
    sync_config = {
        "conclusion": sync_run.get("conclusion") if sync_run else None,
        "url": sync_run.get("html_url") if sync_run else None,
        "at": sync_run.get("updated_at") if sync_run else None,
    }

    return {
        "generated_at": now.isoformat().replace("+00:00", "Z"),
        "repository": repository,
        "main": {
            "sha": commits[0].get("sha") if commits else None,
            "commits": main_commits,
        },
        "open_prs": open_prs,
        "merged_24h": merged,
        "branches": branches,
        "failed_runs_24h": failed_runs,
        "releases": releases,
        "code_scanning_open": code_scanning_open,
        "sync_config": sync_config,
    }


def one_line(value: object, limit: int = 120) -> str:
    text = " ".join(str(value or "").splitlines()).replace("|", "\\|").replace("`", "'").strip()
    if len(text) > limit:
        return text[: limit - 1] + "…"
    return text


def render_markdown(digest: dict) -> str:
    main = digest.get("main") or {}
    commits = main.get("commits") or []
    open_prs = digest.get("open_prs") or []
    merged = digest.get("merged_24h") or []
    branches = digest.get("branches") or []
    failed_runs = digest.get("failed_runs_24h") or []
    releases = digest.get("releases") or []

    lines = [
        f"# Repo digest: {one_line(digest.get('repository'))}",
        f"Generated: {one_line(digest.get('generated_at'))}",
    ]
    sync_config = digest.get("sync_config") or {}
    if sync_config.get("conclusion") in RED_CONCLUSIONS:
        lines.append(f"- CẢNH BÁO: sync-config thất bại; config từ xa có thể cũ. {one_line(sync_config.get('url'), 120)}")
    lines.append(f"## Main `{one_line(main.get('sha') or 'unknown', 40)}`")
    for commit in commits[:3]:
        lines.append(f"- `{one_line(commit.get('sha'), 7)}` {one_line(commit.get('title'))} ({one_line(commit.get('at'))})")
    if len(commits) > 3:
        lines.append(f"- … {len(commits) - 3} commits in digest.json")

    lines.append(f"## Open PRs ({len(open_prs)})")
    for pr in open_prs[:5]:
        checks = ", ".join(f"{one_line(name, 24)}={one_line(result, 16)}" for name, result in pr.get("check_runs", {}).items())
        comments = ", ".join(
            one_line(comment.get("type", "khac"), 24)
            + (f":{one_line(comment.get('conclusion'), 20)}" if comment.get("conclusion") else "")
            + (f"({one_line(comment.get('sha'), 7)})" if comment.get("sha") else "")
            for comment in pr.get("comments", [])
        )
        suffix = "; ".join(part for part in (f"checks {checks}" if checks else "", f"comments {comments}" if comments else "") if part)
        line = f"- #{pr.get('number')} {one_line(pr.get('title'), 64)} — {one_line(pr.get('branch'), 32)} @{one_line(str(pr.get('head_sha') or '')[:7], 7)}; mergeable={one_line(pr.get('mergeable_state') or 'unknown', 16)}"
        if suffix:
            line += f"; {one_line(suffix, 120)}"
        lines.append(one_line(line, 220))
    if len(open_prs) > 5:
        lines.append(f"- … {len(open_prs) - 5} more in digest.json")

    lines.append(f"## Merged in 24h ({len(merged)})")
    for pr in merged[:3]:
        lines.append(f"- #{pr.get('number')} {one_line(pr.get('title'), 72)} ({one_line(pr.get('merged_at'))})")
    if len(merged) > 3:
        lines.append(f"- … {len(merged) - 3} more in digest.json")

    lines.append(f"## Branches sol/*, sonnet/* and luna/* ({len(branches)})")
    for branch in branches[:4]:
        lines.append(f"- {one_line(branch.get('name'), 48)} @{one_line(str(branch.get('commit_sha') or '')[:7], 7)} ({one_line(branch.get('last_commit_at'))})")
    if len(branches) > 4:
        lines.append(f"- … {len(branches) - 4} more in digest.json")

    lines.append(f"## Failed workflows on main in 24h ({len(failed_runs)})")
    for run in failed_runs[:4]:
        lines.append(f"- {one_line(run.get('name'), 72)}: {one_line(run.get('url'), 120)}")
    if len(failed_runs) > 4:
        lines.append(f"- … {len(failed_runs) - 4} more in digest.json")

    lines.append(f"## Releases ({len(releases)} latest)")
    for release in releases[:4]:
        flags = ", ".join(flag for flag, enabled in (("prerelease", release.get("prerelease")), ("draft", release.get("draft"))) if enabled)
        lines.append(f"- {one_line(release.get('tag'), 48)}" + (f" ({flags})" if flags else ""))
    if len(releases) > 4:
        lines.append(f"- … {len(releases) - 4} more in digest.json")

    count = digest.get("code_scanning_open")
    lines.append("## Open code scanning alerts")
    lines.append("- Không có quyền hoặc tính năng chưa bật" if count is None else f"- {count}")

    if len(lines) > MAX_MARKDOWN_LINES:
        lines = lines[: MAX_MARKDOWN_LINES - 1] + ["… Xem digest.json để biết phần còn lại."]
    return "\n".join(lines) + "\n"


def valid_repository(value: str) -> bool:
    return bool(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", value))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Tạo bản tin trạng thái repository GitHub (chỉ đọc).")
    parser.add_argument("--out", required=True, type=Path, help="thư mục ghi digest.json và digest.md")
    args = parser.parse_args(argv)
    token = os.environ.get("GITHUB_TOKEN", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    if not token:
        parser.error("GITHUB_TOKEN is required")
    if not valid_repository(repository):
        parser.error("GITHUB_REPOSITORY must be owner/repository")

    try:
        digest = build_digest(GitHubAPI(repository, token), repository)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "digest.json").write_text(json.dumps(digest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (args.out / "digest.md").write_text(render_markdown(digest), encoding="utf-8")
    except GitHubAPIError as error:
        print(f"GitHub API error (HTTP {error.status_code}).", file=sys.stderr)
        return 1
    except OSError:
        print("Could not write digest files.", file=sys.stderr)
        return 1
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        return 1
    print("Digest files written.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
