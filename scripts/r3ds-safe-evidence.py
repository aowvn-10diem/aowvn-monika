"""Bound transport and export budgets for scrubbed 3DS text evidence only."""
import json
import re
import selectors
import subprocess
import time

MAX_FILE = 1024 * 1024
MAX_TOTAL = 20 * 1024 * 1024
MAX_LIST = 1024 * 1024
SYSTEM_FILES = ('logcat', 'instrumentation.results', 'test_result_1.xml')
NAMES = re.compile(r'(?:summary\.json|run\.json|game-meta\.txt|t\d+\.txt|R[1-3]-(?:exit|crumbs)\.txt|R[1-3]-(?:pid|crash)\.log)')
INTERESTING = re.compile(r'FATAL|Fatal signal|SIGSEGV|SIGABRT|AzaharActivity|vn\.aow|com\.aow|Monika|azahar|citra|R3DS|am_crash|am_proc_died|lowmemorykiller|ANR|Instrumentation|INSTRUMENTATION|Process crashed', re.I)


class EvidenceError(Exception):
    pass


def read_bounded(cmd, limit, timeout=30, missing_ok=False):
    if limit < 0:
        raise EvidenceError('byte budget exhausted')
    process = subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    deadline = time.monotonic() + timeout
    data = bytearray()
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0 or not selector.select(remaining):
                    raise EvidenceError('evidence read timed out')
                chunk = process.stdout.read1(min(65536, limit + 1 - len(data)))
                if not chunk:
                    break
                data.extend(chunk)
                if len(data) > limit:
                    raise EvidenceError('evidence exceeded byte budget')
        try:
            code = process.wait(timeout=max(0.01, deadline - time.monotonic()))
        except subprocess.TimeoutExpired as error:
            raise EvidenceError('evidence process timed out') from error
        if code:
            if missing_ok and code == 1 and not data:
                return b''
            raise EvidenceError('evidence transport failed')
        return bytes(data)
    finally:
        if process.poll() is None:
            process.kill()
        process.wait()
        process.stdout.close()


class Budget:
    def __init__(self):
        self.raw = self.exported = 0
        self.deadline = time.monotonic() + 120

    def read(self, cmd, limit=MAX_FILE, missing_ok=False):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise EvidenceError('evidence batch timed out')
        data = read_bounded(cmd, min(limit, MAX_TOTAL - self.raw), min(30, remaining), missing_ok)
        self.raw += len(data)
        return data

    def export(self, data):
        if len(data) > MAX_FILE or self.exported + len(data) > MAX_TOTAL - 4096:
            raise EvidenceError('redacted evidence exceeded byte budget')
        self.exported += len(data)
        return data


def objects(data, prefix):
    try:
        lines = data.decode('utf-8').splitlines()
    except UnicodeError as error:
        raise EvidenceError('invalid evidence listing') from error
    uris = [line for line in lines if line.startswith('gs://') and not line.endswith(('/', ':'))]
    if len(uris) > 200 or len(set(uris)) != len(uris):
        raise EvidenceError('invalid evidence listing size or duplicates')
    selected = []
    for uri in uris:
        if not uri.startswith(prefix):
            raise EvidenceError('evidence prefix mismatch')
        parts = uri[len(prefix):].split('/')
        if any(part in ('', '.', '..') or not re.fullmatch(r'[A-Za-z0-9_.-]+', part) for part in parts):
            raise EvidenceError('invalid evidence path')
        if parts[-1] in SYSTEM_FILES or ('r3ds' in parts[:-1] and NAMES.fullmatch(parts[-1])):
            selected.append(uri)
    return selected


def redact(data, name, scrub, scrub_json):
    try:
        text = data.decode('utf-8')
        if '\x00' in text:
            raise ValueError('binary evidence')
        if name.endswith('.json'):
            def clean(value):
                if isinstance(value, str):
                    return re.sub(r'gs://\S+', '<private-uri>', scrub(value))
                if isinstance(value, list):
                    return [clean(item) for item in value]
                if isinstance(value, dict):
                    return {clean(str(key)): ('<hidden>' if re.search(r'(?i)token|password|secret|authorization|api[_-]?key|bucket|project', str(key)) else clean(item)) for key, item in value.items()}
                return value
            text = json.dumps(clean(scrub_json(json.loads(text))), ensure_ascii=False, indent=2)
        else:
            if name in SYSTEM_FILES:
                text = '\n'.join(line for line in text.splitlines() if INTERESTING.search(line))
                if not text:
                    raise ValueError('no relevant evidence')
            text = scrub(text)
    except (UnicodeError, ValueError, RecursionError) as error:
        raise EvidenceError('invalid text or JSON evidence') from error
    if not name.endswith('.json'):
        text = re.sub(r'gs://\S+', '<private-uri>', text)
    return (text + '\n').encode('utf-8')
