#!/usr/bin/env python3
"""Chặn lỗi IncompatibleClassChangeError kiểu v0.4.5: thư viện đóng kèm bản sao lớp CỦA ANDROID rồi R8 đổi tên chúng.
Đọc mapping.txt của R8; báo lỗi nếu lớp hệ thống (android.*, java.*, javax.*, org.xmlpull, org.w3c, org.xml, org.json, dalvik.*)
bị ĐỔI TÊN (vế trái khác vế phải). Bỏ qua: `android.support.*` (androidx tự đặt tên) và `javax.microedition.*` (J2ME Loader nhúng sẵn, lớp của chính app).
Dùng: python3 scripts/check-r8-mapping.py app/build/outputs/mapping/release/mapping.txt
"""
import re
import sys

PLATFORM = re.compile(r'^(android\.(?!support\.)|java\.|javax\.(?!microedition)|org\.xmlpull\.|org\.w3c\.|org\.xml\.|org\.json\.|dalvik\.)')

def main(path):
    bad = []
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            if line.startswith((' ', '#')) or ' -> ' not in line:
                continue
            src, dst = line.rstrip('\n').split(' -> ', 1)
            dst = dst.rstrip(':')
            if PLATFORM.match(src) and dst != src and 'REMOVED' not in dst:
                bad.append(f'{src} -> {dst}')
    if bad:
        print('LỖI: lớp của Android bị R8 đổi tên (thư viện nào đó đóng kèm bản sao). Lọc khỏi jar như arscStripped trong app/build.gradle.kts:')
        for b in bad[:30]:
            print('  ', b)
        sys.exit(1)
    print('OK: không có lớp Android nào bị R8 đổi tên.')

if __name__ == '__main__':
    main(sys.argv[1])
