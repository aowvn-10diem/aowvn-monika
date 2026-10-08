#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('evidence', Path(__file__).with_name('collect-testlab-evidence.py'))
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class EvidenceContract(unittest.TestCase):
    def test_only_own_evidence_names(self):
        for name in ('summary.json', 'metadata.json', 'K3.png', 'K8-final.png', 'K2-pid.log', 'K3-crash.log',
                     'K3-1234-tombstone.pb', 's3-monika-load.txt'):
            self.assertTrue(evidence.allowed('gs://bucket/results/artifacts/v56/' + name), name)

    def test_reject_inputs_other_directories_and_traversal(self):
        for path in ('app.apk', 'game.zip', 'startup.tjs', 'beep.wav', 'credential.json', 'logcat', '../summary.json',
                     'K9.png', 'K3-tombstone.pb', 'summary.json/child'):
            self.assertFalse(evidence.allowed('gs://bucket/results/v56/' + path), path)
        self.assertFalse(evidence.allowed('gs://bucket/results/summary.json'))
        self.assertFalse(evidence.allowed('https://bucket/results/v56/summary.json'))

    def test_text_redacted_before_export(self):
        sanitized = evidence.scrub('token=fakevalue\npassword=not-a-secret\nhttps://example.invalid/signed?q=secret\n/data/user/0/fake/files/a game\ntest@example.invalid')
        for text in ('fakevalue', 'not-a-secret', 'example.invalid', '/data/user', 'a game'):
            self.assertNotIn(text, sanitized)

    def test_nested_json_remains_valid(self):
        import json
        value = {'rows': [{'detail': 'at /data/user/0/fake/files/local name', 'status': 'FAIL'}], 'synthetic': True}
        result = json.loads(json.dumps(evidence.scrub_json(value)))
        self.assertEqual(result['rows'][0]['status'], 'FAIL')
        self.assertEqual(result['rows'][0]['detail'], 'at <private-path>')
        self.assertTrue(result['synthetic'])

    def test_total_budget_below_download_limit(self):
        self.assertLess(evidence.MAX_TOTAL, 32 * 1024 * 1024)
        self.assertLessEqual(evidence.MAX_FILE, evidence.MAX_TOTAL)


if __name__ == '__main__':
    unittest.main()
