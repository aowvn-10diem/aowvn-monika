#!/usr/bin/env python3
"""V200: test offline cho scripts/gate-expected.py (không gọi mạng)."""
import importlib.util
import re
import sys
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("gate_expected", Path(__file__).resolve().parent / "gate-expected.py")
ge = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = ge
spec.loader.exec_module(ge)

ROOT = Path(__file__).resolve().parent.parent
ALL = ["Build", "Coverage", "CodeQL (V41)"]
BUILD = ["Build", "Coverage"]


class GateExpectedTest(unittest.TestCase):
    def test_docs_only_needs_nothing(self):
        self.assertEqual(ge.expected(["docs/dac-ta/x.md", "docs/assets/a.png", "README.md", "SECURITY.md"]), [])

    def test_empty_list_needs_nothing(self):
        self.assertEqual(ge.expected([]), [])
        self.assertEqual(ge.expected(["", "  "]), [])

    def test_app_code_needs_all(self):
        self.assertEqual(ge.expected(["app/src/main/java/A.kt"]), ALL)

    def test_config_needs_build_not_codeql(self):
        self.assertEqual(ge.expected(["config/monika-config.json"]), BUILD)

    def test_mixed_docs_and_config_needs_build(self):
        self.assertEqual(ge.expected(["docs/CHANGELOG.md", "config/monika-config.json"]), BUILD)

    def test_codeql_paths(self):
        for p in ("cloudflare/worker.js", "scripts/x.py", ".github/workflows/a.yml",
                  ".github/codeql-config.yml", "gradle/libs.versions.toml"):
            self.assertEqual(ge.expected([p]), ALL, p)

    def test_markdown_inside_codeql_path_is_still_doc_for_build(self):
        # scripts/README.md: Build/Coverage bỏ qua (đuôi .md) nhưng CodeQL chạy vì nằm trong scripts/**.
        self.assertEqual(ge.expected(["scripts/README.md"]), ["CodeQL (V41)"])

    def test_rename_from_code_to_docs_counts_old_name(self):
        # app/X.kt đổi thành docs/X.md: gate đưa cả tên cũ lẫn tên mới vào danh sách.
        self.assertEqual(ge.expected(["docs/X.md", "app/X.kt"]), ALL)

    def test_json_lines_keep_odd_filenames(self):
        # Tên tệp có xuống dòng: dạng JSON giữ nguyên là một tên, không tách thành hai mảnh tài liệu.
        names = ge.parse_json_lines(['"a.md\\nx.kt"', '"docs/ok.md"'])
        self.assertEqual(names[0], "a.md\nx.kt")
        self.assertEqual(ge.expected(names), BUILD)

    def test_order_is_stable(self):
        self.assertEqual(ge.expected(["scripts/a.py", "app/B.kt"]), ALL)


class WorkflowDriftTest(unittest.TestCase):
    """Phát hiện lệch giữa gate-expected.py và bộ lọc thật của workflow (đọc chữ, không cần PyYAML)."""

    def read(self, name):
        return (ROOT / ".github" / "workflows" / name).read_text(encoding="utf-8")

    def test_build_and_coverage_filters(self):
        for fname, wf in (("build.yml", "Build"), ("coverage.yml", "Coverage")):
            text = self.read(fname)
            self.assertRegex(text, r"(?m)^name: " + re.escape(wf) + r"\s*$", fname)
            self.assertIn('pull_request:\n    paths-ignore: ["docs/**", "**/*.md"]', text, fname)

    def test_codeql_filters(self):
        text = self.read("codeql.yml")
        self.assertRegex(text, r"(?m)^name: " + re.escape(ge.CODEQL_NAME) + r"\s*$")
        m = re.search(r"pull_request:\n\s+paths: \[(.*?)\]", text)
        self.assertIsNotNone(m, "codeql.yml mất bộ lọc pull_request.paths")
        items = {x.strip().strip("'\"") for x in m.group(1).split(",")}
        want = {p + "**" for p in ge.CODEQL_PREFIXES} | set(ge.CODEQL_FILES)
        self.assertEqual(items, want)


if __name__ == "__main__":
    unittest.main()
