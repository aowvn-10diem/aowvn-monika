#!/usr/bin/env bash
# V25 dùng Activity thật, game chỉ ở runner/emulator; OUT chỉ log/ảnh/số đo.
set -euo pipefail
SCRATCH="${1:?scratch game CI}"; OUT="${2:?report}"
PKG=com.aow.monika
mkdir -p "$OUT"
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
fail=0
while IFS='|' read -r id relative <&3; do
  GAME="$BASE/games/v25-$id"
  adb push "$SCRATCH/$relative" "/data/local/tmp/v25-$id" >/dev/null
  adb shell "mkdir -p $BASE/games; cp -r /data/local/tmp/v25-$id $GAME; chown -R $APP_UID:$APP_UID $GAME; chmod -R 777 $GAME; restorecon -R $GAME"
  adb shell am force-stop "$PKG"
  adb logcat -c
  adb shell am start -W -f 0x10008000 -n "$PKG/vn.aow.monika.runner.RgssGameActivity" --es game_path "$GAME" --es title "R6-$id" > "$OUT/$id-start.txt"
  # Đợi native, chụp tiêu đề trước gửi phím; ảnh còn cần người đọc xác nhận.
  sleep 25
  adb exec-out screencap -p > "$OUT/$id-before-key.png"
  adb shell uiautomator dump /data/local/tmp/v25-ui.xml >/dev/null
  adb pull /data/local/tmp/v25-ui.xml "$OUT/$id-before-key.xml" >/dev/null
  # Dialog Ruby đã lỗi thì không lặp Maestro 13 lần trên nút bị dialog che.
  engine_error=0
  if python3 - "$OUT/$id-before-key.xml" <<'PYUI'
import re,sys,xml.etree.ElementTree as ET
text=' '.join(n.get('text','') for n in ET.parse(sys.argv[1]).iter('node'))
raise SystemExit(0 if re.search(r"Script.*(?:SyntaxError|RuntimeError|LoadError|NameError|NoMethodError).*occurred", text, re.S) else 1)
PYUI
  then
    engine_error=1; fail=1
    echo "$id: dialog lỗi Ruby trước phím; chưa vào tiêu đề/chơi60s" >> "$OUT/summary.txt"
  else
    maestro test .maestro-rgss-real/key.yaml --test-output-dir "$OUT/$id-key" > "$OUT/$id-key.txt" 2>&1 || fail=1
  fi
  # Mỗi 5 giây chạm phím ảo A/điều hướng. Không coi process sống là đã vào gameplay.
  for i in $(seq 1 12); do
    if [ "$engine_error" = 1 ]; then break; fi
    if ! adb shell pidof "$PKG:game" >/dev/null; then fail=1; break; fi
    maestro test .maestro-rgss-real/key.yaml --test-output-dir "$OUT/$id-key" >> "$OUT/$id-key.txt" 2>&1 || fail=1
    sleep 5
  done
  adb exec-out screencap -p > "$OUT/$id-after-60s.png"
  # Thu đầy đủ tag để không bỏ mất Ruby/native chỉ vì tag không khớp.
  adb logcat -d -v threadtime > "$OUT/$id-logcat.txt"
  adb shell dumpsys media.audio_flinger > "$OUT/$id-audio.txt"
  adb shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$id-framestats.txt"
  adb shell "find $GAME -iname 'save*' -type f" > "$OUT/$id-save-files.txt"
  if [ "$engine_error" = 1 ]; then
    : # Đã ghi đúng lỗi dialog; không ghi còn PID thành kết quả chơi.
  elif adb shell pidof "$PKG:game" > "$OUT/$id-pid.txt" && ! grep -Eq 'FATAL EXCEPTION|Fatal signal|Failed to register methods' "$OUT/$id-logcat.txt"; then
    echo "$id: còn tiến trình sau phím ảo/60s; tiêu đề/gameplay/âm thanh/lưu-tải/FPS cần đọc bằng chứng" >> "$OUT/summary.txt"
  else
    echo "$id: native chết/sập; xem log và ảnh" >> "$OUT/summary.txt"; fail=1
  fi
done 3< <(python3 - "$SCRATCH/cases.json" <<'PY'
import json,sys
for r in json.load(open(sys.argv[1])):
    if r['prepared']: print(r['id']+'|'+r['localGameDir'])
PY
)
python3 - "$SCRATCH/cases.json" "$OUT/summary.txt" <<'PY2' || fail=1
import json,sys
missing=[r['id'] for r in json.load(open(sys.argv[1])) if not r['prepared']]
if missing:
    with open(sys.argv[2],'a') as f:f.write('Chưa chuẩn bị được: '+','.join(missing)+'\n')
    raise SystemExit(1)
PY2
cat "$OUT/summary.txt"
exit "$fail"
