#!/usr/bin/env python3
"""V33: fixtures XP tự sinh, server HTTPS cục bộ; không ROM/game mạng hay publish."""
import argparse
import hashlib
import http.server
import json
from pathlib import Path
import shutil
import ssl
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def prepare(out, tag):
    # Chỉ đọc release nháp/gói engine, không tải game. Không in credential.
    releases = json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp',
        'repos/aowvn-10diem/aowvn-monika/releases?per_page=100']))
    matches = [r for page in releases for r in page if r['tag_name'] == tag and r['draft']]
    if len(matches) != 1:
        raise RuntimeError('Cần đúng một release RGSS nháp')
    assets = [a for a in matches[0]['assets'] if a['name'] == 'rgss-arm64-v8a.zip']
    if len(assets) != 1:
        raise RuntimeError('Thiếu gói RGSS arm64')
    web = out / 'web'; web.mkdir(parents=True, exist_ok=True)
    pack = web / 'rgss.zip'
    with pack.open('wb') as target:
        subprocess.run(['gh', 'api', f"repos/aowvn-10diem/aowvn-monika/releases/assets/{assets[0]['id']}",
            '-H', 'Accept: application/octet-stream'], stdout=target, check=True)
    with zipfile.ZipFile(pack) as z:
        assert 'lib/libmkxp-z.so' in z.namelist(), 'Gói sai cây thư mục'
        assert z.testzip() is None, 'ZIP hỏng'
    cfg = json.loads((ROOT / 'config/monika-config.json').read_text())
    cfg['configVersion'] = 2147483647  # Chỉ APK test, không áp config từ mạng giữa bài.
    cfg['prefetchCores'] = []; cfg['prefetchByExtension'] = {}
    cfg['crash'] = {'autoSend': False, 'endpoint': ''}
    cfg['modules']['rgss'] = dict(version='ci-V33', url='https://localhost:18443/rgss.zip',
        abis=['arm64-v8a'], sha256=hashlib.sha256(pack.read_bytes()).hexdigest(), size=pack.stat().st_size)
    for system in cfg['systems']:
        if system['id'] == 'rgss':
            system['engine'] = 'rgss'; system['allowExternalApp'] = False
    debug = ROOT / 'app/src/debug'
    (debug / 'assets').mkdir(parents=True, exist_ok=True)
    (debug / 'assets/monika-config.json').write_text(json.dumps(cfg, ensure_ascii=False))
    tls = out / 'tls'; tls.mkdir(exist_ok=True)
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
        '-keyout', str(tls / 'key.pem'), '-out', str(tls / 'ca.pem'), '-subj', '/CN=localhost',
        '-addext', 'subjectAltName=DNS:localhost'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    (debug / 'res/raw').mkdir(parents=True, exist_ok=True)
    shutil.copyfile(tls / 'ca.pem', debug / 'res/raw/ci_ca.pem')
    (debug / 'res/xml').mkdir(parents=True, exist_ok=True)
    (debug / 'res/xml/ci_network.xml').write_text('''<network-security-config>
<base-config><trust-anchors><certificates src="system" /></trust-anchors></base-config>
<domain-config><domain>localhost</domain><trust-anchors><certificates src="@raw/ci_ca" /></trust-anchors></domain-config>
</network-security-config>''')
    (debug / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<application android:networkSecurityConfig="@xml/ci_network" /></manifest>''')
    game = out / 'game/Data'; game.mkdir(parents=True, exist_ok=True)
    code = '''File.write("monika-user-open.txt", "V33")
loop do
  Graphics.update
  Input.update
  if Input.trigger?(Input::C)
    File.write("monika-user-key.txt", "V33")
  end
end
'''
    subprocess.run(['ruby', '-rzlib', '-e',
        'File.binwrite(ARGV[0], Marshal.dump([[1,"Main",Zlib::Deflate.deflate(STDIN.read)]]))',
        str(game / 'Scripts.rxdata')], input=code.encode(), check=True)
    with zipfile.ZipFile(out / 'Monika-V33.zip', 'w') as z:
        z.writestr('Game.ini', '[Game]\r\nTitle=Monika-V33\r\nScripts=Data\\Scripts.rxdata\r\nRTP1=\r\n')
        z.write(game / 'Scripts.rxdata', 'Data/Scripts.rxdata')
    (out / 'fixture.json').write_text(json.dumps({'tag': tag, 'asset': assets[0]['id'], 'sha256': cfg['modules']['rgss']['sha256']}))

def serve(out):
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(out / 'web'), **kwargs)
        def log_request(self, code='-', size='-'):
            with (out / 'requests.jsonl').open('a') as log:
                log.write(json.dumps({'path': self.path, 'status': code})+'\n')
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 18443), Handler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(out / 'tls/ca.pem', out / 'tls/key.pem')
    server.socket = ctx.wrap_socket(server.socket, server_side=True)
    server.serve_forever()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'serve'])
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--rgss-tag', default='')
    args = parser.parse_args()
    if args.action == 'prepare': prepare(args.out.resolve(), args.rgss_tag)
    else: serve(args.out.resolve())
