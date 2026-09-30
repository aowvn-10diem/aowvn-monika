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
  [ "$result" = OK ] || fail=1
done
exit $fail
