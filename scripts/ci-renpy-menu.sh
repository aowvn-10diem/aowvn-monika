#!/usr/bin/env bash
# Synthetic only. Artifact directory contains evidence only, no game/ZIP/APK.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'CI only'; exit 1; }
PACK=${1:?engine ZIP}; OUT=${2:?evidence directory}
mkdir -p "$OUT"
SCRATCH="$RUNNER_TEMP/v45-menu"
mkdir -p "$SCRATCH/pack" "$SCRATCH/synthetic/game"
unzip -q "$PACK" -d "$SCRATCH/pack"
cat > "$SCRATCH/synthetic/game/script.rpy" <<'RPY'
init python:
    import os
    config.name = "Monika CI RenPy"
    config.version = "V45-B"
    config.screen_width = 640
    config.screen_height = 360
    def monika_ci_mark(name):
        with open(os.path.join(os.environ["ANDROID_PUBLIC"], "v45-" + name + ".txt"), "w") as f:
            f.write("Monika-CI-" + name + ":1")
screen main_menu():
    tag menu
    add Solid("#F28C28")
    text "Monika CI RenPy" xalign 0.5 yalign 0.3
    textbutton "Start CI" xalign 0.5 yalign 0.6 action Start()
    timer 1.0 action Function(monika_ci_mark, "menu")
label main_menu:
    call screen main_menu
    return
label start:
    $ monika_ci_mark("start")
    scene expression Solid("#F28C28")
    "Monika CI: synthetic menu opened."
    while True:
        pause 1.0
RPY
PKG=com.aow.monika
adb root >/dev/null; adb wait-for-device
APK=$(find app/build/outputs/apk/debug -name '*universal*.apk' -print -quit)
if [ -z "$APK" ]; then APK=$(find app/build/outputs/apk/debug -name '*.apk' -print -quit); fi
test -n "$APK"
adb install -r "$APK" >/dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
adb shell "am start -W -n $PKG/vn.aow.monika.ui.MainActivity" >/dev/null
APP_UID=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
ROOT="/data/data/$PKG/files"
adb push "$SCRATCH/pack" /data/local/tmp/v45-pack >/dev/null
adb push "$SCRATCH/synthetic" /data/local/tmp/v45-synthetic >/dev/null
adb shell "mkdir -p $ROOT/packs; rm -rf $ROOT/packs/renpy8 $ROOT/v45-synthetic $ROOT/renpy-games; cp -r /data/local/tmp/v45-pack $ROOT/packs/renpy8; cp -r /data/local/tmp/v45-synthetic $ROOT/v45-synthetic; chown -R $APP_UID:$APP_UID $ROOT; chmod -R 755 $ROOT/packs/renpy8 $ROOT/v45-synthetic; restorecon -R $ROOT"
collect() {
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/final.png" 2>/dev/null || true
  adb shell dumpsys activity exit-info "$PKG" > "$OUT/exit-info.txt" 2>/dev/null || true
}
trap collect EXIT
adb shell am force-stop "$PKG"; adb logcat -c
adb shell "am start -W -n $PKG/vn.aow.monika.runner.RenpyGameActivity --es game_base $ROOT/v45-synthetic --es title Monika-CI-RenPy" > "$OUT/launch.txt"
marker() { adb shell "find $ROOT/renpy-games -name v45-$1.txt -exec cat {} \;" 2>/dev/null | tr -d '\r'; }
for i in $(seq 1 60); do
  if [ "$(marker menu)" = Monika-CI-menu:1 ]; then break; fi
  sleep 2
done
[ "$(marker menu)" = Monika-CI-menu:1 ] || { echo 'FAIL: synthetic menu marker missing'; exit 1; }
adb exec-out screencap -p > "$OUT/menu.png"
read -r WIDTH HEIGHT < <(python3 - "$OUT/menu.png" <<'PY'
import struct,sys
with open(sys.argv[1],'rb') as f:b=f.read(24)
assert b[:8]==b'\x89PNG\r\n\x1a\n';print(*struct.unpack('>II',b[16:24]))
PY
)
adb shell input tap "$((WIDTH/2))" "$((HEIGHT*6/10))"
for i in $(seq 1 15); do
  [ "$(marker start)" = Monika-CI-start:1 ] && break
  sleep 2
done
[ "$(marker start)" = Monika-CI-start:1 ] || { echo 'FAIL: Start input did not reach synthetic label'; exit 1; }
for i in $(seq 1 15); do
  sleep 4
  adb shell pidof "$PKG:game" > "$OUT/game-pid.txt" || { echo 'FAIL: game process died'; exit 1; }
done
collect
if grep -Eq 'FATAL EXCEPTION|Fatal signal|Traceback \(most recent call last\)' "$OUT/logcat.txt"; then echo 'FAIL: runtime exception'; exit 1; fi
printf 'Monika-CI-menu:1\nMonika-CI-start:1\nobserved-after-input=60s\n' > "$OUT/markers.txt"
printf '{"status":"PASS_SYNTHETIC_MENU","api":34,"afterInputSeconds":60,"productionConfigChanged":false}\n' > "$OUT/verdict.json"
