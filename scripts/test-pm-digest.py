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

    def list_all(self, route, field=None):
        value = self.responses.get(route, [])
        if isinstance(value, dict) and "_error" in value:
            raise pm_digest.GitHubAPIError(value["_error"])
        if field:
            return value.get(field, []) if isinstance(value, dict) else []
        return value if isinstance(value, list) else []


class DigestTests(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.now = datetime.fromisoformat(self.fixture["now"].replace("Z", "+00:00")).astimezone(timezone.utc)

    def digest(self):
        return pm_digest.build_digest(FixtureAPI(self.fixture["responses"]), "owner/repo", self.now)

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
        self.assertEqual([branch["name"] for branch in digest["branches"]], ["luna/L10", "sol/V35"])
        self.assertEqual([run["name"] for run in digest["failed_runs_24h"]], ["Build"])
        self.assertEqual(digest["code_scanning_open"], 2)
        serialized = json.dumps(digest, ensure_ascii=False)
        self.assertNotIn("comment body must not be copied", serialized)
        self.assertNotIn("example@example.test", serialized)

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

    def test_markdown_never_exceeds_40_lines(self):
        digest = self.digest()
        digest["open_prs"] *= 30
        digest["merged_24h"] *= 30
        digest["branches"] *= 30
        digest["failed_runs_24h"] *= 30
        digest["releases"] *= 30
        rendered = pm_digest.render_markdown(digest)
        self.assertLessEqual(len(rendered.splitlines()), 40)
        self.assertIn("Open code scanning alerts", rendered)


if __name__ == "__main__":
    unittest.main()
