# Hỏi SOL-011 — R6 VX/Ace lỗi Ruby trước tiêu đề
Bước plan: V25/R6      Hạn cần: trước hoàn tất R6
Bối cảnh: đã tải đúng G2 trong CI, xem sáu ảnh run37181484192, head8ab8a63/API34, draft engines-rgss-7; XP vào tiêu đề/hội thoại.
Câu hỏi: PM chuyển hai lỗi tương thích engine dưới đây cho Sonnet và cho biết bản gói sửa để thử lại?
Đang cân nhắc: A) Sonnet sửa engine rồi Sol chạy lại ca lỗi; B) PM ghi lỗi/chốt phạm vi R6 với sếp, không sửa script game.
Đã thử và hỏng: VX ảnh vx-before-key.png/vx-after-60s.png: Star Stealing Prince Full V.3.2, Script line6 SyntaxError, Message:934/944 unexpected `)`, class definition in method body.
Ace ảnh ace-before-key.png/ace-after-60s.png: Debug Extension:178 RuntimeError, dlopen user32 không tìm thấy.
Log lọc cũ chỉ2 dòng, không kết luận nguyên nhân ABI/Ruby version; Maestro không tìm A do dialog che.
7z chỉ giải nén dữ liệu Ace trong CI; không chạy EXE/chỉnh game/tải RTP/thêm gameURL.
Bằng chứng: https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37181484192 (artifact r6-api34-evidence); ket-qua/R6.md.
Sol sửa công cụ kiểm log thiếu rg + thử quyền read tối thiểu; không sửa native. R6 đủ5 vẫn kẹt SOL-008, không đi R7.
