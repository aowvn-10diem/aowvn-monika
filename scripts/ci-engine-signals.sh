#!/usr/bin/env bash
set -euo pipefail
SCRATCH="${1:?fixture}";OUT="${2:?evidence}";PKG=com.aow.monika
mkdir -p "$OUT"
trap 'adb logcat -d -v threadtime > "$OUT/final-logcat.txt"; adb exec-out screencap -p > "$OUT/final.png"' EXIT
adb root >/dev/null;adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk >/dev/null
UID_APP=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
BASE="/data/data/$PKG/files"
for item in 'rgss-pack packs/rgss' 'game games/v22-xp' 'control games/v22-control'; do
  read -r source target <<< "$item"
  adb push "$SCRATCH/$source" "/data/local/tmp/v22-$source" >/dev/null
  adb shell "mkdir -p $BASE/$(dirname "$target"); cp -r /data/local/tmp/v22-$source $BASE/$target; chown -R $UID_APP:$UID_APP $BASE/$target; chmod -R 777 $BASE/$target; restorecon -R $BASE/$target"
done
adb logcat -c
adb shell am start -W -f 0x10008000 -n "$PKG/vn.aow.monika.runner.RgssGameActivity" --es title V22-XP --es game_path "$BASE/games/v22-xp" >/dev/null
sleep 20
adb exec-out screencap -p > "$OUT/xp-error.png"
adb shell input keyevent KEYCODE_ENTER
sleep 5
adb shell run-as "$PKG" sh -c "'for f in files/diag/reports/*.json; do cat \"\$f\"; echo; done'" > "$OUT/xp-reports.jsonl"
adb logcat -d -s SDL MonikaGame AndroidRuntime > "$OUT/xp-logcat.txt"
python3 - "$OUT/xp-reports.jsonl" <<'PY'
import json,sys
r=[json.loads(l) for l in open(sys.argv[1]) if l.strip().startswith('{')]
assert any(x['component']=='engine:rgss' and 'monika-ci' in x['detail'] for x in r),'XP raise chưa thành báo cáo engine:rgss'
PY
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -f 0x10008000 -n "$PKG/vn.aow.monika.runner.RgssGameActivity" --es title V22-RGSS-Control --es game_path "$BASE/games/v22-control" >/dev/null
sleep 45
adb exec-out screencap -p > "$OUT/control.png"
adb exec-out screencap > "$OUT/control.raw"
adb shell run-as "$PKG" cat files/games/v22-control/monika-ready.txt > "$OUT/control-ready.txt" || true
adb shell run-as "$PKG" sh -c "'for f in files/diag/reports/*.json; do cat \"\$f\"; echo; done'" > "$OUT/control-reports.jsonl"
adb shell run-as "$PKG" sh -c "'cat files/diag/crumbs/*.log'" > "$OUT/control-crumbs.txt" || true
adb logcat -d -s MonikaGame AndroidRuntime DEBUG mkxp > "$OUT/control-logcat.txt"
python3 - "$OUT" <<'PY'
import json,sys,struct,pathlib
p=pathlib.Path(sys.argv[1]);assert (p/'control-ready.txt').read_text().strip()=='RGSS-control-ruby18','VX control chưa chạy script qua preload'
assert 'MonikaRuby18 normalized scripts=1' in (p/'control-logcat.txt').read_text(),'Thiếu bằng chứng normalize trong mkxp-z thật'
r=[json.loads(l) for l in (p/'control-reports.jsonl').read_text().splitlines() if l.strip().startswith('{')]
assert not any(x['component']=='engine:rgss' and x['reason']=='màn đen' for x in r),'RGSS control báo màn đen giả'
d=(p/'control.raw').read_bytes();w,h=struct.unpack('<II',d[:8]);hdr=len(d)-w*h*4
assert 12<=hdr<=64;idx=hdr+((h//2)*w+w//2)*4
rgb=d[idx:idx+3];assert all(abs(c-e)<=40 for c,e in zip(rgb,[242,140,40])),f'RGSS control chưa có frame cam: {list(rgb)}'
assert 'rgss: black=false grid=64x36' in (p/'control-crumbs.txt').read_text(),'RGSS control chưa có PixelCopy thành công'
print('PASS XP engine:rgss monika-ci; RGSS control cam 45s không báo màn đen')
PY
