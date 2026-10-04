#!/usr/bin/env python3
"""Hai hướng: emulator Linux ARM64 chính thức; backend KVM cho ARM virtual device."""
import json
import os
import pathlib
import platform
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

out = pathlib.Path('out/arm-emulator'); out.mkdir(parents=True, exist_ok=True)
kvm = pathlib.Path('/dev/kvm')
report = dict(hostMachine=platform.machine(), kvmExists=kvm.exists(),
              kvmReadable=os.access(kvm, os.R_OK), kvmWritable=os.access(kvm, os.W_OK),
              androidHome=bool(os.environ.get('ANDROID_HOME')), directions=[])
# Kho SDK công khai, không tải APK/game/ROM hay image trước khi biết host hỗ trợ.
with urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml', timeout=60) as r:
    tree = ET.fromstring(r.read())
archives = []
for package in tree:
    if package.tag.endswith('remotePackage') and package.attrib.get('path') == 'emulator':
        for archive in package.findall('.//{*}archive'):
            if archive.findtext('{*}host-os') == 'linux':
                archives.append(dict(hostArch=archive.findtext('{*}host-arch'),
                                     url=archive.findtext('{*}complete/{*}url')))
if not archives:
    raise RuntimeError('Không đọc được danh sách emulator Linux; chưa đủ dữ kiện kết luận')
report['sdkLinuxArchives'] = archives
native = [a for a in archives if a['hostArch'] in ['aarch64', 'arm64'] or 'aarch64' in (a['url'] or '') or 'arm64' in (a['url'] or '')]
report['directions'].append(dict(direction='Emulator Linux ARM64 chính thức', available=bool(native)))
report['directions'].append(dict(direction='ARM virtual device dùng KVM', available=report['kvmReadable'] and report['kvmWritable']))
emulator = shutil.which('emulator')
if not emulator and os.environ.get('ANDROID_HOME'):
    path = pathlib.Path(os.environ['ANDROID_HOME'], 'emulator/emulator')
    if path.is_file(): emulator = str(path)
if emulator:
    report['emulatorFile'] = subprocess.run(['file', emulator], capture_output=True, text=True).stdout.strip().split(': ', 1)[-1]
    try:
        result = subprocess.run([emulator, '-version'], capture_output=True, text=True, timeout=30)
        report['emulatorExitCode'] = result.returncode
        report['emulatorVersion'] = (result.stdout + result.stderr)[:1500]
    except (OSError, subprocess.TimeoutExpired) as e:
        report['emulatorError'] = type(e).__name__ + ': ' + str(e)
report['result'] = 'NEEDS_BOOT_TEST' if native or (report['kvmReadable'] and report['kvmWritable']) else 'NO_STANDARD_EMULATOR_ROUTE'
report['bootSeconds'] = None
report['kirikiriTestsPassed'] = []
(out / 'capability.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
text = '# V40 — dữ kiện host ARM64\n\n```json\n' + json.dumps(report, ensure_ascii=False, indent=2) + '\n```\n'
(out / 'capability.md').write_text(text)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as f:f.write(text)
print(text)
