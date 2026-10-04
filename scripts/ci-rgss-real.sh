#!/usr/bin/env bash
# V25 dùng Activity thật, game chỉ ở runner/emulator; OUT chỉ log/ảnh/số đo.
set -euo pipefail
SCRATCH="${1:?scratch game CI}"; OUT="${2:?report}"; EXPECT_FAIL="${3-vx}"
PKG=com.aow.monika
mkdir -p "$OUT"
LOGCAT_PID=
stop_logcat() { if [ -n "$LOGCAT_PID" ]; then kill "$LOGCAT_PID" 2>/dev/null || true; wait "$LOGCAT_PID" 2>/dev/null || true; LOGCAT_PID=; fi; }
trap stop_logcat EXIT
# Ubuntu runner luôn có grep; không phụ thuộc ripgrep ngoài workflow.
command -v grep >/dev/null
adb root >/dev/null
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk >/dev/null
APP_UID=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
python3 - "$SCRATCH/web/rgss.zip" "$SCRATCH/pack" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z: z.extractall(sys.argv[2])
PY
adb push "$SCRATCH/pack" /data/local/tmp/v25-pack >/dev/null
BASE="/data/data/$PKG/files"
adb shell "mkdir -p $BASE/packs; cp -r /data/local/tmp/v25-pack $BASE/packs/rgss; chown -R $APP_UID:$APP_UID $BASE; restorecon -R $BASE"
while IFS='|' read -r id relative <&3; do
  GAME="$BASE/games/v25-$id"
  adb push "$SCRATCH/$relative" "/data/local/tmp/v25-$id" >/dev/null
  adb shell "mkdir -p $BASE/games; cp -r /data/local/tmp/v25-$id $GAME; chown -R $APP_UID:$APP_UID $GAME; chmod -R 777 $GAME; restorecon -R $GAME"
  adb shell am force-stop "$PKG"
  adb shell rm -f "$BASE/rgss-compat/v44-ci-range-types.txt" # Chỉ metadata của probe CI, không file game.
  adb logcat -c
  # Thu ngay từ lúc mở game: bộ đệm logcat có thể mất lỗi khởi động khi máy ghi nhiều log.
  adb logcat -v threadtime > "$OUT/$id-logcat.txt" & LOGCAT_PID=$!
  adb shell am start -W -f 0x10008000 -n "$PKG/vn.aow.monika.runner.RgssGameActivity" --es game_path "$GAME" --es title "R6-$id" > "$OUT/$id-start.txt"
  # Đợi native, chụp tiêu đề trước gửi phím; ảnh còn cần người đọc xác nhận.
  adb shell pidof "$PKG:game" > "$OUT/$id-pid-start.txt" || true
  sleep 25
  OBSERVE_START=$SECONDS
  adb exec-out screencap -p > "$OUT/$id-before-key.png"
  adb shell uiautomator dump /data/local/tmp/v25-ui.xml >/dev/null
  adb pull /data/local/tmp/v25-ui.xml "$OUT/$id-before-key.xml" >/dev/null
  # Dialog Ruby đã lỗi thì không lặp Maestro 13 lần trên nút bị dialog che.
  engine_error=0
  if python3 scripts/ci-rgss-real-verdict.py --dialog "$OUT/$id-before-key.xml"
  then
    engine_error=1
    echo "$id: dialog lỗi Ruby trước phím; chưa vào tiêu đề/chơi60s" >> "$OUT/summary.txt"
  else
    maestro test .maestro-rgss-real/key.yaml --test-output-dir "$OUT/$id-key" > "$OUT/$id-key.txt" 2>&1 || true
  fi
  # Mỗi 5 giây chạm phím ảo A/điều hướng. Không coi process sống là đã vào gameplay.
  for i in $(seq 1 12); do
    if [ "$engine_error" = 1 ]; then sleep 5; continue; fi
    if ! adb shell pidof "$PKG:game" >/dev/null; then break; fi
    maestro test .maestro-rgss-real/key.yaml --test-output-dir "$OUT/$id-key" >> "$OUT/$id-key.txt" 2>&1 || true
    sleep 5
  done
  adb exec-out screencap -p > "$OUT/$id-after-60s.png"
  adb shell uiautomator dump /data/local/tmp/v25-ui.xml >/dev/null
  adb pull /data/local/tmp/v25-ui.xml "$OUT/$id-after-60s.xml" >/dev/null
  printf '%s\n' "$((SECONDS - OBSERVE_START))" > "$OUT/$id-observed-seconds.txt"
  adb shell pidof "$PKG:game" > "$OUT/$id-pid.txt" || true
  adb shell dumpsys activity exit-info "$PKG" > "$OUT/$id-exit-info.txt"
  stop_logcat
  # The observer only writes after a matching Range exception. An absent file
  # is no diagnostic evidence, and must be explicit even when gameplay passes.
  if adb shell cat "$BASE/rgss-compat/v44-ci-range-types.txt" > "$OUT/$id-range-types.txt" 2>/dev/null; then
    if [ -s "$OUT/$id-range-types.txt" ]; then
      printf '%s\n' 'recorded: matching Range exception types; not gameplay proof' > "$OUT/$id-range-probe-status.txt"
    else
      printf '%s\n' 'empty: no Range type evidence; observer activity not verified' > "$OUT/$id-range-probe-status.txt"
    fi
  else
    printf '%s\n' 'missing: no Range type evidence; absent file does not prove absence of errors' > "$OUT/$id-range-probe-status.txt"
  fi
  adb shell dumpsys media.audio_flinger > "$OUT/$id-audio-global.txt"
  # AudioFlinger là service toàn máy: chỉ trích dòng có PID game, không suy nghe được.
  python3 - "$OUT/$id-pid.txt" "$OUT/$id-audio-global.txt" "$OUT/$id-audio.txt" <<'PYAUDIO'
import pathlib,re,sys
pid=pathlib.Path(sys.argv[1]).read_text().strip()
lines=pathlib.Path(sys.argv[2]).read_text().splitlines()
hits=[l for l in lines if pid and re.search(r'(?<![0-9])'+re.escape(pid)+r'(?![0-9])',l)]
pathlib.Path(sys.argv[3]).write_text('Process com.aow.monika:game; PID='+pid+'\nDòng AudioFlinger có PID (không chứng minh âm thanh nghe được):\n'+'\n'.join(hits)+'\n')
PYAUDIO
  adb shell dumpsys gfxinfo "$PKG:game" framestats > "$OUT/$id-framestats.txt"
  adb shell "find $GAME -iname 'save*' -type f" > "$OUT/$id-save-files.txt"
done 3< <(python3 - "$SCRATCH/cases.json" <<'PY'
import json,sys
for r in json.load(open(sys.argv[1])):
    if r['prepared']: print(r['id']+'|'+r['localGameDir'])
PY
)
python3 scripts/ci-rgss-real-verdict.py --out "$OUT" --cases "$SCRATCH/cases.json" --expect-fail "$EXPECT_FAIL"
