#!/usr/bin/env bash
set -euo pipefail
FIXTURE="${1:?fixture}"; OUT="${2:?evidence}"; PKG=com.aow.monika
BASE="/data/data/$PKG/files"; LOGCAT_PID=
mkdir -p "$OUT"
cleanup() {
  if [[ -n "$LOGCAT_PID" ]]; then kill "$LOGCAT_PID" 2>/dev/null || true; wait "$LOGCAT_PID" 2>/dev/null || true; fi
  adb exec-out screencap -p > "$OUT/final.png" || true
  adb shell dumpsys activity exit-info "$PKG" > "$OUT/exit-info.txt" || true
}
trap cleanup EXIT
adb root >/dev/null; adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk >/dev/null
adb reverse tcp:18444 tcp:18444
APP_UID=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
adb push "$FIXTURE/monika.nds" /data/local/tmp/monika-v106.nds >/dev/null
adb shell "mkdir -p $BASE/ci; cp /data/local/tmp/monika-v106.nds $BASE/ci/monika.nds; chown -R $APP_UID:$APP_UID $BASE/ci; restorecon -R $BASE/ci"
adb logcat -c
adb logcat -v threadtime > "$OUT/logcat.txt" & LOGCAT_PID=$!
launch() {
  adb shell am start -W -n "$PKG/vn.aow.monika.runner.RetroActivity" \
    --es core melondsds --es game "$BASE/ci/monika.nds" --es system 'Nintendo DS' \
    --es system_id nds --es title 'CI NDS V106' > "$OUT/start-$1.txt"
}
wait_frame() {
  for tick in $(seq 1 90); do
    if adb logcat -d | grep -q 'MonikaGame: loading-dismissed core=melondsds'; then return 0; fi
    if adb logcat -d | grep -qE 'Fatal signal|FATAL EXCEPTION|MonikaGame: (error|no-first-frame)'; then return 1; fi
    sleep 2
  done
  return 1
}
launch first
maestro test .maestro-nds/loading.yaml --test-output-dir "$OUT/loading-ui"
wait_frame
adb exec-out screencap -p > "$OUT/first-frame.png"
# Native ROM must draw orange pixels, not merely return a black emulator frame.
python3 - "$OUT/first-frame.png" <<'PY'
from PIL import Image
import sys
pixels=Image.open(sys.argv[1]).convert('RGB').getdata()
assert sum(r > 220 and 80 < g < 170 and b < 45 for r,g,b in pixels) > 3000, 'original NDS framebuffer not rendered'
PY
adb shell stat -c '%Y %s' "$BASE/cores/melondsds/melondsds_libretro_android.so" > "$OUT/core-before.txt"
maestro test .maestro-nds/exit.yaml --test-output-dir "$OUT/exit-ui"
for tick in $(seq 1 15); do
  if ! adb shell pidof "$PKG:game" >/dev/null; then break; fi
  sleep 1
done
if adb shell pidof "$PKG:game" >/dev/null; then echo 'FAIL: menu exit did not end game process' >&2; exit 1; fi
adb logcat -c
launch second
wait_frame
adb exec-out screencap -p > "$OUT/second-frame.png"
adb shell stat -c '%Y %s' "$BASE/cores/melondsds/melondsds_libretro_android.so" > "$OUT/core-after.txt"
cmp "$OUT/core-before.txt" "$OUT/core-after.txt"
python3 - "$FIXTURE/requests.jsonl" "$OUT" <<'PY'
import json,pathlib,sys
requests=[json.loads(line) for line in pathlib.Path(sys.argv[1]).read_text().splitlines()]
assert len(requests)==1 and requests[0]=={'path':'/melondsds.zip','status':200}, requests
pathlib.Path(sys.argv[2],'summary.txt').write_text('PASS NDS: loading taps, original framebuffer, menu exit, reentry, core fetched once\n3DS: BLOCKED — ARM64-only engine, awaiting PM route\n')
PY
if grep -qE 'Fatal signal|FATAL EXCEPTION|ANR in com[.]aow[.]monika' "$OUT/logcat.txt"; then echo 'FAIL: crash/ANR' >&2; exit 1; fi
cat "$OUT/summary.txt"
