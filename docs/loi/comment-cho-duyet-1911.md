# Comment soạn sẵn — nhóm con còn lại (19/11)

Trạng thái: **chưa đóng issue nào**. Tệp này giữ câu comment đã soạn để chờ lệnh trưởng nhóm.
Dữ liệu chung: `docs/loi/nhom-con-lai-1911.md`.

## 1. #322 / #578 — `pcsx_rearmed` / `core-error` (37 + 18 = 55 lượt)

Phạm vi bản vá `#668` (đã gộp, **có ở v0.8.15 chính thức** — phát hành 19/11 04:48Z):

- `#668` sửa **phía nhập game**: `Importer`/`DiscSet`/`ArchiveExtractor` xếp `.cue` và các track vào **cùng một thư mục**, nên bộ đĩa nhiều tệp rời không còn sinh ra `.cue` trỏ tới tệp không tồn tại.
- `#649` (đã có ở **v0.8.14 chính thức**) chỉ đổi **thông điệp** khi thiếu tệp đĩa/BIOS; nó không làm `retro_load_game` thành công.
- **Không** vá: `.cue` đã nhập sai từ bản trước (phải nhập lại), `.chd`/`.pbp` mà lõi không đọc, track hỏng, và mã lỗi `0/1/-1` (chờ bản chính thức chứa `#686` — **0.8.16**; `#686` đã gộp `main` 19/11 12:14Z, `fafd13fe`).
- Điều kiện đóng (A102): bản **chính thức** chứa `#668` + không có chữ ký mới trong ≥ 1 tuần. Hiện chưa đủ: mới ~2 ngày kể từ 19/11, chưa đủ 1 tuần để đo.

## 2. #385 — `kirikiroid2-yuri` / `native` (13 lượt, 8 lần gửi)

Đã đủ điều kiện giải mã, **chưa giải mã được vì phiên này không có `CRASH_ADMIN_TOKEN`**:

```bash
gh release download engines-kirikiri-16 -R aowvn-10diem/aowvn-monika-packs \
  -p 'symbols-kirikiri-16-arm64-v8a.zip'
CRASH_ADMIN_TOKEN=… scripts/crash-reports.sh <id> \
  --symbols-dir ./symbols-kirikiri-16-arm64-v8a --symbolizer "$(command -v llvm-symbolizer)"
```

- Ba nhánh kết luận, xét theo khung đầu tiên có PC + BuildId:
  1. trong `libkrkr2yuri.so` → lỗi engine, mở đặc tả V198 (dựng lõi + ký hiệu của Monika) thay vì vá mù;
  2. trong `libretrodroid.so` → thuộc lớp `docs/loi/ppsspp-native-1311.md`, không phải lỗi engine;
  3. trong `libGLES*`/`libc.so` → lỗi môi trường GL/ABI, hạ ưu tiên.
- Chưa có mã lỗi, chưa có khung ⇒ **không** kết luận nguyên nhân.

## 3. #613 — `mkxp-z` / `native` (1 lượt) — đặc tả thiếu ký hiệu

Xem `docs/loi/mkxp-z-thieu-ky-hieu-1911.md`.

## 4. #413 — `unknown` / `anr` (15 lượt / 5 máy — issue ANR lớn nhất của nhóm)

- Chữ ký: `3b48cbfd141e740ca35253ed408b50bc7cab0d43bb05ed609a898ca5ee789eca`; 15 lượt, 5 khoá `seen`, mở 01/11, **tái diễn** (bot gộp cùng ngày).
- Cùng lớp với 6 issue `unknown/anr` còn lại (bảng §2 của `docs/loi/nhom-con-lai-1911.md`): không có `session.core` ⇒ tiêu đề là `unknown`, và 7 chữ ký khác nhau nên **không** phải issue trùng.
- `#683` (ANR có ngữ cảnh tiến trình, `Diagnostics.startProcess` + `writeDeath`) **đã gộp `main` 19/11** nhưng nằm **ngoài tag** `v0.8.15`.
- Trạng thái: chờ bản chính thức chứa `#683` — **0.8.16**. Cột "Chờ gì": **0.8.16**.
- Điều kiện mở lại sau khi đóng: chữ ký `3b48cbfd141e` xuất hiện lại trên bản đã có `#683`, **hoặc** ANR vẫn mang `core=unknown` sau bản đó (nghĩa là ngữ cảnh tiến trình chưa phủ hết đường chết).

## 5. Issue 1–4 lượt (soạn sẵn, chưa đóng)

| Issue | Câu comment đóng dự kiến | Chờ gì |
|---|---|---|
| #398, #414, #415, #419 | "1 lượt, không tái hiện từ 01/11; đóng khi bản chính thức có `#683` và không có chữ ký mới." | 0.8.16 |
| #399 | "2 lượt / 2 máy; cùng lớp `unknown/anr`; đóng cùng #413 sau bản chính thức chứa `#683`." | 0.8.16 |
| #534 | "5 lượt / 5 máy; cùng lớp; đóng sau bản chính thức chứa `#683`." | 0.8.16 |
| #387 | "1 lượt; lõi sẽ được gắn từ phiên chết nhờ `ecdccd33` (có ở v0.8.15 chính thức). Chờ lệnh trưởng nhóm và đo lượt sau bản vá." | lệnh trưởng nhóm |
| #403 | "`mgba` đã ghim bản; cần mã lỗi để tách `0/1/-1` — `#686` đã gộp ở `main` 19/11, chờ bản chính thức chứa `#686`." | 0.8.16 |
| #418 | "1 lượt; `#686` đã gộp ở `main` 19/11, chờ bản chính thức chứa `#686`; dưới ngưỡng kết luận." | 0.8.16 |
| #448, #563 | "Lõi `version=1` nightly, không có gói ký hiệu ⇒ không giải mã được khung; chờ V198." | V198 / gói ký hiệu |
| #464, #465 | "`op-fail` 1 lượt (tải/cài gói lõi); `#665` có ở v0.8.15 chính thức — chờ lệnh trưởng nhóm." | lệnh trưởng nhóm |
| #378, #513 | "`unknown` vì máy API < 30 (không có `ApplicationExitInfo`); không phải lỗi mới — đóng khi hết hạn theo dõi." | quyết định của trưởng nhóm |

## 6. Mốc toạ độ

Rà 21/11/2026 theo `origin/main` `3d8e1bcf`: `scripts/crash-reports.sh` **không đổi** từ `02bd03cc` (còn retrace theo mapping + ghép native theo BuildId); các tệp còn lại của tệp này đều là tài liệu trong repo. Bảng đầy đủ: `docs/loi/nhom-con-lai-1911.md` §5.
