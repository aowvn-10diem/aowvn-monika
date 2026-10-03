# PM phản hồi bản rà soát độc lập O03 (04/10/2026)

Nguồn: `docs/opus/ra-soat/2026-10-04-sol-ra-soat-doc-lap.md` (nhánh `tu-van/O03`, `35da9c0`).

**Kết luận của PM:** chấp nhận kết luận "xa đà". 21/23 phát hiện đồng ý hoàn toàn, 2 đồng ý một phần (R08, R17: đúng về bản chất, nhưng lúc này chỉ cần ghi nhận và kiểm cơ bản, chưa làm lớn). Phần phê bình PM cũng đúng: em đã tối ưu tốc độ thi công hơn độ tin cậy sản phẩm, V27 giao từ một báo động giả mà em không tự kiểm, và em đưa link game thương mại vào repo công khai.

## Việc đội tự làm ngay (không cần sếp quyết)

| Mã | PM | Hành động | Ai | Thứ tự |
|---|---|---|---|---|
| R12 | Đồng ý; lỗi của PM | Đã gỡ link game thương mại khỏi V25 (`KE-HOACH`). Lịch sử git còn link, **không** viết lại lịch sử main | PM | xong |
| R09 → V28 | Đồng ý, mức Cao | Che dữ liệu ở **ranh giới lưu/gửi** cho mọi trường (`title`, `reason`, `crumbs`, `detail`); test bằng chuỗi giả: Bearer, khóa RA `y=`, query key, URI, đường dẫn có dấu cách | Sol | 1 |
| R10 → V29 | Đồng ý | Thống nhất hợp đồng JSON app ↔ Worker; Worker lưu đủ `component/fingerprint/count/env/crumbs`; test gửi → lưu → đọc bằng báo cáo giả. Triển khai Worker cần quyền Cloudflare **[CHƯA KIỂM]** | Sol | 2 |
| R05 → V30 | Đồng ý | `release.yml`: thiếu khóa thì **dừng**, không ký debug; checkout đúng ref của tag; ghi hash APK + mapping vào release | Sol | 3 |
| R15, R03, R07 → V31 | Đồng ý | K10 chỉ đạt khi báo cáo có đúng `engine:rgss` + PID/phiên/thời điểm của lượt thử; nhánh dự phòng chỉ in ra để chẩn đoán | Sonnet | xen kẽ |
| R03 | Đồng ý | Đánh dấu 0.7.4 là **prerelease** cho tới khi sếp thử xong. Luật mới: bản chưa có kết quả máy thật thì luôn là prerelease | Sonnet | ngay |
| R07 (phần đội) | Đồng ý | Cập nhật mục `app` trong config (latestVersionCode/URL đang là 0.2.0) mỗi lần phát hành; sau khi Sync chạy lại, đọc lại version từ endpoint | Sonnet | sau G7 |
| V27 (mục 3) | Đồng ý; lỗi của PM | V27 đóng (báo động giả). L01 phải đọc `cores.<id>.abis` trước khi báo 404 | Luna (sửa PR #17) | ngay |
| R21 → L04 | Đồng ý | Một lô sửa tài liệu mâu thuẫn: README 0.1.0, TEST-MAY-THAT, KE-HOACH (G5/LICENSE, V08 "xong" khi chưa đo). CLAUDE.md (repo private, nhúng Azahar, chỗ ký) thì PM sửa | Luna + PM | sau R09 |
| R02 + R11 → O04 | Đồng ý, mức Cao | Bảng kiểm kê giấy phép **theo từng artifact**: engine/phụ thuộc, giấy phép, commit, bản vá, nguồn tương ứng, thông báo kèm gói. Gồm mkxp-z, krkr2yuri, Azahar, J2ME (160 file EPL 1.0), libretro cores, 7-Zip, Ren'Py. Chỉ lập hồ sơ; quyết định giấy phép là của sếp | Phiên tư vấn (O04) | song song |
| R06 → V32 | Đồng ý | Ghim phiên bản + sha256 theo ABI cho các core đã thử (GB/GBA/NES trước); có đường quay về bản tốt | Sonnet | sau Kirikiri |
| R14 → V33 | Đồng ý | Thêm 1 bài máy ảo đi đúng đường người dùng: Thư viện → nhập game → tải gói → mở → phím | Sol | sau R05 |
| R16, R18, R20, R23 | Đồng ý | Ghi vào bảng việc mức Trung bình/Thấp; **không** làm trước các việc ở trên. R20: dừng mọi tối ưu chưa có số đo | — | hoãn |
| R08 | Đồng ý một phần | Kiểm cơ bản khi nhận config từ xa (không cho lùi bản, URL phải https, hash đúng định dạng). Chưa làm ký số config | Sonnet | hoãn |
| R17 | Đồng ý một phần | Ghi mô hình tin cậy vào tài liệu; không mở dự án sandbox | — | ghi nhận |
| R19 | Đồng ý | Hoãn mở rộng repack/ADB của phần cài APK | — | hoãn |

## Cần sếp quyết (PM đề xuất)

| # | Câu hỏi | PM đề xuất |
|---|---|---|
| Q1 (R13) | Đóng băng engine mới (Ren'Py P2, Ren'Py 7, Symbian S0) cho tới khi **đọc → tải → chơi → lưu** chạy ổn trên điện thoại với Kirikiri, GB/GBA/NES và RPG Maker XP? | **Đồng ý.** Đây là xem lại thứ tự M1→M4 sếp đã chốt: giữ đích dài hạn, chỉ lùi mốc |
| Q2 (R04) | Sao lưu khóa ký: Sonnet gửi **file** khóa cho sếp qua tính năng gửi file (không dán chữ vào chat), sếp cất bản sao ngoài máy phiên và thêm vào GitHub Secrets | **Đồng ý**: mất khóa thì mọi người dùng phải gỡ app |
| Q3 (R09) | Báo lỗi tự gửi đang bật mặc định trong khi bộ che dữ liệu còn hở | Sol sửa R09 trước tiên; **bản phát hành kế tiếp mà R09 chưa xong thì tắt tự gửi mặc định** |
| Q4 (R01, R07) | Hai việc chỉ sếp làm được: xóa release `engines-rgss-6` (đã có 4 lượt tải); thêm secret `CLOUDFLARE_API_TOKEN` để Sync config chạy lại | Làm sớm |
| Q5 (mục 3) | Gọn đội: Sol và Luna chuyển sang **làm theo lô khi có việc**, bỏ tự kiểm mỗi giờ; PM kiểm 30 phút/lần | **Đồng ý**: đỡ hạn mức, ít trùng việc |
