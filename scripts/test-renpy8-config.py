#!/usr/bin/env python3
"""Test offline cho scripts/renpy8-config.py: gói tự sinh, không mạng, không khóa."""
import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("renpy8_config", HERE / "renpy8-config.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

REPO_CONFIG = HERE.parent / "config" / "monika-config.json"
URL = "https://example.invalid/engines-renpy8-{abi}-1/renpy8-{abi}.zip"


def make_zip(directory: Path, abi: str, *, engine="renpy", lib=True, manifest=True) -> Path:
    path = directory / f"renpy8-{abi}.zip"
    with zipfile.ZipFile(path, "w") as z:
        if lib:
            z.writestr(m.MAIN_LIB, b"\x7fELF fixture " + abi.encode())
        if manifest:
            z.writestr("manifest.json", json.dumps({"engine": engine, "abi": abi, "version": "8.5.3"}))
    return path


class Renpy8ConfigTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.config = json.loads(REPO_CONFIG.read_text(encoding="utf-8"))

    def facts(self, *abis):
        return {abi: m.zip_facts(make_zip(self.dir, abi), abi) for abi in abis}

    def test_repo_config_round_trips_byte_for_byte(self):
        self.assertEqual(m.dump(self.config), REPO_CONFIG.read_text(encoding="utf-8"))

    def test_apply_adds_module_enables_engine_and_bumps_version(self):
        facts = self.facts("arm64-v8a")
        new = m.apply_renpy8(self.config, version="8.5.3-r1", url=URL, abis=["arm64-v8a"], facts=facts)
        self.assertEqual(new["configVersion"], self.config["configVersion"] + 1)
        module = new["modules"]["renpy8"]
        self.assertEqual(module["version"], "8.5.3-r1")
        self.assertEqual(module["abis"], ["arm64-v8a"])
        self.assertEqual(module["sha256ByAbi"]["arm64-v8a"], facts["arm64-v8a"]["sha256"])
        self.assertEqual(module["sizeByAbi"]["arm64-v8a"], facts["arm64-v8a"]["size"])
        renpy = next(s for s in new["systems"] if s["id"] == "renpy")
        self.assertEqual(renpy["engine"], "renpy")
        # JoiPlay vẫn là đường dự phòng: không đổi runner/externalApp.
        self.assertEqual((renpy["runner"], renpy["externalApp"]), ("external", "joiplay"))
        # Không đụng gói khác và hệ khác.
        for key in self.config["modules"]:
            self.assertEqual(new["modules"][key], self.config["modules"][key])
        others = [s for s in new["systems"] if s["id"] != "renpy"]
        self.assertEqual(others, [s for s in self.config["systems"] if s["id"] != "renpy"])

    def test_input_config_is_not_mutated(self):
        before = copy.deepcopy(self.config)
        m.apply_renpy8(self.config, version="v", url=URL, abis=["arm64-v8a"], facts=self.facts("arm64-v8a"))
        self.assertEqual(self.config, before)

    def test_no_engine_flag_keeps_system_untouched(self):
        new = m.apply_renpy8(self.config, version="v", url=URL, abis=["arm64-v8a"], facts=self.facts("arm64-v8a"), enable_engine=False)
        self.assertNotIn("engine", next(s for s in new["systems"] if s["id"] == "renpy"))
        self.assertIn("renpy8", new["modules"])

    def test_two_abis_need_placeholder_and_each_hash(self):
        facts = self.facts("arm64-v8a", "armeabi-v7a")
        new = m.apply_renpy8(self.config, version="v", url=URL, abis=["arm64-v8a", "armeabi-v7a"], facts=facts)
        self.assertEqual(set(new["modules"]["renpy8"]["sha256ByAbi"]), {"arm64-v8a", "armeabi-v7a"})
        with self.assertRaises(ValueError):
            m.apply_renpy8(self.config, version="v", url="https://example.invalid/renpy8.zip",
                           abis=["arm64-v8a", "armeabi-v7a"], facts=facts)

    def test_rejects_bad_inputs(self):
        good = self.facts("arm64-v8a")
        bad_cases = [
            dict(url="http://example.invalid/x.zip"),
            dict(version="  "),
            dict(abis=["x86_64"], facts={"x86_64": good["arm64-v8a"]}),
            dict(abis=[], facts={}),
            dict(facts={"arm64-v8a": {"sha256": "ABC", "size": 5}}),
            dict(facts={"arm64-v8a": {"sha256": "0" * 64, "size": 0}}),
            dict(facts={}),
        ]
        for override in bad_cases:
            with self.subTest(override=override):
                args = dict(version="v", url=URL, abis=["arm64-v8a"], facts=good)
                args.update(override)
                with self.assertRaises(ValueError):
                    m.apply_renpy8(self.config, **args)

    def test_explicit_config_version_must_be_higher(self):
        facts = self.facts("arm64-v8a")
        new = m.apply_renpy8(self.config, version="v", url=URL, abis=["arm64-v8a"], facts=facts, config_version=99999)
        self.assertEqual(new["configVersion"], 99999)
        for bad in (self.config["configVersion"], self.config["configVersion"] - 1):
            with self.assertRaises(ValueError):
                m.apply_renpy8(self.config, version="v", url=URL, abis=["arm64-v8a"], facts=facts, config_version=bad)

    def test_requires_exactly_one_renpy_system(self):
        broken = copy.deepcopy(self.config)
        broken["systems"] = [s for s in broken["systems"] if s["id"] != "renpy"]
        with self.assertRaises(ValueError):
            m.apply_renpy8(broken, version="v", url=URL, abis=["arm64-v8a"], facts=self.facts("arm64-v8a"))

    def test_zip_facts_checks_hash_size_and_manifest(self):
        path = make_zip(self.dir, "arm64-v8a")
        facts = m.zip_facts(path, "arm64-v8a")
        self.assertEqual(facts["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
        self.assertEqual(facts["size"], path.stat().st_size)
        with self.assertRaises(ValueError):
            m.zip_facts(path, "armeabi-v7a")  # sai ABI
        with self.assertRaises(ValueError):
            m.zip_facts(make_zip(self.dir, "arm64-v8a", engine="kirikiri"), "arm64-v8a")
        with self.assertRaises(ValueError):
            m.zip_facts(make_zip(self.dir, "arm64-v8a", lib=False), "arm64-v8a")
        with self.assertRaises(ValueError):
            m.zip_facts(make_zip(self.dir, "arm64-v8a", manifest=False), "arm64-v8a")

    def test_cli_writes_out_file_and_validates_like_the_app_expects(self):
        zip_path = make_zip(self.dir, "arm64-v8a")
        out = self.dir / "config-out.json"
        code = m.main(["--config", str(REPO_CONFIG), "--version", "8.5.3-r1", "--url", URL,
                       "--zip", f"arm64-v8a={zip_path}", "--out", str(out)])
        self.assertEqual(code, 0)
        written = json.loads(out.read_text(encoding="utf-8"))
        self.assertEqual(written["configVersion"], self.config["configVersion"] + 1)
        # Cùng điều kiện ConfigValidation của app: mọi sha256 là 64 ký tự hex.
        for module in written["modules"].values():
            for value in [module.get("sha256", "")] + list(module.get("sha256ByAbi", {}).values()):
                if value:
                    self.assertRegex(value, r"^[0-9a-f]{64}$")
        # Không --write và không --out thì file nguồn không đổi.
        self.assertEqual(REPO_CONFIG.read_text(encoding="utf-8"), m.dump(self.config))


if __name__ == "__main__":
    unittest.main()
