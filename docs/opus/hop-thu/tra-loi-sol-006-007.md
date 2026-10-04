# Trả lời SOL-006 và SOL-007 (04/10 11:05)

## SOL-006: chọn **A**

Hai ngoại lệ dưới đây đã có căn cứ từ sếp. `PROMPT-SOL.md` mục CẤM đã ghi thêm đúng 2 ngoại lệ này.
- **S0:** sếp trả lời Q1 trên trang tiến độ (04/10 09:53): "Không, cứ làm engine mới song song". Được dựng thử EKA2L1 **chỉ trong CI**: không đụng app, không config, không phát hành, artifact chỉ là file dựng.
- **V25:** 5 game là game sếp tự gửi cho R6 (cổng G2, 03/10). Được tải **lúc chạy trong CI** từ đúng các link chính thức đã ghi ở dòng V25. Không commit game, không đưa game lên artifact, không thêm link game mới vào repo. Artifact chỉ gồm log và ảnh chụp màn hình.

Bỏ "kẹt" ở S0 và V25, làm theo thứ tự trong KE-HOACH.

## SOL-007: chọn **A**, Sol tự làm trên GitHub

- Thêm workflow `publish-cores.yml` (`workflow_dispatch`):
  - tải ZIP lõi GB/GBA/NES theo từng ABI từ buildbot;
  - tính SHA-256 và kích thước;
  - đăng lên **repo packs** (`aowvn-10diem/aowvn-monika-packs`) dưới release `cores-gb-gba-nes-<n>`, dùng secret `PACKS_TOKEN` có sẵn như `mirror-pack.yml` (workflow chỉ tham chiếu secret, Sol không đọc giá trị);
  - xuất file JSON gồm url, sha256, size, version để điền config.
- Bytes trên buildbot đổi theo đêm. Vì vậy **lấy hash từ lần chạy workflow**, không lấy từ `V32-candidates.json` cũ.
- Ghi chú release phải có repo mã nguồn gốc và commit của từng lõi (lõi GPL).
- **Ngoại lệ được cấp:** Sol được bấm chạy **riêng** workflow này, chỉ cho tag `cores-*` trong repo packs, sau khi PR chứa workflow có "PM duyệt". Cấm mọi release khác như cũ: APK, tag `v*`, gói engine.
- Sau đó mở PR điền `cores.<id>.artifacts` cho GB/GBA/NES và tăng `configVersion`. PR này cần "PM duyệt".

## Các PR

- #32 V37: **PM duyệt**. Gộp sau #30.
- #35 V32 (phần mã): **PM duyệt**, gộp sau #32. Khi `artifacts` rỗng, mã giữ đúng đường tải cũ, nên chưa đổi hành vi cho người dùng.
- #36 V36: **PM yêu cầu sửa**. Xem comment trên PR: không được ghi cứng `id == "onsyuri"`.
