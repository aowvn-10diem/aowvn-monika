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

    def _run_native(self, build_id_in_index):
        """Chạy main() trên báo cáo native có khung đúng dạng app gửi (TombstoneParser.toLines)."""
        detail_build_id = "abc123def456"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "libs" / "x").mkdir(parents=True)
            (root / "libs" / "x" / "libcitra_libretro_android.so").write_bytes(b"\0")
            (root / "build-ids.tsv").write_text(
                "build_id\tfile\tabi\tsymbols\tlevel\n"
                f"{build_id_in_index}\tlibcitra_libretro_android.so\tarm64-v8a\t"
                "libs/x/libcitra_libretro_android.so\tsymtab\n", encoding="utf-8")
            path = root / "report.json"
            path.write_text(json.dumps({
                "kind": "native", "title": "Lõi giả lập bị sập", "app": "0.8.13 (99) release",
                "detail": "#00 libcitra_libretro_android.so+0x3b6297 unknown+0 [build_id "
                          + detail_build_id + "]",
            }), encoding="utf-8")
            output = io.StringIO()
            done = mock.Mock(returncode=0, stdout="emu.cpp:120:5")
            with mock.patch.object(sys, "argv", [str(SCRIPT), "--file", str(path),
                    "--symbols-dir", str(root), "--symbolizer", "llvm-symbolizer"]), \
                    mock.patch.object(MODULE.subprocess, "run", return_value=done) as run, \
                    contextlib.redirect_stdout(output):
                MODULE.main()
        return output.getvalue(), run

    def test_app_tombstone_frame_is_symbolized(self):
        """Khung `tên.so+0x<pc> … [build_id …]` (dạng detail thật của app) phải được giải ký hiệu."""
        text, run = self._run_native("abc123def456")
        self.assertIn("-- Native đã giải ký hiệu --", text)
        self.assertIn("emu.cpp:120:5", text)
        self.assertIn("rel_pc", text)
        self.assertNotIn("Chưa giải được khung native", text)
        self.assertEqual(run.call_args.args[0][-1], "0x3b6297")

    def test_app_tombstone_frame_with_other_build_id_only_notes_missing_symbols(self):
        """BuildId lệch bảng ký hiệu thì chỉ ghi [CHƯA KIỂM], không dùng ký hiệu của bản khác."""
        text, run = self._run_native("999999999999")
        self.assertIn("Không có ký hiệu duy nhất cho libcitra_libretro_android.so, BuildId abc123def456.", text)
        self.assertNotIn("-- Native đã giải ký hiệu --", text)
        run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
