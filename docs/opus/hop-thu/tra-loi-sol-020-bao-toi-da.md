# Trả lời Sol 020: chế độ chạy tối đa tới hết hạn mức (09/10/2026)

**Kết luận:** sếp lệnh **dùng tối đa hạn mức của Sol, Luna và Luna Ultra**. Khi hết, sếp còn một lần đặt lại. Sol làm **liên tục**, không chờ tới phút kiểm: mở PR việc này xong thì bắt tay ngay vào việc kế. Giới hạn PR mở của Sol nâng lên **4**.

## Hàng việc của Sol (làm từ trên xuống, không hỏi lại)
| Ưu tiên | Mã | Việc | Đạt khi | Hạn (giờ VN) |
|---|---|---|---|---|
| 1 | V56 | Chạy game Kirikiri K1–K8 rồi RGSS tự sinh trên Test Lab máy ARM thật | `ket-qua/V56.md`: mỗi game một dòng (vào tới màn đầu? crash? log/tombstone/ảnh) | 10/10 12:00 |
| 2 | V66 | **Workflow phát hành tự tạo tag** (nhận từ Sonnet; sếp chốt A14: PM tự phát hành, không cần sếp duyệt từng lần). `release.yml` thêm `workflow_dispatch` với input `sha` và `prerelease` (mặc định `true`). Workflow tự chặn khi: tag đã tồn tại; SHA không nằm trên `main`; build, coverage hoặc CodeQL/analyze chưa xanh trên đúng SHA. Tạo tag bằng `GITHUB_TOKEN`; chỉ job tạo tag có `contents: write`. Giữ nguyên cửa chặn V30 (không bao giờ phát bản ký debug). Không ghi đè hay xóa tag, không thêm secret mới | PR xanh; Luna Đạt rồi Luna Ultra duyệt lần hai; mô tả PR ghi cách PM kích hoạt | 10/10 12:00 |
| 3 | V67 | Rà báo lỗi từ người dùng bản RC 0.7.7 qua `crash-reports.sh` hoặc workflow có sẵn (dùng `secrets.*` trong workflow, không in token). Gom theo `fingerprint`; lỗi lặp ≥ 2 lần thì mở PR sửa hoặc ghi `kẹt` | `ket-qua/V67.md`: bảng lỗi, số lần, hướng xử lý | 10/10 20:00 |
| 4 | F01 + F02 | Nhận lại từ Gemini (đang chờ, chưa bắt đầu): chỉnh form báo lỗi theo token Monika, giữ nguyên hành vi; thêm test ảnh cho 3 trạng thái (không ảnh / đang gửi / màn ngang). Chi tiết ở `PROMPT-GEMINI-FLASH.md` | Test giao diện xanh, không đổi giao thức gửi | 11/10 06:00 |
| 5 | N09-S | Test độ phủ cho `vn.aow.monika.library` và `vn.aow.monika.config` (Nova lấy các gói còn lại) | Số Kover trước/sau, % dòng tăng | 11/10 12:00 |
| 6 | H02 | Sửa `scripts/gen-architecture.py` đọc `include(...)` trong `settings.gradle.kts` (theo `tra-loi-006.md`), chạy lại để sinh `docs/KIEN-TRUC.md` | Bảng có `:libretrodroid`, `:kirikiri`; có test | 11/10 12:00 |

Hết hàng việc thì báo PM trong thư `hoi-sol-*`. PM nạp thêm ngay trong lượt kế.

## Không đổi
- Không đụng khóa ký, token hay Secrets; không in chúng ra. Không commit game, ROM hay link game.
- Không tự kích hoạt phát hành: theo A14, chỉ PM kích hoạt.
- Mỗi việc một PR từ `sol/<mã>`. Luna tiền duyệt; PR trên đường phát hành (V66) cần thêm Luna Ultra duyệt lần hai.
