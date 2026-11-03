"""Kiểm trang thật bằng Chromium; metadata lấy cùng config nguồn của app."""
import functools
import json
import os
import shutil
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
raw = json.loads(Path(os.environ['CATALOG_CONFIG']).read_text())
catalog = {
    'official': raw['official'],
    'security': {'officialCertSha256': raw['security']['officialCertSha256']},
    'account': {'donate': {key: raw['account']['donate'][key] for key in ('bankName', 'accountNo', 'accountName')}},
}
browser_path = os.environ.get('CHROMIUM_BIN') or shutil.which('google-chrome') or shutil.which('chromium')
assert browser_path, 'Runner cần Chromium hoặc Google Chrome'


class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


server = ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Handler, directory=str(ROOT)))
threading.Thread(target=server.serve_forever, daemon=True).start()
origin = f'http://127.0.0.1:{server.server_port}'
CORS = {'Access-Control-Allow-Origin': '*'}
try:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=browser_path, args=['--no-sandbox'])
        for width, name in [(393, 'mobile'), (1440, 'desktop')]:
            context = browser.new_context(viewport={'width': width, 'height': 1000}, permissions=['clipboard-read', 'clipboard-write'])
            requests = []

            def valid(route):
                requests.append(route.request.url)
                route.fulfill(json=catalog, headers=CORS)

            context.route('**/config.json', valid)
            context.route('https://raw.githubusercontent.com/**', valid)
            context.route('https://fonts.googleapis.com/**', lambda route: route.abort())
            page = context.new_page()
            page.goto(origin + '/chinh-thuc/', wait_until='domcontentloaded')
            page.locator('#content:not([hidden])').wait_for()
            links = page.locator('#sources a')
            assert links.count() == len(catalog['official']['downloadSources'])
            for index, source in enumerate(catalog['official']['downloadSources']):
                assert links.nth(index).get_attribute('href') == source['url']
            for element, value in {
                'certificate': catalog['security']['officialCertSha256'],
                'bank': catalog['account']['donate']['bankName'],
                'account': catalog['account']['donate']['accountNo'],
                'owner': catalog['account']['donate']['accountName'],
            }.items():
                assert page.locator('#' + element).inner_text() == value
            assert page.locator('#report').get_attribute('href') == catalog['official']['reportFakeUrl']
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            assert len(requests) == 1 and requests[0].startswith('https://raw.githubusercontent.com/')
            for key in ('certificate', 'account'):
                page.evaluate('document.querySelector("#copy-status").textContent = ""')
                page.locator(f'[data-copy="{key}"]').click()
                page.wait_for_function('document.querySelector("#copy-status").textContent === "Đã sao chép."')
                expected = page.locator('#' + key).inner_text()
                assert page.evaluate('navigator.clipboard.readText()') == expected
            report = catalog['official']['reportFakeUrl']
            context.route(report, lambda route: route.fulfill(body='report fixture', content_type='text/html'))
            page.locator('#report').click()
            page.wait_for_url(report)
            print(f'Chromium {name} {width}px: identity/links/copy/report/no-overflow PASS')
            context.close()
        context = browser.new_context(viewport={'width': 393, 'height': 851})
        recovered = [False]
        context.route('**/config.json', lambda route: route.fulfill(json=catalog if recovered[0] else {'configVersion': 57}))
        context.route('https://raw.githubusercontent.com/**', lambda route: route.fulfill(json=catalog, headers=CORS) if recovered[0] else route.fulfill(status=503, body='{}', headers=CORS))
        context.route('https://fonts.googleapis.com/**', lambda route: route.abort())
        page = context.new_page()
        page.goto(origin + '/chinh-thuc/', wait_until='domcontentloaded')
        page.locator('#retry:not([hidden])').wait_for()
        assert page.locator('#content').is_hidden()
        assert 'Chưa tải được' in page.locator('#status').inner_text()
        recovered[0] = True
        page.locator('#retry').click()
        page.locator('#content:not([hidden])').wait_for()
        assert page.locator('#certificate').inner_text() == catalog['security']['officialCertSha256']
        print('Chromium catalog missing/error/retry: PASS')
        context.close()
        browser.close()
finally:
    server.shutdown()
    server.server_close()
