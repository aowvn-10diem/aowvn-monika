"""Test chặn file nhạy cảm của ds.py. Chạy: python3 -m unittest discover -s tools/ds -p test_ds.py"""
import os
import sys
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

    def test_chan_ngoai_repo(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "ghi-chu.txt")
            open(path, "w").close()
            with self.assertRaises(ds.AccessError):
                ds.check_readable(path)
            os.environ["DS_EXTRA_ROOTS"] = tmp
            try:
                ds.check_readable(path)
            finally:
                del os.environ["DS_EXTRA_ROOTS"]

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
