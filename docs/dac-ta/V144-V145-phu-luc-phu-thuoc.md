## V144/V145 — Phụ lục khảo sát phụ thuộc trước khi tách kho

- Người khảo sát: Sol 2; đầu vào cho đặc tả của DeepSeek 5, chờ PM duyệt lô chuyển.
- Theo [PM 6651198574](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6651198574), [thiết kế gốc](../BAO-VE-REPO.md) và [thứ tự G3](V193-cong-bao-ve.md#7-thứ-tự-g3-pm-chốt-1244z--bắt-buộc).
- Mốc kiểm: [main@952f11ed257063da36afdf37c5be78cac5197308](https://github.com/aowvn-10diem/aowvn-monika/tree/952f11ed257063da36afdf37c5be78cac5197308). Số tệp dưới đây lấy bằng `git ls-tree -r --name-only <SHA> -- <thư-mục>`; chỉ tính tệp tracked.
- API GitHub ngày 05/11/2026 xác nhận `aowvn-10diem/aowvn-monika-noibo` tồn tại, `private=true`. Nội dung kho riêng, cấu hình vận hành và quyền của tài khoản khác [CHƯA KIỂM].

### 1. Ranh giới cần đưa vào đặc tả

Vị trí ngoài thư mục `app/` chưa đủ để quyết định chuyển riêng tư: một số tệp là đầu vào build APK hoặc test của app. [LICENSE gốc](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/LICENSE#L1) là GPLv3; chưa thấy tệp LICENSE/COPYING/NOTICE riêng trong bảy nhóm khảo sát dưới đây. Quyền sở hữu/giấy phép của từng lô cần chủ dự án xác nhận; chuyển kho không thu hồi những bản đã công khai.

| Nhóm tại mốc kiểm | Số tệp | Phụ thuộc đã kiểm / đề xuất cho đặc tả |
|---|---:|---|
| `config/` | 1 | [Gradle](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/app/build.gradle.kts#L119) đóng bản GitHub trực tiếp từ đây; [bản Play](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/app/build.gradle.kts#L130) sinh qua `scripts/play-config.py`. Giữ cấu hình công khai tối thiểu; thiết kế overlay riêng trước khi tách dữ liệu vận hành. |
| `cloudflare/` | 7 | Worker, test và fixtures; [Gradle test](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/app/build.gradle.kts#L116) dùng cả thư mục fixtures. Cần giữ fixtures công khai ở đường mới và cập nhật consumer trước khi chuyển Worker. |
| `engines/` | 4 | Lock, tài liệu và script đổi tên SDL cho RGSS. [Builder core](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/scripts/prepare-core-build.py#L439) đóng recipe/lock cùng nguồn; duy trì đường cấp nguồn và tái dựng đúng phiên bản. |
| `packs/` | 3 | Hai ZIP SevenZip và môi trường Ren'Py: mới kiểm tên tệp tracked, chưa mở ZIP. Nguồn/giấy phép/hash của từng ZIP và quyền di chuyển [CHƯA KIỂM]; cần danh mục riêng cho từng gói. |
| `scripts/` | 125 | Có script tạo config Play, cổng trước push, dựng core, kiểm APK và bot vận hành. Chia theo consumer/giấy phép từng tệp; giữ đủ script để clone công khai vẫn build/test được. |
| `tools/` | 7 | `tools/ds` và công cụ kiểm gói. [pack-check](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/tools/pack-check/build.gradle.kts#L6) biên dịch thẳng `PackTransaction.kt` của app và dùng test/resources; cần giữ hợp đồng đường dẫn với V37. |
| `.github/workflows/` | 47 | Build/test, dựng/phân phối engine và bot vận hành cùng tồn tại. Tách từng workflow sau khi đủ script/test tương ứng; giữ CI công khai chạy được PR hai flavor. |

Fixtures là phụ thuộc cụ thể: [CrashContractTest](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/app/src/test/java/vn/aow/monika/CrashContractTest.kt#L11) lấy `app-report.json` qua classloader. Xóa/chuyển cả `cloudflare/` sẽ làm mất đầu vào đó nếu chưa cập nhật resources.

### 2. Chuỗi phụ thuộc trước khi đổi đường tải

1. DS5 chốt đặc tả, danh sách tệp/giấy phép/consumer và người nhận từng PR; phụ lục này làm đầu vào cho bước đó.
2. Làm PR giữ fixtures và các đầu vào build/test cần công khai ở đường ổn định, cập nhật mọi consumer và kỳ vọng test chính xác.
3. Chuẩn bị bản sao các lô vận hành đã được duyệt trong kho riêng bằng PR; ghi nguồn SHA và cách chạy. Gói native giữ hash, nguồn tương ứng, recipe/lock, giấy phép và symbols cùng BuildId.
4. Kiểm đường tải G3 thật trên hạ tầng được PM/sếp triển khai; bản app đang cài tiếp tục lấy được gói. Sau đó mới làm PR config riêng, tăng configVersion theo main.
5. Chạy đủ cổng và kiểm bản app chuyển đổi; PM/sếp quyết định phát hành/đổi quyền kho. Cuối cùng mới dọn các tệp công khai đã có bản thay thế được kiểm, qua PR thông thường.

[G3 Worker](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/cloudflare/crash-worker/worker.js#L240) đã có mã trả 302 tới host được phép; #502 gộp tại `688692a052a55ce9def16f6748d296ae23f34ee9`. Đây là bằng chứng mã, còn deploy/cold download tới kho riêng trên máy người dùng [CHƯA KIỂM]. Cần giữ thứ tự đã chốt trong V193: đường mới → config → app chuyển đổi → sếp đổi quyền kho; giữ đường cũ trong thời gian chuyển.

PPSSPP: #508 gộp `952f11ed257063da36afdf37c5be78cac5197308`, chuẩn bị [nguồn/symbols theo ABI](https://github.com/aowvn-10diem/aowvn-monika/blob/952f11ed257063da36afdf37c5be78cac5197308/scripts/ppsspp-snapshot.py#L121). Theo PM, workflow đăng còn chờ sếp dispatch; snapshot công khai tải lại/URL/hash và game thật [CHƯA KIỂM]. Chỉ ghim config sau khi kiểm gói đã đăng; digest artifact Actions không thay SHA256 ZIP cài.

### 3. Cổng nghiệm thu cho các PR di chuyển sau này

- [ ] Clone công khai sạch, không quyền kho riêng, vẫn chạy `testDebugUnitTest`, build debug hai flavor và quét APK Play bằng cổng repo; không bỏ test hoặc nới điều kiện để né đường dẫn bị mất.
- [ ] Test/resources của CrashContractTest vẫn lấy đúng fixture; script tạo config Play và pack-check dùng đúng nguồn/test app.
- [ ] Gói tải qua đường mới có SHA256/size/ABI đúng; sai hash không được cài, mất mạng không làm hỏng gói/cache đang dùng; đo số request thực tế trước/sau chuyển.
- [ ] Có nguồn tương ứng, recipe/lock, giấy phép, symbols/BuildId lấy được cho mỗi bản native được phân phối, theo nghĩa vụ của giấy phép từng gói.
- [ ] Bản đang cài tải được trong giai đoạn chuyển; đường cấp nguồn/rollback và người thao tác đổi quyền/phát hành được PM chốt.
- [ ] Mô tả PR ghi rõ phạm vi Play, giấy phép và CI trên đúng head; chỉ đưa dữ liệu giả vào test, không chép dữ liệu vận hành hay giá trị bí mật.

### 4. Phạm vi lần khảo sát

Bản Play: tài liệu; giữ nguyên mã/config/APK của cả hai flavor. Config: không đổi, `minVersionCode` giữ nguyên; chưa di chuyển/xóa tệp vận hành, đổi quyền kho, triển khai Worker hay chạy workflow phát hành.

Kiểm tài liệu bằng cây Git/consumer ở SHA trên, link nội bộ và guard V113. Android CI/Gradle N/A cho diff chỉ `docs/**` theo V11; các cổng mã ở mục 3 vẫn bắt buộc khi triển khai. Phân loại giấy phép cuối cùng, hạ tầng thật, tần suất/chi phí Worker và máy thật [CHƯA KIỂM].
