# Kiểm tra tay — trình cài game Android (điền kết quả khi thử trên máy thật)

Mọi dòng dưới đây **CHƯA KIỂM** trên máy thật cho tới khi có kết quả ghi vào bảng. Code đã có test tự động (`vn.aow.monika.apkinstall.*`, chạy trong CI) và đã kiểm `ApkRepacker` trên APK thật bằng `apksigner verify` + `aapt2 dump badging`, nhưng chưa ai chạy game đã chỉnh trên điện thoại.

| Ca thử | Android 11 | 13 | 14 | 15/16 |
|---|---|---|---|---|
| APK thường | | | | |
| APKS / XAPK | | | | |
| OBB (tự chép) | | | | |
| Cách 1 — game cần Data | | | | |
| Cách 1 — game targetSdk thấp | — | — | | |
| Tự kiểm tra bắt được game crash / thoát ngay | | | | |
| Cách 2 — chọn thư mục dữ liệu | | | | |
| Cách 3 — gỡ lỗi không dây | chưa làm | | | |
| Game chỉ 32-bit trên máy 64-bit-only → báo đúng | | | | |

Điểm cần chú ý khi thử:
- Cách 1: game đã cài bản gốc thì Monika sẽ hỏi gỡ bản cũ (đổi chữ ký). Save trong game cũ mất.
- ARSCLib ghi lại manifest nhị phân: nếu game nào cài lỗi `INSTALL_PARSE_FAILED_*`, ghi tên game + lỗi vào đây.
- `zipalign` do ARSCLib (`ZipAlign.alignApk`) — kiểm game có `.so` lưu không nén (extractNativeLibs=false).
- Nhịp sống bộ nạp có bị Xiaomi/Oppo tiết kiệm pin cắt không (HealthCheck báo "thoát ngay" nhầm).
