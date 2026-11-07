#!/usr/bin/env python3
"""V200: cho danh sách tệp đổi của một PR (mỗi dòng một đường dẫn, đọc từ stdin),
in tên các workflow bắt buộc phải xanh (mỗi dòng một tên). Không in gì = PR chỉ sửa tài liệu.

Phản chiếu bộ lọc đường dẫn thật của workflow. Đổi bộ lọc ở workflow nào thì phải đổi ở đây:
  - Build:        build.yml     pull_request paths-ignore ["docs/**", "**/*.md"]
  - Coverage:     coverage.yml  pull_request paths-ignore ["docs/**", "**/*.md"]
  - CodeQL (V41): codeql.yml    pull_request paths [app/**, cloudflare/**, scripts/**,
                  .github/workflows/**, .github/codeql-config.yml, gradle/libs.versions.toml]
Quy tắc GitHub: workflow có paths-ignore chỉ bị bỏ qua khi MỌI tệp đổi đều khớp mẫu bỏ qua;
workflow có paths chỉ chạy khi có ÍT NHẤT một tệp khớp.
"""
import sys

BUILD_NAMES = ("Build", "Coverage")
CODEQL_NAME = "CodeQL (V41)"
CODEQL_PREFIXES = ("app/", "cloudflare/", "scripts/", ".github/workflows/")
CODEQL_FILES = (".github/codeql-config.yml", "gradle/libs.versions.toml")


def is_doc(path: str) -> bool:
    return path.startswith("docs/") or path.endswith(".md")


def expected(paths):
    paths = [p.strip() for p in paths if p.strip()]
    out = []
    if any(not is_doc(p) for p in paths):
        out.extend(BUILD_NAMES)
    if any(p.startswith(CODEQL_PREFIXES) or p in CODEQL_FILES for p in paths):
        out.append(CODEQL_NAME)
    return out


if __name__ == "__main__":
    for name in expected(sys.stdin.read().splitlines()):
        print(name)
