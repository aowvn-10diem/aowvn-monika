# Thư PM → Nova 001: giao thêm việc (05/10/2026)

**Kết luận:** sếp đã chốt hai điều (05/10): (1) Nova kết luận "Đạt" thì được tính như Luna "Đạt" khi PM xét gộp, áp dụng khi Luna chưa duyệt đúng head sau 2 giờ; (2) giao thêm việc cho Nova. Mỗi việc **một PR** từ nhánh `nova/<mã>`. PR của Nova phải do **Luna** duyệt (Nova không tự duyệt PR của mình). Luna vắng quá 2 giờ thì PM xem trực tiếp.

| Ưu tiên | Mã | Việc | Đạt khi |
|---|---|---|---|
| 1 | N00 | Tiền duyệt dự phòng PR của Sol/Sonnet (thường trực, như PROMPT-NOVA). Hiện chờ duyệt: #82–#86 | Mỗi PR đúng head có một comment đủ 5 mục + `Kết luận:` |
| 2 | N01 | `docs/TEST-MAY-THAT.md` mục 6c: bỏ câu "Chỉ áp dụng khi bản app đã có gói `rgss` (sau R7)". Ghi đúng: config 34 chọn engine RGSS nhúng (gói arm64 `engines-rgss-6`), JoiPlay chỉ dự phòng | Diff chỉ tài liệu; đối chiếu `config/monika-config.json` + `runner/GameLauncher.kt` |
| 3 | N03 | **Unit test cho `runner/EngineRoutes.kt`** (hiện chưa có test nào nhắc tới lớp này): RGSS chọn engine nhúng khi gói arm64 có sẵn; rơi về JoiPlay khi thiếu gói, sai ABI, hoặc route tắt; Kirikiri và Ren'Py đi đúng route. Chỉ thêm test. Nếu phải sửa mã app mới test được thì ghi `kẹt` và nhắn PM | `./gradlew testDebugUnitTest` xanh trên CI; test có ca dương và ca dự phòng |
| 4 | N04 | **Test cho script chưa có test**, ưu tiên theo rủi ro: `verify-apk-cert.py` (dùng khi phát hành), `prepare-core-pins.py`, `gen-architecture.py`, `crash-report-detail.py`, `cheats-index.py`. Mỗi file `scripts/test-<tên>.py`, dữ liệu mẫu tự sinh, không gọi mạng, không dùng khóa thật (`verify-apk-cert` dùng APK/chứng thư giả tạo trong test) | Từng test chạy bằng `python3`; nêu cách chạy trong PR |
| 5 | N05 | **Trang "Thử nhanh 0.7.5" cho sếp** (`docs/THU-NHANH.md`, tối đa 1 màn hình điện thoại): 4 bài thử, mỗi bài ≤ 3 bước + chỗ ghi kết quả. Gồm Kirikiri Kara no Shoujo (cổng G7), một game RPG Maker, một game GB/GBA/NES, nút "Báo lỗi game này". Lấy nội dung từ `TEST-MAY-THAT.md`, không bịa tính năng | Mỗi bước có tên nút đúng như trong app (đối chiếu `strings.xml`) |
| 6 | N02 | Khi PM báo v0.7.5 đã phát hành: CHANGELOG v0.7.5 (`docs/CHANGELOG.md`), ghi rõ là bản thử | Mỗi dòng có PR/commit nguồn |
| 7 | N06 | **Đồng bộ tài liệu dự phòng (thay L09 khi Luna vắng quá 2 giờ)**: sau mỗi PR tính năng được gộp, sửa `README.md`, `docs/opus/HANDOFF-SONNET.md`, `docs/KIEN-TRUC.md` (chạy `scripts/gen-architecture.py`) cho khớp main | Diff chỉ tài liệu; mỗi chỗ sửa dẫn PR nguồn |
| 8 | N07 | **Rà thông tin cũ trong `docs/*.md` và `README.md`** (không gồm `docs/opus/**`): phiên bản app, `configVersion`, tên gói engine, phân vai đội. Gộp vào một PR, ghi bảng "chỗ sai → sửa thành → bằng chứng" | Mọi số đều có nguồn (file:dòng trên main) |

## Luật (giữ nguyên PROMPT-NOVA)
Không gộp, không push `main`, không phát hành, không đụng khóa/token/Secrets, không sửa KE-HOACH, `docs/opus/pm/**`, config, workflow hay mã engine. Song song tối đa 2 PR đang mở chờ duyệt cùng lúc; có PR bị "Cần sửa" thì sửa xong PR đó trước khi mở PR mới. Xong việc nào thì báo PM 1–3 dòng bằng `send_message` tới `session_01EAtBeDqHPJ2AkJq2j1jYMC`.
