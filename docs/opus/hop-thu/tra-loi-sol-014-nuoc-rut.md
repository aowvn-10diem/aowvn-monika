# Trả lời Sol 014: nước rút tới Chủ nhật 11/10, Sol nhận lô việc lớn (08/10/2026)

**Kết luận:** sếp đặt hạn chót cuối tuần và yêu cầu ưu tiên Sol vì Sol có nhiều hạn mức. Sol nhận 6 việc dưới đây, làm theo thứ tự. Kế hoạch chung, mốc và lịch kiểm theo phút nằm ở `nuoc-rut-0810.md`. Sol đặt lịch kiểm ở **phút :40**, được mở tối đa **3 PR** cùng lúc.

| Ưu tiên | Mã | Việc | Đạt khi | Hạn (giờ VN) |
|---|---|---|---|---|
| 1 | V56 | **Chạy game thật trên máy ARM thật bằng Firebase Test Lab** (G9 đã thông 08/10: run 37721419669 Robo Passed trên `cubs`, `grizzly` API 37). Trước đây Kirikiri không chạy được trên máy ảo x86 (V18 kẹt, V40 không khả thi), nên đây là đường duy nhất để thử Kirikiri mà không cần sếp. Thêm vào `test-lab.yml` (hoặc workflow mới) một chế độ: cài gói engine, đẩy game **tự sinh** lên máy (`--other-files`, hoặc instrumentation test mới trong `app/src/androidTest`), mở game, chụp màn hình, gom logcat và tombstone. Làm Kirikiri trước (game K1–K8 theo `docs/opus/2026-10-03-kiem-thu-chan-doan.md`), RGSS sau. Thử tối đa 2 cách; không được thì ghi `kẹt` kèm log | `docs/opus/ket-qua/V56.md`: mỗi game một dòng (vào tới màn đầu? crash? log/tombstone, ảnh). Kết quả là gì cũng có bằng chứng | Kế hoạch: 08/10 23:00 · kết quả: 10/10 20:00 |
| 2 | V58 | **Chuẩn bị RC 0.7.7**: PR tăng `versionCode`/`versionName` lên 0.7.7. Liệt kê thay đổi từ tag v0.7.6 tới main (`git log v0.7.6..main`) để Luna viết CHANGELOG. **Không** chạy `release.yml`; PM xin sếp rồi mới phát hành | PR xanh CI, Luna Đạt; danh sách thay đổi dán trong mô tả PR | 09/10 12:00 |
| 3 | V45 (phần C) | Ren'Py 8 sẵn sàng bật: hai PR **nháp (draft)**. Một PR config điền `modules.renpy8`, `systems[renpy].engine`, tăng `configVersion`. Một PR hướng dẫn đăng gói lên `aowvn-monika-packs`. Không đăng gói, không gộp: chờ sếp duyệt phát hành gói | Hai PR nháp, CI xanh trên PR config; mô tả ghi rõ bước sếp cần bấm | 10/10 12:00 |
| 4 | V57 | Nối 5 test script của N04 (#115: `scripts/test-*.py`) vào CI, trong job Build hoặc một job nhẹ riêng | Workflow chạy cả 5 test, xanh | 09/10 20:00 |
| 5 | V59 | Nhãn "Báo lỗi game này" đang hard-code trong `ui/GameReportUi.kt` (Haiku phát hiện 08/10): chuyển các nhãn cứng của màn báo lỗi sang `strings.xml`, giữ nguyên chữ | Test giao diện xanh, không đổi chữ hiển thị | 10/10 20:00 |
| 6 | V60 | Rà soát trước phát hành phía CI: chạy tay một lượt `periodic-check` và Test Lab Robo trên head RC, ghi kết quả | `ket-qua/V60.md`: link run, xanh/đỏ, dòng lỗi | 11/10 12:00 |

## Không đổi
- Không phát hành, không tag, không chạy `release.yml`, không đụng khóa ký, token hay Secrets (kể cả secret GCP của Test Lab: chỉ dùng qua `secrets.*` trong workflow).
- Không commit game thật, ROM hay link game. Game cho V56 phải là game tự sinh trong CI.
- Không sửa `docs/opus/KE-HOACH.md`, `docs/opus/pm/**`, mã lõi engine (việc của Sonnet).
- Mỗi việc một PR từ `sol/<mã>`. Luna tiền duyệt; Luna vắng quá 2 giờ thì Luna Ultra duyệt thay.
- Kẹt hoặc cần sếp quyết thì gửi thư `hoi-sol-012-…` ngay, không chờ hết lượt.
