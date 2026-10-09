#!/usr/bin/env python3
"""Baseline may already be hardened; the resulting manifest must always be closed."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
spec = importlib.util.spec_from_file_location("surface", Path(__file__).with_name("check-j2me-manifest.py"))
surface = importlib.util.module_from_spec(spec)
spec.loader.exec_module(surface)

class SurfaceTest(unittest.TestCase):
    def manifest(self, exported):
        activities = "".join(f'<activity android:name="{name}" android:exported="{exported}"/>' for name in surface.CLOSED)
        activities += '<activity android:name="ru.woesss.j2me.installer.MonikaLaunchActivity" android:exported="false"/><activity android:name="javax.microedition.shell.MicroActivity" android:exported="false"/>'
        return f'<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application>{activities}<activity android:name="vn.aow.monika.ui.MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.VIEW"/><data android:mimeType="application/java-archive"/><data android:mimeType="text/vnd.sun.j2me.app-descriptor"/></intent-filter></activity></application></manifest>'
    def inspect(self, xml, hardened):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.xml"
            path.write_text(xml)
            return surface.inspect(path, hardened)
    def test_baseline_records_old_and_already_closed_components(self):
        for exported in ("true", "false"):
            with self.subTest(exported=exported):
                result = self.inspect(self.manifest(exported), False)
                self.assertEqual({item["exported"] for item in result if item["name"] in surface.CLOSED}, {exported == "true"})
    def test_final_requires_closed_components(self):
        self.inspect(self.manifest("false"), True)
        with self.assertRaisesRegex(AssertionError, "unexpected exported"):
            self.inspect(self.manifest("true"), True)
    def test_view_contract_and_unambiguous_exported_stay_required(self):
        for xml in (self.manifest("false").replace("application/java-archive", "wrong"), self.manifest("false").replace('android:exported="false"', 'android:exported="unknown"', 1)):
            with self.assertRaises(AssertionError):
                self.inspect(xml, False)

if __name__ == "__main__":
    unittest.main()
