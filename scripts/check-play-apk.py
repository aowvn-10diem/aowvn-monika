#!/usr/bin/env python3
"""V150: kiểm artifact Play trước R8, không dựa vào việc ẩn nút hoặc loại mã chết."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

FORBIDDEN_CLASSES = (
    b'Lvn/aow/monika/official/', b'Lvn/aow/monika/config/OfficialSource',
    b'Lvn/aow/monika/donate/', b'Lvn/aow/monika/download/', b'Lvn/aow/monika/apkinstall/',
    b'Lvn/aow/monika/browser/InAppBrowserActivity', b'Lvn/aow/monika/ui/screens/SupportStrip',
    b'Lvn/aow/monika/config/Donate',
    b'Lvn/aow/monika/ui/ForceUpdateScreenKt',
    # [A65] Thẻ tài khoản chỉ thuộc bản GitHub; bản Play không đóng gói.
    b'Lvn/aow/monika/ui/screens/AccountCardKt',
)
# [A95] Anti-steering: bản Play không có câu mời trả tiền ngoài cửa hàng (chữ có dấu, tên khoá và tiếng Anh).
# Thêm chuỗi mới thì thêm vào đây — test offline tự quét lại từng mục.
FORBIDDEN_TEXT = (
    'img.vietqr.io', 'vote-game.html', 'Tải bản mới', 'monika-loader.dex',
    'ủng hộ', 'Ủng hộ', 'chuyển khoản', 'Chuyển khoản',
    'vietqr', 'VietQR', 'donate', 'Donate', 'DONATE',
)
FORBIDDEN_PERMISSIONS = (
    'REQUEST_INSTALL_PACKAGES', 'MANAGE_EXTERNAL_STORAGE', 'QUERY_ALL_PACKAGES',
    # [A65] :j2me kéo micro, vị trí, Bluetooth và shortcut vào manifest chung — bản Play gỡ hết.
    'RECORD_AUDIO', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'BLUETOOTH', 'INSTALL_SHORTCUT',
)
REQUIRED_ASSETS = (
    'assets/licenses/j2me-loader.txt', 'assets/licenses/fluent-system-icons.txt',
    'assets/rgss/monika-win32api.rb', 'assets/rgss/monika-ruby18.rb',
)


def decoded_manifest(apk, raw):
    if raw.lstrip().startswith(b'<'):
        return raw.decode('utf-8')
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    candidates = sorted(Path(sdk, 'build-tools').glob('*/aapt2')) if sdk else []
    aapt = str(candidates[-1]) if candidates else shutil.which('aapt2')
    if not aapt:
        raise ValueError('Cần aapt2 để kiểm manifest nhị phân')
    return subprocess.check_output([aapt, 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)


def check(apk):
    with zipfile.ZipFile(apk) as archive:
        if any(name.endswith('/donate_qr.webp') for name in archive.namelist()):
            raise ValueError('Play còn ảnh QR ủng hộ')
        for name in REQUIRED_ASSETS:
            if name not in archive.namelist() or not archive.read(name).strip():
                raise ValueError('Play thiếu ghi công hoặc script tương thích: ' + name)
        manifest = decoded_manifest(apk, archive.read('AndroidManifest.xml'))
        if 'com.aow.monika.play' not in manifest:
            raise ValueError('Sai applicationId bản Play')
        if any(p in manifest for p in FORBIDDEN_PERMISSIONS) or '<queries' in manifest or 'E: queries ' in manifest:
            raise ValueError('Manifest Play còn quyền hoặc queries bị cấm')
        dex_files = [n for n in archive.namelist() if n.startswith('classes') and n.endswith('.dex')]
        if not dex_files:
            raise ValueError('APK không có DEX')
        for name in dex_files:
            data = archive.read(name)
            if any(marker in data for marker in FORBIDDEN_CLASSES):
                raise ValueError('DEX Play còn lớp phân phối ngoài hoặc ủng hộ')
        for name in archive.namelist():
            if name.endswith('monika-loader.dex'):
                raise ValueError('Play còn bộ nạp APK game')
            if name.endswith('.dex') or name.startswith(('assets/', 'res/')) or name == 'resources.arsc':
                data = archive.read(name)
                if any(value.encode(enc) in data for value in FORBIDDEN_TEXT for enc in ('utf-8', 'utf-16le')):
                    raise ValueError('Artifact Play còn chuỗi phân phối ngoài hoặc ủng hộ')
        cfg = json.loads(archive.read('assets/monika-config.json'))
        if cfg.get('official'):
            raise ValueError('Catalog Play còn nơi tải APK ngoài và dữ liệu xác nhận ủng hộ')
        if cfg.get('app', {}).get('apkUrl') or cfg.get('externalApps') or cfg.get('downloadHosts'):
            raise ValueError('Catalog Play còn đường phân phối ngoài')
        if any(k in cfg.get('account', {}) for k in ('donate', 'donateUrl', 'voteUrl')):
            raise ValueError('Catalog Play còn dữ liệu ủng hộ')
        account = cfg.get('account', {})
        if account.get('enabled') or any(account.get(k) for k in ('apiKey', 'databaseUrl', 'loginUrl')):
            raise ValueError('Catalog Play còn bật đăng nhập hoặc URL aow.vn')
        if (cfg.get('feed') or {}).get('url') or (cfg.get('feed') or {}).get('enabled'):
            raise ValueError('Catalog Play còn nguồn bài viết ngoài')
        if (cfg.get('forum') or {}).get('baseUrl') or (cfg.get('forum') or {}).get('enabled'):
            raise ValueError('Catalog Play còn diễn đàn ngoài')
        if (cfg.get('crash') or {}).get('autoSend'):
            raise ValueError('Catalog Play còn tự gửi báo lỗi mặc định')
        if any(c.get('url') or c.get('artifacts') or c.get('systemFiles') for c in cfg.get('cores', {}).values()):
            raise ValueError('Catalog Play còn link lõi ngoài Play')
        if any(m.get('url') for m in cfg.get('modules', {}).values()):
            raise ValueError('Catalog Play còn link engine ngoài Play')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    check(args.apk)
    print('Play APK: mã, manifest và catalog đạt kiểm tra V150')
