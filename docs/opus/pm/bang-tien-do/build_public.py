#!/usr/bin/env python3
"""Sinh bản công khai của bảng tiến độ từ index.html + state.json.
- public.html: bản cho Artifact (không có khung <html>).
- <repo>/docs/index.html: trang GitHub Pages đầy đủ (nhánh docs/opus-tra-loi, thư mục /docs).
"""
import json, os, sys
d = os.path.dirname(os.path.abspath(__file__))
repo = sys.argv[1] if len(sys.argv) > 1 else '/home/user/aowvn-monika'
tpl = open(os.path.join(d, 'index.html'), encoding='utf-8').read()
state = json.load(open(os.path.join(d, 'state.json'), encoding='utf-8'))
data = json.dumps(state, ensure_ascii=False).replace('<', '\\u003c').replace('>', '\\u003e').replace('&', '\\u0026')
page = tpl.replace('<title>Bảng tiến độ Monika</title>', '<title>Tiến độ Aow Monika</title>', 1)
page = page.replace('\n<script>\n', '\n<script type="application/json" id="embedded-state">' + data + '</script>\n<script>\n', 1)
assert 'embedded-state' in page and 'Tiến độ Aow Monika' in page
open(os.path.join(d, 'public.html'), 'w', encoding='utf-8').write(page)
print('public.html', len(page))  # docs/index.html giờ là landing page tải app, không ghi đè nữa
