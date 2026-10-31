#!/bin/bash
# Quét cú pháp Kotlin bằng quy tắc (không dùng DS, chắc chắn). Dùng: kt-lint.sh tệp.kt ...
# Bắt mẫu chuỗi "$tên" mà `tên` không xuất hiện ở bất kỳ đâu NGOÀI chuỗi trong tệp (nên chắc chắn chưa khai báo),
# ví dụ "cheat$i_code" bị hiểu là biến i_code (phải viết "cheat${i}_code"). Thoát mã 1 nếu có nghi vấn.
# Không kiểm ngoặc/kiểu: các lỗi đó để CI biên dịch bắt.
python3 -I - "$@" <<'PY'
import re,sys
bad=0
IMPLICIT={"it","this","packageName"}  # tham số ngầm của lambda và thuộc tính kế thừa từ Context
STR=re.compile(r'"""(.*?)"""|"((?:[^"\\\n]|\\.)*)"',re.S)
for p in sys.argv[1:]:
    s=open(p,encoding="utf-8").read()
    s=re.sub(r"/\*.*?\*/","",s,flags=re.S); s=re.sub(r"(?m)(?<![:\"])//.*$","",s)
    templates=[]
    for m in STR.finditer(s):
        body=m.group(1) if m.group(1) is not None else m.group(2)
        line=s.count("\n",0,m.start())+1
        for t in re.finditer(r"(?<!\\)\$([A-Za-z_]\w*)",body): templates.append((line,t.group(1)))
    outside=STR.sub('""',s)
    # tên xuất hiện trong ${...} cũng tính là dùng, nhưng không tính là khai báo; khai báo chỉ ở phần ngoài chuỗi
    tokens=set(re.findall(r"\b[A-Za-z_]\w*\b",outside))|IMPLICIT
    for line,ident in templates:
        if ident not in tokens:
            bad+=1; print(f"{p}:{line}: mẫu chuỗi ${ident} chưa khai báo (ý là ${{{ident.split('_')[0]}}}_...?)")
sys.exit(1 if bad else 0)
PY
