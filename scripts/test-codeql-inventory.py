#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("inventory", Path(__file__).with_name("codeql-inventory.py"))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)


class InventoryTest(unittest.TestCase):
    def alert(self, number):
        return {"number": number, "rule": {"id": "java/path-injection", "security_severity_level": "high"},
                "most_recent_instance": {"location": {"path": "app/A.kt", "start_line": 12},
                                         "message": {"text": "token=do-not-copy"}},
                "body": "source must not be copied"}

    def test_only_metadata_for_tracked_source(self):
        result = m.summarize(self.alert(1), {"app/A.kt"})
        self.assertEqual(result["path"], "app/A.kt")
        self.assertEqual(result["line"], 12)
        self.assertNotIn("token", str(result))
        self.assertNotIn("body", result)
        alert = self.alert(1)
        alert["most_recent_instance"]["location"]["path"] = "/home/private/file"
        self.assertIsNone(m.summarize(alert, set())["path"])

    def test_pagination_and_duplicate_numbers(self):
        calls = []
        def page(n):
            calls.append(n)
            return [self.alert(i) for i in range(1, 101)] if n == 1 else [self.alert(100), self.alert(101)]
        self.assertEqual(len(m.collect(page, {"app/A.kt"})), 101)
        self.assertEqual(calls, [1, 2])

    def test_budget_failure_does_not_claim_complete_count(self):
        with self.assertRaises(RuntimeError):
            m.collect(lambda n: [self.alert(1)] * 100, set())


if __name__ == "__main__": unittest.main()
