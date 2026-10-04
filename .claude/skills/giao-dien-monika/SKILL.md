---
name: giao-dien-monika
description: Quy tắc giao diện Aow Monika (design spec "Aow Monika": token Monika.colors/type/motion, Radius, FloatingDock, font/icon, PerformanceTier) và cách chạy test giao diện Robolectric. Dùng khi sửa/ thêm màn hình, animation, icon hoặc ảnh chụp UI.
---

# Giao diện "Aow Monika"

- Theo design spec "Aow Monika": nền cream, charcoal, gradient cam–hồng–tím, bo lớn, menu nổi `ui/theme/FloatingDock.kt`.
- Màn hình **chỉ dùng** token `Monika.colors/type/motion`, `Radius`, `primaryGradient()` trong `ui/theme/Theme.kt` và thành phần trong `ui/theme/Components.kt`. Không hard-code màu/cỡ.
- Font **Manrope** (OFL) `res/font/manrope.ttf`. Icon: **Fluent System Icons** (MIT) chép lẻ `res/drawable/ic_fluent_*`; minh họa 3D **Fluent Emoji** `res/drawable-nodpi/fluent3d_*`.
- Chuỗi giao diện và tài liệu viết tiếng Việt.

## Chuyển động tự điều chỉnh
`PerformanceTier` FULL/LITE/OFF. `detectTier`: RAM < 3 GB hoặc < 6 nhân → LITE; tỉ lệ hoạt ảnh Android = 0 → OFF; user chọn được trong Cài đặt. **Mọi animation phải lấy thời lượng từ `Monika.motion`.**

## Test giao diện
Robolectric, nằm trong `./gradlew testDebugUnitTest`: `app/src/test/.../ui/` mở từng màn + lớp phủ trong game; ảnh ra `app/build/screenshots/` (CI: artifact `anh-chup-giao-dien`). Đồng hồ Compose chỉnh tay (`autoAdvance = false`) để không flaky. Máy thật: `docs/TEST-MAY-THAT.md`; cloud: `docs/TEST-LAB.md`.
