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
APP_GRADLE = Path(__file__).resolve().parents[1] / "app/build.gradle.kts"

# Ngưỡng bền: không phải "số chính xác hiện tại" mà là sàn để phát hiện quét hụt cả thư mục.
# 48 = đúng số tệp `.yml` trên nhánh này; sàn cũ trên `main` là 40 nên KHÔNG BAO GIỜ hạ dưới mức đó
# (xem `test_workflow_floor_never_below_main_baseline`).
MIN_WORKFLOWS = 48
MIN_WORKFLOWS_FLOOR = 40

# A85/V196: allowlist tải của bản phát hành phải RỖNG, biến môi trường chỉ được dùng cho bản debug.
ENV_EXTRA_HOSTS = 'System.getenv("MONIKA_EXTRA_DOWNLOAD_HOSTS")'
RELEASE_EMPTY_ALLOWLIST = 'buildConfigField("String", "EXTRA_DOWNLOAD_HOSTS", "\\"\\"")'
EMPTY_ALLOWLIST_LINE = RELEASE_EMPTY_ALLOWLIST
# Dòng sinh allowlist cho bản debug, dùng chung cho tệp thật lẫn tệp giả trong ca âm.
DEBUG_ALLOWLIST_LINES = [
    "buildConfigField(",
    '"String", "EXTRA_DOWNLOAD_HOSTS",',
    '"\\"${' + ENV_EXTRA_HOSTS + '.orEmpty().replace(Regex("[^A-Za-z0-9.,;-]"), "")}\\""',
    ")",
]

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


def kotlin_scan(text: str) -> tuple[str, str]:
    """Tách mã Kotlin khỏi chú thích/chuỗi: trả (bản giữ chuỗi, bản chỉ còn mã).

    Bản 1 bỏ chú thích nhưng GIỮ nguyên chuỗi, để so khớp đúng nguyên văn
    `System.getenv("...")`. Bản 2 bỏ cả ruột chuỗi, để đếm ngoặc `{}` — dấu ngoặc
    nằm trong chuỗi hay chú thích không phải cú pháp khối nên không được tính.
    Cả hai bản giữ nguyên độ dài và vị trí ký tự của tệp gốc.
    """
    keep = list(text)
    code = list(text)
    states: list[str] = []
    braces: list[int] = []  # độ sâu `{}` cho mục "template" (`${...}`)

    def blank(buf: list[str], index: int) -> None:
        if buf[index] != "\n":
            buf[index] = " "

    def blank_both(index: int) -> None:
        blank(keep, index)
        blank(code, index)

    def push(state: str) -> None:
        states.append(state)
        braces.append(0)

    def pop() -> None:
        states.pop()
        braces.pop()

    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        state = states[-1] if states else "code"
        if state == "line":
            blank_both(i)
            if ch == "\n":
                pop()
            i += 1
        elif state == "block":
            blank_both(i)
            if text.startswith("*/", i):
                blank_both(i + 1)
                pop()
                i += 2
            else:
                i += 1
        elif state == "raw":
            blank(code, i)
            if text.startswith('"""', i):
                for offset in range(3):
                    blank(code, i + offset)
                pop()
                i += 3
            else:
                i += 1
        elif state == "string":
            blank(code, i)
            if ch == "\\" and i + 1 < n:
                blank(code, i + 1)
                i += 2
            elif ch == '"':
                pop()
                i += 1
            elif text.startswith("${", i):
                push("template")
                i += 2
            elif ch == "\n":  # chuỗi chưa đóng: đừng nuốt phần còn lại của tệp
                pop()
                i += 1
            else:
                i += 1
        elif state == "template":
            # Trong `${...}` là mã thật: vẫn đếm ngoặc để biết `}` nào đóng template.
            if ch == "{":
                braces[-1] += 1
                i += 1
            elif ch == "}":
                if braces[-1]:
                    braces[-1] -= 1
                    i += 1
                else:
                    pop()
                    i += 1
            elif ch == '"':
                push("string")
                blank(code, i)
                i += 1
            elif text.startswith("//", i):
                push("line")
                blank_both(i)
                blank_both(i + 1)
                i += 2
            elif ch == "\n":
                pop()
                i += 1
            else:
                i += 1
        else:  # code
            if text.startswith("//", i):
                blank_both(i)
                blank_both(i + 1)
                push("line")
                i += 2
            elif text.startswith("/*", i):
                blank_both(i)
                blank_both(i + 1)
                push("block")
                i += 2
            elif text.startswith('"""', i):
                for offset in range(3):
                    blank(code, i + offset)
                push("raw")
                i += 3
            elif ch == '"':
                push("string")
                blank(code, i)
                i += 1
            else:
                i += 1
    return "".join(keep), "".join(code)


def kotlin_block_bounds(code: str, header: str) -> tuple[int, int] | None:
    """Vị trí (sau `{` của header, trước `}` đóng) của khối `header {` đầu tiên.

    `code` là bản chỉ-còn-mã từ `kotlin_scan`; nhờ vậy `{`/`}` trong chuỗi, chú thích
    hay `${...}` không làm lệch độ sâu. Trả None nếu không có khối.
    """
    match = re.search(rf"\b{re.escape(header)}\s*\{{", code)
    if not match:
        return None
    start = match.end() - 1
    depth = 0
    for index in range(start, len(code)):
        if code[index] == "{":
            depth += 1
        elif code[index] == "}":
            depth -= 1
            if depth == 0:
                return start + 1, index
    return None


def allowlist_problems(text: str) -> list[str]:
    """Lỗi cấu hình allowlist tải trong `buildTypes` của `app/build.gradle.kts` (rỗng = đạt)."""
    keep, code = kotlin_scan(text)
    problems: list[str] = []
    build_types = kotlin_block_bounds(code, "buildTypes")
    if build_types is None:
        return ["không thấy khối `buildTypes`"]
    keep_types = keep[build_types[0]:build_types[1]]
    code_types = code[build_types[0]:build_types[1]]

    def body(header: str) -> str | None:
        bounds = kotlin_block_bounds(code_types, header)
        return None if bounds is None else keep_types[bounds[0]:bounds[1]]

    release = body("release")
    if release is None:
        problems.append("không thấy khối `release`")
    else:
        if RELEASE_EMPTY_ALLOWLIST not in release:
            problems.append("release: thiếu " + RELEASE_EMPTY_ALLOWLIST)
        if release.count("buildConfigField") != 1:
            problems.append("release: phải có đúng 1 buildConfigField (allowlist rỗng)")
        if ENV_EXTRA_HOSTS in release:
            problems.append("release: không được đọc " + ENV_EXTRA_HOSTS)

    debug = body("debug")
    if debug is None:
        problems.append("không thấy khối `debug`")
    elif ENV_EXTRA_HOSTS not in debug:
        problems.append("debug: phải đọc " + ENV_EXTRA_HOSTS)
    else:
        # Đếm trên toàn tệp: mọi lần đọc biến đều phải nằm trong khối debug.
        if keep.count(ENV_EXTRA_HOSTS) != debug.count(ENV_EXTRA_HOSTS):
            problems.append("còn chỗ đọc " + ENV_EXTRA_HOSTS + " ngoài khối `debug`")
        if debug_filter_pattern(debug) is None:
            problems.append("debug: thiếu bộ lọc ký tự cho giá trị env")
    return problems


def debug_filter_pattern(debug_body: str) -> str | None:
    """Chuỗi regex lọc ký tự trong khối `debug` (dùng để chặn newline/backslash phá mã sinh ra)."""
    match = re.search(r'Regex\(\s*"((?:[^"\\]|\\.)+)"\s*\)', debug_body)
    return None if match is None else match.group(1)


def fake_gradle(release_body: list[str], debug_body: list[str]) -> str:
    """Dựng đoạn Kotlin giả cùng hình dạng `buildTypes { release {} debug {} }` để thử detector."""
    lines = ["android {", "    buildTypes {", "        release {"]
    lines += ["            " + line for line in release_body]
    lines += ["        }", "        debug {"]
    lines += ["            " + line for line in debug_body]
    lines += ["        }", "    }", "}"]
    return "\n".join(lines) + "\n"


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

    def test_workflow_floor_never_below_main_baseline(self):
        """Sàn quét không được hạ: main từng dùng 40, hạ nữa là tự bịt mắt trước workflow mới."""
        self.assertGreaterEqual(
            MIN_WORKFLOWS, MIN_WORKFLOWS_FLOOR,
            f"MIN_WORKFLOWS={MIN_WORKFLOWS} thấp hơn sàn cũ trên main ({MIN_WORKFLOWS_FLOOR})",
        )

    def test_release_build_has_empty_download_allowlist(self):
        """A85: `release {` sinh đúng allowlist rỗng; biến môi trường chỉ được đọc trong `debug {`."""
        text = APP_GRADLE.read_text(encoding="utf-8")
        self.assertEqual(allowlist_problems(text), [], "allowlist tải của bản phát hành không còn rỗng")

        # Chứng minh đã parse đúng khối thật trong tệp, không phải so khớp một chuỗi mô tả.
        keep, code = kotlin_scan(text)
        bounds = kotlin_block_bounds(code, "release")
        self.assertIsNotNone(bounds, "không tìm thấy khối `release` trong app/build.gradle.kts")
        release = keep[bounds[0]:bounds[1]]
        self.assertIn("isMinifyEnabled = true", release, "khối `release` parse được phải là khối R8 thật")
        self.assertNotIn(ENV_EXTRA_HOSTS, release)
        self.assertIn(RELEASE_EMPTY_ALLOWLIST, release)

    def test_allowlist_detector_catches_synthetic_violations(self):
        """Ca âm: dựng tệp giả rồi bắt buộc detector phải báo lỗi, tránh test chỉ đọc mô tả."""
        good = fake_gradle([EMPTY_ALLOWLIST_LINE], DEBUG_ALLOWLIST_LINES)
        self.assertEqual(allowlist_problems(good), [], "detector báo lỗi trên tệp giả HỢP LỆ")

        # 1) release đọc biến môi trường → phải bị bắt.
        env_in_release = fake_gradle([EMPTY_ALLOWLIST_LINE] + DEBUG_ALLOWLIST_LINES, DEBUG_ALLOWLIST_LINES)
        self.assertTrue(
            any("release: không được đọc" in problem for problem in allowlist_problems(env_in_release)),
            "detector bỏ lọt env trong release",
        )

        # 2) release mất allowlist rỗng / có thêm field thứ hai → phải bị bắt.
        missing_field = fake_gradle(["// không có buildConfigField"], DEBUG_ALLOWLIST_LINES)
        self.assertTrue(any("release: thiếu" in p for p in allowlist_problems(missing_field)))
        duplicate_field = fake_gradle([EMPTY_ALLOWLIST_LINE, EMPTY_ALLOWLIST_LINE], DEBUG_ALLOWLIST_LINES)
        self.assertTrue(any("đúng 1 buildConfigField" in p for p in allowlist_problems(duplicate_field)))

        # 3) debug không lọc ký tự → phải bị bắt.
        no_filter = fake_gradle(
            [EMPTY_ALLOWLIST_LINE],
            [
                'buildConfigField(',
                '"String", "EXTRA_DOWNLOAD_HOSTS",',
                '"\\"${System.getenv("MONIKA_EXTRA_DOWNLOAD_HOSTS").orEmpty()}\\""',
                ')',
            ],
        )
        self.assertTrue(
            any("debug: thiếu bộ lọc" in problem for problem in allowlist_problems(no_filter)),
            "detector bỏ lọt debug thiếu bộ lọc",
        )

        # 4) env nằm ngoài cả release lẫn debug → phải bị bắt.
        stray = "\n".join(
            ["android {", "    buildTypes {", "        " + ENV_EXTRA_HOSTS, "    }", "}"]
        ) + "\n"
        self.assertTrue(allowlist_problems(stray), "detector bỏ lọt env ngoài buildTypes")

    def test_debug_download_host_filter_strips_unsafe_characters(self):
        """Bộ lọc ký tự của bản debug: chỉ giữ ký tự host, chặn newline/backslash phá mã Kotlin sinh ra."""
        text = APP_GRADLE.read_text(encoding="utf-8")
        keep, code = kotlin_scan(text)
        debug_bounds = kotlin_block_bounds(code, "debug")
        self.assertIsNotNone(debug_bounds, "không tìm thấy khối `debug` trong app/build.gradle.kts")
        pattern = debug_filter_pattern(keep[debug_bounds[0]:debug_bounds[1]])
        self.assertIsNotNone(pattern, "khối `debug` thiếu bộ lọc Regex cho env")
        allowed = re.compile(pattern)

        raw = 'localhost,example.com;host-1\n"\\${System.exit(1)}\r\t'
        filtered = allowed.sub("", raw)
        self.assertEqual(filtered, "localhost,example.com;host-1System.exit1")
        for char in ("\n", "\r", "\t", '"', "\\", "$", "{", "}", " "):
            self.assertEqual(allowed.sub("", char), "", f"bộ lọc để lọt ký tự nguy hiểm {char!r}")
        for char in "abcXYZ019.,;-":
            self.assertEqual(allowed.sub("", char), char, f"bộ lọc xoá nhầm ký tự hợp lệ {char!r}")

        # Ca âm: bộ lọc cũ (chỉ bỏ dấu nháy) phải bị coi là thiếu.
        self.assertIsNone(debug_filter_pattern('replace("\\"", "")'))

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
