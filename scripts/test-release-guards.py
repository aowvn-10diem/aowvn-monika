#!/usr/bin/env python3
"""Chạy đúng hai cửa chặn trong release.yml bằng dữ liệu giả; không ký/build/publish."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

WORKFLOW = Path(__file__).resolve().parents[1] / '.github/workflows/release.yml'
TEXT = WORKFLOW.read_text()

def block(step_id):
    pattern = rf'^        id: {step_id}\n.*?^        run: \|\n(.*?)(?=^      - |\Z)'
    found = re.search(pattern, TEXT, re.M | re.S)
    if not found:
        raise AssertionError(f'Thiếu cửa chặn {step_id}')
    return textwrap.dedent(found.group(1))

class Guards(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.cwd = Path(self.tmp.name)
        self.env = dict(os.environ)
        self.env.update(TAG='v1.2.3', KEYSTORE_BASE64='fixture-only', MONIKA_KEYSTORE_PASSWORD='fixture-only',
                        MONIKA_KEY_ALIAS='fixture-only', MONIKA_KEY_PASSWORD='fixture-only',
                        RUNNER_TEMP=str(self.cwd), ANDROID_HOME=str(self.cwd / 'sdk'))

    def run_guard(self, step):
        return subprocess.run(['bash', '-c', block(step)], cwd=self.cwd, env=self.env,
                              text=True, capture_output=True)

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.cwd, text=True, check=True, capture_output=True)

    def repo(self):
        self.git('init'); self.git('config', 'user.name', 'Fixture'); self.git('config', 'user.email', 'fixture@example.test')
        self.git('commit', '--allow-empty', '-m', 'fixture'); self.git('tag', 'v1.2.3')

    def test_missing_each_signing_field_stops_dispatch_and_push(self):
        for event in ('workflow_dispatch', 'push'):
            for key in ('KEYSTORE_BASE64', 'MONIKA_KEYSTORE_PASSWORD', 'MONIKA_KEY_ALIAS', 'MONIKA_KEY_PASSWORD'):
                self.env['GITHUB_EVENT_NAME'] = event
                old = self.env.pop(key)
                result = self.run_guard('release_guard')
                self.env[key] = old
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Thiếu cấu hình ký', result.stdout)
                self.assertNotIn('fixture-only', result.stdout + result.stderr)

    def test_tag_must_exist_and_match_head(self):
        self.repo()
        self.assertEqual(self.run_guard('release_guard').returncode, 0)
        self.git('commit', '--allow-empty', '-m', 'wrong-head')
        self.assertNotEqual(self.run_guard('release_guard').returncode, 0)
        self.env['TAG'] = 'v9.9.9'
        self.assertNotEqual(self.run_guard('release_guard').returncode, 0)
        self.env['TAG'] = 'main; echo bad'
        self.assertNotEqual(self.run_guard('release_guard').returncode, 0)

    def signer(self, output, exit_code=0, verify_code=0):
        signer = self.cwd / 'sdk/build-tools/35.0.0/apksigner'
        signer.parent.mkdir(parents=True, exist_ok=True)
        signer.write_text('#!/bin/sh\nprintf "%s\\n" "$SIM_CERT"\nexit '+str(exit_code)+'\n')
        signer.chmod(0o755)
        self.env['SIM_CERT'] = output
        # APK giả không có khối chữ ký thật: thay verify-apk-cert.py bằng bản giả trả mã theo ca thử.
        stub = self.cwd / 'scripts/verify-apk-cert.py'
        stub.parent.mkdir(parents=True, exist_ok=True)
        stub.write_text('import sys\nsys.exit('+str(verify_code)+')\n')
        (self.cwd / 'AowVN-Monika-v1.2.3.apk').write_bytes(b'fixture APK, not Android')
        (self.cwd / 'mapping-v1.2.3.txt').write_text('fixture mapping')

    def test_debug_certificate_stops_before_notes(self):
        self.signer('Signer #1 certificate DN: CN=Android Debug, O=Android\nSigner #1 certificate SHA-256 digest: fake')
        self.assertNotEqual(self.run_guard('apk_guard').returncode, 0)
        self.assertFalse((self.cwd / 'release-notes-v1.2.3.md').exists())

    def test_unsigned_or_unreadable_certificate_stops(self):
        for code, verify in ((0, 1), (1, 0), (1, 1)):
            self.signer('unreadable fixture', code, verify)
            result = self.run_guard('apk_guard')
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('unreadable fixture', result.stdout)

    def test_notes_contain_both_real_file_hashes(self):
        self.signer('Signer #1 certificate DN: CN=Fixture\nSigner #1 certificate SHA-256 digest: fake')
        self.assertEqual(self.run_guard('apk_guard').returncode, 0)
        notes = (self.cwd / 'release-notes-v1.2.3.md').read_text()
        for name in ('AowVN-Monika-v1.2.3.apk', 'mapping-v1.2.3.txt'):
            digest = hashlib.sha256((self.cwd / name).read_bytes()).hexdigest()
            self.assertIn(f'{digest}  {name}', notes)

    def test_workflow_checks_tag_and_guards_before_publish(self):
        self.assertIn('ref: refs/tags/${{ inputs.tag || github.ref_name }}', TEXT)
        self.assertLess(TEXT.index('id: release_guard'), TEXT.index('Giải mã khóa ký'))
        self.assertLess(TEXT.index('id: apk_guard'), TEXT.index('softprops/action-gh-release'))
        self.assertIn('body_path: release-notes-${{ env.TAG }}.md', TEXT)
        self.assertNotIn('ký bằng khóa debug (bản thử)', TEXT)

    def test_dry_run_accepts_only_dispatch_main_exact_sha(self):
        self.repo()
        sha=self.git('rev-parse','HEAD').stdout.strip()
        self.env.update(DISPATCH_EVENT='workflow_dispatch',DISPATCH_REF='refs/heads/main',DISPATCH_SHA=sha)
        self.assertEqual(self.run_guard('dry_guard').returncode,0)
        for key,bad in [('DISPATCH_EVENT','push'),('DISPATCH_REF','refs/heads/sol/V43'),('DISPATCH_SHA','0'*40),('DISPATCH_SHA','bad; echo bad')]:
            old=self.env[key];self.env[key]=bad
            self.assertNotEqual(self.run_guard('dry_guard').returncode,0)
            self.env[key]=old

    def test_dry_run_missing_signing_field_never_prints_values(self):
        for key in ('KEYSTORE_BASE64','MONIKA_KEYSTORE_PASSWORD','MONIKA_KEY_ALIAS','MONIKA_KEY_PASSWORD'):
            old=self.env.pop(key);r=self.run_guard('dry_signing_guard');self.env[key]=old
            self.assertNotEqual(r.returncode,0)
            self.assertIn('Thiếu cấu hình ký',r.stdout)
            self.assertNotIn('fixture-only',r.stdout+r.stderr)

    def test_dry_run_has_no_publish_or_upload_path(self):
        dry=TEXT.split('  signing_dry_run:',1)[1]
        self.assertIn('permissions: {contents: read}',dry)
        for token in ['softprops/action-gh-release','actions/upload-artifact','pixeldrain','gh release','git push']:
            self.assertNotIn(token,dry)
        self.assertIn("if: github.event_name != 'workflow_dispatch' || inputs.dry_run != true",TEXT)
        self.assertIn("if: github.event_name == 'workflow_dispatch' && inputs.dry_run == true",dry)
        self.assertIn('python3 scripts/verify-apk-cert.py',dry)
        self.assertLess(dry.index('id: dry_guard'),dry.index('secrets.MONIKA_KEYSTORE_BASE64'))

if __name__ == '__main__':
    unittest.main()
