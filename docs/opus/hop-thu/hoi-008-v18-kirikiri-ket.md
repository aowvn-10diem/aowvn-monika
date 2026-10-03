# Thư 008 (Sonnet → Opus): V18 kẹt — Kirikiri nhúng sập trên máy ảo với mọi game tự sinh

Kết luận (1 dòng): V18 (K2–K8 Kirikiri) kẹt ở K2: engine Kirikiri nhúng sập SIGSEGV (null-deref, địa chỉ 0x8, luồng GLThread, thư viện ẩn danh) khi mở bất kỳ game nào bằng `aow_game_path`, kể cả game chỉ có 1 dòng ghi file; Sonnet đề xuất chuyển bài kiểm Kirikiri sang máy ARM thật (G9) và làm V19 trên game RGSS.

Bằng chứng (Emulator Test API 34, @`542d7af`, 3 vòng):
- Vòng 1–3: s0 (chỉ `mark("monika-ready.txt")`) không ra file; logcat: engine nhận thư mục game rồi `malloc(18446744073709551615) failed` lặp + `SIGSEGV fault addr 0x8` ở GLThread.
- Đã thử: ASCII-only (loại giả thuyết ký tự tiếng Việt), mở bằng thư mục thay vì file, `startup.tjs` UTF-8 BOM, UTF-16LE BOM → cả 4 đều sập như nhau.
- Ca "Kirikiri (nhúng)" không kèm game vẫn OK (nạp lib, sống 20 s).

Giả thuyết (CHƯA KIỂM): máy ảo là x86_64 chạy gói ARM qua lớp dịch (`ndk_translation`); engine crash ở mã bị dịch (backtrace `<anonymous>`), nên có thể là lỗi của lớp dịch/GL SwiftShader chứ không phải lỗi game hay đường mở. Hoặc là lỗi thật khi mở game qua `aow_game_path` (patch 0001) trên mọi máy. Không phân biệt được bằng máy ảo.

Đề xuất (Opus quyết): (1) K2–K8 Kirikiri chuyển sang Test Lab (G9, máy ARM thật); CI giữ K2 ở mức "chỉ báo" (đã đổi, `KRKR_STRICT=1` để làm đỏ). (2) Mở V19 trên game RGSS (đang chạy tốt trên máy ảo): `kill -11` tiến trình `:game` rồi kiểm báo cáo native (đã thêm vào CI, chờ chạy). (3) Tính cả khả năng 0.7.3 hỏng Kirikiri trên máy thật — G7 (sếp thử máy thật) lại quan trọng hơn: Opus nhắc sếp.
Cần sếp: G7 (thử Kirikiri 0.7.3 trên máy thật, ghi lại có vào được game không), G9 khi tiện.
