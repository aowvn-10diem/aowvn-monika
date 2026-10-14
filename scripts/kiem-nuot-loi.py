#!/usr/bin/env python3
"""V113: compare newly introduced silent Kotlin handlers, without executing app code."""
import argparse
from collections import Counter
from dataclasses import dataclass
import hashlib
import re
import subprocess
import sys

PREFIX = "app/src/main/java/vn/aow/monika/"
SCOPES = ("download", "library", "pack", "runner", "azahar")
LOG_CALLS = {"recordHandled", "recordCoreFailure", "recordJavaCrash",
             "recordEngineSignal", "op", "opSuspend", "opFail"}
FOLLOWUPS = {"onFailure", "getOrElse", "recover", "recoverCatching"}
SHA = re.compile(r"[0-9a-f]{40}\Z")
MAX_FILE = 1024 * 1024
IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")


@dataclass(frozen=True)
class Token:
    text: str
    line: int


def lex(source):
    """Ignore comments/literal text; retain code inside Kotlin ${...} templates."""
    tokens, comments = [], {}
    index, line = 0, 1

    def scan(stop_template=False):
        nonlocal index, line
        depth = 0
        while index < len(source):
            c = source[index]
            if c.isspace():
                line += c == "\n"
                index += 1
                continue
            if source.startswith("//", index):
                end = source.find("\n", index)
                end = len(source) if end < 0 else end
                comments.setdefault(line, []).append(source[index + 2:end])
                index = end
                continue
            if source.startswith("/*", index):
                index += 2
                nested = 1
                while index < len(source) and nested:
                    if source.startswith("/*", index):
                        nested += 1
                        index += 2
                    elif source.startswith("*/", index):
                        nested -= 1
                        index += 2
                    else:
                        line += source[index] == "\n"
                        index += 1
                if nested:
                    raise ValueError("unterminated comment")
                continue
            if c in "\"'":
                literal_start, literal_line = index, line
                quote = '"""' if source.startswith('"""', index) else c
                index += len(quote)
                while index < len(source):
                    if source.startswith(quote, index):
                        index += len(quote)
                        literal_hash = hashlib.sha256(source[literal_start:index].encode()).hexdigest()
                        tokens.append(Token("literal:" + literal_hash, literal_line))
                        break
                    if quote != "'" and source.startswith("${", index):
                        tokens.append(Token("{", line))
                        index += 2
                        scan(True)
                    elif quote != '"""' and source[index] == "\\":
                        line += index + 1 < len(source) and source[index + 1] == "\n"
                        index += 2
                    else:
                        line += source[index] == "\n"
                        index += 1
                else:
                    raise ValueError("unterminated literal")
                continue
            match = IDENTIFIER.match(source, index)
            if match:
                text = match.group()
                tokens.append(Token(text, line))
                index += len(text)
                continue
            tokens.append(Token(c, line))
            index += 1
            if c == "{":
                depth += 1
            elif c == "}":
                if stop_template and depth == 0:
                    return
                depth -= 1
        if stop_template:
            raise ValueError("unterminated template")

    scan()
    return tokens, comments


def balanced(tokens, start, left, right):
    depth = 0
    for index in range(start, len(tokens)):
        text = tokens[index].text
        if text == left:
            depth += 1
        elif text == right:
            depth -= 1
            if depth == 0:
                return index + 1
    raise ValueError("unbalanced handler")


def logged(tokens):
    words = [x.text for x in tokens]
    return any(words[i:i+2] == ["Diagnostics", "."] and
               words[i+2] in LOG_CALLS and words[i+3] == "("
               for i in range(len(words) - 3))


def exempt(comments, line):
    # Only this handler's line or immediately preceding line; never a file-wide waiver.
    for text in comments.get(line, []) + comments.get(line - 1, []):
        match = re.fullmatch(r"\s*diag:\s*bỏ qua\s+(.+?)\s*", text)
        if match:
            reason = match.group(1)
            if len(reason) >= 3 and any(c.isalpha() for c in reason) and not any(c in reason for c in "<>"):
                return True
    return False


def direct_rethrow(tokens, arguments, block, block_end):
    """Accept only a catch body consisting of throw of its own parameter."""
    parameter = [x.text for x in tokens[arguments:block]]
    if (len(parameter) < 5 or parameter[0] != "(" or parameter[2] != ":" or
            parameter[-1] != ")" or not IDENTIFIER.fullmatch(parameter[1])):
        return False
    body = [x.text for x in tokens[block + 1:block_end - 1]]
    return body in (["throw", parameter[1]], ["throw", parameter[1], ";"])


def silent_handlers(source):
    tokens, comments = lex(source)
    handlers = []
    for start, token in enumerate(tokens):
        kind = token.text
        if kind not in {"catch", "runCatching"}:
            continue
        if start and tokens[start - 1].text in {"fun", ":"}:
            continue
        end = start + 1
        if kind == "runCatching" and end < len(tokens) and tokens[end].text == "<":
            end = balanced(tokens, end, "<", ">")
        arguments = end
        has_parentheses = end < len(tokens) and tokens[end].text == "("
        if has_parentheses:
            end = balanced(tokens, end, "(", ")")
        if end < len(tokens) and tokens[end].text == "{":
            block_end = balanced(tokens, end, "{", "}")
        elif kind == "runCatching" and has_parentheses:
            # Includes runCatching({ ... }), block=..., and a referenced lambda.
            block_end = end
            end = arguments
        else:
            continue  # references/identifiers, not a handler call
        finish = block_end
        has_log = logged(tokens[end:block_end])
        if kind == "catch":
            has_log |= direct_rethrow(tokens, arguments, end, block_end)
        if kind == "runCatching":
            # Standard failure handling adjacent to Result creation.
            while finish + 2 < len(tokens) and tokens[finish].text == ".":
                name = tokens[finish + 1].text
                argument = finish + 2
                if name == "getOrThrow" and tokens[argument].text == "(":
                    finish = balanced(tokens, argument, "(", ")")
                    has_log = True  # explicit propagation, not swallowing
                    break
                if name not in FOLLOWUPS:
                    break
                if tokens[argument].text == "{":
                    follow_end = balanced(tokens, argument, "{", "}")
                elif tokens[argument].text == "(":
                    follow_end = balanced(tokens, argument, "(", ")")
                else:
                    break
                has_log |= logged(tokens[argument:follow_end])
                finish = follow_end
        if not has_log and not exempt(comments, token.line):
            # Stable baseline ignores comments/spacing but includes hashed literal content.
            fingerprint = tuple(x.text for x in tokens[start:finish])
            handlers.append((fingerprint, token.line, kind))
    return handlers


def new_violations(before, after):
    old = Counter(x[0] for x in silent_handlers(before))
    violations = []
    for fingerprint, line, kind in silent_handlers(after):
        if old[fingerprint]:
            old[fingerprint] -= 1
        else:
            violations.append((line, kind))
    return violations


def git(*args):
    result = subprocess.run(["git", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    if len(result.stdout) > MAX_FILE:
        raise ValueError("git response too large")
    return result.stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--pull-request", action="store_true", help="compare PR changes from the common ancestor")
    args = parser.parse_args(argv)
    try:
        if not SHA.fullmatch(args.head) or not SHA.fullmatch(args.base):
            raise ValueError("full commit SHAs required")
        base = args.base
        if base == "0" * 40:
            if args.pull_request:
                raise ValueError("PR base commit required")
            base = git("merge-base", "refs/remotes/origin/main", args.head).decode().strip()
        elif args.pull_request:
            # A PR target can advance without becoming an ancestor of its head.
            # Resolve the real common commit; missing history still fails closed.
            base = git("merge-base", base, args.head).decode().strip()
        git("merge-base", "--is-ancestor", base, args.head)
        paths = git("diff", "--name-only", "-z", "--diff-filter=ACMR", base, args.head,
                    "--", *[PREFIX + scope + "/" for scope in SCOPES]).decode("utf-8").split("\0")
        errors = []
        for path in filter(lambda p: p.endswith(".kt"), paths):
            # Renames into a guarded directory are new files; never skip a missing baseline.
            exists = subprocess.run(["git", "cat-file", "-e", base + ":" + path],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
            before = git("show", base + ":" + path).decode("utf-8") if exists else ""
            after = git("show", args.head + ":" + path).decode("utf-8")
            errors.extend((path, line, kind) for line, kind in new_violations(before, after))
        for path, line, kind in errors[:100]:
            print(f"{path}:{line}: {kind} mới thiếu Diagnostics hoặc // diag: bỏ qua <lý do>")
        if len(errors) > 100:
            print("V113: còn lỗi; đầu ra giới hạn 100 dòng")
        if errors:
            return 1
        print("V113 PASS: không thêm handler nuốt lỗi trong phạm vi")
        return 0
    except (ValueError, UnicodeError, subprocess.CalledProcessError):
        print("V113 BLOCKED: thiếu lịch sử git hoặc không phân tích được nguồn", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
