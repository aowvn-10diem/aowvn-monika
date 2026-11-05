#!/usr/bin/env python3
"""Test offline các cổng nguồn/ABI/phụ thuộc và chuẩn bị artifact V198."""
import copy
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("corebuild", Path(__file__).with_name("prepare-core-build.py"))
corebuild = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(corebuild)


class CoreBuildTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.lock_path = self.root / "lock.json"
        self.config = corebuild.ROOT / "config/monika-config.json"
        self.raw = json.loads((corebuild.ROOT / "engines/sources.lock.json").read_text())
        self.write(self.raw)

    def write(self, data):
        self.lock_path.write_text(json.dumps(data))

    def load(self):
        return corebuild.load_lock(self.lock_path, self.config)

    def test_plan_builds_only_implemented_abis(self):
        lock = self.load()
        self.assertEqual(corebuild.plan(lock), {"include": [
            {"core": "fceumm", "abi": "arm64-v8a"},
            {"core": "fceumm", "abi": "armeabi-v7a"},
            {"core": "gambatte", "abi": "arm64-v8a"},
            {"core": "gambatte", "abi": "armeabi-v7a"},
            {"core": "mgba", "abi": "arm64-v8a"},
            {"core": "mgba", "abi": "armeabi-v7a"},
            {"core": "ppsspp", "abi": "arm64-v8a"},
            {"core": "ppsspp", "abi": "armeabi-v7a"}]})
        self.assertEqual(len(lock["inventory"]["cores"]), 22)
        self.assertEqual(len(set(lock["inventory"]["cores"]) - set(lock["cores"])), 18)

    def test_repository_recipe_and_license_are_bound_to_each_core(self):
        for name, wrong in [('fceumm', 'gambatte'), ('gambatte', 'fceumm'), ('mgba', 'fceumm'), ('ppsspp', 'mgba')]:
            for key in ['repository', 'recipe', 'license', 'licenseFiles']:
                data = copy.deepcopy(self.raw)
                data['cores'][name][key] = data['cores'][wrong][key]
                self.write(data)
                with self.subTest(core=name, key=key), self.assertRaisesRegex(ValueError, 'recipe'):
                    self.load()

    def test_reject_mutable_or_short_commits(self):
        for sha in ["HEAD", "master", "7a542dab", "a" * 39, "a" * 40 + "\n"]:
            with self.subTest(sha=sha):
                data = copy.deepcopy(self.raw)
                data["cores"]["fceumm"]["commit"] = sha
                self.write(data)
                with self.assertRaisesRegex(ValueError, "SHA"):
                    self.load()

    def test_inventory_drift_and_duplicate_fail(self):
        for key in ["cores", "modules", "additional"]:
            for change in ["missing", "duplicate"]:
                data = copy.deepcopy(self.raw)
                if change == "missing":
                    data["inventory"][key].pop()
                else:
                    data["inventory"][key].append(data["inventory"][key][0])
                self.write(data)
                with self.subTest(key=key, change=change), self.assertRaises(ValueError):
                    self.load()

    def test_reject_unreviewed_recipe_inputs(self):
        cases = [("repository", "https://example.org/malicious.git"),
                 ("recipe", "echo anything"), ("abis", ["x86_64"]), ("abis", []),
                 ("abis", ["arm64-v8a", "arm64-v8a"]), ("submodules", ["HEAD"]),
                 ("patches", ["unhashed.patch"]), ("dependencies", ["latest"]),
                 ("licenseFiles", ["../Copying"])]
        for key, value in cases:
            data = copy.deepcopy(self.raw)
            data["cores"]["fceumm"][key] = value
            self.write(data)
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                self.load()

    def test_wrong_toolchain_fails(self):
        for version in ["latest", "22.1.7171670\n"]:
            data = copy.deepcopy(self.raw)
            data["toolchain"]["ndk"] = version
            self.write(data)
            with self.assertRaises(ValueError):
                self.load()
        self.write(self.raw)
        ndk = self.root / "ndk"
        ndk.mkdir()
        (ndk / "source.properties").write_text("Pkg.Revision = 25.2.9519653\n")
        with patch.object(corebuild, "checkout") as fetch:
            with self.assertRaisesRegex(ValueError, "NDK"):
                corebuild.prepare(self.load(), "fceumm", "arm64-v8a", ndk,
                                  self.root / "out", self.lock_path)
            fetch.assert_not_called()

    def test_unknown_core_abi_fails_before_network(self):
        with patch.object(corebuild, "checkout") as fetch:
            for core, abi in [("melondsds", "arm64-v8a"), ("fceumm", "x86")]:
                with self.assertRaisesRegex(ValueError, "chưa có recipe"):
                    corebuild.prepare(self.load(), core, abi, self.root / "ndk",
                                      self.root / "out", self.lock_path)
            fetch.assert_not_called()
        self.assertFalse((self.root / "out").exists())

    def test_mgba_missing_or_wrong_cmake_ninja_fails_before_network(self):
        ndk = self.root / "ndk"; ndk.mkdir()
        (ndk / "source.properties").write_text("Pkg.Revision = 22.1.7171670\n")
        for cmake, versions, message in [
                (None, [], "CMake"),
                (self.root / "cmake", ["cmake version 3.31.6"], "CMake"),
                (self.root / "cmake", ["cmake version 3.22.1-g37088a8", "1.11.1"], "Ninja")]:
            with self.subTest(message=message, versions=versions), \
                    patch.object(corebuild, "checkout") as fetch, \
                    patch.object(corebuild, "run", side_effect=versions):
                with self.assertRaisesRegex(ValueError, message):
                    corebuild.prepare(self.load(), "mgba", "arm64-v8a", ndk,
                                      self.root / "out", self.lock_path, cmake)
                fetch.assert_not_called()
        self.assertFalse((self.root / "out").exists())

    def test_reject_unpinned_cmake_and_ninja_in_lock(self):
        for tool in ("cmake", "cmakeBuild", "ninja"):
            for version in ("latest", "3.22.1\n", None):
                data = copy.deepcopy(self.raw)
                data["toolchain"][tool] = version
                self.write(data)
                with self.subTest(tool=tool, version=version), self.assertRaisesRegex(ValueError, "CMake/Ninja"):
                    self.load()

    def test_psp_submodule_source_path_commit_and_closure_fail_before_fetch(self):
        for key, value in (("path", "../outside"), ("repository", "https://example.org/x.git"),
                           ("commit", "master"), ("commit", "a" * 39)):
            data = copy.deepcopy(self.raw)
            data["cores"]["ppsspp"]["submodules"][0][key] = value
            self.write(data)
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "Submodule PSP"):
                self.load()
        data = copy.deepcopy(self.raw); data["cores"]["ppsspp"]["submodules"].pop()
        self.write(data)
        with self.assertRaisesRegex(ValueError, "Submodule PSP"):
            self.load()

    def test_psp_gitlink_sha_url_and_unexpected_module_are_rejected(self):
        source = self.root / "source"; source.mkdir()
        module = self.raw["cores"]["ppsspp"]["submodules"][0]
        path = module["path"]
        (source / ".gitmodules").write_text(
            f'[submodule "fixture"]\npath = {path}\nurl = {module["repository"]}\n')
        modules = {path: module}
        line = f'160000 commit {module["commit"]}\t{path}'
        with patch.object(corebuild, "run", return_value=line):
            self.assertEqual({path}, corebuild.verify_psp_gitlinks(source, "", modules))
        for bad in (line.replace(module["commit"], "a" * 40), line.replace(path, "ext/unknown")):
            with patch.object(corebuild, "run", return_value=bad), self.assertRaisesRegex(ValueError, "khớp lock"):
                corebuild.verify_psp_gitlinks(source, "", modules)
        (source / ".gitmodules").write_text(f'[submodule "fixture"]\npath = {path}\nurl = https://example.org/changed\n')
        with patch.object(corebuild, "run", return_value=line), self.assertRaisesRegex(ValueError, "khớp lock"):
            corebuild.verify_psp_gitlinks(source, "", modules)

    def test_psp_requires_real_debug_info_and_symbol_table(self):
        for sections in ("", ".dynsym", ".symtab", ".debug_info"):
            with patch.object(corebuild, "run", return_value=sections), self.assertRaisesRegex(ValueError, "dynamic-only"):
                corebuild.require_debug_symbols(self.root / "lib.so", "readelf")
        with patch.object(corebuild, "run", return_value=".symtab .debug_info"):
            corebuild.require_debug_symbols(self.root / "lib.so", "readelf")

    def test_checkout_must_match_commit_and_no_submodules(self):
        core = self.raw["cores"]["fceumm"]
        source = self.root / "source"
        source.mkdir()
        with patch.object(corebuild, "run", return_value="b" * 40):
            with self.assertRaisesRegex(ValueError, "Commit"):
                corebuild.checkout(core, source)
        (source / ".gitmodules").write_text("[submodule]\n")
        with patch.object(corebuild, "run", return_value=core["commit"]):
            with self.assertRaisesRegex(ValueError, "submodule"):
                corebuild.checkout(core, source)

    def elf(self):
        data = bytearray(64)
        data[:6] = b"\x7fELF\x02\x01"
        data[16:20] = b"\x03\x00\xb7\x00"
        elf = self.root / "lib.so"
        elf.write_bytes(data)
        return elf

    def inspect(self, elf, dynamic=None, exports=None, notes=None):
        with patch.object(corebuild, "run", side_effect=[
                dynamic if dynamic is not None else "Shared library: [libc.so]",
                exports if exports is not None else "\n".join("T " + s for s in corebuild.EXPORTS),
                notes if notes is not None else "Build ID: abc123"]):
            return corebuild.elf_metadata(elf, "arm64-v8a", "readelf", "nm")

    def test_elf_metadata_and_missing_contract(self):
        self.assertEqual(self.inspect(self.elf()), {"needed": ["libc.so"], "buildId": "abc123"})
        with self.assertRaisesRegex(ValueError, "hợp đồng"):
            self.inspect(self.elf(), exports="T retro_run")
        with self.assertRaisesRegex(ValueError, "BuildId"):
            self.inspect(self.elf(), notes="")
        with self.assertRaisesRegex(ValueError, "Phụ thuộc"):
            self.inspect(self.elf(), dynamic="Shared library: [libsecret.so]")

    def test_psp_system_libraries_do_not_relax_other_core_checks(self):
        with self.assertRaisesRegex(ValueError, "Phụ thuộc"):
            self.inspect(self.elf(), dynamic="Shared library: [libEGL.so]")
        for needed, allowed in (("libEGL.so", True), ("libsecret.so", False)):
            with patch.object(corebuild, "run", side_effect=[
                    f"Shared library: [{needed}]", "\n".join("T " + s for s in corebuild.EXPORTS),
                    "Build ID: abc123"]):
                if allowed:
                    self.assertEqual([needed], corebuild.elf_metadata(
                        self.elf(), "arm64-v8a", "readelf", "nm", corebuild.PSP_NEEDED)["needed"])
                else:
                    with self.assertRaisesRegex(ValueError, "Phụ thuộc"):
                        corebuild.elf_metadata(self.elf(), "arm64-v8a", "readelf", "nm", corebuild.PSP_NEEDED)

    def test_wrong_abi_rejected_before_tools(self):
        elf = self.elf()
        data = bytearray(elf.read_bytes()); data[18] = 40
        elf.write_bytes(data)
        with patch.object(corebuild, "run") as tools:
            with self.assertRaisesRegex(ValueError, "ABI"):
                corebuild.elf_metadata(elf, "arm64-v8a", "readelf", "nm")
            tools.assert_not_called()

    def test_zip_is_stable_and_root_library_kept(self):
        source = self.root / "pack"; source.mkdir()
        (source / "fceumm_libretro_android.so").write_bytes(b"test")
        (source / "LICENSES").mkdir()
        (source / "LICENSES/Copying").write_text("fixture license")
        first = self.root / "a.zip"; second = self.root / "b.zip"
        corebuild.write_zip(source, first)
        corebuild.write_zip(source, second)
        self.assertEqual(corebuild.digest(first), corebuild.digest(second))
        with zipfile.ZipFile(first) as z:
            self.assertEqual(z.namelist(), ["LICENSES/Copying", "fceumm_libretro_android.so"])

    def test_zip_rejects_symlink_to_outside(self):
        source = self.root / "pack"; source.mkdir()
        outside = self.root / "outside"; outside.write_text("private fixture")
        (source / "leak").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "symlink"):
            corebuild.write_zip(source, self.root / "bad.zip")


if __name__ == "__main__":
    unittest.main()
