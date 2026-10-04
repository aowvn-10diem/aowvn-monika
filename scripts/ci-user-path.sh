#!/usr/bin/env bash
# Không chép gói/game vào kho app, không cấp appops/quyền thay người dùng.
set -euo pipefail
OUT="${1:?thư mục kết quả}"
FIXTURE="${2:?thư mục fixture}"
PKG=com.aow.monika
mkdir -p "$OUT"
trap 'adb logcat -d > "$OUT/logcat.txt"; adb exec-out screencap -p > "$OUT/final.png"' EXIT
adb root
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
adb reverse tcp:18443 tcp:18443
adb shell run-as "$PKG" test ! -e files/packs/rgss
adb push "$FIXTURE/Monika-V33.zip" /sdcard/Download/Monika-V33.zip
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/Monika-V33.zip >/dev/null
adb logcat -c
maestro test .maestro-user-path/import.yaml --test-output-dir "$OUT/import"
# SAF phải trả content URI và app thật sự giải nén Game.ini; marker chưa có trước mở.
adb shell find /sdcard/Download /sdcard/Android/data/com.aow.monika -name Game.ini > "$OUT/imported.txt"
grep -q 'Monika-V33/Game.ini' "$OUT/imported.txt"
maestro test .maestro-user-path/open.yaml --test-output-dir "$OUT/open"
wait_marker() {
  local name="$1"
  for i in $(seq 1 30); do
    adb shell find /sdcard/Download /sdcard/Android/data/com.aow.monika -name "$name" 2>/dev/null > "$OUT/$name.paths" || true
    if grep -q "Monika-V33/$name" "$OUT/$name.paths"; then return 0; fi
    sleep 2
  done
  echo "::error::Không có $name từ game V33"; return 1
}
wait_marker monika-user-open.txt
adb shell run-as "$PKG" cat files/packs/rgss/version > "$OUT/pack-version.txt"
grep -qx 'ci-V33' "$OUT/pack-version.txt"
python3 - "$FIXTURE/requests.jsonl" <<'PY'
import json,sys
requests=[json.loads(line) for line in open(sys.argv[1])]
assert any(r['path']=='/rgss.zip' and r['status']==200 for r in requests), 'App chưa tải gói qua HTTPS'
PY
cp "$FIXTURE/requests.jsonl" "$OUT/download-requests.jsonl"
cp "$FIXTURE/fixture.json" "$OUT/fixture.json"
maestro test .maestro-user-path/key.yaml --test-output-dir "$OUT/key"
wait_marker monika-user-key.txt
printf 'PASS V33: Thư viện → SAF → nhập ZIP → HTTPS tải gói → mở → phím ảo\n' > "$OUT/summary.txt"
