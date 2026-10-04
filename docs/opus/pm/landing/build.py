#!/usr/bin/env python3
"""landing-body.html -> site/index.html (trang đầy đủ cho GitHub Pages) + chép assets."""
import os, shutil
d = os.path.dirname(os.path.abspath(__file__))
body = open(os.path.join(d, 'landing-body.html'), encoding='utf-8').read()
cut = body.index('<header class="top"')
head, rest = body[:cut], body[cut:]
full = ('<!doctype html>\n<html lang="vi">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n'
        + head + '</head>\n<body>\n' + rest + '\n</body>\n</html>\n')
site = os.path.join(d, 'site'); os.makedirs(site, exist_ok=True)
open(os.path.join(site, 'index.html'), 'w', encoding='utf-8').write(full)
shutil.copytree(os.path.join(d, 'assets'), os.path.join(site, 'assets'), dirs_exist_ok=True)
print('site/index.html', len(full))
