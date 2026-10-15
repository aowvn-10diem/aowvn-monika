#!/usr/bin/env python3
"""Test offline cho changelog-pending.py (không cần mạng)."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("pending", Path(__file__).with_name("changelog-pending.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

RELEASES = [
    {"tag_name": "v0.8.3", "draft": False},
    {"tag_name": "v0.8.2", "draft": False},
    {"tag_name": "v0.8.4", "draft": True},      # draft: chưa phát hành
    {"tag_name": "symbols-kirikiri-16", "draft": False},
    {"tag_name": "v0.7.9", "draft": False},
]


class PendingTest(unittest.TestCase):
    def test_published_tags_skip_drafts_and_non_versions(self):
        self.assertEqual(m.published_tags(RELEASES), {"v0.8.3", "v0.8.2", "v0.7.9"})

    def test_tag_without_published_release_is_not_pending(self):
        # v0.8.5 có tag git nhưng không có Release publish: không nằm trong danh sách Releases
        tags = m.published_tags(RELEASES)
        self.assertNotIn("v0.8.5", m.pending(tags, ""))
        self.assertNotIn("v0.8.4", m.pending(tags, ""))

    def test_newest_first_floor_and_limit(self):
        tags = m.published_tags(RELEASES)
        self.assertEqual(m.pending(tags, ""), ["v0.8.3", "v0.8.2"])      # v0.7.9 dưới mốc 0.8.0
        self.assertEqual(m.pending(tags, "", floor="0.7.0"), ["v0.8.3", "v0.8.2", "v0.7.9"])
        self.assertEqual(m.pending(tags, "", limit=1), ["v0.8.3"])

    def test_done_marker_skips_version(self):
        text = "## v0.8.3\n<!-- bien-tap: v0.8.3 -->\n- x\n## v0.8.2\n- y\n"
        self.assertEqual(m.pending(m.published_tags(RELEASES), text), ["v0.8.2"])
        done_all = text + "<!--bien-tap:v0.8.2-->\n"
        self.assertEqual(m.pending(m.published_tags(RELEASES), done_all), [])

    def test_api_failure_exits_nonzero(self):
        def boom():
            raise OSError("403")
        m.fetch_releases = boom
        import sys
        old = sys.argv
        sys.argv = ["x", "--changelog", str(Path(__file__))]
        try:
            self.assertEqual(m.main(), 1)
        finally:
            sys.argv = old


if __name__ == "__main__":
    unittest.main()
