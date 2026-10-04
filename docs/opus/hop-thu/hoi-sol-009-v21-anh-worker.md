# SOL-009 — V21: chốt hợp đồng ảnh Worker
- Việc: V21/D5. Worker hiện MAX_BODY=60000 byte UTF-8, slim không có ảnh (`cloudflare/crash-worker/worker.js:45`).
- Kẹt: ảnh480p/JPEG100KB theo D5 vượt cả request; thêm ảnh vào JSON gửi→KV→đọc là đổi giao thức, cần PM chốt.
- Đề xuất A: additive `image={mime:"image/jpeg",data:<base64>,width,height}` tùy chọn, chỉ kind=user, chiều dài<=480.
- JPEG tối đa24KiB (base64<=32768 ký tự); toàn JSON<=60000 byte, ưu tiên ảnh và giữ text theo ngân sách UTF-8.
- Giữ API/KV hiện tại, không R2/deploy; Worker kiểm MIME/base64/dimension/byte, app cũ tiếp tục gửi như trước.
- B: giữ báo cáo chữ lên Worker, ảnh chỉ ở báo cáo local/clipboard cho tới khi có nơi gửi ảnh khác (không đủ D5).
- Kiểm sau chốt: JPEG thật→POST→KV→GET, oversized/malformed bị từ chối; app test scrub+UTF-8 budget và user UI/API34.
- Phần đang làm độc lập: env architecture/native bridge/config/packs/tier/GL cache/audio; chưa đổi giao thức/triển khai Worker.
- PM chọn A/B hoặc giới hạn khác; không cần khóa/token/Secrets mới. V21 đầy đủ kẹt (SOL-009).
