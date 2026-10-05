#!/usr/bin/env python3
"""Test bản tin GitHub bằng fixture JSON; không gọi mạng."""
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path
import unittest


SCRIPT_DIR = Path(__file__).resolve().parent
FIXTURE = SCRIPT_DIR / "testdata/pm-digest-sample.json"
SPEC = importlib.util.spec_from_file_location("pm_digest", SCRIPT_DIR / "pm-digest.py")
pm_digest = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pm_digest)


class FixtureAPI:
    def __init__(self, responses):
        self.responses = responses
        self.calls = []

    def list_all(self, route, field=None):
        self.calls.append(route)
        value = self.responses.get(route, [])
        if isinstance(value, dict) and "_error" in value:
            raise pm_digest.GitHubAPIError(value["_error"])
        if field:
            return value.get(field, []) if isinstance(value, dict) else []
        return value if isinstance(value, list) else []

    def list_recent(self, route, cutoff, time_field, field=None):
        self.calls.append(route)
        value = self.responses.get(route, [])
        page = value.get(field, []) if field and isinstance(value, dict) else value
        recent = []
        for item in page if isinstance(page, list) else []:
            at = pm_digest.parse_time(item.get(time_field))
            if at and at < cutoff:
                break
            recent.append(item)
        return recent

    def fetch_commit(self, sha):
        route = f"/commits/{sha}"
        self.calls.append(route)
        return self.responses.get(route, {})

    def latest_completed_workflow_run(self, workflow):
        value = self.responses.get("latest_sync", None)
        if isinstance(value, dict) and "_error" in value:
            raise pm_digest.GitHubAPIError(value["_error"])
        return value


class PagedGitHubAPI(pm_digest.GitHubAPI):
    def __init__(self, responses):
        super().__init__("owner/repo", "")
        self.responses = responses
        self.calls = []

    def _request_json(self, route):
        self.calls.append(route)
        return self.responses[route]


class DigestTests(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.now = datetime.fromisoformat(self.fixture["now"].replace("Z", "+00:00")).astimezone(timezone.utc)

    def digest(self):
        self.api = FixtureAPI(self.fixture["responses"])
        return pm_digest.build_digest(self.api, "owner/repo", self.now)

    def test_fixture_builds_digest_without_comment_bodies(self):
        digest = self.digest()
        self.assertEqual(digest["main"]["sha"], "a" * 40)
        self.assertEqual(digest["main"]["commits"][0]["sha"], "a" * 7)
        self.assertEqual(digest["open_prs"][0]["check_runs"], {"Build": "success", "Emulator": "in_progress"})
        comments = digest["open_prs"][0]["comments"]
        self.assertEqual([comment["type"] for comment in comments], ["khac", "luna_tien_duyet", "pm_sua"])
        self.assertEqual(comments[1]["sha"], "b123456")
        self.assertEqual(comments[1]["conclusion"], "Cần PM xem")
        self.assertEqual([pr["number"] for pr in digest["merged_24h"]], [40])
        self.assertEqual([branch["name"] for branch in digest["branches"]], ["luna/L10", "sonnet/V17", "sol/V35"])
        self.assertEqual(digest["branches"][0]["last_commit_at"], "2026-10-04T03:00:00Z")
        self.assertEqual([run["name"] for run in digest["failed_runs_24h"]], ["Build"])
        self.assertEqual(digest["code_scanning_open"], 2)
        self.assertIn("/actions/runs?branch=main&per_page=100&created=%3E%3D2026-10-03T04%3A00%3A00Z", self.api.calls)
        serialized = json.dumps(digest, ensure_ascii=False)
        self.assertNotIn("comment body must not be copied", serialized)
        self.assertNotIn("example@example.test", serialized)

    def test_closed_pr_pagination_stops_after_old_updated_at(self):
        cutoff = datetime.fromisoformat("2026-10-03T04:00:00+00:00")
        route = "/pulls?state=closed&sort=updated&direction=desc&per_page=100"
        next_page = "https://api.github.com/repos/owner/repo/pulls?page=2"
        api = PagedGitHubAPI({
            route: ([
                {"number": 40, "updated_at": "2026-10-04T03:30:00Z"},
                {"number": 39, "updated_at": "2026-10-03T03:59:59Z"},
            ], {"Link": f'<{next_page}>; rel="next"'}),
            next_page: ([{"number": 38, "updated_at": "2026-10-03T03:00:00Z"}], {}),
        })

        results = api.list_recent(route, cutoff, "updated_at")

        self.assertEqual([item["number"] for item in results], [40])
        self.assertEqual(api.calls, [route])

    def test_comment_categories(self):
        cases = (
            ({"body": "PM duyệt. Build xanh.", "created_at": "2026-10-04T03:00:00Z"}, "pm_duyet"),
            ({"body": "PM yêu cầu sửa: kiểm tra thêm.", "created_at": "2026-10-04T03:00:00Z"}, "pm_sua"),
            ({"body": "Luna tiền duyệt (commit b123456)\nKết luận: Đạt", "created_at": "2026-10-04T03:00:00Z"}, "luna_tien_duyet"),
            ({"body": "Trao đổi khác.", "created_at": "2026-10-04T03:00:00Z"}, "khac"),
        )
        for raw, expected in cases:
            with self.subTest(expected=expected):
                result = pm_digest.classify_comment(raw)
                self.assertEqual(result["type"], expected)
                self.assertNotIn("body", result)

    def test_missing_code_scanning_permission_is_null(self):
        route = "/code-scanning/alerts?state=open&per_page=100"
        self.fixture["responses"][route] = {"_error": 403}
        self.assertIsNone(self.digest()["code_scanning_open"])

    def test_old_failed_sync_remains_visible_until_success(self):
        self.fixture["responses"]["latest_sync"] = {
            "conclusion": "failure", "updated_at": "2026-10-01T01:00:00Z",
            "html_url": "https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37",
        }
        failed = self.digest()
        self.assertIn("CẢNH BÁO: sync-config", pm_digest.render_markdown(failed))
        self.assertEqual(failed["sync_config"]["conclusion"], "failure")
        self.fixture["responses"]["latest_sync"]["conclusion"] = "success"
        self.assertNotIn("CẢNH BÁO", pm_digest.render_markdown(self.digest()))

    def test_sync_fetch_never_paginates_history(self):
        route = "/actions/workflows/sync-config.yml/runs?branch=main&status=completed&per_page=1"
        api = PagedGitHubAPI({route: ({"workflow_runs": [{"conclusion": "failure"}]},
                                      {"Link": '<https://api.github.com/repos/owner/repo/actions/runs?page=2>; rel="next"'})})
        self.assertEqual(api.latest_completed_workflow_run("sync-config.yml")["conclusion"], "failure")
        self.assertEqual(api.calls, [route])

    def test_sync_permission_missing_is_unknown(self):
        self.fixture["responses"]["latest_sync"] = {"_error": 403}
        result = self.digest()
        self.assertIsNone(result["sync_config"]["conclusion"])
        self.assertNotIn("CẢNH BÁO", pm_digest.render_markdown(result))

    def test_markdown_never_exceeds_40_lines(self):
        digest = self.digest()
        digest["open_prs"] *= 30
        digest["merged_24h"] *= 30
        digest["branches"] *= 30
        digest["failed_runs_24h"] *= 30
        digest["releases"] *= 30
        digest["sync_config"] = {"conclusion": "failure", "url": "https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37"}
        rendered = pm_digest.render_markdown(digest)
        self.assertLessEqual(len(rendered.splitlines()), 40)
        self.assertIn("Open code scanning alerts", rendered)


if __name__ == "__main__":
    unittest.main()
