#!/usr/bin/env python3
"""Test offline cho scripts/crash-insight.py — không gọi mạng, dùng payload giả.

Chạy: python3 scripts/test-crash-insight.py
"""
import importlib.util
import json
import os
import sys
import unittest
import urllib.error
import urllib.parse

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
    def test_foreign_cert_rows_are_excluded_from_statistics_by_default(self):
        payload = {"reports": [
            row(cert="c46902e97029a965"),          # chứng thư phát hành
            row(cert="1e08a903aef90000"),          # bản khác chứng thư → loại
            row(cert=""),                          # bản cũ không gửi chứng thư → giữ
        ]}
        markdown, table, scanned, groups = insight.build(payload)
        self.assertEqual(3, scanned)
        self.assertEqual(2, groups)
        self.assertIn("Đã loại khỏi thống kê", markdown)
        self.assertIn("**1 báo cáo**", markdown)
        # Chứng thư lạ không xuất hiện trong bảng, phần đếm chứng thư hay CSV.
        for output in (markdown, table):
            self.assertNotIn("1e08a903aef9", output)
        all_markdown, _, _, all_groups = insight.build(payload, include_foreign=True)
        self.assertEqual(3, all_groups)
        self.assertNotIn("Đã loại khỏi thống kê", all_markdown)

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

    def test_noexit_gets_plain_vietnamese_guess_and_unknown_is_unchanged(self):
        # L21: `noexit` (máy không báo được lý do dừng) là nhóm riêng, đọc được bằng chữ thường.
        self.assertEqual("máy không báo được lý do dừng", insight.guess("noexit", "app:game"))
        self.assertEqual("chưa đủ dữ liệu phân loại", insight.guess("unknown", "app:game"))
        groups = insight.group_rows([row(k="noexit", c="midlet", component="engine:j2me"), row(k="unknown")])
        self.assertEqual(2, len(groups))
        self.assertEqual({"noexit", "unknown"}, {g["kind"] for g in groups})

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
        cell = dict(zip(insight.COLUMNS, insight.table_rows(groups)[0]))
        self.assertEqual("treo tiến trình chính (không có phiên game)", cell["giả thuyết"])

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


class Response:
    def __init__(self, body):
        import io
        self.body = io.BytesIO(body)

    def read(self, _n=-1):
        return self.body.read()

    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False


class Flaky:
    """Giả lập Worker: n lần đầu trả 500 rồi trả JSON."""

    def __init__(self, failures, payload=b'{"reports":[]}'):
        self.failures = failures
        self.payload = payload
        self.calls = 0

    def __call__(self, _request, timeout=None):
        self.calls += 1
        if self.calls <= self.failures:
            raise urllib.error.HTTPError("https://x.workers.dev/reports", 500, "Internal", {}, None)
        return Response(self.payload)


class Retry(unittest.TestCase):
    def fetch(self, opener):
        return insight.fetch_listing("https://x.workers.dev", "token", opener=opener, sleep=lambda _s: None)

    def test_server_error_is_retried_until_it_succeeds(self):
        flaky = Flaky(failures=2)
        self.assertEqual({"reports": []}, self.fetch(flaky))
        self.assertEqual(3, flaky.calls)

    def test_permanent_server_error_stops_after_three_attempts(self):
        flaky = Flaky(failures=99)
        with self.assertRaises(ValueError) as error:
            self.fetch(flaky)
        self.assertIn("HTTP 500", str(error.exception))
        self.assertEqual(insight.ATTEMPTS, flaky.calls)

    def test_client_error_is_not_retried(self):
        calls = []

        def unauthorized(_request, timeout=None):
            calls.append(1)
            raise urllib.error.HTTPError("https://x.workers.dev/reports", 401, "Unauthorized", {}, None)

        with self.assertRaises(ValueError) as error:
            self.fetch(unauthorized)
        self.assertIn("HTTP 401", str(error.exception))
        self.assertEqual(1, len(calls))

    def test_network_failure_is_retried_then_reported(self):
        calls = []

        def offline(_request, timeout=None):
            calls.append(1)
            raise urllib.error.URLError("down")

        with self.assertRaises(ValueError) as error:
            self.fetch(offline)
        self.assertIn("cannot reach Worker", str(error.exception))
        self.assertEqual(insight.ATTEMPTS, len(calls))
class Certificate(unittest.TestCase):
    """V155b: cột + bảng đếm theo chứng thư để nhóm và đối chiếu thủ công với chứng thư bản phát hành."""

    CERT_A = "a1b2c3d4e5f60718" + "a" * 48
    CERT_B = "0011223344556677" + "b" * 48

    def test_certificate_caveat_is_stated_in_table(self):
        # Luna 5: cert do client gửi — chỉ để đối chiếu, KHÔNG phải bằng chứng xác thực / không kết luận "bản giả".
        markdown = insight.render_markdown(insight.group_rows([row(cert=self.CERT_A), row(cert=self.CERT_B, fp="bbbbbbbbbbbb2222")]), 2)
        self.assertIn("bằng chứng xác thực", markdown)
        self.assertIn("client gửi lên", markdown)
        self.assertIn("kết luận đây là bản giả", markdown)
        self.assertNotIn("để phát hiện bản giả", markdown)

    def test_group_criteria_line_matches_group_rows(self):        # Luna 5: mô tả "số nhóm" phải khớp tiêu chí gộp thật (5 phần, có thành phần và chứng thư).
        markdown = insight.render_markdown(insight.group_rows([row()]), 1)
        line = next(l for l in markdown.splitlines() if l.startswith("Số dòng nhận được"))
        for part in ("lõi", "loại", "chữ ký", "thành phần", "chứng thư"):
            self.assertIn(part, line)
        self.assertIn("client gửi lên", markdown)

    def test_different_component_is_a_separate_group(self):
        groups = insight.group_rows([row(component="op:giai-nen"), row(component="app:chính")])
        self.assertEqual(2, len(groups))

    def cell(self, group, column):
        return dict(zip(insight.COLUMNS, insight.table_rows([group])[0]))[column]

    def test_certificate_is_a_column_and_separates_groups(self):
        groups = insight.group_rows([row(cert=self.CERT_A), row(cert=self.CERT_B)])
        self.assertEqual(2, len(groups))
        self.assertEqual(sorted([self.CERT_A[:12], self.CERT_B[:12]]), sorted(self.cell(g, "chứng thư") for g in groups))

    def test_certificate_counts_block_sums_reports_and_uses(self):
        groups = insight.group_rows([
            row(cert=self.CERT_A, count=3), row(cert=self.CERT_A, count=4),
            row(cert=self.CERT_B, count=1, fp="bbbbbbbbbbbb2222"),
        ])
        markdown = insight.render_markdown(groups, 3)
        self.assertIn("Đếm theo chứng thư", markdown)
        self.assertIn("| %s | 2 | 7 |" % self.CERT_A[:12], markdown)
        self.assertIn("| %s | 1 | 1 |" % self.CERT_B[:12], markdown)
        self.assertIn("2 chứng thư khác nhau", markdown)

    def test_foreign_certificate_is_summarised_not_counted(self):
        # V205: báo cáo chứng thư lạ chỉ còn dòng tổng "đã loại", không vào bảng đếm chứng thư.
        payload = {"reports": [row(cert="c46902e97029a965", count=2), row(cert=self.CERT_B, count=5)]}
        markdown, _, _, group_count = insight.build(payload)
        self.assertEqual(1, group_count)
        self.assertIn("**1 báo cáo** · **5 lượt**", markdown)
        self.assertIn("| c46902e97029 | 1 | 2 |", markdown)
        self.assertNotIn(self.CERT_B[:12], markdown)

    def test_single_certificate_has_no_warning(self):
        markdown = insight.render_markdown(insight.group_rows([row(cert=self.CERT_A)]), 1)
        self.assertNotIn("chứng thư khác nhau", markdown)

    def test_missing_certificate_is_dash_and_reported_in_footer(self):
        markdown = insight.render_markdown(insight.group_rows([row()]), 1)
        self.assertEqual(insight.MISSING, self.cell(insight.group_rows([row()])[0], "chứng thư"))
        self.assertIn("Thiếu trường:", markdown)
        self.assertIn("chứng thư", markdown)
        self.assertIn("`cert`", markdown)

    def test_csv_carries_certificate_column(self):
        table = insight.render_csv(insight.group_rows([row(cert=self.CERT_A)]))
        self.assertIn("chứng thư", table.splitlines()[0])
        self.assertIn(self.CERT_A[:12], table.splitlines()[1])

    def test_non_hex_certificate_is_not_echoed(self):
        poisoned = "<script>alert(1)</script>"
        markdown, table, _, _ = insight.build({"reports": [row(cert=poisoned)]})
        for output in (markdown, table):
            self.assertNotIn("script", output)
            self.assertNotIn("alert", output)


BUILD_ID = "8f3c2d1e0a9b7c6d5e4f30211223344556677889"


def detail_body(*lines, kind="native"):
    """Body `/reports/<id>`: đúng dạng app gửi (`detail` là chuỗi tombstone/backtrace)."""
    return json.dumps({"kind": kind, "app": "0.8.8 (128)", "detail": "\n".join(lines)}).encode("utf-8")


class Router:
    """Giả lập Worker cho `/reports/<id>`: trả body theo id, hoặc ném HTTPError theo mã lỗi."""

    def __init__(self, bodies):
        self.bodies = bodies
        self.calls = []

    def __call__(self, request, timeout=None):
        url = request.full_url
        self.calls.append(url)
        report_id = urllib.parse.unquote(url.rsplit("/", 1)[-1])
        value = self.bodies[report_id]
        if isinstance(value, int):
            raise urllib.error.HTTPError(url, value, "error", {}, None)
        if isinstance(value, Exception):
            raise value
        return Response(value)


class Details(unittest.TestCase):
    """V174+: mục `## Khung đầu của top nhóm` đọc `/reports/<id>` — offline, không gọi mạng."""

    BASE = "https://x.workers.dev"
    TOKEN = "token-bi-mat-khong-duoc-lo"

    def listing(self):
        return {"reports": [
            row(id="r:1:aaa1", k="anr", c="melondsds", fp="bbbbbbbbbbbb2222", count=421),
            row(id="r:2:aaa2", k="resource", c="unknown", fp="cccccccccccc3333", count=236),
            row(id="r:3:aaa3", k="native", c="ppsspp", fp="aaaaaaaaaaaa1111", count=206),
        ]}

    def app_frames(self, count=10):
        lines = ["SIGSEGV (SEGV_MAPERR) địa chỉ lỗi 0x0", "luồng gây lỗi: RenderThread (tid 4242)"]
        lines += ["#%02d libppsspp.so+0x%x Core_Run+%d [build_id %s]" % (i, 0x1a2b3 + i, i, BUILD_ID)
                  for i in range(count)]
        return lines

    def collect(self, bodies, limit=3):
        groups = insight.group_rows(self.listing()["reports"])
        router = Router(bodies)
        entries = insight.collect_details(groups, limit, self.BASE, self.TOKEN, opener=router, sleep=lambda _s: None)
        return entries, router

    def test_frames_are_trimmed_to_eight_and_carry_lib_build_id_signal(self):
        entries, router = self.collect({"r:1:aaa1": detail_body(*self.app_frames(10))}, limit=1)
        entry = entries[0]
        self.assertEqual(insight.DETAIL_FRAMES, len(entry["frames"]))
        self.assertEqual("libppsspp.so", entry["lib"])
        self.assertEqual(BUILD_ID, entry["build_id"])
        self.assertEqual("SIGSEGV", entry["signal"])
        self.assertEqual(1, len(router.calls))
        self.assertIn("r%3A1%3Aaaa1", router.calls[0])

        markdown = insight.render_markdown(
            insight.group_rows(self.listing()["reports"]), 3, details=entries)
        self.assertIn("## Khung đầu của top nhóm (1)", markdown)
        self.assertIn(BUILD_ID, markdown)
        self.assertIn("libppsspp.so", markdown)
        self.assertIn("Core_Run", markdown)
        self.assertEqual(insight.DETAIL_FRAMES, markdown.count("[build_id %s]" % BUILD_ID))
        self.assertNotIn(self.TOKEN, markdown)

    def test_raw_tombstone_paths_are_cut_to_file_names(self):
        entries, _ = self.collect({"r:1:aaa1": detail_body(
            "#00 pc 00000000001a2b3c /data/app/~~xyz==/vn.aow.monika-1/lib/arm64/libmelondsds.so (BuildId: %s)" % BUILD_ID,
            "#01 pc 00000000001a2b3d /storage/emulated/0/Android/data/%s/files/libfoo.so (BuildId: 0011223344556677)" % GAME_NAME,
            kind="anr",
        )}, limit=1)
        markdown = insight.render_markdown(insight.group_rows(self.listing()["reports"]), 3, details=entries)
        self.assertIn("libmelondsds.so", markdown)
        self.assertIn("libfoo.so", markdown)
        for leak in ("/data/app", "emulated", "Android/data", "~~", GAME_NAME, "vn.aow.monika-1/lib"):
            self.assertNotIn(leak, markdown)

    def test_anr_trace_native_prefix_is_understood(self):
        # ANR của Android 12+ ghi khung native kèm tiền tố `native:`; phải nhận, không bỏ cả khối.
        entries, _ = self.collect({"r:1:aaa1": detail_body(
            "----- pid 4321 at 2026-11-16 10:00:00 -----",
            "Cmd line: vn.aow.monika",
            "  native: #00 pc 0000000000055d1c  /apex/com.android.runtime/lib64/bionic/libc.so (abort+164) (BuildId: %s)" % BUILD_ID,
            "  native: #01 pc 0000000000055d20  /data/app/~~Q1z9==/vn.aow.monika-1/lib/arm64/libmelondsds.so (EmuThreadMain+88) (BuildId: 00112233445566778899aabbccddeeff00112233)",
            kind="anr",
        )}, limit=1)
        entry = entries[0]
        self.assertEqual("libc.so", entry["lib"])
        self.assertEqual(BUILD_ID, entry["build_id"])
        self.assertEqual(["#00", "#01"], [frame["text"][:3] for frame in entry["frames"]])
        self.assertIn("libmelondsds.so", entry["frames"][1]["text"])
        self.assertNotIn("/data/app", entry["frames"][1]["text"])

    def test_report_without_frames_says_so_instead_of_inventing(self):
        entries, _ = self.collect({"r:1:aaa1": detail_body("ANR in vn.aow.monika (chưa có ngăn xếp)", kind="anr")}, limit=1)
        self.assertEqual([], entries[0]["frames"])
        self.assertFalse(entries[0]["error"])
        markdown = insight.render_markdown(insight.group_rows(self.listing()["reports"]), 3, details=entries)
        self.assertIn("không có khung trong báo cáo", markdown)

    def test_one_group_failing_does_not_stop_the_others(self):
        entries, _ = self.collect({
            "r:1:aaa1": 403,                      # token thiếu quyền cho báo cáo này
            "r:2:aaa2": 500,                      # Worker lỗi tạm thời (có thử lại, vẫn hỏng)
            "r:3:aaa3": detail_body(*self.app_frames(3)),
        })
        self.assertEqual(3, len(entries))
        # Thứ tự đọc: anr (403) → native (có khung) → resource (500).
        self.assertEqual([True, False, True], [entry["error"] for entry in entries])
        markdown = insight.render_markdown(insight.group_rows(self.listing()["reports"]), 3, details=entries)
        rows_with_error = [line for line in markdown.splitlines()
                           if line.startswith("| ") and "lỗi đọc chi tiết" in line]
        self.assertEqual(2, len(rows_with_error))
        self.assertIn("libppsspp.so", markdown)       # nhóm thứ ba vẫn có khung
        self.assertIn("## Khung đầu của top nhóm (3)", markdown)

    def test_details_zero_never_calls_network_and_output_is_unchanged(self):
        payload = self.listing()
        expected = insight.build(payload)[0]
        calls = []

        def boom(_request, timeout=None):
            calls.append(1)
            raise AssertionError("không được gọi mạng khi --details 0")

        markdown, table, scanned, groups = insight.build(
            payload, False, details=0, base=self.BASE, token=self.TOKEN, opener=boom)
        self.assertEqual(expected, markdown)
        self.assertEqual([], calls)
        self.assertNotIn("Khung đầu của top nhóm", markdown)
        self.assertIn("không gọi chi tiết từng báo cáo", markdown)
        self.assertEqual(12, insight.DETAIL_LIMIT)     # workflow không truyền cờ → mặc định 12

    def test_native_and_anr_groups_are_read_first(self):
        groups = insight.group_rows(self.listing()["reports"])   # đã xếp theo số lượt: anr 421, resource 236, native 206
        labels = [group["kind"] for group in insight.detail_targets(groups, 2)]
        self.assertEqual(["anr", "native"], labels)
        self.assertEqual([], insight.detail_targets(groups, 0))

    def test_missing_report_id_is_an_error_not_a_crash(self):
        groups = insight.group_rows([row(id="", k="native", c="ppsspp")])
        entries = insight.collect_details(groups, 1, self.BASE, self.TOKEN,
                                          opener=lambda *_a, **_k: self.fail("không có id thì không gọi"), sleep=lambda _s: None)
        self.assertTrue(entries[0]["error"])


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
