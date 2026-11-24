#!/usr/bin/env python3
"""Test offline cho `scripts/kiem-bang-ton-2211.py` (bảng chốt chiến dịch .md ↔ .json).

Mỗi ca chạy đúng hàm `main` của script kiểm, trên bản sao tệp thật trong thư mục
tạm (không cần mạng, không sửa tệp trong repo):

- bản thật ⇒ PASS, mã thoát 0;
- đổi một con số trong bảng "đóng theo ngày" ⇒ FAIL;
- đổi số issue của một bản phát hành ⇒ FAIL;
- đổi một tên khoá trong `.md` ⇒ FAIL (đúng lỗi đã xảy ra ở PR #685 với bảng 19/11);
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
MD = ROOT / "docs" / "loi" / "TON-2211.md"
JSON = ROOT / "docs" / "loi" / "TON-2211.json"

spec = importlib.util.spec_from_file_location("kiem_bang_ton_2211", HERE / "kiem-bang-ton-2211.py")
kbt = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = kbt
spec.loader.exec_module(kbt)


class KiemBangTon2211Test(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="kiem-bang-ton-2211-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.md = self.tmp / "TON-2211.md"
        self.js = self.tmp / "TON-2211.json"
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

    def test_so_ngay_lech_bi_bat(self):
        goc = self.md.read_text(encoding="utf-8")
        self.assertIn("| 20/11 | 27 | 27 | 0 | 1 |", goc)
        self.md.write_text(goc.replace("| 20/11 | 27 | 27 | 0 | 1 |", "| 20/11 | 26 | 27 | 0 | 1 |"),
                           encoding="utf-8")
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)
        self.assertIn("**SAI**", ra)

    def test_so_theo_ban_lech_bi_bat(self):
        goc = self.md.read_text(encoding="utf-8")
        self.assertIn("| 0.8.13 | 13/11 08:46Z | 20 | 20 | 0 |", goc)
        self.md.write_text(goc.replace("| 0.8.13 | 13/11 08:46Z | 20 | 20 | 0 |",
                                       "| 0.8.13 | 13/11 08:46Z | 19 | 20 | 0 |"), encoding="utf-8")
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)
        self.assertIn("Bảng 4: 0.8.13 số issue", ra)

    def test_ten_khoa_lech_bi_bat(self):
        goc = self.md.read_text(encoding="utf-8")
        self.assertIn("`tong.dong_ban_va`", goc)
        self.md.write_text(goc.replace("`tong.dong_ban_va`", "`tong.dong_ban_va_sai`"), encoding="utf-8")
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)
        self.assertIn("dong_ban_va_sai", ra)
        self.assertIn("**SAI**", ra)

    def test_chi_lay_duong_dan_khoa(self):
        """Chuỗi trong dấu `…` không bắt đầu bằng khoá cấp 1 thì bỏ qua."""
        du_lieu = {"tong": {"a": 1}}
        md = "`tong.a` `crash-moi` `v0.8.15` `tong.khong_co`"
        self.assertEqual(kbt.khoa_trong_md(md, du_lieu), ["tong.a", "tong.khong_co"])

    def test_thieu_tep(self):
        self.js.unlink()
        ma, ra = self.chay()
        self.assertEqual(ma, 1, ra)


if __name__ == "__main__":
    unittest.main(verbosity=2)
