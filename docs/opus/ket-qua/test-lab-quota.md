# Hạn mức Test Lab — UTC, mỗi máy thật tính một lượt

SOL023: tối đa5/ngày, kể cả FAIL; máy thật chỉ cho engine cần ARM. UI/Robo dùng máy ảo. Cả hai workflow tuần tự chung concurrency; V56 một máy/cách instrumentation, không chạy lại K2 SIGSEGV trước Sonnet sửa. K1–K8 đã chung một instrumentation; RGSS chung lượt chưa có runner [CHƯA KIỂM], không tự phát sinh lượt riêng.

| Ngày UTC | Run ID | Workflow | Máy | Mục đích / kết quả | Head | Đơn vị |
|---|---|---|---|---|---|---|
| 2026-10-08 | 37721419669 | test-lab.yml | [CHƯA KIỂM] | G9 Robo lịch sử; dự trữ bảo thủ2 theo workflow cũ, chưa đối chiếu model/usage thật | 59c15402174bdd23f38e759f11423a9353194f97 | 2 |
| 2026-10-08 | 37796105693 | test-lab.yml | cubs37,grizzly37 | V60: cả hai Passed, logs đã đọc; không gameplay | 9dc977417368e0678aa7a4ad47dcd2cdad3c7f96 | 2 |
| 2026-10-08 | 37819533688 | test-lab-engine-games.yml | cubs37,grizzly37 | PM/SOL024: K1 PASS, K2 SIGSEGV; chưa Sol đọc toàn bộ evidence, không claim gameplay đạt | 9f35f8d2c0dbca0b864d0caedaf29e8de7f42482 | 2 |

Tổng dự trữ bảo thủ8Oct=6, trong đó2 lịch sử chưa xác minh; dừng máy thật, không nói đã dùng chính xác6 hay vượt quota Firebase thực tế. Các ngày sau phải đối chiếu mọi run đã bắt đầu với sổ này trước khi chạy. Không ghi project/bucket/token/URL ký. Mỗi kết quả mới do Sol cập nhật bằng PR, không workflow push main.
