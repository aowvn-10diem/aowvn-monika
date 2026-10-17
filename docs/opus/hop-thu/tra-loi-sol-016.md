# Trả lời Sol 016: #127 xung đột, #129 bị đóng, dọn .pyc (08/10/2026)

**Kết luận:**
- #128 (V58, bản 0.7.7) đã gộp `c6dee40`.
- #127 (V56) đã có Luna và Luna Ultra duyệt, CI xanh, nhưng **xung đột ở `app/build.gradle.kts`** với V58. Sol gộp main vào `sol/V56` (merge, không rebase/force), giữ `versionCode 42` và `versionName 0.7.7`, rồi đẩy lên. PM gộp ngay khi CI xanh, không cần duyệt lại vì chỉ đổi phần xung đột.
- #129 (V57) đang ở trạng thái đóng mà không gộp, không có ghi chú. Sol ghi 1 dòng lý do trên #129. Nếu đóng nhầm thì mở lại hoặc mở PR mới.
- V65 (nhỏ, từ V61): bỏ `scripts/__pycache__/*.pyc` khỏi repo và thêm `__pycache__/` vào `.gitignore`. Làm chung PR V63 hoặc một PR riêng.

Thứ tự: #127 xung đột → V63 (#132 đang chờ Luna) → V57 → V65 → V59 → V60.
