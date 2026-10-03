# Phương án câu 6 + 7 (mục 7): kiểm thử không có máy thật, chất lượng crash log

> Opus (PM), 03/10/2026, `main` @ `8229bae`. Việc O02 trong `KE-HOACH.md`, phải xong trước R6.
> Mọi thứ trong file này chưa chạy trên máy thật: **[CHƯA KIỂM]** trừ khi ghi khác.

## Tóm tắt

| Câu | Kết luận |
|---|---|
| 6. Kiểm thử không máy thật | Làm **"game kiểm thử tự sinh"** cho Kirikiri, theo đúng khuôn R3 của RGSS: CI tự viết `startup.tjs` và một file `beep.wav`, không cần tài nguyên có bản quyền. Game ghi **file dấu** cho từng chức năng (vẽ hình, chạm, âm thanh, lưu/tải, Back, quay lại sau Home). CI chỉ kiểm các file đó và điểm ảnh, không phải nhìn mắt. Thêm bài **kiểm chính đường báo lỗi** (giết `:game` bằng `kill -11`, rồi xem có báo cáo đúng hay không). Giai đoạn 2 (cổng G9): chạy các game tự sinh này trên máy ARM thật bằng Firebase Test Lab loại *Game Loop*. |
| 7. Chất lượng crash log | Thiếu nặng nhất là **không đọc được ngăn xếp**. Mapping R8 của bản phát hành không được lưu, trong khi app đã bị đổi tên lớp. Crash native chỉ còn tên `.so`, vì cách lọc chuỗi hiện tại làm mất `rel_pc` và BuildId. Thứ tự sửa: (D1) lưu mapping + ký hiệu `.so`, đọc đúng tombstone → (D5) nút **"Báo lỗi game này"** trong menu game (ảnh màn + mô tả), để bắt lỗi không gây crash → (D4) thêm thông tin môi trường → (D2) lỗi script của engine → (D3) phát hiện màn đen. |

---

## Câu 6. Kiểm thử không có máy thật (Kirikiri: chạm, âm thanh, lưu)

### Kết luận
Dùng lại khuôn đã chạy được ở R3: **game kiểm thử tự sinh + file dấu + kiểm bằng adb**. Emulator Test hiện chỉ kiểm Kirikiri "nạp được `.so` và không chết trong 20 giây". Phương án mở rộng thành 7 bài kiểm tự động. Không cần game thật, không cần người bấm.

### Lý do + đánh đổi
- R3 đã chứng minh mẫu này chạy trên CI, ổn định trên API 30 và 34. Kirikiri mở thẳng được file game qua extra `aow_game_path` (`KirikiriGameActivity.start`), và `startup.tjs` là một game Kirikiri hợp lệ. Vậy không cần `.xp3`, cũng không đụng bản quyền.
- File dấu kiểm được **phía engine**: chạm thật sự tới TJS, âm thanh thật sự chạy (vị trí phát tăng), và dữ liệu lưu còn sau khi tiến trình chết. Ảnh chụp chỉ bổ sung (điểm ảnh giữa màn đúng màu thì GL vẽ được).
- Đánh đổi: máy ảo CI là **x86_64, chạy `.so` arm64 qua native bridge**. Nên nó không bắt được lỗi riêng của ARM thật (driver GPU Mali/Adreno, NEON, máy 32-bit). Phần đó để giai đoạn 2 (Test Lab, máy thật) và G7 (sếp thử tay).
- Mỗi lượt Emulator Test tốn thêm khoảng 2–3 phút. Chấp nhận được vì workflow chạy tay (`workflow_dispatch`).

### Các bước thi công (mỗi bước có cách kiểm)

**K1. Sinh game kiểm thử** (`scripts/ci-emulator-games.sh`, khối mới `=== kirikiri-game`, đặt sau khối Kirikiri hiện có và dùng lại gói đã giải nén).
- `beep.wav`: dùng module `wave` của Python sinh tiếng sine 1 giây, 22050 Hz, mono, 16-bit.
- `startup.tjs` mẫu bên dưới. **[CHƯA KIỂM]** tên và chữ ký API TJS2 trên krkr2yuri: `Timer`, `Window.onMouseDown`, `System.dataPath`, `Storages.isExistentStorage`, `WaveSoundBuffer.position`. Sonnet chỉnh theo log của lần chạy đầu.

```javascript
// startup.tjs: game kiểm thử tự sinh của CI (Aow Monika). Không có tài nguyên bản quyền.
var out = System.exePath;
function mark(name, text) { var a = []; a.add(text + " t=" + System.getTickCount()); a.save(out + name); }

class CiWindow extends Window {
  var base, snd, t, pos1;
  function CiWindow() {
    super.Window();
    setInnerSize(640, 360);
    base = new Layer(this, null);
    base.setImageSize(640, 360); base.setSizeToImageSize();
    base.fillRect(0, 0, 640, 360, 0xFFF28C28);        // màu cam: CI kiểm điểm ảnh giữa màn
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

// Lưu/tải: lần chạy 1 ghi n=1, lần chạy 2 đọc lại rồi ghi n=2.
var saveFile = System.dataPath + "monika-save.txt";
if (Storages.isExistentStorage(saveFile)) {
  var d = Scripts.evalStorage(saveFile);
  mark("monika-load.txt", "n=" + (d.n + 1));
} else {
  var d = %["n" => 1];
  (Dictionary.saveStruct incontextof d)(saveFile);
}
var win = new CiWindow(); win.visible = true;
```
Kiểm: `ls -l` thư mục game trong log CI có cả 2 file.

**K2. Đặt game vào máy ảo và mở.** Đẩy vào `files/games/krkr-ci/` như khối rgss. Chạy `appops set $PKG MANAGE_EXTERNAL_STORAGE allow`, để màn xin quyền "Truy cập mọi tệp" (`KR2Activity.java:341`) không chặn. Rồi `am start -n $PKG/vn.aow.monika.runner.KirikiriGameActivity --es title CI-krkr --es aow_game_path $P/games/krkr-ci/startup.tjs`.
Kiểm: có `monika-ready.txt` trong ≤ 60 giây. Hết giờ thì đỏ, và in logcat đã lọc như các khối khác.

**K3. Vẽ hình.** Chụp `adb exec-out screencap` ở dạng thô (không `-p`). Python đọc header rồi lấy điểm ảnh giữa màn.
Kiểm: màu gần `#F28C28` (sai số ±40 mỗi kênh). Toàn đen thì ghi `BLACK` và đỏ.

**K4. Chạm.** `adb shell input tap <giữa màn>`.
Kiểm: có `monika-touch.txt` trong ≤ 5 giây, với `x≈320, y≈180` (±40). Bài này bắt được 2 lỗi: lớp phủ Compose nuốt chạm, và tọa độ bị sai khi co giãn hoặc có viền đen. Không có file thì chạm lại 1 lần rồi mới kết luận.

**K5. Back mở menu Monika, không thoát game.** Gửi `input keyevent KEYCODE_BACK`.
Kiểm: tiến trình `$PKG:game` vẫn sống, và ảnh chụp khác ảnh ở K3 (menu đã hiện). Gửi Back lần nữa để đóng menu.

**K6. Âm thanh.** Đọc `monika-audio.txt`.
Kiểm: `status=play` và `pos2 > pos1` (bộ trộn tiếng thật sự đang chạy). Phụ thêm: ghi `dumpsys audio`, phần danh sách player của uid app, vào artifact để tham khảo, không dùng để quyết đỏ/xanh.
Lưu ý: `android-emulator-runner` mặc định có `-noaudio`. Đặt `emulator-options: -no-window -gpu swiftshader_indirect -no-snapshot -no-boot-anim` (bỏ `-noaudio`). **[CHƯA KIỂM]** máy ảo không đầu có khởi động được với âm thanh hay không. Không được thì giữ `-noaudio` và đổi K6 thành "chỉ báo, không đỏ", ghi lý do vào `ket-qua/`.

**K7. Lưu/tải qua lần chết tiến trình.** Sau K6: `am force-stop $PKG`, mở lại game y như K2.
Kiểm: `monika-load.txt` có `n=2`. Thiếu file thì đỏ: lưu không bền, hoặc sai thư mục `savedata`.

**K8 (tùy chọn). Quay lại sau Home.** Gửi `KEYCODE_HOME`, đợi 3 giây, rồi đưa task game lên lại. **[CHƯA KIỂM]** cách đưa lên: thử `KEYCODE_APP_SWITCH` 2 lần, hoặc `am start` lại activity khi task còn sống.
Kiểm: ảnh không đen, và chạm lần nữa thì `monika-touch.txt` có `t=` mới. Đây là lỗi hay gặp trên máy thật: mất GL context khi quay lại.

**K9. Áp cùng khuôn cho RGSS ở R6.** Khung RGSS (V12) đã có, V16 đã có bài phím Enter → `Input::C`. Thêm vào script XP tự sinh: một `Sprite` tô màu (cho K3), `Audio.se_play` file wav tự sinh (cho K6, kiểm qua `dumpsys audio`, vì RGSS không có vị trí phát cho SE), `save_data`/`load_data` qua 2 lần chạy (cho K7). Ren'Py làm tương tự ở P6.

**K10. Bài kiểm đường báo lỗi** (cũng là bước kiểm cho câu 7). Khi game krkr-ci đang chạy: `adb shell kill -11 $(pidof $PKG:game)`, rồi mở `MainActivity`.
Kiểm: trong ≤ 30 giây có báo cáo mới ở `files/diag/reports/*.json`. Python kiểm các trường sau:
- `kind` là native;
- `component` bắt đầu bằng `engine:` (theo tên đang dùng cho Kirikiri);
- `session.stage == "playing"`;
- `crumbs` không rỗng;
- trên API ≥ 31, `detail` có `SIGSEGV` hoặc `signal 11`; trên API 30 thì `log` có `Fatal signal 11`.

**[CHƯA KIỂM]** `kill -11` từ ngoài có ra `REASON_CRASH_NATIVE` hay không; lần chạy đầu sẽ cho biết. Lặp lại với `kill -9`, nhưng chỉ ghi kết quả, không đỏ.

### Giai đoạn 2: máy ARM thật trên cloud (cổng G9, không chặn giai đoạn 1)
- Firebase Test Lab có loại test **Game Loop**: app nhận intent `com.google.intent.action.TEST_LOOP`, tự chạy kịch bản, rồi ghi kết quả vào file theo URI trong intent. Dùng lại đúng các game tự sinh ở K1/K9, đẩy lên máy bằng `--other-files` (cùng các gói `kirikiri`/`rgss`, vì app không tải được Releases riêng tư).
- Chỉ bật activity nhận `TEST_LOOP` trong bản dựng CI (`manifestPlaceholders`, cờ Gradle `-PmonikaCi=true`). APK phát hành **không** có lối vào này.
- Mục tiêu: chạy trên GPU thật (Mali/Adreno) và ARM thật. Nếu Test Lab còn máy chạy được armeabi-v7a thì thêm máy đó, đây là đường duy nhất kiểm V15 khi chưa có máy thật. **[CHƯA KIỂM]** danh sách máy và hạn mức miễn phí của gói Spark.
- Cần sếp: workflow `test-lab.yml` **chưa chạy lần nào** (0 lượt), nghĩa là secrets `GCP_SA_KEY`/`GCP_PROJECT_ID` có thể chưa có. Cách cài nằm ở `docs/TEST-LAB.md`. Đây là cổng G9, không gấp.

### Điều chưa chắc [CHƯA KIỂM]
- API TJS2 trên krkr2yuri (xem K1). Kirikiroid2 chuyển chạm thành `onMouseDown` ở `Window` hay ở `Layer`: nếu không vào `Window` thì đặt `onMouseDown` ở lớp `base`.
- Máy ảo bỏ `-noaudio` có chạy được trên runner không đầu không (K6).
- `System.dataPath` có được krkr2 tự tạo không. Không tự tạo thì lưu vào `System.exePath`.
- Cách đưa task về trước sau Home (K8).

**Phần CI không thay được máy thật** (vẫn cần G7/G2): cảm giác chạm và độ trễ âm thanh, hiệu năng và nhiệt trên máy yếu, lỗi driver GPU, máy 32-bit thật, đường dẫn thẻ SD/SAF, và các ROM tùy biến diệt tiến trình nền `:game` (Xiaomi, Oppo…).

---

## Câu 7. Chất lượng crash log: còn thiếu tín hiệu gì

### Hiện có (đã đọc mã `diag/Diagnostics.kt`)
Phiên game (`begin/stage/heartbeat/end`), lý do chết lấy từ `ApplicationExitInfo`, dấu vân tay gộp lỗi trùng, vệt sự kiện (`crumbs`), `envLine` (RAM, heap, đĩa, mạng), `deviceLine` (có `SOC_MODEL`), logcat của pid, `scrub` che dữ liệu riêng tư, gửi tự động lên Worker và đọc lại bằng `scripts/crash-reports.sh`. Nền này tốt. Thiếu nằm ở 5 chỗ dưới đây, xếp theo giá trị.

### Kết luận
Ưu tiên **D1 (đọc được ngăn xếp)** và **D5 (người chơi tự báo lỗi kèm ảnh)**. D1 sửa chỗ mà báo cáo hiện nay gần như không đọc được. D5 là đường duy nhất để biết các lỗi **không gây crash**, gồm đúng nhóm lỗi chạm, âm thanh và lưu của câu 6.

### Các lỗ hổng và cách vá (mỗi bước có cách kiểm)

**D1. Ngăn xếp không đọc được (nặng nhất).**
- *Java:* `app/proguard-rules.pro` chỉ giữ `SourceFile,LineNumberTable`, không có `-dontobfuscate`, nên tên lớp bị R8 đổi. Bản phát hành lại được dựng **trên máy phiên** (`CLAUDE.md`), nên `mapping.txt` mất theo container. Hiện không retrace được báo cáo nào của bản đã phát hành.
  - Vá: mỗi lần phát hành, đính `mapping.txt` thành asset `mapping-<tag>.txt` của release đó (cả đường dựng trên máy phiên lẫn `release.yml`). `scripts/crash-reports.sh <id>` lấy mapping theo phiên bản ghi trong `app` của báo cáo rồi chạy R8 `retrace`.
  - Kiểm: lấy một báo cáo Java thật hoặc giả lập, retrace ra tên lớp `vn.aow.monika.*` gốc.
- *Native:* `traceStrings` chỉ lọc **chuỗi** trong tombstone protobuf (API 31+). `rel_pc` và các trường số bị mất, BuildId có thể bị regex `INTERESTING` bỏ đi. Còn `.so` trong gói đã cắt ký hiệu. Kết quả: chỉ còn biết chết trong `libmkxp-z.so`, không biết chết ở hàm nào.
  - Vá 1: đọc tombstone bằng bộ đọc protobuf tối giản (varint + length-delimited, không thêm thư viện), lấy `signal`, `fault addr`, và từng khung của luồng chết (`rel_pc`, `file_name`, `build_id`, `function_name+offset`). Số thứ tự trường lấy từ `tombstone.proto` của AOSP (Apache-2.0); **không tự đoán**.
  - Vá 2: workflow dựng gói (`build-rgss`, `build-kirikiri`, `build-engines`, `build-renpy-pack`) giữ bản `.so` **chưa cắt** (hoặc `llvm-objcopy --only-keep-debug`), đóng thành `symbols-<gói>-<phiên bản>-<abi>.zip` ở release riêng tư, ghi bảng `BuildId → file`.
  - Vá 3: `crash-reports.sh` gọi `llvm-symbolizer` để ra tên hàm và dòng.
  - Vá 4 (API 30, không có tombstone protobuf): `logcat(pid)` bỏ sót các dòng `DEBUG` của `crash_dump`, vì chúng có pid khác. Lấy thêm dòng `DEBUG` trong khoảng ±5 giây quanh thời điểm chết. **[CHƯA KIỂM]** app có đọc được các dòng này không (logd lọc theo uid; `crash_dump` chạy cùng uid với app).
  - Kiểm: dùng K10 trên API 34, báo cáo có khung với `build_id` và `rel_pc`, và `crash-reports.sh` in ra được tên hàm.

**D5. Lỗi không gây crash: người chơi tự báo.** Thêm mục **"Báo lỗi game này"** vào menu Monika trong game (Retro, Kirikiri, RGSS, sau này Ren'Py). Bấm vào thì gom:
- ảnh màn hiện tại bằng `PixelCopy` của SurfaceView, thu nhỏ ≤ 480p, JPEG ≤ 100 KB;
- một dòng mô tả do người chơi gõ;
- vệt sự kiện và logcat 60 giây gần nhất của `:game`;
- `env`.

Gửi qua Worker sẵn có với `kind = "user"`. Chỉ chụp khi người chơi bấm. Có màn xem trước, và người chơi bỏ được ô "đính kèm ảnh".

Kiểm:
- test Robolectric cho màn gửi;
- Emulator Test: mở menu → bấm "Báo lỗi" → có file báo cáo `kind=user` kèm ảnh. Không gửi thật, vì CI không có endpoint.

**[CHƯA KIỂM]** Worker hiện có nhận được ảnh (giới hạn kích thước, nơi lưu KV/R2) hay không: cần đọc mã Worker trước khi làm.

**D4. Thiếu thông tin môi trường hay là nguyên nhân thật.** Ghi một lần mỗi phiên trong `Diagnostics.begin`, vào `env`:
- `Process.is64Bit()` và native bridge (`ro.dalvik.vm.native.bridge` ≠ `0`: máy x86 chạy `.so` ARM sẽ chết theo kiểu lạ);
- `GL_RENDERER`/`GL_VERSION`, lưu đệm vào Prefs theo phiên bản app;
- phiên bản mọi gói đã cài (`packs/*/version`) và `configVersion`;
- `PerformanceTier`;
- `PowerManager.currentThermalStatus`;
- âm thanh: âm lượng nhạc bằng 0 hay không, thiết bị ra (loa, tai nghe, BT);
- quyền "Truy cập mọi tệp" và loại ổ chứa game (bộ nhớ trong / thẻ SD / SAF), cùng **dung lượng trống của ổ chứa game** (hiện chỉ đo `filesDir`).

Kiểm: unit test cho hàm dựng chuỗi `env`; báo cáo của K10 có đủ các trường.

**D2. Lỗi script của engine không phải crash.** Ví dụ lỗi Ruby trong script game RGSS, lỗi TJS của Kirikiri, `traceback.txt` của Ren'Py. Engine có thể tự thoát "êm", rồi `Diagnostics.end()` chạy nên không có báo cáo nào.
- Vá: khi phiên `:game` kết thúc, kể cả thoát bình thường, quét logcat của pid theo mẫu lỗi **trong config** (config-first: `engines.<id>.errorPatterns`, có mặc định rỗng). Với Ren'Py thì đọc thêm `traceback.txt`/`errors.txt` trong thư mục game. Khớp thì tạo báo cáo `recordHandled(ctx, "engine:<id>", …)` kèm các dòng khớp.
- Kiểm: K9 cho script XP tự sinh `raise "monika-ci"`, CI kiểm có báo cáo `engine:rgss` chứa `monika-ci`.

**D3. Màn đen hoặc treo mà không có ANR** (luồng GL kẹt, luồng chính vẫn ổn).
- Vá: với các engine SDL và Kirikiri, `PixelCopy` lưới 64×36 điểm sau `lib-loaded` 10 giây, 30 giây, và sau mỗi lần `onResume`. Toàn điểm tối liên tục 30 giây thì ghi vệt `black-frame` và tạo báo cáo `recordHandled(…, "màn đen")`. Không gửi ảnh, chỉ gửi thống kê.
- Kiểm: unit test cho hàm phân loại "đen". Emulator Test: K3 ở trạng thái bình thường không tạo báo cáo nào.

**D6 (nhỏ). Đọc báo cáo.** `crash-reports.sh` thêm chế độ `--by-fp`: nhóm theo dấu vân tay + phiên bản app, kèm lần đầu/lần cuối gặp. Dùng để thấy ngay lỗi mới sinh ra sau một bản phát hành.

### Điều chưa chắc [CHƯA KIỂM]
- Số trường trong `tombstone.proto` theo từng bản Android (đọc từ AOSP, không đoán).
- App có đọc được dòng `DEBUG` của `crash_dump` không (D1, vá 4).
- Giới hạn kích thước và nơi lưu của Worker cho ảnh (D5).
- `PixelCopy` có chụp được SurfaceView của SDL/cocos trên mọi máy không (D3, D5). Máy chụp không được thì bỏ qua lặng lẽ và ghi vệt.

### Cần sếp quyết
- **[A1]** D5: ô "đính kèm ảnh màn hình" **mặc định bật**, có xem trước, người chơi bỏ tick được. PM đề xuất bật.
- **[A2]** G9: cài secrets cho Test Lab (`docs/TEST-LAB.md`) khi tiện. Không chặn giai đoạn 1.

---

## Bảng chốt

| Câu hỏi | Kết luận | Việc Sonnet cần làm tiếp |
|---|---|---|
| 6. Kiểm thử không máy thật | Game kiểm thử tự sinh + file dấu (khuôn R3); 7 bài kiểm K1–K7 cho Kirikiri; K9 áp cho RGSS ở R6; giai đoạn 2 là Test Lab Game Loop (G9) | V18: K1–K8 vào `ci-emulator-games.sh` (+ `emulator-options` bỏ `-noaudio`). K9 gộp vào R6 |
| 6/7. Kiểm đường báo lỗi | `kill -11` tiến trình `:game` → phải có báo cáo native đúng `component`/`stage`/`crumbs` | V19: K10 |
| 7. Ngăn xếp đọc được | Lưu `mapping.txt` + ký hiệu `.so` theo phiên bản; đọc tombstone protobuf đúng trường; retrace/symbolize trong `crash-reports.sh` | V20: D1 (cả 4 vá) |
| 7. Lỗi không crash | Nút "Báo lỗi game này" kèm ảnh + mô tả (cần sếp chốt A1) | V21: D5 + D4 |
| 7. Lỗi script / màn đen | Mẫu lỗi engine trong config; theo dõi màn đen bằng `PixelCopy` | V22: D2 + D3 (+ D6) |
