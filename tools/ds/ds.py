#!/usr/bin/env python3
"""Cổng gọi DeepSeek qua Tokenize: phân luồng flash/pro, chạy song song, giữ dưới trần gói.

Chỉ dùng thư viện chuẩn. Key đọc từ biến môi trường (xem ds-config.json), không bao giờ in ra.
Lệnh: run | batch | usage | mcp   (chi tiết: tools/ds/README.md)
"""
import argparse
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.environ.get("DS_CONFIG", os.path.join(HERE, "ds-config.json")), encoding="utf-8") as _f:
    CFG = json.load(_f)


class BudgetError(Exception):
    pass


class ApiError(Exception):
    def __init__(self, code, body):
        super().__init__(f"HTTP {code}: {body}")
        self.code = code


def api_key():
    key = os.environ.get(CFG["keyEnv"], "").strip()
    path = os.environ.get(CFG["keyFileEnv"], "")
    if not key and path and os.path.isfile(path):
        with open(path, encoding="utf-8") as f:
            key = f.read().strip()
    if not key:
        raise ApiError(0, f"thiếu key: đặt {CFG['keyEnv']} hoặc {CFG['keyFileEnv']} (đường dẫn file chứa key)")
    return key


class Budget:
    """Đếm token mới + request theo từng cửa sổ của gói; đặt chỗ trước để chạy song song không vượt trần."""

    def __init__(self):
        self.lock = threading.Lock()
        self.path = os.path.expanduser(CFG["ledger"])
        self.rows = []
        horizon = time.time() - max(w["seconds"] for w in CFG["windows"])
        if os.path.isfile(self.path):
            with open(self.path, encoding="utf-8") as f:
                for line in f:
                    try:
                        row = json.loads(line)
                    except ValueError:
                        continue
                    if row.get("t", 0) >= horizon:
                        self.rows.append(row)

    def used(self, window, now=None):
        cut = (now or time.time()) - window["seconds"]
        rows = [r for r in self.rows if r["t"] >= cut]
        return sum(r["tok"] for r in rows), len(rows)

    def reserve(self, est):
        now = time.time()
        with self.lock:
            for w in CFG["windows"]:
                tok, req = self.used(w, now)
                if tok + est > w["tokens"] * CFG["margin"] or req + 1 > w["requests"] * CFG["margin"]:
                    raise BudgetError(f"chạm trần cửa sổ {w['name']} (đã dùng {tok} token, {req} request)")
            row = {"t": now, "tok": est}
            self.rows.append(row)
            return row

    def release(self, row):
        with self.lock:
            if row in self.rows:
                self.rows.remove(row)

    def settle(self, row, actual, extra):
        with self.lock:
            row.update(extra)
            row["tok"] = actual
            os.makedirs(os.path.dirname(self.path), exist_ok=True)
            with open(self.path, "a", encoding="utf-8") as f:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")

    def report(self):
        lines = []
        for w in CFG["windows"]:
            tok, req = self.used(w)
            lines.append(f"{w['name']:>3}: {tok:,}/{w['tokens']:,} token · {req}/{w['requests']} request")
        return "\n".join(lines)


def post(body, key):
    req = urllib.request.Request(
        CFG["endpoint"],
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "anthropic-version": "2023-06-01",
            "Content-Type": "application/json",
        },
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=CFG["timeout"]) as resp:
                return json.load(resp)
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 504) and attempt < 3:
                time.sleep(min(float(e.headers.get("Retry-After") or 2 ** (attempt + 1)), 60))
                continue
            raise ApiError(e.code, e.read().decode("utf-8", "replace")[:300])
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt < 3:
                time.sleep(2 ** (attempt + 1))
                continue
            raise ApiError(0, str(e))


def read_text(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def run_job(budget, key, tier, prompt, files=(), system=None, max_tokens=None, model=None):
    """Một lần gọi. Phần cố định (system, file) đứng trước, chỉ thị đứng cuối để trúng cache."""
    model = model or CFG["routes"][tier]
    max_tokens = max_tokens or CFG["maxTokens"][tier]
    user = "\n".join([f'<file path="{p}">\n{read_text(p)}\n</file>' for p in files] + [prompt])
    for attempt in range(2):
        row = budget.reserve((len(user) + len(system or "")) // 3 + max_tokens)
        body = {"model": model, "max_tokens": max_tokens, "messages": [{"role": "user", "content": user}]}
        if system:
            body["system"] = [{"type": "text", "text": system, "cache_control": {"type": "ephemeral"}}]
        try:
            resp = post(body, key)
        except Exception:
            budget.release(row)
            raise
        u = resp.get("usage", {})
        fresh = u.get("input_tokens", 0) + u.get("cache_creation_input_tokens", 0)
        cached = u.get("cache_read_input_tokens", 0)
        budget.settle(row, fresh + u.get("output_tokens", 0), {"model": model, "in": fresh, "cache": cached, "out": u.get("output_tokens", 0)})
        text = "".join(b.get("text", "") for b in resp.get("content", []) if b.get("type") == "text")
        # suy nghĩ ăn hết max_tokens thì chưa có chữ nào: gọi lại một lần với trần gấp đôi
        if text or resp.get("stop_reason") != "max_tokens" or attempt:
            break
        max_tokens = min(max_tokens * 2, CFG["maxTokensCap"])
    total_in = fresh + cached
    return {
        "text": text,
        "model": model,
        "new": fresh + u.get("output_tokens", 0),
        "out": u.get("output_tokens", 0),
        "cache": round(100 * cached / total_in) if total_in else 0,
        "truncated": resp.get("stop_reason") == "max_tokens",
    }


def write_out(path, text):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def summary(r, out=None):
    tail = f" -> {out}" if out else ""
    warn = " CẮT-CỤT" if r["truncated"] else ""
    return f"ok {r['model']} mới={r['new']} ra={r['out']} cache={r['cache']}%{warn}{tail}"


def cmd_run(args):
    budget, key = Budget(), api_key()
    r = run_job(budget, key, args.tier, args.prompt, args.input or (), read_text(args.system) if args.system else None, args.max_tokens, args.model)
    if args.out:
        write_out(args.out, r["text"])
        print(summary(r, args.out))
    else:
        print(r["text"])
        print(summary(r), file=sys.stderr)


def cmd_batch(args):
    budget, key = Budget(), api_key()
    with open(args.jobs, encoding="utf-8") as f:
        jobs = [json.loads(line) for line in f if line.strip()]
    jobs.sort(key=lambda j: (j.get("system_file", ""), j.get("tier", "simple")))  # cùng tiền tố đứng cạnh nhau
    stop = threading.Event()

    def one(job):
        jid = job.get("id", job["out"])
        if stop.is_set():
            return jid, "bỏ-qua: chạm trần", None
        try:
            system = read_text(job["system_file"]) if job.get("system_file") else None
            r = run_job(budget, key, job.get("tier", "simple"), job["prompt"], job.get("files", ()), system, job.get("max_tokens"), job.get("model"))
            write_out(job["out"], r["text"])
            return jid, summary(r, job["out"]), r
        except BudgetError as e:
            stop.set()
            return jid, f"bỏ-qua: {e}", None
        except Exception as e:
            return jid, f"lỗi: {e}", None

    with ThreadPoolExecutor(max_workers=args.workers or CFG["workers"]) as pool:
        results = list(pool.map(one, jobs))
    for jid, msg, _ in results:
        print(f"{jid}: {msg}")
    done = [r for _, _, r in results if r]
    print(f"--- {len(done)}/{len(jobs)} xong · mới={sum(r['new'] for r in done)} token")
    print(budget.report())


def cmd_usage(_args):
    print(Budget().report())
    print(f"gói hết hạn: {CFG['planEnds']} · lề an toàn: {int(CFG['margin'] * 100)}%")


TOOLS = [
    {
        "name": "deepseek_chat",
        "description": "Giao việc cho DeepSeek (flash=việc đơn giản/số lượng, pro=việc khó). Nội dung nặng nên truyền qua files và out_file để khỏi vào ngữ cảnh.",
        "inputSchema": {
            "type": "object",
            "required": ["prompt"],
            "properties": {
                "prompt": {"type": "string"},
                "tier": {"type": "string", "enum": ["simple", "hard"], "default": "simple"},
                "files": {"type": "array", "items": {"type": "string"}},
                "system": {"type": "string"},
                "out_file": {"type": "string"},
                "max_tokens": {"type": "integer"},
            },
        },
    },
    {"name": "deepseek_usage", "description": "Xem mức dùng theo cửa sổ 5h/24h/7d của gói.", "inputSchema": {"type": "object", "properties": {}}},
]


def call_tool(budget, name, a):
    if name == "deepseek_usage":
        return budget.report()
    if name != "deepseek_chat":
        raise ApiError(0, f"không có tool {name}")
    r = run_job(budget, api_key(), a.get("tier", "simple"), a["prompt"], a.get("files", ()), a.get("system"), a.get("max_tokens"))
    if a.get("out_file"):
        write_out(a["out_file"], r["text"])
        return summary(r, a["out_file"])
    return r["text"] + "\n\n" + summary(r)


def cmd_mcp(_args):
    budget = Budget()
    for line in sys.stdin:
        if not line.strip():
            continue
        msg = json.loads(line)
        if msg.get("id") is None:
            continue  # thông báo, không cần trả lời
        method, resp = msg.get("method"), {"jsonrpc": "2.0", "id": msg["id"]}
        try:
            if method == "initialize":
                resp["result"] = {
                    "protocolVersion": msg.get("params", {}).get("protocolVersion", "2024-11-05"),
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "ds", "version": "1.0"},
                }
            elif method == "ping":
                resp["result"] = {}
            elif method == "tools/list":
                resp["result"] = {"tools": TOOLS}
            elif method == "tools/call":
                p = msg["params"]
                try:
                    resp["result"] = {"content": [{"type": "text", "text": call_tool(budget, p["name"], p.get("arguments", {}))}]}
                except (ApiError, BudgetError, OSError) as e:
                    resp["result"] = {"content": [{"type": "text", "text": f"lỗi: {e}"}], "isError": True}
            else:
                resp["error"] = {"code": -32601, "message": f"không hỗ trợ {method}"}
        except Exception as e:
            resp["error"] = {"code": -32603, "message": str(e)}
        sys.stdout.write(json.dumps(resp, ensure_ascii=False) + "\n")
        sys.stdout.flush()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run", help="một lần gọi")
    r.add_argument("-t", "--tier", choices=["simple", "hard"], default="simple")
    r.add_argument("-m", "--model")
    r.add_argument("-s", "--system", help="file chứa system prompt cố định (để trúng cache)")
    r.add_argument("-i", "--input", nargs="*", help="file đưa vào ngữ cảnh")
    r.add_argument("-p", "--prompt", required=True)
    r.add_argument("-o", "--out", help="ghi kết quả ra file, chỉ in một dòng tóm tắt")
    r.add_argument("--max-tokens", type=int)
    r.set_defaults(fn=cmd_run)
    b = sub.add_parser("batch", help="chạy nhiều việc song song từ file jsonl")
    b.add_argument("jobs", help='mỗi dòng: {"id","tier","system_file","files","prompt","out"}')
    b.add_argument("-w", "--workers", type=int)
    b.set_defaults(fn=cmd_batch)
    sub.add_parser("usage", help="mức dùng theo cửa sổ").set_defaults(fn=cmd_usage)
    sub.add_parser("mcp", help="chạy như MCP server stdio").set_defaults(fn=cmd_mcp)
    args = ap.parse_args()
    try:
        args.fn(args)
    except (ApiError, BudgetError, OSError) as e:
        sys.exit(f"lỗi: {e}")


if __name__ == "__main__":
    main()
