"""Regression: a native first-frame event can precede the visible Android frame."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from PIL import Image, ImageDraw

SCRIPT = Path(__file__).with_name('ci-nds.sh').read_text()
START = SCRIPT.index('verify_frame() {')
END = SCRIPT.index('\nwait_visible_frame "$OUT/first-frame.png"', START)
FUNCTIONS = SCRIPT[START:END]


class VisibleFrameGate(unittest.TestCase):
    def run_frames(self, frames):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); binary = root / 'bin'; binary.mkdir()
            paths = []
            for index, kind in enumerate(frames):
                image = Image.new('RGB', (320, 640), '#202020')
                if kind == 'rom':
                    ImageDraw.Draw(image).rectangle((67, 4, 252, 143), fill=(255, 123, 0))
                elif kind == 'ui-orange':
                    ImageDraw.Draw(image).rectangle((67, 4, 252, 143), fill=(242, 140, 40))
                path = root / f'{index}.png'; image.save(path); paths.append(str(path))
            adb = binary / 'adb'
            adb.write_text('''#!/usr/bin/env python3
import json,os,pathlib,sys
root=pathlib.Path(os.environ['OUT']); count=root/'count'
i=int(count.read_text()) if count.exists() else 0
frames=json.loads(os.environ['FRAMES'])
count.write_text(str(i+1))
sys.stdout.buffer.write(pathlib.Path(frames[min(i,len(frames)-1)]).read_bytes())
'''); adb.chmod(0o755)
            sleep = binary / 'sleep'; sleep.write_text('#!/bin/sh\nexit 0\n'); sleep.chmod(0o755)
            env = dict(os.environ, PATH=str(binary)+os.pathsep+os.environ['PATH'], OUT=str(root), FRAMES=json.dumps(paths))
            result = subprocess.run(['bash', '-euc', FUNCTIONS+'\nwait_visible_frame "$OUT/frame.png"'],
                                    env=env, capture_output=True, text=True, timeout=30)
            return result, int((root/'count').read_text())

    def test_loading_snapshot_then_real_rom_waits_for_pixels(self):
        result, captures = self.run_frames(['loading', 'rom'])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, captures)

    def test_loading_never_becomes_a_pass(self):
        result, captures = self.run_frames(['loading'])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(20, captures)

    def test_theme_orange_does_not_substitute_for_rom_pixels(self):
        result, captures = self.run_frames(['ui-orange'])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(20, captures)


if __name__ == '__main__':
    unittest.main()
