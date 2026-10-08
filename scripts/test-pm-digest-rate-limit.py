#!/usr/bin/env python3
"""V63: quota/permission, bounded retries and stale-bot warning; fake responses only."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest import mock

spec = importlib.util.spec_from_file_location('safe', Path(__file__).with_name('pm-digest-safe.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class RateLimitTest(unittest.TestCase):
    def test_http_hints_whitelist_and_permission_classification(self):
        headers = {'x-ratelimit-remaining': '0', 'X-RateLimit-Reset': '12345', 'Retry-After': '12',
                   'Authorization': 'Bearer fake-credential', 'X-Private': 'not-exported'}
        error = urllib.error.HTTPError('https://api.github.com/fixture', 403, 'Forbidden', headers,
                                       io.BytesIO(b'{"message":"API rate limit exceeded; fake-credential"}'))
        hint = m.digest.GitHubAPIError.from_http(error).diagnostic()
        self.assertEqual('rate-limit', hint['kind'])
        self.assertEqual(0, hint['remaining'])
        self.assertEqual(12345, hint['reset_epoch'])
        self.assertNotIn('fake-credential', json.dumps(hint))
        self.assertNotIn('not-exported', json.dumps(hint))
        denied = urllib.error.HTTPError('https://api.github.com/fixture', 403, 'Forbidden', {},
                                        io.BytesIO(b'{"message":"Resource not accessible by integration"}'))
        self.assertEqual('permission', m.digest.GitHubAPIError.from_http(denied).kind)

    def test_retry_403_recovers_without_real_sleep(self):
        calls = []
        waits = []
        def request():
            calls.append(1)
            if len(calls) == 1:
                raise m.digest.GitHubAPIError(403, headers={'X-RateLimit-Remaining': '0'})
            return 'recovered'
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual('recovered', m.retry_http(request, waits.append))
        self.assertEqual(2, len(calls))
        self.assertEqual([5], waits)

    def test_429_exhausts_three_attempts(self):
        waits = []
        request = mock.Mock(side_effect=m.digest.GitHubAPIError(429, headers={'Retry-After': '12'}))
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(m.digest.GitHubAPIError):
            m.retry_http(request, waits.append)
        self.assertEqual(3, request.call_count)
        self.assertEqual([12, 15], waits)

    def test_long_server_backoff_defers_instead_of_retrying_early(self):
        request = mock.Mock(side_effect=m.digest.GitHubAPIError(403, headers={
            'X-RateLimit-Remaining': '0', 'X-RateLimit-Reset': '1200'}))
        waits = []
        with mock.patch.object(m.time, 'time', return_value=1000), self.assertRaises(m.digest.GitHubAPIError):
            m.retry_http(request, waits.append)
        self.assertEqual(1, request.call_count)
        self.assertEqual([], waits)

    def test_non_rate_error_does_not_retry(self):
        request = mock.Mock(side_effect=m.digest.GitHubAPIError(404))
        with self.assertRaises(m.digest.GitHubAPIError):
            m.retry_http(request, lambda _: self.fail('must not sleep'))
        self.assertEqual(1, request.call_count)

    def test_read_cache_and_budget_preserve_security_guard(self):
        api = m.BoundedDigestAPI(m.REPOSITORY, '')
        with mock.patch.object(m.digest.GitHubAPI, '_request_json', return_value=([1], {})) as request, \
             contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(api._request_json('/commits/fake'), ([1], {}))
            self.assertEqual(api._request_json('/commits/fake'), ([1], {}))
            self.assertEqual(1, request.call_count)
            api.reads = m.READ_BUDGET
            with self.assertRaises(m.DigestUnavailable):
                api._request_json('/commits/new-fake')
        with self.assertRaises(RuntimeError):
            api._request_json('https://evil.invalid/repos/elsewhere')

    def test_main_commit_metadata_reused_without_losing_branch_timestamp(self):
        api = m.BoundedDigestAPI(m.REPOSITORY, '')
        commit = {'sha': 'fake-sha', 'commit': {'committer': {'date': '2026-10-08T00:00:00Z'}}}
        with mock.patch.object(m.digest.GitHubAPI, '_request_json', return_value=([commit], {})) as request, \
             contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual([commit], api.list_all('/commits?sha=main&per_page=10'))
            self.assertEqual(commit, api.fetch_commit('fake-sha'))
            self.assertEqual(commit, api.fetch_commit('fake-sha'))
        self.assertEqual(1, request.call_count)
        self.assertEqual(1, api.reads)
        self.assertEqual({'inventory': 1}, api.read_groups)

    def test_soft_warning_does_not_publish_partial_or_claim_new_snapshot(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            stderr = io.StringIO()
            with mock.patch.dict(os.environ, {'GITHUB_TOKEN': 'fake-fixture-token', 'GITHUB_REPOSITORY': m.REPOSITORY}), \
                 mock.patch.object(m.digest, 'build_digest', side_effect=m.digest.GitHubAPIError(403, headers={'X-RateLimit-Remaining': '0'})), \
                 mock.patch.object(m, 'publish') as publish, contextlib.redirect_stderr(stderr):
                self.assertEqual(0, m.main(['--out', str(output), '--publish']))
            publish.assert_not_called()
            self.assertFalse((output / 'digest.json').exists())
            self.assertEqual(0, json.loads((output / 'warning.json').read_text())['remaining'])
            self.assertIn('::warning::', stderr.getvalue())
            self.assertNotIn('fake-fixture-token', stderr.getvalue())
            self.assertIn('Không coi job xanh là dữ liệu mới', (output / 'warning.md').read_text())

    def test_publish_fixture_and_wrong_repository_still_fail_hard(self):
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, m.main(['--out', temp, '--publish', '--fixture', 'not-opened.json']))
            with mock.patch.dict(os.environ, {'GITHUB_TOKEN': 'fake', 'GITHUB_REPOSITORY': 'other/repo'}):
                self.assertEqual(1, m.main(['--out', temp, '--publish']))

    def test_gitdb_network_mutation_not_retried(self):
        with mock.patch.object(m.urllib.request, 'urlopen', side_effect=urllib.error.URLError('synthetic unavailable')) as request:
            with self.assertRaises(m.DigestUnavailable):
                m.GitDB('fake').request('POST', '/git/trees', {'tree': []})
        self.assertEqual(1, request.call_count)


if __name__ == '__main__':
    unittest.main()
