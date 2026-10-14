#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("kiem_nuot_loi", HERE / "kiem-nuot-loi.py")
guard = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = guard
spec.loader.exec_module(guard)


class HandlerTest(unittest.TestCase):
    def bad(self, source, count=1, before=""):
        self.assertEqual(len(guard.new_violations(before, source)), count)

    def test_new_catch_and_run_catching_fail(self):
        self.bad('try { fail() } catch (e: Exception) { null }; runCatching { fail() }', 2)

    def test_logged_direct_and_failure_callbacks_pass(self):
        for source in [
            'try { fail() } catch (e: Exception) { Diagnostics.recordHandled(c,"op","step",e) }',
            'runCatching { fail() }.onFailure { Diagnostics.recordCoreFailure(c,"op",it.toString()) }',
            'runCatching { fail() }.getOrElse { Diagnostics.opFail(c,"op",it); null }',
            'runCatching { Diagnostics.op(c,"op") { fail() } }',
            'runCatching { fail() }.getOrThrow()',
        ]:
            with self.subTest(source=source):
                self.bad(source, 0)

    def test_log_names_or_strings_are_not_logging(self):
        self.bad('runCatching { "Diagnostics.recordHandled(c)"; /* Diagnostics.opFail(c) */ fail() }')
        self.bad('catch (e: Exception) { val name = Diagnostics.recordHandled; null }')
        self.bad('runCatching { Diagnostics.begin(c); fail() }')

    def test_nested_handler_needs_its_own_diagnostic(self):
        self.bad('runCatching { Diagnostics.recordHandled(c,"x","y"); try { fail() } catch(e: Exception) { null } }')

    def test_parenthesized_and_referenced_lambda_calls(self):
        self.bad('runCatching({ fail() })')
        self.bad('runCatching(block = { fail() })')
        self.bad('runCatching(action)')
        self.bad('runCatching({ fail() }).onFailure { Diagnostics.recordHandled(c,"x","y",it) }', 0)

    def test_multiline_generics_and_braces_in_literals(self):
        self.bad('runCatching\n <String> { val s = "}}"; fail() }')
        self.bad("catch (e: Exception) { val char = '}'; null }")
        self.bad('val s = "runCatching { fail() }"', 0)
        self.bad('val s = """catch(e: Exception) { fail() }"""', 0)

    def test_string_template_code_is_not_hidden(self):
        self.bad('val s = "${runCatching { fail() }}"')
        self.bad('val s = """${runCatching { "nested ${runCatching { fail() }}" }}"""', 2)

    def test_nested_comments_are_ignored(self):
        self.bad('/* /* inner */ runCatching { fail() } */ // catch(e) { fail() }', 0)

    def test_reason_must_be_adjacent_nonempty_and_not_placeholder(self):
        self.bad('// diag: bỏ qua cache tùy chọn, đã có fallback\nrunCatching { fail() }', 0)
        self.bad('runCatching { fail() } // diag: bỏ qua nhánh thử giả', 0)
        for reason in ['', ' ', '<lý do>', '123']:
            self.bad('// diag: bỏ qua ' + reason + '\nrunCatching { fail() }')
        self.bad('// diag: bỏ qua cache tùy chọn\n\nrunCatching { fail() }')

    def test_baseline_is_not_a_blanket_allowance(self):
        old = 'runCatching { legacy() }'
        self.bad('/* comment */ runCatching { legacy() }', 0, old)
        self.bad(old + '; runCatching { newOperation() }', 1, old)
        self.bad(old + '; ' + old, 1, old)
        self.bad('runCatching { changedOperation() }', 1, old)
        self.bad('runCatching { operation("changed") }', 1,
                 'runCatching { operation("legacy") }')

    def test_removing_log_or_waiver_introduces_a_violation(self):
        self.bad('runCatching { fail() }', before='runCatching { fail(); Diagnostics.opFail(c,"x",e) }')
        self.bad('runCatching { fail() }', before='// diag: bỏ qua test riêng\nrunCatching { fail() }')

    def test_malformed_source_fails_closed(self):
        for source in ['runCatching { fail()', '/* never closed', 'val s = "never closed', 'val s = "${runCatching { fail() }']:
            with self.subTest(source=source), self.assertRaises(ValueError):
                guard.silent_handlers(source)


class GitBoundaryTest(unittest.TestCase):
    def test_diverged_pr_target_uses_common_baseline_and_still_blocks_new_handlers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', '-C', directory, *args], stderr=subprocess.DEVNULL, text=True).strip()
            def commit():
                git('add', '.')
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture')
                return git('rev-parse', 'HEAD')
            def write(source):
                path = root / (guard.PREFIX + 'library/Legacy.kt')
                path.parent.mkdir(parents=True, exist_ok=True); path.write_text(source)
            def check(base, head, *options):
                return subprocess.run(['bash', str(HERE / 'kiem-nuot-loi.sh'), '--base', base, '--head', head, *options], cwd=root, capture_output=True, text=True)
            git('init', '-q'); write('runCatching { legacy() }'); common = commit()
            git('checkout', '-qb', 'fixture-feature', common)
            write('runCatching { legacy() }; runCatching { newOperation() }.getOrThrow()'); good = commit()
            git('checkout', '-qb', 'fixture-target', common)
            write('runCatching { upstreamOperation() }.getOrThrow()'); target = commit()
            self.assertEqual(check(target, good).returncode, 2)  # Push ancestry remains strict.
            self.assertEqual(check(target, good, '--pull-request').returncode, 0)
            git('checkout', '-q', 'fixture-feature')
            write('runCatching { legacy() }; runCatching { newOperation() }'); bad = commit()
            result = check(target, bad, '--pull-request')
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn('Legacy.kt:1', result.stdout)
            self.assertEqual(check('f' * 40, bad, '--pull-request').returncode, 2)
            self.assertEqual(check('0' * 40, bad, '--pull-request').returncode, 2)

    def test_real_git_scope_rename_new_branch_missing_history_and_exit_codes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.check_output(['git', '-C', directory, *args], stderr=subprocess.DEVNULL, text=True).strip()

            def commit():
                git('add', '.')
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture')
                return git('rev-parse', 'HEAD')

            def write(path, source):
                p = root / path
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(source)

            def check(base, head):
                return subprocess.run(['bash', str(HERE / 'kiem-nuot-loi.sh'), '--base', base, '--head', head],
                                      cwd=root, capture_output=True, text=True)

            git('init', '-q')
            write(guard.PREFIX + 'library/Legacy.kt', 'runCatching { legacy() }')
            write(guard.PREFIX + 'settings/Outside.kt', 'runCatching { unrelated() }')
            base = commit()
            git('update-ref', 'refs/remotes/origin/main', base)
            for scope in guard.SCOPES:
                write(guard.PREFIX + scope + '/New.kt', 'runCatching { fail() }')
            head = commit()
            result = check(base, head)
            self.assertEqual(result.returncode, 1, result.stderr)
            for scope in guard.SCOPES:
                self.assertIn(scope + '/New.kt:1', result.stdout)
            self.assertNotIn('Legacy.kt:', result.stdout)
            self.assertNotIn('Outside.kt:', result.stdout)
            self.assertEqual(check('0' * 40, head).returncode, 1)
            self.assertEqual(check('f' * 40, head).returncode, 2)
            self.assertEqual(check('main', head).returncode, 2)
            for scope in guard.SCOPES:
                write(guard.PREFIX + scope + '/New.kt', '// diag: bỏ qua fixture kiểm CI\nrunCatching { fail() }')
            good = commit()
            self.assertEqual(check(base, good).returncode, 0)
            git('mv', guard.PREFIX + 'settings/Outside.kt', guard.PREFIX + 'pack/Moved.kt')
            moved = commit()
            self.assertEqual(check(good, moved).returncode, 1)


if __name__ == '__main__':
    unittest.main()
