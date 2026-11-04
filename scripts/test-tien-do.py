#!/usr/bin/env python3
"""V197: test offline cho bảng tiến độ (không gọi mạng)."""
import datetime as dt
import importlib.util
import sys
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("tien_do", Path(__file__).resolve().parent / "tien-do.py")
td = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = td
spec.loader.exec_module(td)

HEAD = "abcdef1234567890"


def pr(draft=False):
    return {"draft": draft, "head": {"sha": HEAD}}


def c(body):
    return {"body": body}


class TienDo(unittest.TestCase):
    def test_doi_theo_nhanh(self):
        self.assertEqual(td.doi("deepseek6/V187"), "DeepSeek")
        self.assertEqual(td.doi("sol2/V177"), "Sol 2")
        self.assertEqual(td.doi("luna-ultra/ra-soat"), "Luna")
        self.assertEqual(td.doi("claude/project-thread-x"), "PM")
        self.assertEqual(td.doi("feature/x"), "Khác")

    def test_gon_tieu_de(self):
        self.assertEqual(td.gon("[viec-V139] Trang game"), "V139 · Trang game")
        self.assertEqual(td.gon("[bao-mat-V192] Nâng thư viện"), "V192 · Nâng thư viện")
        self.assertEqual(td.gon("docs: mẫu đặc tả"), "docs: mẫu đặc tả")

    def test_hai_dat_dung_head(self):
        cm = [c("Luna 3 tiền duyệt (commit abcdef1)\n\nKết luận: Đạt"),
              c("[Duyệt DeepSeek → PR] head abcdef1\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, False), ("✅ Chờ gộp", 2))

    def test_hai_dat_khong_luna(self):
        cm = [c("[Duyệt DeepSeek → PR] head abcdef1\nKết luận: Đạt"),
              c("[Sol 2 → PR] (commit abcdef1)\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, False)[0], "👀 Chờ Luna duyệt")

    def test_head_cu_khong_tinh(self):
        cm = [c("Luna 3 tiền duyệt (commit 1111111)\nKết luận: Đạt"),
              c("Luna 4 tiền duyệt (commit abcdef1)\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, False), ("👀 Chờ duyệt (1/2 Đạt)", 1))

    def test_ket_luan_moi_nhat_cua_moi_nguoi(self):
        cm = [c("Luna 5 tiền duyệt (commit abcdef1)\nKết luận: Cần sửa: x"),
              c("Luna 5 tiền duyệt (commit abcdef1)\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, False)[0], "👀 Chờ duyệt (1/2 Đạt)")

    def test_can_sua_chan(self):
        cm = [c("Luna 3 tiền duyệt (commit abcdef1)\nKết luận: Đạt"),
              c("Luna 4 tiền duyệt (commit abcdef1)\nKết luận: Cần sửa: a.kt:1")]
        self.assertEqual(td.trang_thai(pr(), cm, False)[0], "🔧 Cần sửa")

    def test_nhap_va_ci_do(self):
        self.assertEqual(td.trang_thai(pr(True), [], False)[0], "📝 Nháp")
        self.assertEqual(td.trang_thai(pr(), [], True)[0], "🔴 CI đỏ")

    def test_dung_than(self):
        now = dt.datetime(2026, 11, 4, 3, 0, tzinfo=dt.timezone.utc)
        rel = [{"tag_name": "v0.8.9", "html_url": "u", "published_at": "2026-11-02T15:09:00Z", "prerelease": False}]
        rows = [{"n": 5, "t": "V1 · a", "doi": "Sol 2", "tt": "📝 Nháp"}]
        merged = [{"number": 7, "title": "[viec-V2] b", "merged_at": "2026-11-03T10:00:00Z", "head": {"ref": "nova/x"}}]
        body = td.dung_than(now, rel, rows, merged, 62)
        self.assertIn("Ổn định: [v0.8.9](u) (02/11)", body)
        self.assertIn("| #5 | V1 · a | Sol 2 | 📝 Nháp |", body)
        self.assertIn("**03/11** (1)", body)
        self.assertIn("- #7 V2 · b · Nova", body)
        self.assertIn("**62**", body)
        self.assertIn("10:00 04/11/2026", body)


if __name__ == "__main__":
    unittest.main()
