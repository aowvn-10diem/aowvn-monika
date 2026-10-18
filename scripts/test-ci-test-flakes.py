#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("ci_test_flakes", Path(__file__).with_name("ci-test-flakes.py"))
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


SHA_A = "a" * 40
SHA_B = "b" * 40


class CiTestFlakesTest(unittest.TestCase):
    def test_parses_gradle_case_and_summary_without_retaining_log_text(self):
        parsed = ci.parse_log(
            "2026-10-18T00:00:00Z ExampleTest > transient FAILED\n"
            "12 tests completed, 1 failed, 2 skipped\n"
        )
        self.assertEqual(parsed["failed_cases"], ["ExampleTest.transient"])
        self.assertEqual(parsed["summaries"], [{"total": 12, "failed": 1, "skipped": 2}])

    def test_calculates_only_same_head_retry_rate(self):
        rows = [
            {"run_id": 1, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.transient"],
             "summaries": [{"total": 3, "failed": 1, "skipped": 0}]},
            {"run_id": 1, "attempt": 2, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "success", "failed_cases": [], "summaries": []},
            {"run_id": 2, "attempt": 1, "workflow": "Build", "head_sha": SHA_B,
             "tests_ran": True, "tests_conclusion": "success", "failed_cases": [], "summaries": []},
        ]
        result = ci.analyze(rows)
        self.assertEqual(result["candidate_count"], 1)
        self.assertEqual(result["tests"][0]["failed"], 1)
        self.assertEqual(result["tests"][0]["passed"], 1)
        self.assertEqual(result["tests"][0]["same_head_rate"], 0.5)
        self.assertEqual(result["tests"][0]["heads"], 1)

    def test_all_red_on_same_head_is_not_called_random(self):
        rows = [
            {"run_id": 1, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.broken"],
             "summaries": [{"total": 3, "failed": 1, "skipped": 0}]},
            {"run_id": 2, "attempt": 1, "workflow": "Coverage", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.broken"],
             "summaries": [{"total": 3, "failed": 1, "skipped": 0}]},
        ]
        result = ci.analyze(rows)
        self.assertEqual(result["tests"][0]["same_head_rate"], 1.0)
        self.assertFalse(result["tests"][0]["candidate_over_threshold"])

    def test_pretest_and_unclassified_failures_do_not_enter_case_denominator(self):
        rows = [
            {"run_id": 1, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.transient"],
             "summaries": [{"total": 3, "failed": 1, "skipped": 0}]},
            {"run_id": 2, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": [], "summaries": []},
            {"run_id": 3, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": False, "tests_conclusion": "failure", "failed_cases": [], "summaries": []},
        ]
        result = ci.analyze(rows)
        self.assertEqual(result["unclassified_failed_suites"], 1)
        self.assertEqual(result["tests"][0]["attempts"], 1)

    def test_incomplete_failure_names_do_not_become_false_passes(self):
        rows = [
            {"run_id": 1, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.a"],
             "summaries": [{"total": 4, "failed": 1, "skipped": 0}]},
            {"run_id": 2, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.a"],
             "summaries": [{"total": 4, "failed": 2, "skipped": 0}]},
            {"run_id": 3, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "success", "failed_cases": [], "summaries": []},
        ]
        result = ci.analyze(rows)
        self.assertEqual(result["unclassified_failed_suites"], 1)
        self.assertEqual(result["tests"][0]["failed"], 1)
        self.assertEqual(result["tests"][0]["passed"], 1)
        self.assertEqual(result["tests"][0]["attempts"], 2)

    def test_reads_zip_logs(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            archive = Path(temp_dir) / "actions.zip"
            import zipfile
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("job.txt", "ZipTest > broken FAILED\n4 tests completed, 1 failed, 0 skipped\n")
            parsed = ci.parse_log(ci.read_log(archive))
        self.assertEqual(parsed["failed_cases"], ["ZipTest.broken"])

    def test_load_manifest_resolves_relative_log_paths(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "failed.txt").write_text(
                "SampleTest > flaky FAILED\n5 tests completed, 1 failed, 0 skipped\n",
                encoding="utf-8",
            )
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps({"executions": [{
                "run_id": 7,
                "attempt": 1,
                "workflow": "Build",
                "head_sha": SHA_A,
                "tests_ran": True,
                "tests_conclusion": "failure",
                "log_path": "failed.txt",
            }]}), encoding="utf-8")
            loaded = ci.load_manifest(manifest)
        self.assertEqual(loaded[0]["failed_cases"], ["SampleTest.flaky"])
        self.assertEqual(loaded[0]["summaries"][0]["failed"], 1)

    def test_rate_threshold_is_strictly_greater_than_threshold(self):
        rows = [
            {"run_id": 1, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "failure", "failed_cases": ["ExampleTest.transient"],
             "summaries": [{"total": 3, "failed": 1, "skipped": 0}]},
            {"run_id": 2, "attempt": 1, "workflow": "Build", "head_sha": SHA_A,
             "tests_ran": True, "tests_conclusion": "success", "failed_cases": [], "summaries": []},
        ]
        result = ci.analyze(rows, threshold=0.5)
        self.assertEqual(result["tests"][0]["same_head_rate"], 0.5)
        self.assertFalse(result["tests"][0]["candidate_over_threshold"])


if __name__ == "__main__":
    unittest.main()
