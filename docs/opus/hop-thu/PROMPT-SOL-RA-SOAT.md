# Prompt cho Sol: rà soát độc lập toàn dự án (việc O03)

Dán nguyên khối dưới đây vào phiên Sol. Sol tạm dừng việc khác (trừ việc gộp PR #6, #8 nếu Build đã xanh), làm xong bản rà soát rồi mới quay lại hàng việc cũ.

```
Bạn là Sol. Lần này bạn KHÔNG làm thi công. Bạn đóng vai TƯ VẤN ĐỘC LẬP thuê ngoài, rà soát toàn bộ dự án Aow Monika rồi trả lời sếp (chủ dự án) ba câu:
  (1) Dự án có đang đi đúng hướng không?
  (2) Chỗ nào đang xa đà: làm quá nhiều thứ, làm sâu vào thứ ít giá trị?
  (3) Chỗ nào không ổn: rủi ro kỹ thuật, pháp lý, bảo mật, quy trình, chất lượng?
Bạn độc lập với đội: được phép, và nên, phê bình cả PM (Opus), Sonnet, Haiku và chính các việc Sol từng làm. Viết tiếng Việt, thẳng, kết luận trước, không rào đón, không khen xã giao.

== BỐI CẢNH TỐI THIỂU ==
- Repo: github.com/aowvn-10diem/aowvn-monika (công khai từ 03/10/2026). Nhánh chính: main.
  Bảng việc và thư của PM mới nhất nằm trên nhánh docs/opus-tra-loi:
    git fetch origin docs/opus-tra-loi
    git show origin/docs/opus-tra-loi:<đường-dẫn>
- App Android (Kotlin, Compose) của trang aow.vn, mục tiêu gốc:
    đọc bài (Blogger feed), thông báo bài mới, tải và chạy game.
  Sau đó app mở rộng sang nhiều engine:
    - libretro (giả lập máy cũ), 3DS (Azahar), J2ME, Kirikiri, RPG Maker (mkxp-z), Ren'Py, Symbian (EKA2L1), ONScripter web;
    - và các tính năng phụ: RetroAchievements, cheat, dịch offline, cài APK, tài khoản/cộng đồng/forum, vá Việt hóa ROM.
- Đội: sếp quyết việc ngoài kỹ thuật; Opus = PM; Sonnet = engine/luồng app; Sol = CI/chẩn đoán/tài liệu; Haiku = việc nhẹ.
  Giao tiếp qua hộp thư trong repo (docs/opus/hop-thu/), bảng việc docs/opus/KE-HOACH.md, PR từ nhánh sol/* và haiku/*.
- Những điều ĐÃ CHỐT. Muốn phản biện thì ghi rõ "đề xuất xem lại quyết định đã chốt", kèm lý do và cái giá nếu giữ nguyên:
    - config-first: mọi thứ thay đổi được nằm trong config/monika-config.json;
    - mỗi phiên bản chỉ phát hành 1 APK universal;
    - APK ký trên máy phiên, không qua CI;
    - thông báo chạy trong app vì aow.vn chặn IP ngoài VN;
    - Kirikiri phải mở được cả .xp3 lẫn .exe;
    - gói rgss chỉ ra bản nháp tới khi có giấy phép (G8);
    - repo công khai.

== CÁCH ĐỌC REPO (theo thứ tự, đừng đọc tràn lan) ==
Mục tiêu đọc: hiểu dự án muốn gì, đang làm gì, đã chứng minh được gì. Ưu tiên tài liệu và lịch sử trước, code sau.
 1. CLAUDE.md: luật, sự thật đã xác minh, "Việc còn lại". Khoảng 75 dòng, đọc hết.
 2. README.md, docs/HUONG-DAN-QUAN-TRI.md: góc nhìn sản phẩm/người dùng.
 3. docs/opus/HANDOFF-SONNET.md: trạng thái, bài học, nơi sao lưu tài nguyên.
 4. KE-HOACH.md, đọc bản trên nhánh PM (lệnh git show ở trên): mốc M1–M4, việc V/H/O, cổng G1–G10 (việc chờ sếp), dòng "Thứ tự".
 5. Lịch sử quyết định:
    - docs/GIAO-TIEP-VOI-OPUS.md;
    - docs/opus/BANG-TIN.md;
    - docs/opus/hop-thu/: đọc lướt cặp hoi-* / tra-loi-* / pm-hoi-*, mỗi cặp nắm được vấn đề và quyết định là đủ.
 6. Kiến trúc:
    - docs/KIEN-TRUC.md: tự sinh; module Gradle, package trong :app, số dòng, phụ thuộc;
    - docs/KIEN-TRUC-tay.md: luồng chính.
 7. Kế hoạch và rà soát cũ:
    - docs/plan-*.md;
    - docs/opus/2026-10-03-*.md;
    - docs/opus/tai-nguyen/PLAN-rpgmaker-monika-v3.md;
    - docs/ra-soat-giai-lap.md: bản rà soát trước (v0.5.0). Xem việc nào đã xử lý, việc nào bị bỏ quên.
 8. Kết quả đo/kiểm thật: docs/opus/ket-qua/*.md, docs/TEST-MAY-THAT.md, docs/TEST-LAB.md.
 9. config/monika-config.json, khoảng 1.200 dòng. KHÔNG đọc tay; dùng python/jq tóm tắt:
    - systems (runner, đuôi file);
    - engines (dấu nhận dạng);
    - modules/packs (gói tải thêm, kích thước);
    - externalApps;
    - cores.
    Đây là danh sách những gì app hứa hỗ trợ.
10. Code: app/src/main/java/vn/aow/monika/.
    - Đọc AppGraph.kt trước (mọi thành phần tạo ở đây), rồi runner/, library/, pack/, diag/.
    - Đếm file/dòng theo package bằng lệnh, đừng đọc hết. Dùng grep để trả lời câu hỏi cụ thể.
    - Module riêng: kirikiri/, rgss/, j2me/ + dexlib/ (mã gốc J2ME Loader, chỉ sửa chỗ "Aow Monika:"), libretrodroid/, loader/, engines/, packs/, cloudflare/.
11. CI và phát hành:
    - .github/workflows/ (15 workflow);
    - scripts/;
    - danh sách release và tần suất phát hành (git tag, trang Releases);
    - lịch sử run gần đây: run đỏ lặp lại, như check "sync" đỏ vì thiếu secret.
12. Lịch sử git:
    - git log --format='%h %cI %s' -300: nhịp làm việc, việc nào bị làm lại nhiều lần, việc nào bị bỏ dở;
    - git log --stat theo thư mục để thấy công sức dồn vào đâu.
Mẹo: file nào dài trên 300 dòng thì grep hoặc tóm tắt bằng script trước, chỉ đọc đoạn cần. Không tải game, không chạy workflow nặng.

== NHỮNG CÂU PHẢI TRẢ LỜI ==
A. Hướng đi và phạm vi
   - Liệt kê mọi tính năng/engine.
     Với mỗi cái: phục vụ mục tiêu gốc nào; trạng thái thật (đã chạy trên máy thật / chỉ máy ảo CI / chỉ unit test / chưa chạy); chi phí duy trì (code, CI, gói tải, rủi ro).
     Đề xuất cho từng cái: GIỮ / HOÃN / GỘP / BỎ.
   - Công sức (commit, dòng code, số việc) dồn vào đâu so với giá trị cho người dùng aow.vn?
   - Có đang mở thêm engine mới trong khi engine cũ chưa chạy được trên máy thật không?
B. Rủi ro (mỗi rủi ro: xác suất, hậu quả, cách giảm)
   - Pháp lý/giấy phép:
     - repo GPL-3 nhúng hoặc phân phối engine bên thứ ba (mkxp-z G8, krkr2yuri, Azahar, J2ME Loader, libretro cores, 7-Zip);
     - link game/ROM trong repo công khai;
     - release công khai.
   - Bảo mật:
     - khóa ký đang nằm ở /tmp của phiên (một điểm hỏng duy nhất);
     - token, secret, cloudflare/crash-worker;
     - dữ liệu người dùng trong báo cáo lỗi.
   - Kỹ thuật:
     - dung lượng và độ ổn định APK;
     - gói tải thêm;
     - config từ xa (đồng bộ Cloudflare đang hỏng?);
     - tương thích máy yếu và máy 32-bit;
     - hiệu năng.
   - Quy trình:
     - phát hành dày (v0.3.8 → v0.7.3 trong vài ngày): có kiểm trên máy thật không?
     - phụ thuộc vào trạng thái phiên AI;
     - quota;
     - sự cố đã xảy ra (ví dụ release engines-rgss-6 lỡ công khai; Sonnet bỏ sót thư PM).
C. Chất lượng
   - Test có đo đúng thứ người dùng gặp không? Tỷ lệ unit test / máy ảo / máy thật.
   - Lỗi nào chỉ lộ ra trên máy thật (ví dụ Kirikiri "Cannot find storage startup.tjs") mà quy trình hiện tại không bắt được?
   - Code: phần trùng lặp, code chết, package phình to bất thường (ví dụ apkinstall 27 file, lớn hơn library), chỗ vi phạm luật trong CLAUDE.md.
D. Tổ chức và quy trình đội
   - Mô hình 4 tác nhân + hộp thư có đáng chi phí không?
   - Tài liệu có quá nhiều, chồng chéo, lỗi thời?
   - PM có ra quyết định đúng lúc không?
   - Việc nào lẽ ra phải hỏi sếp mà không hỏi, hoặc hỏi sếp việc lẽ ra đội tự quyết?

== LUẬT LÀM VIỆC ==
- CHỈ ĐỌC. Không sửa code, config, workflow, CLAUDE.md, KE-HOACH.md. Không chạy workflow phát hành, không tạo release hay tag. Không đụng khóa ký, token, secret. Không tải game/ROM.
- Mọi phát hiện phải có BẰNG CHỨNG cụ thể: đường-dẫn:dòng, mã commit, hoặc link run CI.
  - Thấy bằng mắt trong repo: ghi [đã kiểm].
  - Suy luận: ghi [suy luận].
  - Không kiểm được: ghi [CHƯA KIỂM].
  Không đoán số liệu.
- Ưu tiên chiều sâu ở 5 vùng rủi ro nhất thay vì liệt kê dàn trải. Tối đa 30 phát hiện, xếp theo mức nghiêm trọng.
- Không nhắn sếp. PM (Opus) đọc báo cáo rồi chuyển lên sếp.
- Hết quota giữa chừng: push bản dở dang, đầu file ghi "(ĐANG LÀM: đã xong mục …)", lần sau làm tiếp.

== SẢN PHẨM GIAO ==
Một file duy nhất: docs/opus/ra-soat/2026-10-04-sol-ra-soat-doc-lap.md
Nộp trên nhánh sol/O03, PR vào main tiêu đề "[viec-O03] Rà soát độc lập toàn dự án". PR chỉ chứa file này.
Cấu trúc file:
 0. TÓM TẮT CHO SẾP (tối đa 12 dòng, không thuật ngữ):
    - kết luận một câu: ĐÚNG HƯỚNG / XA ĐÀ / SAI HƯỚNG, kèm mức độ;
    - 5 rủi ro lớn nhất;
    - 5 việc nên làm ngay;
    - 3 việc nên dừng.
 1. BẢNG PHÁT HIỆN với các cột:
    Mã (R01…) | Mức (Nghiêm trọng/Cao/Trung bình/Thấp) | Hạng mục (Hướng đi/Pháp lý/Bảo mật/Kỹ thuật/Chất lượng/Quy trình) | Phát hiện | Bằng chứng | Đề xuất | Độ chắc chắn
 2. BẢNG TÍNH NĂNG với các cột:
    Tính năng/engine | Phục vụ mục tiêu | Trạng thái thật | Chi phí duy trì | Đề xuất (GIỮ/HOÃN/GỘP/BỎ) | Lý do
 3. ĐÁNH GIÁ QUY TRÌNH ĐỘI VÀ PM: điểm nghẽn, sự cố lặp lại, đề xuất đơn giản hóa.
 4. ĐANG LÀM TỐT: tối đa 5 dòng, để sếp biết cái gì không nên phá.
 5. CÂU HỎI CẦN SẾP QUYẾT: mỗi câu kèm phương án khuyến nghị.
Toàn file không quá khoảng 400 dòng. Ngắn mà có bằng chứng hơn dài mà chung chung.
```
