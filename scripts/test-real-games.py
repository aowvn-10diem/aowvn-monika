#!/usr/bin/env python3
"""V124: test offline cho scripts/real-games.py, chỉ dùng secret giả, không mạng."""
import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import importlib.util

SPEC = importlib.util.spec_from_file_location(
    "real_games", Path(__file__).resolve().parent / "real-games.py")
real_games = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(real_games)

SYSTEMS = {
    "nds": {"id": "nds", "extensions": ["nds"]},
    "gbc": {"id": "gbc", "extensions": ["gb", "gbc"]},
    "rpgmv": {"id": "rpgmv", "extensions": []},
}
LINK_A = "https://example.invalid/a-secret-link.nds"
LINK_B = "https://example.invalid/b-secret-link.gb"
PASSWORD = "matkhau-gia-123"


class ParseSecretTest(unittest.TestCase):
    def test_comments_blank_lines_and_note_are_handled(self):
        text = f"nds | {LINK_A} | bản thử\n# ghi chú bị bỏ qua\n\n   \n"
        entries, errors = real_games.parse_secret(text, "nds", ["nds"])
        self.assertEqual(errors, [])
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["note"], "bản thử")
        self.assertEqual(entries[0]["password"], "")
        self.assertEqual(entries[0]["line"], 1)

    def test_extension_is_normalized_and_unknown_one_is_refused(self):
        text = f".NDS | {LINK_A}\ngb | {LINK_B}\n"
        entries, errors = real_games.parse_secret(text, "nds", ["nds"])
        self.assertEqual([e["ext"] for e in entries], ["nds"])
        self.assertEqual(len(errors), 1)
        self.assertIn("đuôi không thuộc hệ này", errors[0])
        self.assertIn("dòng 2", errors[0])

    def test_missing_column_and_non_https_link_are_reported(self):
        text = "nds\nnds |  | ghi chú\nnds | http://example.invalid/x.nds\n"
        entries, errors = real_games.parse_secret(text, "nds", ["nds"])
        self.assertEqual(entries, [])
        self.assertEqual(len(errors), 3)
        self.assertIn("thiếu cột", errors[0])
        self.assertIn("thiếu cột", errors[1])
        self.assertIn("không phải https", errors[2])

    def test_password_option_is_read_from_any_column_after_link(self):
        text = f"gb | {LINK_B} | mk={PASSWORD} | bản nén\n"
        entries, errors = real_games.parse_secret(text, "gbc", ["gb", "gbc"])
        self.assertEqual(errors, [])
        self.assertEqual(entries[0]["password"], PASSWORD)
        self.assertEqual(entries[0]["note"], "bản nén")

    def test_empty_password_option_means_let_the_app_try_its_own(self):
        text = f"nds | {LINK_A} | mk=\n"
        entries, _ = real_games.parse_secret(text, "nds", ["nds"])
        self.assertEqual(entries[0]["password"], "")

    def test_system_without_extensions_refuses_every_line(self):
        entries, errors = real_games.parse_secret(f"nds | {LINK_A}\n", "rpgmv", [])
        self.assertEqual(entries, [])
        self.assertEqual(len(errors), 1)


class BuildTest(unittest.TestCase):
    def test_missing_secret_is_skipped_and_values_are_masked(self):
        env = {
            "TEST_GAME_NDS": f"nds | {LINK_A} | ghi chú | mk={PASSWORD}\n",
            "TEST_GAME_GBC": "gb | not-https\n",
        }
        matrix, rows, errors, masks = real_games.build(SYSTEMS, env, set())
        self.assertEqual(len(matrix), 1)
        self.assertEqual(matrix[0]["system"], "nds")
        self.assertEqual(masks, [LINK_A, PASSWORD])
        results = {(row[0], row[3]) for row in rows}
        self.assertIn(("rpgmv", "bỏ qua: chưa có secret"), results)
        self.assertIn(("gbc", "bỏ qua: dòng 1: link không phải https"), results)
        self.assertEqual(len(errors), 1)

    def test_apk_is_excluded_even_when_its_secret_is_set(self):
        systems = dict(SYSTEMS, apk={"id": "apk", "extensions": ["apk"]})
        env = {"TEST_GAME_APK": f"apk | {LINK_A}\n"}
        matrix, rows, errors, masks = real_games.build(systems, env, set())
        self.assertEqual(matrix, [])
        self.assertEqual(masks, [])
        self.assertEqual(errors, [])
        self.assertIn(("apk", "bỏ qua: hệ không nằm trong V124"), {(r[0], r[3]) for r in rows})

    def test_requested_systems_filter_the_matrix(self):
        env = {"TEST_GAME_NDS": f"nds | {LINK_A}\n", "TEST_GAME_GBC": f"gb | {LINK_B}\n"}
        matrix, rows, _, _ = real_games.build(SYSTEMS, env, {"gbc"})
        self.assertEqual([m["system"] for m in matrix], ["gbc"])
        self.assertEqual({row[0] for row in rows}, {"gbc"})


class MainTest(unittest.TestCase):
    def run_main(self, env, requested=""):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = root / "config.json"
            config.write_text(json.dumps({"systems": list(SYSTEMS.values())}), encoding="utf-8")
            matrix_out = root / "matrix.json"
            summary = root / "summary.md"
            github_output = root / "output.txt"
            args = ["--config", str(config), "--systems", requested,
                    "--matrix-out", str(matrix_out), "--summary", str(summary),
                    "--github-output", str(github_output)]
            stdout = io.StringIO()
            with mock.patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(stdout):
                code = real_games.main(args)
            return (code, stdout.getvalue(), matrix_out.read_text(encoding="utf-8"),
                    summary.read_text(encoding="utf-8"), github_output.read_text(encoding="utf-8"))

    def test_output_masks_links_and_password_but_summary_has_no_secret(self):
        env = {"TEST_GAME_NDS": f"nds | {LINK_A} | ghi chú | mk={PASSWORD}\n"}
        code, stdout, matrix, summary, github_output = self.run_main(env)
        self.assertEqual(code, 0)
        lines = stdout.splitlines()
        self.assertIn(f"::add-mask::{LINK_A}", lines)
        self.assertIn(f"::add-mask::{PASSWORD}", lines)
        summary_line = next(i for i, line in enumerate(lines) if line.startswith("real-games:"))
        self.assertLess(lines.index(f"::add-mask::{LINK_A}"), summary_line)
        self.assertLess(lines.index(f"::add-mask::{PASSWORD}"), summary_line)
        self.assertNotIn(LINK_A, summary)
        self.assertNotIn(PASSWORD, summary)
        self.assertNotIn(LINK_A, stdout.replace(f"::add-mask::{LINK_A}", ""))
        self.assertIn("| nds | nds | ghi chú | chờ chạy |", summary)
        entry = json.loads(matrix)["include"][0]
        self.assertEqual(set(entry), {"system", "ext", "note", "line"})
        self.assertNotIn(LINK_A, matrix)
        self.assertNotIn(PASSWORD, matrix)
        self.assertIn("count=1", github_output)

    def test_empty_secrets_give_zero_count_and_no_entries(self):
        code, stdout, matrix, summary, github_output = self.run_main({})
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(matrix)["include"], [])
        self.assertIn("count=0", github_output)
        self.assertIn("bỏ qua: chưa có secret", summary)
        self.assertIn("0 mục chạy", stdout)


class PickTest(unittest.TestCase):
    def test_pick_writes_the_line_to_env_file_and_masks_first(self):
        env = {"TEST_GAME_NDS": f"# bỏ qua\nnds | {LINK_A} | ghi chú | mk={PASSWORD}\n"}
        with tempfile.TemporaryDirectory() as tmp:
            env_file = Path(tmp) / "github-env"
            stdout = io.StringIO()
            with mock.patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(stdout):
                code = real_games.main(["--config", str(self.config(tmp)), "--pick-system", "nds",
                                        "--pick-line", "2", "--env-file", str(env_file)])
            text = env_file.read_text(encoding="utf-8")
        self.assertEqual(code, 0)
        self.assertEqual(stdout.getvalue().splitlines(), [f"::add-mask::{LINK_A}", f"::add-mask::{PASSWORD}"])
        self.assertIn(f"REAL_LINK={LINK_A}\n", text)
        self.assertIn(f"REAL_PASSWORD={PASSWORD}\n", text)
        self.assertIn("REAL_EXT=nds\n", text)
        self.assertIn("REAL_NOTE=ghi chú\n", text)

    def test_pick_refuses_a_line_that_is_not_valid(self):
        env = {"TEST_GAME_NDS": f"gb | {LINK_B}\n"}
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.dict(os.environ, env, clear=True), self.assertRaises(SystemExit) as caught:
                real_games.main(["--config", str(self.config(tmp)), "--pick-system", "nds",
                                 "--pick-line", "1", "--env-file", str(Path(tmp) / "env")])
        self.assertIn("không có dòng 1 hợp lệ", str(caught.exception))
        self.assertNotIn(LINK_B, str(caught.exception))

    def test_pick_refuses_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SystemExit):
                real_games.main(["--config", str(self.config(tmp)), "--pick-system", "apk",
                                 "--pick-line", "1", "--env-file", str(Path(tmp) / "env")])

    def config(self, directory):
        path = Path(directory) / "config.json"
        path.write_text(json.dumps({"systems": list(SYSTEMS.values()) + [{"id": "apk", "extensions": ["apk"]}]}),
                        encoding="utf-8")
        return path


if __name__ == "__main__":
    unittest.main()
