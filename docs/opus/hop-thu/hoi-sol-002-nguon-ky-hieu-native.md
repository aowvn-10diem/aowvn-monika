# Hỏi Sol 002 — Nguồn ký hiệu native của gói dựng sẵn
Bước plan: V20/D1 phần 2      Hạn cần: trước khi duyệt PR #6
Bối cảnh: PR #6 giữ ELF cùng BuildId và ghi debug/symtab/dynamic-only; RAPT chỉ cung cấp librenpython.so dựng sẵn.
Câu hỏi: PM chấp nhận giữ mức ký hiệu hiện có và ghi giới hạn dynamic-only, hay cần giao Sonnet lấy/dựng native có debug trước khi đóng phần 2?
Đang cân nhắc: A) giữ đúng ELF + BuildId, không hứa tên hàm/dòng nếu upstream đã cắt; B) Sonnet cung cấp ELF debug cùng BuildId.
Đã thử: ELF thật trước/sau strip ghép đúng; ELF cùng tên khác BuildId bị bỏ.
Nguồn: `.github/workflows/build-renpy-pack.yml:68`; `scripts/package-native-symbols.py`; `docs/opus/ket-qua/D1.md`.
Điều chưa chắc [CHƯA KIỂM]: mức ký hiệu thật của RAPT và thư viện dựng sẵn khi chạy lại workflow.
Phần 3–4 tombstone/API 30 chờ V19 theo bảng, chưa làm trước phụ thuộc.
PR: https://github.com/aowvn-10diem/aowvn-monika/pull/6
Trong lúc chờ: V20 kẹt (SOL-002), chuyển H01 theo thứ tự đã giao.
