# Thiết kế lại Thư viện Aow Monika

Soạn 20/10/2026, trên `main` @ `4f59179`. Chỉ nghiên cứu + đề xuất, chưa viết mã app. Số dòng/tên hàm lấy từ code thật; chỗ chưa kiểm ghi **[CHƯA KIỂM]**.

## 1. Kết luận

[A56] Thư viện hiện là **một màn 740 dòng làm hết mọi việc** (`ui/screens/LibraryScreen.kt`). Mọi tính năng sếp nêu đều ĐÃ CÓ ở dạng mảnh, nhưng nằm rải và khó tìm. Đề xuất: không viết lại từ đầu, mà **đổi kiến trúc thông tin**:

1. Thư viện chia **3 mục**: **Chơi** (game) · **Đọc** (tài liệu) · **Tệp** (quản lý file + giải nén).
2. Mỗi game có **trang chi tiết riêng** (thay menu sheet), chứa Save, Bản vá, Tệp của game.
3. Gom mọi việc đang chạy (tải, giải nén, nhập, vá) vào **một thanh tác vụ**, không chen vào lưới game.
4. Chỉ **một nút "+ Thêm"** cho mọi cách đưa đồ vào Thư viện.

## 2. Hiện trạng (có bằng chứng)

| Năng lực | Đang ở đâu | Điểm yếu |
|---|---|---|
| Đọc/chơi game | Lưới 2 cột, `LibraryScreen.kt` (ô `GameTile`, thẻ `ContinueCard`) | Lưới trộn: thẻ cấp quyền, thẻ "game lần cài trước", ô tác vụ, ô game, rồi tài liệu cuối trang |
| Lọc | 3 chip: Tất cả / Gần đây / Thường xuyên + chọn hệ máy bằng sheet; ô tìm `GameSearch` | Không lọc theo trạng thái (chưa giải nén, đã dọn, cần quyền), không sắp xếp |
| Tài liệu | `LibraryDocuments.kt`: chỉ nhận **thư mục toàn file** đuôi `documentExtensions` (pdf doc docx txt epub); mở bằng app ngoài (`documentViewIntent`) | Nằm cuối lưới game; tài liệu đi kèm game bị bỏ; không đọc trong app; chỉ xóa, không đổi tên/di chuyển |
| Giải nén | Nút "Giải nén" chỉ hiện khi `needsExtract` (game có kho chưa mở được), hoặc tự chạy lúc nhập (`Importer`, `ImportWorker`) | Không giải nén được file nén **bất kỳ** (không phải game); mật khẩu chỉ hỏi ở dialog trong game |
| Quản lý file | **Chưa có.** Chỉ: xóa game, ẩn game quét được (`external`), đường dẫn hiện ở đầu menu game, `StorageCleaner` ở Cài đặt | Không duyệt thư mục, đổi tên, chép, di chuyển, xem dung lượng từng thứ |
| Vá Việt hóa | Menu game → "Vá Việt hóa (IPS/BPS/UPS)" (`patch/PatchFlow.kt`), **chỉ hệ libretro**, người dùng tự chọn file bản vá | Khó tìm (nằm trong menu ⋯); không có bản vá gợi ý theo game (config không có khóa `patch*`); tạo bản "(Việt hóa)" thành game riêng, dễ nhầm với bản gốc |
| Lấy/nhập save | Menu game → đầu sheet: nút Xuất/Nhập (`GameSaveUi.kt`); chỉ `java`, `nds`, `gba` (`GameSaveTransfer.supported`). Ngoài ra `SaveVault` (xuất cả kho) nằm ở Cài đặt | Hai nơi, hai kiểu; hệ khác chưa có (đợt 2 [A54] đang làm); không thấy danh sách bản sao lưu cũ |
| Tải/giải nén đang chạy | `GameTasks` → ô `TaskTile` chen trong lưới; tab Tải xuống nằm ở Hub (`HubScreen`: Tải xuống/Cài đặt) | Tiến độ ở hai nơi |
| Đặt tên | Dock ghi "Thư viện" (`MainActivity.kt` dockItems); route tên `EMULATOR`; comment đầu màn ghi "Màn Giả lập"; menu có "Cài đặt giả lập" | Lẫn khái niệm Thư viện / Giả lập |
| Nhập từ app khác | `AppGraph.pendingImports` → `importAll(autoPlay)` | Tốt, giữ nguyên |
| Khóa quyền | Thẻ `LibraryStorageAccess` + thẻ "game lần cài trước" | Chiếm đầu trang dù đa số người dùng đã xử lý xong |

Hai khái niệm game dễ gây nhầm: game **của Monika** (xóa = xóa thật) và game **quét được** (`external`, xóa = ẩn). Người dùng chỉ thấy hai chữ "Xóa"/"Ẩn" khác nhau lúc bấm.

Khớp với việc đã có: V122 (sếp báo "thư viện loạn" 14/10), V132 (thư viện chưa làm mới theo `configVersion`), [A44] dọn tự động không bao giờ xóa game, [A54] save, V112/V116. Thiết kế này **không đổi** logic `GameLibrary`/`GameDetector`/`Importer`/`StorageCleaner`; chỉ đổi lớp giao diện và thêm vài hàm mới.

## 3. Kiến trúc thông tin đề xuất

```
Thư viện (tab dock)
├─ Thanh trên:  [Tìm]  [Tác vụ (n)]  [+ Thêm]
├─ Mục "Chơi"   lưới game + Tiếp tục chơi + lọc/sắp xếp
├─ Mục "Đọc"    danh sách tài liệu (PDF đọc ngay trong app)
└─ Mục "Tệp"    duyệt kho Monika: giải nén, đổi tên, xóa, dung lượng

Trang game (bấm vào ô)
├─ Chơi · thông tin (hệ, lõi, thời gian) · bài viết
├─ Save      xuất · nhập · danh sách bản sao lưu · nhập từ giả lập ngoài
├─ Bản vá    bản vá gợi ý + chọn file tay
└─ Tệp       thư mục game, giải nén lại, xóa/ẩn
```

Nguyên tắc: (a) mỗi việc có **một** chỗ; (b) việc nguy hiểm (xóa, nhập đè save) luôn qua xác nhận + sao lưu; (c) mọi thứ đổi được nằm trong config; (d) dùng đúng `Monika.*`, `Components.kt` (luật giao diện của dự án).

### 3.1 Nút "+ Thêm" (một cửa duy nhất)
Sheet gồm: Thêm file từ máy (game, file nén, tài liệu: app tự phân loại) · Quét cả máy · Mở thư mục (SAF) · Cho phép đọc mọi tệp (khi chưa có). Thẻ cấp quyền đầu trang biến mất; trạng thái quyền chỉ hiện ở sheet này và ở mục Tệp.

### 3.2 Thanh tác vụ
`GameTasks` + lượt tải từ `DownloadManager` hiện trong một sheet "Tác vụ (n)": tải, giải nén, nhập, vá, chuyển save. Có hủy. Ô `TaskTile` trong lưới bỏ đi. Tab Tải xuống ở Hub giữ cho lịch sử/dung lượng.

### 3.3 Mục Chơi
Giữ `ContinueCard` và lưới. Thêm: sắp xếp (gần đây, tên, hệ, dung lượng); lọc trạng thái (cần giải nén, đã dọn, cần quyền); đánh dấu rõ nguồn game (Monika / quét trong máy) bằng một nhãn nhỏ. Đa số trạng thái lạ (chưa giải nén, khóa) hiện thành **một dải cảnh báo mỏng**, không phải thẻ lớn.

### 3.4 Mục Đọc
Gom mọi tài liệu (cả PDF kèm game, hiển thị kèm tên game). PDF mở **trong app** bằng `android.graphics.pdf.PdfRenderer` (có sẵn hệ thống, không thêm thư viện, hợp luật "app nhẹ"); txt đọc trong app; epub/doc/docx vẫn mở app ngoài. **[CHƯA KIỂM]** chất lượng PdfRenderer với PDF hướng dẫn thật của aow.vn: cần thử vài file mẫu trước khi chốt.

### 3.5 Mục Tệp (quản lý file + giải nén)
Phạm vi gọn, **không phải trình quản lý file đầy đủ**: duyệt `GameStorage.games()`, thư mục đã quét, và thư mục Download khi có quyền. Thao tác: giải nén (zip/rar/7z/nhiều phần, hỏi mật khẩu, dùng lại `ArchiveExtractor`), mở như game/tài liệu, đổi tên, xóa (có xác nhận), xem dung lượng, chia sẻ. Không đụng `Android/data`, không root. Một file nén bất kỳ giải ra thư mục và hỏi "Nhận là game hay tài liệu?" nếu `GameDetector` không chắc.

### 3.6 Trang game
Thay `GameMenuSheet` (hiện nhét 10 hành động trong một sheet) bằng trang chi tiết. Menu ⋯ ngoài lưới chỉ còn: Chơi, Mở trang game, Xóa/Ẩn. Trang gồm:
- **Save**: gom `GameSaveUi` (Xuất/Nhập), liệt kê bản sao lưu tự tạo khi nhập, nút "Nhập từ giả lập khác" (hướng dẫn chọn đúng thư mục/đuôi theo hệ, theo [A54]). Hệ nào chưa hỗ trợ thì hiện rõ "Chưa hỗ trợ" thay vì ẩn nút. `SaveVault` (xuất cả kho) chuyển từ Cài đặt sang đây ("Sao lưu toàn bộ").
- **Bản vá**: danh sách bản vá gợi ý nếu config có (xem Q4), nút chọn file `.ips/.bps/.ups`; kết quả hiện là bản "(Việt hóa)" ngay bên dưới ROM gốc trong cùng trang, không phải một ô game rời; hiện rõ "ROM gốc giữ nguyên".
- **Tệp**: đường dẫn, dung lượng, giải nén lại, xóa/ẩn.

## 4. Luồng người dùng chính

| # | Việc | Luồng đề xuất (số bước) |
|---|---|---|
| 1 | Chơi game | Mở Thư viện → bấm "Tiếp tục chơi" hoặc "Chơi" trên ô (1–2) |
| 2 | Thêm game vừa tải về | Tải xong → thanh tác vụ tự giải nén → game hiện, có thông báo "Chơi ngay" (0 thao tác thêm) |
| 3 | Thêm game có sẵn trong máy | + Thêm → chọn file/quét → game hiện (2–3) |
| 4 | Đọc tài liệu | Mục Đọc → bấm tài liệu → đọc ngay (2) |
| 5 | Giải nén file bất kỳ | Mục Tệp → bấm file nén → Giải nén → nhập mật khẩu nếu cần (3) |
| 6 | Dọn/xóa/đổi tên | Mục Tệp (hoặc trang game → Tệp) (2–3) |
| 7 | Cài Việt hóa | Trang game → Bản vá → chọn bản vá → xác nhận (3–4) |
| 8 | Lấy save | Trang game → Save → Xuất (2–3) |
| 9 | Nhập save (kể cả từ giả lập khác) | Trang game → Save → Nhập → xem trước + sao lưu cũ → xác nhận (4) |

## 5. Lộ trình chia việc (mã tạm TV-*, chờ PM đánh số chính thức)

| Mã | Việc | Người đề xuất | Phụ thuộc | Ghi chú |
|---|---|---|---|---|
| TV-0 | Tách `LibraryScreen.kt` (740 dòng) thành các tệp nhỏ, **không đổi hành vi**; thêm test Robolectric cho các phần tách | Sol | — | Làm trước để các việc sau không đụng nhau |
| TV-1 | Khung 3 mục + thanh tác vụ + nút "+ Thêm" (gom đường nhập hiện có) | Nova | TV-0 | Giữ `pendingImports`, `scanner`, `Importer` |
| TV-2 | Trang game (thay `GameMenuSheet`) | Nova | TV-1 | Chưa gồm Save/Bản vá |
| TV-3 | Save trong trang game, liệt kê bản sao lưu, chuyển `SaveVault` vào | Sol | [A54] đợt 2 xong, TV-2 | Tránh sửa song song `GameSaveUi` |
| TV-4 | Mục Tệp + giải nén file bất kỳ | Sol 2 | TV-1 | Dùng lại `ArchiveExtractor`, `GameStorage` |
| TV-5 | Mục Đọc: PdfRenderer + txt | Nova | TV-1, thử mẫu PDF | Giữ nguyên luật không thêm thư viện |
| TV-6 | Bản vá trong trang game; (nếu chốt Q4) khóa config `patches` + `ConfigTest` | Sol | TV-2, Q4 | Tăng `configVersion`, đồng bộ Cloudflare |
| TV-7 | Làm mới danh sách theo `configVersion` (đã là V132) | Sol | — | Gộp vào TV-1 nếu V132 chưa xong |

Mỗi việc chạy `./gradlew testDebugUnitTest` trước push; Luna 3 → Luna Ultra duyệt như thường lệ. Bản đầu dùng được: TV-0 + TV-1 + TV-2 (đã giải quyết "loạn"); Save/Bản vá/Tệp/Đọc ra dần.

## 6. Rủi ro

| Rủi ro | Cách giảm |
|---|---|
| Đổi UI lớn làm hỏng luồng chơi game đang ổn | TV-0 tách không đổi hành vi + test chụp UI trước khi đổi; bật theo từng PR nhỏ |
| Quản lý file lộ thao tác xóa nhầm | Chỉ xóa trong kho Monika/thư mục người dùng chủ động chọn; mọi xóa có xác nhận; [A44] giữ nguyên (không tự dọn game) |
| Nhập save đè mất save | Giữ cơ chế hiện có (sao lưu trước, xem trước, `GameSaveGate`); thêm danh sách bản sao để khôi phục |
| Đụng việc Sol/Nova đang làm (save đợt 2, NDS, tay cầm) | Xếp TV-3 sau [A54] đợt 2; TV-0/1 không động vào `GameSaveTransfer`, `runner/` |
| Máy yếu / app nặng thêm | Không thêm thư viện; PdfRenderer có sẵn; duyệt tệp làm lười, theo trang |

## 7. Câu cần sếp quyết

| # | Câu hỏi | Đề xuất của em |
|---|---|---|
| Q1 | Chia Thư viện thành 3 mục Chơi / Đọc / Tệp? | **Có.** Phương án khác: giữ một trang, chỉ thêm bộ lọc |
| Q2 | Quản lý file đi tới đâu? | **Gọn:** kho Monika + Download (khi có quyền); không `Android/data`, không root |
| Q3 | Đọc PDF ngay trong app? | **Có**, bằng PdfRenderer có sẵn; epub/doc mở app ngoài |
| Q4 | Bản vá Việt hóa: có kho bản vá chính thức (config) để app gợi ý theo game? | **Có** nếu aow.vn có sẵn link bản vá theo game; nếu chưa, TV-6 chỉ làm giao diện chọn file tay. Em chưa biết aow.vn có kho đó không, cần sếp xác nhận |
| Q5 | Đổi tên hiển thị thống nhất "Thư viện", bỏ chữ "Giả lập" trong menu ("Cài đặt giả lập" → "Cài đặt")? | **Có** |
| Q6 | Game quét được trong máy: gộp "Xóa/Ẩn" thành một nút "Gỡ khỏi Thư viện" + ghi rõ file có bị xóa không? | **Có** |
| Q7 | Thứ tự làm: sau [A54] đợt 2 và [A55], hay chen TV-0/TV-1 ngay (không đụng save/runner)? | **Chen TV-0 ngay** (rủi ro thấp), phần còn lại sau [A54] |

## 8. Chưa kiểm
- Chưa chạy app trên máy thật; mọi nhận xét giao diện suy từ code.
- Chưa đọc hết 548 bình luận của issue #119; chỉ đối chiếu V122, V132, [A54], [A44] theo bộ nhớ dự án. Nếu Luna Ultra đã đề xuất cấu trúc khác cho V122, cần PM đối chiếu trước khi giao việc.
- Chưa thử PdfRenderer với PDF hướng dẫn thật.

## 9. Quyết định của sếp (20/10 07:54 UTC)

[A56] Chốt hướng thiết kế Thư viện ở mục 1.
[A57] Q1 chia 3 mục Chơi / Đọc / Tệp: OK. Q2 quản lý file gọn (kho Monika + Download): OK. Q5 thống nhất tên "Thư viện", bỏ chữ "Giả lập": OK. Q6 gộp Xóa/Ẩn thành "Gỡ khỏi Thư viện": OK. Q7 chen TV-0/TV-1 ngay, Save sau [A54] đợt 2: OK.
[A58] Q3 đọc PDF trong app: làm tạm bằng PdfRenderer. **Tách thành một module riêng** (giao diện trình đọc nằm sau một interface, ví dụ `DocumentReader`) để sau thay/tối ưu mà không đụng phần còn lại. Cập nhật TV-5.
[A59] Q4 có kho bản vá theo game (config-first, khóa `patches`, gợi ý theo game) **nhưng người dùng luôn chọn file tay được**. Cập nhật TV-6: làm cả hai đường; nguồn bản vá còn cần sếp/aow.vn cung cấp danh sách, chưa có thì phát hành chọn tay trước.

## 10. Số việc chính thức (PM, 20/10)

| Mã tạm | Việc | Người làm |
|---|---|---|
| TV-0 | V137 | Sol |
| TV-1 | V138 | Nova |
| TV-2 | V139 | Nova |
| TV-3 | V140 | Sol |
| TV-4 | V141 | Sol 2 |
| TV-5 | V142 | Nova |
| TV-6 | V143 | Sol |
| TV-7 | V132 (đã có) | Sol |
