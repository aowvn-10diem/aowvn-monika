# Rà soát giả lập, hiệu năng, dung lượng (v0.5.0)

Mọi dòng ghi **[đã kiểm]** nghĩa là có chạy thật (test tự động, script, hoặc máy ảo trên CI). **[CHƯA KIỂM]** = chưa có máy thật / chưa đo.

## 1. Lỗi GBA: màn chờ "đang khởi động game" không biến mất

| | |
|---|---|
| Nguyên nhân (đọc mã) | `RetroActivity` gọi `loading.animate()…start()` **ở mỗi khung hình**. Mỗi lần gọi lại hủy hoạt ảnh mờ dần trước đó, và hành động "gỡ màn chờ" chỉ chạy khi hoạt ảnh kết thúc bình thường → với game 60 khung/giây hoạt ảnh luôn bị hủy trước khi chạy, màn chờ không bao giờ gỡ. Game vẫn chạy phía sau (có tiếng) nhưng bị màn chờ che. |
| Sửa | Gỡ **đúng 1 lần** (cờ `loadingGone`), thêm bước gỡ cứng sau 500 ms phòng khi hệ thống bỏ hoạt ảnh. Lỗi nạp game giờ hiện thẳng trên màn chờ (không chỉ thông báo 4 giây); sau 30 giây không có khung hình thì báo rõ và ghi chẩn đoán `no-first-frame-30s`. |
| Kiểm | Test tự động cho logic này chưa có (cần GL thật). `scripts/ci-emulator-games.sh` mở GBA/GB/NES bằng ROM thử trên máy ảo và đòi dòng `MonikaGame: loading-dismissed`. **[CHƯA KIỂM trên máy thật]** |

Lưu ý: đây là kết luận từ đọc mã + thư viện LibretroDroid 0.14.0 (phân tích bytecode: `FrameRendered` được phát mỗi khung hình). Chưa tái hiện được trên máy sếp; nếu sau bản này GBA vẫn kẹt, báo lỗi trong app (có `stage` chẩn đoán) để biết lõi có nạp được không.

## 2. Kiểm lõi giả lập — `python3 scripts/audit-cores.py`

**[đã kiểm]** 21 lõi × (arm64-v8a, armeabi-v7a, x86_64 nếu có) = 61 gói: link sống, có file .so, không phụ thuộc thư viện lạ, mọi khóa/giá trị tùy chọn trong config có thật trong lõi. 0 lỗi, 0 cảnh báo (ngày 30/09/2026).

Rủi ro còn lại: mọi lõi lấy từ `buildbot.libretro.com/nightly/.../latest` — **bản nightly dựng lại mỗi ngày**, không ghim phiên bản. Một bản nightly hỏng có thể làm một hệ máy hỏng mà app không đổi gì. Chạy lại script này (hoặc Emulator Test) khi nghi ngờ.

## 3. Hiệu năng theo sức máy (mới)

- `EmuTier` chia máy: **lite** (RAM < 3,5 GB, < 6 nhân, máy RAM thấp, hoặc đang tiết kiệm pin) · **mid** · **full** (RAM ≥ 7 GB và ≥ 8 nhân). Người chơi ép được ở Cài đặt → Hiệu năng giả lập.
- Config: `cores.<id>.perf.lite|full` đè lên `options`; `lowLatencyAudio:false` cho lõi nặng; máy **lite luôn dùng âm thanh bộ đệm thường**.
- Đã đặt (đều đã kiểm khóa/giá trị bằng script ở mục 2): NDS lite = 256×192 (mặc định cũ 512×384 cho mọi máy, nặng với máy yếu) · PSP lite = tự bỏ khung, full = 960×544 · Dreamcast full = 1280×960 · N64 full = 640×480 · PS1 lite = tự bỏ khung · melonDS dựng hình đa luồng (tắt ở máy lite).
- Thêm: `setSustainedPerformanceMode`, `skipDuplicateFrames`.
- **[CHƯA KIỂM]** Chưa đo FPS trên máy thật. Các giá trị trên là lựa chọn thận trọng (chỉ giảm tải ở máy yếu, nâng chất lượng ở máy mạnh), không phải kết quả đo. Không thể "đảm bảo mọi game tốt nhất": hiệu năng phụ thuộc game × lõi × chip.
- Đã thử rồi **bỏ**: khóa tần số quét 60 Hz — LibretroDroid đọc tần số quét một lần lúc tạo game, đổi sau đó sẽ làm game chạy nửa tốc độ trên màn 120 Hz.

## 4. Tải trước (preload)

- Lõi: đã có từ 0.4.4 (`CorePrefetchWorker` + tải theo hệ đang có game khi mở Thư viện).
- **Mới — Tải trước game** (Cài đặt → Hiệu năng giả lập, mặc định bật): khi mở Thư viện có game "Tiếp tục chơi", hoặc giữ ô game để mở menu → đọc sẵn tối đa 64 MB đầu file game (bộ nhớ đệm hệ điều hành) và dựng sẵn tiến trình `:game` + nạp sẵn thư viện native LibretroDroid (`GameWarmService`).
- **[CHƯA KIỂM]** chưa đo thời gian vào game nhanh hơn bao nhiêu. Không nạp trước lõi (dlopen trong tiến trình làm sẵn) vì đổi lõi giữa chừng để lại bộ nhớ thừa và không thể kiểm trên máy thật.

## 5. Dung lượng APK (bản chung, cả arm64 + armeabi-v7a)

| Bước | APK |
|---|---|
| 0.4.9 | 46,23 MB |
| Bỏ bộ khung tài nguyên Android của ARSCLib (~2 MB, chỉ dùng để in tên thuộc tính) | −2,0 MB |
| Bỏ bảng số Picnic của BouncyCastle (~1,2 MB, libadb không dùng) | −1,2 MB |
| 70 ảnh emoji/logo PNG → WebP q92 (2,15 MB → 0,39 MB; PSNR ≥ 38 dB) + bỏ 10 ảnh trùng | −1,7 MB |
| **0.5.0** | **40,78 MB** (−5,45 MB, −11,8%) |

**[đã kiểm]** toàn bộ test đơn vị (gồm `ApkRepackerTest`, `Step5Test` chạy trên jar ARSCLib đã bỏ khung) qua; script kiểm tên lớp R8 qua.

Còn lớn nhưng chưa động (cần quyết định):
1. `assets/engines/azahar.zip` **12,4 MB** (engine 3DS, chỉ arm64) nằm trong APK vì repo private nên app không tải được từ Releases. Muốn bỏ phải có nơi lưu công khai + ghim SHA-256 trong config.
2. Thư viện native armeabi-v7a ~8 MB: bỏ thì mất máy 32-bit đời cũ.
3. BouncyCastle giữ nguyên (`-keep org.bouncycastle.**`): thu hẹp ~1,5 MB nhưng không kiểm được Cách 3 trên máy thật.

## 6. Mượt

- **Baseline Profile** viết tay (`app/src/main/baseline-prof.txt`, quy tắc có ký tự đại diện cho `ui`, `library`, `runner`, `config`, `feed`…): sau khi R8 gộp còn ~1.400 phương thức của app được biên dịch sẵn lúc cài. **[CHƯA KIỂM]** chưa đo thời gian mở app / số khung rớt.
- Ảnh WebP nhỏ hơn → giải mã nhanh hơn, ít RAM hơn.

## 7. Kiểm bằng máy ảo trên CI (Actions → Emulator Test)

Ngoài Maestro (mở app 2 lần), nay chạy thêm `scripts/ci-emulator-games.sh`: GBA (mGBA), Game Boy (gambatte), NES (fceumm) bằng ROM kiểm thử mã nguồn mở (tải lúc chạy). Đòi dòng `loading-dismissed`; báo đỏ khi có lỗi nạp, 30 giây không lên hình, hoặc app sập. Chụp ảnh từng hệ. Chưa phủ: NDS, SNES, PS1, PSP, N64, Dreamcast, 3DS (chưa có ROM thử nhỏ hợp lệ).
