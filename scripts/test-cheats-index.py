#!/usr/bin/env python3
"""Exercise cheats-index.py with a tiny synthetic directory; no repo/game data."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("cheats-index.py")


class CheatsIndexOfflineTest(unittest.TestCase):
    def test_lists_only_sorted_cht_filenames_from_known_systems(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = root / "Nintendo - Game Boy"
            folder.mkdir()
            (folder / "Zeta.cht").write_text("synthetic", encoding="utf-8")
            (folder / "Alpha.cht").write_text("synthetic", encoding="utf-8")
            (folder / "ignored.txt").write_text("synthetic", encoding="utf-8")
            (root / "unlisted-system").mkdir()
            (root / "unlisted-system/Hidden.cht").write_text("synthetic", encoding="utf-8")

            completed = subprocess.run(
                [sys.executable, str(SCRIPT), str(root)], capture_output=True, text=True, check=True,
            )
            self.assertEqual(
                "Nintendo - Game Boy\tAlpha\nNintendo - Game Boy\tZeta\n",
                completed.stdout,
            )


if __name__ == "__main__":
    unittest.main()
