---
name: tham-khao-du-an
description: Sự thật đã xác minh của dự án Aow Monika (feed Blogger, Cloudflare/aow.vn, Pixeldrain, LibretroDroid, mật khẩu file nén, lõi buildbot), vá Việt hóa ROM, bàn giao Opus, backlog cũ. Dùng khi đụng tới feed bài viết, thông báo bài mới, tải từ Pixeldrain, vá ROM, hoặc cần biết còn việc gì.
---

# Tham khảo dự án

App Android (Kotlin, Jetpack Compose) của aow.vn: đọc bài (Blogger feed), thông báo bài mới, tải và chạy game. Chủ repo giao tiếp tiếng Việt, gọi là "sếp".

## Sự thật đã xác minh
- Feed: `https://www.aow.vn/feeds/posts/default?alt=json&orderby=published`; bài lẻ: `/feeds/posts/default/{postId}?alt=json`. Blog ID `4482370512868492154`. Feed trả **nguyên** bài (không bị cắt). Trang tĩnh đọc qua `/feeds/pages/default?alt=json` (dùng cho trang tải giả lập aow.vn/p/...).
- aow.vn đứng sau **Cloudflare** với rule chặn IP ngoài VN/LA/CU. Máy chủ Claude chỉ được mở riêng đường dẫn `/feeds/` (rule skip theo IP). Vì vậy thông báo chạy **trong app** (WorkManager, IP user VN), không dùng server/GitHub Action nước ngoài.
- LibretroDroid 0.14.0 (JitPack): `GLRetroView(context, GLRetroViewData)`, `serializeSRAM()`, `getGLRetroErrors()`.
- Pixeldrain: `/u/{id}` → `/api/file/{id}?download`; `/d/{id}` → `/api/filesystem/{id}?attach` (đã thử, trả file + tên tiếng Việt qua `filename*=UTF-8`).
- Mật khẩu file nén aow.vn: `aowvn.org` (ghi cuối bài).
- libarchive (me.zhanghai.android.libarchive): giải nén bằng `readOpenFd` + `readDataIntoFd`; `readNextHeader` trả 0 khi hết file — **đã bỏ khỏi app** (xem `giai-nen`).
- Lõi libretro Android: `https://buildbot.libretro.com/nightly/android/latest/{abi}/<core>_libretro_android.so.zip`.

## Vá Việt hóa ROM
`patch/RomPatcher.kt` (IPS/BPS/UPS, nhận diện theo chữ ký, BPS/UPS kiểm CRC32) + `patch/PatchFlow.kt` (menu game trong Thư viện → "Vá Việt hóa", ghi game mới vào thư mục riêng "<tên> (Việt hóa)"). Chưa hỗ trợ xdelta/PPF/APS và ISO lớn (> 256 MB).

## Phối hợp
- Phiên mất/mới? đọc `docs/opus/HANDOFF-SONNET.md` trước (trạng thái, cách khôi phục, bài học, nơi sao lưu tài nguyên). Bảng việc hiện hành: `docs/opus/KE-HOACH.md` (nguồn sự thật tiến độ).
- Bàn giao cho Opus (tham gia từ ngoài, đưa phương án): `docs/GIAO-TIEP-VOI-OPUS.md`. Cập nhật mục 4 của file đó sau mỗi mốc lớn.
- Kiểm hộp thư của PM: skill `pm-kiem-thu`.

## Backlog cũ trong CLAUDE.md (có thể đã lỗi thời — KE-HOACH.md là nguồn đúng)
1. Link chính thức cho app ngoài (chủ repo cung cấp) → điền `externalApps[].downloadUrl`, xác nhận `packageNames`.
2. Đã có: tay cầm theo hệ máy, kéo đổi chỗ + cỡ, save/load state 3 ô, ảnh bìa game từ bài viết (`library/GameMeta.kt`, file `.monika.json` trong thư mục game), chọn lõi NDS, tùy chọn lõi (`runner/CoreOptions.kt`).
3. Chưa test trên máy thật — mọi thứ mới chỉ build + unit test.
4. Giải nén zip/rar/rar5/7z, chia nhiều phần, % tiến độ — chưa test máy thật.
5. Đã nhúng J2ME Loader (runner "j2me") + phím ảo kiểu Monika. Còn: ONScripter (native C++ + SDL, cần high).
6. Port Ren'Py, mkxp-z (RPG Maker XP/VX/Ace) — khó, làm sau. [CHƯA KIỂM] hiện đã làm tới đâu: xem KE-HOACH (M1/M2).
