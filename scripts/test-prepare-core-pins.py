#!/usr/bin/env python3
"""Offline tests with generated config/ZIP bytes and a patched URL opener."""
import hashlib
import io
import json
import runpy
import sys
import tempfile
import unittest
import urllib.request
import zipfile
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("prepare-core-pins.py")
CORES = ("gambatte", "mgba", "fceumm")


def fake_archive(core):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr(f"{core}_libretro_android.so", b"synthetic shared object")
    return stream.getvalue()


class PrepareCorePinsOfflineTest(unittest.TestCase):
    def test_creates_candidate_records_from_synthetic_archives_without_network(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = root / "config.json"
            output = root / "snapshot"
            definitions = {
                core: {"url": f"https://offline.invalid/{core}/{{abi}}", "abis": ["arm64-v8a"]}
                for core in CORES
            }
            config.write_text(json.dumps({"cores": definitions}), encoding="utf-8")
            calls = []

            def fake_urlopen(url, timeout):
                self.assertTrue(url.startswith("https://offline.invalid/"))
                self.assertEqual(120, timeout)
                calls.append(url)
                core = url.split("/")[-2]
                return io.BytesIO(fake_archive(core))

            with mock.patch.object(urllib.request, "urlopen", side_effect=fake_urlopen), \
                    mock.patch.object(sys, "argv", [str(SCRIPT), "--config", str(config), "--out", str(output)]):
                runpy.run_path(str(SCRIPT), run_name="__main__")

            self.assertEqual(3, len(calls))
            records = json.loads((output / "candidates.json").read_text(encoding="utf-8"))
            self.assertEqual(list(CORES), [record["core"] for record in records])
            for record in records:
                archive = output / record["file"]
                self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(), record["sha256"])
                self.assertTrue(record["version"].startswith("sha256-"))
                self.assertIn("CHƯA KIỂM", record["fixedUrl"])

    def test_rejects_archive_missing_expected_synthetic_library(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = root / "config.json"
            output = root / "snapshot"
            config.write_text(json.dumps({"cores": {
                core: {"url": f"https://offline.invalid/{core}/{{abi}}", "abis": ["arm64-v8a"]}
                for core in CORES
            }}), encoding="utf-8")
            wrong = io.BytesIO()
            with zipfile.ZipFile(wrong, "w") as archive:
                archive.writestr("wrong.so", b"synthetic")
            with mock.patch.object(urllib.request, "urlopen", return_value=io.BytesIO(wrong.getvalue())), \
                    mock.patch.object(sys, "argv", [str(SCRIPT), "--config", str(config), "--out", str(output)]):
                with self.assertRaisesRegex(AssertionError, "thiếu SO chính"):
                    runpy.run_path(str(SCRIPT), run_name="__main__")


if __name__ == "__main__":
    unittest.main()
