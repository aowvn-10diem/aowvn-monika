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
CUR_OUT="$OUT"
collect() {
  adb logcat -d > "$CUR_OUT/logcat.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$CUR_OUT/final.png" 2>/dev/null || true
  adb shell dumpsys activity exit-info "$PKG" > "$CUR_OUT/exit-info.txt" 2>/dev/null || true
  adb shell "cat $ROOT/diag/crumbs/*.log" > "$CUR_OUT/crumbs.txt" 2>/dev/null || true
}
trap 'collect' EXIT
marker() { adb shell "find $ROOT/renpy-games -name v45-$1.txt -exec cat {} \;" 2>/dev/null | tr -d '\r'; }

# run_phase <tên> <thư mục chứng cứ> <giữ bao nhiêu vòng 4 giây> <lệnh am start đầy đủ>
# Menu tự sinh -> chạm Start -> dấu label -> giữ tiến trình :game -> không có lỗi nghiêm trọng.
run_phase() {
  local name=$1 dir=$2 hold=$3 launch=$4
  mkdir -p "$dir"; CUR_OUT="$dir"
  adb shell am force-stop "$PKG"; adb shell "rm -rf $ROOT/renpy-games"; adb logcat -c
  # shellcheck disable=SC2086
  adb shell "$launch" > "$dir/launch.txt"
  for i in $(seq 1 60); do
    if [ "$(marker menu)" = Monika-CI-menu:1 ]; then break; fi
    sleep 2
  done
  [ "$(marker menu)" = Monika-CI-menu:1 ] || { echo "FAIL[$name]: synthetic menu marker missing"; exit 1; }
  adb exec-out screencap -p > "$dir/menu.png"
  read -r WIDTH HEIGHT < <(python3 - "$dir/menu.png" <<'PY'
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
  [ "$(marker start)" = Monika-CI-start:1 ] || { echo "FAIL[$name]: Start input did not reach synthetic label"; exit 1; }
  for i in $(seq 1 "$hold"); do
    sleep 4
    adb shell pidof "$PKG:game" > "$dir/game-pid.txt" || { echo "FAIL[$name]: game process died"; exit 1; }
  done
  collect
  if grep -Eq 'FATAL EXCEPTION|Fatal signal|Traceback \(most recent call last\)' "$dir/logcat.txt"; then echo "FAIL[$name]: runtime exception"; exit 1; fi
  printf 'Monika-CI-menu:1\nMonika-CI-start:1\nobserved-after-input=%ss\n' "$((hold*4))" > "$dir/markers.txt"
}

# Giai đoạn 1: mở thẳng RenpyGameActivity (kiểm gói + engine).
run_phase direct "$OUT" 15 "am start -W -n $PKG/vn.aow.monika.runner.RenpyGameActivity --es game_base $ROOT/v45-synthetic --es title Monika-CI-RenPy"
printf '{"status":"PASS_SYNTHETIC_MENU","api":34,"afterInputSeconds":60,"productionConfigChanged":false}\n' > "$OUT/verdict.json"

# Giai đoạn 2: đường sản xuất. Config (cache, cùng khuôn với PR config thật) bật modules.renpy8 + systems[renpy].engine,
# rồi mở qua EnginePrepActivity (đúng thứ GameLauncher gọi cho hệ có engine nhúng).
ROUTE="$OUT/route"; mkdir -p "$ROUTE"
python3 scripts/renpy8-config.py --config config/monika-config.json --version ci-route \
  --url 'https://invalid.example/renpy8-{abi}.zip' --abis arm64-v8a --zip "arm64-v8a=$PACK" \
  --config-version 999999 --out "$SCRATCH/route-config.json"
cp "$SCRATCH/route-config.json" "$ROUTE/route-config.json"
adb push "$SCRATCH/route-config.json" /data/local/tmp/v45-route-config.json >/dev/null
adb shell "cp /data/local/tmp/v45-route-config.json $ROOT/monika-config.json; rm -f $ROOT/packs/renpy8/version; chown -R $APP_UID:$APP_UID $ROOT; restorecon -R $ROOT"
ENGINE_PREP="am start -W -n $PKG/vn.aow.monika.runner.EnginePrepActivity --es engine renpy --es entry $ROOT/v45-synthetic --es title Monika-CI-RenPy-route"

# Đối chứng (không chặn): chưa có tệp version => gói "chưa sẵn sàng" => app phải thử tải từ url giả và báo lỗi tải.
# Chứng minh config mới được nạp (modules.renpy8 được coi là hỗ trợ trên ABI này).
CONTROL=inconclusive
CUR_OUT="$ROUTE"; adb shell am force-stop "$PKG"; adb shell "rm -rf $ROOT/diag/crumbs"; adb logcat -c
adb shell "$ENGINE_PREP" > "$ROUTE/control-launch.txt" || true
for i in $(seq 1 20); do
  if adb shell "cat $ROOT/diag/crumbs/*.log 2>/dev/null" | grep -q 'pack:renpy8'; then CONTROL=download-attempted; break; fi
  sleep 3
done
adb exec-out screencap -p > "$ROUTE/control.png" 2>/dev/null || true
adb shell "cat $ROOT/diag/crumbs/*.log" > "$ROUTE/control-crumbs.txt" 2>/dev/null || true

# Giai đoạn 2 chính: ghi tệp version khớp config => gói sẵn sàng => mở game tới menu tự sinh.
adb shell "printf ci-route > $ROOT/packs/renpy8/version; chown -R $APP_UID:$APP_UID $ROOT/packs; rm -rf $ROOT/diag/crumbs"
run_phase route "$ROUTE" 8 "$ENGINE_PREP"
grep -q 'prep renpy' "$ROUTE/crumbs.txt" || { echo 'FAIL[route]: EnginePrepActivity crumb "prep renpy" missing'; exit 1; }
if grep -q 'invalid.example' "$ROUTE/logcat.txt"; then echo 'FAIL[route]: pack was downloaded although ready'; exit 1; fi
printf '{"status":"PASS_SYNTHETIC_MENU","api":34,"phases":["direct","route"],"routeAfterInputSeconds":32,"configEffectiveControl":"%s","productionConfigChanged":false}\n' "$CONTROL" > "$OUT/verdict.json"
