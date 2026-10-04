import importlib.util
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


if __name__ == '__main__':
    unittest.main()
