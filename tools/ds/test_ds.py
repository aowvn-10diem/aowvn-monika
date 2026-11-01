"""Test chặn file nhạy cảm của ds.py. Chạy: python3 -m unittest discover -s tools/ds -p test_ds.py"""
import os
import sys
import importlib
import json
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ds  # noqa: E402


class CheckReadableTest(unittest.TestCase):
    def test_cho_phep_file_trong_repo(self):
        ds.check_readable(os.path.join(ds.REPO, "CLAUDE.md"))

    def test_chan_ten_nhay_cam(self):
        for name in ("a.jks", "x.keystore", "k.p12", "c.pem", ".env", ".env.local", "local.properties",
                     "keystore.properties", "google-services.json", "id_rsa"):
            with self.subTest(name=name), self.assertRaises(ds.AccessError):
                ds.check_readable(os.path.join(ds.REPO, "app", name))

    def test_chan_ngoai_repo_ke_ca_khi_dat_bien_cu(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "ghi-chu.txt")
            open(path, "w").close()
            with self.assertRaises(ds.AccessError):
                ds.check_readable(path)
            os.environ["DS_EXTRA_ROOTS"] = tmp  # biến cũ không còn hiệu lực
            try:
                with self.assertRaises(ds.AccessError):
                    ds.check_readable(path)
            finally:
                del os.environ["DS_EXTRA_ROOTS"]

    def test_cau_hinh_khong_noi_duoc_san_bao_mat(self):
        co_ban = {"endpoint": "https://tokenize.vn/v1/messages", "keyEnv": "TOKENIZE_API_KEY", "keyFileEnv": "TOKENIZE_KEY_FILE"}
        r = ds.harden_config(dict(co_ban, blockedGlobs=[]))  # denylist rỗng vẫn có sàn
        for g in (".env*", "*.pem", "*.jks", "google-services.json", "id_rsa*"):
            self.assertIn(g, r["blockedGlobs"])
        for xau in (dict(co_ban, endpoint="https://evil.example/v1/messages"), dict(co_ban, endpoint="http://tokenize.vn/v1/messages"),
                    dict(co_ban, endpoint="https://tokenize.vn.evil.example/x"), dict(co_ban, keyEnv="KHAC"), dict(co_ban, keyFileEnv="KHAC")):
            with self.subTest(cfg=xau), self.assertRaises(ds.ConfigError):
                ds.harden_config(dict(xau))

    def test_ds_config_khong_con_hieu_luc(self):
        with tempfile.TemporaryDirectory() as tmp:
            bad = os.path.join(tmp, "x.json")
            json.dump({"endpoint": "https://evil.example/", "keyEnv": "K", "keyFileEnv": "F", "blockedGlobs": []}, open(bad, "w"))
            os.environ["DS_CONFIG"] = bad
            try:
                importlib.reload(ds)
                self.assertEqual("tokenize.vn", ds.urllib.parse.urlparse(ds.CFG["endpoint"]).hostname)
                self.assertIn(".env*", ds.CFG["blockedGlobs"])
            finally:
                del os.environ["DS_CONFIG"]
                importlib.reload(ds)

    def test_allowedroots_khong_mo_ra_ngoai_repo(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "ghi-chu.txt")
            open(path, "w").close()
            cu = list(ds.CFG["allowedRoots"])
            try:
                for root in (tmp, "/tmp", "../..", ".."):
                    ds.CFG["allowedRoots"] = [root]
                    with self.subTest(root=root), self.assertRaises(ds.AccessError):
                        ds.check_readable(path)
                    with self.subTest(root=root, loai="ghi"), self.assertRaises(ds.AccessError):
                        ds.check_writable(path)
            finally:
                ds.CFG["allowedRoots"] = cu

    def test_chan_env_moi_bien_the(self):
        for name in (".env", ".env.local", ".envrc", ".env.production"):
            with self.subTest(name=name), self.assertRaises(ds.AccessError):
                ds.check_readable(os.path.join(ds.REPO, "app", name))

    def test_ghi_chi_trong_repo(self):
        ds.check_writable(os.path.join(ds.REPO, "docs", "ds", "ra.md"))
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(ds.AccessError):
            ds.check_writable(os.path.join(tmp, "ra.md"))
        for bad in (os.path.join(ds.REPO, ".git", "hooks", "x"), os.path.join(ds.REPO, "app", "khoa.pem"), os.path.join(ds.REPO, ".envrc")):
            with self.subTest(path=bad), self.assertRaises(ds.AccessError):
                ds.check_writable(bad)

    def test_symlink_khong_lach_duoc(self):
        with tempfile.TemporaryDirectory(dir=ds.REPO) as tmp:
            secret = os.path.join(tmp, "khoa.pem")
            open(secret, "w").close()
            link = os.path.join(tmp, "vo-hai.txt")
            os.symlink(secret, link)
            with self.assertRaises(ds.AccessError):
                ds.check_readable(link)

    def test_chan_thu_muc_git(self):
        with self.assertRaises(ds.AccessError):
            ds.check_readable(os.path.join(ds.REPO, ".git", "config"))


if __name__ == "__main__":
    unittest.main()
