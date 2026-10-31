#!/bin/bash
# Trích "bảng sự thật" từ mã (kèm file:dòng) để đưa cho DS đối chiếu khi đồng bộ tài liệu. Dùng: trich-su-that.sh [ref=origin/main] > su-that.md
ref=${1:-origin/main}
echo "# BẢNG SỰ THẬT ($ref $(git rev-parse --short "$ref"), trích bằng lệnh, kèm file:dòng)"
echo "## app/build.gradle.kts"; git show "$ref:app/build.gradle.kts" | grep -n -E 'versionCode|versionName|minSdk|targetSdk|compileSdk|ndkVersion' | sed 's/^/app\/build.gradle.kts:/'
echo "## config/monika-config.json"
git show "$ref:config/monika-config.json" | grep -n '"configVersion"' | sed 's/^/config\/monika-config.json:/'
git show "$ref:config/monika-config.json" | python3 -I -c "
import sys,json,re
txt=sys.stdin.read(); c=json.loads(txt); L=txt.split('\n')
ln=lambda pat:next((i+1 for i,l in enumerate(L) if re.search(pat,l)),'?')
print('app:',json.dumps(c.get('app'),ensure_ascii=False)[:400])
print('modules (dòng',ln(r'\"modules\"'),'):',json.dumps({k:{kk:vv for kk,vv in v.items() if kk in ('version','abis')} for k,v in c['modules'].items()},ensure_ascii=False))
print('Hệ máy (id | runner | lõi/engine | dòng):')
for s in c['systems']: print(' -',s.get('id'),'|',s.get('runner'),'|',s.get('core') or s.get('engine') or s.get('player') or '','|',ln(r'\"id\":\s*\"%s\"'%re.escape(s['id'])))
"
echo "## settings.gradle.kts"; git show "$ref:settings.gradle.kts" | grep -n include | sed 's/^/settings.gradle.kts:/'
echo "## Tag mới nhất"; git tag --sort=-v:refname | head -6 | tr '\n' ' '; echo
echo "## Workflow"; git ls-tree --name-only "$ref" .github/workflows/ | sed 's#.github/workflows/##' | tr '\n' ' '; echo
echo "## scripts/"; git ls-tree --name-only "$ref" scripts/ | sed 's#scripts/##' | tr '\n' ' '; echo
echo "## Package trong app/src/main/java/vn/aow/monika"; git ls-tree -d --name-only "$ref" app/src/main/java/vn/aow/monika/ | sed 's#.*/##' | tr '\n' ' '; echo
