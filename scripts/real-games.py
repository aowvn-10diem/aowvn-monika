#!/usr/bin/env python3
"""V124: đọc secret TEST_GAME_<MÃ HỆ> rồi dựng ma trận kiểm game thật.

Mỗi dòng secret: `đuôi | link | [ghi chú] | [mk=mật khẩu]`; dòng trống và dòng bắt đầu bằng `#` bỏ qua.
Đuôi phải thuộc `systems[].extensions` của hệ đó trong config, sai thì báo và không chạy.
Chỉ chạy trong CI (xem .github/workflows/real-games.yml).

Không in link hay mật khẩu ra ngoài lệnh `::add-mask::`. Lỗi chỉ ghi số dòng và lý do.
"""
import argparse
import json
import os
import shlex
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PASSWORD_PREFIX = "mk="
# PM V124: bỏ apk khỏi bộ kiểm game thật.
EXCLUDED_SYSTEMS = {"apk"}


def load_systems(config_path):
    """Trả về {mã hệ: cấu hình hệ} từ config/monika-config.json."""
    data = json.loads(Path(config_path).read_text(encoding="utf-8"))
    return {system["id"]: system for system in data.get("systems", [])}


def secret_name(system_id):
    return "TEST_GAME_" + system_id.upper()


def parse_secret(text, system_id, extensions):
    """Trả về (mục, lỗi). Mục: dict có đuôi, link, mật khẩu, ghi chú, số dòng."""
    entries, errors = [], []
    allowed = {ext.lower().lstrip(".") for ext in extensions}
    for lineno, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        cols = [col.strip() for col in line.split("|")]
        if len(cols) < 2 or not cols[0] or not cols[1]:
            errors.append(f"dòng {lineno}: thiếu cột")
            continue
        ext = cols[0].lower().lstrip(".")
        if ext not in allowed:
            errors.append(f"dòng {lineno}: đuôi không thuộc hệ này, không chạy")
            continue
        link = cols[1]
        if not link.startswith("https://"):
            errors.append(f"dòng {lineno}: link không phải https")
            continue
        password, note = "", ""
        for extra in cols[2:]:
            if extra.startswith(PASSWORD_PREFIX):
                password = extra[len(PASSWORD_PREFIX):]
            elif extra and not note:
                note = extra
        entries.append({
            "system": system_id,
            "ext": ext,
            "link": link,
            "password": password,
            "note": note,
            "line": lineno,
        })
    return entries, errors


def build(systems, env, requested):
    """Trả về (ma trận, dòng tóm tắt, lỗi, giá trị cần che).

    systems: {mã hệ: cấu hình hệ}; env: biến môi trường chứa secret; requested: tập mã hệ, rỗng = mọi hệ.
    """
    matrix, rows, errors, masks = [], [], [], []
    for system_id in sorted(systems):
        if system_id in EXCLUDED_SYSTEMS:
            rows.append((system_id, "-", "", "bỏ qua: hệ không nằm trong V124"))
            continue
        if requested and system_id not in requested:
            continue
        name = secret_name(system_id)
        text = env.get(name, "")
        if not text.strip():
            rows.append((system_id, "-", "", "bỏ qua: chưa có secret"))
            continue
        extensions = systems[system_id].get("extensions", [])
        entries, problems = parse_secret(text, system_id, extensions)
        for problem in problems:
            errors.append(f"{name}: {problem}")
            rows.append((system_id, "-", "", "bỏ qua: " + problem))
        for entry in entries:
            # Ma trận đi qua output giữa các job: không mang link hay mật khẩu. Job kiểm đọc lại từ secret của hệ.
            item = {key: entry[key] for key in ("system", "ext", "note", "line")}
            item["secret"] = system_id.upper()
            matrix.append(item)
            masks.append(entry["link"])
            if entry["password"]:
                masks.append(entry["password"])
            rows.append((system_id, entry["ext"], entry["note"], "chờ chạy"))
    return matrix, rows, errors, masks


def pick(systems, text, system_id, line, out_file):
    """Đọc lại đúng một dòng hợp lệ, che link và mật khẩu, rồi ghi REAL_* vào out_file (file tạm, quyền 600).

    text: nội dung secret TEST_GAME_<MÃ HỆ>, job kiểm truyền qua biến GAMES chỉ ở bước đọc dòng.
    Không ghi vào GITHUB_ENV: các bước sau đọc file này bằng shell, không qua env.
    """
    if system_id in EXCLUDED_SYSTEMS or system_id not in systems:
        raise SystemExit(f"hệ {system_id} không nằm trong V124")
    entries, _ = parse_secret(text, system_id, systems[system_id].get("extensions", []))
    entry = next((item for item in entries if item["line"] == line), None)
    if entry is None:
        raise SystemExit(f"{system_id}: không có dòng {line} hợp lệ")
    for value in (entry["link"], entry["password"]):
        if value:
            print(f"::add-mask::{value}")
    with open(out_file, "w", encoding="utf-8") as out:
        os.chmod(out_file, 0o600)
        out.write(f"REAL_LINK={shlex.quote(entry['link'])}\n")
        out.write(f"REAL_PASSWORD={shlex.quote(entry['password'])}\n")
        out.write(f"REAL_EXT={shlex.quote(entry['ext'])}\n")
        out.write(f"REAL_NOTE={shlex.quote(entry['note'])}\n")
    return 0


def write_summary(path, rows):
    """Bảng tóm tắt theo hệ: không có link, không có mật khẩu."""
    lines = ["| Hệ | Đuôi | Ghi chú | Kết quả |", "| --- | --- | --- | --- |"]
    for system_id, ext, note, result in rows:
        lines.append(f"| {system_id} | {ext} | {note} | {result} |")
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--config", default=str(ROOT / "config" / "monika-config.json"))
    parser.add_argument("--systems", default="", help="mã hệ cách nhau bằng dấu phẩy, trống = mọi hệ")
    parser.add_argument("--matrix-out", default="")
    parser.add_argument("--summary", default="")
    parser.add_argument("--github-output", default="")
    parser.add_argument("--pick-system", default="", help="chế độ đọc lại một dòng: mã hệ")
    parser.add_argument("--pick-line", type=int, default=0, help="chế độ đọc lại một dòng: số dòng")
    parser.add_argument("--out-file", default="", help="chế độ đọc lại một dòng: file tạm cho các bước sau")
    args = parser.parse_args(argv)

    systems = load_systems(args.config)
    if args.pick_system:
        return pick(systems, os.environ.get("GAMES", ""), args.pick_system, args.pick_line, args.out_file)
    if not args.matrix_out:
        parser.error("cần --matrix-out khi dựng ma trận")
    requested = {part.strip() for part in args.systems.split(",") if part.strip()}
    matrix, rows, errors, masks = build(systems, dict(os.environ), requested)

    # Che giá trị trước mọi dòng in khác, kể cả khi chỉ có lỗi.
    for value in masks:
        print(f"::add-mask::{value}")
    for error in errors:
        print(f"::warning::{error}")

    Path(args.matrix_out).write_text(json.dumps({"include": matrix}, ensure_ascii=False), encoding="utf-8")
    if args.summary:
        write_summary(args.summary, rows)
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as out:
            out.write(f"count={len(matrix)}\n")
            out.write("matrix=" + json.dumps({"include": matrix}, ensure_ascii=False) + "\n")
    print(f"real-games: {len(matrix)} mục chạy, {len(errors)} dòng bị bỏ qua")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
