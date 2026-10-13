"""V106: generate an original NDS framebuffer program; no ROM, BIOS or firmware download."""
import argparse
import hashlib
import http.server
import json
from pathlib import Path
import struct
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def crc16(data):
    crc = 0xffff
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ (0xa001 if crc & 1 else 0)
    return crc


def framebuffer_program():
    # ARMv5: enable LCD power, map VRAM A as LCD, set main engine to VRAM display,
    # fill 256x192 RGB555 pixels orange, then loop. Literal pool avoids toolchain/SDK.
    code, literals, fixups = [], [], []
    def load(register, value):
        fixups.append((len(code), register, len(literals)))
        code.append(0)
        literals.append(value)
    for address, value, store in ((0x04000304, 0x820f, 0xe1c010b0),
                                  (0x04000240, 0x80, 0xe5c01000),
                                  (0x04000000, 0x20000, 0xe5801000)):
        load(0, address)
        load(1, value)
        code.append(store)
    load(0, 0x06800000)
    load(1, 0x81ff)
    load(2, 256 * 192)
    code += [0xe0c010b2, 0xe2522001, 0x1afffffc, 0xeafffffe]
    for index, register, literal in fixups:
        offset = (len(code) + literal - index) * 4 - 8
        assert 0 <= offset < 4096
        code[index] = 0xe59f0000 | (register << 12) | offset
    return struct.pack('<' + 'I' * (len(code) + len(literals)), *code, *literals)


def make_rom():
    arm9 = framebuffer_program()
    arm7 = struct.pack('<I', 0xeafffffe)  # original idle program, no copied BIOS/logo
    rom = bytearray(128 * 1024)
    rom[:12] = b'MONIKA V106\0'
    rom[12:16] = b'####'
    rom[16:18] = b'00'
    struct.pack_into('<IIII', rom, 0x20, 0x200, 0x02000000, 0x02000000, len(arm9))
    struct.pack_into('<IIII', rom, 0x30, 0x400, 0x03800000, 0x03800000, len(arm7))
    struct.pack_into('<II', rom, 0x80, 0x404, 0x200)
    struct.pack_into('<H', rom, 0x15c, crc16(rom[0xc0:0x15c]))
    struct.pack_into('<H', rom, 0x15e, crc16(rom[:0x15e]))
    rom[0x200:0x200+len(arm9)] = arm9
    rom[0x400:0x404] = arm7
    return bytes(rom)


def prepare(out, root=ROOT):
    out.mkdir(parents=True, exist_ok=True)
    (out / 'web').mkdir(exist_ok=True)
    (out / 'monika.nds').write_bytes(make_rom())
    cfg = json.loads((root / 'config/monika-config.json').read_text())
    url = cfg['cores']['melondsds']['url'].replace('{abi}', 'x86_64')
    archive = out / 'web/melondsds.zip'
    print('V106 downloading test core: two attempts, total deadline 130s', flush=True)
    # urllib's timeout covers individual socket operations, not DNS/whole transfer.
    # Bound the process as well so a trickling response cannot stall the flow job.
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
        '--connect-timeout', '20', '--max-time', '60', '--retry', '1', '--retry-all-errors',
        '--max-filesize', str(20 * 1024 * 1024), '--output', str(archive), url],
        check=True, timeout=130)
    assert archive.stat().st_size <= 20 * 1024 * 1024, 'oversized core archive'
    pack = archive.read_bytes()
    print('V106 core archive downloaded; verifying ELF/hash', flush=True)
    with zipfile.ZipFile(archive) as z:
        entry = next(n for n in z.namelist() if n.endswith('melondsds_libretro_android.so'))
        elf = z.read(entry)
        assert elf[:6] == b'\x7fELF\x02\x01' and struct.unpack_from('<H', elf, 18)[0] == 62, 'not x86_64 ELF'
    digest = hashlib.sha256(pack).hexdigest()
    (out / 'fixture.json').write_text(json.dumps({'generated': True, 'romSha256': hashlib.sha256(make_rom()).hexdigest(),
        'coreSha256': digest, 'coreSource': url, 'abi': 'x86_64'}))
    cfg['configVersion'] = 2147483647  # generated debug APK only, production config unchanged
    cfg['prefetchCores'] = []
    cfg['prefetchByExtension'] = {}
    cfg['crash'] = {'autoSend': False, 'endpoint': ''}
    core = cfg['cores']['melondsds']
    core.update(url='http://localhost:18444/melondsds.zip', version='ci-V106', abis=['x86_64'],
                artifacts={'x86_64': {'url': 'http://localhost:18444/melondsds.zip',
                           'version': 'ci-V106', 'sha256': digest, 'size': len(pack)}})
    # Native bridge is compiled only for the test APK. No engine pack publication or app ABI change.
    gradle = root / 'libretrodroid/build.gradle'
    original = gradle.read_text()
    needle = "abiFilters 'arm64-v8a', 'armeabi-v7a'"
    assert needle in original, 'native ABI declaration changed'
    gradle.write_text(original.replace(needle, "abiFilters 'x86_64'"))
    app = root / 'app/build.gradle.kts'
    original = app.read_text()
    needle = 'excludes += listOf("lib/x86/**", "lib/x86_64/**")'
    assert needle in original, 'packaging declaration changed'
    app.write_text(original.replace(needle, '// V106 generated debug build includes its x86_64 test bridge'))
    debug = root / 'app/src/debug'
    (debug / 'assets').mkdir(parents=True, exist_ok=True)
    (debug / 'assets/monika-config.json').write_text(json.dumps(cfg, ensure_ascii=False))
    (debug / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
<application android:usesCleartextTraffic="true" tools:replace="android:usesCleartextTraffic" /></manifest>''')
    print('V106 original NDS ROM + verified x86_64 core; production config/ABI unchanged in git')


def serve(out):
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(out / 'web'), **kwargs)
        def do_GET(self):
            # Leave a deterministic loading window for Maestro taps, not a fake native readiness signal.
            time.sleep(40)
            super().do_GET()
        def log_request(self, code='-', size='-'):
            with (out / 'requests.jsonl').open('a') as log:
                log.write(json.dumps({'path': self.path, 'status': code}) + '\n')
    http.server.ThreadingHTTPServer(('127.0.0.1', 18444), Handler).serve_forever()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'serve'])
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if args.action == 'prepare':
        prepare(args.out.resolve())
    else:
        serve(args.out.resolve())
