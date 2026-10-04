import importlib.util
from pathlib import Path
import tempfile
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location('verdict', Path(__file__).with_name('ci-rgss-real-verdict.py'))
v = importlib.util.module_from_spec(spec); spec.loader.exec_module(v)

class VerdictTest(unittest.TestCase):
    def fixture(self, out, case, error='', alive=True, black=False):
        (out/f'{case}-observed-seconds.txt').write_text('60')
        (out/f'{case}-pid.txt').write_text('1234' if alive else '')
        for phase in ['before-key', 'after-60s']:
            (out/f'{case}-{phase}.xml').write_text(f'<hierarchy><node text="{error}"/></hierarchy>')
        image=Image.new('RGB',(20,10), 'black')
        if not black:
            for x in range(10):
                for y in range(10):image.putpixel((x,y),(255,255,255))
        image.save(out/f'{case}-after-60s.png')

    def test_expected_errors_and_healthy_xp_pass_the_gate(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)
            for case,error in [('xp',''),('vx','Error occured'),('ace','RuntimeError')]:self.fixture(p,case,error)
            result=v.verdicts(p,[dict(id=c,prepared=True) for c in ['xp','vx','ace']],{'vx','ace'})
            self.assertTrue(result['matchesExpectation']);self.assertEqual('PASS_LEVEL_1',result['games'][0]['verdict'])

    def test_dead_black_or_dialog_game_fails_without_exception(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)
            for options in [dict(alive=False),dict(black=True),dict(error='SyntaxError')]:
                self.fixture(p,'xp',**options)
                self.assertFalse(v.verdicts(p,[dict(id='xp',prepared=True)],set())['matchesExpectation'])

    def test_unexpected_recovery_requires_removing_expect_fail(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);self.fixture(p,'vx')
            result=v.verdicts(p,[dict(id='vx',prepared=True)],{'vx'})
            self.assertFalse(result['matchesExpectation']);self.assertIn('expect_fail',result['games'][0]['note'])

    def test_alive_but_short_observation_is_not_60_seconds(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);self.fixture(p,'xp')
            (p/'xp-observed-seconds.txt').write_text('59')
            self.assertFalse(v.verdicts(p,[dict(id='xp',prepared=True)],set())['matchesExpectation'])

    def test_missing_evidence_and_unknown_expectation_never_pass(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);cases=[dict(id='vx',prepared=True)]
            self.assertFalse(v.verdicts(p,cases,{'vx'})['matchesExpectation'])
            with self.assertRaises(ValueError):v.verdicts(p,cases,{'typo'})

if __name__=='__main__':unittest.main()
