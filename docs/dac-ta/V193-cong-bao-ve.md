## V193 — Cổng bảo vệ G1–G3 (đặc tả)

- Người làm: DeepSeek 2
- Thiết kế gốc: `docs/BAO-VE-REPO.md` (mục G1–G3, A70/A77)
- Trạng thái: Nháp (chờ PM duyệt trước khi làm mã)

### 1. Mục tiêu
Bản app do người khác tự build từ repo công khai **không** tự lấy bài viết/diễn đàn của AowVN, **không** tự tải Pixeldrain, **không** tự tải lõi/engine từ kho packs. Bản chính thức vẫn đủ tính năng như hiện tại.

### 2. Ngoài phạm vi
- Không chặn việc fork mã nguồn (GPLv3); không làm G0/HMAC, P1a overlay, P2 kiểm chứng thư, P5 tách repo.
- Không đổi dữ liệu người chơi (save, phím, bố cục), không đổi luồng phát hành APK.
- Không sửa `config/monika-config.json` trong PR đặc tả này.

### 3. Tiêu chí đạt

**G1 — bài viết + diễn đàn**
- [ ] Bản tự build (không có `BuildConfig.OFFICIAL_SERVICES`) không gửi request nào tới `feed.url`, `forum.baseUrl` hay `aow.vn/feeds` trong suốt 24 giờ chạy nền; test đếm URL bằng OkHttp interceptor = 0.
- [ ] Trang chủ/Diễn đàn trên bản tự build ghi rõ "Bản tự build không bật dịch vụ AowVN" thay vì danh sách rỗng gây hiểu nhầm.
- [ ] Có công tắc rõ trong Cài đặt: "Bài viết & diễn đàn" — chỉ bản chính thức bật được; tắt = không gọi mạng, không thăm dò nền.
- [ ] Bản chính thức giữ nguyên hành vi hiện tại (feed/forum như 0.8.9).

**G2 — Pixeldrain**
- [ ] Bản tự build không gọi `pixeldrain.com` khi mở app, nhập game hay prefetch; request chỉ phát sinh sau khi người dùng **chủ động dán link** và bấm xác nhận.
- [ ] Hộp thoại cảnh báo: "Tệp do bên thứ ba (Pixeldrain), AowVN không kiểm soát"; huỷ thì không request.
- [ ] Bản chính thức dùng `/pd/<id>` của Worker (302 sang Pixeldrain, khoá API ở secret Worker); không stream tệp qua Worker.
- [ ] Test: trước xác nhận, số request tới Pixeldrain = 0; sau xác nhận mới có đúng 1.

**G3 — lõi/engine/module**
- [ ] Bản tự build không tải 16 link `aowvn-monika-packs` hiện có, không tự thay bằng mirror/buildbot khi thiếu lõi; hiện "Cần bản chính thức để tải lõi" + nút mở trang chính thức.
- [ ] Bản chính thức tải qua link 302 có chữ ký TTL ngắn hoặc R2; **tệp lớn không đi qua Worker**.
- [ ] Vẫn kiểm `sha256` sau khi tải; sai hash thì xoá tệp và báo lỗi, không chạy lõi.
- [ ] Test: bản self-build, `CoreManager`/module download bị chặn **trước** khi gọi mạng.

### 4. Bản Play
`không` — Play đã dùng stub config, không tải game/lõi/feed; G1–G3 giữ nguyên hành vi Play, không thêm màn hay quyền mới.

### 5. Config
- `BuildConfig.OFFICIAL_SERVICES` (mặc định `false` trong mã; chỉ CI release đặt `true`) — không nằm trong config, fork sửa config cũng không bật được.
- `feed.enabled` / `forum.enabled` mặc định `false` trong `MonikaConfig.kt`; config chính thức đặt `true` (an toàn khi thiếu trường).
- G2: thêm `downloadHosts[].autoDirect` (mặc định `false`); Pixeldrain chỉ resolve khi người dùng dán link.
- G3: `cores.*.url`, `engines[].url`, `modules.*.url` đổi sang `/dl/...` (Worker ký 302 hoặc R2), giữ `sha256`.
- `configVersion` 58 → **61** (+1 mỗi PR G1/G2/G3), tăng cùng PR đổi config; đồng bộ KV/Pages theo V181.

### 6. Chia PR
| # | Phạm vi | Phụ thuộc | PR |
|---|---|---|---|
| 1 | G1: `OFFICIAL_SERVICES`, tắt feed/forum bản tự build, công tắc Cài đặt, config +1 | — | #… |
| 2 | G2: chỉ tải Pixeldrain khi dán link + cảnh báo, `autoDirect`, config +1 | 1 | #… |
| 3 | G3: đường tải mới (Worker 302 ký TTL/R2), client tải + kiểm hash | 1 | #… |
| 4 | G3: đổi link config + `configVersion` +1, test | 3 (Worker đã deploy) | #… |
| — | Ra bản app dùng link mới (release, không phải PR) | 4 | — |

### 7. Thứ tự G3 (PM chốt 12:44Z — bắt buộc)
1. Làm **đường tải mới**: Worker trả **302 có chữ ký TTL ngắn** (hoặc R2); tệp lớn không đi qua Worker.
2. Đổi link trong `config/monika-config.json` **+ tăng `configVersion`**.
3. **Ra bản app** dùng link mới (kèm `minVersionCode` nếu cần ép).
4. **Sau đó** PM báo sếp chuyển repo `aowvn-monika-packs` sang riêng tư.

**Điều kiện chặn:** chưa xong bước 3 (và chưa có thời gian chuyển đổi cho bản đang cài) thì **không** được để việc repo riêng tư thành điều kiện chạy — bản app đang dùng phải tiếp tục tải được. Trong lúc chuyển, giữ link cũ và link mới cùng sống.

### 8. Rủi ro
- Không thêm lượt gọi Worker mỗi lần mở app: feed/forum dùng cache như hiện tại; G2 chỉ gọi khi dán link; G3 chỉ gọi khi người dùng cài lõi/module.
- Save/phím/bố cục không đụng; lỗi mạng vẫn dùng bản đóng gói/cache như hiện tại.
- Fork "lười" bị chặn; người cố tình dịch ngược APK chính thức vẫn trích được secret (G0 chỉ là rào thấp).
- Đổi đường tải lõi có thể làm người dùng cũ hỏng nếu bỏ link cũ quá sớm → giữ song song tới khi bản mới phủ đủ.

[CHƯA KIỂM] Hạn mức/chi phí Cloudflare và R2, số người dùng thật, TTL/chữ ký Worker chưa có mã; đặc tả docs-only nên chưa chạy gradle (V11) và chưa thử tải thật.
