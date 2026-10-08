"""Run the real smoke script with offline tool doubles; skipped ARM must not hide RGSS."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class EmulatorPolicyTest(unittest.TestCase):
    def run_guest(self, abi):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / "bin"
            binary.mkdir()
            tools = {
                "adb": '#!/bin/sh\ncase "$*" in\n  *ro.product.cpu.abi*) echo "$FAKE_ABI";;\n  *"stat -c %u"*) echo 10001;;\nesac\n',
                "curl": "#!/bin/sh\nexit 22\n",  # no ROM, pack, or network access
                "sleep": "#!/bin/sh\nexit 0\n",
            }
            for name, content in tools.items():
                path = binary / name
                path.write_text(content)
                path.chmod(0o755)
            env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ["PATH"],
                       FAKE_ABI=abi, RGSS_PACK_ZIP="", KRKR_GAME="1", KRKR_STRICT="1")
            result = subprocess.run(["bash", str(ROOT / "scripts/ci-emulator-games.sh"), str(root / "out")],
                                    cwd=ROOT, env=env, capture_output=True, text=True, timeout=10)
            return result, (root / "out/games/summary.txt").read_text()

    def test_x86_known_skip_keeps_rgss_and_rom_rows(self):
        for abi in ("x86", "x86_64"):
            with self.subTest(abi=abi):
                result, summary = self.run_guest(abi)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("SKIP_KNOWN kirikiri", summary)
                self.assertIn("docs/opus/ket-qua/V40.md", summary)
                self.assertIn("K1–K8 CHƯA KIỂM", summary)
                self.assertIn("SKIP rgss (không có RGSS_PACK_ZIP)", summary)
                self.assertIn("SKIP nes", summary)
                self.assertNotIn("OK kirikiri", summary)

    def test_arm_and_unknown_abis_do_not_receive_x86_waiver(self):
        for abi in ("arm64-v8a", "armeabi-v7a", ""):
            with self.subTest(abi=abi):
                result, summary = self.run_guest(abi)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertNotIn("SKIP_KNOWN", summary)
                self.assertIn("không tải được gói", summary)
                self.assertIn("SKIP rgss", summary)


class EmulatorTransportPolicyTest(unittest.TestCase):
    def test_artifact_failure_has_no_release_or_tag_fallback(self):
        workflow = (ROOT / ".github/workflows/emulator-test.yml").read_text()
        self.assertNotRegex(workflow, r"gh release (create|delete|upload|edit)")
        self.assertNotIn("ci-apk-", workflow)
        transport = workflow.split("  emulator:", 1)[0]
        self.assertIn("permissions: { contents: read }", transport)
        self.assertNotIn("continue-on-error:", transport)
        download = workflow.split("actions/download-artifact@", 1)[1].split("      - name:", 1)[0]
        self.assertNotIn("continue-on-error:", download)

    def test_result_artifact_keeps_proof_and_excludes_game_payload(self):
        workflow = (ROOT / ".github/workflows/emulator-test.yml").read_text()
        proof = workflow.split("name: ket-qua-api-", 1)[1].split("retention-days:", 1)[0]
        paths = proof.split("path: |", 1)[1].split("if-no-files-found:", 1)[0]
        patterns = [line.strip() for line in paths.splitlines() if line.strip()]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = {"out/maestro.xml", "out/crash.flag", "out/games/summary.txt",
                        "out/games/rgss.png", "out/games/rgss.logcat.txt",
                        "out/games/k10-report.json", "out/shots/flow/shot.png"}
            payloads = {"out/games/test.gba", "out/games/test.gb", "out/games/test.nes",
                        "out/kirikiri-pack.zip", "out/kpack/lib.so", "out/rpack/lib.so",
                        "out/rgame/Game.ini", "out/rgame/Data/Scripts.rxdata",
                        "out/krkr-game/startup.tjs", "out/krkr-game/beep.wav"}
            for name in expected | payloads:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            selected = {str(path.relative_to(root)) for pattern in patterns for path in root.glob(pattern)}
            self.assertEqual(expected, selected)
            self.assertIn("if-no-files-found: error", proof)


if __name__ == "__main__":
    unittest.main()
