#!/usr/bin/env python3
"""Test scripts/kiem-phu-thuoc.py — không gọi mạng, tiêm post/get giả."""

from __future__ import annotations

import json
import os
import sys
import unittest

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
        details = {
            "pkg:maven/a/b@1.0": ["GHSA-3"],
            "_meta": {"GHSA-3": {"severity": [
                {"type": "CVSS_V3", "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H/9.8"}]}},
        }
        rows, _ = self._run(details)
        self.assertEqual(rows[0][3], "CRITICAL")

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
