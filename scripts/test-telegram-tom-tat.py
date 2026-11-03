"""V177: dữ liệu giả, thời gian cố định, không gọi GitHub/Telegram."""
from datetime import datetime, timedelta, timezone
import importlib.util
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

    def list_all(self, route):
        self.calls.append(route)
        return {
            '/pulls?state=open&per_page=100': [{'number': 7, 'head': {'sha': HEAD, 'ref': 'sol2/V177'}}],
            '/issues/7/comments?per_page=100': self.comments,
            '/pulls/7/reviews?per_page=100': [],
            '/releases?per_page=100': self.releases,
        }[route]

    def list_recent(self, route, cutoff, field):
        self.calls.append(route)
        assert cutoff == NOW - timedelta(hours=24)
        assert field == 'updated_at'
        return self.closed


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


if __name__ == '__main__':
    unittest.main()
