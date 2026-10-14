"""Verify actual CI commands across adb shell's argument-joining boundary, offline."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('ci-nds.sh').read_text()


class AdbShellBoundary(unittest.TestCase):
    def run_remote(self, command):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / 'bin'; binary.mkdir()
            tools = {
                'adb': '#!/usr/bin/env python3\nimport subprocess,sys\nassert sys.argv[1]=="shell"\nsys.exit(subprocess.call(["sh","-c"," ".join(sys.argv[2:])]))\n',
                'am': '#!/usr/bin/env python3\nimport json,sys\nprint(json.dumps(sys.argv[1:]))\n',
            }
            for name, content in tools.items():
                path = binary / name; path.write_text(content); path.chmod(0o755)
            (root / 'cores/melondsds').mkdir(parents=True)
            (root / 'cores/melondsds/melondsds_libretro_android.so').write_bytes(b'test-elf')
            env = dict(os.environ, PATH=str(binary)+os.pathsep+os.environ['PATH'],
                       BASE=str(root), OUT=str(root), PKG='com.aow.monika')
            subprocess.run(['bash', '-euc', command], env=env, check=True, capture_output=True, text=True)
            return {p.name: p.read_text() for p in root.glob('*.txt')}

    def test_system_and_title_survive_remote_shell(self):
        launch = SCRIPT[SCRIPT.index('launch() {'):SCRIPT.index('\nwait_frame()')]
        data = self.run_remote(launch + '\nlaunch first')
        args = json.loads(data['start-first.txt'])
        self.assertEqual('Nintendo DS', args[args.index('system')+1])
        self.assertEqual('CI NDS V106', args[args.index('title')+1])

    def test_stat_format_survives_remote_shell(self):
        command = next(line for line in SCRIPT.splitlines() if 'stat -c' in line and 'core-before.txt' in line)
        data = self.run_remote(command)
        timestamp, size = data['core-before.txt'].split()
        self.assertTrue(timestamp.isdecimal())
        self.assertEqual('8', size)


if __name__ == '__main__':
    unittest.main()
