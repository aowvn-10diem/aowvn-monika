# Kế hoạch & bảng việc Aow Monika

> Chủ: Opus (PM, sếp giao 03/10/2026). Đội thi công: **Sonnet** (việc khó: native, engine, luồng app), **Sol** (từ 03/10: CI, chẩn đoán, tài liệu; nộp qua PR, `PROMPT-SOL.md`) và **Haiku** (việc nhẹ, máy móc: tài liệu, kiểm tra, sửa nhỏ có test sẵn). Sếp quyết việc ngoài kỹ thuật (mục 4).
> Đây là **nguồn sự thật duy nhất về tiến độ**. Mỗi phương án chi tiết nằm trong `docs/opus/<ngày>-<chủ đề>.md`; bảng này chỉ trỏ tới.
> Cập nhật: 03/10/2026 15:10 (GMT+7), `main` @ `687c537`, app 0.7.3 (versionCode 38), `configVersion` 31.

## 1. Mốc

| Mốc | Nội dung | Phương án | Trạng thái |
|---|---|---|---|
| M0 | Kirikiri 0.7.3 (menu tiếng Việt) phát hành | — | **xong** (đã gửi sếp APK); máy thật **[CHƯA KIỂM]** (G7) |
| M1 | RPG Maker XP/VX/Ace nhúng (khối R), tắt app ngoài cho `rgss` và `kirikiri` | `2026-10-03-nhung-renpy-rgss.md` | R0, R1 (gói 7,6 MiB), R2, R3 (API 30+34), R4, gói v7a xong · tiếp R5 (V16) |
| M2 | Ren'Py 8 nhúng (khối P) | như trên | P0, P1 xong (gói `renpy8` arm64 22,6 MB) · P2 mở (V17) |
| M3 | Ren'Py 7 (khối P7) | như trên | chờ M2 |
| M4 | Symbian/N-Gage nhúng (khối S) | `2026-10-03-nhung-symbian-eka2l1.md` | chờ sếp chốt A1, A2 · làm sau M2 |
| Q | Phương án cho câu 6 và 7 của mục 7 (kiểm thử khi không có máy thật; chất lượng crash log) | `2026-10-03-kiem-thu-chan-doan.md` | **phương án xong** (03/10) · thi công V18–V22 |
| — | Câu 1, 2, 3, 4 của mục 7 (tách module, dịch offline, Kirikiri 32-bit, RA cho NDS/N64/PS1) | chưa có | tồn đọng, xếp sau M2 |

Thứ tự giao hàng (Q3 của sếp): **M1 → M2 → M3 → M4**. Spike chỉ dùng CI, không đụng `:app`, thì được chạy song song.

## 2. Bảng việc đang mở

Trạng thái: `chờ` · `đang làm` · `kẹt` (kèm số thư) · `xong` (kèm commit). Ai làm thì người đó sửa dòng của mình, trong cùng commit `[viec-<mã>]`. **Sol và Haiku không sửa bảng này trong PR; PM ghi trạng thái theo PR.**

| Mã | Việc | Giao | Phụ thuộc | Cách kiểm | Trạng thái |
|---|---|---|---|---|---|
| V01 | R0: spike mkxp-z ra `libmkxp-z.so` arm64, ghi `docs/opus/ket-qua/R0.md`. Ngưỡng dừng: 5 nguyên nhân hỏng khác nhau (đã dùng 2) | Sonnet | — | Artifact `.so` + bảng kích thước | xong (7ec1c47: R0.md; còn cổng G1 hỏi sếp 1 game XP) |
| V02 | R1: `build-rgss.yml` + `engines/rgss/`, áp `hop-thu/tra-loi-001.md` | Sonnet | V01 | `grep`/`nm`/`strings` theo thư 001; máy sạch dựng ra `rgss-{abi}.zip` | xong (run 3 xanh; R1.md: gói rgss-arm64 7,6 MiB, JNI+OpenSSL kiểm đạt; chưa publish) |
| V07 | R1.1 theo `tra-loi-003.md`: bỏ OpenSSL (`-DMKXPZ_SSL`, `openssl` của Ruby), cắt ký hiệu rồi đo lại `libSDL2_ttf.so`; theo `tra-loi-004.md`: ghim commit bản port, ghi trạng thái giấy phép vào `UPSTREAM.md` | Sonnet | V02 | `readelf`/`strings` không còn OpenSSL; bảng kích thước trước/sau; gói `rgss` arm64 ≤ 15 MB | xong (PM đóng 03/10 theo `2ee93cc`: bỏ OpenSSL + bước kiểm trong `build-rgss.yml`; ghim `b668e08` và trạng thái giấy phép trong `engines/rgss/UPSTREAM.md`; gói arm64 7,6 MiB, v7a 7,1 MiB đều ≤ 15 MB nên không cần đo riêng `libSDL2_ttf.so`) |
| V08 | R2: module `:rgss` (9 file Java SDL 2.26.3 đổi gói `vn.aow.monika.rgss.sdl`, bước 3 của thư 001) | Sonnet | V02 | `assembleRelease` xanh; `dexdump` thấy cả hai `SDLActivity`; đo APK tăng | xong (6f0623e: module :rgss biên dịch, :app + toàn bộ test xanh trên CI; chưa đo APK tăng) |
| V03 | P0: spike Ren'Py, ghi `docs/opus/ket-qua/P0.md` | Sonnet | — | Bảng đường dẫn + kích thước thật | xong (P0.md, run 5 xanh) |
| V04 | P1: `build-renpy-pack.yml` (song song, chỉ CI) | Sonnet | V03 | Artifact `renpy8-{abi}.zip` + `.sha256` | xong (9d67241, run 1 xanh: renpy8-arm64-v8a.zip ≈ 22,6 MB, SHA-256 29c32501…a554c; armv7 cùng run; chưa publish — chờ cổng G5) |
| V05 | Phát hành Kirikiri 0.7.3 | Sonnet | — | Release có APK universal, SHA-256 chữ ký đúng (`CLAUDE.md`) | xong (v0.7.3, chữ ký c46902e9…d45ab20c khớp, APK 18,1 MiB đã gửi sếp) |
| V06 | Mỗi lượt kiểm tra: gộp nhánh `docs/opus-tra-loi` vào `main` (quét mọi nhánh, theo README hộp thư) | Sonnet | — | `main` có các commit `[opus]` mới | lặp lại |
| V09 | Trả lời `hop-thu/pm-hoi-001-kiem-tra-it-token.md` | Sonnet | — | Có `pm-tra-loi-001.md` | xong (pm-tra-loi-001.md) |
| V10 | Tìm **gốc** lỗi test `BrowserDownloadTest.confirmBeforeFinishStillMovesWhenDone` (dòng 74; đỏ ở run 37071652777, đã đỏ nhiều lần trước đó). "Chập chờn" không phải nguyên nhân: đọc thông điệp `state=… confirmed=…` trong báo cáo test, sửa ở `BrowserDownloads` hoặc ở test. Gợi ý: `return@repeat` trong `repeat {}` chỉ bỏ qua một vòng, **không thoát vòng lặp** (dòng 61 và 72) | Sonnet | — | Chạy riêng test này 20 lần liền (`--tests …` trong vòng lặp shell) đều xanh | xong (gốc = race tên file: Content-Disposition đè tên người dùng; sửa BrowserDownloads.kt; 14/14 xanh dưới tải; test tái hiện đúng race, đỏ khi hoàn nguyên bản sửa) |
| V11 | `build.yml`: thêm `paths-ignore: ['docs/**', '**/*.md']` cho `push` và `pull_request`, để commit chỉ sửa tài liệu không chạy Gradle (mỗi lượt khoảng 3 phút runner) | Sonnet | — | Push thử một commit chỉ sửa `docs/` thì không có run `Build` mới | xong (build.yml paths-ignore docs/** và *.md; commit chỉ sửa tài liệu không chạy Build) |
| V12 | R3: `RgssGameActivity` (`:game`, hợp đồng JNI, nạp `.so` theo `manifest.json`) + Emulator Test với "game" XP tối thiểu tự sinh (`hop-thu/tra-loi-005.md` bước 3) | Sonnet | V08 | API 30 và 34: có `MonikaGame: rgss-lib-loaded` + file `monika-ok.txt`; không có `Failed to register methods` | xong (API 34 run 17, API 30 run 18 @ `c6146d3`; `ket-qua/R3.md`) |
| V13 | `.gitignore` thêm `.kotlin/`; gỡ `.kotlin/sessions/*.salive` khỏi repo | Sonnet | — | `git ls-files .kotlin` rỗng | xong (.kotlin/ trong .gitignore, gỡ khỏi repo) |
| V14 | `emulator-test.yml`: release tạm `ci-apk-<run_id>` (commit `62a4a70`) phải tự xóa cuối job: thêm bước `if: always()` chạy `gh release delete "ci-apk-${{ github.run_id }}" --yes --cleanup-tag \|\| true`. Không để release tạm tích lại lẫn với release thật `v*` | Sonnet | — | Sau một lần chạy Emulator Test, `gh release list` không còn `ci-apk-*` | xong (run 18: job `don-dep` xanh, không còn `ci-apk-37098139874`; 2 release tạm của run 16, 17 có trước V14 còn lại, sếp xóa tay được, proxy của Sonnet không có quyền xóa) |
| V15 | `build-rgss.yml`: thêm `armeabi-v7a` vào ma trận ABI (`tra-loi-003.md`, bắt buộc trước R7). Không đổi mã, cùng vá SDL và cùng cờ bỏ OpenSSL như arm64 | Sonnet | V12 | Artifact `rgss-armeabi-v7a.zip`; `readelf -h` các `.so` ra `Machine: ARM`; `llvm-nm -D libSDL2.so` có `Java_vn_aow_monika_rgss_sdl_*`, không có `Java_org_libsdl_app_*`; ghi kích thước vào `ket-qua/R1.md`. Chạy thật trên máy 32-bit **[CHƯA KIỂM]** (máy ảo CI là 64-bit) | xong (build-rgss run 5, release riêng tư `engines-rgss-5`, `rgss-armeabi-v7a.zip` 7.455.174 byte; các kiểm `readelf`/`llvm-nm` nằm trong workflow, xanh; máy 32-bit thật **[CHƯA KIỂM]**) |
| V16 | R5 theo phương án Ren'Py/RGSS (dòng R5): lớp phủ + phím (`pad: "rpg"`, nối `SDLActivity.onNativeKeyDown/Up`), menu Monika qua `ComposeHost`, `PackManager.installers` (`rgss`), `EngineRoute`, config `modules.rgss` + `systems[rgss].engine` + `entry`, tăng `configVersion`. **Giữ `allowExternalApp: true` cho `rgss` và `kirikiri`**: tắt ở R7 (cần V15 + G8). Không phát hành bản app nào có `systems[rgss].engine` khi gói `rgss` chưa publish; gói thử để ở release riêng tư như V12 | Sonnet | V12 | Test giao diện Robolectric cho lớp phủ (ảnh trong `anh-chup-giao-dien`); `ConfigTest` đọc được config mới và cũ; Emulator Test `rgss_tag`: bấm phím ảo → game nhận phím (game XP tự sinh in phím vào file) | một phần (xem `ket-qua/R5.md`): phím ảo + menu xong, Emulator Test API 34 KEY_OK; phần config hoãn chờ gói rgss công khai (G5/G8) |
| V17 | P2: module `:renpy` (dòng P2 của phương án Ren'Py/RGSS): Java của **đúng RAPT 8.5.3**, bản vá ở `renpy/patches/` (bỏ Play Asset Delivery, `renpyiap`, `slf4j`), `build.gradle` Groovy riêng, `UPSTREAM.md`, chỉ sửa chỗ `Aow Monika:`. Xem U8 (`compileSdk`) | Sonnet | V12 | `./gradlew :renpy:assembleRelease` và `assembleRelease` của app xanh; `dexdump` thấy cùng lúc `org.libsdl.app.SDLActivity` (Ren'Py) và `vn.aow.monika.rgss.sdl.SDLActivity` (RGSS); ghi mức APK tăng vào `ket-qua/P0.md` | chờ |
| V18 | Game kiểm thử Kirikiri tự sinh, bước K1–K8 của `2026-10-03-kiem-thu-chan-doan.md` (vẽ, chạm, Back, âm thanh, lưu/tải qua lần chết tiến trình, quay lại sau Home); `emulator-options` bỏ `-noaudio` | Sonnet | — | Emulator Test API 30+34 xanh với đủ file dấu `monika-*.txt`; K6 không chạy được âm thanh thì ghi lý do vào `ket-qua/` và để K6 "chỉ báo" | chờ |
| V19 | Bài kiểm đường báo lỗi K10: `kill -11` tiến trình `:game` → báo cáo native có `component`/`stage`/`crumbs` đúng; `kill -9` chỉ ghi kết quả | Sonnet | V18 | Emulator Test xanh, in nội dung báo cáo JSON vào log | chờ |
| V20 | D1 ngăn xếp đọc được: (1) **từ lần phát hành tới** đính `mapping-<tag>.txt` vào release + retrace trong `crash-reports.sh`; (2) workflow dựng gói giữ ký hiệu `.so` (`symbols-*.zip`, bảng BuildId); (3) đọc tombstone protobuf đúng trường (lấy số trường từ `tombstone.proto` AOSP); (4) API 30: thêm dòng `DEBUG` quanh lúc chết | Sol | — (phần 3–4 sau V19) | Retrace ra tên lớp gốc; báo cáo K10 trên API 34 có `build_id` + `rel_pc`, `crash-reports.sh` in tên hàm | PR #6 (phần 1–2): PM yêu cầu sửa 1 điểm (bước ký hiệu không được chặn việc phát hành gói); SOL-002 đã trả lời (chọn A). Phần 3–4 sau V19 |
| V21 | D5 nút "Báo lỗi game này" trong menu game (ảnh `PixelCopy` + mô tả + vệt + logcat 60 giây) + D4 thêm thông tin môi trường. Đọc mã Worker trước (giới hạn ảnh) | Sonnet | — (A1 đã chốt 03/10: ảnh mặc định bật, chọn loại lỗi bằng nút, gõ tùy chọn) | Test Robolectric màn gửi; Emulator Test tạo được báo cáo `kind=user` kèm ảnh; unit test chuỗi `env` | chờ |
| V22 | D2 mẫu lỗi engine trong config (`engines.<id>.errorPatterns`, mặc định rỗng) + D3 theo dõi màn đen bằng `PixelCopy` + D6 `crash-reports.sh --by-fp` | Sonnet | V19 | Script XP tự sinh `raise "monika-ci"` → có báo cáo `engine:rgss`; K3 bình thường không tạo báo cáo màn đen | chờ |
| V23 | Kho artifact của Actions lại đầy (Emulator Test run 20 không tải lên được `ket-qua`, mất log gỡ lỗi). Gốc: 7 workflow tải artifact **không đặt `retention-days`** (mặc định giữ rất lâu): `build-engines`, `build-kirikiri`, `build-renpy-pack`, `build-rgss`, `spike-renpy`, `spike-rgss`, `test-lab`. Sửa: (1) thêm `retention-days: 3` cho mọi `upload-artifact` (gói đã có bản ở release riêng tư); (2) workflow `don-dep-actions.yml` (`workflow_dispatch` + cron hằng ngày, `permissions: actions: write, contents: write`, dùng `GITHUB_TOKEN`) xóa artifact cũ hơn 2 ngày và mọi release `ci-apk-*` (gồm 2 release của run 16, 17) | Sol | — | `grep` mọi `upload-artifact` đều có `retention-days`; chạy tay workflow dọn: hết `ci-apk-*`, Emulator Test kế tiếp tải được `ket-qua-api-*` | PR #5 PM duyệt; Sol đã gộp main vào nhánh (`f2191ac`), chờ Sol gộp PR → PM kiểm run "Dọn dẹp Actions" |
| V24 | Chuẩn bị công khai repo (G5/G10): khi repo công khai, mọi release đều công khai. Gói `rgss` dựng bằng script bản port chưa có giấy phép (G8) nên **chưa được phát hành công khai**. Sửa `build-rgss.yml`: bước "Đưa lên Releases" và release `symbols-rgss-*` tạo bằng `gh release create --draft` (bản nháp chỉ người có quyền ghi thấy) cho tới khi G8 xong. `emulator-test.yml`: tải gói rgss từ release nháp bằng `GITHUB_TOKEN` | Sol | — | Chạy `build-rgss` → release mới ở trạng thái Draft; Emulator Test với `rgss_tag` đó vẫn tải được gói. Trong lúc chưa gộp V24, **không chạy `build-rgss` với `publish=true`** | PR #7 PM duyệt (03/10 17:10); gộp sau PR #5 |
| H01 | Sửa đầu `docs/GIAO-TIEP-VOI-OPUS.md` thành 0.7.3 / versionCode 38 / `configVersion` 31. Mục 4: thêm dòng "E0 xong", "R4 xong" | Haiku | — | Diff chỉ đụng file đó | đạt trong PR #4; gộp cùng H03 khi PM duyệt PR #4 |
| H02 | Chạy lại `python3 scripts/gen-architecture.py`, commit `docs/KIEN-TRUC.md`. Nếu bảng module vẫn thiếu `:libretrodroid`/`:kirikiri` thì **không sửa script**: ghi `kẹt` và gửi thư | Sol | — | `git diff` chỉ đụng `docs/KIEN-TRUC.md` | Haiku kẹt đúng luật (thư 006); PM đã trả lời `tra-loi-006.md`: sửa script đọc `include(...)` trong `settings.gradle.kts`. Giao Sol |
| H03 | `docs/TEST-MAY-THAT.md`: thêm mục "Kirikiri (0.7.3)" cho sếp thử: mở game `.xp3` → màn chuẩn bị tự tải gói → vào game → chạm, âm thanh, lưu/tải, tua nhanh, menu tiếng Việt, thoát. Máy 32-bit phải thấy thông báo chưa hỗ trợ. Nguồn: `docs/plan-kirikiri.md` + mục 4 của tài liệu bàn giao | Haiku | — | Chỉ đụng tài liệu; mỗi dòng là một thao tác sếp làm được | PR #4: Haiku tự sửa theo comment PM (6b.2: thông báo "chưa hỗ trợ máy 32-bit", không tải gói; 6b.6: "Tua nhanh" giữ Ctrl, không có 2x; 6b.7: đúng menu `KirikiriOverlay`), rồi gộp main vào nhánh PR (lấy bản main của `KE-HOACH.md`, chỉ đổi trạng thái H01/H03) |
| H04 | `docs/GIAO-TIEP-VOI-OPUS.md` mục 4: thêm các dòng R0, R1 (gói rgss 7,6 MiB), R2, R3 (API 30+34), R5 (phím ảo + menu), gói v7a 7,1 MiB, "repo công khai 03/10". Nguồn duy nhất: `docs/opus/ket-qua/R0.md`, `R1.md`, `R3.md`, `R5.md` và bảng này; không thêm số không có trong nguồn | Haiku | PR #4 đã gộp | Diff chỉ đụng file đó; mỗi dòng mới trỏ tới file `ket-qua` tương ứng | chờ |
| H05 | `docs/TEST-MAY-THAT.md`: thêm mục "RPG Maker XP/VX/Ace (nhúng)", ghi rõ chỉ áp dụng khi app có gói `rgss` (sau R7). Các thao tác: mở game → phím ảo (D-pad, A/B, START/SELECT) → menu Monika (Chơi tiếp · giữ Shift · độ mờ phím · Thoát) → nút Back mở/đóng menu → lưu/tải trong game → máy 32-bit. Nguồn: `docs/opus/ket-qua/R5.md` và `runner/RgssOverlay.kt` | Haiku | — | Chỉ đụng tài liệu; mỗi dòng là một thao tác sếp làm được; tên nút đúng như trong `RgssOverlay.kt` | chờ |
| H06 | Test ảnh chụp (Robolectric) cho `RgssOverlay` và `KirikiriOverlay` theo đúng mẫu `app/src/test/java/vn/aow/monika/ui/InGameTest.kt` (đồng hồ chỉnh tay, `rule.shot(...)`): mỗi lớp phủ 2 ảnh (đóng menu, mở menu). Không sửa mã app; nếu lớp phủ không dựng được trong test thì ghi `kẹt` và gửi thư | Haiku | — | `./gradlew testDebugUnitTest` xanh; có ảnh mới trong artifact `anh-chup-giao-dien` | chờ |
| H07 | `docs/KIEN-TRUC-tay.md`: thêm mục "Engine nhúng (Kirikiri, RPG Maker)": luồng `EngineRoutes` → `EnginePrepActivity` → `PackManager` → `*GameActivity` (tiến trình `:game`) + lớp phủ; bảng "sửa X thì mở file nào". Chỉ ghi file có thật trên main (kiểm bằng `git ls-files`) | Haiku | — | Chỉ đụng tài liệu; mọi đường dẫn trong mục mới đều tồn tại | chờ |
| O01 | Trả lời hộp thư; cập nhật bảng này sau mỗi mốc | Opus | — | `BANG-TIN.md` không còn thư `mở` quá 1 lượt kiểm tra | lặp lại |
| O02 | Phương án câu 6 + 7 (kiểm thử không máy thật, crash log) | Opus | — | File `docs/opus/<ngày>-kiem-thu-chan-doan.md` | xong (`docs/opus/2026-10-03-kiem-thu-chan-doan.md`) |

**Thứ tự (03/10 16:35, repo đã công khai, CI chạy lại).** Sonnet: Emulator Test API 34 cho thăm dò tầng V18 (chạy **không** kèm `rgss_tag`, vì release rgss cũ đã xóa) → sửa V18 → V19 → R6; V17 xen kẽ. **Sol:** V24 trước tiên (release rgss dạng nháp; chưa gộp V24 thì không ai chạy `build-rgss` với `publish=true`) → gộp PR #5 → sửa PR #6 → H02 (sửa script theo `tra-loi-006.md`). **Haiku** (tự kiểm thư mỗi giờ, nộp qua PR từ nhánh `haiku/<mã>` theo `PROMPT-HAIKU.md`): sửa PR #4 (H03) → H04 → H05 → H06 → H07.

Việc tiếp theo của từng khối (R2–R7, P2–P7, S0–S7) nằm sẵn trong file phương án. Khi một việc trên `xong`, PM thêm việc kế tiếp vào bảng; đội thi công không tự mở việc ngoài bảng.

## 3. Luật giao việc

| Ai | Được làm | Không được làm |
|---|---|---|
| Sonnet | Mọi việc `Giao: Sonnet`; sửa nhỏ để CI xanh | Đổi hướng hay thứ tự mốc; phát hành gói GPL mới trước cổng G5 |
| Haiku | Chỉ việc `Giao: Haiku`, đúng phạm vi ghi trong dòng việc | Sửa mã native, workflow build, `config/monika-config.json`, khóa ký. Việc to hơn mô tả → ghi `kẹt` và gửi thư, không tự làm rộng ra |
| Sol | Chỉ việc `Giao: Sol`; nộp bằng PR từ nhánh `sol/<mã>`, tự gộp khi Build xanh + PM comment "PM duyệt" | Push thẳng `main`; phát hành, tag, khóa ký, secrets; sửa dòng việc của người khác; đổi hướng hay thứ tự mốc |
| Opus | Lên kế hoạch, viết phương án, trả lời thư, duyệt qua pha, giao việc | Sửa mã app, push `main`, phát hành, chạm bí mật |

Chạy `./gradlew testDebugUnitTest` trước mọi push có đụng mã (`CLAUDE.md`). Việc chỉ đụng tài liệu thì không cần.

## 4. Cổng cần sếp quyết

Sonnet hỏi sếp khi tới bước cần; mỗi lần một câu.

| Mã | Cổng | Khi nào | Trạng thái |
|---|---|---|---|
| G1 | 1 game RPG Maker XP để chạy thử (R3) | Ngay (V01 đã xong) | Sonnet hỏi sếp |
| G2 | 5 game cho R6 (XP, VX, VX Ace `.rgss3a`, Pokémon Essentials, game Việt hóa có dấu) + thử máy thật, hoặc chấp nhận kết quả "chỉ máy ảo" | R6 | chờ |
| G3 | 2 game Ren'Py (một bản 7, một bản 8) | P6 | chờ |
| G4 | Symbian: A1 (chạy S0 sớm?), A2 (đủ 3 đường nhập firmware?) — mục 8 của phương án Symbian | Bất kỳ lúc nào | chờ sếp |
| G5 | Công khai mã nguồn Monika trước khi phát hành gói GPL mới (`rgss`, `renpy`, `symbian`) | Trước khi phát hành bản app có gói GPL mới | **repo đã công khai (03/10)**. Còn: sếp chọn file LICENSE |
| G6 | Nhắn RAdmin duyệt client "AowMonika" (hardcore) | Bất kỳ lúc nào | chờ sếp |
| G7 | Thử Kirikiri 0.7.3 trên máy thật theo `TEST-MAY-THAT.md` (H03) | Sau V05 + H03 | chờ |
| G8 | Xin tác giả bản port mkxp-z (`BookerRues9`/`thehatkid`) giấy phép cho `Makefile`, `*.mk`, `get_deps.sh`. Không có thì phải viết lại script dựng (`tra-loi-004.md`) | Trước R7 | sếp đã hỏi tác giả (03/10), chờ trả lời; đội cứ chạy theo plan. Tới R7 chưa có giấy phép thì làm hướng B (viết lại script dựng) |
| G9 | Cài secrets Firebase Test Lab (`GCP_SA_KEY`, `GCP_PROJECT_ID`) theo `docs/TEST-LAB.md`: workflow `test-lab.yml` chưa chạy lần nào. Mở đường chạy game tự sinh trên máy ARM thật (Game Loop) | Khi tiện, không chặn đội | sếp đang làm (hướng dẫn chi tiết: `docs/TEST-LAB.md`, 03/10) |
| G10 | GitHub Actions bị chặn vì thanh toán (từ ~16:02 03/10, thư 007): sửa thẻ/hạn mức chi tiêu ở Settings → Billing của `aowvn-10diem`, **hoặc** công khai repo (G5), vì repo công khai được chạy Actions miễn phí trên runner chuẩn | Ngay: mọi việc cần CI đang đứng | **xong (03/10 16:35)**: sếp đã công khai repo và xóa 2 release `engines-rgss-4/5`. Lượt CI đầu tiên sau khi công khai sẽ xác nhận Actions chạy lại |

## 5. Nhịp theo dõi

- Opus kiểm mỗi **15 phút** (sếp chốt 03/10): `git ls-remote origin`, SHA không đổi thì dừng. Rảnh 4 lượt liền → 30 phút; 23:00–06:00 → 60 phút; có việc mới thì quay về 15 phút. Có commit mới → đọc `BANG-TIN.md`, bảng này, `git log` từ lần trước và trạng thái CI.
- Sonnet kiểm cùng nhịp, theo cách ít token chốt ở `pm-tra-loi-001.md`.
- Opus **không nhắn sếp chi tiết** (sếp chốt 03/10): tiến độ ghi lên trang theo dõi https://claude.ai/artifact/7UC9kqrWqv3GNUhhtt4z5d (Opus cập nhật mỗi lượt có thay đổi). Chỉ nhắn sếp khi cần sếp quyết, 1–2 dòng.
