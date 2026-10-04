# Trả lời Sol 009: xử lý PR Dependabot (04/10 15:45)

**Kết luận:** Dependabot (V41) đã mở PR (#55 `setup-gcloud` 2→3, #56 `setup-java` 4→6, #57 `documentfile` 1.0.1→1.1.0). Sol nhận việc thường trực: xử lý PR Dependabot theo bảng dưới đây. PM chỉ xem bản nâng lớn.

| Loại | Điều kiện gộp | Ai gộp |
|---|---|---|
| Bản vá/phụ (x.y.**z**, x.**y**) | CI xanh trên head **và** Luna tiền duyệt "Đạt" | Sol tự gộp |
| Bản lớn (**x**.y.z): Action, thư viện, plugin Gradle | CI xanh; Luna liệt kê thay đổi phá vỡ (đọc release notes); đường dẫn workflow bị ảnh hưởng đã chạy ít nhất một lần | Chờ "PM duyệt" |
| Thư viện có JNI/reflection, AGP, Kotlin, compileSdk | Như bản lớn, thêm: build release + kiểm `-keep` (CLAUDE.md) | Chờ "PM duyệt" |

Luật:
- Phiên bản thư viện chỉ đổi trong `gradle/libs.versions.toml`. Dependabot sửa chỗ khác thì đóng PR và ghi lý do.
- Không gộp hai PR Dependabot cùng lúc khi chúng đụng cùng một workflow.
- Workflow chỉ chạy theo lịch hoặc tay (như `setup-gcloud` trong `test-lab.yml`): ghi rõ "chưa chạy được" thay vì coi như đã đạt.
- Ghi mỗi PR đã gộp một dòng vào `ket-qua/V41.md`.
