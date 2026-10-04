#!/usr/bin/env python3
"""Test nhanh cho script của pm-kiem-thu (không cần mạng): python3 .claude/skills/pm-kiem-thu/scripts/test-scripts.py"""
import importlib.util, json, os, struct, subprocess, sys, tempfile, zipfile
D = os.path.dirname(os.path.abspath(__file__))


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, os.path.join(D, file))
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m


def elf(machine=183, cls=2):  # đầu ELF 20 byte tối thiểu cho val-goi-that
    return b'\x7fELF' + bytes([cls, 1, 1]) + b'\0' * 11 + struct.pack('<H', machine)


def zipof(path, files):
    with zipfile.ZipFile(path, 'w') as z:
        for n, b in files.items(): z.writestr(n, b)


v = load('val', 'val-goi-that.py')
t = tempfile.mkdtemp()
ok = lambda c, m: (print(('PASS ' if c else 'FAIL ') + m), c)[1]
r = []
z = os.path.join(t, 'a.zip')
zipof(z, {'libx.so': elf(), 'needed.txt': 'libc.so\n'}); r.append(ok(v.run(z, 'libx.so', 'arm64-v8a').startswith('OK'), 'gói đạt'))
zipof(z, {'other.so': elf()}); r.append(ok('không có libx.so' in v.run(z, 'libx.so', 'arm64-v8a'), 'thiếu file chính'))
zipof(z, {'libx.so': elf(40, 1)}); r.append(ok('FAIL ELF' in v.run(z, 'libx.so', 'arm64-v8a'), 'sai ABI'))
zipof(z, {'libx.so': elf(), 'needed.txt': 'libthieu.so\n'}); r.append(ok('needed thiếu' in v.run(z, 'libx.so', 'arm64-v8a'), 'needed.txt thiếu thư viện'))
zipof(z, {'../evil.so': elf(), 'libx.so': elf()}); r.append(ok('vượt thư mục' in v.run(z, 'libx.so', 'arm64-v8a'), 'chặn zip-slip'))
zipof(z, {'libx.so': elf() + b'\0' * 5000}); v.MAX_ENTRY = 1000; r.append(ok('quá lớn' in v.run(z, 'libx.so', 'arm64-v8a'), 'giới hạn kích thước file'))
v.MAX_ENTRY = 10**9; v.MAX_TOTAL = 1000; r.append(ok('tổng giải nén' in v.run(z, 'libx.so', 'arm64-v8a'), 'giới hạn tổng'))
v.MAX_TOTAL = 10**9; v.MAX_FILES = 1; zipof(z, {'libx.so': elf(), 'b': b'1'}); r.append(ok('quá nhiều file' in v.run(z, 'libx.so', 'arm64-v8a'), 'giới hạn số file'))

# manifest/needed có đường dẫn thoát thư mục tạm
secret = os.path.join(t, 'ngoai.txt'); open(secret, 'w').write('x')
v.MAX_ENTRY = 10**9; v.MAX_TOTAL = 10**9; v.MAX_FILES = 5000
mk = lambda man: zipof(z, {'libx.so': elf(), 'manifest.json': json.dumps(man)})
mk({'loadOrder': ['../ngoai.txt']}); r.append(ok('không hợp lệ' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest loadOrder có ../ bị chặn'))
mk({'files': {'../ngoai.txt': {'sha256': '0' * 64}}}); r.append(ok('không hợp lệ' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest files có ../ bị chặn (không băm file ngoài)'))
mk({'loadOrder': [secret]}); r.append(ok('không hợp lệ' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest đường dẫn tuyệt đối bị chặn'))
mk({'files': {'libx.so': 5}}); r.append(ok('sai dạng' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest files sai dạng bị từ chối'))
zipof(z, {'libx.so': elf(), 'manifest.json': '{khong json'}); r.append(ok('không đọc được' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest hỏng bị từ chối'))
zipof(z, {'libx.so': elf(), 'needed.txt': '../ngoai.txt\n'}); r.append(ok('không hợp lệ' in v.run(z, 'libx.so', 'arm64-v8a'), 'needed.txt có ../ bị chặn'))
import hashlib
mk({'loadOrder': ['libx.so'], 'files': {'libx.so': {'size': len(elf()), 'sha256': hashlib.sha256(elf()).hexdigest()}}}); r.append(ok(v.run(z, 'libx.so', 'arm64-v8a').startswith('OK'), 'manifest hợp lệ (size + sha256) vẫn đạt'))
v.MAX_ENTRY = 10; r.append(ok('quá lớn' in v.run(z, 'libx.so', 'arm64-v8a'), 'file vượt giới hạn bị chặn trước khi băm')); v.MAX_ENTRY = 10**9

# giới hạn metadata
big = lambda files: zipof(z, dict({'libx.so': elf()}, **files))
big({'manifest.json': '{"loadOrder": [' + ','.join(['"a"'] * 10) + ']}'}); v.MAX_MANIFEST = 20
r.append(ok('manifest.json quá lớn' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest.json vượt giới hạn byte')); v.MAX_MANIFEST = 1 << 20
big({'manifest.json': json.dumps({'loadOrder': ['libx.so'] * 5})}); v.MAX_ENTRIES = 3
r.append(ok('quá nhiều mục' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest vượt giới hạn số mục'))
big({'needed.txt': 'libc.so\n' * 5}); r.append(ok('quá nhiều dòng' in v.run(z, 'libx.so', 'arm64-v8a'), 'needed.txt vượt giới hạn số dòng')); v.MAX_ENTRIES = 1000
v.MAX_NEEDED = 10; r.append(ok('needed.txt quá lớn' in v.run(z, 'libx.so', 'arm64-v8a'), 'needed.txt vượt giới hạn byte')); v.MAX_NEEDED = 64 * 1024
big({'manifest.json': '[' * 100000}); r.append(ok('không đọc được' in v.run(z, 'libx.so', 'arm64-v8a'), 'manifest lồng quá sâu bị từ chối, không sập'))

st = os.path.join(t, 'state.json')
json.dump({'updatedAt': 'x', 'tasks': [{'code': 'V1', 'title': 't', 'owner': 'o', 'status': 'cho', 'note': ''}],
           'modules': [{'id': 'm', 'steps': {'build': 'cho'}}], 'log': []}, open(st, 'w'))
b = lambda *a: subprocess.run([sys.executable, os.path.join(D, 'bang-cap-nhat.py'), '--state', st, *a], capture_output=True, text=True)
r.append(ok(b('task', 'V1', '--status', 'dang').returncode == 0, 'status hợp lệ được ghi'))
x = b('task', 'V1', '--status', 'hoan-thanh'); r.append(ok(x.returncode != 0 and json.load(open(st))['tasks'][0]['status'] == 'dang', 'status sai bị từ chối, không ghi'))
r.append(ok(b('module', 'm', 'build', 'xong').returncode == 0, 'bước module hợp lệ'))
r.append(ok(b('module', 'm', 'build', 'lung-tung').returncode != 0 and json.load(open(st))['modules'][0]['steps']['build'] == 'xong', 'trạng thái bước sai bị từ chối'))
sys.exit(0 if all(r) else 1)
