#!/usr/bin/env python3
"""Run the generator only in a temporary synthetic repository tree."""
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("gen-architecture.py")


class GenArchitectureOfflineTest(unittest.TestCase):
    def test_generates_package_edges_and_manifest_in_temporary_tree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "scripts").mkdir()
            shutil.copyfile(SCRIPT, root / "scripts/gen-architecture.py")
            source = root / "app/src/main/java/vn/aow/monika"
            (source / "library").mkdir(parents=True)
            (source / "feed").mkdir()
            (source / "Root.kt").write_text("package vn.aow.monika\nclass AppGraph\n", encoding="utf-8")
            (source / "library/Library.kt").write_text(
                "package vn.aow.monika.library\nimport vn.aow.monika.AppGraph\n"
                "import vn.aow.monika.feed.Feed\nclass Library { val a = Feed(); val b = AppGraph() }\n",
                encoding="utf-8",
            )
            (source / "feed/Feed.kt").write_text("package vn.aow.monika.feed\nclass Feed\n", encoding="utf-8")
            (root / "settings.gradle.kts").write_text('include(":app", ":sample")\n', encoding="utf-8")
            (root / "app/src/main/AndroidManifest.xml").write_text(
                '<manifest><application><activity android:name=".MainActivity"/></application></manifest>',
                encoding="utf-8",
            )
            (root / "docs").mkdir()
            (root / "docs/KIEN-TRUC-tay.md").write_text("Luồng tổng hợp viết tay.\n", encoding="utf-8")

            completed = subprocess.run(
                [sys.executable, str(root / "scripts/gen-architecture.py")],
                cwd=root, capture_output=True, text=True, check=True,
            )
            report = (root / "docs/KIEN-TRUC.md").read_text(encoding="utf-8")

            self.assertIn('`:sample`', report)
            self.assertIn('`library`', report)
            self.assertIn('`feed`', report)
            self.assertIn('feed(1), (gốc)(1)', report)
            self.assertIn('`.MainActivity`', report)
            self.assertIn("Luồng tổng hợp viết tay.", report)
            self.assertIn("OK docs/KIEN-TRUC.md", completed.stdout)


if __name__ == "__main__":
    unittest.main()
