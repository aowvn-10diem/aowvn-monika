"""Chạy script thật với Gradle/scanner giả để bắt việc báo ĐẠT sai."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parent
GATES = ['testDebugUnitTest', 'assembleDebug', 'assemblePlayDebug', 'scan']
FAKE = '''
import json,os,sys
from pathlib import Path
root=Path(__file__).resolve().parent
stage=sys.argv[1] if root.name!='scripts' else 'scan'
if stage=='scan': root=root.parent
with (root/'calls.jsonl').open('a',encoding='utf-8') as target:
    target.write(json.dumps(stage)+'\\n')
if os.environ.get('V175_TEST_FAIL_STAGE')==stage: sys.exit(23)
apk=root/'app/build/outputs/apk/play/debug/app-play-universal-debug.apk'
if stage=='assemblePlayDebug':
    apk.parent.mkdir(parents=True,exist_ok=True); apk.write_bytes(b'fixture')
if stage=='scan':
    assert Path(sys.argv[1]).resolve()==apk
    assert apk.read_bytes()==b'fixture'
'''


def shells():
    if os.name == 'nt':
        names = ('powershell', 'pwsh')
        for name in names:
            if not shutil.which(name):
                raise RuntimeError('Thiếu shell bắt buộc để kiểm Windows: ' + name)
        return [(name, [name, '-NoProfile', '-NonInteractive', '-File'],
                 'truoc-khi-push.ps1') for name in names]
    return [('bash', ['bash'], 'truoc-khi-push.sh')]


class PrePushGate(unittest.TestCase):
    def run_case(self, launch, filename, failure='', missing_wrapper=False):
        with tempfile.TemporaryDirectory(prefix='v175 kiểm ') as directory:
            root = Path(directory)
            (root / 'scripts').mkdir()
            shutil.copyfile(SCRIPTS / filename, root / 'scripts' / filename)
            (root / 'scripts/check-play-apk.py').write_text(FAKE, encoding='utf-8')
            if not missing_wrapper:
                (root / 'fake-gradle.py').write_text(FAKE, encoding='utf-8')
                if os.name == 'nt':
                    (root / 'gradlew.bat').write_text(
                        '@echo off\n"' + sys.executable + '" "%~dp0fake-gradle.py" %*\n'
                        'exit /b %errorlevel%\n', encoding='utf-8')
                else:
                    wrapper = root / 'gradlew'
                    wrapper.write_text('#!/usr/bin/env python3\n' + FAKE, encoding='utf-8')
                    wrapper.chmod(0o755)
            result = subprocess.run(
                launch + [str(root / 'scripts' / filename)],
                cwd=root.parent,
                env=dict(os.environ, V175_TEST_FAIL_STAGE=failure, PYTHONUTF8='1'),
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding='utf-8', timeout=30)
            log = root / 'calls.jsonl'
            calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
            return result, calls

    def test_success_runs_every_gate_before_final_success(self):
        for name, launch, filename in shells():
            with self.subTest(shell=name):
                result, calls = self.run_case(launch, filename)
                self.assertEqual(0, result.returncode, result.stdout)
                self.assertEqual(GATES, calls)
                self.assertTrue(result.stdout.splitlines()[-1].startswith('ĐẠT:'), result.stdout)

    def test_each_failed_gradle_gate_stops_and_preserves_exit_code(self):
        for name, launch, filename in shells():
            for index, gate in enumerate(GATES[:-1]):
                with self.subTest(shell=name, gate=gate):
                    result, calls = self.run_case(launch, filename, failure=gate)
                    self.assertEqual(23, result.returncode, result.stdout)
                    self.assertEqual(GATES[:index + 1], calls)
                    self.assertTrue(result.stdout.splitlines()[-1].startswith('LỖI:'), result.stdout)
                    self.assertNotIn('ĐẠT:', result.stdout)

    def test_failed_scanner_is_not_success(self):
        for name, launch, filename in shells():
            with self.subTest(shell=name):
                result, calls = self.run_case(launch, filename, failure='scan')
                self.assertEqual(23, result.returncode, result.stdout)
                self.assertEqual(GATES, calls)
                self.assertTrue(result.stdout.splitlines()[-1].startswith('LỖI:'), result.stdout)
                self.assertNotIn('ĐẠT:', result.stdout)

    def test_missing_wrapper_fails_without_running_later_gates(self):
        for name, launch, filename in shells():
            with self.subTest(shell=name):
                result, calls = self.run_case(launch, filename, missing_wrapper=True)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertEqual([], calls)
                self.assertTrue(result.stdout.splitlines()[-1].startswith('LỖI:'), result.stdout)


if __name__ == '__main__':
    unittest.main()
