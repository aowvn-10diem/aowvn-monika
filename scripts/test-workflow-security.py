#!/usr/bin/env python3
"""A85: kiểm tĩnh bảo mật toàn bộ workflow GitHub Actions (chỉ dùng thư viện chuẩn Python)."""
from pathlib import Path
import re
import unittest

try:
    import yaml  # type: ignore
except ImportError:  # Windows / setup-python trần không cài PyYAML
    yaml = None

WORKFLOWS_DIR = Path(__file__).resolve().parents[1] / ".github/workflows"

UNTRUSTED_RUN_EXPR = re.compile(
    r"\$\{\{\s*(?:"
    r"inputs\.|"
    r"github\.event\.(?:inputs|comment|issue|pull_request\.title|pull_request\.body|pull_request\.head\.ref|head_commit\.message)|"
    r"github\.head_ref"
    r")"
)

THIRD_PARTY_PREFIXES = (
    "softprops/",
    "google-github-actions/",
    "ruby/setup-ruby",
    "reactivecircus/",
)


def extract_run_blocks(text: str) -> list[tuple[int, str]]:
    """Trích toàn bộ giá trị `run:` (cả 1 dòng lẫn khối `|` / `>`) theo thụt lề YAML."""
    lines = text.splitlines()
    blocks: list[tuple[int, str]] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^(\s*(?:-\s+)?)run:\s*(.*)$", line)
        if not m:
            i += 1
            continue
        key_indent = len(line) - len(line.lstrip())
        # Nếu là `- run:`, thuộc tính cùng step có indent = key_indent + 2; nếu là `  run:`, indent = key_indent.
        prop_indent = key_indent + 2 if line.lstrip().startswith("- ") else key_indent
        rest = m.group(2).strip()
        start_line = i + 1
        if rest and rest[0] in ("|", ">"):
            body: list[str] = []
            i += 1
            while i < len(lines):
                cur = lines[i]
                if not cur.strip():
                    body.append(cur)
                    i += 1
                    continue
                cur_indent = len(cur) - len(cur.lstrip())
                if cur_indent <= prop_indent:
                    break
                body.append(cur)
                i += 1
            blocks.append((start_line, "\n".join(body)))
        else:
            # Có thể nối nhiều dòng thụt sâu hơn prop_indent
            body = [rest]
            i += 1
            while i < len(lines):
                cur = lines[i]
                if not cur.strip():
                    i += 1
                    continue
                cur_indent = len(cur) - len(cur.lstrip())
                if cur_indent <= prop_indent:
                    break
                body.append(cur.strip())
                i += 1
            blocks.append((start_line, "\n".join(body)))
    return blocks


def has_explicit_permissions(text: str) -> bool:
    if re.search(r"^permissions\s*:", text, re.MULTILINE):
        return True
    # Nếu không có top-level permissions thì mọi job thường (không phải reusable `uses:`) phải có `    permissions:`
    m = re.search(r"^jobs:\s*$", text, re.MULTILINE)
    if not m:
        return False
    jobs_body = text[m.end():]
    job_starts = [jm.start() for jm in re.finditer(r"^  [A-Za-z0-9_-]+:\s*$", jobs_body, re.MULTILINE)]
    if not job_starts:
        return False
    for idx, start in enumerate(job_starts):
        end = job_starts[idx + 1] if idx + 1 < len(job_starts) else len(jobs_body)
        chunk = jobs_body[start:end]
        if re.search(r"^    uses:\s*", chunk, re.MULTILINE):
            continue
        if not re.search(r"^    permissions\s*:", chunk, re.MULTILINE):
            return False
    return True


class WorkflowSecurityTest(unittest.TestCase):
    def iter_workflows(self):
        files = sorted(WORKFLOWS_DIR.glob("*.yml"))
        self.assertGreaterEqual(len(files), 40)
        for path in files:
            text = path.read_text(encoding="utf-8")
            if yaml is not None:
                yaml.safe_load(text)
            yield path, text

    def test_no_dangerous_pull_request_target_or_workflow_run(self):
        for path, text in self.iter_workflows():
            self.assertIsNone(
                re.search(r"^\s*pull_request_target\s*:", text, re.MULTILINE),
                f"{path.name}: cấm pull_request_target",
            )
            self.assertIsNone(
                re.search(r"^\s*workflow_run\s*:", text, re.MULTILINE),
                f"{path.name}: cấm workflow_run",
            )

    def test_every_workflow_declares_explicit_permissions(self):
        for path, text in self.iter_workflows():
            self.assertTrue(
                has_explicit_permissions(text),
                f"{path.name}: thiếu khai báo permissions tường minh",
            )

    def test_no_untrusted_expressions_interpolated_in_run_blocks(self):
        for path, text in self.iter_workflows():
            for line_no, script in extract_run_blocks(text):
                match = UNTRUSTED_RUN_EXPR.search(script)
                self.assertIsNone(
                    match,
                    f"{path.name}:L{line_no} nội suy đầu vào trực tiếp vào run: {match.group(0) if match else ''}",
                )

    def test_third_party_actions_are_pinned_to_40_char_sha(self):
        uses_re = re.compile(r"^\s*(?:-\s+)?uses:\s*['\"]?([^\s#'\"]+)", re.MULTILINE)
        for path, text in self.iter_workflows():
            for m in uses_re.finditer(text):
                uses = m.group(1)
                if uses.startswith(THIRD_PARTY_PREFIXES):
                    self.assertRegex(
                        uses,
                        r"^[^@]+@[0-9a-f]{40}$",
                        f"{path.name}: action bên thứ ba chưa ghim SHA 40 ký tự: {uses}",
                    )

    def test_detector_catches_synthetic_violations(self):
        bad_run = "jobs:\n  x:\n    steps:\n      - run: |\n          echo ${{ inputs.tag }}\n"
        blocks = extract_run_blocks(bad_run)
        self.assertEqual(len(blocks), 1)
        self.assertIsNotNone(UNTRUSTED_RUN_EXPR.search(blocks[0][1]))
        self.assertFalse(has_explicit_permissions("on: push\njobs:\n  a:\n    runs-on: ubuntu-latest\n"))
        self.assertTrue(has_explicit_permissions("on: push\npermissions: {}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"))


if __name__ == "__main__":
    unittest.main()
