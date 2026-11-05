"""V177: dữ liệu giả, thời gian cố định, không gọi GitHub/Telegram."""
from datetime import datetime, timedelta, timezone
from contextlib import redirect_stdout, redirect_stderr
import importlib.util
import io
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    'daily_summary', Path(__file__).with_name('telegram-tom-tat.py'))
daily = importlib.util.module_from_spec(spec)
spec.loader.exec_module(daily)
NOW = datetime(2026, 11, 3, 1, tzinfo=timezone.utc)
HEAD = 'a' * 40


def comment(status, header=HEAD[:7], created='2026-11-03T00:00:00Z',
            updated=None, association='OWNER'):
    return {
        'body': f'Luna 4 tiền duyệt (commit {header})\nKết luận: **{status}**',
        'created_at': created, 'updated_at': updated or created,
        'author_association': association,
    }


class API:
    def __init__(self):
        self.comments = [comment('Đạt')]
        self.closed = [
            {'number': 1, 'merged_at': '2026-11-02T01:00:00Z'},
            {'number': 2, 'merged_at': '2026-11-02T00:59:59Z'},
            {'number': 3, 'merged_at': None},
            {'number': 4, 'merged_at': '2026-11-03T01:00:01Z'},
        ]
        self.releases = [
            {'tag_name': 'v0.8.9', 'published_at': '2026-11-02T23:00:00Z',
             'prerelease': True, 'draft': False},
            {'tag_name': 'draft', 'draft': True},
            {'tag_name': 'old', 'published_at': '2026-11-01T23:00:00Z', 'draft': False},
        ]
        self.calls = []
        self.now = NOW
        self.pushes = []
        self.global_comments = []
        self.inline_comments = []
        self.fork = False

    def list_all(self, route):
        self.calls.append(route)
        if route.startswith('/issues/comments?'):
            return self.global_comments
        if route.startswith('/pulls/comments?'):
            return self.inline_comments
        return {
            '/pulls?state=open&per_page=100': [{
                'number': 7,
                'head': {'sha': HEAD, 'ref': 'sol2/V177',
                         'repo': {'full_name': 'fork/repo' if self.fork else 'owner/repo'}},
                'base': {'repo': {'full_name': 'owner/repo'}},
            }],
            '/issues/7/comments?per_page=100': self.comments,
            '/pulls/7/reviews?per_page=100': [],
            '/releases?per_page=100': self.releases,
        }[route]

    def list_recent(self, route, cutoff, field):
        self.calls.append(route)
        assert cutoff == self.now - timedelta(hours=24)
        if route.startswith('/activity?'):
            assert field == 'timestamp'
            return self.pushes
        assert field == 'updated_at'
        return self.closed


def push(at, branch='sol2/V177', kind='push'):
    return {'ref': 'refs/heads/' + branch, 'timestamp': at.isoformat(), 'activity_type': kind}


def report(body, at, association='OWNER'):
    return {'body': body, 'updated_at': at.isoformat(), 'author_association': association}


def send_env():
    return {'GITHUB_REPOSITORY': 'aowvn-10diem/aowvn-monika',
            'TELEGRAM_BOT_TOKEN': 'fixture-only', 'TELEGRAM_CHAT_ID': 'fixture-chat'}


class Summary(unittest.TestCase):
    def test_24h_boundary_draft_old_and_future_do_not_claim_new_release_or_merge(self):
        api = API()
        result = daily.collect_snapshot(api, NOW)
        self.assertEqual([1], [item['number'] for item in result['merged']])
        self.assertEqual(['v0.8.9'], [item['tag'] for item in result['releases']])
        text = daily.render_summary(result, 'Đội: [CHƯA KIỂM]')
        self.assertIn('03/11/2026', text)
        self.assertIn('v0.8.9 (thử)', text)
        self.assertIn('#7@aaaaaaa: Đạt', text)
        self.assertNotIn('Worker', ' '.join(api.calls))

    def test_edited_comment_is_newest_and_old_header_cannot_borrow_new_sha_in_body(self):
        edited = comment('Cần sửa', created='2026-11-02T03:00:00Z',
                         updated='2026-11-03T00:30:00Z')
        older = comment('Đạt')
        old_head = comment('Đạt', header='b' * 7, updated='2026-11-03T00:59:00Z')
        old_head['body'] += '\nNhắc head mới ' + HEAD
        result = daily.latest_verdict([edited, older, old_head], [], HEAD)
        self.assertEqual('Cần sửa', result['status'])

    def test_outsider_and_native_review_on_old_commit_cannot_approve_current_head(self):
        forged = comment('Đạt', association='CONTRIBUTOR')
        native = dict(comment('Đạt'), commit_id='b' * 40, state='APPROVED')
        result = daily.latest_verdict([forged], [native], HEAD)
        self.assertEqual('chưa duyệt head', result['status'])
        native.update(commit_id=HEAD, body='', state='CHANGES_REQUESTED')
        self.assertEqual('Cần sửa', daily.latest_verdict([], [native], HEAD)['status'])
        native['state'] = 'DISMISSED'
        self.assertEqual('chưa duyệt head', daily.latest_verdict([], [native], HEAD)['status'])

    def test_api_failure_does_not_become_an_empty_success_report(self):
        api = API()
        api.releases = [{'tag_name': 'bad', 'draft': False}]
        with self.assertRaises(RuntimeError):
            daily.collect_snapshot(api, NOW)

    def test_large_queues_keep_counts_fit_bot_limit_and_do_not_add_untrusted_lines(self):
        snapshot = {
            'at': NOW,
            'open': [{'number': n, 'head': HEAD, 'status': 'chưa duyệt head'} for n in range(1, 60)],
            'merged': [{'number': n} for n in range(1, 1000)],
            'releases': [{'tag': 'v1\nFAKE\x1b' * 20, 'prerelease': True} for _ in range(40)],
        }
        text = daily.render_summary(snapshot, 'Đội: [CHƯA KIỂM]')
        self.assertLessEqual(len(text), daily.telegram.MAX_CHARS)
        self.assertLessEqual(len(text.splitlines()) + 1, 15)
        self.assertIn('Gộp 24h (999)', text)
        self.assertIn('Chờ duyệt: 59', text)
        self.assertIn('Phát hành 24h (40)', text)
        self.assertIn('ở link', text)
        self.assertNotIn('\nFAKE', text)


class Activity(unittest.TestCase):
    opened = [{'branch': 'sol2/V177'}]

    def test_two_hours_is_inclusive_and_actual_push_time_resets_the_clock(self):
        api = API()
        api.pushes = [push(NOW - timedelta(hours=2))]
        self.assertEqual(1, daily.idle_team_count(api, self.opened, NOW))
        api.pushes.append(push(NOW - timedelta(seconds=1)))
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        api.pushes = [push(NOW - timedelta(hours=3), kind='branch_deletion')]
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))

    def test_comments_on_any_issue_or_pr_and_inline_comments_count(self):
        api = API()
        api.pushes = [push(NOW - timedelta(hours=3))]
        api.global_comments = [report('[Sol 2 → PM] Báo ở PR bất kỳ.', NOW - timedelta(minutes=1))]
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        api.global_comments = []
        api.inline_comments = [report('[Sol 2 → PM] Rà dòng mã.', NOW - timedelta(minutes=1))]
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        api.inline_comments[0]['author_association'] = 'CONTRIBUTOR'
        self.assertEqual(1, daily.idle_team_count(api, self.opened, NOW))

    def test_not_started_stopped_and_pausing_only_a_task_are_distinct(self):
        api = API()
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        api.pushes = [push(NOW - timedelta(hours=25))]
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        api.pushes = [push(NOW - timedelta(hours=4))]
        api.global_comments = [report('[Sol 2 → PM] Đã dừng phiên, chờ sếp.', NOW - timedelta(hours=3))]
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        api.global_comments = [report('[Sol 2 → PM] Tạm dừng V180, làm V175.', NOW - timedelta(hours=3))]
        self.assertEqual(1, daily.idle_team_count(api, self.opened, NOW))

    def test_group_count_requires_a_local_open_pr_and_does_not_count_each_member(self):
        api = API()
        api.pushes = [push(NOW - timedelta(hours=3), 'deepseek1/V1')]
        self.assertEqual(0, daily.idle_team_count(api, self.opened, NOW))
        opened = [{'branch': 'deepseek1/V1'}, {'branch': 'deepseek2/V2'}]
        self.assertEqual(1, daily.idle_team_count(api, opened, NOW))
        api.fork = True
        snapshot = daily.collect_snapshot(api, NOW)
        self.assertEqual(0, daily.idle_team_count(api, snapshot['open'], NOW))

    def test_numbered_deepseek_stop_reports_and_later_activity_resume_group(self):
        for branch, name in [('deepseek1/V1', 'DeepSeek'), ('duyet/V1', 'Duyệt DeepSeek')]:
            reporters = [name, name + ' 1', name + '12', 'Trưởng nhóm ' + name,
                         'Trưởng nhóm ' + name + ' 1', 'trưởng nhóm ' + name.lower() + '12',
                         'Trưởng nhóm ' + name + '   2']
            for reporter in reporters:
                with self.subTest(branch=branch, reporter=reporter):
                    api = API()
                    opened = [{'branch': branch}]
                    api.pushes = [push(NOW - timedelta(hours=4), branch)]
                    api.global_comments = [report(
                        f'[{reporter} → PM] Đã dừng phiên, chờ sếp.',
                        NOW - timedelta(hours=3))]
                    self.assertEqual(0, daily.idle_team_count(api, opened, NOW))
                    # Báo dừng không che hoạt động mới hơn của đội.
                    api.pushes.append(push(NOW - timedelta(hours=2), branch))
                    self.assertEqual(1, daily.idle_team_count(api, opened, NOW))
                    api.pushes.pop()
                    api.global_comments.append(report(
                        f'[{reporter} → PM] Làm lại việc.', NOW - timedelta(hours=2)))
                    self.assertEqual(1, daily.idle_team_count(api, opened, NOW))

    def test_numbered_reporter_task_pause_outsider_and_other_prefix_do_not_stop_group(self):
        opened = [{'branch': 'deepseek1/V1'}]
        for body, association in [
                ('[Trưởng nhóm DeepSeek 1 → PM] Tạm dừng V180, làm V175.', 'OWNER'),
                ('[DeepSeek 1 → PM] Đã dừng phiên.', 'CONTRIBUTOR'),
                ('[Trưởng nhóm DeepSeekish 1 → PM] Đã dừng phiên.', 'OWNER')]:
            with self.subTest(body=body, association=association):
                api = API()
                api.pushes = [push(NOW - timedelta(hours=4), 'deepseek1/V1')]
                api.global_comments = [report(body, NOW - timedelta(hours=3), association)]
                self.assertEqual(1, daily.idle_team_count(api, opened, NOW))

    def test_daytime_window_matches_dung_and_does_not_fetch_activity_at_night(self):
        for hour, minute, expected in [(6, 59, None), (7, 0, 1), (23, 59, 1), (0, 0, None)]:
            with self.subTest(hour=hour, minute=minute):
                now = datetime(2026, 11, 3, hour, minute,
                               tzinfo=timezone(timedelta(hours=7))).astimezone(timezone.utc)
                api = API()
                api.now = now
                api.pushes = [push(now - timedelta(hours=3))]
                self.assertEqual(expected, daily.idle_team_count(api, self.opened, now))
                if expected is None:
                    self.assertEqual([], api.calls)


class Delivery(unittest.TestCase):
    def test_preview_never_sends_and_send_reuses_existing_json_sender_once(self):
        sent = []

        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *_): return False

        def opener(request, timeout):
            sent.append(request)
            return Response()

        sender = lambda env: daily.telegram.run(env, opener)
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(0, daily.run(send_env(), api=API(), now=NOW, sender=sender))
        self.assertEqual([], sent)

        self.assertNotIn('fixture-only', output.getvalue())
        with redirect_stdout(io.StringIO()):
            self.assertEqual(0, daily.run(send_env(), api=API(), now=NOW, send=True, sender=sender))
        self.assertEqual(1, len(sent))
        payload = json.loads(sent[0].data)
        self.assertLessEqual(len(payload['text'].splitlines()), 15)
        self.assertIn('Đội im ≥2h: 0', payload['text'])
        self.assertIn('Phát hành 24h', payload['text'])
        self.assertTrue(payload['disable_web_page_preview'])

    def test_api_error_missing_channel_and_disallowed_author_send_nothing(self):
        cases = [(API(), dict(send_env(), TELEGRAM_BOT_TOKEN='')),
                 (API(), dict(send_env(), ALLOWED_LOGINS='someone-else'))]
        broken = API()
        broken.list_all = lambda _: (_ for _ in ()).throw(daily.digest.GitHubAPIError(429))
        cases.append((broken, send_env()))
        sent = []
        for api, env in cases:
            with self.subTest(env_keys=list(env)):
                with redirect_stderr(io.StringIO()), redirect_stdout(io.StringIO()):
                    result = daily.run(env, api=api, now=NOW, send=True,
                                       sender=lambda value: sent.append(value))
                self.assertEqual(1, result)
        self.assertEqual([], sent)


class Pagination(unittest.TestCase):
    def test_numeric_alias_keeps_page_two_and_is_bound_to_current_repository(self):
        class Pages(daily.DailyAPI):
            seen = []
            def _request_json(self, route):
                self.seen.append(self._url(route))
                if len(self.seen) == 1:
                    return [{'body': 'khác'}] * 100, {
                        'Link': '<https://api.github.com/repositories/42/issues/comments?page=2>; rel="next"'}
                return [report('[Sol 2 → PM] Tin ở trang hai.', NOW)], {}
        api = Pages('owner/repo', 'fixture-only', '42')
        rows = api.list_all('/issues/comments?per_page=100')
        self.assertEqual(101, len(rows))
        self.assertEqual('[Sol 2 → PM] Tin ở trang hai.', rows[-1]['body'])
        self.assertEqual('https://api.github.com/repos/owner/repo/issues/comments?page=2', api.seen[-1])
        for url in ('https://api.github.com/repositories/43/issues/comments?page=2',
                    'https://elsewhere.test/repositories/42/issues/comments?page=2',
                    'https://api.github.com/repos/other/repo/issues/comments?page=2'):
            with self.subTest(url=url), self.assertRaises(RuntimeError):
                api._url(url)


if __name__ == '__main__':
    unittest.main()
