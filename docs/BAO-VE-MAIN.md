# Bảo vệ nhánh `main` — hướng dẫn cho sếp và bàn giao cho PM gốc

Viết 07/11/2026 bởi Nova (PM phụ tạm, lệnh sếp). Phần chưa kiểm được ghi **[CHƯA KIỂM]**.
Liên quan: `docs/BAO-VE-REPO.md` (kế hoạch bảo vệ repo), `docs/dac-ta/V201-nhan-dien-review.md` (cách phân biệt người duyệt).

## 0. Hiện trạng (07/11/2026)

- `main` **không có bảo vệ nào**: không có ruleset (`gh api repos/aowvn-10diem/aowvn-monika/rulesets` trả `[]`), không có branch protection (cố vấn Opus kiểm: 404). Ai có token đẩy được thẳng `main`, gộp được PR dù CI đỏ.
- Mọi phiên (PM, Sol, Luna, DeepSeek, Nova) dùng chung tài khoản `aowvn-10diem`. Vì vậy GitHub **không phân biệt được** tác giả và người duyệt; chỉ phân biệt qua chữ trong comment. Xem V201.
- Build, Coverage, CodeQL có bộ lọc đường dẫn (`paths-ignore`/`paths`): PR chỉ sửa `docs/**` hay `*.md` **không có** check Build/Coverage/CodeQL. Bắt buộc thẳng ba check này sẽ treo mọi PR tài liệu ở "Expected — Waiting for status".

## 1. Quyết định chốt [A1] — phương án `gate`

Thêm một workflow `gate` (check tên `gate`) luôn chạy trên mọi PR:
- PR chỉ sửa tài liệu: xanh ngay.
- PR có mã: đợi Build, Coverage, CodeQL (khi bộ lọc đường dẫn của CodeQL khớp) xanh **trên đúng SHA head của PR**; thiếu, đỏ, hủy thì đỏ.
- Chỉ đọc, không secret, không action bên thứ ba. Mã: `.github/workflows/gate.yml`, `scripts/gate-expected.py` (có test `scripts/test-gate-expected.py`, gate tự chạy test này mỗi lượt). `gate` đối chiếu workflow theo **đường dẫn tệp** (không theo tên hiển thị), lấy danh sách tệp và lượt chạy có phân trang, đỏ khi số tệp nhận được khác số tệp của PR (PR trên 3000 tệp cần sếp xem tay), và chạy lại khi sửa mô tả PR (`edited`) hay chuyển từ nháp sang sẵn sàng. `gate` đã gộp 09/11 (PR #528).
- Sếp chỉ cần bắt buộc đúng **một** check `gate` trong ruleset. Không bắt buộc Build/Coverage/CodeQL riêng lẻ.

Phương án đã loại: (a) bỏ `paths-ignore` ở ba workflow (tốn thêm CI cho PR tài liệu, trái V11); (b) bắt buộc thẳng ba check rồi để sếp bỏ qua PR tài liệu (mất ý nghĩa bảo vệ); (c) `pull_request_target` để PR không sửa được gate (check gắn vào commit của `main`, không hiện trên PR; còn có rủi ro chạy mã không tin cậy).

## 2. Việc sếp làm (theo thứ tự)

### Bước A — bật luật cơ bản ngay (2 phút, không cần chờ gate)

1. Repo `aowvn-10diem/aowvn-monika` → **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**.
2. **Ruleset Name**: `main-bao-ve`. **Enforcement status**: `Active`.
3. **Bypass list**: để trống (mọi phiên dùng chung tài khoản của sếp; thêm sếp vào bypass thì luật không chặn được ai).
4. **Target branches** → **Add target** → **Include default branch** (là `main`).
5. Tick: **Restrict deletions**, **Block force pushes**, **Require a pull request before merging** với **Required approvals = 0** (đặt 1 sẽ chặn cả việc gộp, vì không ai duyệt được PR của chính tài khoản mình). Các ô khác để trống.
6. **Create**.

Sau khi bật: push thẳng `main` bị từ chối (kể cả của sếp); gộp qua PR bình thường. Workflow hiện có chỉ thấy `sync-config.yml` đẩy lên nhánh `trang-web`, không thấy cái nào đẩy `main` (dựa trên grep `git push`; **[CHƯA KIỂM]** các phiên ngoài repo).

### Bước B — bắt buộc `gate` (làm khi `gate` đã chạy ổn)

Điều kiện: PR gate đã gộp **và** `gate` hiện trên ít nhất 3 PR liên tiếp với kết quả đúng:
- 1 PR chỉ tài liệu: `gate` xanh trong vài giây.
- 1 PR có mã, CI xanh: `gate` xanh sau khi Build/Coverage (và CodeQL nếu có) xanh.
- 1 PR có workflow đỏ: `gate` đỏ.

Làm: Settings → Rules → Rulesets → `main-bao-ve` → bật **Require status checks to pass** → **Add checks** → gõ `gate` → chọn dòng của **GitHub Actions** → **Save**. Để tắt ô **Require branches to be up to date before merging** (bật sẽ bắt mọi PR rebase lại sau mỗi lần main đổi, trong khi đội đang xếp hàng nhiều PR config).

### Hoàn tác / khẩn cấp

Settings → Rules → Rulesets → `main-bao-ve` → **Enforcement status: Disabled** (tức thì). Bật lại sau khi xong việc. Đừng xóa ruleset.

### Kiểm sau khi bật (lệnh đọc, ai cũng chạy được)

- `gh api repos/aowvn-10diem/aowvn-monika/rulesets` phải thấy `main-bao-ve`, `enforcement: active`.
- `git push origin HEAD:main` thử từ một nhánh rác phải bị từ chối; hoặc nhìn trang một PR: nút gộp báo "Required" cho `gate` (sau Bước B).

## 3. Giới hạn thật (đừng hiểu nhầm là an toàn tuyệt đối)

- PR sửa `gate.yml`, `scripts/gate-expected.py` hoặc `scripts/test-gate-expected.py` (gate chạy trực tiếp tệp này từ nhánh PR) có thể làm yếu gate (workflow `pull_request` dùng bản trong chính PR). Quy tắc: mọi PR đụng `.github/workflows/**` hoặc ba tệp này cần duyệt hai lượt **và** sếp (CodeQL chạy ở các PR đó, nhưng không chặn việc làm yếu gate).
- `gate-expected.py` phản chiếu bộ lọc đường dẫn của Build/Coverage/CodeQL. Đổi bộ lọc ở workflow nào phải đổi file này cùng PR; nếu lệch, gate có thể đợi workflow không bao giờ chạy (đỏ) hoặc bỏ sót workflow (lọt).
- `gate` chỉ nhìn kết quả một lần: chạy lại Build/Coverage/CodeQL sau khi đỏ thì phải chạy lại cả `gate` (nút **Re-run** của `gate`), và Build chạy lại ra đỏ sau khi `gate` đã xanh thì `gate` vẫn xanh (lọt nhỏ, mỗi PR vẫn có CI hiển thị riêng). PR hơn 300 tệp: GitHub chỉ xét 300 tệp đầu khi lọc đường dẫn, `gate` xét hết nên có thể đỏ oan (không lọt).
- `gate` chỉ bao Build, Coverage và CodeQL. Các cổng khác (kiểm web khi đổi config app, kiểm gói thật V37, dựng lõi V198, Test Lab ARM V56) **không** nằm trong `gate`, nên ruleset chỉ bắt `gate` sẽ không chặn gộp khi các cổng đó đỏ. `libretrodroid/` và `j2me/` chỉ kéo Build + Coverage vì CodeQL không quét hai thư mục này (khớp với CI hiện tại, không phải lỗ của gate).
- Đổi tên hay bộ lọc của `build.yml`, `coverage.yml`, `codeql.yml` mà quên sửa `scripts/gate-expected.py` thì test chống lệch làm `gate` đỏ cho mọi PR có mã cho tới khi sửa (cố ý, để lệch lộ ra ngay).
- CODEOWNERS cho `.github/workflows/**` và ba tệp trên **chưa tạo được người duyệt độc lập** khi mọi phiên dùng chung một tài khoản; chỉ có tác dụng sau V201 giai đoạn B (mỗi vai một danh tính). Đừng ghi là đã bảo vệ.
- `gate` chỉ chứng minh CI xanh, **không** chứng minh có người duyệt (không có review bắt buộc, vì cùng một tài khoản). Phần "đủ hai lượt Đạt đúng SHA" vẫn dựa kỷ luật; V201 sẽ thêm bảng/kiểm tra bán tự động.
- Gate không thay cho việc sếp xem PR đụng config/Cloudflare/khóa/phát hành: các PR đó vẫn cần sếp.

## 4. Bàn giao cho PM gốc (khi quay lại)

**Vai trò tạm:** từ 07/11 Nova là PM phụ trách thay Sol 2 theo lệnh sếp, kèm cố vấn Opus 5.5 (subagent, chỉ đọc, không ghi GitHub). Khi PM gốc quay lại, mọi quyền gộp tạm của Nova **hết hiệu lực**; Nova trở về BA + UX + CHANGELOG.

**Đã làm 07/11** (đều ghi ở #119 và #337):
| Việc | Kết quả |
|---|---|
| Gộp #515, #518 (tài liệu, L3 Đạt, CI xanh) và #501 (G2, L3 + Ultra Đạt cùng head 1632a71, CI xanh, config 60→61) | đã gộp; Sync config lên Cloudflare chạy thành công; CI trên main chạy tiếp (kiểm ở lượt Nova 22:32 và 07:32) |
| Phân việc Luna cho "DeepSeek Duyệt 2" (sếp tạo phiên) | đăng ở #337; phiên tự giới thiệu ở #119 khi lên |
| Soạn gate (V200), cố vấn Opus phản biện độc lập (sửa các điểm "Nên sửa"), sửa tiếp theo Argon và PM | PR #528 đã gộp 09/11; chưa bắt buộc trong ruleset, chờ 3 PR chạy đúng (Bước B) |
| Soạn đặc tả V201 (phân biệt tác giả/người duyệt) | đặc tả trong PR #527, chưa có mã |

**Việc còn dở, cần PM gốc nắm:**
| Việc | Trạng thái |
|---|---|
| #517 (V198 PSP, config 62) | phải hòa main sau #501, **giữ configVersion 62**, rồi L3 → Ultra lại; Opus khuyên để sếp gộp (đổi nguồn lõi PSP của mọi người dùng, khó hoàn tác) |
| #503 (V196) | thiếu lượt hai trên head hiện tại (lượt hai cũ nằm trên head cũ) |
| #471 | behind main (cũ hàng trăm commit): cần hòa main + CI mới |
| #457, #469 | thiếu cả hai lượt duyệt theo luật hiện hành |
| #514 (V145) | Nova chưa rà phần BA; Ultra từng ghi "Cần PM xem" phạm vi giao việc |
| Ruleset `main-bao-ve` | sếp tự bật (Bước A), Bước B sau khi gate ổn |
| Cách hoàn tác config đã đồng bộ lên Cloudflare | **chưa có**: cần quy trình đẩy lại bản cũ với `configVersion` +1 và đã thử trước |

**Mã việc tạm:** V200 (gate), V201 (nhận diện người duyệt) do Nova đặt tạm; PM gốc xác nhận hoặc đổi.
**Không đụng:** Nova không sửa `docs/opus/KE-HOACH.md`, `docs/opus/pm/**`, trạng thái PM (`trang-thai-pm.json`, bảng tiến độ). PM gốc cập nhật các tệp đó theo bảng trên.
**Điểm vào nhanh:** #119 (comment `[Nova → đội]` 07/11 và các comment `[PM phụ (Nova) → đội]` sau đó), #337 (phân việc DeepSeek), `docs/BAO-VE-REPO.md`, tệp này.
