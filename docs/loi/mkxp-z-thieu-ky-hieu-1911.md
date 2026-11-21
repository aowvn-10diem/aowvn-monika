# #613 `mkxp-z` / `native` — đặc tả: thiếu gói ký hiệu cho engine RGSS

## 1. Dữ liệu chữ ký (từ thân issue, không cần token)

| Trường | Giá trị |
|---|---|
| Chữ ký băm | `fe83ddef34811eb740409da6df03a4ddaa38afd93bdcad31b8d28e8a213e3d70` |
| Lõi / loại | `mkxp-z` / `native` |
| Thành phần | `engine:rgss` (dòng `Thành phần` có sẵn — issue tạo 12/11, sau mốc bot thêm dòng này 08/11) |
| Lượt / máy | 1 / 1 (khoá `6e0dbb24c62df952c484d4152f46ea54`) |
| Ngày | 12/11/2026 — issue crash mới nhất của cả repo |
| Khung | **không có** (thân issue không chép stack) |

⇒ Đây là lần chết native trong **engine RGSS (mkxp-z)**, không phải trong lõi libretro. Một mẫu duy nhất, không tái diễn 7 ngày.

## 2. Vì sao không giải mã được khung

- Engine đang ghim: `modules.rgss.version = "engines-rgss-6"`, tải `rgss-arm64-v8a.zip` (7 922 296 B, `sha256 3135ef8a…0864b`), chỉ `arm64-v8a`. Ghim bản ⇒ `.so` ổn định, **không** phải "lõi trôi bản".
- Nhưng release `engines-rgss-6` và `engines-rgss-7` (kiểm 19/11, `gh release view`) **chỉ có** `rgss-arm64-v8a.zip` + `.sha256`; **không** có asset ký hiệu.
- Trong 54 release của repo (gồm cả bản nháp, `gh api releases?per_page=100`): **không** có release `symbols-rgss-*` nào. So sánh: `symbols-kirikiri-16-arm64-v8a` có thật (68 MB) và workflow `build-kirikiri.yml` **bắt buộc** đính ZIP ký hiệu mới cho phát hành gói.
- `.github/workflows/build-rgss.yml` **đã có** bước dựng và đẩy ký hiệu (`package-native-symbols.py` → `symbols-rgss-<run>-<abi>.zip`, `gh release create --draft`, dòng 174–200), nhưng theo V24/G8 gói này là **nháp chờ chốt giấy phép** ⇒ chưa từng được tạo cho bản `engines-rgss-6` đang phát.
- Hệ quả: `scripts/crash-reports.sh` chỉ ghép khung có **PC + BuildId**; không có ZIP ký hiệu khớp BuildId ⇒ mọi khung của #613 là địa chỉ trần, **không đoán**.

## 3. Việc phải làm (theo thứ tự, rẻ trước)

1. **Chốt G8 (giấy phép)** cho việc phát hành ký hiệu của engine RGSS — đây là cổng duy nhất đang chặn, không phải kỹ thuật. Người quyết: PM.
2. **Xuất ký hiệu đúng bản đang phát**: build lại `engines-rgss-6` (hoặc tái dùng run đã tạo gói) và đẩy `symbols-rgss-6-arm64-v8a.zip`, kèm `build-ids.tsv` ghi rõ mức `debug`/`symtab`/`dynamic-only` cho từng `.so`.
3. **Đối chiếu BuildId trước khi công bố**: `.so` của mkxp-z phải được liên kết với `-Wl,--build-id=sha1` (đã bật cho `libretrodroid.so` ở `libretrodroid/src/main/cpp/CMakeLists.txt`, PR #601). Nếu `.so` RGSS **không** có BuildId thì ZIP ký hiệu vô dụng — kiểm bằng `readelf -n` trước khi phát. **[CHƯA KIỂM]** (chưa tải gói 7,9 MB trong phiên này).
4. **Đính kèm vào chính release gói** (như `build-kirikiri.yml` làm) để lượt sau không phải đi tìm.
5. Chỉ khi có (2)+(3): chạy `scripts/crash-reports.sh <id> --symbols-dir … --symbolizer …` cho báo cáo #613 rồi mới kết luận hàm/tệp.

## 4. Tiêu chí nghiệm thu

- `gh release view engines-rgss-6` có `symbols-rgss-6-arm64-v8a.zip`.
- `build-ids.tsv` trong ZIP có ít nhất một dòng khớp `BuildId` của `.so` trong `rgss-arm64-v8a.zip`.
- `scripts/crash-reports.sh` trên báo cáo #613 in ra được **tên hàm + tệp + dòng** cho khung đầu, hoặc ghi rõ `dynamic-only` nếu bảng ký hiệu bị lược.

## 5. Điều kiện đóng #613

Bản chính thức có ký hiệu (mục 3) **và** khung đã giải mã **và** không có chữ ký mới trong ≥ 1 tuần. Trước đó giữ mở, không vá mù engine.

## 6. Mốc toạ độ

Rà 21/11/2026 theo `origin/main` `3d8e1bcf`, so với mốc `02bd03cc`: `build-rgss.yml` **không đổi** (208 dòng) ⇒ dải `dòng 174–200` còn đúng; `build-kirikiri.yml` không đổi ⇒ rào chắn `[ -s "$SYM" ]` (dòng 218) còn hiệu lực; `libretrodroid/src/main/cpp/CMakeLists.txt` không đổi ⇒ `-Wl,--build-id=sha1` ở **dòng 114**; `scripts/package-native-symbols.py` (workflow gọi `self/scripts/…`) và `scripts/crash-reports.sh` không đổi. Bảng đầy đủ: `docs/loi/nhom-con-lai-1911.md` §5.
