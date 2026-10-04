#!/usr/bin/env python3
"""Trích artifact S0 từ APK upstream, không phát hành hoặc chép vào app."""
import json
import os
import pathlib
import re
import subprocess
import zipfile

apk = pathlib.Path(os.environ['EKA_APK'])
ndk = pathlib.Path(os.environ.get('EKA_NDK') or str(pathlib.Path(os.environ['ANDROID_HOME']) / 'ndk/25.1.8937393'))
tools = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
output = pathlib.Path('out/symbian')
output.mkdir(parents=True, exist_ok=True)
records = []
with zipfile.ZipFile(apk) as source:
    for abi in ['arm64-v8a', 'armeabi-v7a']:
        pack = output / abi
        pack.mkdir()
        prefix = f'lib/{abi}/'
        for entry in source.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            relative = None
            if name.startswith(prefix) and name.endswith('.so'):
                relative = pathlib.PurePosixPath(name[len(prefix):])
            elif name.startswith('assets/'):
                path = pathlib.PurePosixPath(name[len('assets/'):])
                if path.parts[0] in ['resources', 'patch', 'compat', 'scripts']:
                    relative = path
            if relative is None:
                continue
            if '..' in relative.parts or relative.is_absolute():
                raise ValueError('APK có đường dẫn ngoài gói')
            dest = pack / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(source.read(entry))
        main = pack / 'libnative-lib.so'
        assert main.is_file(), f'{abi}: thiếu libnative-lib.so'
        for library in pack.glob('*.so'):
            subprocess.run([str(tools / 'llvm-strip'), '--strip-unneeded', str(library)], check=True)
        symbols = subprocess.check_output([str(tools / 'llvm-nm'), '-D', '--defined-only', str(main)], text=True)
        jni = sorted(line.split()[-1] for line in symbols.splitlines() if line.split()[-1].startswith('Java_'))
        (pack / 'jni-symbols.txt').write_text('\n'.join(jni) + ('\n' if jni else ''))
        dynamic = subprocess.check_output([str(tools / 'llvm-readelf'), '-d', str(main)], text=True)
        needed = [m.group(1) for line in dynamic.splitlines() if 'NEEDED' in line for m in [re.search(r'\[(.+)\]', line)] if m]
        (pack / 'needed.txt').write_text('\n'.join(needed) + '\n')
        (pack / 'SOURCE.txt').write_text('https://github.com/EKA2L1/EKA2L1 (GPLv3)\nCommit: ' + os.environ['EKA_COMMIT'] + '\nS0 chỉ CI, không phát hành.\n')
        files = {str(f.relative_to(pack)): f.stat().st_size for f in sorted(pack.rglob('*')) if f.is_file()}
        archive = output / f'symbian-{abi}.zip'
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for file in sorted(pack.rglob('*')):
                if file.is_file():
                    z.write(file, file.relative_to(pack))
        records.append(dict(abi=abi, zipBytes=archive.stat().st_size, files=files, needed=needed, jniCount=len(jni)))
report = dict(commit=os.environ['EKA_COMMIT'], buildSeconds=int(os.environ['EKA_BUILD_SECONDS']), packages=records)
(output / 'measurements.json').write_text(json.dumps(report, indent=2) + '\n')
text = '# S0 — số đo tự động\n\n```json\n' + json.dumps(report, indent=2) + '\n```\n'
(output / 'measurements.md').write_text(text)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as f:
        f.write(text)
print(text)
