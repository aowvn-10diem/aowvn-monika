#!/usr/bin/env bash
# Chạy TRONG máy ảo Android (xem .github/workflows/emulator-test.yml).
# Với từng hệ máy có ROM thử nhỏ, miễn phí bản quyền: mở thẳng màn chơi (RetroActivity) bằng quyền root của máy ảo,
# đợi app ghi "loading-dismissed" (màn chờ đã gỡ = game đã vẽ khung hình đầu) và chụp màn hình.
# Lỗi nếu: có "error code=", "no-first-frame-30s", hoặc app sập (FATAL EXCEPTION / Fatal signal) trong lúc chờ.
# Dùng: scripts/ci-emulator-games.sh <thư-mục-kết-quả>
set -u
OUT="${1:-out}"; mkdir -p "$OUT/games"
PKG=com.aow.monika
# Tên | lõi | tên hệ | id hệ | đuôi | link ROM thử | tham số thêm cho am start (vd. ép kiểu hiển thị GBA để chụp so sánh). ROM đều là ROM kiểm thử mã nguồn mở, tải lúc chạy, không đóng vào repo.
CASES=(
  "gba|mgba|Game Boy Advance|gba|gba|https://raw.githubusercontent.com/jsmolka/gba-tests/master/ppu/hello.gba|"
  "gba-lcd|mgba|Game Boy Advance|gba|gba|https://raw.githubusercontent.com/jsmolka/gba-tests/master/ppu/hello.gba|--es display lcd"
  "gba-sharp|mgba|Game Boy Advance|gba|gba|https://raw.githubusercontent.com/jsmolka/gba-tests/master/ppu/hello.gba|--es display sharp"
  "gba-smooth|mgba|Game Boy Advance|gba|gba|https://raw.githubusercontent.com/jsmolka/gba-tests/master/ppu/hello.gba|--es display smooth"
  "gb|gambatte|Game Boy|gbc|gb|https://raw.githubusercontent.com/retrio/gb-test-roms/master/cpu_instrs/individual/01-special.gb|"
  "nes|fceumm|NES|nes|nes|https://raw.githubusercontent.com/christopherpow/nes-test-roms/master/instr_test-v5/rom_singles/01-basics.nes|"
)
adb root >/dev/null 2>&1 || true
adb wait-for-device
sleep 3
APP_UID=$(adb shell stat -c %u "/data/data/$PKG" | tr -d '\r')
fail=0
for c in "${CASES[@]}"; do
  IFS='|' read -r name core system sid ext url extras <<<"$c"
  echo "=== $name ($core)"
  if ! curl -fsSL --retry 3 "$url" -o "$OUT/games/$name.$ext"; then echo "KHÔNG TẢI ĐƯỢC ROM $name"; echo "SKIP $name (không tải được ROM)" >> "$OUT/games/summary.txt"; continue; fi
  adb push "$OUT/games/$name.$ext" /data/local/tmp/ci-rom.$ext >/dev/null
  DIR="/data/data/$PKG/files/ci"
  adb shell "mkdir -p $DIR && cp /data/local/tmp/ci-rom.$ext $DIR/$name.$ext && chown -R $APP_UID:$APP_UID $DIR && chmod -R 755 $DIR && restorecon -R $DIR"
  adb shell am force-stop "$PKG"
  adb logcat -c
  adb shell am start -W -n "$PKG/vn.aow.monika.runner.RetroActivity" --es core "$core" --es game "$DIR/$name.$ext" \
      --es system "$system" --es title "CI $name" --es system_id "$sid" $extras >/dev/null
  result=TIMEOUT
  for i in $(seq 1 45); do   # tối đa ~180 giây (lần đầu phải tải lõi từ buildbot)
    sleep 4
    adb logcat -d > "$OUT/games/$name.logcat.txt" 2>/dev/null
    if grep -q "MonikaGame: loading-dismissed" "$OUT/games/$name.logcat.txt"; then result=OK; break; fi
    if grep -qE "MonikaGame: (error|no-first-frame)" "$OUT/games/$name.logcat.txt"; then result=ERROR; break; fi
    if grep -qE "FATAL EXCEPTION|Fatal signal" "$OUT/games/$name.logcat.txt"; then result=CRASH; break; fi
  done
  sleep 3
  adb exec-out screencap -p > "$OUT/games/$name.png" 2>/dev/null || true
  adb logcat -d > "$OUT/games/$name.logcat.txt" 2>/dev/null
  echo "$result $name ($core) sau $((i*4))s" | tee -a "$OUT/games/summary.txt"
  if [ "$result" != OK ]; then
    echo "--- logcat (lọc) của $name ---"
    grep -E "MonikaGame|Monika|AndroidRuntime|libretro|Fatal signal|DEBUG|Diagnostics" "$OUT/games/$name.logcat.txt" | tail -40 | cut -c1-240
    echo "--- hết ---"
  fi
  [ "$result" = OK ] || fail=1
done

# ---- Kirikiri (nhúng sâu): tải GÓI THẬT từ Releases theo config, đặt vào files/packs/kirikiri như PackManager làm,
# mở KirikiriGameActivity không kèm game (→ màn chọn thư mục của Kirikiri). Kiểm: nạp được libkrkr2yuri.so ngoài APK, tìm thấy tài nguyên, không sập.
echo "=== kirikiri (nhúng)"
KURL=$(python3 -c "import json;print(json.load(open('config/monika-config.json'))['modules']['kirikiri']['url'])")
echo "KURL=$KURL"
if [ -z "$KURL" ] || ! curl -fsSL --retry 3 "$KURL" -o "$OUT/kirikiri-pack.zip"; then
  echo "SKIP kirikiri (không tải được gói)" | tee -a "$OUT/games/summary.txt"
else
  rm -rf "$OUT/kpack" && mkdir -p "$OUT/kpack" && unzip -q "$OUT/kirikiri-pack.zip" -d "$OUT/kpack"
  adb shell rm -rf /data/local/tmp/kpack
  adb push "$OUT/kpack" /data/local/tmp/kpack >/dev/null
  P="/data/data/$PKG/files/packs"
  adb shell "mkdir -p $P && rm -rf $P/kirikiri && cp -r /data/local/tmp/kpack $P/kirikiri && chown -R $APP_UID:$APP_UID $P && chmod -R 755 $P && restorecon -R $P"
  adb shell am force-stop "$PKG"
  adb logcat -c
  adb shell am start -W -n "$PKG/vn.aow.monika.runner.KirikiriGameActivity" --es title "CI kirikiri" >/dev/null
  result=TIMEOUT
  for i in $(seq 1 30); do
    sleep 4
    adb logcat -d > "$OUT/games/kirikiri.logcat.txt" 2>/dev/null
    if grep -q "MonikaGame: kirikiri-lib-loaded" "$OUT/games/kirikiri.logcat.txt"; then result=LOADED; break; fi
    if grep -qE "MonikaGame: error" "$OUT/games/kirikiri.logcat.txt"; then result=ERROR; break; fi
    if grep -qE "FATAL EXCEPTION|Fatal signal" "$OUT/games/kirikiri.logcat.txt"; then result=CRASH; break; fi
  done
  if [ "$result" = LOADED ]; then
    sleep 20   # cho engine dựng cảnh đầu tiên; sập trong lúc này vẫn bị bắt
    adb logcat -d > "$OUT/games/kirikiri.logcat.txt" 2>/dev/null
    if grep -qE "FATAL EXCEPTION|Fatal signal" "$OUT/games/kirikiri.logcat.txt"; then result=CRASH
    elif adb shell pidof "$PKG:game" >/dev/null 2>&1 || adb shell ps -A | grep -q "$PKG:game"; then result=OK
    else result=DIED; fi
  fi
  adb exec-out screencap -p > "$OUT/games/kirikiri.png" 2>/dev/null || true
  adb logcat -d > "$OUT/games/kirikiri.logcat.txt" 2>/dev/null
  echo "$result kirikiri (nhúng)" | tee -a "$OUT/games/summary.txt"
  if [ "$result" != OK ]; then
    echo "--- logcat (lọc) của kirikiri ---"
    grep -E "MonikaGame|Monika|AndroidRuntime|krkr|Cocos|cocos|Fatal signal|DEBUG|Diagnostics|UnsatisfiedLink" "$OUT/games/kirikiri.logcat.txt" | tail -60 | cut -c1-260
    echo "--- hết ---"
  fi
  [ "$result" = OK ] || fail=1
fi

# ---- RPG Maker XP/VX/Ace (RGSS, mkxp-z nhúng): đặt gói rgss vào files/packs/rgss như PackManager làm, sinh một "game" XP tối thiểu
# (Game.ini + Data/Scripts.rxdata: ghi monika-ok.txt rồi thoát; không vẽ gì nên không cần RTP), mở RgssGameActivity.
# Kiểm: nạp được .so từ gói (SDL đổi gói + FindClass), không "Failed to register methods", Ruby chạy script, game ghi được file.
# Chỉ chạy khi có RGSS_PACK_ZIP (workflow đưa vào khi nhập rgss_tag).
echo "=== rgss (nhúng)"
if [ -z "${RGSS_PACK_ZIP:-}" ] || [ ! -f "$RGSS_PACK_ZIP" ]; then
  echo "SKIP rgss (không có RGSS_PACK_ZIP)" | tee -a "$OUT/games/summary.txt"
else
  rm -rf "$OUT/rpack" "$OUT/rgame" && mkdir -p "$OUT/rpack" "$OUT/rgame/Data" && unzip -q "$RGSS_PACK_ZIP" -d "$OUT/rpack"
  printf '[Game]\r\nTitle=Monika RGSS CI\r\nScripts=Data\\Scripts.rxdata\r\nRTP1=\r\n' > "$OUT/rgame/Game.ini"
  ruby -rzlib -e 'code = "File.open(%q(monika-ok.txt), %q(w)) { |f| f.write(%q(ok)) }\nloop do\n  Graphics.update\n  Input.update\n  if Input.trigger?(Input::C)\n    File.open(%q(monika-key.txt), %q(w)) { |f| f.write(%q(enter)) }\n    exit\n  end\nend\n"; File.binwrite(ARGV[0], Marshal.dump([[1, "Main", Zlib::Deflate.deflate(code)]]))' "$OUT/rgame/Data/Scripts.rxdata"
  ls -l "$OUT/rgame" "$OUT/rgame/Data"
  adb shell rm -rf /data/local/tmp/rpack /data/local/tmp/rgame
  adb push "$OUT/rpack" /data/local/tmp/rpack >/dev/null
  adb push "$OUT/rgame" /data/local/tmp/rgame >/dev/null
  P="/data/data/$PKG/files"
  adb shell "mkdir -p $P/packs $P/games && rm -rf $P/packs/rgss $P/games/rgss-ci && cp -r /data/local/tmp/rpack $P/packs/rgss && cp -r /data/local/tmp/rgame $P/games/rgss-ci && chown -R $APP_UID:$APP_UID $P/packs $P/games && chmod -R 777 $P/games/rgss-ci && chmod -R 755 $P/packs && restorecon -R $P"
  # PackManager.ready() cần tệp "version" khớp config; ở đây bỏ qua (mở thẳng Activity), nên không cần.
  adb shell am force-stop "$PKG"
  adb logcat -c
  adb shell "am start -W -n $PKG/vn.aow.monika.runner.RgssGameActivity --es title CI-rgss --es game_path $P/games/rgss-ci" >/dev/null
  result=TIMEOUT
  for i in $(seq 1 30); do
    sleep 4
    adb logcat -d > "$OUT/games/rgss.logcat.txt" 2>/dev/null
    if grep -q "SDL: Failed to register methods" "$OUT/games/rgss.logcat.txt"; then result=JNI_REGISTER; break; fi
    if adb shell "test -f $P/games/rgss-ci/monika-ok.txt" 2>/dev/null; then result=OK; break; fi
    if grep -qE "MonikaGame: error|FATAL EXCEPTION|Fatal signal" "$OUT/games/rgss.logcat.txt"; then result=CRASH; break; fi
  done
  grep -q "MonikaGame: rgss-lib-loaded" "$OUT/games/rgss.logcat.txt" && echo "rgss-lib-loaded: có" || echo "rgss-lib-loaded: KHÔNG"
  adb exec-out screencap -p > "$OUT/games/rgss.png" 2>/dev/null || true
  adb logcat -d > "$OUT/games/rgss.logcat.txt" 2>/dev/null
  echo "$result rgss (nhúng)" | tee -a "$OUT/games/summary.txt"
  # Phím: game chờ Input::C; gửi Enter qua hệ thống (đi đường SDL → mkxp-z, cùng đường lớp phủ Monika gọi onNativeKeyDown).
  if [ "$result" = OK ]; then
    keyres=KEY_TIMEOUT
    for i in $(seq 1 10); do
      adb shell input keyevent KEYCODE_ENTER
      sleep 2
      if adb shell "test -f $P/games/rgss-ci/monika-key.txt" 2>/dev/null; then keyres=KEY_OK; break; fi
    done
    echo "$keyres rgss phím Enter → Input::C" | tee -a "$OUT/games/summary.txt"
    [ "$keyres" = KEY_OK ] || result=KEY_FAIL
    adb exec-out screencap -p > "$OUT/games/rgss-after-key.png" 2>/dev/null || true
  fi
  if [ "$result" != OK ] && [ "$result" != KEY_OK ]; then
    echo "--- logcat (lọc) của rgss ---"
    grep -E "MonikaGame|Monika|AndroidRuntime|SDL|mkxp|ruby|Fatal signal|DEBUG|Diagnostics|UnsatisfiedLink" "$OUT/games/rgss.logcat.txt" | tail -80 | cut -c1-260
    echo "--- hết ---"
  fi
  [ "$result" = OK ] && [ "${keyres:-}" = KEY_OK ] || fail=1
fi
exit $fail
