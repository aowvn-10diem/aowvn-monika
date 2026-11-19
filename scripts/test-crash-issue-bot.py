#!/usr/bin/env python3
import copy
import importlib.util
import contextlib
import io
import json
import pathlib
import unittest
import urllib.parse
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("bot", pathlib.Path(__file__).with_name("crash-issue-bot.py"))
bot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bot)
OWNERS = {"Sol": "sol-fixture", "Sonnet": "sonnet-fixture"}


def record(i, core="melondsds", kind="anr", detail="at a.b.f(File.java:9)\nat x.y.g(File.java:10)", count=1,
           app=None, cert=None, time=None):
    report = {"session": {"core": core}, "kind": kind, "detail": detail, "count": count, "log": [],
              "game": "private", "image": {"data": "private"}, "device": "private-device"}
    for key, value in (("app", app), ("certSha256", cert), ("time", time)):
        if value is not None:
            report[key] = value
    return {"id": f"r:{1700000000000+i}:abcdef00", "report": report}


OFFICIAL = "c46902e97029a96593f43c38abd9871170a7519d90992a0b5466c752d45ab20c"
OTHER_CERT = "a" * 64


def issue(action):
    return {"number": 20, "body": action["body"], "state": "closed"}


class BatchGitHub:
    """GitHub + Worker giả cho ca chia lô: GET trả trạng thái hiện tại, PATCH sửa thân, POST thêm issue."""

    def __init__(self, rows, issues):
        self.rows, self.issues, self.writes = rows, issues, []

    def call(self, path, method="GET", data=None):
        if method == "GET":
            if path == "/reports":
                return {"reports": [{"id": row["id"]} for row in self.rows]}
            if path.startswith("/reports/"):
                report_id = urllib.parse.unquote(path[len("/reports/"):])
                return next(row["report"] for row in self.rows if row["id"] == report_id)
            if "/assignees/" in path:
                return {}
            if "/labels/" in path:
                return {"name": "crash-moi"}
            if "/issues?" in path:
                return [dict(item) for item in self.issues]
        else:
            self.writes.append((method, path, data))
            if method == "PATCH":
                number = int(path.rsplit("/", 1)[1])
                for item in self.issues:
                    if item["number"] == number:
                        item["body"] = data["body"]
            else:
                self.issues.append({"number": 900 + len(self.issues), "state": "open",
                                    "title": data["title"], "body": data["body"]})
            return {}
        raise AssertionError("unexpected request: " + path)


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

    def test_main_strips_outer_token_whitespace_before_building_clients(self):
        for supplied in ["fixture-token", " \t\r\nfixture-token\r\n\t ", "!fixture ~"]:
            with self.subTest(supplied=supplied):
                stdout, stderr = io.StringIO(), io.StringIO()
                with patch.object(bot.sys, "argv", ["crash-issue-bot.py", "--apply"]), \
                     patch.dict(bot.os.environ, {"CRASH_ADMIN_TOKEN": supplied, "GITHUB_TOKEN": "fake-github",
                                                "GITHUB_REPOSITORY": "o/r"}, clear=True), \
                     patch.object(bot, "Client") as client, patch.object(bot, "live", return_value=(0, 0)) as live, \
                     contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    bot.main()
                self.assertEqual(client.call_args_list[0].args[1], supplied.strip())
                self.assertEqual(client.call_args_list[1].args[1], "fake-github")
                live.assert_called_once()
                self.assertEqual(stdout.getvalue(), "Issue mutations: 0; còn lại: 0.\n")
                self.assertEqual(stderr.getvalue(), "")

    def test_main_rejects_non_printable_or_non_ascii_token_before_clients(self):
        for char in ["\x00", "\t", "\r", "\n", "\x1f", "\x7f", "\x80", "đ", "\u200b"]:
            with self.subTest(char=repr(char)):
                stdout, stderr = io.StringIO(), io.StringIO()
                with patch.object(bot.sys, "argv", ["crash-issue-bot.py", "--apply"]), \
                     patch.object(bot.os, "environ", {"CRASH_ADMIN_TOKEN": "private-fixture" + char + "tail",
                                                    "GITHUB_TOKEN": "fake-github", "GITHUB_REPOSITORY": "o/r"}), \
                     patch.object(bot, "Client") as client, patch.object(bot, "live") as live, \
                     contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    with self.assertRaises(SystemExit) as exit_status:
                        bot.main()
                self.assertEqual(exit_status.exception.code, 2)
                client.assert_not_called()
                live.assert_not_called()
                self.assertEqual(stdout.getvalue(), "")
                self.assertEqual(stderr.getvalue(), "BLOCKED: CRASH_ADMIN_TOKEN has invalid characters\n")
                self.assertNotIn("private-fixture", stderr.getvalue())

    def test_main_empty_token_after_strip_keeps_missing_credentials_guard(self):
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(bot.sys, "argv", ["crash-issue-bot.py", "--apply"]), \
             patch.dict(bot.os.environ, {"CRASH_ADMIN_TOKEN": " \t\r\n ", "GITHUB_TOKEN": "fake-github",
                                        "GITHUB_REPOSITORY": "o/r"}, clear=True), \
             patch.object(bot, "Client") as client, patch.object(bot, "live") as live, \
             contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            with self.assertRaises(SystemExit) as exit_status:
                bot.main()
        self.assertEqual(exit_status.exception.code, 2)
        client.assert_not_called()
        live.assert_not_called()
        self.assertEqual(stdout.getvalue(), "")
        self.assertEqual(stderr.getvalue(), "BLOCKED: missing runtime credentials/repository\n")

    def test_http_library_value_error_never_exposes_header_value(self):
        for raw, expected in [("Invalid header value b'private-token-fixture'", "invalid HTTP request configuration"),
                              ("redirect blocked", "redirect blocked")]:
            with self.subTest(raw=raw):
                opener = unittest.mock.Mock()
                opener.open.side_effect = ValueError(raw)
                with patch.object(bot.urllib.request, "build_opener", return_value=opener):
                    with self.assertRaises(ValueError) as raised:
                        bot.Client("https://worker.invalid", "fake").call("/reports")
                self.assertIs(type(raised.exception), ValueError)
                self.assertEqual(str(raised.exception), expected)

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

    def test_core_uses_outer_report_level_when_session_is_missing(self):
        # L1 (bot-core): `anr`/`lowmem` có lõi ở cấp ngoài, không có `session` (xem #413/#534).
        outer = {"id": "r:1700000000100:abcdef00",
                 "report": {"kind": "anr", "core": "citra", "detail": "", "count": 1, "log": []}}
        body = bot.plan([outer], [], OWNERS)[0]["body"]
        self.assertIn("Lõi: `citra`", body)
        self.assertNotIn("Lõi: `unknown`", body)

    def test_core_prefers_session_and_falls_back_to_outer(self):
        # Chỉ có phiên: giữ nguyên như trước.
        self.assertIn("Lõi: `melondsds`", bot.plan([record(1, core="melondsds")], [], OWNERS)[0]["body"])
        # Có cả hai: ưu tiên phiên, không lấy lõi ngoài.
        both = {"id": "r:1700000000101:abcdef00",
                "report": {"kind": "anr", "core": "citra", "session": {"core": "melondsds"},
                           "detail": "", "count": 1, "log": []}}
        body = bot.plan([both], [], OWNERS)[0]["body"]
        self.assertIn("Lõi: `melondsds`", body)
        self.assertNotIn("`citra`", body)
        # Cả hai đều rỗng/không hợp lệ: vẫn là unknown.
        empty = {"id": "r:1700000000102:abcdef00",
                 "report": {"kind": "anr", "core": "", "session": {"core": ""}, "detail": "", "count": 1, "log": []}}
        self.assertIn("Lõi: `unknown`", bot.plan([empty], [], OWNERS)[0]["body"])

    def test_closed_issue_body_marks_the_count_as_cumulative_history(self):
        # #304/#305: issue đã đóng nhưng bot vẫn cộng dồn ⇒ người đọc phải biết số đó không phải mức tăng hiện tại.
        first = bot.plan([record(1)], [], OWNERS)[0]
        closed = issue(first)  # helper đặt state="closed"
        updated = bot.plan([record(i) for i in range(1, 7)], [closed], OWNERS)[0]
        self.assertIn("Issue này đã đóng", updated["body"])
        self.assertIn("cộng dồn lịch sử", updated["body"])
        self.assertEqual(updated["body"].count("cộng dồn lịch sử"), 1)

        opened = dict(closed, state="open")
        reopened = bot.plan([record(i) for i in range(1, 7)], [opened], OWNERS)[0]
        self.assertNotIn("cộng dồn lịch sử", reopened["body"])

        # Cập nhật lần sau thay cả khối giữa hai marker nên ghi chú không bị nhân đôi.
        again = bot.plan([record(i) for i in range(1, 13)], [dict(closed, body=updated["body"])], OWNERS)[0]
        self.assertEqual(again["body"].count("cộng dồn lịch sử"), 1)

    def test_client_count_increase_triggers_without_double_count(self):
        previous = issue(bot.plan([record(1, count=2)], [], OWNERS)[0])
        self.assertEqual(bot.plan([record(1, count=6)], [previous], OWNERS), [])
        self.assertIn("(+5)", bot.plan([record(1, count=7)], [previous], OWNERS)[0]["body"])

    def test_bad_state_duplicate_id_and_missing_owner_fail_closed(self):
        action = bot.plan([record(1)], [], OWNERS)[0]
        for rows, issues, owners in [([record(1), record(1)], [], OWNERS), ([record(2)], [issue(action)]*2, OWNERS),
                                    ([record(1)], [], {}), ([record(1, count=-1)], [], OWNERS)]:
            with self.assertRaises(ValueError): bot.plan(rows, issues, owners)

    def test_mutation_budget_trims_instead_of_blocking_the_run(self):
        # Ngân sách là chia lô: 21 nhóm mới ⇒ lượt này lập 20 issue, phần dư để lượt sau (không ném, không huỷ lượt).
        actions = bot.plan([record(i, core=f"core{i}") for i in range(21)], [], OWNERS)
        self.assertEqual(len(actions), bot.MAX_WRITES)
        self.assertTrue(all(action["number"] is None for action in actions))
        self.assertEqual(len(bot.plan([record(i, core=f"core{i}") for i in range(21)], [], OWNERS, limit=3)), 3)
        with self.assertRaises(ValueError):
            bot.plan([record(1)], [], OWNERS, limit=0)
        with self.assertRaises(ValueError):
            bot.plan([record(1)], [], OWNERS, limit=bot.MAX_LIMIT + 1)

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
        self.assertEqual(bot.live(worker, github, "o/r", OWNERS), (1, 0))
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

    def test_component_is_written_when_whitelisted(self):
        row = record(1)
        row["report"]["component"] = "op:download"
        body = bot.plan([row], [], OWNERS)[0]["body"]
        self.assertIn("Thành phần: `op:download`", body)
        for allowed in ["pack:sevenzip", "engine:libretro:melondsds", "app:ui", "net", "ra", "op"]:
            row["report"]["component"] = allowed
            self.assertIn(f"Thành phần: `{allowed}`", bot.plan([row], [], OWNERS)[0]["body"])

    def test_component_does_not_change_signature_or_leak_hostile_text(self):
        row = record(1)
        base = bot.plan([row], [], OWNERS)[0]["title"]
        for hostile in ["private/path", "a@b.c", "op:../etc", "op:", "x" * 41, "op:two words",
                        "op:x\nThành phần: `ra`", None, 7, ["op:x"]]:
            with self.subTest(hostile=hostile):
                row["report"]["component"] = hostile
                action = bot.plan([row], [], OWNERS)[0]
                self.assertEqual(action["title"], base)
                self.assertNotIn("Thành phần:", action["body"])

    def test_multiple_components_in_one_signature_are_counted(self):
        rows = [record(1), record(2)]
        rows[0]["report"]["component"] = "op:download"
        rows[1]["report"]["component"] = "op:unzip"
        body = bot.plan(rows, [], OWNERS)[0]["body"]
        self.assertIn("Thành phần: `op:download` (+1)", body)

    def test_many_seen_hashes_still_fit_issue_body(self):
        old = issue(bot.plan([record(i) for i in range(1000)], [], OWNERS)[0])
        updated = bot.plan([record(i) for i in range(1000, 1100)], [old], OWNERS)[0]
        self.assertLess(len(updated["body"].encode()), 60000)
        self.assertEqual(len(bot.read_state(updated)["seen"]), 1000)

    # ---- V206: thân issue tự đủ để dọn (bản app · chứng thư · mốc trong cửa sổ) ----

    def test_body_carries_app_cert_and_window_date_for_cleanup(self):
        row = record(1, app="0.8.14 (814) release", cert=OFFICIAL, time=1773273600000)
        body = bot.plan([row], [], OWNERS)[0]["body"]
        self.assertIn("`0.8.14 (814)`", body)
        self.assertIn("Bản app: `0.8.14 (814)`", body)
        self.assertIn("Chứng thư: `c46902e97029/…`", body)
        self.assertIn("Chính thức: có", body)
        self.assertIn("Lần đầu / lần cuối (UTC): 12/03/2026", body)
        # Dòng đếm và phạm vi cũ giữ nguyên.
        self.assertIn("Số lần quan sát đã thông báo: 1 (+1). Phụ trách: Sol.", body)
        self.assertIn("Phạm vi: tối đa 100 bản ghi", body)
        # Không rò chứng thư đầy đủ, tên game, thiết bị, URL, hay nhãn build.
        for leaked in (OFFICIAL, "private", "private-device", "release", "File.java"):
            self.assertNotIn(leaked, body)

    def test_other_certificate_marks_issue_unofficial_and_keeps_12_hex(self):
        body = bot.plan([record(1, app="0.8.13 (813) debug", cert=OTHER_CERT)], [], OWNERS)[0]["body"]
        self.assertIn("Chứng thư: `aaaaaaaaaaaa/…`", body)
        self.assertIn("Chính thức: không", body)
        self.assertNotIn(OTHER_CERT, body)

    def test_mixed_certificates_show_newest_and_count_the_rest_as_unofficial(self):
        rows = [record(1, cert=OFFICIAL, time=1773273600000), record(2, cert=OTHER_CERT, time=1773446400000)]
        body = bot.plan(rows, [], OWNERS)[0]["body"]
        self.assertIn("Chứng thư: `aaaaaaaaaaaa/…` (+1 chứng thư khác)", body)
        self.assertIn("Chính thức: không", body)
        self.assertIn("Lần đầu / lần cuối (UTC): 12/03/2026 / 14/03/2026", body)

    def test_missing_certificate_is_unknown_never_official(self):
        body = bot.plan([record(1, app="0.8.14 (814) debug")], [], OWNERS)[0]["body"]
        self.assertIn("Chính thức: không rõ (báo cáo không kèm chứng thư)", body)
        self.assertNotIn("Chính thức: có", body)
        self.assertNotIn("Chứng thư:", body)

    def test_official_plus_report_without_cert_is_never_official(self):
        # Nhóm có 1 bản ghi đúng chứng thư phát hành + 1 bản ghi THIẾU chứng thư ⇒ không được coi là "có".
        rows = [record(1, app="0.8.14 (814) release", cert=OFFICIAL, time=1773273600000),
                record(2, app="0.8.14 (814) release", time=1773446400000)]
        body = bot.plan(rows, [], OWNERS)[0]["body"]
        self.assertIn("Chứng thư: `c46902e97029/…` · Chính thức: không", body)
        self.assertNotIn("Chính thức: có", body)
        self.assertIn("(1/2 bản ghi có chứng thư)", body)
        # Cùng chứng thư nhưng chỉ 1 bản ghi thiếu ⇒ vẫn "không", không phải "có".
        self.assertEqual(bot.describe([(1, "0.8.14 (814)", OFFICIAL)]),
                         ["Bản app: `0.8.14 (814)`", "Chứng thư: `c46902e97029/…` · Chính thức: có"])
        self.assertEqual(bot.describe([(1, "", OFFICIAL), (2, "", "")])[0],
                         "Chứng thư: `c46902e97029/…` · Chính thức: không (1/2 bản ghi có chứng thư)")

    def test_junk_timestamps_never_raise_and_keep_app_and_cert(self):
        for junk in (None, "1773446400000", 1.5, True, [1773446400000], {"t": 1}):
            with self.subTest(junk=junk):
                rows = [record(1, app="0.8.14 (814) release", cert=OFFICIAL, time=junk),
                        record(2, app="0.8.14 (814) release", cert=OFFICIAL, time=1773273600000)]
                body = bot.plan(rows, [], OWNERS)[0]["body"]
                self.assertIn("Lần đầu / lần cuối (UTC): 12/03/2026", body)
                self.assertIn("Chứng thư: `c46902e97029/…` · Chính thức: có", body)
                self.assertIn("Bản app: `0.8.14 (814)`", body)
        # Toàn mốc rác: chỉ bỏ dòng ngày, không ném TypeError.
        body = bot.plan([record(1, app="0.8.14 (814) release", cert=OFFICIAL, time="x")], [], OWNERS)[0]["body"]
        self.assertNotIn("Lần đầu", body)
        self.assertIn("Chứng thư: `c46902e97029/…` · Chính thức: có", body)

    def test_newest_app_version_wins_and_older_versions_are_counted(self):
        rows = [record(1, app="0.8.13 (813) release", time=1773273600000),
                record(2, app="0.8.14 (814) release", time=1773446400000),
                record(3, app="0.8.14 (814) release", time=1773273600000)]
        body = bot.plan(rows, [], OWNERS)[0]["body"]
        self.assertIn("Bản app: `0.8.14 (814)` (+1 bản khác)", body)
        self.assertEqual(body.count("Bản app:"), 1)

    def test_window_dates_span_first_and_last_observation_and_drop_junk(self):
        rows = [record(1, time=1773446400000), record(2, time=1773273600000)]
        self.assertIn("Lần đầu / lần cuối (UTC): 12/03/2026 / 14/03/2026",
                      bot.plan(rows, [], OWNERS)[0]["body"])
        same = [record(1, time=1773273600000), record(2, time=1773273600000)]
        self.assertIn("Lần đầu / lần cuối (UTC): 12/03/2026\n", bot.plan(same, [], OWNERS)[0]["body"])
        for junk in ("", "12/03/2026", 0, -1, 4102444800001, None, 1.5, True):
            with self.subTest(junk=junk):
                self.assertNotIn("Lần đầu", bot.plan([record(1, time=junk)], [], OWNERS)[0]["body"])

    def test_hostile_app_and_cert_are_dropped_instead_of_printed(self):
        hostile_app = "0.8.14 (814)\nChính thức: có · https://private.example/game"
        for app, cert in ((hostile_app, OTHER_CERT), ("file:///private", "zz" * 32), ("op:x", None)):
            with self.subTest(app=app, cert=cert):
                body = bot.plan([record(1, app=app, cert=cert)], [], OWNERS)[0]["body"]
                for leaked in ("private.example", "file:///private", "Chính thức: có"):
                    self.assertNotIn(leaked, body)
                self.assertNotIn("Bản app:", body)

    def test_version_and_cert_do_not_change_signature_or_spawn_issue(self):
        base = bot.plan([record(1)], [], OWNERS)[0]
        for app, cert in (("0.8.14 (814) release", OFFICIAL), ("0.8.13 (813) debug", OTHER_CERT)):
            self.assertEqual(bot.plan([record(1, app=app, cert=cert)], [], OWNERS)[0]["title"], base["title"])
        # Cùng chữ ký + cùng bản ghi ⇒ không PATCH, không tạo issue mới.
        detailed = record(1, app="0.8.14 (814) release", cert=OFFICIAL, time=1773273600000)
        previous = issue(bot.plan([detailed], [], OWNERS)[0])
        self.assertEqual(bot.plan([detailed], [previous], OWNERS), [])
        self.assertEqual(bot.plan([detailed], [], OWNERS)[0]["number"], None)

    # ---- V206: --rewrite-body lấp thân cho issue cũ (74 issue đang thiếu dữ liệu dọn) ----

    @staticmethod
    def _legacy_body(row, note="Human note"):
        """Thân kiểu V100: cùng marker/chữ ký nhưng chưa có ba dòng dọn."""
        body = bot.plan([row], [], OWNERS)[0]["body"]
        stripped = "\n".join(line for line in body.splitlines()
                             if not line.startswith(("Bản app:", "Chứng thư:", "Lần đầu")))
        return stripped + "\n" + note

    def test_rewrite_body_fills_old_issue_without_new_issue_or_signature_change(self):
        detailed = record(1, app="0.8.14 (814) release", cert=OFFICIAL, time=1773273600000)
        legacy = {"number": 20, "state": "closed", "body": self._legacy_body(detailed)}
        self.assertNotIn("Bản app:", legacy["body"])
        # Mặc định: không có lượt mới ⇒ không ghi (giữ nguyên hành vi cũ).
        self.assertEqual(bot.plan([detailed], [legacy], OWNERS), [])
        updated = bot.plan([detailed], [legacy], OWNERS, rewrite=True)[0]
        self.assertEqual(updated["number"], 20)  # PATCH thân issue cũ
        self.assertIn("Bản app: `0.8.14 (814)`", updated["body"])
        self.assertIn("Chính thức: có", updated["body"])
        self.assertIn("Lần đầu / lần cuối (UTC): 12/03/2026", updated["body"])
        self.assertTrue(updated["body"].endswith("Human note"))
        self.assertEqual(bot.read_state(updated)["signature"], bot.read_state(legacy)["signature"])
        self.assertEqual(bot.read_state(updated)["count"], bot.read_state(legacy)["count"])
        # Lượt thứ hai: thân đã đúng ⇒ không tốn lượt ghi.
        self.assertEqual(bot.plan([detailed], [dict(legacy, body=updated["body"])], OWNERS, rewrite=True), [])
        # Không có issue cũ ⇒ --rewrite-body không tạo issue mới.
        self.assertEqual(bot.plan([detailed], [], OWNERS, rewrite=True), [])

    def test_rewrite_body_trims_to_one_batch_instead_of_blocking(self):
        rows, legacy = [], []
        for i in range(bot.MAX_WRITES + 1):
            row = record(i, core=f"core{i}", app="0.8.14 (814) release", cert=OFFICIAL)
            legacy.append({"number": 100 + i, "state": "open", "body": self._legacy_body(row)})
            rows.append(row)
        # Chia lô: 21 mục ⇒ lượt này 20, không ném; `--max-writes` đổi cỡ lô.
        self.assertEqual(len(bot.plan(rows, legacy, OWNERS, rewrite=True)), bot.MAX_WRITES)
        self.assertEqual(len(bot.plan(rows, legacy, OWNERS, rewrite=True, limit=5)), 5)

    # ---- V206b: ngân sách ghi chia lô, chạy lặp tới khi hết (74 issue ⇒ 4 lượt 20+20+20+14) ----

    def _legacy_scene(self, count):
        """`count` issue cũ + bản ghi tương ứng, cùng dữ liệu — mô phỏng 74 issue đang thiếu dữ liệu dọn."""
        rows, issues = [], []
        for i in range(count):
            row = record(i, core=f"core{i}", app="0.8.14 (814) release", cert=OFFICIAL)
            rows.append(row)
            issues.append({"number": 10 + i, "state": "open", "body": self._legacy_body(row)})
        return rows, issues

    def test_budget_batch_one_writes_twenty_and_reports_twentyfive_left(self):
        rows, issues = self._legacy_scene(45)
        gh = BatchGitHub(rows, issues)
        self.assertEqual(bot.live(gh, gh, "o/r", OWNERS, rewrite=True), (20, 25))
        self.assertEqual(len([1 for method, _, _ in gh.writes if method == "PATCH"]), 20)
        # `main` chạy hết rồi thoát 0 (không SystemExit) và in rõ đã ghi / còn lại.
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(bot.sys, "argv", ["crash-issue-bot.py", "--apply", "--rewrite-body"]), \
             patch.dict(bot.os.environ, {"CRASH_ADMIN_TOKEN": "fixture-token", "GITHUB_TOKEN": "fake-github",
                                        "GITHUB_REPOSITORY": "o/r"}, clear=True), \
             patch.object(bot, "Client"), patch.object(bot, "live", return_value=(20, 25)), \
             contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            bot.main()
        self.assertEqual(stdout.getvalue(), "Issue mutations: 20; còn lại: 25. Chạy lại lượt sau để làm tiếp.\n")
        self.assertEqual(stderr.getvalue(), "")

    def test_budget_batch_two_writes_next_twenty_without_rewriting_finished_issues(self):
        rows, issues = self._legacy_scene(45)
        gh = BatchGitHub(rows, issues)
        self.assertEqual(bot.live(gh, gh, "o/r", OWNERS, rewrite=True), (20, 25))
        first = [path for method, path, _ in gh.writes if method == "PATCH"]
        self.assertEqual(bot.live(gh, gh, "o/r", OWNERS, rewrite=True), (20, 5))
        second = [path for method, path, _ in gh.writes if method == "PATCH"][len(first):]
        self.assertEqual(len(second), 20)
        self.assertEqual(set(first) & set(second), set())  # 20 mục đã đúng không bị ghi lại

    def test_budget_batch_three_writes_five_and_every_issue_is_written_once(self):
        rows, issues = self._legacy_scene(45)
        gh = BatchGitHub(rows, issues)
        rounds = [bot.live(gh, gh, "o/r", OWNERS, rewrite=True) for _ in range(3)]
        self.assertEqual(rounds, [(20, 25), (20, 5), (5, 0)])
        self.assertEqual(bot.live(gh, gh, "o/r", OWNERS, rewrite=True), (0, 0))  # hết việc ⇒ không ghi
        patched = [path for method, path, _ in gh.writes if method == "PATCH"]
        self.assertEqual(len(patched), 45)
        self.assertEqual(len(set(patched)), 45)  # không mục nào bị ghi hai lần
        self.assertEqual(len(gh.issues), 45)     # không tạo issue mới
        self.assertTrue(all("Bản app: `0.8.14 (814)`" in item["body"] for item in gh.issues))

    def test_official_cert_matches_published_config(self):
        # Chỉ ĐỌC config (nguồn chuẩn `security.officialCertSha256`), không sửa — chặn lệch giữa bot và config.
        path = pathlib.Path(__file__).resolve().parents[1] / "config" / "monika-config.json"
        config = json.loads(path.read_text(encoding="utf-8-sig"))
        self.assertEqual(bot.OFFICIAL_CERT, config["security"]["officialCertSha256"])
        self.assertRegex(bot.OFFICIAL_CERT, r"^[0-9a-f]{64}$")


if __name__ == "__main__": unittest.main()
