#!/usr/bin/env python3
"""A85: kiểm tĩnh bảo mật toàn bộ workflow GitHub Actions (cần PyYAML để kiểm cấu trúc)."""
from pathlib import Path
import re
import sys
import unittest

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover - môi trường thiếu thư viện phải báo rõ, không bỏ qua âm thầm
    sys.stderr.write(
        "Thiếu PyYAML: script cần PyYAML để kiểm cấu trúc workflow.\n"
        "Cài: python3 -m pip install pyyaml\n"
    )
    raise SystemExit(2)

WORKFLOWS_DIR = Path(__file__).resolve().parents[1] / ".github/workflows"

# Ngưỡng bền: không phải "số chính xác hiện tại" mà là sàn để phát hiện quét hụt cả thư mục.
MIN_WORKFLOWS = 30

# Hành động chính thức của GitHub / đường dẫn nội bộ được phép dùng tag major.
# Mọi `uses:` khác BẮT BUỘC ghim SHA 40 ký tự (deny-by-default: action thêm sau này cũng bị phủ).
FIRST_PARTY_PREFIXES = ("actions/", "github/", "./")
SHA_PINNED = re.compile(r"^[^@\s]+@[0-9a-f]{40}$")

# Input nhận lệnh/đoạn mã: nội suy dữ liệu không tin cậy vào đây cũng là chỗ chèn lệnh.
RISKY_WITH_INPUTS = {"script", "command", "args", "cmd", "entrypoint", "shell", "run"}

UNTRUSTED_RUN_EXPR = re.compile(
    r"\$\{\{\s*(?:"
    r"inputs\.|"
    r"github\.event\.(?:inputs|comment|issue|pull_request\.title|pull_request\.body|pull_request\.head\.ref|head_commit\.message)|"
    r"github\.head_ref"
    r")"
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


def iter_steps(document) -> list[dict]:
    """Mọi `steps:` trong workflow đã parse (bỏ qua job không có steps)."""
    steps: list[dict] = []
    jobs = (document or {}).get("jobs") or {}
    if not isinstance(jobs, dict):
        return steps
    for job in jobs.values():
        if not isinstance(job, dict):
            continue
        for step in job.get("steps") or []:
            if isinstance(step, dict):
                steps.append(step)
    return steps


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


def workflow_triggers(document) -> set[str]:
    """Tập trigger của workflow. PyYAML đọc `on:` thành khoá boolean True (YAML 1.1)."""
    on = document.get("on", document.get(True))
    if isinstance(on, str):
        return {on}
    if isinstance(on, list):
        return {str(item) for item in on}
    if isinstance(on, dict):
        return {str(key) for key in on}
    return set()


def has_write_permission(permissions) -> bool:
    if permissions == "write-all":
        return True
    if isinstance(permissions, dict):
        return any(value == "write" for value in permissions.values())
    return False


def job_has_repository_guard(job: dict) -> bool:
    condition = job.get("if")
    return isinstance(condition, str) and "github.repository" in condition


class WorkflowSecurityTest(unittest.TestCase):
    def iter_workflows(self):
        files = sorted(WORKFLOWS_DIR.glob("*.yml"))
        self.assertGreaterEqual(
            len(files), MIN_WORKFLOWS,
            f"chỉ thấy {len(files)} workflow (sàn {MIN_WORKFLOWS}) — kiểm lại thư mục quét",
        )
        for path in files:
            text = path.read_text(encoding="utf-8")
            document = yaml.safe_load(text)
            self.assertIsInstance(document, dict, f"{path.name}: YAML phải là object")
            yield path, text, document

    def test_no_dangerous_pull_request_target_or_workflow_run(self):
        for path, text, _ in self.iter_workflows():
            self.assertIsNone(
                re.search(r"^\s*pull_request_target\s*:", text, re.MULTILINE),
                f"{path.name}: cấm pull_request_target",
            )
            self.assertIsNone(
                re.search(r"^\s*workflow_run\s*:", text, re.MULTILINE),
                f"{path.name}: cấm workflow_run",
            )

    def test_every_workflow_declares_explicit_permissions(self):
        for path, text, _ in self.iter_workflows():
            self.assertTrue(
                has_explicit_permissions(text),
                f"{path.name}: thiếu khai báo permissions tường minh",
            )

    def test_write_workflows_on_schedule_or_dispatch_guard_every_job(self):
        """Workflow có `schedule`/`workflow_dispatch` + quyền write ⇒ mọi job phải có guard `github.repository`.

        Đóng bằng test thay vì thêm guard thủ công: workflow chạy định kỳ hoặc bấm tay mà có
        token ghi thì chỉ được chạy trong repo chính thức, tránh fork/bản sao dùng token ghi.
        """
        for path, _text, document in self.iter_workflows():
            triggers = workflow_triggers(document)
            if not ({"schedule", "workflow_dispatch"} & triggers):
                continue
            top_write = has_write_permission(document.get("permissions"))
            for name, job in (document.get("jobs") or {}).items():
                if not isinstance(job, dict):
                    continue
                # Job reusable (`uses:`) không có `if` riêng để kiểm ở đây.
                if "uses" in job:
                    continue
                writes = (
                    has_write_permission(job["permissions"])
                    if "permissions" in job
                    else top_write
                )
                if not writes:
                    continue
                self.assertTrue(
                    job_has_repository_guard(job),
                    f"{path.name}: job `{name}` có quyền write và chạy schedule/dispatch "
                    "nhưng thiếu guard `github.repository`",
                )

    def test_no_untrusted_expressions_interpolated_in_run_blocks(self):
        for path, text, _ in self.iter_workflows():
            for line_no, script in extract_run_blocks(text):
                match = UNTRUSTED_RUN_EXPR.search(script)
                self.assertIsNone(
                    match,
                    f"{path.name}:L{line_no} nội suy đầu vào trực tiếp vào run: {match.group(0) if match else ''}",
                )

    def test_no_untrusted_expressions_in_script_or_with_inputs(self):
        for path, _text, document in self.iter_workflows():
            for step in iter_steps(document):
                where = step.get("uses") or step.get("name") or "step"
                script = step.get("script")
                if isinstance(script, str):
                    match = UNTRUSTED_RUN_EXPR.search(script)
                    self.assertIsNone(
                        match,
                        f"{path.name}: nội suy đầu vào trực tiếp vào script của {where}: {match.group(0) if match else ''}",
                    )
                with_ = step.get("with")
                if not isinstance(with_, dict):
                    continue
                for key, value in with_.items():
                    if key not in RISKY_WITH_INPUTS or not isinstance(value, str):
                        continue
                    match = UNTRUSTED_RUN_EXPR.search(value)
                    self.assertIsNone(
                        match,
                        f"{path.name}: nội suy đầu vào trực tiếp vào with.{key} của {where}: {match.group(0) if match else ''}",
                    )

    def test_third_party_actions_are_pinned_to_40_char_sha(self):
        """Deny-by-default: mọi `uses:` ngoài actions/, github/, ./ phải ghim SHA 40 ký tự."""
        uses_re = re.compile(r"^\s*(?:-\s+)?uses:\s*['\"]?([^\s#'\"]+)", re.MULTILINE)
        for path, text, _ in self.iter_workflows():
            for m in uses_re.finditer(text):
                uses = m.group(1)
                if uses.startswith(FIRST_PARTY_PREFIXES):
                    continue
                self.assertRegex(
                    uses,
                    SHA_PINNED.pattern,
                    f"{path.name}: action ngoài actions/github/./ chưa ghim SHA 40 ký tự: {uses}",
                )

    def test_detector_catches_synthetic_violations(self):
        bad_run = "jobs:\n  x:\n    steps:\n      - run: |\n          echo ${{ inputs.tag }}\n"
        blocks = extract_run_blocks(bad_run)
        self.assertEqual(len(blocks), 1)
        self.assertIsNotNone(UNTRUSTED_RUN_EXPR.search(blocks[0][1]))
        self.assertFalse(has_explicit_permissions("on: push\njobs:\n  a:\n    runs-on: ubuntu-latest\n"))
        self.assertTrue(has_explicit_permissions("on: push\npermissions: {}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"))

        sha = "a" * 40
        self.assertIsNone(SHA_PINNED.search("foo/bar@v1"))
        self.assertIsNone(SHA_PINNED.search("gradle/actions/setup-gradle@v6"))
        self.assertIsNotNone(SHA_PINNED.search(f"foo/bar@{sha}"))
        self.assertTrue("actions/checkout@v4".startswith(FIRST_PARTY_PREFIXES))

        doc = yaml.safe_load(
            "jobs:\n  x:\n    steps:\n"
            "      - uses: actions/github-script@v9\n"
            "        with:\n"
            "          script: |\n"
            "            console.log('${{ inputs.tag }}')\n"
            "      - uses: foo/bar@" + sha + "\n"
            "        with:\n"
            "          args: ${{ github.head_ref }}\n"
        )
        found = 0
        for step in iter_steps(doc):
            with_ = step.get("with") or {}
            for key, value in with_.items():
                if key in RISKY_WITH_INPUTS and isinstance(value, str) and UNTRUSTED_RUN_EXPR.search(value):
                    found += 1
        self.assertEqual(found, 2, "phải bắt được cả script: và with.args")

        # Guard: workflow schedule/dispatch + write phải có github.repository ở mọi job.
        guarded = yaml.safe_load(
            "on:\n  schedule: [{cron: '0 1 * * *'}]\npermissions:\n  contents: write\n"
            "jobs:\n  a:\n    if: github.repository == 'aowvn-10diem/aowvn-monika'\n    steps: []\n"
        )
        unguarded = yaml.safe_load(
            "on:\n  workflow_dispatch:\npermissions:\n  contents: write\n"
            "jobs:\n  a:\n    steps: []\n"
        )
        read_only = yaml.safe_load(
            "on:\n  workflow_dispatch:\npermissions:\n  contents: read\n"
            "jobs:\n  a:\n    steps: []\n"
        )
        self.assertIn("schedule", workflow_triggers(guarded))
        self.assertTrue(has_write_permission(guarded["permissions"]))
        self.assertTrue(job_has_repository_guard(guarded["jobs"]["a"]))
        self.assertFalse(job_has_repository_guard(unguarded["jobs"]["a"]))
        self.assertFalse(has_write_permission(read_only["permissions"]))


if __name__ == "__main__":
    unittest.main()
