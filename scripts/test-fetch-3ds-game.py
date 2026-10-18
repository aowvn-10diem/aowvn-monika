#!/usr/bin/env python3
"""Test offline cho scripts/fetch-3ds-game.py: parse secret, URL ứng viên Pixeldrain, nhận dạng gói, kiểm header game, từ chối trang HTML nhỏ."""
import contextlib
import io
import http.server
import importlib.util
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location('fetch3ds', Path(__file__).resolve().parent / 'fetch-3ds-game.py')
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)


def blob(directory, name, data):
    path = Path(directory) / name
    path.write_bytes(data)
    return path


class Fetch3dsTest(unittest.TestCase):
    def test_parse_full_line_and_bare_link(self):
        self.assertEqual(mod.parse('# ghi chú\n3ds | https://x.test/a | tên | mk=abc'), ('3ds', 'https://x.test/a', 'abc'))
        self.assertEqual(mod.parse('https://x.test/a'), ('', 'https://x.test/a', ''))
        with self.assertRaises(SystemExit):
            mod.parse('3ds | http://không-https')

    def test_pixeldrain_candidates(self):
        self.assertEqual(mod.candidates('https://pixeldrain.com/u/AbCdEf12'), ['https://pixeldrain.com/api/file/AbCdEf12?download'])
        self.assertEqual(len(mod.candidates('https://pixeldrain.com/d/AbCdEf12')), 2)
        self.assertEqual(mod.candidates('https://x.test/f.zip'), ['https://x.test/f.zip'])

    def test_shape_hides_ids(self):
        self.assertEqual(mod.shape('https://pixeldrain.com/d/AbCdEf12XYZ?attach#x'), 'pixeldrain.com')

    def test_shape_keeps_only_hostname_with_userinfo_and_short_tokens(self):
        for url in (
            'https://user:pass@FILES.example:8443/a/B7?key=short#fragment',
            'https://FILES.example/a/b?q=v#f',
            'https://other:credential@FILES.example/x',
        ):
            with self.subTest(url=url):
                self.assertEqual(mod.shape(url), 'files.example')
        self.assertEqual(mod.shape('https://[broken/path?secret=value'), 'không rõ')
        self.assertEqual(mod.shape('/relative/path?secret=value'), 'không rõ')

    def test_main_logs_only_source_and_redirect_hostname(self):
        source = 'https://alice:sourcePass@source.example/a/X7?key=sourceToken#sourceFragment'
        redirect = 'https://bob:redirectPass@redirect.example:8443/b/Y8?key=redirectToken#redirectFragment'
        def download(url, raw):
            self.assertEqual(url, source)
            data = bytearray(mod.MIN_BYTES)
            data[0x100:0x104] = b'NCSD'
            raw.write_bytes(data)
            return redirect, 'application/octet-stream'
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory, \
                mock.patch.dict(os.environ, {'TEST_GAME_3DS': 'fixture'}), \
                mock.patch.object(mod, 'parse', return_value=('3ds', source, '')), \
                mock.patch.object(mod, 'candidates', return_value=[source]), \
                mock.patch.object(mod, 'download', side_effect=download), \
                contextlib.redirect_stdout(output):
            mod.main(directory)
        # GitHub masking instructions intentionally receive the original URL; normal logs must not.
        lines = [line for line in output.getvalue().splitlines() if not line.startswith('::add-mask::')]
        normal = '\n'.join(lines)
        self.assertIn('Dạng link khai báo: source.example', normal)
        self.assertIn('Thử source.example -> redirect.example:', normal)
        self.assertIn('máy chủ cuối: redirect.example)', normal)
        for private in ('alice', 'sourcePass', 'X7', 'sourceToken', 'sourceFragment',
                        'bob', 'redirectPass', 'Y8', 'redirectToken', 'redirectFragment', '8443', 'https://'):
            self.assertNotIn(private, normal)

    def test_valid_game_headers(self):
        with tempfile.TemporaryDirectory() as d:
            ncsd = bytearray(0x400); ncsd[0x100:0x104] = b'NCSD'
            ncch = bytearray(0x400); ncch[0x100:0x104] = b'NCCH'
            cia = bytearray(0x400); cia[0:4] = (0x2020).to_bytes(4, 'little')
            dsx = bytearray(0x400); dsx[0:4] = b'3DSX'
            self.assertTrue(mod.valid_game(blob(d, 'a.3ds', bytes(ncsd))))
            self.assertTrue(mod.valid_game(blob(d, 'a.cci', bytes(ncsd))))
            self.assertTrue(mod.valid_game(blob(d, 'a.cxi', bytes(ncch))))
            self.assertTrue(mod.valid_game(blob(d, 'a.cia', bytes(cia))))
            self.assertTrue(mod.valid_game(blob(d, 'a.3dsx', bytes(dsx))))
            self.assertFalse(mod.valid_game(blob(d, 'b.3ds', bytes(0x400))))
            self.assertFalse(mod.valid_game(blob(d, 'b.cia', bytes(0x400))))
            self.assertFalse(mod.valid_game(blob(d, 'b.txt', bytes(ncsd))))

    def test_sniff_by_content(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(mod.sniff(blob(d, 'x', b'Rar!\x1a\x07\x01\x00' + bytes(512))), 'rar')
            self.assertEqual(mod.sniff(blob(d, 'y', b'PK\x03\x04' + bytes(512))), 'zip')
            self.assertEqual(mod.sniff(blob(d, 'z', b'7z\xbc\xaf\x27\x1c' + bytes(512))), '7z')
            self.assertEqual(mod.sniff(blob(d, 'w', b'<html>' + bytes(512))), 'other')

    def test_small_html_is_rejected(self):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                body = b'<html><title>trang xem</title></html>'
                self.send_response(200); self.send_header('Content-Type', 'text/html'); self.send_header('Content-Length', str(len(body)))
                self.end_headers(); self.wfile.write(body)

            def log_message(self, *args):
                pass

        server = http.server.HTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        url = f'http://127.0.0.1:{server.server_port}/a'
        original = mod.parse
        mod.parse = lambda text: ('3ds', url, '')
        os.environ['TEST_GAME_3DS'] = 'x'
        try:
            with tempfile.TemporaryDirectory() as d, self.assertRaises(SystemExit) as caught:
                mod.main(d)
            self.assertIn('không trả về file game', str(caught.exception))
        finally:
            mod.parse = original
            server.shutdown()


if __name__ == '__main__':
    unittest.main()
