#!/usr/bin/env python3
"""V198: snapshot offline, dữ liệu giả; không tải gói hay gọi GitHub thật."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("snapshot", Path(__file__).with_name("ppsspp-snapshot.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
SHA = "a" * 40
ENV = {"GITHUB_REPOSITORY": m.REPO, "GITHUB_REF": "refs/heads/main",
       "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_SHA": SHA}


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.inputs, self.out = self.root / "artifacts", self.root / "snapshot"
        (self.root / "engines").mkdir()
        (self.root / "scripts").mkdir()
        self.recipe = self.root / "scripts/prepare-core-build.py"
        self.recipe.write_text("# fixture recipe\n")
        (self.root / "scripts/package-native-symbols.py").write_text("# fixture symbol packager\n")
        self.core = {"commit": "b" * 40, "repository": "https://github.com/hrydgard/ppsspp.git",
                     "recipe": "ppsspp-cmake-v1", "submodules": [
                         {"path": "ext/fixture", "commit": "c" * 40, "repository": "https://example.test/fixture.git"}]}
        self.lock = {"toolchain": {"ndk": "22.1.7171670"}, "cores": {"ppsspp": self.core}}
        self.lock_path = self.root / "engines/sources.lock.json"
        self.lock_path.write_text(json.dumps(self.lock))
        root_patch = patch.object(m, "ROOT", self.root)
        root_patch.start(); self.addCleanup(root_patch.stop)
        env_patch = patch.dict(os.environ, ENV)
        env_patch.start(); self.addCleanup(env_patch.stop)
        for abi in m.ABIS:
            directory = self.directory(abi)
            directory.mkdir(parents=True)
            for name in m.files_for(self.core, abi):
                (directory / name).write_bytes(b"fixture corresponding source")
            (directory / "sources.lock.json").write_bytes(self.lock_path.read_bytes())
            (directory / "BUILD-RECIPE.py").write_bytes(self.recipe.read_bytes())
            provenance = {"core": "ppsspp", "abi": abi, "commit": self.core["commit"],
                          "repository": self.core["repository"], "recipe": self.core["recipe"],
                          "builderCommit": SHA, "lockSha256": m.sha(self.lock_path),
                          "recipeSha256": m.sha(self.recipe), "toolchain": self.lock["toolchain"],
                          "submodules": self.core["submodules"], "buildId": "d" * 40}
            with zipfile.ZipFile(directory / f"ppsspp-{abi}.zip", "w") as archive:
                archive.writestr("manifest.json", json.dumps(provenance))
            self.symbols(abi)
            self.seal(abi, provenance)

    def directory(self, abi):
        return self.inputs / ("source-build-ppsspp-" + abi)

    def symbols(self, abi, level="debug"):
        relative = f"libs/{'d' * 40}/ppsspp_libretro_android.so"
        with zipfile.ZipFile(self.directory(abi) / f"symbols-ppsspp-{abi}.zip", "w") as archive:
            archive.writestr(relative, b"\x7fELFfixture symbols")
            archive.writestr("build-ids.tsv", "build_id\tfile\tabi\tsymbols\tlevel\n" +
                             f"{'d' * 40}\tppsspp_libretro_android.so\t{abi}\t{relative}\t{level}\n")

    def seal(self, abi, provenance=None):
        directory = self.directory(abi)
        if provenance is None:
            provenance = json.loads((directory / "provenance.json").read_text())
        provenance["artifacts"] = {n: m.metadata(directory / n) for n in m.files_for(self.core, abi)}
        (directory / "provenance.json").write_text(json.dumps(provenance))
        (directory / "SHA256SUMS").write_text("".join(
            f"{m.sha(p)}  {p.name}\n" for p in sorted(directory.iterdir()) if p.name != "SHA256SUMS"))

    def prepare(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return m.prepare(self.inputs, self.out, "7")

    def test_both_abi_inner_zip_hash_size_source_symbols_and_notes(self):
        manifest = self.prepare()
        self.assertEqual(set(m.ABIS), set(manifest["artifacts"]))
        for abi in m.ABIS:
            entry = manifest["artifacts"][abi]
            self.assertEqual(m.metadata(self.directory(abi) / f"ppsspp-{abi}.zip"),
                             {k: entry[k] for k in ("size", "sha256")})
            self.assertIn(f"/cores-ppsspp-7/ppsspp-{abi}.zip", entry["url"])
            with zipfile.ZipFile(self.out / entry["sources"]["file"]) as archive:
                self.assertEqual(m.files_for(self.core, abi) - {
                    f"ppsspp-{abi}.zip", f"symbols-ppsspp-{abi}.zip"} | {
                        "provenance.json", "SHA256SUMS"}, set(archive.namelist()))
            self.assertEqual((self.out / entry["symbols"]["file"]).read_bytes(),
                             (self.directory(abi) / f"symbols-ppsspp-{abi}.zip").read_bytes())
            self.assertIn(entry["sha256"], (self.out / "notes.md").read_text())
        with self.assertRaises(ValueError):
            self.prepare()

    def test_missing_abi_and_missing_source_rejected(self):
        abi = m.ABIS[1]
        source = next(self.directory(abi).glob("*-source.tar.gz"))
        source.unlink()
        with self.assertRaises(ValueError): self.prepare()
        self.assertFalse(self.out.exists())

    def test_corruption_duplicate_checksums_and_symlinks_rejected(self):
        directory = self.directory(m.ABIS[0])
        pack = directory / f"ppsspp-{m.ABIS[0]}.zip"
        original = pack.read_bytes()
        pack.write_bytes(original + b"bad")
        with self.assertRaises(ValueError): self.prepare()
        pack.write_bytes(original)
        sums = directory / "SHA256SUMS"
        sums.write_text(sums.read_text() + sums.read_text().splitlines()[0] + "\n")
        with self.assertRaises(ValueError): self.prepare()
        self.seal(m.ABIS[0])
        pack.unlink(); pack.symlink_to(self.recipe)
        with self.assertRaises(ValueError): self.prepare()

    def test_wrong_builder_source_and_lock_rejected(self):
        for field in ("builderCommit", "commit", "lockSha256"):
            abi = m.ABIS[0]
            path = self.directory(abi) / "provenance.json"
            original = json.loads(path.read_text())
            changed = dict(original); changed[field] = "0" * 40
            self.seal(abi, changed)
            with self.assertRaises(ValueError): self.prepare()
            self.seal(abi, original)

    def test_dynamic_only_symbols_rejected_even_with_valid_checksums(self):
        abi = m.ABIS[1]
        self.symbols(abi, "dynamic-only")
        self.seal(abi)
        with self.assertRaises(ValueError): self.prepare()

    def test_only_main_dispatch_in_canonical_repo(self):
        m.guard()
        for key, value in (("GITHUB_REPOSITORY", "fork/repo"), ("GITHUB_REF", "refs/heads/sol2/test"),
                           ("GITHUB_EVENT_NAME", "pull_request"), ("GITHUB_SHA", "abcd")):
            with patch.dict(os.environ, {key: value}), patch.object(m, "gh") as remote:
                with self.assertRaises(ValueError): m.publish(self.out, "7")
                remote.assert_not_called()

    def test_rejects_bad_snapshot_numbers_before_remote(self):
        for number in ("0", "-1", "01", "1;echo bad", "1\n", "latest", "9" * 10):
            with patch.object(m, "gh") as remote:
                with self.assertRaises(ValueError): m.publish(self.out, number)
                remote.assert_not_called()

    def test_existing_release_or_tag_rejected(self):
        for replies in ([True], [False, True]):
            with patch.object(m, "gh", side_effect=replies):
                with self.assertRaises(ValueError): m.unused("cores-ppsspp-7")

    def test_only_http404_is_missing_no_access_or_network_bypass(self):
        for stderr in ("HTTP 401", "HTTP 403", "timeout", "HTTP 500"):
            with patch.object(m.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", stderr)) as call:
                with self.assertRaises(ValueError): m.gh("api", "fixture", missing_ok=True)
                self.assertEqual(1, call.call_count)
        with patch.object(m.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "HTTP 404")):
            self.assertFalse(m.gh("api", "fixture", missing_ok=True))

    def test_create_new_draft_with_all_assets_then_prerelease_no_clobber(self):
        self.prepare()
        with patch.object(m, "gh", side_effect=[False, False, True, True]) as remote, contextlib.redirect_stdout(io.StringIO()):
            m.publish(self.out, "7")
        calls = [c.args for c in remote.call_args_list]
        create, edit = calls[2:]
        self.assertEqual(("release", "create", "cores-ppsspp-7"), create[:3])
        self.assertIn("--draft", create); self.assertIn("--prerelease", create)
        self.assertIn("--latest=false", edit)
        for abi in m.ABIS:
            for prefix in ("", "sources-", "symbols-"):
                self.assertIn(str(self.out / f"{prefix}ppsspp-{abi}.zip"), create)
        self.assertFalse(any("clobber" in str(c) or "delete" in c for c in calls))

    def test_upload_failure_keeps_draft_number_burned_no_edit_retry_delete(self):
        self.prepare()
        with patch.object(m, "gh", side_effect=[False, False, ValueError("upload failed")]) as remote:
            with self.assertRaises(ValueError): m.publish(self.out, "7")
            self.assertEqual(3, remote.call_count)

    def test_snapshot_changed_after_prepare_never_calls_remote(self):
        self.prepare()
        (self.out / "manifest.json").write_text("{}")
        with patch.object(m, "gh") as remote:
            with self.assertRaises(ValueError): m.publish(self.out, "7")
            remote.assert_not_called()


if __name__ == "__main__":
    unittest.main()
