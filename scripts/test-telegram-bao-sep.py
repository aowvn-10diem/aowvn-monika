#!/usr/bin/env python3
"""V146: test offline cho bộ lọc comment và phần đóng JSON gửi Telegram.

Không gọi mạng: thay opener bằng hàm giả, chỉ kiểm dữ liệu đã đóng.
"""
import importlib.util
import io
import json
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("telegram_bao_sep", HERE / "telegram-bao-sep.py")
bot = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = bot
spec.loader.exec_module(bot)

PREFIX = "[PM → sếp]"


class FakeResponse:
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class FakeOpener:
    def __init__(self, error=None):
        self.requests = []
        self.error = error

    def __call__(self, request, timeout=None):
        self.requests.append(request)
        if self.error:
            raise self.error
        return FakeResponse()


def env(**overrides):
    base = {
        "TELEGRAM_BOT_TOKEN": "123:secret-token",
        "TELEGRAM_CHAT_ID": "-1001234567890",
        "COMMENT_AUTHOR": "aowvn-10diem",
        "COMMENT_BODY": PREFIX + " việc mới",
        "COMMENT_URL": "https://github.com/aowvn-10diem/aowvn-monika/issues/337#issuecomment-1",
        "ALLOWED_LOGINS": "",
    }
    base.update(overrides)
    return base


class FilterTest(unittest.TestCase):
    def test_allowed_default_and_custom(self):
        self.assertEqual(bot.DEFAULT_ALLOWED, bot.allowed_logins(""))
        self.assertEqual(bot.DEFAULT_ALLOWED, bot.allowed_logins(" , "))
        self.assertEqual(("a", "b"), bot.allowed_logins(" a, b ,"))

    def test_forwards_both_prefixes_for_allowed_author(self):
        for prefix in ("[Cố vấn → sếp]", "[PM → sếp]"):
            with self.subTest(prefix=prefix):
                self.assertTrue(bot.should_forward("vudoanbinhminh", prefix + " nội dung", bot.DEFAULT_ALLOWED))
                self.assertTrue(bot.should_forward("aowvn-10diem", prefix + "\nnội dung", bot.DEFAULT_ALLOWED))

    def test_ignores_other_authors_and_prefixes(self):
        self.assertFalse(bot.should_forward("someone", PREFIX + " nội dung", bot.DEFAULT_ALLOWED))
        self.assertFalse(bot.should_forward("", PREFIX + " nội dung", bot.DEFAULT_ALLOWED))
        self.assertFalse(bot.should_forward("aowvn-10diem", "chào " + PREFIX, bot.DEFAULT_ALLOWED))
        self.assertFalse(bot.should_forward("aowvn-10diem", "\n  " + PREFIX + " nội dung", bot.DEFAULT_ALLOWED))
        self.assertFalse(bot.should_forward("aowvn-10diem", "[Cố vấn → sếp hộ] nội dung", bot.DEFAULT_ALLOWED))
        self.assertFalse(bot.should_forward("aowvn-10diem", PREFIX + " nội dung", ("nguoi-khac",)))

    def test_text_keeps_body_and_link_and_cuts_600(self):
        url = "https://example.test/c/1"
        self.assertEqual("[PM → sếp] a\n" + url, bot.telegram_text(PREFIX + " a", url))
        text = bot.telegram_text(PREFIX + " " + "x" * 700, url)
        first, link = text.split("\n")
        self.assertEqual(600, len(first))
        self.assertEqual(url, link)


class SendTest(unittest.TestCase):
    def test_missing_secret_is_green_and_sends_nothing(self):
        for missing in ("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"):
            with self.subTest(missing=missing):
                opener = FakeOpener()
                out = io.StringIO()
                with redirect_stdout(out):
                    code = bot.run(env(**{missing: ""}), opener)
                self.assertEqual(0, code)
                self.assertEqual([], opener.requests)
                self.assertIn("chua cau hinh", out.getvalue())

    def test_filtered_comment_sends_nothing(self):
        for overrides in (
            {"COMMENT_AUTHOR": "someone"},
            {"COMMENT_BODY": "không có tiền tố"},
            {"ALLOWED_LOGINS": "nguoi-khac"},
        ):
            with self.subTest(overrides=overrides):
                opener = FakeOpener()
                with redirect_stdout(io.StringIO()):
                    code = bot.run(env(**overrides), opener)
                self.assertEqual(0, code)
                self.assertEqual([], opener.requests)

    def test_payload_is_json_with_body_and_chat_id(self):
        opener = FakeOpener()
        body = PREFIX + ' nhắc "; rm -rf / # $(touch /tmp/x)'
        out = io.StringIO()
        with redirect_stdout(out):
            code = bot.run(env(COMMENT_BODY=body), opener)
        self.assertEqual(0, code)
        self.assertEqual(1, len(opener.requests))
        request = opener.requests[0]
        self.assertIn("sendMessage", request.full_url)
        payload = json.loads(request.data.decode("utf-8"))
        self.assertEqual("-1001234567890", payload["chat_id"])
        self.assertTrue(payload["text"].startswith(body))
        self.assertIn("https://github.com/", payload["text"])
        self.assertTrue(payload["disable_web_page_preview"])
        self.assertNotIn("secret-token", out.getvalue())

    def test_telegram_error_is_red(self):
        opener = FakeOpener(error=OSError("mạng hỏng"))
        with redirect_stdout(io.StringIO()):
            code = bot.run(env(), opener)
        self.assertEqual(1, code)


class ErrorLineTest(unittest.TestCase):
    """V146-fix: lỗi Telegram in mã HTTP + description, cắt 120 ký tự, không lộ bí mật."""

    URL = "https://api.telegram.org/bot123:secret-token/sendMessage"

    def http_error(self, payload, code=400, reason="Bad Request"):
        return bot.urllib.error.HTTPError(
            self.URL, code, reason, None, io.BytesIO(json.dumps(payload).encode("utf-8"))
        )

    def run_error(self, error):
        opener = FakeOpener(error=error)
        out = io.StringIO()
        with redirect_stdout(out):
            code = bot.run(env(), opener)
        self.assertEqual(1, code)
        return out.getvalue()

    def test_http_code_and_description_are_printed(self):
        line = self.run_error(self.http_error({"ok": False, "error_code": 400, "description": "Bad Request: chat not found"}))
        self.assertIn("HTTP 400", line)
        self.assertIn("chat not found", line)

    def test_description_is_cut_to_120(self):
        line = self.run_error(self.http_error({"ok": False, "description": "x" * 500}))
        self.assertLessEqual(len(line.strip()), 120 + len("gui that bai: "))

    def test_no_url_token_or_chat_id_in_error(self):
        for error in (
            self.http_error({"ok": False, "description": "Bad Request: chat not found"}),
            bot.urllib.error.URLError("mạng hỏng " + self.URL),
        ):
            with self.subTest(error=type(error).__name__):
                line = self.run_error(error)
                self.assertNotIn("secret-token", line)
                self.assertNotIn("123:", line)
                self.assertNotIn("-1001234567890", line)
                self.assertNotIn("api.telegram.org", line)

    def test_network_error_without_http_still_reports_type(self):
        line = self.run_error(bot.urllib.error.URLError("mạng hỏng"))
        self.assertIn("URLError", line)


if __name__ == "__main__":
    unittest.main(verbosity=2)
