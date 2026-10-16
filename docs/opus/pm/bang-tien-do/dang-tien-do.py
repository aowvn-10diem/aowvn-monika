#!/usr/bin/env python3
"""Dựng trang công khai tien-do/index.html cho trang web monika.aow.vn từ bảng tiến độ của PM.

Dùng:  python3 dang-tien-do.py <thư-mục-gốc-nhánh-trang-web>
Ví dụ: python3 docs/opus/pm/bang-tien-do/dang-tien-do.py /đường/dẫn/checkout-nhánh-trang-web

Làm gì: chạy build_public.py (sinh public.html từ index.html + state.json), bọc khung HTML đầy đủ
(giao diện tối, meta SEO, liên kết về trang chủ) rồi ghi <gốc>/tien-do/index.html.
Sau đó: commit vào nhánh `trang-web`, chờ Pages dựng xong rồi xóa cache Cloudflare cho /tien-do/.
"""
import os, subprocess, sys

d = os.path.dirname(os.path.abspath(__file__))
if len(sys.argv) != 2:
    sys.exit(__doc__)
out_root = sys.argv[1]
subprocess.run([sys.executable, os.path.join(d, 'build_public.py'), d], check=True, stdout=subprocess.DEVNULL)
frag = open(os.path.join(d, 'public.html'), encoding='utf-8').read()
i = frag.index('</style>') + len('</style>')
head_part, body_part = frag[:i].replace('<title>Tiến độ Aow Monika</title>\n', '', 1), frag[i:]
S = 'https://monika.aow.vn'
T = 'Tiến độ phát triển AowVN Monika'
D = 'Bảng tiến độ phát triển AowVN Monika: các mốc, việc đang làm, cổng phát hành và nhật ký của đội. Cập nhật theo từng đợt của người quản lý dự án.'
page = f'''<!doctype html>
<html lang="vi" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>{T}</title>
<meta name="description" content="{D}">
<link rel="canonical" href="{S}/tien-do/">
<meta name="robots" content="index,follow">
<meta name="theme-color" content="#161412">
<meta name="color-scheme" content="dark">
<meta property="og:type" content="website">
<meta property="og:site_name" content="AowVN Monika">
<meta property="og:locale" content="vi_VN">
<meta property="og:url" content="{S}/tien-do/">
<meta property="og:title" content="{T}">
<meta property="og:description" content="{D}">
<meta property="og:image" content="{S}/assets/og-monika.jpg">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" type="image/png" href="/assets/favicon.png">
{head_part}
</head>
<body>
<p style="margin:0;padding:14px 18px;font:700 14px/1.4 var(--font);display:flex;gap:16px;flex-wrap:wrap"><a href="/" style="color:var(--fg);text-decoration:none">← Trang chủ Monika</a><a href="/lich-su.html" style="color:var(--muted);text-decoration:none">Lịch sử cập nhật</a></p>
{body_part}
</body>
</html>
'''
os.makedirs(os.path.join(out_root, 'tien-do'), exist_ok=True)
open(os.path.join(out_root, 'tien-do', 'index.html'), 'w', encoding='utf-8').write(page)
print('đã ghi', os.path.join(out_root, 'tien-do', 'index.html'), len(page), 'ký tự')
