#!/usr/bin/env bash
# V176: chụp ảnh "trước/sau" cho PR giao diện — màn Monika (UI) + bàn phím ảo J2ME (ngang/dọc).
# Chạy trong bước `script:` của reactivecircus/android-emulator-runner (adb đã sẵn sàng trên host).
# Dùng: bash scripts/ci-ui-shots.sh <thư-mục-ra> [đường-dẫn-apk]
# Bỏ trống tham số 2 thì tự tìm APK debug dưới app/build/outputs/apk/<flavor>/debug (find, ưu tiên bản universal).
# Không tải gì từ mạng, không đụng dữ liệu người dùng. Không chụp được ảnh nào thì ghi vào
# ghi-chu.txt/thieu-anh.txt rồi thoát 0 (job ảnh không phải cổng chặn); chỉ thoát 1 khi không cài được APK.
# Ảnh j2me-ban-phim-* CHỈ được tạo khi xác nhận MicroActivity đang hiển thị — không đoán.
set -uo pipefail

OUT=${1:?cần thư mục ra}
APK=${2:-}
# Không truyền đường dẫn: tự tìm APK debug (find thay vì đoán tên; APK nằm dưới thư mục flavor
# app/build/outputs/apk/<flavor>/debug/, ưu tiên bản universal).
if [ -z "$APK" ]; then
  APK=$(find app/build/outputs/apk -path '*/debug/*' -name '*-universal-debug.apk' ! -name '*androidTest*' 2>/dev/null | sort | head -n1)
  [ -n "$APK" ] || APK=$(find app/build/outputs/apk -path '*/debug/*' -name '*.apk' ! -name '*androidTest*' 2>/dev/null | sort | head -n1)
fi
APK=${APK:-}
PKG=com.aow.monika
mkdir -p "$OUT"
NOTE="$OUT/ghi-chu.txt"
THIEU="$OUT/thieu-anh.txt"
: > "$NOTE"
# Chỉ tạo thieu-anh.txt khi thật sự thiếu ảnh (có tệp = có ảnh bị bỏ qua).

note() { printf '%s\n' "$*" | tee -a "$NOTE"; }

# Ghi lý do thiếu ảnh vào log, ghi-chu.txt, thieu-anh.txt (job vẫn xanh: đây không phải cổng chặn).
missing() {
  printf '::warning::%s\n' "$*"
  printf '%s\n' "$*" | tee -a "$NOTE" >> "$THIEU"
}

# Chờ cửa sổ đang focus khớp chuỗi (tối đa ~90 giây).
wait_focus() {
  local want=$1 i
  for i in $(seq 1 45); do
    if adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -q "mCurrentFocus.*$want"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# Activity có thật sự đang ở trên cùng không? Ba nguồn, chỉ cần một nguồn khớp:
#   1. `dumpsys activity activities` — topResumedActivity/mResumedActivity (nguồn ổn định nhất trên API 30+)
#   2. `dumpsys activity top`       — dòng "ACTIVITY <pkg>/<lớp>"
#   3. `dumpsys window`             — mCurrentFocus (chỉ là nguồn phụ, có bản Android không in ra)
activity_visible() { # activity_visible <Tên Activity>
  local want=$1 dump
  dump=$(adb shell dumpsys activity activities 2>/dev/null | tr -d '\r')
  printf '%s\n' "$dump" | grep -qE "(topResumedActivity|mResumedActivity|ResumedActivity).*$want" && return 0
  dump=$(adb shell dumpsys activity top 2>/dev/null | tr -d '\r')
  printf '%s\n' "$dump" | grep -qE "^ *ACTIVITY .*$want" && return 0
  dump=$(adb shell dumpsys window 2>/dev/null | tr -d '\r')
  printf '%s\n' "$dump" | grep -qE "mCurrentFocus.*$want" && return 0
  return 1
}

# Chờ activity hiện ra: wait_activity <Tên Activity> <số lần thử> (mỗi lần cách 3 giây).
wait_activity() {
  local want=$1 tries=$2 i
  for i in $(seq 1 "$tries"); do
    activity_visible "$want" && return 0
    sleep 3
  done
  return 1
}

shot() {
  local name=$1 file="$OUT/$1.png"
  adb exec-out screencap -p > "$file" 2>/dev/null || true
  if [ -s "$file" ] && [ "$(wc -c < "$file")" -gt 1000 ]; then
    note "OK $name.png ($(wc -c < "$file") byte)"
  else
    rm -f "$file"
    missing "THIẾU $name.png — screencap không trả ảnh"
  fi
}

# ---------- Chuẩn bị ----------
[ -n "$APK" ] && [ -f "$APK" ] || { note "LỖI: không thấy APK (${APK:-không tìm thấy *.apk dưới app/build/outputs/apk/*/debug})"; exit 1; }
note "APK: $APK"
adb install -r "$APK" || { note "LỖI: cài APK thất bại"; exit 1; }
# Đã cấp quyền thông báo để hộp thoại hệ thống không che màn hình chụp.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true

# ---------- A. Màn Monika: trước (Trang chủ) → sau (Thư viện) ----------
MAIN="$PKG/vn.aow.monika.ui.MainActivity"
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell am start -W -n "$MAIN" >/dev/null 2>&1 || note "CẢNH BÁO: không mở được MainActivity"
if wait_focus MainActivity; then sleep 2; else note "CẢNH BÁO: không thấy MainActivity trong cửa sổ focus"; sleep 8; fi
shot giao-dien-truoc

# Mở thẳng Thư viện bằng extra có sẵn của app (không phụ thuộc chữ trên màn hình).
adb shell am start -n "$MAIN" --ez open_library true >/dev/null 2>&1 || note "CẢNH BÁO: không gửi được open_library"
sleep 6
shot giao-dien-sau

# ---------- B. J2ME: game Java tổng hợp + bàn phím ảo ngang/dọc ----------
# MonikaLaunchActivity là exported=false (chỉ app gọi), nên cần adb root để mở từ CI.
root_ok=0
if [ "$(adb shell id -u 2>/dev/null | tr -d '\r')" = 0 ]; then
  root_ok=1
else
  adb root >/dev/null 2>&1 || true
  adb wait-for-device
  sleep 2
  [ "$(adb shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && root_ok=1
fi
if [ "$root_ok" != 1 ]; then
  missing "BỎ QUA J2ME: adb root không khả dụng (MonikaLaunchActivity exported=false) — không tạo ảnh j2me-ban-phim-*"
  adb logcat -d 2>/dev/null | tail -n 200 > "$OUT/nhat-ky.txt" || true
  exit 0
fi

# Tạo MIDlet tối thiểu: biên dịch với lớp khung (stub) rồi CHỈ đóng gói CiMidlet.class,
# để lúc chạy lớp javax.microedition.* là bản thật của J2ME Loader nhúng trong app.
build_midlet_jar() {
  local dest=$1 work
  work=$(mktemp -d "${TMPDIR:-/tmp}/v176-midlet.XXXXXX") || return 1
  mkdir -p "$work/stub/javax/microedition/midlet" "$work/stub/javax/microedition/lcdui" \
           "$work/stubclasses" "$work/classes" || return 1

  cat > "$work/stub/javax/microedition/midlet/MIDletStateChangeException.java" <<'JAVA'
package javax.microedition.midlet;

public class MIDletStateChangeException extends Exception {
    public MIDletStateChangeException() { }
    public MIDletStateChangeException(String message) { super(message); }
}
JAVA

  cat > "$work/stub/javax/microedition/midlet/MIDlet.java" <<'JAVA'
package javax.microedition.midlet;

/** Khung tối thiểu để biên dịch MIDlet CI; lúc chạy dùng lớp thật của J2ME Loader. */
public abstract class MIDlet {
    protected MIDlet() { }
    public abstract void startApp() throws MIDletStateChangeException;
    public abstract void pauseApp();
    public abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;
    public final void notifyDestroyed() { }
    public final void notifyPaused() { }
    public String getAppProperty(String key) { return null; }
}
JAVA

  cat > "$work/stub/javax/microedition/lcdui/Displayable.java" <<'JAVA'
package javax.microedition.lcdui;

public abstract class Displayable {
    public int getWidth() { return 240; }
    public int getHeight() { return 320; }
}
JAVA

  cat > "$work/stub/javax/microedition/lcdui/Graphics.java" <<'JAVA'
package javax.microedition.lcdui;

public class Graphics {
    public static final int HCENTER = 1;
    public static final int TOP = 16;
    public void setColor(int color) { }
    public void fillRect(int x, int y, int width, int height) { }
    public void drawString(String text, int x, int y, int anchor) { }
}
JAVA

  cat > "$work/stub/javax/microedition/lcdui/Canvas.java" <<'JAVA'
package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    protected abstract void paint(Graphics g);
}
JAVA

  cat > "$work/stub/javax/microedition/lcdui/Display.java" <<'JAVA'
package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    public static Display getDisplay(MIDlet midlet) { return null; }
    public void setCurrent(Displayable next) { }
}
JAVA

  cat > "$work/CiMidlet.java" <<'JAVA'
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/** Game Java tổng hợp cho ảnh CI: nền tối + chữ, để bàn phím ảo Monika nổi lên trên. */
public class CiMidlet extends MIDlet {
    private final CiCanvas canvas = new CiCanvas();

    public void startApp() throws MIDletStateChangeException {
        Display.getDisplay(this).setCurrent(canvas);
    }

    public void pauseApp() { }

    public void destroyApp(boolean unconditional) throws MIDletStateChangeException { }

    private static final class CiCanvas extends Canvas {
        protected void paint(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xFF7A32);
            g.drawString("Aow Monika CI - J2ME", getWidth() / 2, 20,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }
}
JAVA

  javac --release 8 -Xlint:-options -d "$work/stubclasses" $(find "$work/stub" -name '*.java') || return 1
  javac --release 8 -Xlint:-options -cp "$work/stubclasses" -d "$work/classes" "$work/CiMidlet.java" || return 1
  printf 'Manifest-Version: 1.0\r\nMIDlet-1: Aow Monika CI, , CiMidlet\r\nMIDlet-Name: Aow Monika CI\r\nMIDlet-Vendor: Aow Monika\r\nMIDlet-Version: 1.0\r\nMicroEdition-Profile: MIDP-2.0\r\nMicroEdition-Configuration: CLDC-1.1\r\n' \
    > "$work/MANIFEST.MF" || return 1
  ( cd "$work/classes" && jar cfm "$dest" "$work/MANIFEST.MF" . ) || return 1
  return 0
}

JAR_HOST=${RUNNER_TEMP:-${TMPDIR:-/tmp}}/v176-ci-j2me.jar
if ! build_midlet_jar "$JAR_HOST"; then
  missing "BỎ QUA J2ME: không tạo được MIDlet tổng hợp (javac/jar lỗi) — không tạo ảnh j2me-ban-phim-*"
  adb logcat -d 2>/dev/null | tail -n 200 > "$OUT/nhat-ky.txt" || true
  exit 0
fi

# Đặt file trong thư mục riêng của app để app đọc file:// không cần quyền bộ nhớ.
JAR_DEV="/sdcard/Android/data/$PKG/files/ci-j2me.jar"
adb shell mkdir -p "/sdcard/Android/data/$PKG/files" >/dev/null 2>&1 || true
adb push "$JAR_HOST" "$JAR_DEV" >/dev/null 2>&1 || { missing "BỎ QUA J2ME: không đẩy được .jar vào máy ảo — không tạo ảnh j2me-ban-phim-*"; exit 0; }

adb logcat -c >/dev/null 2>&1 || true
adb shell am start -a android.intent.action.VIEW \
  -d "file://$JAR_DEV" -t application/java-archive \
  -n "$PKG/ru.woesss.j2me.installer.MonikaLaunchActivity" >/dev/null 2>&1 \
  || note "CẢNH BÁO: không mở được MonikaLaunchActivity"

if wait_activity MicroActivity 20; then
  note "J2ME: MicroActivity đang hiển thị (topResumedActivity) — sẽ chụp bàn phím ảo"
else
  # Không có bằng chứng MicroActivity hiển thị thì KHÔNG chụp và KHÔNG đặt tên ảnh J2ME.
  missing "BỎ QUA J2ME: không xác nhận được MicroActivity đang hiển thị sau ~60 giây — không tạo ảnh j2me-ban-phim-*"
  adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' \
    | grep -iE "ResumedActivity|MicroActivity|MonikaLaunch" | head -n 20 > "$OUT/j2me-man-hinh.txt" || true
  adb logcat -d 2>/dev/null | tail -n 400 > "$OUT/nhat-ky.txt" || true
  exit 0
fi
sleep 3

# Ngang rồi dọc: MicroActivity xin ORIENTATION_UNSPECIFIED nên xoay theo hệ thống.
rotate() { # rotate <0|1>
  adb shell wm set-user-rotation lock "$1" >/dev/null 2>&1 \
    || adb shell settings put system user_rotation "$1" >/dev/null 2>&1 || true
}
adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
rotate 1
sleep 5
# Kiểm lại ngay trước mỗi ảnh: activity có thể bị đẩy lui (hộp thoại, thoát game) sau khi xoay.
if activity_visible MicroActivity; then
  shot j2me-ban-phim-ngang
else
  missing "BỎ QUA ảnh j2me-ban-phim-ngang: MicroActivity không còn hiển thị sau khi xoay ngang"
fi

rotate 0
sleep 5
if activity_visible MicroActivity; then
  shot j2me-ban-phim-doc
else
  missing "BỎ QUA ảnh j2me-ban-phim-doc: MicroActivity không còn hiển thị sau khi xoay dọc"
fi

# Trả máy về tự xoay; lưu log để đọc khi ảnh thiếu.
adb shell wm set-user-rotation free >/dev/null 2>&1 \
  || adb shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
adb logcat -d 2>/dev/null | tail -n 400 > "$OUT/nhat-ky.txt" || true
exit 0
