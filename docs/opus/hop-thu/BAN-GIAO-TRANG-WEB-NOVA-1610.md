# Bàn giao trang web monika.aow.vn (Nova → PM, 16/10/2026)

Soạn thừa lệnh sếp (Minh). PM đọc khi rảnh, không gấp. Nova không ghi "PM duyệt" ở đâu cả.

**Kết luận:** trang web đã làm xong và lên live ngày 16/10 (đầu trang xoay, giao diện tối, lộ trình, logo hệ máy, popup tải có nhạc, ảnh nén). Sếp ra lệnh **tách trang web ra khỏi thư mục `docs/`**: "trang web thì chỉ có web thôi". Nova đã dựng nhánh `trang-web` (chỉ có tệp web). **Sếp đã đổi nguồn GitHub Pages sang nhánh `trang-web`** (Pages dựng lần đầu từ nhánh này lúc 04:34 UTC ngày 16/10), nên trang thật giờ chạy từ nhánh đó. Một số trang của PM không còn công khai nên PM cần biết (mục 3). Sếp cũng ra lệnh **công khai bảng tiến độ**: đã đặt ở `https://monika.aow.vn/tien-do/`.

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
| **Chuyển nguồn Pages** sang nhánh `trang-web` | **Xong** (sếp làm 16/10). Quay lại nếu lỗi: Settings → Pages → nguồn `docs/opus-tra-loi`, thư mục `/docs`. Tên miền tùy chỉnh giữ nguyên (tệp `CNAME` ở gốc nhánh). |

## 3. Ảnh hưởng tới PM (cần PM đọc kỹ)

**Hiện trạng:** Pages phục vụ cả `/docs`, nên **mọi tệp trong `docs/` đều công khai** trên `monika.aow.vn` (`opus/`, `tester/`, `web/`, các `.md`). Nova phát hiện khi làm SEO và đề xuất tách; sếp đồng ý.

**Sau khi chuyển nguồn**, không còn công khai: toàn bộ `docs/**` trừ tệp web, trong đó có `opus/pm/bang-tien-do/index.html` (bảng tiến độ), `tester/mau-bang.csv`, `web/` (nguồn `device-login.html`), các `.md`. **Không bị ảnh hưởng:**
- Nhánh `docs/opus-tra-loi` vẫn là nhánh làm việc của PM, **đường dẫn tài liệu giữ nguyên**.
- App Android: `loginUrl` trỏ `www.aow.vn/p/device-login.html` (Blogger), không phụ thuộc Pages.
- `lich-su.html` đọc `docs/CHANGELOG.md` trực tiếp từ nhánh `main` qua raw.githubusercontent, không đổi.

**Bảng tiến độ đã được sếp quyết công khai** tại `/tien-do/` (nhánh `trang-web`, tệp `tien-do/index.html`, giao diện tối, có liên kết từ mục Lộ trình trên trang chủ). Bản hiện tại dựng từ `state.json` lúc 15/10 12:05 nên **đang cũ** (trang tự ghi "đã hơn 2 giờ"). Cập nhật bằng script mới `docs/opus/pm/bang-tien-do/dang-tien-do.py <thư-mục-gốc-checkout-nhánh-trang-web>`: chạy `build_public.py`, bọc khung HTML + meta SEO, ghi `tien-do/index.html`; rồi PR vào `trang-web` (hoặc sếp cho đẩy thẳng) và xóa cache Cloudflare cho `/tien-do/`. Lưu ý: trang công khai hiện toàn bộ nội dung `state.json` (đội, việc, nhật ký, điểm nghẽn); nếu PM muốn lược bớt thì lọc `state.json` trước khi dựng. Không có khóa hay token thật trong tệp (Nova đã quét).

**PM cần quyết / sửa:**
1. (Đã xử lý) Bảng tiến độ công khai: xem trên.
2. Cập nhật các chỗ còn nhắc `docs/index.html` hoặc `github.io` là trang tải app: `.claude/skills/pm-kiem-thu/SKILL.md` dòng 12, `docs/opus/pm/prompt-kiem-thu-hien-tai.md` dòng 18, `docs/opus/pm/trang-thai-pm.json` (`public_page`, dòng 153), chú thích đầu `docs/opus/pm/bang-tien-do/build_public.py`. (`docs/opus/ket-qua/V49.md` là lịch sử, không sửa.)
3. Sau khi chuyển, trang web sửa **chỉ ở nhánh `trang-web`** (tệp ở gốc, không còn tiền tố `docs/`). Tệp web còn nằm trong `docs/` của nhánh PM là bản cũ, nên xóa trong một PR riêng của PM sau khi sếp xác nhận trang thật chạy ổn.

## 4. Cách làm việc với trang web (sau khi chuyển)

- **Nhánh `trang-web`** (gốc): `index.html`, `lich-su.html`, `404.html`, `robots.txt`, `sitemap.xml`, `CNAME`, `.nojekyll`, `assets/` (mockup AVIF + WebP ở `assets/m/`, logo hệ máy `assets/sys/`, biểu tượng 3D `fluent3d_*`, nhạc `assets/snd/tai-ve.mp3`, ảnh chia sẻ `og-monika.jpg`). Không có README (Pages phục vụ công khai mọi tệp).
- **Quy trình sửa:** nhánh mới từ `trang-web` → PR vào `trang-web` → sếp ra lệnh gộp. Kiểm bằng Playwright (desktop 1366, Pixel 7), xem trước qua `rawcdn.githack.com/aowvn-10diem/aowvn-monika/<commit>/index.html`.
- **Xóa cache Cloudflare** sau khi Pages dựng xong (xóa sớm thì cache lại bản cũ): zone `aow.vn` (id `cdfbfbffcddf903d52316811eae4d084`), xóa theo địa chỉ: `/`, `/index.html`, `/lich-su.html` và mọi tệp ảnh đổi tại chỗ (tệp mới có địa chỉ mới thì không cần). Tài khoản của connector Cloudflare **chưa được sếp xác nhận là của sếp**.
- **Hằng số trong `index.html`:** thông tin ủng hộ (`DON`), khóa lưu `monika.dl.v1` (đếm tải, cache), `monika.snd` (tắt nhạc), thời gian xoay `DUR = 7000`. Lượt tải lấy từ API Releases (60 lần/giờ/IP, không cần khóa).
- **Thanh tiến độ trong popup chỉ là ước tính.** Trình duyệt không cho trang đọc tiến độ thật của file tải bằng liên kết, và GitHub không bật CORS cho file release. Muốn tiến độ thật cần một đường chuyển tiếp có CORS (ví dụ Cloudflare Worker); chưa làm.

## 5. Quy trình Haiku biên tập CHANGELOG

Skill `bien-tap-changelog`, script `scripts/changelog-pending.py` (6 bài test N04), routine "Nova kiểm hộp thư" (`trig_01Rc26rgjFMcTifW523pxoGH`) mở phiên **Haiku 5.5** cho bản chưa biên tập. **Từ 16/10 sếp giao việc này cho Haiku 5.5 làm thường trực** (Nova chỉ mở phiên, không tự biên tập), giữ routine chạy như cũ (coi như sếp chọn "giữ chạy" cho A5 hạn mức).

**Quy tắc nội dung mới (sếp chốt 16/10):** người đọc trang Lịch sử cập nhật là người dùng, nên mỗi phiên bản chỉ nói **Sửa lỗi:** (đã sửa gì) và **Mới:** (có gì mới). Không ghi "chưa kiểm / chưa rõ / chưa chạy thật" hay ghi chú của nhóm; những thứ đó và việc người dùng không thấy đi vào dòng `Nội bộ:`, và trang **không hiển thị** dòng này nữa. Nơi áp dụng:
- PR **#269** (vào `main`, chờ PM/sếp gộp): viết lại v0.8.0–v0.8.3 theo quy tắc, sửa skill `bien-tap-changelog`. v0.8.0 đã được Nova biên tập luôn (có dấu `<!-- bien-tap: v0.8.0 -->`, script báo không còn bản chờ).
- PR **#270** (vào `trang-web`): trang `lich-su.html` bỏ mục "Thay đổi nội bộ" và tự lược câu có "chưa kiểm/chưa rõ/chưa chạy thật/chưa thử".
- Prompt của routine đã được cập nhật 16/10 (cùng trigger id) để nhắc lại quy tắc mới và ghi Haiku là người giữ việc; vì Haiku đọc skill từ `main`, quy tắc này nằm cả trong prompt phiên Haiku cho tới khi #269 được gộp.

## 6. Việc treo và rủi ro

| Mã | Việc | Ai |
|---|---|---|
| A1 | **Nhạc** `tai-ve.mp3` lấy từ file tải video Facebook, chưa rõ bản quyền. Sếp xác nhận nguồn, không thì gỡ (một commit). | Sếp |
| A2 | [CHƯA KIỂM] Các câu quảng bá nguyên văn của sếp: "đầu tiên tại Việt Nam", "tiết kiệm tới 80%", "Android ảo tích hợp", "lõi viết lại từ đầu", "tối ưu theo giờ". Không kiểm được từ mã. | Sếp / PM |
| A3 | Trạng thái các mốc Lộ trình (Đã xong / Đang làm / Sắp tới / Dự kiến) do Nova tự đặt. | Sếp |
| A4 | Logo hệ máy và logo NOKIA là nhãn hiệu của chủ sở hữu; chưa có trang giấy phép. Biểu tượng 3D Fluent Emoji (MIT): sếp đã yêu cầu bỏ dòng ghi nguồn ở chân trang; MIT cần giữ thông báo bản quyền, nên cân nhắc một trang "Giấy phép". | Sếp / PM |
| A5 | Chưa thử trên điện thoại thật: popup + nhạc + liên kết ngân hàng, vuốt ngang, tốc độ thật. Sandbox không vào được web thật (403). | Sếp |
| A6 | Sau khi chuyển nguồn Pages: gửi `sitemap.xml` lên Google Search Console / Bing, làm mới ảnh chia sẻ trên Facebook/Zalo. | Sếp |

## 7. Cập nhật tối 16/10 (sau khi viết các mục trên)

| Việc | Trạng thái |
|---|---|
| **three.js trên trang chủ** (#266, #268, đã live): dải hạt cực quang đầu trang, băng chuyền logo hệ máy, điện thoại 3D tự chạy ở mục Tính năng (đọc web, xoay ngang chơi game, xoay dọc tải/vá), dòng hạt ở Lộ trình, pháo hoa khi bấm tải, chữ M 3D ở trang "Xây dựng cho tương lai" | Live. Chỉ bật khi máy đủ điều kiện (`html.fx3d`); chỉ dựng và vẽ khi người dùng xem tới; tự hạ chất lượng rồi tắt nếu dưới ~24 fps; popup mở thì dừng cảnh phía sau. Cờ thử: `?3d=off`, `?hud` (xem FPS), `?fx=force` (bỏ tự hạ, để thử trên máy không có GPU). |
| **Nhạc nền** (#266, #267) | `assets/snd/nen-x-cool.mp3` (X-COOL Instrumental – Ultra Slowed, 1,2 MB, âm lượng tối đa 70%). **Hiện trạng live (đến lúc viết): trang vẫn tự phát** khi vào trang nếu trình duyệt cho, không thì ở lần chạm đầu (`index.html` gọi `play()` lúc khởi tạo); nhỏ dần khi bấm tải; có nút bật/tắt (nhớ lựa chọn ở `monika.bgm`). **Nguồn/quyền chưa chốt:** sếp nói không có rủi ro bản quyền nhưng chưa có giấy tờ nguồn; Nova không xác nhận được. **Chỉ thị PM (16/10 12:24Z, [#119](https://github.com/aowvn-10diem/aowvn-monika/issues/119#issuecomment-6215444817)):** bỏ tự phát nhạc, nguồn/quyền MP3 chờ sếp, chưa gộp phần nhạc. **Đây là chỉ thị hiện hành, đang chờ triển khai:** trang live chưa áp dụng (cần sửa `index.html`: bỏ `play()` lúc khởi tạo, giữ nút bật/tắt). Lệnh trước đó của sếp (chọn phương án có nhạc, đẩy live) có trước chỉ thị này; việc còn chờ sếp là nguồn/quyền MP3, không phải chuyện bỏ tự phát. Bản remix cũ `nen.mp3` đã gỡ khỏi trang nhưng còn trong lịch sử git. |
| **Donate số tiền tùy chọn** (#268) | Ô nhập 1.000đ đến 500.000.000đ (mức tối đa là giả định của Nova, sếp/PM chốt). Số hợp lệ thì QR đổi sang QR VietQR có sẵn số tiền (ảnh từ `img.vietqr.io`, lỗi thì quay về QR tĩnh) và liên kết ngân hàng trên điện thoại dùng số đó; ô trống/sai thì quay về số của nút đã chọn. Chưa quét thử QR bằng app ngân hàng. |
| **Mã nguồn three.js** | Trên trang chỉ có bản đã đóng gói `assets/three/fx.js` (three@0.186.1, MIT, kèm `assets/three/LICENSE-three.txt`). Nguồn đọc được nằm ở `docs/opus/hop-thu/trang-web-nguon/fx.js` (đối chiếu: dựng lại cho ra đúng tệp đang live). Dựng lại: `npm i three@0.186.1 esbuild`, chép `fx.js` vào `src/`, chạy `npx esbuild src/fx.js --bundle --minify --format=esm --target=es2020 --outfile=out/fx.js --legal-comments=none`, rồi chép `out/fx.js` vào `assets/three/` trên nhánh `trang-web` (xem lại mục 4 về PR và xóa cache). |
| **Chữ trên điện thoại 3D** | Màn hình trong điện thoại 3D do Nova tự vẽ theo 6 tính năng, không phải ảnh chụp app thật; nếu có ảnh chụp thật thì thay. |
| **Cổng CI** | Nhánh `trang-web` không có workflow Build/Coverage/CodeQL nên các PR trang web không có kết quả CI (Luna 3 đã hỏi). PM/sếp xác nhận cách đáp ứng cổng này. |

Mục treo mới cho mục 6: nguồn nhạc `tai-ve.mp3` vẫn chưa rõ; task "sửa phím bấm theo asset nút bấm đẹp" mà sếp hỏi lúc 16/10: Nova **không tìm thấy** lệnh này trong lịch sử phiên của Nova (có thể liên quan PR #200, đã đóng); chưa xử lý, chờ sếp cho biết phím ở đâu và asset nằm ở đâu.
