# Trả lời 005
Việc cho Sonnet: (1) Làm **V08 = R2** rồi **V12 = R3**, theo bảng việc. (2) Thêm `.kotlin/` vào `.gitignore` và gỡ `.kotlin/sessions/*.salive` khỏi repo (commit `abf636b` lỡ thêm). (3) V10: ngoài lần chạy 14 lượt, thêm **một test tái hiện đúng race** đó, rồi đánh dấu xong.

Kết luận (1 dòng): **Duyệt cổng R1 → R2/R3** (R1 đạt mọi kiểm, gói 7,6 MiB ≤ 15 MB). Gốc lỗi V10 được duyệt; cách sửa đúng hướng.

Lý do + đánh đổi:
- R2 và R3 không cần game. R3 là nơi đầu tiên kiểm đường "SDL đổi gói + nạp `.so` từ gói + tiến trình `:game`" (U4). Mọi bước sau của R và cả khối P (từ P2) đều chờ R3, nên R3 là đường găng.
- Đo tốc độ thật vẫn cần game XP (G1). PM đã ghi G1 trong báo cáo cho sếp; Sonnet hỏi lại sếp khi tới R6, không cần chặn R3.

Các bước (mỗi bước có cách kiểm):
1. **R2 (V08):** module `:rgss` chứa 9 file Java SDL 2.26.3 đổi gói bằng script (ghi trong `UPSTREAM.md`). Kiểm: `assembleRelease` xanh; `dexdump` thấy cả `org.libsdl.app.SDLActivity` lẫn `vn.aow.monika.rgss.sdl.SDLActivity`; ghi mức tăng dung lượng APK.
2. **R3 (V12):** `RgssGameActivity : vn.aow.monika.rgss.sdl.SDLActivity`, tiến trình `:game`, viết mới, đủ hợp đồng JNI (phương án câu 5, mục 3.3). Ghi đè nạp thư viện theo `manifest.json`; gán `GAME_PATH` trước `super.onCreate`; kết thúc tiến trình khi thoát. Lưu ý: bản port bỏ `resumeNativeThread()` trong `onStart` và tự khởi luồng SDL từ `MainActivity`. Đọc kỹ thứ tự gọi đó trước khi viết lại **[CHƯA KIỂM]**.
3. **Kiểm R3 không cần game có bản quyền:** tự sinh một "game" XP tối thiểu lúc chạy CI:
   - `Game.ini`.
   - `Data/Scripts.rxdata` = `Marshal.dump([[0, "Main", Zlib::Deflate.deflate(code)]])`, sinh bằng Ruby trên máy CI.
   - `code` ghi file `monika-ok.txt` vào thư mục game rồi `exit`; không vẽ gì nên không cần RTP.

   Emulator Test (API 30 và 34) đợi log `MonikaGame: rgss-lib-loaded`, rồi kiểm file `monika-ok.txt` bằng `adb shell run-as`. Đạt thì chứng minh được SDL + Ruby + đọc dữ liệu game chạy thật **[CHƯA KIỂM: mkxp-z có chịu chạy khi thiếu RTP lúc chỉ chạy script không]**.
4. logcat không có dòng `SDL: Failed to register methods of` (thư 001, bước 4).

Điều chưa chắc [CHƯA KIỂM]:
- `FindClass` trong `JNI_OnLoad` khi nạp bằng `System.load(<gói>)` (U4): R3 sẽ trả lời.
- JIT của Ruby và mkxp-z dưới lớp dịch ARM của máy ảo x86_64: nếu chỉ máy ảo bị lỗi thì ghi rõ "chỉ máy ảo", không coi là hỏng.

Cần sếp quyết: G1 (game XP) cho bước đo tốc độ. Không chặn R2/R3.
