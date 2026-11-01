"""Test chặn file nhạy cảm của ds.py. Chạy: python3 -m unittest discover -s tools/ds -p test_ds.py"""
import os
import sys
import importlib
import json
import http.client
import http.server
import io
import tempfile
import threading
import unittest
import urllib.error
from unittest import mock

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


class _GiaNganSach:
    """Sổ đếm giả: không ghi sổ thật vào ~/.cache."""
    def reserve(self, est):
        return {}

    def release(self, row):
        pass

    def settle(self, row, actual, extra):
        pass


class _GiaResp:
    def __init__(self, data):
        self._data = data

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def read(self, *a):
        return self._data


class RetryEffortTest(unittest.TestCase):
    def _post(self, hanh_vi):
        dem = {"n": 0}

        def gia_open(req, timeout=None):
            h = hanh_vi[min(dem["n"], len(hanh_vi) - 1)]
            dem["n"] += 1
            if isinstance(h, Exception):
                raise h
            return _GiaResp(h)

        with mock.patch.object(ds.OPENER, "open", gia_open), mock.patch.object(ds.time, "sleep", lambda s: None):
            try:
                return ds.post({"model": "m"}, "khoa-gia"), dem["n"]
            except ds.ApiError as e:
                return e, dem["n"]

    def test_thu_lai_khi_bi_cat_ket_noi(self):
        r, n = self._post([ConnectionResetError(), http.client.RemoteDisconnected("dong"), b'{"ok": 1}'])
        self.assertEqual(({"ok": 1}, 3), (r, n))

    def test_thu_lai_khi_json_bi_cat_giua_chung(self):
        r, n = self._post([b'{"ok":', b'{"ok": 2}'])
        self.assertEqual(({"ok": 2}, 2), (r, n))

    def test_het_luot_thi_bao_loi_ro(self):
        r, n = self._post([ConnectionResetError()])
        self.assertIsInstance(r, ds.ApiError)
        self.assertEqual((0, 4), (r.code, n))

    def test_loi_4xx_khong_thu_lai(self):
        loi = urllib.error.HTTPError("u", 400, "bad", http.client.HTTPMessage(), io.BytesIO(b"loi"))
        r, n = self._post([loi])
        self.assertIsInstance(r, ds.ApiError)
        self.assertEqual((400, 1), (r.code, n))

    def _gui(self, effort):
        gui = {}

        def gia_post(body, key, on_retry=None):
            gui.update(body)
            return {"content": [{"type": "text", "text": "ok"}], "usage": {"input_tokens": 1, "output_tokens": 1}, "stop_reason": "end_turn"}

        with mock.patch.object(ds, "post", gia_post):
            ds.run_job(_GiaNganSach(), "khoa-gia", "simple", "chi thi", effort=effort)
        return gui

    def test_effort_di_vao_than_yeu_cau(self):
        self.assertEqual("high", self._gui("high")["reasoning_effort"])
        self.assertEqual("none", self._gui("none")["reasoning_effort"])
        self.assertNotIn("reasoning_effort", self._gui(None))

    def test_tool_mcp_co_effort(self):
        props = [t for t in ds.TOOLS if t["name"] == "deepseek_chat"][0]["inputSchema"]["properties"]
        self.assertEqual(["none", "low", "high", "max"], props["effort"]["enum"])


class _SoGhi:
    """Sổ đếm giả ghi lại từng lần đặt chỗ, ghi sổ và nhả chỗ (không ghi ledger thật)."""
    def __init__(self, gioi_han_dat_cho=None):
        self.dat, self.ghi, self.nha = [], [], 0
        self.gioi_han = gioi_han_dat_cho

    def reserve(self, est):
        if self.gioi_han is not None and len(self.dat) >= self.gioi_han:
            raise ds.BudgetError("chạm trần")
        row = {"id": len(self.dat)}
        self.dat.append(est)
        return row

    def release(self, row):
        self.nha += 1

    def settle(self, row, actual, extra):
        self.ghi.append((actual, extra))


class RetryBudgetTest(unittest.TestCase):
    def _chay(self, hanh_vi, so):
        dem = {"n": 0}

        def gia_open(req, timeout=None):
            h = hanh_vi[min(dem["n"], len(hanh_vi) - 1)]
            dem["n"] += 1
            if isinstance(h, Exception):
                raise h
            return _GiaResp(h)

        ok = b'{"content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":10,"output_tokens":5},"stop_reason":"end_turn"}'
        hanh_vi = [ok if h == "ok" else h for h in hanh_vi]
        with mock.patch.object(ds.OPENER, "open", gia_open), mock.patch.object(ds.time, "sleep", lambda s: None):
            try:
                return ds.run_job(so, "khoa-gia", "simple", "chi thi"), dem["n"], None
            except Exception as e:
                return None, dem["n"], e

    def test_moi_luot_mat_phan_hoi_deu_duoc_tinh_du_token_uoc_tinh(self):
        so = _SoGhi()
        r, n, loi = self._chay([ConnectionResetError(), ConnectionResetError(), "ok"], so)
        self.assertIsNone(loi)
        self.assertEqual(3, n)
        self.assertEqual(3, len(so.dat))  # 3 lần đặt chỗ cho 3 request thật sự gửi đi
        est = so.dat[0]
        self.assertEqual([est, est, 15], [g[0] for g in so.ghi])  # hai lượt mất phản hồi tính đủ ước tính, lượt cuối tính số thật
        self.assertEqual(0, so.nha)

    def test_het_luot_van_tinh_ca_4_request(self):
        so = _SoGhi()
        _, n, loi = self._chay([ConnectionResetError()], so)
        self.assertIsInstance(loi, ds.ApiError)
        self.assertEqual(4, n)
        self.assertEqual(4, len(so.ghi))
        self.assertTrue(all(g[0] == so.dat[0] for g in so.ghi))
        self.assertEqual(0, so.nha)

    def test_5xx_tinh_du_uoc_tinh_vi_chua_ro_co_tinh_phi(self):
        loi5xx = urllib.error.HTTPError("u", 503, "bận", http.client.HTTPMessage(), io.BytesIO(b"x"))
        so = _SoGhi()
        r, n, loi = self._chay([loi5xx, "ok"], so)
        self.assertIsNone(loi)
        self.assertEqual(2, len(so.dat))
        self.assertEqual([so.dat[0], 15], [g[0] for g in so.ghi])

    def test_429_chi_tinh_request_khong_tinh_token(self):
        loi429 = urllib.error.HTTPError("u", 429, "nhanh quá", http.client.HTTPMessage(), io.BytesIO(b"x"))
        so = _SoGhi()
        r, n, loi = self._chay([loi429, "ok"], so)
        self.assertIsNone(loi)
        self.assertEqual([0, 15], [g[0] for g in so.ghi])

    def test_5xx_het_luot_van_tinh_du_uoc_tinh(self):
        loi5xx = urllib.error.HTTPError("u", 502, "x", http.client.HTTPMessage(), io.BytesIO(b"x"))
        so = _SoGhi()
        _, n, loi = self._chay([loi5xx], so)
        self.assertIsInstance(loi, ds.ApiError)
        self.assertEqual((4, 4), (n, len(so.ghi)))
        self.assertTrue(all(g[0] == so.dat[0] for g in so.ghi))

    def test_loi_4xx_tinh_1_request_khong_thu_lai(self):
        loi400 = urllib.error.HTTPError("u", 400, "sai", http.client.HTTPMessage(), io.BytesIO(b"x"))
        so = _SoGhi()
        _, n, loi = self._chay([loi400], so)
        self.assertIsInstance(loi, ds.ApiError)
        self.assertEqual((1, 1), (n, len(so.ghi)))
        self.assertEqual(0, so.ghi[0][0])

    def test_cham_tran_khi_thu_lai_thi_dung_va_luot_truoc_da_duoc_ghi(self):
        so = _SoGhi(gioi_han_dat_cho=1)  # chỉ cho đặt chỗ lượt đầu
        _, n, loi = self._chay([ConnectionResetError()], so)
        self.assertIsInstance(loi, ds.BudgetError)
        self.assertEqual(1, n)  # không gửi thêm khi hết hạn mức
        self.assertEqual(1, len(so.ghi))  # lượt đã gửi vẫn được ghi sổ
        self.assertEqual(so.dat[0], so.ghi[0][0])


class RedirectTest(unittest.TestCase):
    """Khóa Bearer không được chuyển tiếp khi server trả redirect sang host khác (urllib mặc định chép header)."""

    def _chay(self):
        thay = []

        class Dich(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                thay.append(dict(self.headers))
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"{}")
            do_POST = do_GET

            def log_message(self, *a):
                pass

        dich = http.server.HTTPServer(("127.0.0.1", 0), Dich)

        class Nguon(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                self.send_response(302)
                self.send_header("Location", f"http://127.0.0.1:{dich.server_port}/lay-key")
                self.end_headers()

            def log_message(self, *a):
                pass

        nguon = http.server.HTTPServer(("127.0.0.1", 0), Nguon)
        for sv in (dich, nguon):
            threading.Thread(target=sv.serve_forever, daemon=True).start()
        cu = ds.CFG["endpoint"]
        ds.CFG["endpoint"] = f"http://127.0.0.1:{nguon.server_port}/v1/messages"
        try:
            with self.assertRaises(ds.ApiError) as cm:
                ds.post({"model": "m"}, "khoa-bi-mat")
        finally:
            ds.CFG["endpoint"] = cu
            for sv in (dich, nguon):
                sv.shutdown()
                sv.server_close()
        return thay, cm.exception

    def test_redirect_khong_chuyen_tiep_authorization(self):
        thay, loi = self._chay()
        self.assertEqual([], thay, "host đích không được nhận yêu cầu nào")
        self.assertEqual(302, loi.code)


if __name__ == "__main__":
    unittest.main()
