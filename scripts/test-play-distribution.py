#!/usr/bin/env python3
"""Test offline cho ranh giới phân phối và bộ kiểm artifact, không cần SDK."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


policy = module('play-config')
scanner = module('check-play-apk')


class PlayDistributionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name, 'play.apk')
        self.source = json.loads((ROOT / 'config/monika-config.json').read_text())
        self.original = copy.deepcopy(self.source)
        self.cfg = policy.for_play(self.source)

    def fixture(self, dex=b'dex fixture', extra=None, manifest=None, cfg=None):
        contents = {
            'AndroidManifest.xml': manifest or b'<manifest package="com.aow.monika.play"/>',
            'classes.dex': dex,
            'assets/monika-config.json': json.dumps(self.cfg if cfg is None else cfg).encode(),
            **(extra or {}),
        }
        with zipfile.ZipFile(self.apk, 'w') as archive:
            for name, data in contents.items(): archive.writestr(name, data)

    def test_source_is_unchanged_and_play_has_no_external_distribution(self):
        self.fixture()
        scanner.check(self.apk)
        self.assertEqual(self.original, self.source)
        self.assertTrue(self.source['app']['apkUrl'])
        self.assertTrue(self.source['account']['donate'])
        self.assertTrue(all(not s.get('allowExternalApp') for s in self.cfg['systems']))

    def test_every_forbidden_class_is_rejected_even_without_r8(self):
        for marker in scanner.FORBIDDEN_CLASSES:
            with self.subTest(marker=marker):
                self.fixture(dex=b'dex fixture' + marker)
                with self.assertRaises(ValueError): scanner.check(self.apk)

    def test_forbidden_strings_are_rejected_in_both_resource_encodings(self):
        for text in scanner.FORBIDDEN_TEXT:
            for encoding in ('utf-8', 'utf-16le'):
                with self.subTest(text=text, encoding=encoding):
                    self.fixture(extra={'resources.arsc': text.encode(encoding)})
                    with self.assertRaises(ValueError): scanner.check(self.apk)

    def test_restricted_permissions_and_queries_are_rejected(self):
        for value in scanner.FORBIDDEN_PERMISSIONS + ('<queries/>',):
            self.fixture(manifest=('<manifest package="com.aow.monika.play">' + value + '</manifest>').encode())
            with self.assertRaises(ValueError): scanner.check(self.apk)

    def test_restored_external_config_is_rejected(self):
        self.fixture(cfg=self.source)
        with self.assertRaises(ValueError): scanner.check(self.apk)

    def test_github_identity_and_injected_loader_are_rejected(self):
        self.fixture(manifest=b'<manifest package="com.aow.monika"/>')
        with self.assertRaises(ValueError): scanner.check(self.apk)
        self.fixture(extra={'assets/monika-loader.dex': b'loader fixture'})
        with self.assertRaises(ValueError): scanner.check(self.apk)


if __name__ == '__main__': unittest.main()
