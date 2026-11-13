#!/usr/bin/env python3
"""Test scripts/kiem-phu-thuoc.py — không gọi mạng, tiêm post/get giả."""

from __future__ import annotations

import json
import os
import sys
import unittest
import urllib.error

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import importlib.util

_spec = importlib.util.spec_from_file_location(
    "kiem_phu_thuoc",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "kiem-phu-thuoc.py"),
)
kpt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(kpt)


def _sbom(*packages):
    return {"sbom": {"packages": list(packages)}}


def _pkg(name, version, purl=None, extra_refs=None):
    refs = [{"referenceLocator": purl or f"pkg:maven/{name.replace(':', '/')}@{version}"}]
    if extra_refs:
        refs = extra_refs + refs
    return {"name": name, "versionInfo": version, "externalRefs": refs}


class MavenComponentsTest(unittest.TestCase):
    def test_reads_maven_packages_only(self):
        sbom = _sbom(
            _pkg("io.netty:netty-handler", "4.1.93.Final"),
            {"name": "npm-thing", "versionInfo": "1.0.0",
             "externalRefs": [{"referenceLocator": "pkg:npm/thing@1.0.0"}]},
        )
        components = kpt.maven_components(sbom)
        self.assertEqual(components, [
            ("io.netty:netty-handler", "4.1.93.Final",
             "pkg:maven/io.netty/netty-handler@4.1.93.Final"),
        ])

    def test_picks_maven_ref_when_other_refs_come_first(self):
        sbom = _sbom(_pkg("a:b", "1.0", extra_refs=[
            {"referenceLocator": "pkg:github/a/b@1.0"},
        ]))
        self.assertEqual(len(kpt.maven_components(sbom)), 1)

    def test_deduplicates_same_purl_and_skips_missing_version(self):
        sbom = _sbom(
            _pkg("a:b", "1.0"),
            _pkg("a:b", "1.0"),
            {"name": "c:d", "externalRefs": [
                {"referenceLocator": "pkg:maven/c/d@2.0"}]},
        )
        self.assertEqual(len(kpt.maven_components(sbom)), 1)

    def test_empty_sbom(self):
        self.assertEqual(kpt.maven_components({}), [])


class QueryOsvTest(unittest.TestCase):
    def _run(self, details, components=None):
        calls = {"batch": 0, "detail": []}

        def post(url, payload):
            calls["batch"] += 1
            return {"results": [
                {"vulns": [{"id": vid} for vid in details.get(purl, [])]}
                for purl in (q["package"]["purl"] for q in payload["queries"])
            ]}

        def get(url):
            vid = url.rsplit("/", 1)[-1]
            calls["detail"].append(vid)
            return details["_meta"][vid]

        comps = components or [("a:b", "1.0", "pkg:maven/a/b@1.0")]
        return kpt.query_osv(comps, post=post, get=get), calls

    def test_severity_from_database_specific_and_fix_version(self):
        details = {
            "pkg:maven/a/b@1.0": ["GHSA-1", "GHSA-2"],
            "_meta": {
                "GHSA-1": {"database_specific": {"severity": "HIGH"},
                           "affected": [{"package": {"ecosystem": "Maven"},
                                         "ranges": [{"events": [{"fixed": "2.0"}]}]}]},
                "GHSA-2": {"database_specific": {"severity": "CRITICAL"},
                           "affected": [{"package": {"ecosystem": "Maven"},
                                         "ranges": [{"events": [{"introduced": "0"}]}]}]},
            },
        }
        rows, _ = self._run(details)
        self.assertEqual([r[2] for r in rows], ["GHSA-2", "GHSA-1"])
        self.assertEqual(rows[0][4], "")
        self.assertEqual(rows[1][4], "2.0")

    def test_severity_from_cvss_when_label_missing(self):
        # Vector thật của GHSA-j288-q9x7-2f5v: OSV trả vector trần, không kèm
        # "/9.8", nên phải tự tính điểm (6.4 → MODERATE).
        details = {
            "pkg:maven/a/b@1.0": ["GHSA-3"],
            "_meta": {"GHSA-3": {"severity": [
                {"type": "CVSS_V3",
                 "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N"}]}},
        }
        rows, _ = self._run(details)
        self.assertEqual(rows[0][3], "MODERATE")

    def test_severity_from_cvss_v4_when_label_missing(self):
        details = {
            "pkg:maven/a/b@1.0": ["GHSA-4"],
            "_meta": {"GHSA-4": {"severity": [
                {"type": "CVSS_V4",
                 "score": "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/"
                          "VC:H/VI:H/VA:H/SC:N/SI:N/SA:N"}]}},
        }
        rows, _ = self._run(details)
        self.assertEqual(rows[0][3], "CRITICAL")

    def test_short_results_stops_instead_of_false_negative(self):
        components = [
            ("a:b", "1.0", "pkg:maven/a/b@1.0"),
            ("c:d", "2.0", "pkg:maven/c/d@2.0"),
        ]

        def post(url, payload):
            return {"results": [{}]}  # thiếu 1 kết quả

        with self.assertRaises(kpt.OsvError):
            kpt.query_osv(components, post=post, get=lambda url: {})

    def test_no_vulns_gives_no_rows(self):
        rows, calls = self._run({"pkg:maven/a/b@1.0": [], "_meta": {}})
        self.assertEqual(rows, [])
        self.assertEqual(calls["batch"], 1)
        self.assertEqual(calls["detail"], [])

    def test_batches_over_thousand_components(self):
        comps = [(f"g{i}:a", "1.0", f"pkg:maven/g{i}/a@1.0") for i in range(1001)]

        def post(url, payload):
            self.assertLessEqual(len(payload["queries"]), kpt.BATCH)
            return {"results": [{} for _ in payload["queries"]]}

        rows = kpt.query_osv(comps, post=post, get=lambda url: {})
        self.assertEqual(rows, [])


class SummarizeTest(unittest.TestCase):
    def test_counts_alerts_combos_and_codes(self):
        rows = [
            ("a:b", "1.0", "GHSA-1", "HIGH", "2.0"),
            ("a:b", "1.0", "GHSA-2", "HIGH", "2.0"),
            ("c:d", "3.0", "GHSA-1", "MODERATE", ""),
        ]
        text = kpt.summarize(rows)
        self.assertIn("3 cảnh báo / 2 tổ hợp / 2 mã", text)
        self.assertIn("2 high", text)
        self.assertIn("1 moderate", text)

    def test_unknown_severity_still_counted(self):
        text = kpt.summarize([("a:b", "1.0", "X", "UNKNOWN", "")])
        self.assertIn("1 unknown", text)


class CvssTest(unittest.TestCase):
    def test_v31_vector_without_trailing_score(self):
        score = kpt._cvss_score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H")
        self.assertEqual(score, 9.8)

    def test_v31_scope_changed(self):
        score = kpt._cvss_score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H")
        self.assertEqual(score, 10.0)

    def test_v30_vector_accepted(self):
        score = kpt._cvss_score("CVSS:3.0/AV:L/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N")
        self.assertEqual(score, 1.8)

    def test_explicit_trailing_score_still_used(self):
        score = kpt._cvss_score("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H/9.8")
        self.assertEqual(score, 9.8)

    def test_v4_vector_mapped(self):
        score = kpt._cvss_score(
            "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N")
        self.assertGreaterEqual(score, 9.0)

    def test_unreadable_vectors_return_minus_one(self):
        for vector in ("", "9.8", "AV:N/AC:L", "CVSS:3.1/AV:X/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"):
            self.assertEqual(kpt._cvss_score(vector), -1.0)


class NetworkTest(unittest.TestCase):
    def test_url_error_becomes_osv_error(self):
        import unittest.mock as mock
        with mock.patch.object(kpt.urllib.request, "urlopen",
                               side_effect=urllib.error.URLError("mất mạng")), \
                mock.patch.object(kpt.time, "sleep"):
            with self.assertRaises(kpt.OsvError):
                kpt._get_json("https://api.osv.dev/v1/vulns/X")

    def test_429_is_retried(self):
        import unittest.mock as mock

        class _Resp:
            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

            def read(self):
                return b'{"ok": true}'

        calls = {"n": 0}

        def fake_urlopen(request, timeout):
            calls["n"] += 1
            if calls["n"] == 1:
                raise urllib.error.HTTPError("u", 429, "too many", {}, None)
            return _Resp()

        with mock.patch.object(kpt.urllib.request, "urlopen", side_effect=fake_urlopen), \
                mock.patch.object(kpt.time, "sleep"):
            self.assertEqual(kpt._get_json("https://api.osv.dev/v1/vulns/X"), {"ok": True})
        self.assertEqual(calls["n"], 2)

    def test_403_not_retried(self):
        import unittest.mock as mock
        with mock.patch.object(kpt.urllib.request, "urlopen",
                               side_effect=urllib.error.HTTPError("u", 403, "forbidden", {}, None)):
            with self.assertRaises(kpt.OsvError):
                kpt._get_json("https://api.osv.dev/v1/vulns/X")


class MainTest(unittest.TestCase):
    def test_bad_sbom_returns_2(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            bad = os.path.join(tmp, "sbom.json")
            with open(bad, "w", encoding="utf-8") as fh:
                fh.write("{not json")
            self.assertEqual(kpt.main([bad]), 2)

    def test_missing_file_returns_2(self):
        self.assertEqual(kpt.main([os.path.join(os.sep, "khong-co.json")]), 2)

    def test_utf16_sbom_is_readable(self):
        import tempfile
        import unittest.mock as mock
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "sbom.json")
            with open(path, "w", encoding="utf-16") as fh:
                json.dump(_sbom(_pkg("a:b", "1.0")), fh)
            with mock.patch.object(kpt, "query_osv", return_value=[]) as q:
                self.assertEqual(kpt.main([path]), 0)
            self.assertEqual(len(q.call_args[0][0]), 1)

    def test_missing_codes_file_returns_2(self):
        import tempfile
        import unittest.mock as mock
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "sbom.json")
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(_sbom(_pkg("a:b", "1.0")), fh)
            missing = os.path.join(tmp, "khong-co.txt")
            with mock.patch.object(kpt, "query_osv") as q:
                self.assertEqual(kpt.main([path, "--codes", missing]), 2)
            q.assert_not_called()

    def test_osv_error_returns_3(self):
        import tempfile
        import unittest.mock as mock
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "sbom.json")
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(_sbom(_pkg("a:b", "1.0")), fh)
            with mock.patch.object(kpt, "query_osv", side_effect=kpt.OsvError("hỏng")):
                self.assertEqual(kpt.main([path]), 3)

    def test_codes_report_lists_new_and_gone(self):
        import tempfile
        import unittest.mock as mock
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "sbom.json")
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(_sbom(_pkg("a:b", "1.0")), fh)
            codes = os.path.join(tmp, "codes.txt")
            with open(codes, "w", encoding="utf-8") as fh:
                fh.write("GHSA-GONE\n")
            rows = [("a:b", "1.0", "GHSA-NEW", "HIGH", "2.0")]
            with mock.patch.object(kpt, "query_osv", return_value=rows):
                self.assertEqual(kpt.main([path, "--codes", codes]), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
