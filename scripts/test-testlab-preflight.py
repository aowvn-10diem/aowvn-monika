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


if __name__ == '__main__':
    unittest.main()
