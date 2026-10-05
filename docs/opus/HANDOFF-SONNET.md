# HANDOFF cho Sonnet (phiên thi công) — đọc file này đầu tiên khi phiên mới/phiên mất

> Sao lưu 03/10/2026 (GMT+7). Mục đích: container của phiên có thể bị thu hồi (đã xảy ra một lần: mất bản clone cục bộ, Android SDK, vòng lặp nền, việc chưa commit). Mọi thứ cần để tiếp tục nằm trong repo này, **không có bí mật**.
> Chủ dự án gọi là **"sếp"**. Đọc tiếp: `CLAUDE.md` (luật dự án) → `docs/opus/KE-HOACH.md` (bảng việc, nguồn sự thật) → `docs/opus/BANG-TIN.md` + `docs/opus/hop-thu/README.md` (kênh với Opus).

## 1. Cách làm việc với sếp (bắt buộc)
- Xưng **"sếp"**, trả lời **tiếng Việt**, **kết luận trước**, trực tiếp, không rào đón. Nội dung giao sếp luôn đầy đủ (chỉ nén phần suy nghĩ/meta).
- **Không đoán** số liệu/fact: không chắc thì hỏi. Việc lặp ≥ 2–3 lần → đề xuất dựng script.
- Giờ báo cáo luôn theo **GMT+7**. Không dán token/khóa/mật khẩu vào chat hay repo.
- Gửi file cho sếp: `SendUserFile` (≤ 30 MiB) hoặc Pixeldrain (`scripts/pixeldrain-upload.sh`, key do sếp cấp, không ghi vào repo). Mỗi phiên bản Monika chỉ phát hành **một APK universal**.
- Commit kết thúc bằng 2 dòng: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` và `Claude-Session: <URL phiên>`. Tiền tố commit việc: `[viec-<mã>]`; thư Opus: `[hỏi-opus]`, `[xong-opus]`.
- Sếp **đã nhận việc công khai mã nguồn và giấy phép** (cổng G5, G8 trong KE-HOACH) — không hỏi lại, nhưng **không phát hành gói GPL mới lên repo packs công khai** cho tới khi sếp báo xong (dùng release riêng tư của repo này để thử).
- Opus = PM thay sếp (chọn hướng, thứ tự, duyệt cổng). Sonnet = thi công việc khó. Haiku = việc nhẹ (dòng `Giao: Haiku` trong KE-HOACH). Việc ngoài kỹ thuật (tiền, pháp lý, khóa ký, máy thật, game thử) vẫn do sếp.

## 2. Môi trường & hạ tầng
| Mục | Sự thật |
|---|---|
| Repo | `aowvn-10diem/aowvn-monika` (công khai). Clone: `git clone https://github.com/aowvn-10diem/aowvn-monika /home/user/aowvn-monika`; đặt `git config user.name Claude; user.email noreply@anthropic.com`. Repo packs công khai (asset release): `aowvn-10diem/aowvn-monika-packs`. |
| Local | Container **không có Android SDK/Gradle cache** sau khi tái tạo → biên dịch/test bằng **CI** (đẩy nhánh → workflow `Build`). Nhánh làm việc: tạo `viec-<mã>` rồi merge `--no-ff` vào `main` khi xanh. |
| Gradle test | `./gradlew testDebugUnitTest` (cần SDK; nếu có thể). `build.yml` bỏ qua commit chỉ sửa `docs/**`/`*.md`. |
| Ký APK / phát hành | Workflow Release build và ký APK chính thức trên CI bằng GitHub Secrets đã được sếp xác nhận cấu hình ngày 04/10; thiếu cấu hình thì job dừng trước khi dựng APK và workflow chặn chứng chỉ Android Debug. Kiểm chữ ký bằng `apksigner verify --print-certs`; SHA-256 của v0.7.4 là `c46902e9…d45ab20c`. |
| Secrets | Các giá trị `PACKS_TOKEN`, `PIXELDRAIN_API_KEY`, `CLOUDFLARE_API_TOKEN` không ghi vào repo. `MONIKA_KEYSTORE_*` đã được sếp xác nhận cấu hình trong GitHub Secrets ngày 04/10; trạng thái `PIXELDRAIN_API_KEY` và `CLOUDFLARE_API_TOKEN` hiện [CHƯA KIỂM]. |
| Opus | Phiên ngoài, trả lời trên **nhánh** `docs/opus-tra-loi` (không push được `main`) — gộp bằng `git pull --no-rebase origin docs/opus-tra-loi`. `BANG-TIN.md` xung đột thì giữ hàng của cả hai. |
| Lịch tự kiểm | `mcp__Claude_Code_Remote__send_later` (15–20 phút, rảnh 4 lượt liền → 30, 23:00–06:00 GMT+7 → 60). Mỗi lượt: (1) `git ls-remote origin` so SHA nhánh Opus; (2) CI; (3) việc đang chờ. Vòng lặp nền trong container sẽ mất khi container bị thu hồi. |

## 3. Workflow có sẵn (dispatch bằng `mcp__github__actions_run_trigger`)
`build.yml` (push) · `release.yml` (`tag`) · `emulator-test.yml` (`apis`, `rgss_tag`) · `build-kirikiri.yml` (`publish`) · `build-rgss.yml` (`abi`, `port_ref`, `publish`, `private_only`) · `build-renpy-pack.yml` (`version`, `publish`) · `spike-rgss.yml` · `spike-renpy.yml` · `sync-config.yml` · `native-check.yml` · `test-lab.yml` · `build-engines.yml` · `mirror-pack.yml`/`publish-pack.yml` · `check-packs.yml` · `periodic-check.yml` · `spike-arm-emulator.yml` · `publish-cores.yml` (`number`) · `pm-digest.yml` (schedule, repo events, manual).

## 4. Bài học đã trả giá (đừng lặp lại)
- `list_workflow_runs` **bỏ qua `per_page`** → luôn dùng `workflow_runs_filter` (`{"branch":"…"}` hoặc `{"created":">2026-10-03T00:00:00Z"}`); release.yml không lọc trả hàng chục lượt rất dài.
- Log CI: `get_job_logs` với `tail_lines` 75–130 mới thấy lỗi thật; `failed_only` cần `run_id`. Artifact/log tải qua `gh api` bị chặn redirect.
- YAML: tên bước có `: ` (dấu hai chấm + cách) làm workflow hỏng im lặng (run hiện tên = đường dẫn file). Kiểm `python3 -c "import yaml;yaml.safe_load(open(f))"` trước khi push.
- `pkill -f <mẫu>` có thể giết chính shell của lệnh → lưu PID rồi `kill`.
- `get_deps.sh` của bản port không có quyền thực thi → chạy `bash`; thiếu `make_xxd.sh` (README bỏ sót). Gói Ren'Py: RAPT **không** chứa `private/` → dùng `launcher distribute --no-archive`.
- Đừng nối `test; commit && push` mà không kiểm mã thoát test (từng làm phát hành khi test đỏ).
- Chữ ký, `configVersion` (tăng khi sửa `config/monika-config.json`), `modules.*` phải nằm đúng chỗ — `ConfigTest` chặn.
- Test tải trình duyệt từng đỏ "ngẫu nhiên": gốc là race tên file (đã sửa, có test tái hiện).

- **Hạn mức lưu trữ Actions đã đầy một lần (03/10/2026, 14 GB artifact, chủ yếu APK debug 83 MB mỗi lần push):** đã xóa 370 artifact bằng `curl -X DELETE https://api.github.com/repos/aowvn-10diem/aowvn-monika/actions/artifacts/<id>` (proxy tự gắn quyền); GitHub tính lại hạn mức mỗi 6–12 giờ nên có thể còn chặn tải lên một lúc. Đã đặt retention ngắn + `continue-on-error` cho mọi `upload-artifact` — job không đỏ chỉ vì tải artifact lỗi. Workflow cần artifact ở job khác (hiện không có) sẽ phải dùng release.

## 5. Trạng thái (cập nhật 04/10/2026)
- **Phát hành:** v0.7.4 (versionCode 39; nguồn: `app/build.gradle.kts`). Cấu hình trong main hiện `configVersion` 34 (nguồn: `config/monika-config.json`); thay đổi cấu hình #67/#73 không tạo APK mới.
- **RPG Maker XP/VX/Ace (V16/#73):** `systems.rgss.engine="rgss"` chọn engine nhúng; cấu hình ghim gói `engines-rgss-6` cho `arm64-v8a`. JoiPlay là dự phòng khi thiết bị không hỗ trợ ABI này; nếu tải gói lỗi, màn chuẩn bị báo lỗi để thử lại, không tự chuyển sang app ngoài. Gameplay máy thật, âm thanh, lưu/tải và FPS [CHƯA KIỂM].
- **Đã xong:** E0, R0–R4, P0–P2, V09–V13. R2/R3 đã gộp main; kết quả thử R3 API 30/34 ở `docs/opus/ket-qua/R3.md`.
- **Ren'Py 8:** P0/P1 đã có kết quả trong `docs/opus/ket-qua/P0.md`; P2 (module `:renpy`) đã gộp trong #33 và kết quả APK ở `docs/opus/ket-qua/P2.md`. P3 (#41) đã gộp: `RenpyGameActivity` chạy trong `:game`, gọi `RenpyBase.resolve(entry)`, dùng `EngineRoutes` → `PackManager.RENPY8` và `RenpyOverlay`. `config/monika-config.json` vẫn chọn `runner: external` + `joiplay`, nên route nhúng chưa bật; game Ren'Py thật [CHƯA KIỂM].
- **Libretro V32:** Snapshot [cores-gb-gba-nes-1](https://github.com/aowvn-10diem/aowvn-monika-packs/releases/tag/cores-gb-gba-nes-1) đã phát hành qua [run 37196834615](https://github.com/aowvn-10diem/aowvn-monika/actions/runs/37196834615), gồm 12 ZIP + manifest, không đặt latest. PR [#67](https://github.com/aowvn-10diem/aowvn-monika/pull/67) đã gộp: cấu hình ghim URL/hash/version cho GB/GBA/NES theo 4 ABI, tăng configVersion 32→33 và giữ URL/version legacy; bằng chứng ở `docs/opus/ket-qua/V32.md`. Chơi game/ROM thật và đồng bộ Cloudflare [CHƯA KIỂM]. Không dispatch publish/deploy thêm.
- **Thứ tự việc còn lại:** lấy từ kế hoạch PM hiện hành; ghi chú thứ tự cũ bên dưới không thay thế kế hoạch đó.
- **Cổng sếp (KE-HOACH mục 4):** G1 (1 game RPG Maker XP), G2 (5 game R6 + máy thật), G3 (2 game Ren'Py 7/8), G4 (Symbian A1/A2), G5/G8 (công khai mã nguồn + giấy phép build file bản port mkxp-z — **sếp tự làm**), G6 (nhắn RAdmin duyệt client "AowMonika"), G7 (thử lại Kirikiri 0.7.4 trên máy thật với Kara no Shoujo; lỗi 0.7.3 vẫn được ghi trong kế hoạch).
- **Haiku (việc nhẹ, dòng `Giao: Haiku`):** H01 (sửa đầu `GIAO-TIEP-VOI-OPUS.md`), H02 (gen-architecture), H03 (mục thử Kirikiri trong `TEST-MAY-THAT.md`).

## 6. Tài nguyên đã sao lưu trong repo
- Phương án Opus: `docs/opus/2026-10-03-nhung-renpy-rgss.md`, `2026-10-03-nhung-symbian-eka2l1.md`; thư: `docs/opus/hop-thu/`; kết quả: `docs/opus/ket-qua/{R0,R1,P0}.md`.
- Plan RPG Maker gốc của sếp (v3, đã bị phương án Opus thay thế nhưng chứa bảng nguồn đã xác minh): `docs/opus/tai-nguyen/PLAN-rpgmaker-monika-v3.md`.
- Tài liệu dự án: `docs/KIEN-TRUC*.md`, `docs/plan-*.md`, `docs/HUONG-DAN-QUAN-TRI.md`, `docs/CAP-NHAT.md`, `CLAUDE.md`.
- Prompt cho phiên khác: `docs/opus/hop-thu/PROMPT-OPUS.md`, `PROMPT-HAIKU.md`.
- **Không sao lưu (cố ý):** khóa ký, token, key Pixeldrain/Cloudflare, ảnh/zip kết quả test cũ, file `.key` trong thư mục upload của sếp.

## 7. Khởi động lại phiên (làm theo thứ tự)
1. Clone repo, đặt git identity (mục 2). 2. `git fetch --all`; xem nhánh `viec-*`, `docs/opus-tra-loi`. 3. Đọc `KE-HOACH.md` (việc `đang làm`), `BANG-TIN.md` (thư `mở`). 4. Kiểm CI nhánh đang làm bằng `workflow_runs_filter`. 5. Đặt lại `send_later` (mục 2). 6. Báo sếp ngắn: đã phục hồi, đang ở việc nào.
