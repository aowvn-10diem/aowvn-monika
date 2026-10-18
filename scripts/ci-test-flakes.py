#!/usr/bin/env python3
"""Summarize same-head Android unit-test outcomes from saved Actions logs.

The input is a JSON object with an ``executions`` array. Each execution has
``run_id``, ``attempt``, ``workflow``, ``head_sha``, ``tests_ran``,
``tests_conclusion`` (``success`` or ``failure``), and optionally
``log_path``. Failure logs may be plain text or a downloaded Actions ZIP.

This tool is offline by design. It compares only executions of an identical
head SHA, where the tested source is unchanged. It reports an empirical
same-head retry rate, not a population-wide probability for test suites that
have different test inventories.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
import json
from pathlib import Path
import re
import sys
import zipfile


SUMMARY_RE = re.compile(
    r"(?P<total>\d+)\s+tests? completed,\s*"
    r"(?P<failed>\d+)\s+failed,\s*(?P<skipped>\d+)\s+skipped",
    re.IGNORECASE,
)
GRADLE_CASE_RE = re.compile(
    r"^(?:\d{4}-\d{2}-\d{2}T\S+\s+)?(?P<class>[\w.$]+(?:Test|Tests))\s*>\s*"
    r"(?P<method>[\w$]+)\s+FAILED\s*$"
)
DOT_CASE_RE = re.compile(
    r"^(?:\d{4}-\d{2}-\d{2}T\S+\s+)?(?P<class>[\w.$]+(?:Test|Tests))\."
    r"(?P<method>[\w$]+)\s+FAILED\s*$"
)


def parse_log(text: str) -> dict[str, object]:
    """Return named failing cases and Gradle completion summaries."""
    cases: set[str] = set()
    summaries: list[dict[str, int]] = []
    for line in text.splitlines():
        match = GRADLE_CASE_RE.search(line) or DOT_CASE_RE.search(line)
        if match:
            cases.add(f"{match.group('class')}.{match.group('method')}")
        summary = SUMMARY_RE.search(line)
        if summary:
            summaries.append({key: int(value) for key, value in summary.groupdict().items()})
    return {"failed_cases": sorted(cases), "summaries": summaries}


def read_log(path: Path) -> str:
    """Read a plain-text log or a GitHub Actions log ZIP without echoing it."""
    if path.suffix.lower() != ".zip":
        return path.read_text(encoding="utf-8", errors="replace")
    with zipfile.ZipFile(path) as archive:
        return "\n".join(
            archive.read(name).decode("utf-8", errors="replace")
            for name in archive.namelist()
            if not name.endswith("/")
        )


def load_manifest(path: Path) -> list[dict[str, object]]:
    """Load the normalized execution manifest and parse any supplied logs."""
    data = json.loads(path.read_text(encoding="utf-8"))
    executions = data.get("executions") if isinstance(data, dict) else data
    if not isinstance(executions, list):
        raise ValueError("manifest must be a JSON array or an object with executions[]")

    parsed: list[dict[str, object]] = []
    for index, row in enumerate(executions):
        if not isinstance(row, dict):
            raise ValueError(f"execution {index} must be an object")
        required = ("run_id", "attempt", "workflow", "head_sha", "tests_ran", "tests_conclusion")
        missing = [key for key in required if key not in row]
        if missing:
            raise ValueError(f"execution {index} is missing: {', '.join(missing)}")
        sha = str(row["head_sha"]).lower()
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise ValueError(f"execution {index} has an invalid 40-character head_sha")
        if row["tests_conclusion"] not in ("success", "failure", "skipped", "cancelled"):
            raise ValueError(f"execution {index} has an invalid tests_conclusion")

        item = dict(row)
        item["head_sha"] = sha
        item["failed_cases"] = []
        item["summaries"] = []
        if item.get("log_path"):
            log_path = Path(str(item["log_path"]))
            if not log_path.is_absolute():
                log_path = path.parent / log_path
            outcome = parse_log(read_log(log_path))
            item.update(outcome)
        parsed.append(item)
    return parsed


def analyze(executions: list[dict[str, object]], threshold: float = 0.02) -> dict[str, object]:
    """Calculate failure ratios for named cases on heads where they failed.

    A case is eligible only when its name appears in a completed failure log
    for that exact SHA. Green suite runs at that same SHA count as passes. A
    failed suite with a parsed summary counts each known case not listed among
    its failures as a pass. Failed runs without a completion summary are
    excluded from the per-case denominator.
    """
    if threshold < 0 or threshold > 1:
        raise ValueError("threshold must be between 0 and 1")

    known_by_sha: dict[str, set[str]] = defaultdict(set)
    unclassified_failures = 0
    eligible_executions = 0
    for row in executions:
        if not row.get("tests_ran") or row.get("tests_conclusion") not in ("success", "failure"):
            continue
        sha = str(row["head_sha"])
        cases = set(row.get("failed_cases", []))
        summaries = row.get("summaries", [])
        if row["tests_conclusion"] == "failure":
            if not summaries or not cases or summaries[-1].get("failed") != len(cases):
                unclassified_failures += 1
                continue
            known_by_sha[sha].update(cases)

    counts: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {"failed": 0, "passed": 0})
    for row in executions:
        if not row.get("tests_ran") or row.get("tests_conclusion") not in ("success", "failure"):
            continue
        sha = str(row["head_sha"])
        known_cases = known_by_sha.get(sha, set())
        if not known_cases:
            continue
        cases = set(row.get("failed_cases", []))
        summaries = row.get("summaries", [])
        if row["tests_conclusion"] == "failure" and (
            not summaries or not cases or summaries[-1].get("failed") != len(cases)
        ):
            continue
        eligible_executions += 1
        for case in known_cases:
            counts[(sha, case)]["failed" if case in cases else "passed"] += 1

    totals: dict[str, dict[str, object]] = {}
    for (sha, case), count in counts.items():
        row = totals.setdefault(case, {"failed": 0, "passed": 0, "heads": set()})
        row["failed"] = int(row["failed"]) + count["failed"]
        row["passed"] = int(row["passed"]) + count["passed"]
        row["heads"].add(sha)

    cases_out = []
    for case, count in totals.items():
        failed = int(count["failed"])
        passed = int(count["passed"])
        total = failed + passed
        rate = failed / total if total else 0.0
        cases_out.append({
            "test": case,
            "failed": failed,
            "passed": passed,
            "attempts": total,
            "same_head_rate": rate,
            "heads": len(count["heads"]),
            "candidate_over_threshold": failed > 0 and passed > 0 and rate > threshold,
        })
    cases_out.sort(key=lambda item: (-int(item["candidate_over_threshold"]), -float(item["same_head_rate"]), str(item["test"])))

    return {
        "scope": "same-head only; not a population-wide test probability",
        "threshold": threshold,
        "eligible_suite_executions": eligible_executions,
        "unclassified_failed_suites": unclassified_failures,
        "candidate_count": sum(bool(item["candidate_over_threshold"]) for item in cases_out),
        "tests": cases_out,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="normalized JSON manifest of unit-test executions")
    parser.add_argument("--threshold", type=float, default=0.02, help="red-rate threshold, default 0.02")
    args = parser.parse_args(argv)
    try:
        result = analyze(load_manifest(args.manifest), args.threshold)
    except (OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
