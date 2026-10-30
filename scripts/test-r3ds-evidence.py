#!/usr/bin/env python3
"""Offline regressions: bounded child reads, hostile listings, scrub and fail-closed batches."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


collector = load('collector', 'collect-3ds-evidence.py')
safe = collector.safe
peek = load('peek', 'peek-testlab.py')
PREFIX = 'gs://fake/real3ds-123-1/'


class EvidenceTest(unittest.TestCase):
    def test_process_exact_limit(self):
        self.assertEqual(b'xxxx', safe.read_bounded([sys.executable, '-c', "print('xxxx',end='')"], 4))

    def test_process_overflow_killed(self):
        with self.assertRaisesRegex(safe.EvidenceError, 'budget'):
            safe.read_bounded([sys.executable, '-c', "import sys;sys.stdout.buffer.write(b'x'*1000000)"], 10)

    def test_process_deadline(self):
        with self.assertRaisesRegex(safe.EvidenceError, 'timed out'):
            safe.read_bounded([sys.executable, '-c', 'import time;time.sleep(10)'], 10, timeout=0.1)

    def test_nonzero_process_cannot_supply_evidence(self):
        with self.assertRaises(safe.EvidenceError):
            safe.read_bounded([sys.executable, '-c', "print('fake');raise SystemExit(2)"], 100)

    def test_binary_and_game_not_selected(self):
        names = ['device/r3ds/R1-1-tombstone.pb', 'game.3ds', 'app.apk', 'device/r3ds/R1-crumbs.txt']
        self.assertEqual([PREFIX + names[-1]], safe.objects(('\n'.join(PREFIX+n for n in names)).encode(), PREFIX))

    def test_wrong_scope_traversal_and_duplicates_rejected(self):
        for text in ('gs://other/r3ds/R1-crumbs.txt', PREFIX+'../r3ds/R1-crumbs.txt', '\n'.join([PREFIX+'r3ds/R1-crumbs.txt']*2)):
            with self.subTest(text=text), self.assertRaises(safe.EvidenceError):
                safe.objects(text.encode(), PREFIX)

    def test_scrub_json_stays_valid_and_hides_sensitive_keys(self):
        raw = json.dumps({'token': 'fake-value', 'note': 'gs://fake/object', 'ok': ['https://example.invalid/x', '/sdcard/fake']}).encode()
        value = json.loads(safe.redact(raw, 'summary.json', collector.v56.scrub, collector.v56.scrub_json))
        self.assertEqual('<hidden>', value['token'])
        self.assertEqual('<private-uri>', value['note'])
        self.assertNotIn('example.invalid', str(value))
        self.assertNotIn('/sdcard', str(value))

    def test_binary_or_malformed_json_rejected(self):
        for raw, name in [(b'\xff', 'R1-crash.log'), (b'abc\x00', 'R1-crumbs.txt'), (b'{bad}', 'summary.json')]:
            with self.subTest(name=name), self.assertRaises(safe.EvidenceError):
                safe.redact(raw, name, collector.v56.scrub, collector.v56.scrub_json)

    def test_raw_budget_shared_by_system_files(self):
        budget = safe.Budget()
        with patch.object(safe, 'MAX_TOTAL', 6), patch.object(safe, 'read_bounded', side_effect=lambda cmd, limit, *args: b'1234' if limit >= 4 else (_ for _ in ()).throw(safe.EvidenceError('budget'))):
            budget.read(['fake-system-file-1'])
            with self.assertRaises(safe.EvidenceError):
                budget.read(['fake-system-file-2'])

    def test_redaction_expansion_has_aggregate_budget(self):
        budget = safe.Budget()
        with patch.object(safe, 'MAX_TOTAL', 4100):
            budget.export(b'1234')
            with self.assertRaises(safe.EvidenceError):
                budget.export(b'5')

    def test_collector_removes_partial_export_after_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); result = root/'result'; result.write_text('gs://fake/result')
            listing = (PREFIX+'device/r3ds/R1-crumbs.txt\n'+PREFIX+'device/logcat').encode()
            with patch.object(safe, 'read_bounded', side_effect=[listing, b'R3DS token=fake', safe.EvidenceError('budget')]), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit):
                    collector.collect(result, root/'missing', root/'out', 'real3ds-123-1')
            report = json.loads((root/'out/collection.json').read_text())
            self.assertEqual(('BLOCKED', 0, 0), (report['collection'], report['files'], report['bytes']))
            self.assertEqual(['collection.json'], [p.name for p in (root/'out').iterdir()])

    def test_collector_removes_partial_file_after_write_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); result = root/'result'; result.write_text('gs://fake/result')
            listing = (PREFIX+'device/r3ds/R1-crumbs.txt').encode()
            original = Path.write_bytes
            def fail_after_partial(path, data):
                original(path, data[:1])
                raise OSError('simulated disk failure')
            with patch.object(safe, 'read_bounded', side_effect=[listing, b'R3DS OK']), patch.object(Path, 'write_bytes', fail_after_partial), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit):
                    collector.collect(result, root/'missing', root/'out', 'real3ds-123-1')
            self.assertEqual(['collection.json'], [p.name for p in (root/'out').iterdir()])
            self.assertEqual('BLOCKED', json.loads((root/'out/collection.json').read_text())['collection'])

    def test_collector_exports_only_scrubbed_counted_text(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); result = root/'result'; result.write_text('gs://fake/result')
            listing = (PREFIX+'device/r3ds/R1-crumbs.txt\n'+PREFIX+'device/logcat').encode()
            with patch.object(safe, 'read_bounded', side_effect=[listing, b'R3DS token=fake-value', b'unrelated\nR3DS /sdcard/fake']), contextlib.redirect_stdout(io.StringIO()):
                collector.collect(result, root/'missing', root/'out', 'real3ds-123-1')
            report = json.loads((root/'out/collection.json').read_text())
            files = [p for p in (root/'out').iterdir() if p.name != 'collection.json']
            self.assertEqual('COLLECTED', report['collection'])
            self.assertEqual(2, report['files'])
            self.assertEqual(sum(p.stat().st_size for p in files), report['bytes'])
            text = ''.join(p.read_text() for p in files)
            self.assertNotIn('fake-value', text)
            self.assertNotIn('/sdcard', text)
            self.assertNotIn('unrelated', text)
            self.assertFalse((root/'out/bucket-listing.txt').exists())

    def test_peek_can_find_scoped_evidence_after_empty_bucket(self):
        output = io.StringIO()
        listing = (PREFIX+'device/r3ds/R1-crumbs.txt').encode()
        with patch.object(sys, 'argv', ['peek', '--run-id', '123']), patch.object(peek.safe, 'read_bounded', side_effect=[b'gs://empty/\ngs://fake/\n', b'', listing, b'R3DS token=fake-value']), contextlib.redirect_stdout(output):
            peek.main()
        self.assertIn('COLLECTED', output.getvalue())
        self.assertNotIn('gs://', output.getvalue())
        self.assertNotIn('fake-value', output.getvalue())

    def test_peek_no_evidence_is_failure_without_private_listing(self):
        output = io.StringIO()
        with patch.object(sys, 'argv', ['peek', '--run-id', '123']), patch.object(peek.safe, 'read_bounded', side_effect=[b'gs://fake/\n', b'']), contextlib.redirect_stdout(output):
            with self.assertRaises(SystemExit) as error:
                peek.main()
        self.assertEqual(1, error.exception.code)
        self.assertEqual('3DS evidence: BLOCKED\n', output.getvalue())

    def test_peek_prints_nothing_from_partial_batch(self):
        listing = (PREFIX+'device/r3ds/R1-crumbs.txt\n'+PREFIX+'device/logcat').encode()
        output = io.StringIO()
        with patch.object(sys, 'argv', ['peek', '--run-id', '123']), patch.object(peek.safe, 'read_bounded', side_effect=[b'gs://fake/\n', listing, b'R3DS OK', peek.safe.EvidenceError('budget')]), contextlib.redirect_stdout(output):
            with self.assertRaises(SystemExit):
                peek.main()
        self.assertEqual('3DS evidence: BLOCKED\n', output.getvalue())


if __name__ == '__main__':
    unittest.main()
