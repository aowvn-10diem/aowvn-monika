"""Exercise the real retry helper against an offline SDK manager and filesystem."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('ci-install-j2me-ndk.sh')
NDK = 'ndk;22.1.7171670'
CMAKE = 'cmake;3.22.1'
INSTALL = '''
import os,pathlib
root=pathlib.Path(os.environ['ANDROID_HOME'])
def install(package, revision=None, broken=False):
    kind,version=package.split(';')
    directory=root/kind/version
    directory.mkdir(parents=True,exist_ok=True)
    (directory/'source.properties').write_text('Pkg.Revision = '+(revision or version)+'\\n')
    if kind=='ndk':
        compiler=directory/'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
        include=directory/'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/jni.h'
        include.parent.mkdir(parents=True,exist_ok=True); include.write_text('test header')
    else: compiler=directory/'bin/cmake'
    compiler.parent.mkdir(parents=True,exist_ok=True)
    compiler.write_text('#!/bin/sh\\nexit '+('1' if broken else '0')+'\\n'); compiler.chmod(0o755)
'''


class NdkInstall(unittest.TestCase):
    def run_case(self, failures=0, partial=False, preinstalled=(), broken=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); sdk = root/'sdk'; sdk.mkdir()
            manager = sdk/'cmdline-tools/latest/bin/sdkmanager'; manager.parent.mkdir(parents=True)
            manager.write_text('#!/usr/bin/env python3\n'+INSTALL+'''
import json,sys
log=root/'calls.jsonl'
previous=log.read_text().splitlines() if log.exists() else []
with log.open('a') as target: target.write(json.dumps(sys.argv[1:])+'\\n')
if len(previous)<int(os.environ['FAILURES']):
    # A damaged extracted package left by the failed download.
    for package in sys.argv[2:]: install(package,revision='0')
    print('Error on ZipFile unknown archive',file=sys.stderr)
    sys.exit(7)
if os.environ['PARTIAL']=='1': sys.exit(0)
for package in sys.argv[2:]: install(package)
'''); manager.chmod(0o755)
            binary=root/'bin'; binary.mkdir()
            sleep=binary/'sleep'; sleep.write_text('#!/bin/sh\nexit 0\n'); sleep.chmod(0o755)
            env=dict(os.environ,ANDROID_HOME=str(sdk),RUNNER_TEMP=str(root),
                     PATH=str(binary)+os.pathsep+os.environ['PATH'],FAILURES=str(failures),PARTIAL=str(int(partial)))
            if preinstalled:
                subprocess.run(['python3','-c',INSTALL+'\nimport json\nfor p in json.loads(os.environ["PACKAGES"]): install(p,broken='+repr(broken)+')'],
                               env=dict(env,PACKAGES=json.dumps(preinstalled)),check=True)
            other=sdk/'ndk/99.0.0'; other.mkdir(parents=True); (other/'keep').write_text('untouched')
            result=subprocess.run(['bash',str(SCRIPT)],env=env,capture_output=True,text=True,timeout=20)
            calls=[json.loads(line) for line in (sdk/'calls.jsonl').read_text().splitlines()] if (sdk/'calls.jsonl').exists() else []
            self.assertEqual('untouched',(other/'keep').read_text())
            self.assertFalse(list(root.glob('monika-sdk-v123.*')))
            return result,calls

    def test_success_ignores_yes_sigpipe_and_verifies_packages(self):
        result,calls=self.run_case()
        self.assertEqual(0,result.returncode,result.stderr+result.stdout)
        self.assertEqual([['--install',NDK,CMAKE]],calls)

    def test_two_bad_archives_recover_on_third_attempt(self):
        result,calls=self.run_case(failures=2)
        self.assertEqual(0,result.returncode,result.stderr+result.stdout)
        self.assertEqual(3,len(calls))
        self.assertIn('attempt 3/3',result.stdout)

    def test_permanent_failure_stops_at_three_and_preserves_error(self):
        result,calls=self.run_case(failures=99)
        self.assertEqual(7,result.returncode)
        self.assertEqual(3,len(calls))

    def test_zero_exit_with_missing_packages_is_failure(self):
        result,calls=self.run_case(partial=True)
        self.assertNotEqual(0,result.returncode)
        self.assertEqual(3,len(calls))

    def test_verified_install_does_not_download_again(self):
        result,calls=self.run_case(preinstalled=(NDK,CMAKE))
        self.assertEqual(0,result.returncode)
        self.assertEqual([],calls)

    def test_only_incomplete_cmake_is_reinstalled(self):
        result,calls=self.run_case(preinstalled=(NDK,))
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertEqual([['--install',CMAKE]],calls)

    def test_present_but_unusable_tools_are_reinstalled(self):
        result,calls=self.run_case(preinstalled=(NDK,CMAKE),broken=True)
        self.assertEqual(0,result.returncode,result.stdout)
        self.assertEqual([['--install',NDK,CMAKE]],calls)


if __name__=='__main__': unittest.main()
