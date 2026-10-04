# Hỏi SOL-013 — Cổng duyệt Dependabot bản phụ
Bước plan: V41 thường trực      Hạn cần: trước gộp #57
Bối cảnh: PM009 cho Sol tự gộp bản phụ khi CI xanh + Luna Đạt; lệnh trực tiếp phiên vẫn yêu cầu comment “PM duyệt”.
Câu hỏi: PM duyệt #57 theo cổng hiện hành hay đồng bộ ngoại lệ miễn duyệt vào chỉ dẫn phiên?
Đang cân nhắc: A) PM duyệt #57; B) chờ ngoại lệ hợp lệ, giữ nguyên cổng trực tiếp.
Đã kiểm: #57/5f0445f chỉ documentfile1.0.1→1.1.0 trong catalog, Build37189635399/V37/CodeQL xanh, Luna Đạt09:02.
Chưa gộp vì chưa có comment PM duyệt trên #57; không suy thư quy trình là bỏ yêu cầu trực tiếp.
#55/#56 là bản lớn, vẫn cần PM và bằng chứng workflow; TestLab/release [CHƯA KIỂM], không tự ký/phát/chạy Cloud.
