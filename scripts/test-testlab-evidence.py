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

    def test_collection_requires_images_and_logs_for_both_devices(self):
        import contextlib
        import io
        import json
        import tempfile
        from unittest import mock
        prefix = 'gs://private-fixture/v56-123-1/'
        objects = {}
        for device in ('cubs', 'grizzly'):
            root = prefix + device + '/artifacts/v56/'
            rows = [{'game': f'K{n}', 'status': 'PASS'} for n in range(1, 9)]
            objects[root + 'summary.json'] = json.dumps({'synthetic': True, 'model': device, 'sdk': 37,
                'abi': 'arm64-v8a', 'rows': rows}).encode()
            objects[root + 'metadata.json'] = b'{"synthetic":true}'
            for row in rows:
                for suffix in ('-final.png', '-pid.log', '-crash.log'):
                    objects[root + row['game'] + suffix] = b'synthetic evidence'
            for game in ('K3', 'K5', 'K7', 'K8'):
                objects[root + game + '.png'] = b'synthetic image fixture'
        listing = list(objects) + [prefix + 'input.apk', prefix + 'artifacts/v56/game.zip']

        class FakeDownload:
            def __init__(self, command, **kwargs):
                assert command[:2] == ['gsutil', 'cat']
                assert command[2] in objects
                self.stdout = io.BytesIO(objects[command[2]])
                self.code = 0
            def kill(self):
                self.code = -9
            def wait(self):
                return self.code

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            result = root / 'transport.json'
            result.write_text('gs://private-fixture/transport')
            def run_collect(destination):
                with mock.patch.dict(evidence.os.environ, {'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1'}), \
                     mock.patch.object(evidence.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout='\n'.join(listing))), \
                     mock.patch.object(evidence.subprocess, 'Popen', side_effect=FakeDownload), contextlib.redirect_stdout(io.StringIO()):
                    evidence.collect(result, root / 'stderr', destination)
            run_collect(root / 'ok')
            report = json.loads((root / 'ok/collection.json').read_text())
            self.assertEqual('COLLECTED', report['collection'])
            self.assertEqual(2, len(report['devices']))
            self.assertFalse(list((root / 'ok').rglob('*.apk')))
            self.assertFalse(list((root / 'ok').rglob('*.zip')))
            del objects[prefix + 'cubs/artifacts/v56/K8-final.png']
            listing = list(objects)
            with self.assertRaises(SystemExit):
                run_collect(root / 'missing-image')
            self.assertEqual('BLOCKED', json.loads((root / 'missing-image/collection.json').read_text())['collection'])

    def test_total_budget_below_download_limit(self):
        self.assertLess(evidence.MAX_TOTAL, 32 * 1024 * 1024)
        self.assertLessEqual(evidence.MAX_FILE, evidence.MAX_TOTAL)


if __name__ == '__main__':
    unittest.main()
