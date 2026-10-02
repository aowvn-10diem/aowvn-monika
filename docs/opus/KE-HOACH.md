# Kế hoạch & bảng việc Aow Monika

> Chủ: Opus (PM, sếp giao 03/10/2026). Đội thi công: **Sonnet** (việc khó: native, engine, luồng app) và **Haiku** (việc nhẹ, máy móc: tài liệu, kiểm tra, sửa nhỏ có test sẵn). Sếp quyết việc ngoài kỹ thuật (mục 4).
> Đây là **nguồn sự thật duy nhất về tiến độ**. Mỗi phương án chi tiết nằm trong `docs/opus/<ngày>-<chủ đề>.md`; bảng này chỉ trỏ tới.
> Cập nhật: 03/10/2026 05:50 (GMT+7), `main` @ `48707de`, app 0.7.3 (versionCode 38), `configVersion` 31.

## 1. Mốc

| Mốc | Nội dung | Phương án | Trạng thái |
|---|---|---|---|
| M0 | Kirikiri 0.7.3 (menu tiếng Việt) phát hành | — | đang phát hành; máy thật **[CHƯA KIỂM]** |
| M1 | RPG Maker XP/VX/Ace nhúng (khối R), tắt app ngoài cho `rgss` và `kirikiri` | `2026-10-03-nhung-renpy-rgss.md` | R0 xong (`ket-qua/R0.md`) · R1 đang chạy CI · R4 xong |
| M2 | Ren'Py 8 nhúng (khối P) | như trên | P0 đang làm (song song, chỉ CI) |
| M3 | Ren'Py 7 (khối P7) | như trên | chờ M2 |
| M4 | Symbian/N-Gage nhúng (khối S) | `2026-10-03-nhung-symbian-eka2l1.md` | chờ sếp chốt A1, A2 · làm sau M2 |
| Q | Phương án cho câu 6 và 7 của mục 7 (kiểm thử khi không có máy thật; chất lượng crash log) | Opus viết | Opus làm, cần xong **trước R6** |
| — | Câu 1, 2, 3, 4 của mục 7 (tách module, dịch offline, Kirikiri 32-bit, RA cho NDS/N64/PS1) | chưa có | tồn đọng, xếp sau M2 |

Thứ tự giao hàng (Q3 của sếp): **M1 → M2 → M3 → M4**. Spike chỉ dùng CI, không đụng `:app`, thì được chạy song song.

## 2. Bảng việc đang mở

Trạng thái: `chờ` · `đang làm` · `kẹt` (kèm số thư) · `xong` (kèm commit). Ai làm thì người đó sửa dòng của mình, trong cùng commit `[viec-<mã>]`.

| Mã | Việc | Giao | Phụ thuộc | Cách kiểm | Trạng thái |
|---|---|---|---|---|---|
| V01 | R0: spike mkxp-z ra `libmkxp-z.so` arm64, ghi `docs/opus/ket-qua/R0.md`. Ngưỡng dừng: 5 nguyên nhân hỏng khác nhau (đã dùng 2) | Sonnet | — | Artifact `.so` + bảng kích thước | xong (7ec1c47: R0.md; còn cổng G1 hỏi sếp 1 game XP) |
| V02 | R1: `build-rgss.yml` + `engines/rgss/`, áp `hop-thu/tra-loi-001.md` | Sonnet | V01 | `grep`/`nm`/`strings` theo thư 001; máy sạch dựng ra `rgss-{abi}.zip` | đang làm (f55a72e: build-rgss.yml; chờ run đầu; hướng tạm clone-và-dựng, hỏi thư 004) |
| V07 | R1.1 theo `tra-loi-003.md`: bỏ OpenSSL (`-DMKXPZ_SSL`, `openssl` của Ruby), cắt ký hiệu rồi đo lại `libSDL2_ttf.so`; theo `tra-loi-004.md`: ghim commit bản port, ghi trạng thái giấy phép vào `UPSTREAM.md` | Sonnet | V02 | `readelf`/`strings` không còn OpenSSL; bảng kích thước trước/sau; gói `rgss` arm64 ≤ 15 MB | chờ |
| V08 | R2: module `:rgss` (9 file Java SDL 2.26.3 đổi gói `vn.aow.monika.rgss.sdl`, bước 3 của thư 001) | Sonnet | V02 | `assembleRelease` xanh; `dexdump` thấy cả hai `SDLActivity`; đo APK tăng | chờ |
| V03 | P0: spike Ren'Py, ghi `docs/opus/ket-qua/P0.md` | Sonnet | — | Bảng đường dẫn + kích thước thật | xong (P0.md, run 5 xanh) |
| V04 | P1: `build-renpy-pack.yml` (song song, chỉ CI) | Sonnet | V03 | Artifact `renpy8-{abi}.zip` + `.sha256` | xong (9d67241, run 1 xanh: renpy8-arm64-v8a.zip ≈ 22,6 MB, SHA-256 29c32501…a554c; armv7 cùng run; chưa publish — chờ cổng G5) |
| V05 | Phát hành Kirikiri 0.7.3 | Sonnet | — | Release có APK universal, SHA-256 chữ ký đúng (`CLAUDE.md`) | xong (v0.7.3, chữ ký c46902e9…d45ab20c khớp, APK 18,1 MiB đã gửi sếp) |
| V06 | Mỗi lượt kiểm tra: gộp nhánh `docs/opus-tra-loi` vào `main` (quét mọi nhánh, theo README hộp thư) | Sonnet | — | `main` có các commit `[opus]` mới | lặp lại |
| V09 | Trả lời `hop-thu/pm-hoi-001-kiem-tra-it-token.md` | Sonnet | — | Có `pm-tra-loi-001.md` | chờ |
| V10 | Tìm **gốc** lỗi test `BrowserDownloadTest.confirmBeforeFinishStillMovesWhenDone` (dòng 74; đỏ ở run 37071652777, đã đỏ nhiều lần trước đó). "Chập chờn" không phải nguyên nhân: đọc thông điệp `state=… confirmed=…` trong báo cáo test, sửa ở `BrowserDownloads` hoặc ở test. Gợi ý: `return@repeat` trong `repeat {}` chỉ bỏ qua một vòng, **không thoát vòng lặp** (dòng 61 và 72) | Sonnet | — | Chạy riêng test này 20 lần liền (`--tests …` trong vòng lặp shell) đều xanh | chờ |
| V11 | `build.yml`: thêm `paths-ignore: ['docs/**', '**/*.md']` cho `push` và `pull_request`, để commit chỉ sửa tài liệu không chạy Gradle (mỗi lượt khoảng 3 phút runner) | Sonnet | — | Push thử một commit chỉ sửa `docs/` thì không có run `Build` mới | chờ |
| H01 | Sửa đầu `docs/GIAO-TIEP-VOI-OPUS.md` thành 0.7.3 / versionCode 38 / `configVersion` 31. Mục 4: thêm dòng "E0 xong", "R4 xong" | Haiku | — | Diff chỉ đụng file đó | chờ |
| H02 | Chạy lại `python3 scripts/gen-architecture.py`, commit `docs/KIEN-TRUC.md`. Nếu bảng module vẫn thiếu `:libretrodroid`/`:kirikiri` thì **không sửa script**: ghi `kẹt` và gửi thư | Haiku | — | `git diff` chỉ đụng `docs/KIEN-TRUC.md` | chờ |
| H03 | `docs/TEST-MAY-THAT.md`: thêm mục "Kirikiri (0.7.3)" cho sếp thử: mở game `.xp3` → màn chuẩn bị tự tải gói → vào game → chạm, âm thanh, lưu/tải, tua nhanh, menu tiếng Việt, thoát. Máy 32-bit phải thấy thông báo chưa hỗ trợ. Nguồn: `docs/plan-kirikiri.md` + mục 4 của tài liệu bàn giao | Haiku | — | Chỉ đụng tài liệu; mỗi dòng là một thao tác sếp làm được | chờ |
| O01 | Trả lời hộp thư; cập nhật bảng này sau mỗi mốc | Opus | — | `BANG-TIN.md` không còn thư `mở` quá 1 lượt kiểm tra | lặp lại |
| O02 | Phương án câu 6 + 7 (kiểm thử không máy thật, crash log) | Opus | — | File `docs/opus/<ngày>-kiem-thu-chan-doan.md` | chờ |

Việc tiếp theo của từng khối (R2–R7, P2–P7, S0–S7) nằm sẵn trong file phương án. Khi một việc trên `xong`, PM thêm việc kế tiếp vào bảng; đội thi công không tự mở việc ngoài bảng.

## 3. Luật giao việc

| Ai | Được làm | Không được làm |
|---|---|---|
| Sonnet | Mọi việc `Giao: Sonnet`; sửa nhỏ để CI xanh | Đổi hướng hay thứ tự mốc; phát hành gói GPL mới trước cổng G5 |
| Haiku | Chỉ việc `Giao: Haiku`, đúng phạm vi ghi trong dòng việc | Sửa mã native, workflow build, `config/monika-config.json`, khóa ký. Việc to hơn mô tả → ghi `kẹt` và gửi thư, không tự làm rộng ra |
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
| G5 | Công khai mã nguồn Monika trước khi phát hành gói GPL mới (`rgss`, `renpy`, `symbian`) | Trước R7/P4/S7 | chờ sếp |
| G6 | Nhắn RAdmin duyệt client "AowMonika" (hardcore) | Bất kỳ lúc nào | chờ sếp |
| G7 | Thử Kirikiri 0.7.3 trên máy thật theo `TEST-MAY-THAT.md` (H03) | Sau V05 + H03 | chờ |
| G8 | Xin tác giả bản port mkxp-z (`BookerRues9`/`thehatkid`) giấy phép cho `Makefile`, `*.mk`, `get_deps.sh`. Không có thì phải viết lại script dựng (`tra-loi-004.md`) | Trước R7 | chờ sếp |

## 5. Nhịp theo dõi

- Opus kiểm mỗi **15 phút** (sếp chốt 03/10): `git ls-remote origin`, SHA không đổi thì dừng. Rảnh 4 lượt liền → 30 phút; 23:00–06:00 → 60 phút; có việc mới thì quay về 15 phút. Có commit mới → đọc `BANG-TIN.md`, bảng này, `git log` từ lần trước và trạng thái CI.
- Sonnet kiểm cùng nhịp, theo cách ít token chốt ở `pm-tra-loi-001.md`.
- Opus báo sếp chỉ khi qua mốc, có việc kẹt, hoặc có cổng cần quyết.
