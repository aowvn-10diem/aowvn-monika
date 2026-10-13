#!/usr/bin/env python3
"""Kiểm guard khởi động fixture bằng adb giả, không chạy emulator/game/workflow."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("ci-user-report.sh").read_text()
STARTUP = SCRIPT[SCRIPT.index("LAUNCH_READY=0"):SCRIPT.index("adb shell run-as", SCRIPT.index("maestro test"))]


class ReportStartupTest(unittest.TestCase):
    def run_startup(self, scenario):
        with tempfile.TemporaryDirectory() as directory:
            env = dict(os.environ, OUT=directory, PKG="com.aow.monika", BASE="/fixture", SCENARIO=scenario)
            fake = r'''
set -euo pipefail
attempt=0
adb() {
  if [[ "$*" == "shell am start "* ]]; then
    attempt=$((attempt + 1)); echo "$attempt" >> "$OUT/attempts"; return 0
  fi
  if [[ "$*" == "shell pidof "* ]]; then
    if [[ "$SCENARIO" == no_pid ]]; then return 1; fi
    echo 1234; return 0
  fi
  if [[ "$*" == "shell dumpsys activity activities" ]]; then
    if [[ "$SCENARIO" == ready || ( "$SCENARIO" == recover && "$attempt" == 2 ) ]]; then
      echo 'topResumedActivity=ActivityRecord{abc u0 com.aow.monika/vn.aow.monika.runner.RgssGameActivity t7}'
    elif [[ "$SCENARIO" == no_pid ]]; then
      echo 'mResumedActivity: ActivityRecord{abc u0 com.aow.monika/vn.aow.monika.runner.RgssGameActivity t7}'
    else
      echo 'mResumedActivity: ActivityRecord{abc u0 com.android.launcher/.Launcher t1}'
    fi
    return 0
  fi
  if [[ "$*" == "shell am force-stop "* ]]; then echo stop >> "$OUT/stops"; return 0; fi
  return 99
}
sleep() { echo "$*" >> "$OUT/waits"; }
maestro() { echo called >> "$OUT/maestro"; }
'''
            result = subprocess.run(["bash", "-c", fake + STARTUP], env=env, capture_output=True, text=True)
            def lines(name):
                path = Path(directory, name)
                return path.read_text().splitlines() if path.exists() else []
            return result, {name: lines(name) for name in ("attempts", "waits", "stops", "maestro")}

    def test_resumed_game_starts_maestro_once(self):
        result, facts = self.run_startup("ready")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(facts, dict(attempts=["1"], waits=["25"], stops=[], maestro=["called"]))

    def test_pid_on_launcher_never_starts_maestro(self):
        result, facts = self.run_startup("launcher")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("BLOCKED:", result.stderr)
        self.assertEqual(facts, dict(attempts=["1", "2"], waits=["25", "25"], stops=["stop", "stop"], maestro=[]))

    def test_resumed_activity_without_game_pid_is_blocked(self):
        result, facts = self.run_startup("no_pid")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("BLOCKED:", result.stderr)
        self.assertEqual(facts["attempts"], ["1", "2"])
        self.assertEqual(facts["maestro"], [])

    def test_second_launch_must_reach_resumed_game(self):
        result, facts = self.run_startup("recover")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(facts, dict(attempts=["1", "2"], waits=["25", "25"], stops=["stop"], maestro=["called"]))


if __name__ == "__main__":
    unittest.main()
