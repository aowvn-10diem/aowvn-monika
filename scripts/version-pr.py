#!/usr/bin/env python3
"""V102: produce a version/CHANGELOG PR plan; never merge, tag or release.

--fixture is entirely offline. --apply is for a future authorized operator only.
"""
import argparse
import base64
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BUILD = "app/build.gradle.kts"
CHANGELOG = "docs/CHANGELOG.md"


def semver(value):
    if not isinstance(value, str) or not re.fullmatch(r"\d+\.\d+\.\d+", value):
        raise ValueError("invalid version")
    return tuple(map(int, value.split(".")))


def title_text(value):
    text = re.sub(r"^\[[^\]]+\]\s*", "", value)
    text = re.sub(r"https?://\S+", "(liên kết bỏ qua)", text)
    text = " ".join(text.split())[:200]
    for char in "\\`[]<>*_":
        text = text.replace(char, "\\" + char)
    if not text:
        raise ValueError("empty PR title")
    return text


def make_plan(snapshot, requested=None):
    repo, head = snapshot["repo"], snapshot["head"]
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repo) or not re.fullmatch(r"[a-f0-9]{40}", head):
        raise ValueError("invalid repository/head")
    build, changelog = snapshot["build"], snapshot["changelog"]
    codes = list(re.finditer(r"(?m)^([ \t]*versionCode[ \t]*=[ \t]*)(\d+)[ \t]*$", build))
    names = list(re.finditer(r'(?m)^([ \t]*versionName[ \t]*=[ \t]*)"([^"]+)"[ \t]*$', build))
    if len(codes) != 1 or len(names) != 1:
        raise ValueError("version fields must be unique")
    old = semver(names[0][2]); version = requested or f"{old[0]}.{old[1]}.{old[2]+1}"
    if semver(version) <= old or int(codes[0][2]) >= 2100000000:
        raise ValueError("version must advance within Android limit")
    if version in snapshot.get("pending_versions", []):
        raise ValueError("an existing version PR already covers target")
    if re.search(r"(?m)^## .*\bv?" + re.escape(version) + r"\b", changelog):
        raise ValueError("CHANGELOG already contains target")
    date = snapshot["date"]
    datetime.date.fromisoformat(date)
    tag = snapshot["previous_tag"]
    if tag != "v" + names[0][2]:
        raise ValueError("previous tag must match current source version")
    bullets, seen = [], set()
    for pr in sorted(snapshot["merged_prs"], key=lambda p: p["number"]):
        sha = pr.get("merge_commit_sha", "")
        if pr.get("on_main") is not True or pr.get("after_tag") is not True or not re.fullmatch(r"[a-f0-9]{40}", sha):
            raise ValueError("PR ancestry/range was not verified")
        if type(pr["number"]) is not int or pr["number"] <= 0 or pr["number"] in seen:
            raise ValueError("invalid/duplicate PR number")
        seen.add(pr["number"])
        bullets.append(f"- {title_text(pr['title'])} ([#{pr['number']}](https://github.com/{repo}/pull/{pr['number']}), [{sha[:7]}](https://github.com/{repo}/commit/{sha}))")
    if not bullets:
        raise ValueError("no merged changes since tag")
    build = re.sub(r'(?m)^([ \t]*versionName[ \t]*=[ \t]*)"([^"]+)"[ \t]*$', lambda m: m[1] + '"' + version + '"', build)
    build = re.sub(r"(?m)^([ \t]*versionCode[ \t]*=[ \t]*)(\d+)[ \t]*$", lambda m: m[1] + str(int(codes[0][2])+1), build)
    at = re.search(r"(?m)^## ", changelog)
    if not at:
        raise ValueError("missing CHANGELOG insertion point")
    section = f"## v{version} (Nháp bản thử)\n\n> Đối chiếu PR đã gộp sau `{tag}` tới main `{head}` ngày {date}; tiêu đề cần reviewer rà, chưa phát hành/chưa thử máy thật.\n\n" + "\n".join(bullets) + "\n\n"
    changelog = changelog[:at.start()] + section + changelog[at.start():]
    return {"base": head, "version": version, "version_code": int(codes[0][2])+1,
            "branch": f"bot/version-{version}-{head[:12]}", "title": f"[bot-version] Nháp v{version}: version + CHANGELOG từ PR đã gộp",
            "body": f"Chỉ {BUILD} + {CHANGELOG}, từ tag {tag} tới main {head}. Các commit có kiểm ancestry/range. Nháp, cần CI + Luna3 → Ultra exact head + PM merge. Không tag/release/sign/publish.",
            "files": {BUILD: build, CHANGELOG: changelog}, "draft": True}


class Missing(Exception):
    pass


class Github:
    def __init__(self, repo, auth=""):
        if not re.fullmatch(r"[\w.-]+/[\w.-]+", repo): raise ValueError("invalid repository")
        self.repo, self.auth, self.calls = repo, auth, 0

    def call(self, path, method="GET", data=None):
        self.calls += 1
        if self.calls > 180: raise ValueError("API budget exceeded")
        headers = {"User-Agent": "aow-version-v102", "Content-Type": "application/json"}
        if self.auth: headers["Authorization"] = "Bearer " + self.auth
        request = urllib.request.Request("https://api.github.com/repos/" + self.repo + path,
                                        data=None if data is None else json.dumps(data).encode(), method=method, headers=headers)
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs): raise ValueError("redirect blocked")
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
                raw = response.read(2*1024*1024+1)
        except urllib.error.HTTPError as error:
            if error.code == 404 and method == "GET": raise Missing() from None
            raise ValueError("GitHub request failed") from None
        except OSError: raise ValueError("GitHub unavailable") from None
        if len(raw) > 2*1024*1024: raise ValueError("response exceeds budget")
        return json.loads(raw)


def check_environment(github, ref):
    # Scheduler enforces this Environment policy before granting its scoped secret.
    if ref != "refs/heads/main": raise ValueError("untrusted dispatch ref")
    env = github.call("/environments/version-pr-main")
    policy = env.get("deployment_branch_policy") or {}
    rules = env.get("protection_rules") or []
    review = [r for r in rules if r.get("type") == "required_reviewers"]
    if policy.get("custom_branch_policies") is not True or policy.get("protected_branches") is not False or len(review) != 1:
        raise ValueError("missing protected Environment")
    if not review[0].get("reviewers") or review[0].get("prevent_self_review") is not True:
        raise ValueError("independent review required")
    branches = github.call("/environments/version-pr-main/deployment-branch-policies?per_page=100")
    policies = branches.get("branch_policies", [])
    if branches.get("total_count") != 1 or len(policies) != 1 or policies[0].get("name") != "main" or policies[0].get("type") != "branch":
        raise ValueError("Environment must allow only main branch")


def snapshot(github):
    main = github.call("/git/ref/heads/main")["object"]["sha"]
    def content(path):
        item = github.call("/contents/" + path + "?ref=" + main)
        if item.get("encoding") != "base64": raise ValueError("unexpected file encoding")
        return base64.b64decode(item["content"]).decode("utf-8")
    build, log = content(BUILD), content(CHANGELOG)
    versions = re.findall(r'(?m)^[ \t]*versionName[ \t]*=[ \t]*"([^"]+)"[ \t]*$', build)
    if len(versions) != 1: raise ValueError("ambiguous version")
    tag_name = "v" + versions[0]
    obj = github.call("/git/ref/tags/" + tag_name)["object"]
    for _ in range(3):
        if obj["type"] == "commit": break
        if obj["type"] != "tag": raise ValueError("invalid tag target")
        obj = github.call("/git/tags/" + obj["sha"])["object"]
    if obj["type"] != "commit": raise ValueError("tag peeling limit")
    tag = obj["sha"]; tag_date = github.call("/commits/" + tag)["commit"]["committer"]["date"]
    main_date = github.call("/commits/" + main)["commit"]["committer"]["date"][:10]
    prs = []
    for page in range(1, 6):
        batch = github.call(f"/pulls?state=all&sort=created&direction=desc&per_page=100&page={page}")
        if not isinstance(batch, list): raise ValueError("invalid PR page")
        prs.extend(batch)
        if len(batch) < 100: break
    else: raise ValueError("PR history exceeds 500; no writes")
    if len({pr["number"] for pr in prs}) != len(prs):
        raise ValueError("PR pages changed; regenerate")
    pending, merged = [], []
    for pr in prs:
        if pr["state"] == "open":
            pending.extend(re.findall(r"\b\d+\.\d+\.\d+\b", pr["title"]))
        sha = pr.get("merge_commit_sha")
        if not pr.get("merged_at") or pr["merged_at"] <= tag_date: continue
        if len(merged) >= 40: raise ValueError("merged-PR budget exceeded")
        first = github.call("/compare/" + tag + "..." + sha)
        second = github.call("/compare/" + sha + "..." + main)
        if first["status"] != "ahead" or second["status"] not in ("ahead", "identical"): continue
        merged.append({"number": pr["number"], "title": pr["title"], "merge_commit_sha": sha, "on_main": True, "after_tag": True})
    return {"repo": github.repo, "head": main, "build": build, "changelog": log, "previous_tag": tag_name,
            "date": main_date, "merged_prs": merged, "pending_versions": pending}


def apply(github, plan):
    main = github.call("/git/ref/heads/main")["object"]["sha"]
    if main != plan["base"]: raise ValueError("main changed; regenerate")
    commit = github.call("/git/commits/" + main)
    tree = github.call("/git/trees", "POST", {"base_tree": commit["tree"]["sha"],
        "tree": [{"path": path, "mode": "100644", "type": "blob", "content": text} for path, text in plan["files"].items()]})
    branch = plan["branch"]
    try:
        existing = github.call("/git/ref/heads/" + branch)
    except Missing:
        existing = None
    if existing:
        head = existing["object"]["sha"]; got = github.call("/git/commits/" + head)
        if got["tree"]["sha"] != tree["sha"] or [p["sha"] for p in got["parents"]] != [main]:
            raise ValueError("existing branch differs; never overwrite")
    else:
        new = github.call("/git/commits", "POST", {"message": plan["title"], "tree": tree["sha"], "parents": [main]})
        head = new["sha"]; got = github.call("/git/commits/" + head)
        if got["tree"]["sha"] != tree["sha"] or [p["sha"] for p in got["parents"]] != [main]:
            raise ValueError("created commit verification failed")
        if github.call("/git/ref/heads/main")["object"]["sha"] != main: raise ValueError("main changed before branch")
        github.call("/git/refs", "POST", {"ref": "refs/heads/" + branch, "sha": head})
    prs = github.call("/pulls?state=all&head=" + urllib.parse.quote(github.repo.split('/')[0] + ":" + branch, safe="") + "&per_page=100")
    if prs: return prs[0]["number"]  # Includes closed PR: do not reopen or create a duplicate.
    created = github.call("/pulls", "POST", {"head": branch, "base": "main", "title": plan["title"], "body": plan["body"], "draft": True})
    return created["number"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture")
    parser.add_argument("--out", default="version-pr-plan.json")
    parser.add_argument("--next-version")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--check-environment", action="store_true")
    args = parser.parse_args()
    try:
        if args.check_environment:
            if args.fixture or args.apply: raise ValueError("invalid guard mode")
            check_environment(Github(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", "")), os.environ.get("GITHUB_REF", ""))
            print("Protected main-only Environment verified; no mutations")
            return
        if args.fixture and args.apply: raise ValueError("offline fixture cannot apply")
        github = None if args.fixture else Github(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        data = json.loads(Path(args.fixture).read_text()) if args.fixture else snapshot(github)
        plan = make_plan(data, args.next_version)
        Path(args.out).write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")
        if args.apply:
            if not github.auth: raise ValueError("missing authorized token")
            print("Draft PR:", apply(github, plan))
        else: print("Dry-run plan written; no branch/PR/tag/release mutation")
    except (ValueError, KeyError, TypeError, Missing, OSError):
        print("BLOCKED: invalid/incomplete range, pending version PR, API or configuration", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__": main()
