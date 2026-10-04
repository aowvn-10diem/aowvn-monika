#!/usr/bin/env bash
set -euo pipefail
SCRATCH="${1:?fixture}"; OUT="${2:?evidence}"; PKG=com.aow.monika
mkdir -p "$OUT"
trap 'adb logcat -d -v threadtime > "$OUT/logcat.txt"; adb exec-out screencap -p > "$OUT/final.png"' EXIT
adb root >/dev/null; adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk >/dev/null
UID_APP=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
BASE="/data/data/$PKG/files"
python3 - "$SCRATCH/web/rgss.zip" "$SCRATCH/pack" <<'PY'
import zipfile,sys
with zipfile.ZipFile(sys.argv[1]) as z:z.extractall(sys.argv[2])
PY
for item in 'pack packs/rgss' 'game games/user-report'; do
 read -r source target <<< "$item"
 adb push "$SCRATCH/$source" "/data/local/tmp/report-$source" >/dev/null
 adb shell "mkdir -p $BASE/$(dirname "$target"); cp -r /data/local/tmp/report-$source $BASE/$target; chown -R $UID_APP:$UID_APP $BASE; restorecon -R $BASE"
done
adb shell am start -W -f 0x10008000 -n "$PKG/vn.aow.monika.runner.RgssGameActivity" --es title Monika-User-Report --es game_path "$BASE/games/user-report" >/dev/null
sleep 25
maestro test .maestro-user-report/report.yaml --test-output-dir "$OUT/ui"
adb shell run-as "$PKG" sh -c "'for f in files/diag/reports/*.json; do cat \"\$f\"; echo; done'" > "$OUT/reports.jsonl"
python3 - "$OUT/reports.jsonl" <<'PY'
import base64,json,sys
reports=[json.loads(l) for l in open(sys.argv[1]) if l.strip().startswith('{')]
r=next(r for r in reports if r['kind']=='user')
assert r['reason']=='Không có tiếng'
assert r['session']['kind']=='rgss' and r['env'] and r['fromGame']
i=r['image'];raw=base64.b64decode(i['data'],validate=True)
assert i['mime']=='image/jpeg' and 0<max(i['width'],i['height'])<=480 and len(raw)<=24576
assert raw[:2]==b'\xff\xd8' and raw[-2:]==b'\xff\xd9'
assert len(json.dumps(r,ensure_ascii=False,separators=(',',':')).encode())<=60000
print('PASS UI menu → user report JPEG + session/env; không deploy/gửi Worker thật')
PY
