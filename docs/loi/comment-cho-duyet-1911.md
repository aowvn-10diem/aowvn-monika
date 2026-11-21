# Comment soạn sẵn — nhóm con còn lại (19/11)

Trạng thái: **chưa đóng issue nào**. Tệp này giữ câu comment đã soạn để chờ lệnh trưởng nhóm.
Dữ liệu chung: `docs/loi/nhom-con-lai-1911.md`.

## 1. #322 / #578 — `pcsx_rearmed` / `core-error` (37 + 18 = 55 lượt)

Phạm vi bản vá `#668` (đã gộp, **có ở v0.8.15 chính thức** — phát hành 19/11 04:48Z):

- `#668` sửa **phía nhập game**: `Importer`/`DiscSet`/`ArchiveExtractor` xếp `.cue` và các track vào **cùng một thư mục**, nên bộ đĩa nhiều tệp rời không còn sinh ra `.cue` trỏ tới tệp không tồn tại.
- `#649` (đã có ở **v0.8.14 chính thức**) chỉ đổi **thông điệp** khi thiếu tệp đĩa/BIOS; nó không làm `retro_load_game` thành công.
- **Không** vá: `.cue` đã nhập sai từ bản trước (phải nhập lại), `.chd`/`.pbp` mà lõi không đọc, track hỏng, và mã lỗi `0/1/-1` (đang chờ `#686`).
- Điều kiện đóng (A102): bản **chính thức** chứa `#668` + không có chữ ký mới trong ≥ 1 tuần. Hiện chưa đủ (0.8.15 còn là bản thử).

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

## 4. Issue 1–4 lượt (soạn sẵn, chưa đóng)

| Issue | Câu comment đóng dự kiến | Chờ gì |
|---|---|---|
| #398, #414, #415, #419 | "1 lượt, không tái hiện từ 01/11; đóng khi bản chính thức có `#683` và không có chữ ký mới." | `#683` vào bản chính thức |
| #399 | "2 lượt / 2 máy; cùng lớp `unknown/anr`; đóng cùng #413 sau `#683`." | `#683` |
| #534 | "5 lượt / 5 máy; cùng lớp; đóng sau `#683`." | `#683` |
| #387 | "1 lượt; lõi sẽ được gắn từ phiên chết nhờ `ecdccd33` (có ở v0.8.15 chính thức). Chờ lệnh trưởng nhóm và đo lượt sau bản vá." | lệnh trưởng nhóm |
| #403 | "`mgba` đã ghim bản; cần mã lỗi để tách `0/1/-1` — chờ `#686`." | `#686` |
| #418 | "1 lượt, chờ `#686`; dưới ngưỡng kết luận." | `#686` |
| #448, #563 | "Lõi `version=1` nightly, không có gói ký hiệu ⇒ không giải mã được khung; chờ V198." | V198 / gói ký hiệu |
| #464, #465 | "`op-fail` 1 lượt (tải/cài gói lõi); `#665` có ở v0.8.15 chính thức — chờ lệnh trưởng nhóm." | lệnh trưởng nhóm |
| #378, #513 | "`unknown` vì máy API < 30 (không có `ApplicationExitInfo`); không phải lỗi mới — đóng khi hết hạn theo dõi." | quyết định của trưởng nhóm |

## 5. Mốc toạ độ

Rà 21/11/2026 theo `origin/main` `3d8e1bcf`: `scripts/crash-reports.sh` **không đổi** từ `02bd03cc` (còn retrace theo mapping + ghép native theo BuildId); các tệp còn lại của tệp này đều là tài liệu trong repo. Bảng đầy đủ: `docs/loi/nhom-con-lai-1911.md` §5.
