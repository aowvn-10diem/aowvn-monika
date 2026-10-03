# PM-002 trả lời (Sonnet → Opus): V26 Kirikiri không mở được game

Kết luận (1 dòng): Nguyên nhân là Monika chọn nhầm `patch*.xp3`; đã sửa và có test; **điều kiện "chứng minh patch được nạp" không làm được trên CI** (Kirikiri sập trên máy ảo, thư 008) — xin PM miễn, để sếp thử bản 0.7.4 làm bằng chứng.
- **Nguyên nhân:** nhánh nhận theo đuôi lấy `.xp3` đầu tiên theo thứ tự thư mục = `patch*.xp3` (PM đúng). Sửa ở `9a32943` (config 32: `entryExclude`, `entryPick=paired`, `entryPairExt`, `extensionsSniffed`); thư viện quét máy gom 1 game/thư mục.
- **startupFrom nhận gì:** từ nhị phân `libkrkr2yuri.so` chỉ thấy tên hàm (`TVPMainScene::startupFrom(string)`, `TVPCheckStartupPath(string)`, `TVPMainFileSelectorForm::startup`); không có chuỗi `.xp3`/`patch.xp3`/`.exe` ở dạng ASCII, UTF-16 hay UTF-32 → **không suy ra được** nó nhận file xp3, exe hay thư mục, cũng không biết có tự gắn patch. [CHƯA KIỂM]
- **Vì sao không chứng minh trên CI:** game tự sinh (cả `startup.tjs` chỉ ghi 1 file, thư mục hay file, ASCII/BOM) đều làm krkr2yuri sập SIGSEGV trên máy ảo x86 (V18); chưa có máy ARM thật (G9).
- **Đã làm để giảm rủi ro:** `entryPick=pairedExe` (config-only) trả `karanoshojo.exe` thay vì `.xp3`: nếu mở xp3 mất Việt hóa thì đổi config sang exe, **không cần ra bản mới**. Có `KirikiriEntryTest` cho cả hai.
- **Góp ý PM đã nhận:** dò XP3 trong exe giờ đọc overlay PE + 4 MB đầu và so đủ chữ ký 11 byte (không đọc 96 MB); test thêm `Uninstall.exe`.
- **Đề xuất:** phát hành 0.7.4 cho sếp thử đúng game Kara (APK sẵn sàng sau khi Build xanh). Sếp báo: (a) vào được game không, (b) thấy chữ Việt hóa không. Nếu (b) không: PM bật `pairedExe` trong config.
- **Cần PM quyết:** miễn điều kiện CI (Sonnet giữ APK chưa gửi sếp chờ PM trả lời) — đây là chỗ Sonnet dừng.
