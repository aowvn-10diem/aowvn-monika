import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parent


def module(filename):
    spec = importlib.util.spec_from_file_location(filename, ROOT / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


issue = module('periodic-issue.py')
links = module('check-config-links.py')
remote = module('check-remote-config.py')


class PeriodicTest(unittest.TestCase):
    def test_one_issue_repeated_failure_reopen_and_recovery(self):
        stored = []
        calls = []

        def api(method, path, data=None, paginate=False):
            calls.append((method, path, data))
            if method == 'GET' and '/issues?' in path:
                return [stored.copy()]
            if method == 'GET':
                return [[{'name': issue.LABEL}]]
            if method == 'POST' and path.endswith('/issues'):
                stored.append(dict(data, number=9, state='open'))
                return stored[-1]
            if method == 'PATCH':
                stored[0].update(data)
                return stored[0]
            raise AssertionError(path)

        red = {'links': {'result': 'failure'}, 'packs': {'result': 'success'}, 'unit': {'result': 'success'}}
        green = {name: {'result': 'success'} for name in red}
        for status in [red, red, green, red]:
            issue.reconcile(api, 'owner/repo', status, 'https://github.com/owner/repo/actions/runs/1', 'abc')
        self.assertEqual(1, sum(method == 'POST' and path.endswith('/issues') for method, path, _ in calls))
        self.assertEqual('open', stored[0]['state'])
        self.assertTrue(any(data and data.get('state') == 'closed' for _, _, data in calls))
        self.assertIn('links: failure', stored[0]['body'])

    def test_existing_duplicates_converge_to_one_owned_issue(self):
        stored = [dict(number=11, state='open', body=issue.MARKER),
                  dict(number=7, state='closed', body=issue.MARKER),
                  dict(number=9, state='open', body=issue.MARKER),
                  dict(number=12, state='closed', body=issue.MARKER),
                  dict(number=13, state='open', body='Issue người dùng'),
                  dict(number=14, state='open', body=issue.MARKER, pull_request={})]
        calls = []
        def api(method, path, data=None, paginate=False):
            if method == 'GET':
                return [stored[:2], stored[2:]]
            self.assertEqual('PATCH', method)  # Không tạo thêm issue hay nhãn.
            number = int(path.rsplit('/', 1)[-1])
            calls.append(number)
            item = next(i for i in stored if i['number'] == number)
            item.update(data)
            return item
        red = {'links': {'result': 'failure'}}
        green = {'links': {'result': 'success'}}
        for status in [red, red, green, red]:
            issue.reconcile(api, 'o/r', status, 'https://github.com/o/r/actions/runs/1', 'abc')
            owned_open = [i['number'] for i in stored if i['state'] == 'open'
                          and issue.MARKER in i['body'] and 'pull_request' not in i]
            self.assertEqual([7] if status == red else [], owned_open)
        self.assertNotIn(13, calls)
        self.assertNotIn(14, calls)
        self.assertNotIn(12, calls)  # Bản trùng đã đóng giữ nguyên.
        self.assertIn('links: failure', next(i for i in stored if i['number'] == 7)['body'])

    def test_skipped_or_cancelled_verification_is_not_green(self):
        calls = []
        def api(method, path, data=None, paginate=False):
            if method == 'GET':
                return [[{'name': issue.LABEL}]] if '/labels?' in path else [[]]
            calls.append(data)
            return {}
        issue.reconcile(api, 'o/r', {'unit': {'result': 'cancelled'}}, 'https://github.com/o/r/actions/runs/1', 'abc')
        self.assertIn('unit: cancelled', calls[0]['body'])

    def test_remote_config_current_stale_and_invalid_without_network(self):
        for actual, expected in [(7, 'stale'), (36, 'current'), (37, 'current'), (True, 'unknown'), ('36', 'unknown')]:
            with self.subTest(actual=actual):
                result = remote.compare(lambda url: {'configVersion': 36 if url == remote.MAIN_URL else actual}, 'https://fixture.test/config.json')
                self.assertEqual(expected, result['status'])
        def fail(url):
            raise TimeoutError('fake secret must never enter report')
        self.assertEqual({'status': 'unknown', 'remote_version': None, 'main_version': None, 'error': 'network_error'}, remote.compare(fail, 'https://fixture.test/config.json'))

    def test_remote_stale_keeps_periodic_issue_open_with_versions(self):
        calls = []
        def api(method, path, data=None, paginate=False):
            if method == 'GET':
                return [[dict(number=7, state='closed', body=issue.MARKER)]]
            calls.append(data)
            return {}
        green = {'unit': {'result': 'success'}}
        issue.reconcile(api, 'o/r', green, 'https://github.com/o/r/actions/runs/1', 'abc', {'status': 'stale', 'remote_version': 7, 'main_version': 36, 'body': 'secret prose'})
        self.assertEqual('open', calls[0]['state'])
        self.assertIn('KV **7**, main **36**', calls[0]['body'])
        self.assertNotIn('secret prose', calls[0]['body'])
        calls.clear()
        issue.reconcile(api, 'o/r', green, 'https://github.com/o/r/actions/runs/2', 'abc', {'status': 'current', 'remote_version': 36, 'main_version': 36})
        self.assertIn('KV **36**, main **36**', calls[0]['body'])

    def test_link_failure_exit_and_machine_report_but_skips_are_not_errors(self):
        with tempfile.TemporaryDirectory() as d:
            config = pathlib.Path(d, 'config.json'); config.write_text('{}')
            rows = [dict(source='test', url='https://example.test/file', method='HEAD', status=404, error=False, checked=True, note='HTTP 404'),
                    dict(source='aow', url='https://aow.vn/file', method='—', status=None, error=False, checked=False, note='ngoài VN')]
            args = ['check', '--report', d + '/links.md', '--json-output', d + '/links.json', '--fail-on-error']
            with patch.object(links, 'CONFIG_PATH', config), patch.object(links, 'supported_abis', return_value=['arm64-v8a']), patch.object(links, 'collect_urls', return_value=rows), patch.object(links, 'probe', side_effect=lambda row: row), patch('sys.argv', args):
                with self.assertRaises(SystemExit) as failure:
                    links.main()
                self.assertEqual(1, failure.exception.code)
                import json
                report = json.loads(pathlib.Path(d, 'links.json').read_text())
                self.assertEqual(1, report['errors']); self.assertEqual(1, report['skipped'])
                rows[0]['status'] = 200
                links.main()

    # ---- V42: hai URL gốc VietQR phải kiểm bằng URL đầy đủ, không gọi thẳng URL gốc ----
    DONATE = {
        "account": {"donate": {
            "bankId": "icb", "accountNo": "101867601261", "template": "compact2", "minAmount": 1000,
            "memo": "MONIKA", "accountName": "NGUYEN VAN A",
            "qrBaseUrl": "https://img.vietqr.io/image/", "payUrl": "https://dl.vietqr.io/pay",
            "returnUrl": "https://monika.aow.vn/",
        }}
    }

    def test_donate_base_urls_are_expanded_like_the_app(self):
        urls = links.donate_derived_urls(self.DONATE)
        qr = urls["account.donate.qrBaseUrl"]
        pay = urls["account.donate.payUrl"]
        self.assertTrue(qr.startswith("https://img.vietqr.io/image/icb-101867601261-compact2.png?"), qr)
        self.assertIn("amount=1000", qr)
        self.assertTrue(pay.startswith("https://dl.vietqr.io/pay?"), pay)
        self.assertIn("ba=101867601261%40icb", pay)
        self.assertIn("app=icb", pay)

    def test_donate_base_urls_are_skipped_with_reason_when_config_is_invalid(self):
        for donate in ({"bankId": "bad id", "accountNo": "12"},
                       {"bankId": "icb", "accountNo": "101867601261", "minAmount": 0}):
            with self.subTest(donate=donate):
                self.assertEqual({}, links.donate_derived_urls({"account": {"donate": donate}}))

    def test_main_probes_full_donate_urls_but_never_prints_account_number(self):
        with tempfile.TemporaryDirectory() as d:
            config = pathlib.Path(d, 'config.json'); config.write_text(json.dumps(self.DONATE))
            rows = [dict(source='account.donate.qrBaseUrl', url='https://img.vietqr.io/image/'),
                    dict(source='account.donate.payUrl', url='https://dl.vietqr.io/pay')]
            seen = []

            def fake_probe(row):
                seen.append(row['url'])
                return {**row, 'status': 200, 'method': 'HEAD', 'remote_size': None,
                        'note': 'Đã nhận phản hồi HTTP', 'error': False, 'checked': True}

            args = ['check', '--report', d + '/links.md', '--json-output', d + '/links.json']
            with patch.object(links, 'CONFIG_PATH', config), patch.object(links, 'supported_abis', return_value=['arm64-v8a']), \
                    patch.object(links, 'collect_urls', return_value=rows), patch.object(links, 'probe', side_effect=fake_probe), \
                    patch('sys.argv', args):
                links.main()
            self.assertTrue(all('101867601261' in url for url in seen), seen)  # gửi URL đầy đủ
            report = pathlib.Path(d, 'links.md').read_text()
            artifact = pathlib.Path(d, 'links.json').read_text()
            for text in (report, artifact):
                self.assertNotIn('101867601261', text)  # không in số tài khoản
                self.assertNotIn('NGUYEN%20VAN%20A', text)
            self.assertIn('img.vietqr.io/image/…', report)

    def test_head_rejected_falls_back_to_get_before_reporting_failure(self):
        calls = []

        class FakeResponse:
            status = 200
            headers = {'Content-Length': '12'}

            def read(self, _n=-1):
                return b'x'

            def __enter__(self):
                return self

            def __exit__(self, *_exc):
                return False

        def fake_urlopen(request, timeout=None):
            calls.append(request.get_method())
            if request.get_method() == 'HEAD':
                raise links.HTTPError(request.full_url, 404, 'Not Found', {}, None)
            return FakeResponse()

        with patch.object(links, 'urlopen', side_effect=fake_urlopen):
            status, _headers, method, error = links.request_head('https://dl.vietqr.io/pay?x=1')
        self.assertEqual(['HEAD', 'GET'], calls)
        self.assertEqual(200, status)
        self.assertIn('GET', method)
        self.assertIsNone(error)


if __name__ == '__main__':
    unittest.main()
