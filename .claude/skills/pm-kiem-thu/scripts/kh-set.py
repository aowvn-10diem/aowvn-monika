#!/usr/bin/env python3
"""Sửa một ô của một dòng việc trong bảng KE-HOACH (mặc định: ô Trạng thái = cột cuối).

Dùng:
  kh-set.py V46 "đang làm (PR #51)"                 # ghi KE-HOACH.md thật
  kh-set.py V46 "xong (PR #51)" --dry-run           # chỉ in dòng trước/sau
  kh-set.py V46 "..." --col 5 --file path/KE-HOACH.md   # cột 5 = "Cách kiểm" (đếm từ 1, không tính ô rỗng đầu/cuối)

Chỉ sửa đúng 1 dòng bắt đầu bằng "| <mã> |"; 0 hoặc >1 dòng khớp thì dừng, không ghi.
"""
import argparse, re, sys

CELL = re.compile(r'(?<!\\)\|')  # tách ô theo '|' không có '\' đứng trước


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('code', help='mã việc, ví dụ V46')
    ap.add_argument('value', help='nội dung mới của ô')
    ap.add_argument('--col', type=int, default=0, help='số thứ tự cột (từ 1); mặc định cột cuối')
    ap.add_argument('--file', default='docs/opus/KE-HOACH.md')
    ap.add_argument('--dry-run', action='store_true')
    a = ap.parse_args()

    lines = open(a.file, encoding='utf-8').read().split('\n')
    hits = [i for i, l in enumerate(lines) if re.match(r'\|\s*' + re.escape(a.code) + r'\s*\|', l)]
    if len(hits) != 1:
        sys.exit(f'LỖI: tìm thấy {len(hits)} dòng cho mã {a.code} trong {a.file}; không ghi gì.')
    i = hits[0]
    parts = CELL.split(lines[i])  # ['', ' V46 ', ..., ' trạng thái ', '']
    if parts[-1].strip() != '' or parts[0].strip() != '':
        sys.exit('LỖI: dòng không đúng dạng bảng markdown | a | b |')
    cells = parts[1:-1]
    col = a.col or len(cells)
    if not 1 <= col <= len(cells):
        sys.exit(f'LỖI: cột {col} ngoài phạm vi 1..{len(cells)}')
    if '|' in a.value.replace('\\|', ''):
        sys.exit("LỖI: giá trị chứa '|' chưa thoát; dùng '\\|'")
    old = lines[i]
    cells[col - 1] = ' ' + a.value.strip() + ' '
    lines[i] = '|' + '|'.join(cells) + '|'
    print('- ' + old[:300])
    print('+ ' + lines[i][:300])
    if a.dry_run:
        print('(dry-run: chưa ghi)')
        return
    open(a.file, 'w', encoding='utf-8').write('\n'.join(lines))
    print('Đã ghi', a.file)


main()
