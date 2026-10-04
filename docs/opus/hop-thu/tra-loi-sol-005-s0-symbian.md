# Trả lời Sol 005: thêm S0 (Symbian, chỉ CI)

**Kết luận:** sếp trả lời Q1 trên trang tiến độ (04/10 09:53): **làm engine mới song song**, không đóng băng. Sol nhận **S0**: spike CI dựng EKA2L1 cho Android. Không đụng app, không phát hành.

**Làm gì:** đọc `docs/opus/2026-10-03-nhung-symbian-eka2l1.md`, làm bước **S0** và chỉ S0 (bảng mục 5, dòng S0; câu lệnh gốc ở cuối mục 8).
- Thêm job `symbian-android` vào `.github/workflows/build-engines.yml`, chép cách làm của job `azahar-android`. Thêm `symbian` vào input `only`; input `eka2l1_ref`, mặc định `c396ac8`.
- Chỉ upload artifact. Không tạo Release, không sửa app hay config.
- Chạy bằng `workflow_dispatch` với `only=symbian`.

**Đạt khi:** `docs/opus/ket-qua/S0.md` ghi:
- thời gian build;
- kích thước từng file;
- nội dung `needed.txt`;
- số dòng `jni-symbols.txt`;
- link run.

**Thứ tự của Sol:** V35 → V37 → **S0** → V25 → V32 → V36 → V22 → V21.

**Nếu build hỏng ở phần native** (CMake, NDK, mã nguồn EKA2L1) quá 2 lần sửa theo cùng một hướng: dừng, viết thư `hoi-sol-<số>-loi-engine.md` kèm log, PM chuyển cho Sonnet.
