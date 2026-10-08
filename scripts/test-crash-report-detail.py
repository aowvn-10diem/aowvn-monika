#!/usr/bin/env python3
"""Offline tests for report formatting and no-network missing-mapping handling."""
import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("crash-report-detail.py")
SPEC = importlib.util.spec_from_file_location("crash_report_detail_under_test", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CrashReportDetailOfflineTest(unittest.TestCase):
    def test_formats_synthetic_unknown_report_without_external_calls(self):
        report = {
            "kind": "unknown", "title": "fixture report", "device": "synthetic device",
            "app": "0.0.0 (0) debug", "session": {"core": "fixture", "game": "fake"},
            "reason": "synthetic reason", "detail": "no real crash data", "log": ["line one"],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            path.write_text(json.dumps(report), encoding="utf-8")
            output = io.StringIO()
            with mock.patch.object(sys, "argv", [str(SCRIPT), "--file", str(path)]), \
                    mock.patch.object(MODULE.shutil, "which", side_effect=AssertionError("external command lookup")), \
                    mock.patch.object(MODULE.subprocess, "run", side_effect=AssertionError("external process")), \
                    contextlib.redirect_stdout(output):
                MODULE.main()
        text = output.getvalue()
        self.assertIn("fixture report", text)
        self.assertIn("synthetic device", text)
        self.assertIn("synthetic reason", text)
        self.assertIn("line one", text)

    def test_java_report_without_local_mapping_only_marks_unverified(self):
        report = {"kind": "java", "app": "0.7.6 (41) release", "detail": "synthetic stack"}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            path.write_text(json.dumps(report), encoding="utf-8")
            output = io.StringIO()
            with mock.patch.object(sys, "argv", [str(SCRIPT), "--file", str(path)]), \
                    mock.patch.object(MODULE.shutil, "which", return_value=None), \
                    mock.patch.object(MODULE.subprocess, "run", side_effect=AssertionError("must not invoke gh/java")), \
                    contextlib.redirect_stdout(output):
                MODULE.main()
        self.assertIn("[CHƯA KIỂM]", output.getvalue())
        self.assertIn("dùng --mapping", output.getvalue())


if __name__ == "__main__":
    unittest.main()
