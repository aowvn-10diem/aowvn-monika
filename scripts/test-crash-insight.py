#!/usr/bin/env python3
"""Test offline cho scripts/crash-insight.py — không gọi mạng, dùng payload giả.

Chạy: python3 scripts/test-crash-insight.py
"""
import importlib.util
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("crash_insight", os.path.join(HERE, "crash-insight.py"))
insight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(insight)

GAME_NAME = "Tên Game Bí Mật"
PATH_LEAK = "/storage/emulated/0/Android/data/secret"


def row(**kw):
    base = {"id": "r:1700000000000:abcd1234", "k": "op-fail", "c": "unknown", "y": "", "g": GAME_NAME,
            "a": "0.8.8 (128)", "st": "", "t": 1_700_000_000_000, "component": "op:giai-nen",
            "fp": "0123456789abcdef0123456789abcdef", "count": 1}
    base.update(kw)
    return base


class Grouping(unittest.TestCase):
    def test_same_core_kind_signature_is_one_group_and_counts_add_up(self):
        groups = insight.group_rows([row(count=2), row(count=3), row(t=1_700_000_000_100)])
        self.assertEqual(1, len(groups))
        self.assertEqual(6, groups[0]["count"])
        self.assertEqual("0123456789ab", groups[0]["signature"])
        self.assertEqual("unknown", groups[0]["core"])
        self.assertEqual("op-fail", groups[0]["kind"])
        self.assertEqual(1_700_000_000_100, groups[0]["newest"])

    def test_different_signature_or_kind_stays_separate_and_sorted_by_count(self):
        groups = insight.group_rows([
            row(count=1, fp="aaaaaaaaaaaa1111"),
            row(count=9, fp="bbbbbbbbbbbb2222"),
            row(count=4, fp="bbbbbbbbbbbb2222", k="anr"),
        ])
        self.assertEqual(3, len(groups))
        self.assertEqual(["bbbbbbbbbbbb", "bbbbbbbbbbbb", "aaaaaaaaaaaa"], [g["signature"] for g in groups])
        self.assertEqual([9, 4, 1], [g["count"] for g in groups])

    def test_missing_or_malformed_signature_is_dash_not_invented(self):
        # Không có chữ ký dùng được → gộp theo (lõi, loại, thành phần) và ghi `—`, không bịa băm.
        groups = insight.group_rows([row(fp=""), row(fp="không-phải-hex", count=2)])
        self.assertEqual(1, len(groups))
        self.assertEqual(3, groups[0]["count"])
        self.assertEqual(insight.MISSING, insight.table_rows(groups)[0][0])

    def test_app_column_lists_distinct_versions(self):
        groups = insight.group_rows([row(a="0.8.8 (128)"), row(a="0.8.9 (130)")])
        self.assertEqual("0.8.8 (128), 0.8.9 (130)", insight.table_rows(groups)[0][4])

    def test_bad_payload_is_rejected(self):
        for payload in ({"khong-co": []}, "chuỗi", {"reports": [1, 2]}):
            with self.assertRaises(ValueError):
                insight.group_rows(insight.rows_of(payload))

    def test_too_many_rows_is_rejected(self):
        with self.assertRaises(ValueError):
            insight.rows_of({"reports": [row()] * (insight.MAX_ROWS + 1)})


class Rendering(unittest.TestCase):
    def test_markdown_has_exact_columns_and_hypothesis(self):
        markdown = insight.render_markdown(insight.group_rows([row()]), 1)
        header = "| " + " | ".join(insight.COLUMNS) + " |"
        self.assertIn(header, markdown)
        self.assertIn("giải nén lỗi", markdown)
        self.assertIn("Đọc **một lần**", markdown)

    def test_missing_device_column_is_dash_and_reported(self):
        markdown = insight.render_markdown(insight.group_rows([row()]), 1)
        self.assertIn("| — |", markdown)
        self.assertIn("Thiếu trường:", markdown)
        self.assertIn("đời máy", markdown)
        self.assertIn("`device`", markdown)

    def test_anr_without_session_gets_main_thread_hypothesis(self):
        groups = insight.group_rows([row(k="anr", component="app:chính", fp="cccccccccccc3333")])
        self.assertEqual("treo tiến trình chính (không có phiên game)", insight.table_rows(groups)[0][7])

    def test_csv_header_and_rows_are_machine_readable(self):
        table = insight.render_csv(insight.group_rows([row(count=7)]))
        lines = table.splitlines()
        self.assertEqual(",".join(insight.COLUMNS), lines[0])
        self.assertEqual(2, len(lines))
        self.assertIn("0123456789ab", lines[1])
        self.assertIn(",7,", lines[1])

    def test_no_game_name_or_path_ever_reaches_output(self):
        payload = {"reports": [row(), row(detail=PATH_LEAK, title=GAME_NAME, device=PATH_LEAK)]}
        markdown, table, _, _ = insight.build(payload)
        for output in (markdown, table):
            self.assertNotIn(GAME_NAME, output)
            self.assertNotIn("secret", output)
            self.assertNotIn("emulated", output)

    def test_empty_listing_still_writes_a_table(self):
        markdown, table, scanned, groups = insight.build({"reports": []})
        self.assertEqual(0, scanned)
        self.assertEqual(0, groups)
        self.assertIn("| " + " | ".join(insight.COLUMNS) + " |", markdown)
        self.assertEqual(1, len(table.splitlines()))


class Endpoint(unittest.TestCase):
    def test_only_https_workers_dev_is_accepted(self):
        self.assertEqual("https://x.workers.dev", insight.validate_base("https://x.workers.dev/"))
        for bad in ("http://x.workers.dev", "https://x.example.com", "https://u:p@x.workers.dev",
                    "https://x.workers.dev/path", "https://x.workers.dev?a=1", ""):
            with self.assertRaises(ValueError):
                insight.validate_base(bad)

    def test_missing_token_is_refused_before_any_request(self):
        with self.assertRaises(ValueError):
            insight.fetch_listing("https://x.workers.dev", "")

    def test_offline_file_path_never_needs_a_token(self):
        # build() là đường offline dùng cho test/--file: không đọc env, không gọi mạng.
        self.assertTrue(insight.build({"reports": [row()]})[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
