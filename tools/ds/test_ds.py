"""Test chặn file nhạy cảm của ds.py. Chạy: python3 -m unittest discover -s tools/ds -p test_ds.py"""
import os
import sys
import tempfile
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
        with mock.patch.object(ds.urllib.request, "urlopen", return_value=_GiaPhanHoi(sse)):
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


if __name__ == "__main__":
    unittest.main()
