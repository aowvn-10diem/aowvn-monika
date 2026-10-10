import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('testlab-preflight.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class PhysicalGate(unittest.TestCase):
    def setUp(self):
        self.checks = [dict(name='Build', head_sha='new', conclusion='success')]
        self.changed = ['kirikiri/src/native.cpp']
        self.rows = [dict(day='2026-10-08', run=1, units=2)]

    def check(self, **overrides):
        args = dict(sha='new', checks=self.checks, changed=self.changed,
                    rows=self.rows, day='2026-10-08', history_ids=[1])
        args.update(overrides)
        return gate.physical_gate(**args)

    def test_budget_counts_failed_or_reserved_units(self):
        self.assertEqual(2, self.check())
        with self.assertRaisesRegex(ValueError, 'exhausted'):
            self.check(rows=[dict(day='2026-10-08', run=1, units=5)])

    def test_missing_ledger_and_wrong_head_fail_closed(self):
        with self.assertRaisesRegex(ValueError, 'Unrecorded'):
            self.check(history_ids=[1, 2])
        with self.assertRaisesRegex(ValueError, 'exact SHA'):
            self.check(checks=[dict(name='Build', head_sha='old', conclusion='success')])

    def test_ui_or_workflow_change_cannot_spend_physical_quota(self):
        with self.assertRaisesRegex(ValueError, 'No engine'):
            self.check(changed=['app/src/main/java/vn/aow/monika/ui/HomeScreen.kt', '.github/workflows/test-lab.yml'])
        self.assertEqual(2, self.check(changed=['app/src/main/java/vn/aow/monika/pack/PackTransaction.kt']))

    def test_midnight_resets_budget_but_not_exact_head_gate(self):
        self.assertEqual(0, self.check(day='2026-10-09', history_ids=[]))

    def test_reserved_rejected_run_counts_but_is_not_tested_baseline(self):
        rows = [dict(day='2026-10-08', run=1, units=3, head='tested', purpose='Physical failed'),
                dict(day='2026-10-08', run=2, units=2, head='unstarted', purpose='[DỰ TRỮ] Quota rejected')]
        self.assertEqual('tested', gate.last_physical_baseline(rows))
        with self.assertRaisesRegex(ValueError, 'exhausted'):
            self.check(rows=rows, history_ids=[1, 2])
        with self.assertRaisesRegex(ValueError, 'no tested baseline'):
            gate.last_physical_baseline(rows[1:])


class PhysicalRerunGate(unittest.TestCase):
    def test_attempt_two_is_blocked_before_any_api_or_git_read(self):
        with patch.dict(gate.os.environ, {'GITHUB_RUN_ATTEMPT': '2'}, clear=True), \
             patch('sys.argv', ['testlab-preflight.py', '--physical']), \
             patch.object(gate, 'gh') as api, \
             patch.object(gate.subprocess, 'check_output') as git:
            with self.assertRaisesRegex(SystemExit, 'Physical rerun forbidden'):
                gate.main()
            api.assert_not_called()
            git.assert_not_called()

    def test_missing_invalid_and_later_attempts_fail_closed(self):
        for attempt in (None, '', '0', '-1', 'not-a-number', '3'):
            with self.subTest(attempt=attempt):
                with self.assertRaisesRegex(ValueError, 'GITHUB_RUN_ATTEMPT must be 1'):
                    gate.require_first_physical_attempt(attempt)
        gate.require_first_physical_attempt('1')


class PhysicalHistoryGate(unittest.TestCase):
    def setUp(self):
        PhysicalGate.setUp(self)

    def check(self, **overrides):
        return PhysicalGate.check(self, **overrides)
    def record(self, attempts=2, created='2026-10-08T08:00:00Z'):
        return dict(id=7, run_attempt=attempts, created_at=created, updated_at='2026-10-08T09:00:00Z')

    def jobs(self, started, conclusion, name='Chạy K1–K8 Kirikiri'):
        return dict(total_count=1, jobs=[dict(started_at='2026-10-08T09:00:00Z', steps=[
            dict(name=name, started_at=started, conclusion=conclusion)])])

    def test_latest_blocked_attempt_cannot_hide_first_physical_attempt(self):
        replies = [self.jobs('2026-10-08T08:00:00Z', 'failure'), self.jobs(None, 'skipped')]
        with patch.object(gate, 'gh', side_effect=replies) as api:
            history = gate.physical_history('repo', [('test-lab-engine-games.yml', [self.record()])], '2026-10-08', 99)
        self.assertEqual({7: 4}, history)  # old/unknown two-model request ×2 attempts, including rejected intent
        self.assertIn('/attempts/1/jobs?', api.call_args_list[0].args[0])
        self.assertIn('/attempts/2/jobs?', api.call_args_list[1].args[0])
        with self.assertRaisesRegex(ValueError, 'Unrecorded'):
            self.check(history_ids=history)
        with self.assertRaisesRegex(ValueError, 'attempts exceed'):
            self.check(rows=[dict(day='2026-10-08', run=7, units=2)], history_ids=history)
        self.assertEqual(4, self.check(rows=[dict(day='2026-10-08', run=7, units=4)], history_ids=history))

    def test_rerun_of_yesterdays_run_is_reserved_on_todays_attempt_date(self):
        old = dict(total_count=1, jobs=[dict(started_at='2026-10-07T08:00:00Z', steps=[
            dict(name='Chạy robot test', started_at='2026-10-07T08:00:00Z', conclusion='failure')])])
        with patch.object(gate, 'gh', side_effect=[old, self.jobs(None, 'skipped', 'Chạy robot test')]):
            history = gate.physical_history('repo', [('test-lab.yml', [self.record(created='2026-10-07T08:00:00Z')])], '2026-10-08', 99)
        self.assertEqual({7: 2}, history)

    def test_explicit_virtual_attempt_does_not_consume_physical_quota(self):
        with patch.object(gate, 'gh', return_value=self.jobs('2026-10-08T09:00:00Z', 'success', 'Chạy Robo máy ảo (không quota máy thật)')):
            self.assertEqual({}, gate.physical_history('repo', [('test-lab.yml', [self.record(1)])], '2026-10-08', 99))

    def test_unknown_attempts_truncated_jobs_and_api_budget_fail_closed(self):
        with self.assertRaisesRegex(ValueError, 'attempt count'):
            gate.physical_history('repo', [('w', [self.record(11)])], '2026-10-08', 99)
        with patch.object(gate, 'gh', return_value=dict(total_count=2, jobs=[])):
            with self.assertRaisesRegex(ValueError, 'truncated'):
                gate.physical_history('repo', [('w', [self.record(1)])], '2026-10-08', 99)
        with patch.object(gate, 'gh', return_value=self.jobs(None, 'skipped')) as api:
            records = [dict(self.record(10), id=n) for n in range(5)]
            with self.assertRaisesRegex(ValueError, 'API budget'):
                gate.physical_history('repo', [('w', records)], '2026-10-08', 99)
            self.assertEqual(40, api.call_count)


if __name__ == '__main__':
    unittest.main()
