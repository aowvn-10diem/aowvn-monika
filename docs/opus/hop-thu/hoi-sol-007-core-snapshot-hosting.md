# Hỏi SOL-007 — Nơi lưu snapshot core V32
Bước plan: V32      Hạn cần: trước ghim config
Bối cảnh: đã lấy 12 ZIP GB/GBA/NES × 4 ABI, đo SHA-256/size/version (V32-candidates.json); mã app kiểm hash/cài ứng viên/quay config về snapshot cũ đã nộp.
Câu hỏi: PM chọn nơi lưu URL bất biến và giao ai phát hành 12 ZIP này?
Đang cân nhắc: A) sếp/Sonnet tạo release riêng core-gb-gba-nes trong repo packs; B) cung cấp URL snapshot bất biến sẵn có.
Đã kiểm: releases repo packs chưa có core; buildbot nightly/android chỉ latest; stable/1.22.2/android chỉ APK, thư mục arm64-v8a 404.
Không dùng latest + hash như URL cố định: máy chủ đổi bytes sẽ làm cài mới hỏng.
Các ZIP chỉ nằm /tmp, không commit/phát hành. Config chuẩn chưa đổi, không điền URL giả.
Giả định kỹ thuật: artifacts theo ABI gồm url/sha256/version; core ngoài phạm vi và config cũ giữ đường tải cũ.
V32: kẹt (SOL-007) ở bước URL/config; PM cập nhật KE-HOACH. Tiếp V36.
