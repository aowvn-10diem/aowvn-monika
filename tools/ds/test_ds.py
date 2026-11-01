"""Test chặn file nhạy cảm của ds.py. Chạy: python3 -m unittest discover -s tools/ds -p test_ds.py"""
import os
import sys
import importlib
import json
import http.server
import tempfile
import threading
import unittest
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


class _GiaPhanHoi:
    """Giả phản hồi streaming: lặp từng dòng SSE, dùng được trong with."""
    def __init__(self, lines):
        self._lines = [(l + "\n").encode("utf-8") for l in lines]

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def __iter__(self):
        return iter(self._lines)


class StreamingEffortTest(unittest.TestCase):
    def test_post_stream_ghep_chu_va_usage(self):
        sse = [
            "event: message_start",
            'data: {"type":"message_start","message":{"usage":{"input_tokens":5,"cache_read_input_tokens":2}}}',
            'data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"suy nghi"}}',
            'data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Xin "}}',
            'data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"chao"}}',
            'data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":9}}',
            "data: [DONE]",
        ]
        with mock.patch.object(ds.OPENER, "open", return_value=_GiaPhanHoi(sse)):
            r = ds.post_stream({"model": "m"}, "khoa-gia")
        self.assertEqual("Xin chao", r["content"][0]["text"])  # bỏ khối suy nghĩ, chỉ lấy chữ
        self.assertEqual({"input_tokens": 5, "cache_read_input_tokens": 2, "output_tokens": 9}, r["usage"])
        self.assertEqual("end_turn", r["stop_reason"])

    def _chay(self, effort, stream):
        gui = {}

        def gia_post(body, key):
            gui.update(body)
            return {"content": [{"type": "text", "text": "ok"}], "usage": {"input_tokens": 1, "output_tokens": 1}, "stop_reason": "end_turn"}

        env = {"DS_STREAM": "1"} if stream else {}
        with mock.patch.dict(os.environ, env, clear=False), \
                mock.patch.object(ds, "post" if not stream else "post_stream", gia_post):
            if not stream:
                os.environ.pop("DS_STREAM", None)
            ds.run_job(_GiaNganSach(), "khoa-gia", "simple", "chi thi", effort=effort)
        return gui

    def test_effort_di_vao_than_yeu_cau(self):
        self.assertEqual("high", self._chay("high", False)["reasoning_effort"])
        self.assertEqual("none", self._chay("none", False)["reasoning_effort"])

    def test_khong_effort_thi_khong_gui_truong(self):
        self.assertNotIn("reasoning_effort", self._chay(None, False))

    def test_ds_stream_chon_ham_streaming(self):
        self.assertEqual("max", self._chay("max", True)["reasoning_effort"])


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
