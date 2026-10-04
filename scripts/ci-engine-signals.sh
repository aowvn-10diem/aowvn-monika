#!/usr/bin/env bash
set -euo pipefail
SCRATCH="${1:?fixture}";OUT="${2:?evidence}";PKG=com.aow.monika
mkdir -p "$OUT"
trap 'adb logcat -d -v threadtime > "$OUT/final-logcat.txt"; adb exec-out screencap -p > "$OUT/final.png"' EXIT
adb root >/dev/null;adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk >/dev/null
UID_APP=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
BASE="/data/data/$PKG/files"
for item in 'rgss-pack packs/rgss' 'kirikiri-pack packs/kirikiri' 'game games/v22-xp' 'k3 games/v22-k3'; do
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
adb shell am start -W -f 0x10008000 -n "$PKG/vn.aow.monika.runner.KirikiriGameActivity" --es title V22-K3 --es aow_game_path "$BASE/games/v22-k3/startup.tjs" >/dev/null
sleep 45
adb exec-out screencap -p > "$OUT/k3.png"
adb exec-out screencap > "$OUT/k3.raw"
adb shell run-as "$PKG" cat files/games/v22-k3/monika-ready.txt > "$OUT/k3-ready.txt" || true
adb shell run-as "$PKG" sh -c "'for f in files/diag/reports/*.json; do cat \"\$f\"; echo; done'" > "$OUT/k3-reports.jsonl"
adb shell run-as "$PKG" sh -c "'cat files/diag/crumbs/*.log'" > "$OUT/k3-crumbs.txt" || true
adb logcat -d -s MonikaGame AndroidRuntime DEBUG > "$OUT/k3-logcat.txt"
python3 - "$OUT" <<'PY'
import json,sys,struct,pathlib
p=pathlib.Path(sys.argv[1]);assert (p/'k3-ready.txt').read_text().strip(),'K3 chưa chạy script'
r=[json.loads(l) for l in (p/'k3-reports.jsonl').read_text().splitlines() if l.strip().startswith('{')]
assert not any(x['component']=='engine:kirikiri' and x['reason']=='màn đen' for x in r),'K3 báo màn đen giả'
d=(p/'k3.raw').read_bytes();w,h=struct.unpack('<II',d[:8]);hdr=len(d)-w*h*4
assert 12<=hdr<=64;idx=hdr+((h//2)*w+w//2)*4
rgb=d[idx:idx+3];assert all(abs(c-e)<=40 for c,e in zip(rgb,[242,140,40])),f'K3 chưa có frame cam: {list(rgb)}'
assert 'kirikiri: black=false grid=64x36' in (p/'k3-crumbs.txt').read_text(),'K3 chưa có PixelCopy thành công'
print('PASS XP engine:rgss monika-ci; K3 cam 45s không báo màn đen')
PY
