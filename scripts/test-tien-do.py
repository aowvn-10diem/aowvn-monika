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


def c(body, login="aowvn-10diem"):
    return {"body": body, "user": {"login": login}}


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
        self.assertEqual(td.trang_thai(pr(), cm, "xanh"), ("✅ Chờ gộp", 2))

    def test_hai_dat_khong_luna(self):
        cm = [c("[Duyệt DeepSeek → PR] head abcdef1\nKết luận: Đạt"),
              c("[Sol 2 → PR] (commit abcdef1)\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, "xanh")[0], "👀 Chờ Luna duyệt")

    def test_head_cu_khong_tinh(self):
        cm = [c("Luna 3 tiền duyệt (commit 1111111)\nKết luận: Đạt"),
              c("Luna 4 tiền duyệt (commit abcdef1)\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, "xanh"), ("👀 Chờ duyệt (1/2 Đạt)", 1))

    def test_ket_luan_moi_nhat_cua_moi_nguoi(self):
        cm = [c("Luna 5 tiền duyệt (commit abcdef1)\nKết luận: Cần sửa: x"),
              c("Luna 5 tiền duyệt (commit abcdef1)\nKết luận: Đạt")]
        self.assertEqual(td.trang_thai(pr(), cm, "xanh")[0], "👀 Chờ duyệt (1/2 Đạt)")

    def test_can_sua_chan(self):
        cm = [c("Luna 3 tiền duyệt (commit abcdef1)\nKết luận: Đạt"),
              c("Luna 4 tiền duyệt (commit abcdef1)\nKết luận: Cần sửa: a.kt:1")]
        self.assertEqual(td.trang_thai(pr(), cm, "xanh")[0], "🔧 Cần sửa")

    def test_nguoi_ngoai_khong_tinh(self):
        cm = [c("Luna 3 tiền duyệt (commit abcdef1)\nKết luận: Đạt", "ke-gia"),
              c("Luna 4 tiền duyệt (commit abcdef1)\nKết luận: Đạt", "ke-gia")]
        self.assertEqual(td.trang_thai(pr(), cm, "xanh"), ("👀 Chờ duyệt (0/2 Đạt)", 0))

    def test_ci_do_tinh_du_check_bat_buoc(self):
        for name in ("build", "coverage", "n04-tests", "CodeQL", "analyze (javascript-typescript)", "offline"):
            self.assertTrue(td.ci_do([{"name": name, "conclusion": "failure"}]), name)
            self.assertTrue(td.ci_do([{"name": name, "conclusion": "timed_out"}]), name)
        for name in ("preview", "links", "digest"):
            self.assertFalse(td.ci_do([{"name": name, "conclusion": "failure"}]), name)
        self.assertFalse(td.ci_do([{"name": "build", "conclusion": "success"}]))

    def test_ci_chua_xanh_khong_cho_gop(self):
        cm = [c("Luna 3 tiền duyệt (commit abcdef1)\nKết luận: Đạt"),
              c("Luna 4 tiền duyệt (commit abcdef1)\nKết luận: Đạt")]
        for ci in ([], [{"name": "build", "status": "in_progress", "conclusion": None}],
                   [{"name": "build", "status": "queued", "conclusion": None}],
                   [{"name": "build", "status": "completed", "conclusion": "cancelled"}]):
            self.assertEqual(td.ci_trang_thai(ci), "cho", ci)
            self.assertEqual(td.trang_thai(pr(), cm, td.ci_trang_thai(ci))[0], "⏳ Chờ CI (2/2 Đạt)", ci)
        ok = [{"name": "build", "status": "completed", "conclusion": "success"},
              {"name": "preview", "status": "completed", "conclusion": "failure"}]
        self.assertEqual(td.ci_trang_thai(ok), "xanh")
        # Thiếu cổng bắt buộc (coverage, n04-tests) thì chưa xanh dù check có mặt đều xanh.
        can = td.cong_bat_buoc(["scripts/check-x.py", "docs/a.md"])
        self.assertEqual(can, {"build", "coverage", "n04-tests"})
        self.assertEqual(td.ci_trang_thai(ok, can), "cho")
        self.assertEqual(td.trang_thai(pr(), cm, td.ci_trang_thai(ok, can))[0], "⏳ Chờ CI (2/2 Đạt)")
        du = ok + [{"name": n, "status": "completed", "conclusion": "success"} for n in ("coverage", "n04-tests")]
        self.assertEqual(td.ci_trang_thai(du, can), "xanh")
        # PR chỉ tài liệu: không cổng bắt buộc (V11); chưa có check nào thì vẫn chờ.
        self.assertEqual(td.cong_bat_buoc(["docs/x.md", "README.md"]), set())
        self.assertEqual(td.ci_trang_thai([], set()), "cho")
        app = td.cong_bat_buoc(["app/src/main/A.kt"])
        self.assertEqual(app, {"build", "coverage", "analyze (java-kotlin)", "analyze (javascript-typescript)"})
        self.assertIn("analyze (java-kotlin)", td.cong_bat_buoc(["cloudflare/w.js"]))
        self.assertNotIn("analyze (java-kotlin)", can)  # chỉ scripts/** thì CodeQL không bắt buộc
        g = lambda n, k="success": {"name": n, "status": "completed", "conclusion": k}
        du_app = [g(n) for n in app]
        self.assertEqual(td.ci_trang_thai(du_app, app), "xanh")
        # Cổng bắt buộc skipped hoặc neutral thì chưa xanh.
        for k in ("skipped", "neutral"):
            th = [g(n) for n in app if n != "analyze (java-kotlin)"] + [g("analyze (java-kotlin)", k)]
            self.assertEqual(td.ci_trang_thai(th, app), "cho", k)
        # Check thông tin đang chạy không làm chậm khi cổng bắt buộc đã xanh.
        for n in ("preview", "links", "digest"):
            self.assertEqual(td.ci_trang_thai(du_app + [{"name": n, "status": "in_progress", "conclusion": None}], app), "xanh", n)
        # PR sửa script V197: job offline là cổng bắt buộc; thiếu hoặc skipped thì chờ.
        v197 = td.cong_bat_buoc(["scripts/tien-do.py", ".github/workflows/tien-do.yml"])
        self.assertEqual(v197, {"build", "coverage", "n04-tests", "offline"})
        co = [g(n) for n in ("build", "coverage", "n04-tests")]
        self.assertEqual(td.ci_trang_thai(co, v197), "cho")
        self.assertEqual(td.ci_trang_thai(co + [g("offline", "skipped")], v197), "cho")
        self.assertEqual(td.ci_trang_thai(co + [g("offline")], v197), "xanh")
        # Check không bắt buộc skipped (vd cap-nhat) không chặn.
        self.assertEqual(td.ci_trang_thai(du_app + [g("cap-nhat", "skipped")], app), "xanh")
        self.assertEqual(td.trang_thai(pr(), cm, td.ci_trang_thai(ok))[0], "✅ Chờ gộp")

    def test_xung_dot(self):
        p = pr(); p["mergeable_state"] = "dirty"
        self.assertEqual(td.trang_thai(p, [], "xanh")[0], "⚠️ Xung đột với main")

    def test_nhap_va_ci_do(self):
        self.assertEqual(td.trang_thai(pr(True), [], "xanh")[0], "📝 Nháp")
        self.assertEqual(td.trang_thai(pr(), [], "do")[0], "🔴 CI đỏ")

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
