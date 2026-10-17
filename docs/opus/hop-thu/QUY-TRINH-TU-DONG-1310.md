# [A40] Quy trình tự động: PM điều phối, đội tự chạy (sếp chốt 13/10)

Sếp theo dõi qua [bảng tiến độ](https://claude.ai/artifact/7UC9kqrWqv3GNUhhtt4z5d), thử bản mới trên GitHub Releases và phản hồi khi cần. PM chỉ nhắn/ping sếp khi **có việc chặn cả dự án**. Mọi việc khác PM tự quyết theo luật dưới đây. Chế độ tiết kiệm token vẫn áp dụng.

## 1. Vòng lặp mỗi giờ (PM)

`luot-pm.sh` in tối đa ~15 dòng; PM chỉ làm theo các dòng đó, in `YEN` thì dừng ngay, không ghi gì.

| Nhãn | PM làm |
|---|---|
| `GOP #N sha` | Gộp merge commit với đúng sha (luật gộp giữ nguyên: Đạt đúng head, CI bắt buộc xanh; app/CI cần Luna 3 → Luna Ultra) |
| `PMXEM #N` | Đọc review cuối, chốt 1 dòng ở #119 |
| `TAU cat …` | Cắt bản thử mới (mục 2) |
| `TAU len-on-dinh …` | Lên ổn định nếu không có `LOI` mới của bản đó (mục 2) |
| `LOI xN lõi \| loại \| giai đoạn` | Chữ ký lỗi mới: chuyển cho người phụ trách lõi (mục 3) |

## 2. Nhịp phát hành: tối thiểu 2 ngày 1 bản

- **Cắt bản thử:** ≥ 44 giờ từ bản trước và main có commit mới. Sol mở PR nâng `versionCode/versionName` + CHANGELOG (V102), PM gộp khi đủ luật, rồi chạy `release.yml` (`sha=` main, `prerelease=true`), kiểm theo skill `kiem-phat-hanh`, ghi 1 dòng #119 và nhật ký bảng.
- **Lên ổn định:** bản thử ≥ 24 giờ, có lượt cài thật (≥ 3 lượt tải APK, hoặc nhịp phiên của V114 > 0), không có chữ ký lỗi mới mức Chặn của bản đó, sếp không báo chặn. Chạy `release.yml tag=vX.Y.Z prerelease=false`.
- **Bản vá gấp:** lỗi Chặn mới ảnh hưởng nhiều máy → cắt ngay, không chờ 44 giờ.
- Không bao giờ xóa tag/release, không ghi đè bản đã phát hành.

## 3. Crashlog: mọi thao tác lỗi đều để lại log chi tiết

Hiện trạng đo trên main `9897563` (13/10):

| Vấn đề | Số đo |
|---|---|
| Báo cáo không có thông tin (`? \| lowmem \| ?`) | 86/163 = 53% |
| Lỗi được ghi khi bị bắt (`recordHandled`/`recordCoreFailure`) | ~10 điểm gọi |
| Chỗ nuốt lỗi im lặng (`runCatching`/`catch`) trong app | 381 chỗ; nhiều nhất: AzaharActivity 18, PackManager 13, RetroActivity 10, BrowserDownloads 10 |

Việc mới (nối vào hàng việc A39):

| Mã | Ai | Việc | Xong khi |
|---|---|---|---|
| V100 | Sol | (đang làm, #229) bot gom chữ ký, mở issue `crash-moi` | đã gộp |
| V101 | Sol | (đang làm, #231) ngữ cảnh lowmem/ANR | tỷ lệ `?\|lowmem\|?` < 10% trên bản kế |
| **V112** | Sonnet | `Diagnostics.op(c, "tai-game") { … }`: bọc **mọi thao tác người dùng** (tải, giải nén, nhập game, vá ROM, cài gói engine, mở game, tải config/feed). Lỗi → báo cáo `kind=op-fail` gồm tên thao tác, bước, mã lỗi, 30 breadcrumb gần nhất, thông tin máy/bản app; tên file game được, **không** gửi link/ROM. Thay các `runCatching` nuốt lỗi trong luồng người dùng | Test: mỗi thao tác cố ý lỗi sinh đúng 1 báo cáo `op-fail` |
| **V113** | Sol | Chặn nuốt lỗi mới: script CI `scripts/kiem-nuot-loi.sh` báo đỏ khi PR thêm `runCatching`/`catch` không ghi Diagnostics trong `download/ library/ pack/ runner/ azahar/` (cho phép đánh dấu `// diag: bỏ qua <lý do>`) | Chạy trong `build.yml`; có test |
| **V114** | Sol | Worker tiết kiệm: gom trùng (cùng chữ ký + máy trong 10 phút → tăng đếm, không lưu bản mới), endpoint `stats?since=` trả gọn theo chữ ký × bản app; đếm phiên ẩn danh theo bản app (chỉ số đếm, không định danh) để tính **tỷ lệ phiên không lỗi** | `crash-reports.sh` đọc ≤ 20 dòng; bảng có crash-free % theo bản |
| **V115** | Luna Ultra | V111 mở rộng: báo cáo ngày theo bản = crash-free %, top 5 chữ ký mới, chữ ký đã sửa còn tái phát | 1 file/ngày trong `docs/opus/ket-qua/` |

Định tuyến chữ ký (`LOI`): azahar → Sonnet; kirikiri/rgss/renpy → Sonnet (gói engine, skill `dung-engine`); libretro/NDS/PSP → Sol; app/Java → Sol; lowmem/ANR → Sol (V101).

## 4. Khi nào ping sếp

Chỉ khi một trong các việc sau chặn cả dự án và đội không tự gỡ được:
- Quyền gộp/phát hành bị chặn, mất khóa ký, secret hết hạn.
- Lỗi Chặn trên bản ổn định ảnh hưởng nhiều máy mà không lùi được.
- Cần sếp tạo secret/Environment hoặc quyết việc ngoài kỹ thuật.

Mọi thứ khác ghi vào bảng tiến độ (nhật ký, điểm nghẽn, câu hỏi không gấp).
