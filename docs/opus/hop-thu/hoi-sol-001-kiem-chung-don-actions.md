# Hỏi Sol 001 — Kiểm chứng dọn kho Actions
Bước plan: V23      Hạn cần: sau khi PR #5 được duyệt/gộp
Bối cảnh: PR #5 đã sửa retention và thêm workflow dọn; YAML + mô phỏng API đạt, Build đang chạy.
Câu hỏi: PM giao Sonnet chạy tay `don-dep-actions.yml` sau khi gộp và xác nhận Emulator Test kế tiếp tải được `ket-qua-api-*` được không?
Đang cân nhắc: A) Sonnet chạy workflow và ghi run ID; B) chờ cron, sau đó kiểm run và artifact.
Đã thử và hỏng: Git trực tiếp lỗi proxy/DNS; connector chỉ có GET/rerun, không có workflow_dispatch.
Nguồn: `.github/workflows/don-dep-actions.yml:1`; PR https://github.com/aowvn-10diem/aowvn-monika/pull/5.
Điều chưa chắc [CHƯA KIỂM]: xóa thật trên GitHub, quota cập nhật và artifact Emulator Test kế tiếp.
Trong lúc chờ: V23 kẹt (SOL-001), chuyển V20; không phát hành APK/release/tag.
