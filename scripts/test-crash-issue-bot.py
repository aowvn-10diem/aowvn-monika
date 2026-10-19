#!/usr/bin/env python3
import copy
import importlib.util
import contextlib
import io
import json
import pathlib
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("bot", pathlib.Path(__file__).with_name("crash-issue-bot.py"))
bot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bot)
OWNERS = {"Sol": "sol-fixture", "Sonnet": "sonnet-fixture"}


def record(i, core="melondsds", kind="anr", detail="at a.b.f(File.java:9)\nat x.y.g(File.java:10)", count=1):
    return {"id": f"r:{1700000000000+i}:abcdef00", "report": {"session": {"core": core}, "kind": kind,
            "detail": detail, "count": count, "log": [], "game": "private", "image": {"data": "private"}}}


def issue(action):
    return {"number": 20, "body": action["body"], "state": "closed"}


class BotTest(unittest.TestCase):
    def test_assignable_204_transport_is_valid_without_json(self):
        class Response:
            status = 204
            def read(self, limit): return b""
            def __enter__(self): return self
            def __exit__(self, *args): pass
        class Opener:
            def open(self, request, timeout): return Response()
        with patch.object(bot.urllib.request, "build_opener", return_value=Opener()):
            self.assertEqual(bot.Client("https://api.github.com", "fake").call("/repos/o/r/assignees/fixture"), {})

    def test_main_blocked_reason_keeps_private_exception_messages_hidden(self):
        class PrivateValueError(ValueError): pass
        private = "private report/header/token fixture"
        cases = [
            (ValueError("HTTP request failed: 401"), "HTTP request failed: 401"),
            (KeyError(private), "KeyError"),
            (TypeError(private), "TypeError"),
            (json.JSONDecodeError(private, private, 0), "JSONDecodeError"),
            (PrivateValueError(private), "PrivateValueError"),
        ]
        for error, expected in cases:
            with self.subTest(kind=type(error).__name__):
                stdout, stderr = io.StringIO(), io.StringIO()
                with patch.object(bot.sys, "argv", ["crash-issue-bot.py", "--fixture", "unused"]), \
                     patch("builtins.open"), patch.object(bot.json, "load", side_effect=error), \
                     contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    with self.assertRaises(SystemExit) as exit_status:
                        bot.main()
                self.assertEqual(exit_status.exception.code, 2)
                self.assertEqual(stdout.getvalue(), "")
                self.assertEqual(stderr.getvalue(), "BLOCKED: " + expected + "\n")
                self.assertNotIn(private, stderr.getvalue())

    def test_http_401_reason_contains_only_status_code(self):
        private = "private report/header/token fixture"
        failure = bot.urllib.error.HTTPError("https://private.invalid", 401, private, {"Authorization": private}, None)
        opener = unittest.mock.Mock()
        opener.open.side_effect = failure
        with patch.object(bot.urllib.request, "build_opener", return_value=opener):
            with self.assertRaises(ValueError) as raised:
                bot.Client("https://worker.invalid", "fake").call("/reports")
        self.assertIs(type(raised.exception), ValueError)
        self.assertEqual(str(raised.exception), "HTTP request failed: 401")

    def test_signature_frames_core_kind_and_normalized_java_location(self):
        a = bot.plan([record(1)], [], OWNERS)[0]
        b = bot.plan([record(2, detail="at a.b.f(Else.java:999)\nat x.y.g(Else.java:12)")], [], OWNERS)[0]
        self.assertEqual(a["title"], b["title"])
        for row in [record(3, core="other"), record(4, kind="lowmem"), record(5, detail="at other.fn(F.java:1)")]:
            self.assertNotEqual(a["title"], bot.plan([row], [], OWNERS)[0]["title"])

    def test_only_first_three_frames_contribute(self):
        a = record(1, detail="\n".join(f"at p.c.f{i}(F.java:1)" for i in range(4)))
        b = copy.deepcopy(a); b["report"]["detail"] = b["report"]["detail"].replace("f3", "different")
        self.assertEqual(bot.plan([a], [], OWNERS), bot.plan([b], [], OWNERS))

    def test_native_addresses_paths_offsets_do_not_change_signature(self):
        a = record(1, detail="#00 pc 00000001 /private/a/libtest.so (function+32)")
        b = record(2, detail="#00 pc abcdef00 /different/libtest.so (function+900)")
        self.assertEqual(bot.plan([a], [], OWNERS)[0]["title"], bot.plan([b], [], OWNERS)[0]["title"])
        b["report"]["detail"] = b["report"]["detail"].replace("libtest.so", "libother.so")
        self.assertNotEqual(bot.plan([a], [], OWNERS)[0]["title"], bot.plan([b], [], OWNERS)[0]["title"])

    def test_native_hex_offsets_group_with_decimal_but_different_symbols_stay_separate(self):
        base = record(1, detail="#00 pc 0001 /private/libtest.so (function+32)")
        expected = bot.plan([base], [], OWNERS)[0]["title"]
        for offset in ["0x20", "0x44", "0XABCD", "9876"]:
            row = record(2, detail=f"#00 pc abcdef /different/libtest.so (function+{offset})")
            self.assertEqual(bot.plan([row], [], OWNERS)[0]["title"], expected)
        other = record(3, detail="#00 pc abcdef /different/libtest.so (other+0x20)")
        self.assertNotEqual(bot.plan([other], [], OWNERS)[0]["title"], expected)

    def test_new_issue_and_repeat_are_idempotent(self):
        first = bot.plan([record(1)], [], OWNERS)[0]
        self.assertEqual(first["assignees"], ["sol-fixture"])
        self.assertEqual(bot.plan([record(1)], [issue(first)], OWNERS), [])
        self.assertNotIn("private", first["body"])
        self.assertNotIn("File.java", first["body"])

    def test_four_then_five_new_occurrences_and_preserve_human_body(self):
        previous = issue(bot.plan([record(1)], [], OWNERS)[0]); previous["body"] += "\nHuman note"
        self.assertEqual(bot.plan([record(i) for i in range(1, 6)], [previous], OWNERS), [])
        updated = bot.plan([record(i) for i in range(1, 7)], [previous], OWNERS)[0]
        self.assertEqual(updated["number"], 20)
        self.assertIn("(+5)", updated["body"])
        self.assertTrue(updated["body"].endswith("Human note"))
        self.assertEqual(bot.plan([record(i) for i in range(1, 7)], [issue(updated)], OWNERS), [])

    def test_owner_routing(self):
        for core in ["azahar", "kirikiri", "mkxp-z"]:
            self.assertEqual(bot.plan([record(1, core=core)], [], OWNERS)[0]["assignees"], ["sonnet-fixture"])

    def test_client_count_increase_triggers_without_double_count(self):
        previous = issue(bot.plan([record(1, count=2)], [], OWNERS)[0])
        self.assertEqual(bot.plan([record(1, count=6)], [previous], OWNERS), [])
        self.assertIn("(+5)", bot.plan([record(1, count=7)], [previous], OWNERS)[0]["body"])

    def test_bad_state_duplicate_id_and_missing_owner_fail_closed(self):
        action = bot.plan([record(1)], [], OWNERS)[0]
        for rows, issues, owners in [([record(1), record(1)], [], OWNERS), ([record(2)], [issue(action)]*2, OWNERS),
                                    ([record(1)], [], {}), ([record(1, count=-1)], [], OWNERS)]:
            with self.assertRaises(ValueError): bot.plan(rows, issues, owners)

    def test_mutation_budget_has_no_partial_plan(self):
        with self.assertRaises(ValueError):
            bot.plan([record(i, core=f"core{i}") for i in range(21)], [], OWNERS)

    def test_all_reads_before_writes_and_existing_closed_issue_not_reopened(self):
        first = bot.plan([record(1)], [], OWNERS)[0]
        class Fake:
            def __init__(self, worker=False): self.worker, self.writes = worker, []
            def call(self, path, method="GET", data=None):
                if method != "GET": self.writes.append((method, data)); return {}
                if self.worker:
                    if path == "/reports": return {"reports": [{"id": record(i)["id"]} for i in range(1, 7)]}
                    return record(int(path.split("170000000000")[1][0]))["report"]
                if "/assignees/" in path: return {}
                if "/labels/" in path: return {"name": "crash-moi"}
                return [issue(first)]
        worker, github = Fake(True), Fake()
        self.assertEqual(bot.live(worker, github, "o/r", OWNERS), 1)
        self.assertEqual(github.writes[0][0], "PATCH")
        self.assertEqual(set(github.writes[0][1]), {"body"})

    def test_missing_label_and_failed_detail_never_write(self):
        class Fake:
            def __init__(self, label=True): self.label, self.writes = label, []
            def call(self, path, method="GET", data=None):
                if method != "GET": self.writes.append(data); return {}
                if "/assignees/" in path: return {}
                if "/labels/" in path: return {"name": "crash-moi"} if self.label else {}
                if "/issues?" in path: return []
                if path == "/reports": return {"reports": [{"id": record(1)["id"]}]}
                raise ValueError("detail failed")
        for configured in [False, True]:
            client = Fake(configured)
            with self.assertRaises(ValueError): bot.live(client, client, "o/r", OWNERS)
            self.assertEqual(client.writes, [])

    def test_many_seen_hashes_still_fit_issue_body(self):
        old = issue(bot.plan([record(i) for i in range(1000)], [], OWNERS)[0])
        updated = bot.plan([record(i) for i in range(1000, 1100)], [old], OWNERS)[0]
        self.assertLess(len(updated["body"].encode()), 60000)
        self.assertEqual(len(bot.read_state(updated)["seen"]), 1000)


if __name__ == "__main__": unittest.main()
