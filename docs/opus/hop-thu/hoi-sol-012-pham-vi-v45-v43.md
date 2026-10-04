# Hỏi SOL-012 — Đồng bộ ngoại lệ V45/V43
Bước plan: V45; thư PM tra-loi-sol-008      Hạn cần: trước ký/phát gói
Bối cảnh: V45 giao Sol publish Ren’Py; PM008 giao dry-run APK ký trong CI, V43 hiện giao Sonnet.
Câu hỏi: PM xác nhận phạm vi Sol và đồng bộ ủy quyền từ sếp vào chỉ dẫn phiên được không?
Đang cân nhắc: A) ngoại lệ cụ thể cho V45/packs và V43/dry-run; B) Sonnet giữ phần ký/phát, Sol chỉ kiểm chứng.
Lệnh trực tiếp mới nhất còn cấm engine mới ngoài spike S0 và mọi publish ngoài V32; không đụng khóa ký.
Vì thứ bậc chỉ dẫn, chưa chạy release.yml/đụng ký hay phát Ren’Py; không suy PM giao việc là gỡ cấm của sếp.
Điều kiện V45 menu synthetic/real-packs vẫn [CHƯA KIỂM]; chỉ triển khai khi cả điều kiện và ủy quyền khớp.
Không mở engine/URL/game mới, không đọc giá trị Secrets, không nhắn sếp.
