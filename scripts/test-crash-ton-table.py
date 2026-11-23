#!/usr/bin/env python3
"""Test offline cho crash-ton-table.py (không cần mạng, không đọc Worker)."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "ton_table", Path(__file__).with_name("crash-ton-table.py")
)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

ISSUES = [
    {
        "number": 306,
        "title": "[Crash] melondsds / killed / bb7c57376b7b",
        "body": "Số lần quan sát đã thông báo: 405 (+160).",
        "createdAt": "2026-10-20T02:04:36Z",
        "updatedAt": "2026-11-08T01:00:00Z",
        "comments": 0,
    },
    {
        "number": 316,
        "title": "[Crash] ppsspp / native / 3f181942717a",
        "body": "Số lần quan sát đã thông báo: 114 (+5).",
        "createdAt": "2026-10-20T02:04:36Z",
        "updatedAt": "2026-10-20T02:04:36Z",
        "comments": 0,
    },
    {
        "number": 305,
        "title": "[Crash] unknown / op-fail / ba1937dab151",
        "body": "không có marker",
        "createdAt": "2026-10-20T02:04:36Z",
        "updatedAt": "2026-11-08T01:00:00Z",
        "comments": 1,
    },
    {
        "number": 535,
        "title": "[Crash] melonds / anr / 4c2c04753f22",
        "body": "Số lần quan sát đã thông báo: 1 (+1).",
        "created_at": "2026-11-08T00:00:00Z",
        "updated_at": "2026-11-08T00:00:00Z",
    },
]


class ParseTest(unittest.TestCase):
    def test_title_and_count(self):
        row = m.parse_issue(ISSUES[0])
        self.assertEqual(row["core"], "melondsds")
        self.assertEqual(row["kind"], "killed")
        self.assertEqual(row["short"], "bb7c57376b7b")
        self.assertEqual(row["count"], 405)
        self.assertEqual(row["created"], "2026-10-20")

    def test_missing_count_is_blank_not_zero(self):
        row = m.parse_issue(ISSUES[2])
        self.assertIsNone(row["count"])

    def test_snake_case_fields_and_missing_comments(self):
        row = m.parse_issue(ISSUES[3])
        self.assertEqual(row["created"], "2026-11-08")
        self.assertEqual(row["comments"], 0)

    def test_bad_title_falls_back_to_unknown(self):
        row = m.parse_issue({"number": 1, "title": "[Crash] hỏng"})
        self.assertEqual((row["core"], row["kind"]), ("unknown", "unknown"))

    def test_comments_as_list_from_gh_cli(self):
        row = m.parse_issue({
            "number": 2,
            "title": "[Crash] mgba / anr / aaaa",
            "comments": [{"body": "x"}],
        })
        self.assertEqual(row["comments"], 1)


class GroupTest(unittest.TestCase):
    def test_type_to_group(self):
        self.assertEqual(m.group_of("killed"), "A")
        self.assertEqual(m.group_of("anr"), "A")
        self.assertEqual(m.group_of("native"), "B")
        self.assertEqual(m.group_of("core-error"), "C")
        self.assertEqual(m.group_of("op-fail"), "D")
        self.assertEqual(m.group_of("user"), "E")
        self.assertEqual(m.group_of("handled"), "F")
        self.assertEqual(m.group_of("java"), "G")
        self.assertEqual(m.group_of("resource"), "H")
        self.assertEqual(m.group_of("unknown"), "H")

    def test_session_by_core(self):
        self.assertEqual(m.session_of("desmume"), "Lỗi 1")
        self.assertEqual(m.session_of("mgba"), "Lỗi 2")
        self.assertEqual(m.session_of("unknown"), "Lỗi 2")
        self.assertEqual(m.session_of("citra"), "Lỗi 3")
        self.assertEqual(m.session_of("gambatte"), "Lỗi 4")


class BuildTest(unittest.TestCase):
    def test_summary_counts_and_recurrence(self):
        text = m.build(ISSUES, "2026-11-03")
        self.assertIn("**4 issue**", text)
        # 405 + 114 + 1, issue #305 không có số nên không cộng
        self.assertIn("**520 lượt**", text)
        row306 = next(line for line in text.splitlines() if "[#306]" in line)
        self.assertIn("| có |", row306)
        row316 = next(line for line in text.splitlines() if "[#316]" in line)
        self.assertIn("| chưa |", row316)
        # có bình luận thì không kết luận tái diễn
        row305 = next(line for line in text.splitlines() if "[#305]" in line)
        self.assertIn("| chưa |", row305)

    def test_dict_input_via_main_shape(self):
        text = m.build({"issues": ISSUES}["issues"], "2026-11-03")
        self.assertIn("## Giới hạn bằng chứng", text)

    def test_pipe_is_escaped(self):
        text = m.build(
            [{"number": 9, "title": "[Crash] a|b / op-fail / c", "body": ""}],
            "2026-11-03",
        )
        row = next(line for line in text.splitlines() if "[#9]" in line)
        self.assertIn("`a\\|b`", row)


class InsightTest(unittest.TestCase):
    TABLE = "\n".join([
        "| chữ ký | lõi | loại | số lượt | bản app | chứng thư | đời máy | màn | giả thuyết |",
        "|---|---|---|---|---|---|---|---|---|",
        "| 363ea01f | unknown | lowmem | 424 | 0.8.10 | c46902e97029 | — | app:chính | máy hết RAM |",
        "| d529a7fd | melondsds | killed | 75 | 0.8.9 | 1e08a903aef9 | — | engine:libretro:melondsds | hệ thống tắt |",
        "| — | — | op-fail | 10 | 0.8.7 | — | — | op:tai-feed | tải feed lỗi |",
    ])

    def test_parse_skips_header_and_keeps_dash_as_empty(self):
        rows = m.parse_insight(self.TABLE)
        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0]["core"], "unknown")
        self.assertEqual(rows[0]["hits"], 424)
        self.assertEqual(rows[2]["signature"], "")
        self.assertEqual(rows[2]["component"], "op:tai-feed")

    def test_parse_garbage_is_empty(self):
        self.assertEqual(m.parse_insight("không phải bảng"), [])
        self.assertEqual(m.parse_insight("| a | b |"), [])

    def test_buckets_sum_hits_and_flag_foreign_cert(self):
        lines = m.insight_lines(m.parse_insight(self.TABLE), "docs/loi/v174.md")
        text = "\n".join(lines)
        self.assertIn("**app / thao tác** 434 lượt", text)
        self.assertIn("**lõi** 75 lượt", text)
        # chứng thư phát hành không bị coi là lạ, chứng thư khác thì có
        self.assertIn("`1e08a903aef9` (75 lượt)", text)
        self.assertNotIn("`c46902e97029`", text.split("Chứng thư khác")[1])

    def test_build_with_insight_changes_limitation_text(self):
        text = m.build(ISSUES, "2026-11-03", m.parse_insight(self.TABLE), "docs/loi/v174.md")
        self.assertIn("## Bằng chứng V174", text)
        self.assertIn("`docs/loi/v174.md`", text)
        self.assertNotIn("cần PM chạy V174 một lần", text)


if __name__ == "__main__":
    unittest.main()
