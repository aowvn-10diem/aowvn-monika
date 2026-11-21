#!/usr/bin/env python3
"""Test offline cho `scripts/kiem-bang-ton.py` (bảng tồn issue .md ↔ .json).

Mỗi ca chạy đúng hàm `main` của script kiểm, trên bản sao tệp thật trong thư mục
tạm (không cần mạng, không sửa tệp trong repo):

- bản thật ⇒ PASS, mã thoát 0;
- đổi một tên khoá trong `.md` (`so_issue` → `tong_issue`, đúng lỗi đã xảy ra ở
  PR #685) ⇒ FAIL, mã thoát 1, và dòng khoá bị đánh **SAI**;
- đổi một con số trong `.md` ⇒ FAIL;
- thiếu tệp ⇒ FAIL.
"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
MD = ROOT / "docs" / "loi" / "TON-1911.md"
JSON = ROOT / "docs" / "loi" / "TON-1911.json"

spec = importlib.util.spec_from_file_location("kiem_bang_ton", HERE / "kiem-bang-ton.py")
kbt = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = kbt
spec.loader.exec_module(kbt)


class KiemBangTonTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="kiem-bang-ton-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.md = self.tmp / "TON-1911.md"
        self.js = self.tmp / "TON-1911.json"
        shutil.copyfile(MD, self.md)
        shutil.copyfile(JSON, self.js)

    def chay(self) -> tuple[int, str]:
        dem = io.StringIO()
        with contextlib.redirect_stdout(dem), contextlib.redirect_stderr(dem):
            ma = kbt.main(["--md", str(self.md), "--json", str(self.js)])
        return ma, dem.getvalue()

    def test_ban_that_khop(self):
        ma, ra = self.chay()
        self.assertEqual(ma, 0, ra)
        self.assertIn("PASS", ra)
        self.assertNotIn("**SAI**", ra)

    def test_ten_khoa_lech_bi_bat(self):
        goc = self.md.read_text(encoding="utf-8")
        self.assertIn("`cho_ban_on_dinh.so_issue`", goc)
        self.md.write_text(goc.replace("`cho_ban_on_dinh.so_issue`", "`cho_ban_on_dinh.tong_issue`"),
                           encoding="utf-8")
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)
        self.assertIn("cho_ban_on_dinh.tong_issue", ra)
        self.assertIn("**SAI**", ra)
        self.assertIn("sai 1", ra)

    def test_so_lech_bi_bat(self):
        goc = self.md.read_text(encoding="utf-8")
        self.assertIn("**Tổng lượt** | **792**", goc)
        self.md.write_text(goc.replace("**Tổng lượt** | **792**", "**Tổng lượt** | **790**"),
                           encoding="utf-8")
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)
        self.assertIn("790", ra)

    def test_chi_lay_duong_dan_khoa(self):
        """Chuỗi trong dấu `…` không bắt đầu bằng khoá cấp 1 thì bỏ qua."""
        du_lieu = {"tong": {"a": 1}}
        md = "`tong.a` `crash-moi` `v0.8.15` `tong.khong_co`"
        self.assertEqual(kbt.khoa_trong_md(md, du_lieu), ["tong.a", "tong.khong_co"])

    def test_thieu_tep(self):
        self.md.unlink()
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)


if __name__ == "__main__":
    unittest.main(verbosity=2)
