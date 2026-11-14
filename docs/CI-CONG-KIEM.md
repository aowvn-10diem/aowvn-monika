# Cổng kiểm CI và bẫy đã gặp thật

Mốc dẫn chiếu: `main` `15f210a6` (14/11/2026). Mọi `tệp:dòng` trong tài liệu này tính theo mốc đó;
đọc lại bằng `git show 15f210a6:<tệp>`. Số dòng ở nhánh khác có thể lệch.

Tài liệu dành cho phiên làm việc mới: đọc mục 1 để biết cổng nào thật sự chặn gộp, mục 2 để không
đoán sai khi một check đỏ, mục 3 để không nhận oan 19 ca test đỏ có sẵn, mục 4 để tự kiểm trước khi push.

## 0. Ba câu chốt

1. **Hôm nay GitHub không bắt buộc cổng nào — đã kiểm bằng API ngày 14/11/2026.** Hai nơi có thể bắt buộc,
   cả hai đều trống:
   - ruleset `main-bao-ve` (id `26765417`, `enforcement: active`) chỉ có 3 luật: `deletion`,
     `non_fast_forward`, `pull_request` (0 approval) — **không** có `required_status_checks`;
   - branch protection của `main`: `protection.enabled = false`,
     `required_status_checks.enforcement_level = off`.

   Hệ quả: gộp được dù cổng đỏ, và `preview` cancelled không chặn nút gộp (bẫy 1). `gate` là cổng *nên*
   bắt buộc (`.github/workflows/gate.yml:3-5`, `docs/BAO-VE-MAIN.md:18`), việc bật nằm ở Bước B của
   `docs/BAO-VE-MAIN.md:35-42` và chưa làm. Tự kiểm lại bằng hai lệnh ở mục "Đọc đúng bắt buộc".
2. **`preview` / `digest` không phải cổng.** `gate` chỉ đợi Build, Coverage, CodeQL
   (`scripts/gate-expected.py:16-39`). `preview` bị hủy liên tục vì concurrency — chuyện bình thường.
3. **Máy Windows có 19 ca test đỏ sẵn trên `main`** (mục 3). CI Linux vẫn xanh. Đừng quy chúng cho PR mới.

## 1. Bảng cổng

Bảng có **10 dòng**, tương ứng **11 check** (dòng `analyze` là 2 check: `analyze (java-kotlin)` và
`analyze (javascript-typescript)`). Cột "bắt buộc để gộp?" trả lời đúng câu đó: cổng có làm `gate` đỏ
(và do đó chặn gộp nếu sếp đã bật `gate`) hay không.

| Cổng (tên job) | Workflow | Chạy gì | Bắt buộc để gộp? | Khi nào không xuất hiện |
|---|---|---|---|---|
| `gate` | `gate.yml:34` | Đọc danh sách tệp PR qua API; chạy `scripts/test-gate-expected.py`; đợi Build/Coverage/CodeQL trên **đúng head SHA** (`gate.yml:44-103`) | **Có** theo thiết kế — nhưng **chưa bật** trong ruleset (§0) | Không bao giờ — chạy trên mọi PR (`gate.yml:17-22`). PR docs-only: xanh ngay (`gate.yml:70-73`) |
| `build` | `build.yml:14` | V113 (`kiem-nuot-loi`), **5 test Ruby RGSS + 1 test Python** (`build.yml:41-48`), `testDebugUnitTest`, `assembleDebug`, `check-play-apk` (`build.yml:21-76`) | **Có**, khi PR có tệp không phải tài liệu | `paths-ignore: ["docs/**","**/*.md"]` (`build.yml:5-8`) |
| `coverage` | `coverage.yml:17` | `koverXmlReportDebug` + in % độ phủ (`coverage.yml:29-35`) | **Có**, cùng điều kiện `build` | `paths-ignore` (`coverage.yml:8,11`) |
| `analyze (java-kotlin)` và `analyze (javascript-typescript)` | `codeql.yml:19` | CodeQL init/analyze 2 ngôn ngữ; java-kotlin build tay bằng `assembleDebug` (`codeql.yml:39-47`) | **Có**, khi PR đụng tiền tố CodeQL | `paths` whitelist (`codeql.yml:5-7`): `app/**`, `cloudflare/**`, `scripts/**`, `.github/workflows/**`, `.github/codeql-config.yml`, `gradle/libs.versions.toml` |
| `submit-gradle` | **Không có tệp workflow trong repo** — là workflow động `dynamic/dependency-graph/auto-submission`, tên hiển thị "Automatic Dependency Submission (Gradle)", GitHub sinh và đăng ký ở cấp repo | Nộp dependency graph Gradle | **Không** (`scripts/gate-expected.py` không liệt kê) | **Có** hiện trên PR: chạy mỗi lần push nhánh (event `dynamic`), không bị `paths` lọc — kể cả PR docs-only |
| `n04-tests` | `script-tests.yml:18` | 10 test N04 + NDK/`truoc-khi-push`/core-build/PPSSPP/Telegram/workflow-security (`script-tests.yml:33-66`) | **Không** (`gate` không đợi) | `paths: scripts/**` (`script-tests.yml:5-12`) |
| `repeated-tests` | `compose-stability.yml:14` | `scripts/ci-compose-stability.sh`: 2 test Compose × 20 lượt, không retry (`compose-stability.yml:27-28`) | **Không** | `paths`: đúng 5 tệp (`compose-stability.yml:5-10`) |
| `preview` | `pm-digest.yml:18` | `test-pm-digest*.py` + `pm-digest-safe.py --out out/preview`, upload artifact | **Không** | Chỉ chạy khi event là `pull_request` (`pm-digest.yml:19`) — và hay bị **hủy** (bẫy 1) |
| `digest` | `pm-digest.yml:34` | `pm-digest-safe.py --publish`, đẩy nhánh mồ côi `bot/trang-thai` | **Không** | PR: `skipped` (`pm-digest.yml:36`); chỉ chạy push `main`, cron, dispatch |
| `links` | `periodic-check.yml:20` | `test-periodic-check.py` + `check-config-links.py --fail-on-error` (`periodic-check.yml:25-26`) | **Không** | `paths`: **6** tệp (`periodic-check.yml:7-13`); cron `43 20 * * *` |

Các cổng khác cũng có thể đỏ nhưng hẹp phạm vi hơn: `offline` (`stability-gate.yml:10`),
`policy` (`testlab-policy.yml`), `contract` (`web-config-contract.yml`), `pre-push-windows`
(`pre-push-windows.yml`), `real-packs` (`check-packs.yml`). Nguyên tắc giống nhau: `paths` hẹp thì PR
không đụng tệp là workflow **không chạy**, nên đừng chờ nó.

Hai chuyện dễ lẫn, đừng gộp:

- **"Không có tệp workflow trong `.github/workflows/`" không đồng nghĩa "không có check trên PR".**
  `submit-gradle` là ví dụ: không có tệp nào trong repo, nhưng hiện trên mọi PR đã kiểm (#640, #645,
  #647, #650 — `gh pr checks <số> --json name,state` thấy `submit-gradle: SUCCESS`). Chỉ đừng bắt `gate`
  đợi nó — `gate` đọc theo **đường dẫn workflow** (`gate.yml:86-90`, `gate-expected.py:19-23`) nên không
  bao giờ thấy nó.
- **"Không xuất hiện"** (bị `paths`/`paths-ignore` loại, workflow không được tạo) khác **"skipped"**
  (workflow có chạy, job bị `if:` loại — ví dụ `digest` trên PR).

### Đọc đúng "bắt buộc"

```bash
# Ruleset đang bật luật gì (kỳ vọng hiện tại: deletion, non_fast_forward, pull_request)
id=$(gh api repos/aowvn-10diem/aowvn-monika/rulesets --jq '.[0].id')
gh api "repos/aowvn-10diem/aowvn-monika/rulesets/$id" --jq '.rules[].type'

# Bảo vệ nhánh trực tiếp của main (kỳ vọng: enabled=false, enforcement_level=off)
gh api repos/aowvn-10diem/aowvn-monika/branches/main \
  --jq '[.protection.enabled, .protection.required_status_checks.enforcement_level] | @tsv'

# Check nào thật sự hiện trên head của PR (gồm cả check không có tệp workflow)
gh pr checks <số PR> --json name,state
gh api "repos/aowvn-10diem/aowvn-monika/commits/<sha40>/check-runs?per_page=100" \
  --jq '.check_runs[] | [.name, .conclusion] | @tsv'
```

`skipped` (job bị `if:` loại) khác hẳn **không xuất hiện** (workflow bị `paths`/`paths-ignore` loại).
`gate` phân biệt hai thứ này: workflow không xuất hiện sau 240 giây là đỏ (`gate.yml:76,91-92`).

## 2. Bẫy đã gặp thật

Mỗi bẫy: **triệu chứng → nguyên nhân (có dẫn chiếu) → cách xử lý**.

### Bẫy 1 — `preview` = CANCELLED do concurrency `pm-digest`

- **Triệu chứng.** PR hiện `preview` đỏ *Cancelled*, `digest` *Skipped*, trong khi `gate` xanh.
  Ví dụ thật: PR #647, head `bf0e594` (14/11/2026 02:14 UTC) — `preview cancelled`, `gate success`,
  `digest skipped`, và PR vẫn gộp được.
- **Nguyên nhân.** `pm-digest.yml:14-16` đặt `concurrency: group: pm-digest, cancel-in-progress: true`
  cho **toàn workflow**, không riêng từng job. Hai job `preview` (`:18`) và `digest` (`:34`) dùng chung
  nhóm đó, mà `digest` chạy theo cron `7,37 * * * *` (`:3`) và mỗi lần push `main` (`:7-8`). Chỉ cần một
  lượt `digest` mới bắt đầu là lượt `preview` đang chạy bị hủy.
- **Hệ quả thật.** `gate` **không** đợi `preview` (`scripts/gate-expected.py:16-39`), ruleset chưa bật
  required check, nên nút gộp không bị GitHub chặn. Cái bị chặn là *điều kiện "CI xanh"* khi PM đọc danh
  sách check: thấy một dòng đỏ là trả PR về. Đừng sửa `pm-digest.yml` chỉ để làm nó xanh — PR đụng
  `.github/workflows/**` phải hai lượt duyệt + sếp (`gate.yml:15-16`).
- **Cách xử lý.** Đẩy **một commit mới** để sinh lượt `preview` mới trên head mới. `pm-digest` không có
  `paths`/`paths-ignore` nên **mọi** push đều chạy lại, kể cả commit rỗng. Nhưng nếu PR cần cả `build`/
  `coverage` chạy lại thì commit rỗng **không** đủ (bẫy 2) — phải đổi một tệp thật.
  Đừng bấm Re-run job `preview` cũ: lượt mới vẫn bị concurrency hủy.

### Bẫy 2 — commit rỗng không kích hoạt workflow có `paths`/`paths-ignore`

- **Triệu chứng.** `git commit --allow-empty` rồi push; không có lượt `Build`/`Coverage`/`CodeQL` mới;
  `gate` đỏ với `ĐỎ: không thấy lượt chạy 'Build' trên <sha>` (`gate.yml:92`).
- **Nguyên nhân.** `build.yml:5-8` và `coverage.yml:8,11` dùng `paths-ignore: ["docs/**","**/*.md"]`;
  `codeql.yml:5-7` dùng `paths` whitelist. Commit không đổi tệp nào ⇒ không có tệp để khớp bộ lọc ⇒
  workflow **không được tạo**, không phải "skipped".
- **Cách xử lý.** Muốn chạy lại CI thật thì **phải đổi một tệp thật**, ví dụ thêm một dòng vào chính tài
  liệu đang sửa (vẫn docs-only, vẫn đúng V11). Hai ngoại lệ dùng được mà không cần commit:
  - sửa **mô tả PR** → event `edited` chạy lại `gate` (`gate.yml:19-22`);
  - `workflow_dispatch` cho các workflow có khai báo (Coverage, CodeQL, compose-stability);
    lưu ý dispatch không tạo lượt `pull_request` nên `gate` **không** nhìn thấy (`gate.yml:80` lọc
    `event=pull_request`).

### Bẫy 3 — V113: `catch`/`runCatching` mới, và cái bẫy SHA 40

- **Triệu chứng.** `build` đỏ ở bước "Chặn handler nuốt lỗi mới (V113, offline)" với dòng
  `duong/dan/Tep.kt:123: catch mới thiếu Diagnostics hoặc // diag: bỏ qua <lý do>`
  (`scripts/kiem-nuot-loi.py:255`). Hoặc chạy tay ra `V113 BLOCKED` (exit 2).
- **Nguyên nhân.** Ba đường duy nhất được chấp nhận:
  1. trong thân handler có lời gọi `Diagnostics.<hàm>(` (`logged()`, `kiem-nuot-loi.py:121-125`);
  2. `catch` chỉ `throw` lại đúng tham số của nó (`direct_rethrow()`, `:139-146`);
  3. comment `// diag: bỏ qua <lý do>` ở **cùng dòng hoặc dòng ngay trên** handler, lý do ≥ 3 ký tự,
     có chữ cái, không chứa `<` hay `>` (`exempt()`, `:128-136`).

  Chữ "Diagnostics." trong chuỗi hoặc trong comment **không** được tính — có test riêng
  (`scripts/test-kiem-nuot-loi.py:34-37`). Script so **handler mới** theo fingerprint, nên copy một
  handler đã có thì không bị tính, còn bỏ `throw e` để thành `catch { null }` thì bị tính
  (`test-kiem-nuot-loi.py:57-59`).
- **Cái bẫy SHA.** `kiem-nuot-loi.py:228-229` chỉ nhận **SHA 40 ký tự**. Truyền `origin/main`,
  `HEAD`, `HEAD~1`… sẽ rơi vào `ValueError` → in `V113 BLOCKED` và **exit 2**
  (`kiem-nuot-loi.py:262-264`). **BLOCKED là không đạt**, đừng đọc là "qua".
- **Cách xử lý.**

  ```bash
  python3 scripts/test-kiem-nuot-loi.py                      # tự kiểm bộ phân tích trước
  base=$(git merge-base origin/main HEAD)
  head=$(git rev-parse HEAD)
  bash scripts/kiem-nuot-loi.sh --base "$base" --head "$head" --pull-request
  ```

  Đây đúng là hai lệnh `build.yml:21-32` chạy (`--pull-request` chỉ thêm khi event là `pull_request`).
  Không có handler mới thì kết quả đúng là `V113 PASS: không thêm handler nuốt lỗi trong phạm vi`
  (`kiem-nuot-loi.py:260`).

### Bẫy 4 — commit rỗng / force-push làm `DIAG_BASE` rơi khỏi lịch sử ⇒ `V113 BLOCKED`

- **Triệu chứng.** `build` đỏ ở bước V113, log chỉ có `V113 BLOCKED: thiếu lịch sử git hoặc không phân
  tích được nguồn`, dù diff mã hoàn toàn không có handler mới. Thường xuất hiện sau khi force-push nhánh
  hoặc push một commit rỗng.
- **Nguyên nhân.** `build.yml:23` lấy `DIAG_BASE = github.event.pull_request.base.sha || github.event.before`.
  Ở event **push**, `before` là head cũ của ref; sau force-push commit đó không còn là tổ tiên của head,
  nên `git merge-base --is-ancestor` (`kiem-nuot-loi.py:239`) trả mã khác 0 → `CalledProcessError` →
  `V113 BLOCKED` (`:262-264`).
- **Cách xử lý ngay.** Đẩy thêm một commit **thật** (không rỗng) để `before` là commit còn trong lịch sử;
  hoặc chỉ tin lượt chạy của event `pull_request` trên đúng head (event này dùng `base.sha`, không dùng
  `before`).
- **Đề xuất sửa (cần PR mã riêng, chưa làm).** Trong `scripts/kiem-nuot-loi.py`, xử lý trường hợp
  `--is-ancestor` thất bại khi **không** có `--pull-request` giống nhánh base `0000…` (`:231-234`):
  lấy `git merge-base refs/remotes/origin/main <head>`; chỉ `BLOCKED` khi không tìm được merge-base.
  Kèm một ca trong `scripts/test-kiem-nuot-loi.py` (base không phải tổ tiên, head hợp lệ ⇒ PASS chứ
  không BLOCKED). Lý do phải sửa: BLOCKED là đỏ, nên một lần force-push là mọi lượt `build` sau đó đỏ
  oan cho tới khi có commit thật.

### Bẫy 5 — `n04-tests` đỏ vĩnh viễn trên nhánh cũ: chỉ rebase mới cứu

- **Triệu chứng.** `n04-tests` đỏ với `can't open file '.../scripts/test-xxx.py'` hoặc thiếu tệp, dù
  nhánh không sửa gì trong `scripts/`. Đẩy thêm commit **không** làm hết đỏ.
- **Nguyên nhân.** `script-tests.yml:22-25` checkout đúng `github.event.pull_request.head.sha`, tức cây
  tệp của **head nhánh**, không phải merge commit. Định nghĩa workflow và danh sách test đến từ bản trên
  nhánh đích, nhưng tệp script thì lấy từ head. Nhánh cũ thiếu script mới thêm trên `main` ⇒ chạy không
  có tệp ⇒ đỏ lại ở mọi lượt.
- **Cách xử lý.** `git merge origin/main` (hoặc rebase lên `origin/main`) rồi đẩy. Chỉ việc đưa `main`
  vào head mới có tác dụng; đẩy lại hay re-run đều vô ích.
- **Ghi chú.** `paths: scripts/**` (`script-tests.yml:5-12`) nên PR docs-only không có cổng này.

### Bẫy 6 — `links` đỏ vì URL ngoài (VietQR), không phải do tài liệu

- **Triệu chứng.** `links` đỏ; log `check-config-links.py` chỉ ra một URL hỏng; PR chỉ sửa tài liệu.
- **Nguyên nhân.** Job kiểm URL thật qua mạng: `periodic-check.yml:26` chạy
  `check-config-links.py --fail-on-error`, timeout 15 giây mỗi URL (`scripts/check-config-links.py:22`).
  Host ngoài chết (VietQR, máy chủ lõi) là đỏ, không liên quan nội dung PR. Hai URL gốc VietQR được dựng
  thành URL đầy đủ trước khi kiểm (`check-config-links.py:25-32`) vì `dl.vietqr.io/pay` trả 404 cho HEAD
  nhưng 200 cho GET (`:141`).
- **Cách xử lý.** Đọc `out/links.md` (artifact `periodic-links`, `periodic-check.yml:27-32`) để biết host
  nào hỏng. Nếu là dịch vụ ngoài: ghi rõ trong PR "`links` đỏ do `<host>`, không do thay đổi này" và báo
  PM. **Không** sửa `config/monika-config.json` để chiều cổng — config-first + `configVersion` + đồng bộ
  Cloudflare là quy trình riêng. PR docs-only không chạy job này (`periodic-check.yml:7-13`).

### Bẫy 7 — `repeated-tests` và `--tests` trên task gộp (đã sửa ở #642)

- **Triệu chứng.** `repeated-tests` đỏ ngay lượt 1/20:
  `> Unknown command-line option '--tests'.` / `FAILURE: Build failed with an exception.`
- **Nguyên nhân.** `app/build.gradle.kts:136` đăng ký `testDebugUnitTest` là **task gộp**:
  `tasks.register("testDebugUnitTest") { dependsOn("testGithubDebugUnitTest", "testPlayDebugUnitTest") }`.
  Task gộp không có action test ⇒ không nhận `--tests`, cũng không sinh
  `app/build/test-results/testDebugUnitTest/`. Test thật gắn vào `sourceSets["testGithub"]`
  (`app/build.gradle.kts:124-126`).
- **Đã sửa.** #642 (merge `0e2c38f`): `scripts/ci-compose-stability.sh` tự dò task thật
  (`:11,15-29`), tự dò thư mục XML (`:63-70`), chép XML thô về đường dẫn workflow đang tải lên
  (`:80-85`).
- **Cách xử lý khi gặp lại.** Chạy tay đúng task thật:
  `./gradlew :app:testGithubDebugUnitTest --tests '<lớp>.<ca>'`. Đừng bao giờ truyền `--tests` cho
  `testDebugUnitTest`.

### Bẫy 8 — `gate` chỉ nhìn một lần

- **Triệu chứng.** Build đỏ, bấm Re-run Build xanh mà `gate` vẫn đỏ; hoặc ngược lại `gate` xanh rồi
  Build chạy lại đỏ mà `gate` vẫn xanh.
- **Nguyên nhân.** `gate` chốt kết quả trong một lượt: mỗi workflow chỉ lấy lượt **mới nhất theo
  `created_at`** (`gate.yml:89`) và thoát ngay khi mọi thứ xanh hoặc có cái đỏ (`:99-100`).
- **Cách xử lý.** Chạy lại workflow đỏ thì phải Re-run **cả `gate`**. Khi báo "CI xanh" luôn ghi kèm
  head SHA, vì kết quả cũ không nói gì về head mới (`docs/BAO-VE-MAIN.md:57`).

## 3. 19 ca test đỏ có sẵn trên `main` (máy Windows/JVM)

Đo trên Windows (14/11/2026) tại `main` `15f210a6`, chỉ thêm `local.properties` (không tính vào git):

```
./gradlew :app:testGithubDebugUnitTest --continue \
  --tests 'vn.aow.monika.RarTest' --tests 'vn.aow.monika.library.DeviceScannerTest' \
  --tests 'vn.aow.monika.runner.CorePreparationCacheTest' --tests 'vn.aow.monika.ConsoleSaveTransferTest' \
  --tests 'vn.aow.monika.GameSaveTransferTest' --tests 'vn.aow.monika.library.ImporterTest' \
  --tests 'vn.aow.monika.library.LibraryDocumentsTest' --tests 'vn.aow.monika.diag.DeathContextTest'
61 tests completed, 19 failed
```

CI Linux (`ubuntu-latest`, `build.yml:52`) vẫn xanh với cùng mã. Đây là **khác biệt môi trường**,
không phải PR của bạn làm hỏng. Đừng "sửa" bằng cách đổi kỳ vọng của test; nếu PR đụng đúng lớp đó thì
ghi rõ trong mô tả PR.

| Số ca | Lớp test (đường dẫn trong `app/src/test/java/vn/aow/monika/`) | Nguyên nhân thật (đọc từ thông điệp lỗi) |
|---|---|---|
| 6 | `RarTest.kt` (`:27,34,40,46,53,63`) | `UninitializedPropertyAccessException: lateinit property app has not been initialized`. `SevenZipNative.init()` gọi `System.loadLibrary("7-Zip-JBinding")` (`app/src/main/java/vn/aow/monika/library/SevenZipNative.kt:42`); trên JVM không có thư viện này nên rơi sang nhánh gói tải, đụng `AppGraph` (`:46-50`) — chỉ có trên máy thật |
| 6 | `library/DeviceScannerTest.kt` (`:55,69,80,90,100,112`) | Đường dẫn Windows dùng `\` còn kỳ vọng và bộ lọc viết bằng `/`: `expected:<[Games/real.md]> but was:<[Games\real.md]>`; ca `:69` còn lọt `Android\data`, `DCIM`, `Music` vì bộ lọc thư mục hệ thống so khớp chuỗi `/` |
| 2 | `runner/CorePreparationCacheTest.kt` (`:93,107`) | `java.io.IOException: Không lưu được lõi melondsds` / `Không lưu được cache hệ thống`. Gốc: `File.renameTo` ở `runner/CoreManager.kt:134` và `runner/SystemFilesCache.kt:62` **không ghi đè** tệp đích đã tồn tại trên Windows |
| 1 | `ConsoleSaveTransferTest.kt:153` | `FileSystemException: ... A required privilege is not held by the client` — `Files.createSymbolicLink` cần quyền tạo symlink (Windows cần Admin/Developer Mode) |
| 1 | `GameSaveTransferTest.kt:253` | Cùng nguyên nhân symlink như trên |
| 1 | `library/ImporterTest.kt:129` | `expected:<...\_TaiVe[/]_phan> but was:<...\_TaiVe[\]_phan>` — so `canonicalPath` với chuỗi nối bằng `/` |
| 1 | `library/LibraryDocumentsTest.kt:61` | `IllegalArgumentException: Failed to find configured root that contains C:\...\files\v84-view\fixture.pdf` — `FileProvider` so khớp gốc theo đường dẫn kiểu Unix |
| 1 | `diag/DeathContextTest.kt:96` | `expected:<[core-ready]> but was:<[start]>` — cập nhật `stage` của phiên chẩn đoán khác nhau trên JVM |

Tổng: 6 + 6 + 2 + 1 + 1 + 1 + 1 + 1 = **19**. Ba nguyên nhân thuần Windows chiếm **12/19** ca:
dấu `\` (8 ca = 6 `DeviceScannerTest` + `ImporterTest` + `LibraryDocumentsTest`), symlink (2),
`renameTo` (2). 7 ca còn lại là `RarTest` (6, thiếu `AppGraph`/thư viện native) và `DeathContextTest` (1).
Con số 19 là số đo trên **một** máy Windows; máy đã bật Developer Mode có thể chỉ còn 17 (2 ca symlink
chạy được), nên hãy đọc theo nhóm chứ đừng đối chiếu tuyệt đối.

Cách kiểm lại nhanh trên máy Windows (đừng chạy cả bộ nếu chỉ cần biết lớp mình đụng):

```bash
./gradlew :app:testGithubDebugUnitTest --continue \
  --tests 'vn.aow.monika.library.ImporterTest'
```

Kết quả XML ở `app/build/test-results/testGithubDebugUnitTest/`. Trên CI Linux, cùng lệnh này xanh —
nên "đỏ trên máy tôi" **không** phải bằng chứng PR hỏng.

## 4. Tự kiểm trước khi push (5 bước)

1. **Bộ phân tích V113 tự nó còn đúng:**
   `python3 scripts/test-kiem-nuot-loi.py`
2. **V113 trên diff thật, bằng SHA 40:** `base=$(git merge-base origin/main HEAD)`;
   `head=$(git rev-parse HEAD)`;
   `bash scripts/kiem-nuot-loi.sh --base "$base" --head "$head" --pull-request`.
   Kết quả đúng là `V113 PASS`. `V113 BLOCKED` (exit 2) là **chưa đạt** — thường do truyền `origin/main`
   thay vì SHA 40 (bẫy 3).
3. **Chạy test của lớp bị ảnh hưởng, đúng task thật:**
   `./gradlew :app:testGithubDebugUnitTest --tests 'vn.aow.monika.<Lớp>'` (bẫy 7). PR docs-only: bỏ qua
   bước này theo V11 (`docs/dac-ta/V193-cong-bao-ve.md:72-77`).
4. **Đọc CI theo head SHA, không đọc theo trí nhớ:** `gate` phải `success`; nếu PR có mã thì `build`,
   `coverage`, và `analyze` (khi đụng tiền tố CodeQL) phải `success` trên **đúng** head. `preview`
   `cancelled` là bình thường (bẫy 1); `digest` `skipped` là bình thường trên PR.
   Nếu phải chạy lại workflow đỏ: Re-run **cả `gate`** (bẫy 8).
5. **Bỏ nháp và ghi đúng head:** chuyển PR sang *Ready for review* (event `ready_for_review` chạy lại
   `gate` — `gate.yml:19-22`), rồi báo PM kèm head `sha8` + số cổng đã xanh.

## 5. Bảng tra nhanh

| Cần biết | Lệnh |
|---|---|
| Cổng đã chạy trên head | `gh api "repos/aowvn-10diem/aowvn-monika/commits/<sha40>/check-runs?per_page=100" --jq '.check_runs[] \| [.name, .conclusion] \| @tsv'` |
| Ruleset + branch protection đang bắt buộc gì | `gh api repos/aowvn-10diem/aowvn-monika/rulesets --jq '.[].name'` rồi xem `rules[].type`; `gh api repos/aowvn-10diem/aowvn-monika/branches/main --jq '.protection'` |
| Check có tệp workflow lẫn không có tệp | `gh pr checks <số> --json name,state` (thấy cả `submit-gradle`) |
| Vì sao workflow không chạy | Đối chiếu `paths`/`paths-ignore` của workflow với danh sách tệp PR (`gh pr view <n> --json files`) |
| Tệp nào khiến `gate` đợi CodeQL | `scripts/gate-expected.py:24-25` (tiền tố) — sửa bộ lọc workflow thì sửa cả tệp này (`docs/BAO-VE-MAIN.md:56`) |
| Job đỏ của `repeated-tests` | `scripts/ci-compose-stability.sh:11` — task thật là `testGithubDebugUnitTest` |

### Đừng làm

- Đừng `git commit --allow-empty` rồi chờ `build` chạy lại (bẫy 2).
- Đừng sửa `.github/workflows/**`, `scripts/gate-expected.py`, `scripts/test-gate-expected.py` trong PR
  thường: phải hai lượt duyệt + sếp (`gate.yml:15-16`, `docs/BAO-VE-MAIN.md:55`).
- Đừng sửa test hay config để làm một cổng ngoài xanh (bẫy 6).
- Đừng coi `V113 BLOCKED` là đạt, và đừng coi test đỏ trên Windows là do PR (mục 3).
