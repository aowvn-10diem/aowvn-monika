# Bàn giao trang web monika.aow.vn (Nova → PM, 16/10/2026)

Soạn thừa lệnh sếp (Minh). PM đọc khi rảnh, không gấp. Nova không ghi "PM duyệt" ở đâu cả.

**Kết luận:** trang web đã làm xong và lên live ngày 16/10 (đầu trang xoay, giao diện tối, lộ trình, logo hệ máy, popup tải có nhạc, ảnh nén). Sếp ra lệnh **tách trang web ra khỏi thư mục `docs/`**: "trang web thì chỉ có web thôi". Nova đã dựng nhánh `trang-web` (chỉ có tệp web). **Chưa có gì đổi trên trang thật**; chỉ khi sếp đổi nguồn GitHub Pages sang nhánh đó thì mới có hiệu lực. Việc này làm một số trang của PM không còn công khai, cần PM biết (mục 3).

## 1. Đã lên live hôm nay (nhánh `docs/opus-tra-loi`, thư mục `/docs`)

| PR | Nội dung |
|---|---|
| #251, #252 | Tải nhanh hơn (preload, đường tải), menu "Made by AowVN" |
| #253 | Trang Lịch sử cập nhật `lich-su.html` + đếm lượt tải APK |
| #255, #256 | Quy trình Haiku biên tập CHANGELOG cho người thường (mục 5) |
| #257 | Làm mới trang theo 12 mục của sếp (ảnh chụp, nội dung, hệ máy, lượt tải, popup ủng hộ VietQR) |
| #258 (`47d7828`) | Luôn dùng giao diện tối (sếp chốt phương án A, 16/10) |
| #259 (`dc27e1b`) | Đầu trang xoay 4 nội dung (nút tải đứng yên), mockup lơ lửng, logo hệ máy không nền, popup tải có thanh tiến độ ước tính + nhạc, ảnh AVIF/WebP nén, mockup Nokia |
| #260 (`f7b1d74`) | Trang 4 đổi tiêu đề "Xây dựng cho tương lai", thêm mục Lộ trình phát triển |

Mỗi lần lên live, Nova xóa cache Cloudflare (mục 4). Các PR trên gộp theo lệnh trực tiếp của sếp.

## 2. Đang chờ

| Việc | Trạng thái |
|---|---|
| **#261** tối ưu SEO (meta, Open Graph, JSON-LD gồm FAQPage, robots, sitemap, 404, ảnh chia sẻ 1200×630) | Mở, chờ sếp duyệt. Nội dung đã nằm sẵn trong nhánh `trang-web`, nên #261 sẽ thừa khi sếp chuyển nguồn Pages; nên đóng #261 sau đó. |
| **Chuyển nguồn Pages** sang nhánh `trang-web` | Chỉ sếp làm được (Nova không có công cụ sửa cài đặt repo): Settings → Pages → Branch `trang-web`, thư mục `/ (root)`, Save. Tên miền tùy chỉnh giữ nguyên (tệp `CNAME` đã có ở gốc nhánh). Quay lại nếu lỗi: đặt nguồn về `docs/opus-tra-loi` thư mục `/docs`. |

## 3. Ảnh hưởng tới PM (cần PM đọc kỹ)

**Hiện trạng:** Pages phục vụ cả `/docs`, nên **mọi tệp trong `docs/` đều công khai** trên `monika.aow.vn` (`opus/`, `tester/`, `web/`, các `.md`). Nova phát hiện khi làm SEO và đề xuất tách; sếp đồng ý.

**Sau khi chuyển nguồn**, không còn công khai: toàn bộ `docs/**` trừ tệp web, trong đó có `opus/pm/bang-tien-do/index.html` (bảng tiến độ), `tester/mau-bang.csv`, `web/` (nguồn `device-login.html`), các `.md`. **Không bị ảnh hưởng:**
- Nhánh `docs/opus-tra-loi` vẫn là nhánh làm việc của PM, **đường dẫn tài liệu giữ nguyên**.
- App Android: `loginUrl` trỏ `www.aow.vn/p/device-login.html` (Blogger), không phụ thuộc Pages.
- `lich-su.html` đọc `docs/CHANGELOG.md` trực tiếp từ nhánh `main` qua raw.githubusercontent, không đổi.

**PM cần quyết / sửa:**
1. Bảng tiến độ có cần công khai không? Nếu có, chọn chỗ mới (Artifact, hoặc thêm một thư mục vào nhánh `trang-web`); `build_public.py` hiện chỉ ghi `public.html`, không ghi `docs/index.html` nữa.
2. Cập nhật các chỗ còn nhắc `docs/index.html` hoặc `github.io` là trang tải app: `.claude/skills/pm-kiem-thu/SKILL.md` dòng 12, `docs/opus/pm/prompt-kiem-thu-hien-tai.md` dòng 18, `docs/opus/pm/trang-thai-pm.json` (`public_page`, dòng 153), chú thích đầu `docs/opus/pm/bang-tien-do/build_public.py`. (`docs/opus/ket-qua/V49.md` là lịch sử, không sửa.)
3. Sau khi chuyển, trang web sửa **chỉ ở nhánh `trang-web`** (tệp ở gốc, không còn tiền tố `docs/`). Tệp web còn nằm trong `docs/` của nhánh PM là bản cũ, nên xóa trong một PR riêng của PM sau khi sếp xác nhận trang thật chạy ổn.

## 4. Cách làm việc với trang web (sau khi chuyển)

- **Nhánh `trang-web`** (gốc): `index.html`, `lich-su.html`, `404.html`, `robots.txt`, `sitemap.xml`, `CNAME`, `.nojekyll`, `assets/` (mockup AVIF + WebP ở `assets/m/`, logo hệ máy `assets/sys/`, biểu tượng 3D `fluent3d_*`, nhạc `assets/snd/tai-ve.mp3`, ảnh chia sẻ `og-monika.jpg`). Không có README (Pages phục vụ công khai mọi tệp).
- **Quy trình sửa:** nhánh mới từ `trang-web` → PR vào `trang-web` → sếp ra lệnh gộp. Kiểm bằng Playwright (desktop 1366, Pixel 7), xem trước qua `rawcdn.githack.com/aowvn-10diem/aowvn-monika/<commit>/index.html`.
- **Xóa cache Cloudflare** sau khi Pages dựng xong (xóa sớm thì cache lại bản cũ): zone `aow.vn` (id `cdfbfbffcddf903d52316811eae4d084`), xóa theo địa chỉ: `/`, `/index.html`, `/lich-su.html` và mọi tệp ảnh đổi tại chỗ (tệp mới có địa chỉ mới thì không cần). Tài khoản của connector Cloudflare **chưa được sếp xác nhận là của sếp**.
- **Hằng số trong `index.html`:** thông tin ủng hộ (`DON`), khóa lưu `monika.dl.v1` (đếm tải, cache), `monika.snd` (tắt nhạc), thời gian xoay `DUR = 7000`. Lượt tải lấy từ API Releases (60 lần/giờ/IP, không cần khóa).
- **Thanh tiến độ trong popup chỉ là ước tính.** Trình duyệt không cho trang đọc tiến độ thật của file tải bằng liên kết, và GitHub không bật CORS cho file release. Muốn tiến độ thật cần một đường chuyển tiếp có CORS (ví dụ Cloudflare Worker); chưa làm.

## 5. Quy trình Haiku biên tập CHANGELOG

Skill `bien-tap-changelog`, script `scripts/changelog-pending.py` (6 bài test N04), routine "Nova kiểm hộp thư" (`trig_01Rc26rgjFMcTifW523pxoGH`) mở phiên Haiku cho bản chưa biên tập. v0.8.1–v0.8.3 đã biên tập. **v0.8.0 còn chờ** vì sếp chưa chọn cách xử lý hạn mức (giữ chạy / giãn routine 3 giờ / tạm dừng tự mở phiên Haiku). Routine không bị tắt.

## 6. Việc treo và rủi ro

| Mã | Việc | Ai |
|---|---|---|
| A1 | **Nhạc** `tai-ve.mp3` lấy từ file tải video Facebook, chưa rõ bản quyền. Sếp xác nhận nguồn, không thì gỡ (một commit). | Sếp |
| A2 | [CHƯA KIỂM] Các câu quảng bá nguyên văn của sếp: "đầu tiên tại Việt Nam", "tiết kiệm tới 80%", "Android ảo tích hợp", "lõi viết lại từ đầu", "tối ưu theo giờ". Không kiểm được từ mã. | Sếp / PM |
| A3 | Trạng thái các mốc Lộ trình (Đã xong / Đang làm / Sắp tới / Dự kiến) do Nova tự đặt. | Sếp |
| A4 | Logo hệ máy và logo NOKIA là nhãn hiệu của chủ sở hữu; chưa có trang giấy phép. Biểu tượng 3D Fluent Emoji (MIT): sếp đã yêu cầu bỏ dòng ghi nguồn ở chân trang; MIT cần giữ thông báo bản quyền, nên cân nhắc một trang "Giấy phép". | Sếp / PM |
| A5 | Chưa thử trên điện thoại thật: popup + nhạc + liên kết ngân hàng, vuốt ngang, tốc độ thật. Sandbox không vào được web thật (403). | Sếp |
| A6 | Sau khi chuyển nguồn Pages: gửi `sitemap.xml` lên Google Search Console / Bing, làm mới ảnh chia sẻ trên Facebook/Zalo. | Sếp |
