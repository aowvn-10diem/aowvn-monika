## V144/V145 — Tách phần vận hành sang repo riêng tư

- Người soạn: Sol 2 hỗ trợ DeepSeek 5; thi công từng lô theo PM phân công.
- Nguồn: [PM 6656567609](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6656567609), [thiết kế gốc](../BAO-VE-REPO.md), [phụ lục đã gộp #509](V144-V145-phu-luc-phu-thuoc.md), [thứ tự G3](V193-cong-bao-ve.md#7-thứ-tự-g3-pm-chốt-1244z--bắt-buộc).
- Trạng thái: Nháp; chờ DeepSeek 5/PM chốt danh sách lô và người thi công trước PR mã.

### 1. Mục tiêu
Người chơi bản chính thức tiếp tục tải gói và dùng bản đã cài khi mất mạng. Repo công khai vẫn đủ đầu vào để build/test app; phần vận hành được duyệt tách sang repo riêng tư, gói native có nguồn tương ứng và giấy phép lấy được.

### 2. Ngoài phạm vi
Không sửa save/phím/bố cục, tăng minVersionCode, viết lại lịch sử hay chuyển mọi thư mục ngoài `app/` theo tên. Phát hành, triển khai, đổi quyền kho và xử lý giá trị bí mật/dữ liệu vận hành thật thuộc sếp/PM theo quy trình đã có; giao thức G3 và các việc đang giao phiên khác là phụ thuộc.

### 3. Tiêu chí đạt
- [ ] Có danh sách cho mỗi lô: đường nguồn + SHA, consumer build/test/runtime, quyền/giấy phép, nơi cấp nguồn tương ứng, phạm vi PR và người nhận; PM/sếp chốt trước khi chuyển.
- [ ] Clone công khai sạch, không quyền repo riêng, chạy đủ cổng test GitHub/Play, assembleDebug, assemblePlayDebug và scanner Play; test/bản config dự phòng không phụ thuộc kho riêng.
- [ ] Hai fixture giả đang ở `cloudflare/crash-worker/fixtures` được giữ ở resources công khai của app; `app-report.json` vẫn ở gốc classpath cho CrashContractTest, test Worker lấy được bản cùng SHA.
- [ ] `scripts/play-config.py`, cổng trước push, pack-check và nguồn/test app mà chúng dùng còn chạy được; CI PR công khai không cần secret hay quyền đọc kho riêng để kiểm mã app.
- [ ] Mỗi gói native đối chiếu SHA256/size/ABI, giữ nguồn/recipe/lock/giấy phép và symbols cùng BuildId; đường cấp nguồn tương ứng được PM/sếp duyệt theo giấy phép từng gói.
- [ ] Gói qua đường tải mới được kiểm thật sau triển khai: tải mới/cài mới, cache sẵn, offline, hash sai và gián đoạn; hash sai không cài, lỗi không làm mất gói đang dùng.
- [ ] Chuyển dữ liệu vận hành qua schema/mẫu giả trước; dữ liệu thật do người có thẩm quyền nạp ngoài repo/APK. Trường vắng dùng mặc định an toàn, cache/fallback và thông tin bản Play có test.
- [ ] Có bằng chứng CI đúng head ở cả hai phía cho từng lô, manifest ánh xạ nguồn SHA và điểm quay lui; chỉ dọn bản công khai sau khi consumer mới được nghiệm thu.

### 4. Bản Play
Giữ quy tắc V150: không thêm tải game/gói, tự cập nhật APK hay link tải/ủng hộ. Mọi PR đụng đường build/config phải chạy kiểm cả hai flavor và scanner Play; PR đặc tả chỉ tài liệu.

### 5. Config
PR đặc tả không đổi config. PR triển khai dùng config-first; có đổi JSON mới tăng configVersion theo main lúc làm, cập nhật test chính xác, giữ minVersionCode=1. Tên/schema mới phải mô tả giá trị mặc định trong chính PR mã; không điền URL giả hoặc giá trị vận hành thật vào mẫu công khai.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | Người/lô |
|---|---|---|---|
| 1 | Đặc tả này + danh sách lô/giấy phép theo phụ lục #509 | — | Sol 2 soạn; DeepSeek 5/PM chốt lô |
| 2 | Giữ fixture/resources và các đầu vào build/test chung ở repo công khai; sửa mọi consumer, test giữ nguyên hợp đồng | 1 được duyệt | PM phân công; một PR nhỏ |
| 3 | Sao chép từng lô mã/schema/mẫu giả đã duyệt sang repo riêng tư bằng PR, kèm nguồn SHA và test/CI của lô | 1 được duyệt, 2 | DeepSeek 5 phối hợp Sol 2; mỗi lô một PR |
| 4 | Cập nhật consumer/CI theo đường mới đã kiểm, giữ kiểm app công khai độc lập; đối chiếu đầu ra trước/sau | 2, 3 | PM chốt lô và người nhận |
| 5 | PR client/config chuyển tiếp theo schema/đường tải đã nghiệm thu, test fallback/cache/Play; configVersion +1 khi đổi | 4, G3 triển khai và tải thật đạt | PM phân công; không tự đồng bộ/phát hành |
| 6 | Dọn bản vận hành công khai thuộc lô đã duyệt, giữ nghĩa vụ cấp nguồn; chỉ bỏ đường cũ khi PM chốt chuyển đổi đủ | 3–5 đạt, PM xác nhận bản chuyển đổi | PM duyệt lô; merge thông thường |

### 7. Rủi ro, quay lui và vận hành
- LICENSE gốc GPLv3; vị trí ngoài APK không xác định quyền sở hữu. Quyền/giấy phép riêng từng lô [CHƯA KIỂM]; PM/sếp chốt đường cấp nguồn và quyền chuyển, không coi việc đổi kho là thu hồi bản đã công khai.
- Giữ save/phím/bố cục và gói/cache đã cài; không có tác vụ dọn dữ liệu người chơi trong lộ trình này. Bản cũ và mới cùng tải được trong thời gian chuyển theo V193.
- Điểm quay lui mỗi lô là SHA đã ghi trong manifest: PR sửa tiếp khôi phục đường cũ/bản công khai và test; giữ đường cũ tới khi PM chốt. Lỗi hạ tầng do người vận hành quay lui theo bản đã lưu, không xóa/reuse tag hay sửa lịch sử.
- Không thêm lượt Worker khi mở app; chỉ giải đường tải lúc cần cài/cập nhật, dùng cache, tệp lớn đi thẳng CDN/R2. Đo request/byte và tải đỉnh mục tiêu 1000 người đồng thời trên hạ tầng được PM/sếp cho thử; hạn mức/chi phí/kết quả thật [CHƯA KIỂM].
- PPSSPP có workflow chuẩn bị snapshot ở #508; việc đăng đang chờ sếp. Chỉ ghim URL/hash sau khi kiểm gói đã đăng; digest artifact Actions không thay SHA256 ZIP cài. Không dispatch hay triển khai từ PR đặc tả.
- Hiện mới có phụ lục khảo sát #509; danh sách lô được chuyển, hạ tầng/đường tải mới và máy thật [CHƯA KIỂM]. Sau duyệt, mỗi PR mã dẫn đặc tả, giữ đủ cổng kiểm, Luna 3 → Luna Ultra rồi PM gộp.
