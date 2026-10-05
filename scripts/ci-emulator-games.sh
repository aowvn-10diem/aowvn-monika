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

# ---- Kirikiri: game kiểm thử TỰ SINH (docs/opus/2026-10-03-kiem-thu-chan-doan.md, K1–K8). Cần gói đã đặt ở khối trên.
# Mỗi bước in "KN <KẾT QUẢ> ..." vào summary. K6 (âm thanh) và K8 (Home) mới chỉ báo, chưa làm đỏ.
if [ -d "$OUT/kpack" ] && [ -n "${KRKR_GAME:-}" ]; then   # tắt mặc định: V18 kẹt (engine sập, báo cáo sập làm nhiễu K10); bật bằng KRKR_GAME=1
  echo "=== kirikiri-game (K1–K8)"
  G="$OUT/krkr-game"; rm -rf "$G"; mkdir -p "$G"
  python3 - "$G" <<'PY'
import sys, os, wave, struct, math
g = sys.argv[1]
HEAD = 'var out = System.exePath;\nfunction mark(name, text) { var a = []; a.add(text + " t=" + System.getTickCount()); a.save(out + name); }\n'
S0 = HEAD + 'mark("monika-ready.txt", "ready");\n'
S1 = HEAD + """class CiWindow extends Window {
  var base;
  function CiWindow() {
    super.Window();
    setInnerSize(640, 360);
    base = new Layer(this, null);
    base.setImageSize(640, 360); base.setSizeToImageSize();
    base.fillRect(0, 0, 640, 360, 0xFFF28C28);
    base.visible = true;
    mark("monika-ready.txt", "ready");
  }
}
var win = new CiWindow(); win.visible = true;
"""
S2 = S1.replace('    mark("monika-ready.txt", "ready");', '    var snd = new WaveSoundBuffer(this); snd.open("beep.wav"); snd.looping = true; snd.play();\n    mark("monika-ready.txt", "ready");')
S3 = HEAD + """class CiWindow extends Window {
  var base, snd, t, pos1;
  function CiWindow() {
    super.Window();
    setInnerSize(640, 360);
    base = new Layer(this, null);
    base.setImageSize(640, 360); base.setSizeToImageSize();
    base.fillRect(0, 0, 640, 360, 0xFFF28C28);
    base.visible = true;
    snd = new WaveSoundBuffer(this);
    snd.open("beep.wav"); snd.looping = true; snd.play();
    t = new Timer(onTimer, ""); t.interval = 1500; t.enabled = true;
    mark("monika-ready.txt", "ready");
  }
  function onTimer() {
    if (pos1 === void) { pos1 = snd.position; return; }
    mark("monika-audio.txt", "status=" + snd.status + " pos1=" + pos1 + " pos2=" + snd.position);
    t.enabled = false;
  }
  function onMouseDown(x, y, button, shift) { mark("monika-touch.txt", "x=" + x + " y=" + y + " b=" + button); }
}
var saveFile = System.exePath + "monika-save.txt";
if (Storages.isExistentStorage(saveFile)) {
  var d = Scripts.evalStorage(saveFile);
  mark("monika-load.txt", "n=" + (d.n + 1));
} else {
  var d = %["n" => 1];
  (Dictionary.saveStruct incontextof d)(saveFile);
}
var win = new CiWindow(); win.visible = true;
"""
for name, enc, bom in (("v_bom", "utf-8", b"\xef\xbb\xbf"), ("v_u16", "utf-16-le", b"\xff\xfe")):
    d = "%s/%s" % (g, name); os.makedirs(d)
    open(d + "/startup.tjs", "wb").write(bom + S0.encode(enc))
for i, src in enumerate([S0, S1, S2, S3]):
    d = "%s/s%d" % (g, i); os.makedirs(d)
    open(d + "/startup.tjs", "w", encoding="utf-8").write("// startup.tjs: CI auto-generated test game (Aow Monika), tier %d. ASCII only (krkr TJS2 text loader).\n" % i + src)
    w = wave.open(d + "/beep.wav", "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(22050)
    w.writeframes(b"".join(struct.pack("<h", int(12000 * math.sin(2 * math.pi * 440 * i2 / 22050))) for i2 in range(22050))); w.close()
PY
  GP="/data/data/$PKG/files/games/krkr-ci"
  adb shell rm -rf /data/local/tmp/krkr-game; adb push "$G" /data/local/tmp/krkr-game >/dev/null
  adb shell "mkdir -p $GP && rm -rf $GP/* && cp -r /data/local/tmp/krkr-game/. $GP/ && chown -R $APP_UID:$APP_UID /data/data/$PKG/files/games && chmod -R 777 $GP && restorecon -R $GP"
  adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
  kopen() { GD="$GP/$1"; KT="${2:-$GD/startup.tjs}"; adb shell am force-stop "$PKG"; adb logcat -c; adb shell "rm -f $GD/monika-ready.txt $GD/monika-touch.txt $GD/monika-audio.txt $GD/monika-load.txt"
            adb shell "am start -W -n $PKG/vn.aow.monika.runner.KirikiriGameActivity --es title CI-krkr --es aow_game_path $KT" >/dev/null; }
  kwait() { for i in $(seq 1 "$2"); do adb shell "test -f $GD/$1" 2>/dev/null && return 0; sleep 2; done; return 1; }
  kshot() { adb exec-out screencap > "$1" 2>/dev/null; }
  kpix() { python3 - "$1" <<'PY'
import sys, struct
d = open(sys.argv[1], "rb").read()
w, h = struct.unpack("<II", d[:8]); hdr = len(d) - w * h * 4
if hdr < 12 or hdr > 64: print("BAD %dx%d len=%d" % (w, h, len(d))); sys.exit()
o = hdr + ((h // 2) * w + w // 2) * 4
print("%d %d %d %d %d" % (w, h, d[o], d[o + 1], d[o + 2]))
PY
  }
  kpid() { adb shell pidof "$PKG:game" >/dev/null 2>&1 || adb shell ps -A | grep -q "$PKG:game"; }
  note() { echo "$*" | tee -a "$OUT/games/summary.txt"; }
  kfail=0
  # Thăm dò theo tầng để biết engine sập ở đâu: s0 chỉ chạy script, s1 + cửa sổ/lớp, s2 + âm thanh, s3 đủ bài.
  best=-1
  # Vòng dò cách mở: cùng nội dung s0 nhưng đường vào khác (thư mục / UTF-8 BOM / UTF-16LE BOM).
  for v in "s0 DIR $GP/s0" "v_bom FILE" "v_u16 FILE"; do
    set -- $v
    if [ "$2" = DIR ]; then kopen "$1" "$3"; else kopen "$1"; fi
    if kwait monika-ready.txt 10; then note "K2.var $1 $2 OK chạy được"; else note "K2.var $1 $2 FAIL"; fi
  done
  for st in 0 1 2 3; do
    kopen "s$st"
    if kwait monika-ready.txt 15; then note "K2.s$st OK chạy được"; best=$st
    else
      note "K2.s$st FAIL không có monika-ready.txt sau 30s"
      adb logcat -d | grep -E "Fatal signal|TJS|tjs|Exception|exception" | head -8 | cut -c1-240
      break
    fi
  done
  if [ "$best" = 3 ]; then note "K2 OK game kiểm thử đủ bài mở được (s3 đang chạy)"; else note "K2 FAIL tầng cao nhất chạy được: s$best"; kfail=1; fi
  if [ "$kfail" = 0 ]; then
    sleep 3; kshot "$OUT/games/krkr-k3.raw"; read -r W H R Gc B <<<"$(kpix "$OUT/games/krkr-k3.raw")"
    if [ "$W" = BAD ] || [ -z "${R:-}" ]; then note "K3 FAIL không đọc được ảnh chụp ($W $H)"; kfail=1
    elif python3 -c "import sys;r,g,b=map(int,sys.argv[1:4]);sys.exit(0 if abs(r-242)<=40 and abs(g-140)<=40 and abs(b-40)<=40 else 1)" "$R" "$Gc" "$B"; then note "K3 OK vẽ hình: điểm giữa màn = $R,$Gc,$B (cam)"
    else note "K3 FAIL điểm giữa màn = $R,$Gc,$B (mong 242,140,40); ${W}x${H}"; kfail=1; fi
    adb shell input tap $((W/2)) $((H/2)); kwait monika-touch.txt 3 || { adb shell input tap $((W/2)) $((H/2)); kwait monika-touch.txt 3; }
    TOUCH=$(adb shell "cat $GD/monika-touch.txt" 2>/dev/null | tr -d '\r')
    if [ -n "$TOUCH" ]; then note "K4 OK chạm: $TOUCH"; else note "K4 FAIL không có monika-touch.txt (lớp phủ nuốt chạm hoặc API chạm sai)"; kfail=1; fi
    adb shell input keyevent KEYCODE_BACK; sleep 2; kshot "$OUT/games/krkr-k5.raw"
    if kpid && ! cmp -s "$OUT/games/krkr-k3.raw" "$OUT/games/krkr-k5.raw"; then note "K5 OK Back mở menu, game còn sống"; else note "K5 FAIL Back làm thoát game hoặc không đổi hình"; kfail=1; fi
    adb shell input keyevent KEYCODE_BACK; sleep 1
    kwait monika-audio.txt 10 >/dev/null 2>&1 || true
    AUD=$(adb shell "cat $GD/monika-audio.txt" 2>/dev/null | tr -d '\r')
    if echo "$AUD" | grep -q "status=play" && python3 -c "
import re,sys
m=re.search(r'pos1=(\d+) pos2=(\d+)',sys.argv[1]); sys.exit(0 if m and int(m.group(2))>int(m.group(1)) else 1)" "$AUD"; then note "K6 OK âm thanh chạy: $AUD"; else note "K6 BÁO (chưa đỏ) âm thanh: '${AUD:-không có file}'"; fi
    adb shell dumpsys audio > "$OUT/games/krkr-dumpsys-audio.txt" 2>/dev/null || true
    adb shell "ls -l $GD; cat $GD/monika-save.txt 2>/dev/null" | tee -a "$OUT/games/krkr-files.txt" >/dev/null
    kopen s3
    if kwait monika-load.txt 30; then LD=$(adb shell "cat $GD/monika-load.txt" | tr -d '\r'); if echo "$LD" | grep -q "n=2"; then note "K7 OK lưu/tải bền qua lần chết tiến trình: $LD"; else note "K7 FAIL nội dung '$LD'"; kfail=1; fi
    else note "K7 FAIL không có monika-load.txt sau lần mở lại"; kfail=1; fi
    sleep 3; adb shell input keyevent KEYCODE_HOME; sleep 3; adb shell input keyevent KEYCODE_APP_SWITCH; sleep 1; adb shell input keyevent KEYCODE_APP_SWITCH; sleep 3
    kshot "$OUT/games/krkr-k8.raw"; read -r W2 H2 R2 G2 B2 <<<"$(kpix "$OUT/games/krkr-k8.raw")"
    note "K8 BÁO (chưa đỏ) sau Home + APP_SWITCH: tiến trình $(kpid && echo sống || echo CHẾT), điểm giữa = ${R2:-?},${G2:-?},${B2:-?}"
  fi
  adb logcat -d > "$OUT/games/krkr-game.logcat.txt" 2>/dev/null
  if [ "$kfail" != 0 ]; then
    echo "--- logcat (lọc) kirikiri-game ---"
    grep -E "MonikaGame|AndroidRuntime|krkr|Cocos|cocos|Fatal signal|DEBUG|TJS|tjs" "$OUT/games/krkr-game.logcat.txt" | tail -60 | cut -c1-260
    echo "--- hết ---"
    [ "${KRKR_STRICT:-0}" = 1 ] && fail=1   # V18 kẹt: engine sập trên máy ảo (xem hop-thu/hoi-008) → chỉ báo cho tới khi có máy ARM thật
  fi
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
  ruby -rzlib -e 'code = "begin\n  v = Win32API.new(%q(user32), %q(GetAsyncKeyState), %q(i), %q(i)).call(1)\n  File.open(%q(monika-win32.txt), %q(w)) { |f| f.write(%q(ok ) + v.to_s) }\nrescue Exception => e\n  File.open(%q(monika-win32.txt), %q(w)) { |f| f.write(%q(err ) + e.class.to_s) }\nend\nFile.open(%q(monika-ok.txt), %q(w)) { |f| f.write(%q(ok)) }\nloop do\n  Graphics.update\n  Input.update\n  if Input.trigger?(Input::C)\n    Process.kill(11, Process.pid) if File.exist?(%q(crash-on-key.txt))\n    File.open(%q(monika-key.txt), %q(w)) { |f| f.write(%q(enter)) }\n    exit\n  end\nend\n"; File.binwrite(ARGV[0], Marshal.dump([[1, "Main", Zlib::Deflate.deflate(code)]]))' "$OUT/rgame/Data/Scripts.rxdata"
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
  # V44: Win32API('user32') phải chạy được nhờ bản giả (preloadScript) — game thử gọi ngay đầu script và ghi kết quả.
  if [ "$result" = OK ]; then
    w32=$(adb shell "cat $P/games/rgss-ci/monika-win32.txt" 2>/dev/null | tr -d '\r')
    echo "WIN32 rgss Win32API(user32): ${w32:-không có tệp}" | tee -a "$OUT/games/summary.txt"
    case "$w32" in ok*) ;; *) result=WIN32_FAIL ;; esac
  fi
  # Phím: game chờ Input::C; gửi Enter qua hệ thống (đi đường SDL → mkxp-z, cùng đường lớp phủ Monika gọi onNativeKeyDown).
  if [ "$result" = OK ]; then
    keyres=KEY_TIMEOUT
    # Nhấn-nhả tức thì có thể lọt giữa hai khung hình (mkxp-z đọc trạng thái phím mỗi Input.update, máy ảo vẽ bằng SwiftShader rất chậm) → nhấn giữ.
    for i in $(seq 1 15); do
      adb shell input keyevent --longpress KEYCODE_ENTER
      sleep 2
      if adb shell "test -f $P/games/rgss-ci/monika-key.txt" 2>/dev/null; then keyres=KEY_OK; break; fi
    done
    echo "$keyres rgss phím Enter → Input::C" | tee -a "$OUT/games/summary.txt"
    [ "$keyres" = KEY_OK ] || result=KEY_FAIL
    adb exec-out screencap -p > "$OUT/games/rgss-after-key.png" 2>/dev/null || true
    note() { echo "$*" | tee -a "$OUT/games/summary.txt"; }
    # V19 (K10): đường báo lỗi. Giết tiến trình :game bằng SIGSEGV rồi mở lại app: phải có báo cáo native trong files/diag/reports.
    if [ "$keyres" = KEY_OK ]; then
      RD="/data/data/$PKG/files/diag/reports"
      adb shell "rm -f $RD/*.json" 2>/dev/null || true
      # Giết từ ngoài (kill -11) không chắc ra REASON_CRASH_NATIVE → để chính game tự gây SIGSEGV thật (Ruby: Process.kill(11, pid)).
      adb shell "touch $P/games/rgss-ci/crash-on-key.txt; rm -f $P/games/rgss-ci/monika-ok.txt $P/games/rgss-ci/monika-key.txt; chmod 666 $P/games/rgss-ci/crash-on-key.txt"
      adb shell am force-stop "$PKG"
      adb shell "am start -W -n $PKG/vn.aow.monika.runner.RgssGameActivity --es title CI-rgss --es game_path $P/games/rgss-ci" >/dev/null
      for i in $(seq 1 15); do adb shell "test -f $P/games/rgss-ci/monika-ok.txt" 2>/dev/null && break; sleep 2; done
      K10_PID=$(adb shell pidof "$PKG:game" 2>/dev/null | tr -d '\r' | awk '{print $1}')
      K10_T0=$(( $(adb shell date +%s | tr -d '\r') * 1000 - 5000 ))
      for i in $(seq 1 10); do adb shell input keyevent --longpress KEYCODE_ENTER; sleep 2; adb shell pidof "$PKG:game" >/dev/null 2>&1 || break; done
      sleep 3
      echo "K10 lượt thử: pid=$K10_PID từ_ms=$K10_T0" | tee -a "$OUT/games/summary.txt"
      adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
      rep=""
      for i in $(seq 1 15); do rep=$(adb shell "grep -l 'engine:rgss' $RD/*.json 2>/dev/null" | tr -d '\r' | head -1); [ -n "$rep" ] && break; sleep 2; done
      # Nhánh dự phòng CHỈ để in chẩn đoán, không bao giờ làm K10 đạt (V31).
      diagrep=""; [ -n "$rep" ] || diagrep=$(adb shell "ls -t $RD/*.json 2>/dev/null" | tr -d '\r' | head -1)
      adb shell "for f in $RD/*.json; do echo \$f; grep -o '\"component\": *\"[^\"]*\"' \$f; done" 2>/dev/null | tr -d '\r' | paste -sd' ' | cut -c1-600 | sed 's/^/K10 các báo cáo hiện có: /' | tee -a "$OUT/games/summary.txt"
      if [ -z "$rep" ]; then
        [ -z "$diagrep" ] || adb shell "cat $diagrep" > "$OUT/games/k10-report-diag.json" 2>/dev/null
        note "K10 FAIL không có báo cáo engine:rgss sau khi game tự gây SIGSEGV (báo cáo khác nếu có: games/k10-report-diag.json, chỉ để chẩn đoán)"; result=K10_FAIL
      else
        adb shell "cat $rep" > "$OUT/games/k10-report.json" 2>/dev/null
        note "K10 báo cáo: $(python3 - "$OUT/games/k10-report.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
s = d.get("session") or {}
print("kind=%s component=%s stage=%s crumbs=%d build_id=%s" % (d.get("kind"), d.get("component"), s.get("stage"), len(d.get("crumbs") or []), "có" if "build_id" in json.dumps(d) else "không"))
PY
)"
        python3 -c "
import json,sys
d=json.load(open(sys.argv[1])); t0=int(sys.argv[2]); pid=sys.argv[3]
ok = (str(d.get('kind')).lower() in ('native','killed') and ('11' in str(d.get('reason')) or 'SIGSEGV' in str(d.get('reason')) or 'native' in str(d.get('kind')).lower())) and d.get('component')=='engine:rgss' and (d.get('session') or {}).get('kind')=='rgss' and str((d.get('session') or {}).get('pid'))==pid and (d.get('crumbs') or []) and int(d.get('time') or d.get('id') or 0) >= t0 and (not pid or pid in json.dumps(d))
sys.exit(0 if ok else 1)" "$OUT/games/k10-report.json" "$K10_T0" "$K10_PID" && note "K10 OK báo cáo native đúng kind/component/crumbs" || { note "K10 FAIL báo cáo thiếu kind/component/crumbs đúng (xem games/k10-report.json)"; result=K10_FAIL; }
      fi
    fi
  fi
  if [ "$result" != OK ] && [ "$result" != KEY_OK ]; then
    echo "--- logcat (lọc) của rgss ---"
    grep -E "MonikaGame|Monika|AndroidRuntime|SDL|mkxp|ruby|Fatal signal|DEBUG|Diagnostics|UnsatisfiedLink" "$OUT/games/rgss.logcat.txt" | tail -80 | cut -c1-260
    echo "--- hết ---"
  fi
  [ "$result" = OK ] && [ "${keyres:-}" = KEY_OK ] || fail=1
fi
exit $fail
