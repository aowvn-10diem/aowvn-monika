# `citra` lõi libretro — hiện trường 18/11/2026: có kèm `build_id` / symbols không?

Phạm vi: 8 issue `crash-moi` nhóm `citra` đang mở — #301 (56 lượt, `core-error`), #458 (37, `native`), #336 (9, `anr`), #404 (3, `native`), #328 (2, `anr`), #460 (2, `anr`), #610 (1, `core-error`), #373 (1, `unknown`) = **111 lượt**; #607 (18, `killed`) đã đóng 18/11, gom về #549.
Nền: `origin/main` `4c4757b1`. Người lập: phiên **Lỗi 3** (nhóm citra). Đây là **bản đo lại nghi phạm số 1** của `docs/loi/citra-native-1311.md` §3.1 (đã gộp qua PR #622) — không viết lại chẩn đoán đã có.
Mọi suy luận nguyên nhân chưa có khung đã giải mã vẫn **[CHƯA KIỂM]**.

## 0. Kết luận (4 dòng)

| Câu hỏi | Trả lời | Bằng chứng |
|---|---|---|
| Gói lõi `citra` app tải về có **symbols**? | **KHÔNG**. Không có gói ký hiệu nào cho `citra_libretro_android.so`; chính `.so` cũng bị **strip** (không `.symtab`, không `.debug_info`). | §2 |
| `.so` đó có **`build_id`**? | **CÓ** — GNU BuildId `6adb3a8b75ce50703db4ad089e9a9b3cc5868d45`; nhưng **app không đọc, không lưu**, và bản dựng đổi mỗi ngày. | §2.3 |
| App có ghi lại **bản lõi đang chạy**? | Chỉ **ngày tải + `Last-Modified`** trong `info.txt`, không hash, không BuildId; `version` luôn `"1"` nên không bao giờ tải lại. | §1 |
| Retrace được chưa? | **Chưa**, hai lớp chặn: (a) thiếu gói ký hiệu — ngoài phần sửa được của lượt này; (b) **công cụ đọc sai dạng khung** của chính app — **đã sửa** trong lượt này. | §3, §4 |

## 1. Gói lõi `citra` app tải về

`config/monika-config.json:1120-1128`:

```json
"citra": {
  "version": "1",
  "url": "https://buildbot.libretro.com/nightly/android/latest/{abi}/citra_libretro_android.so.zip",
  "aspectRatio": 0.8333,
  "abis": ["arm64-v8a"],
  "lowLatencyAudio": false
}
```

- **Không** `sha256`, **không** `artifacts`, **không** `systemFiles`, **không** `options`/`perf`, **không** trường ký hiệu. Đối chiếu: `mgba`/`gambatte`/`fceumm`/`ppsspp` đều có `artifacts` + `sha256` từng ABI (`config:895-913`, `926-944`, `960-983`, `1014-1032`).
- Hệ quả mã: `CoreManager.kt:110-141` là nhánh **ZIP thường** (chỉ nhánh `artifacts` mới đòi `sha256`, `CoreManager.kt:142-147`). Sau khi tải, `CoreManager.kt:135-141` ghi `info.txt` =
  `cfg v1 · arm64-v8a · <KB> · tải dd/MM/yyyy[ · bản dựng <Last-Modified>]`.
- `CoreManager.kt:38` (`installedVersion`), `:45` (`wantedVersion`), `:56` (`isReady` so khớp): vì `version` luôn `"1"`, máy đã có lõi **không bao giờ tải lại** dù buildbot đổi nội dung. Hai máy cùng nhãn `1` có thể chạy hai `.so` khác nhau.
- `info.txt` được đính vào báo cáo: `RetroActivity.kt:128` (`Diagnostics.begin`), `:323` (`Diagnostics.coreInfo`); báo cáo chết tiến trình mang cả phiên (`Diagnostics.kt:250`), `coreInfo` cắt 200 ký tự (`UserGameReport.kt:105`). ⇒ **truy được bản dựng theo ngày máy chủ**, không truy được theo hash.

Đo trực tiếp máy chủ hôm nay (HEAD, 18/11):

```
status 200 · Last-Modified: Tue, 17 Nov 2026 07:45:56 GMT · Content-Length: 9730000 · ETag "1778498538"
```

⇒ bản đang phục vụ được dựng **17/11**, tức đã trôi ít nhất một lần so với bản phát hành 14/11. Nội dung ZIP chỉ có **1 mục**: `citra_libretro_android.so` (31 502 656 B) — **không** kèm tệp ký hiệu nào.

**[CHƯA KIỂM]** tương quan "bản dựng ngày nào thì nhiều `core-error` hơn": cần `CRASH_ADMIN_TOKEN` để đọc `coreInfo` của #301/#610 (xem §5).

## 2. Symbols và BuildId — có hay không

### 2.1 Trong cấu hình: không có

`grep -c symbols config/monika-config.json` → **0**. Không có cơ chế khai báo gói ký hiệu theo lõi; ký hiệu chỉ đi kèm **bản phát hành của gói/engine** do workflow tự dựng.

### 2.2 Trong kho gói: citra không có mặt

Các bản phát hành `aowvn-monika-packs` được cấu hình trỏ tới (`config`): `cores-gb-gba-nes-1`, `cores-ppsspp-1`, `engine-azahar-1`, `pack-sevenzip-1`, `engines-kirikiri-16`, `engines-onsyuri-2`. **Không** có `cores-citra-*`.

`.github/workflows/build-engines.yml` chỉ dựng **engine Azahar** (`azahar-android`, dòng 117-241): `libcitra-android.so` + `symbols-azahar-<run>-arm64-v8a.zip` (dòng 219-233) có `build-ids.tsv`. **Không** có job nào dựng **lõi libretro citra**, nên cũng không có `symbols-citra-*`.

Điểm trớ trêu: bộ ký hiệu đang có là của `libcitra-android.so` — đường **engine Azahar**, mà 3DS **không còn đi** đường đó: `config:200-210` hệ `3ds` chỉ có `runner: libretro`, `core: citra`, không có `engine`; `GameLauncher.kt:50-51` chỉ mở `AzaharActivity` khi `system.engine == "azahar"`. Ký hiệu đang phục vụ một đường chết, còn đường đang chạy thì không có ký hiệu.

### 2.3 Trong chính tệp `.so`: **có BuildId, không có bảng tên**

Tải ZIP thật của buildbot (17/11), đọc header ELF64 + `.note.gnu.build-id`:

```
ZIP entries: ['citra_libretro_android.so']
so size: 31502656
sections có .note/.symtab/.debug: ['.note.android.ident', '.note.gnu.build-id', '.dynsym']
BUILDID: 6adb3a8b75ce50703db4ad089e9a9b3cc5868d45
has .symtab: False
has .debug_info: False
```

Ba hệ quả:

1. **BuildId tồn tại** (sha1 20 byte) ⇒ về lý thuyết ghép ký hiệu theo BuildId là làm được, đúng khuôn `scripts/package-native-symbols.py:40-64` (`build_id\tfile\tabi\tsymbols\tlevel`).
2. **Không có `.symtab`/`.debug_info`** ⇒ upstream đã strip; kể cả lấy đúng `.so` cũng chỉ đạt mức `dynamic-only` — không ra tên hàm nội bộ, không ra dòng. Muốn mức `debug` phải **tự dựng** lõi từ nguồn ghim.
3. **BuildId đổi mỗi lần buildbot dựng lại** (nightly/latest) ⇒ không có kho ký hiệu nào theo kịp trừ khi hai bên cùng một lần dựng. Đây là lý do gốc của "trôi bản, không retrace được".

## 3. Đường đi báo cáo lỗi ↔ công cụ retrace

| Mắt xích | Bằng chứng |
|---|---|
| Báo cáo chết tiến trình lấy `ApplicationExitInfo` | `Diagnostics.kt:232-241` (`REASON_CRASH_NATIVE → native`) |
| Native: `detail` = tombstone protobuf do app tự dựng | `Diagnostics.kt:242`, `:312-317` → `TombstoneParser.toLines` |
| Dòng khung app in ra | `TombstoneParser.kt:150-156`: `#00 libX.so+0x<pc> <hàm>+<off> [build_id <id>]` |
| Logcat nằm ở trường **`log`**, không phải `detail` | `Diagnostics.kt:244,251` |
| Bộ giải ký hiệu đọc khung theo BuildId | `scripts/crash-report-detail.py:65-109` (`symbolize`), ghép `build-ids.tsv` dòng 73-78, 88 |

Kết luận của phép đối chiếu: BuildId **có trong báo cáo** (khi máy đọc được tombstone), nhưng `symbolize()` bản cũ chỉ khớp dạng **dòng `DEBUG` của `crash_dump`** (`#00 pc <hex> <path> (BuildId: <hex>)`) — dạng chỉ nằm trong logcat, **không** nằm trong `detail`. Vì vậy với **mọi** báo cáo `native` do app gửi, kể cả khi đã có `--symbols-dir` đúng, công cụ vẫn in `Chưa giải được khung native`.

## 4. Đã sửa trong lượt này (tệp không bị chặn)

`scripts/crash-report-detail.py`:

- Thêm dạng khung thứ hai (`app_frame`) khớp đúng `TombstoneParser.kt:150-156`; giữ nguyên dạng logcat cũ (`logcat_frame`). Không đoán PC: khung app dùng `rel_pc` (độ lệch so với đầu vùng ánh xạ `.so`) và in rõ một dòng nhắc việc đó.
- Ghi chú khi không giải được nêu cả hai dạng cần có, thay vì chỉ nhắc tombstone.
- Không đổi hành vi retrace Java, không đổi định dạng `build-ids.tsv`, không đụng `Diagnostics.kt`/`azahar/*`/`config/`.

Test: `python scripts/test-crash-report-detail.py` → **4/4 OK**, gồm 2 ca mới: khung đúng BuildId ⇒ giải ký hiệu; BuildId lệch bảng ⇒ chỉ ghi `[CHƯA KIỂM]`, **không** dùng ký hiệu của bản khác (đúng luật "không ghép theo tên `.so`" của skill `bao-loi-diag`).

Giới hạn phải nói rõ: sửa này **không** làm nhóm `citra` retrace được — nó chỉ bỏ một lớp chặn **chung** cho mọi lõi (ppsspp, mkxp-z, azahar, citra) khi đã có gói ký hiệu. Với `citra`, lớp chặn còn lại là §2.

## 5. Cái gì chặn (ngoài phần được sửa)

| Việc | Vì sao chặn |
|---|---|
| Ghim lõi citra + `sha256` trong `config/monika-config.json` | Tệp khoá; sửa phải tăng `configVersion` và PM duyệt đồng bộ Cloudflare |
| Dựng + phát hành `cores-citra-1` và `symbols-citra-*` | Thuộc `aowvn-monika-packs` và `.github/workflows/build-engines.yml` (tệp khoá); cần quyền phát hành gói |
| Đọc `coreInfo`/`reason`/`crumbs` của #301, #610, #458 để biết bản dựng và mã lỗi | Cần `CRASH_ADMIN_TOKEN` — phiên này không có (luật cứng: không in/không đụng) |
| Kết luận nguyên nhân `core-error` 56 lượt là sysdata / lõi trôi / `.cia` cũ | Cần bước trên; `.cia` đã bị chặn từ #637 nên chỉ còn báo cáo cũ |

## 6. Đề xuất bước rẻ nhất (theo thứ tự, bỏ bước 1 là mọi bước sau vô nghĩa)

1. **Ghim lõi (rẻ nhất, gỡ cả hai lớp chặn).** Dựng `citra_libretro_android.so` từ **commit ghim** của libretro/citra cho Android, `RelWithDebInfo`, `-Wl,--build-id=sha1`; đóng `cores-citra-1/citra-arm64-v8a.zip` + `sha256`, kèm `symbols-citra-<run>-arm64-v8a.zip` bằng chính `scripts/package-native-symbols.py` (đã có, không phải viết mới). Cùng lần dựng ⇒ BuildId trong gói chạy **và** trong gói ký hiệu khớp nhau.
2. **Config** (cần PM duyệt + `configVersion` + Cloudflare): đổi `cores.citra` sang khuôn `artifacts` như `ppsspp` (`config:960-983`) — `version` thành số hiệu gói, `sha256` từng ABI; thêm `options`/`perf` hạ tải (độ phân giải, tắt shader nặng) như `melondsds` đang làm (`config:1135-1145`); cân nhắc `systemFiles` cho sysdata 3DS.
3. **Đo lại sau 1-2 bản**: lọc báo cáo `citra` theo `coreInfo` (`bản dựng …`) để chứng minh hết trôi; so số lượt `core-error` với #301 (dừng cộng từ 25/10).
4. **Chỉ khi đó** mới kết luận `core-error`/`native` là lỗi lõi hay thiếu sysdata.

## 7. Khuyến nghị giữa PR #635 và PR #648

| PR | Nội dung | Nhận xét |
|---|---|---|
| **#635** `docs/loi/citra-con-lai-1313.md` | Toàn bộ 7 issue citra còn lại (`core-error`/`anr`/`killed`/`unknown`), có bảng số, `#610` nhãn sai, `.cia` đã chặn | **Nên gộp trước** — phủ rộng nhất, là nền của mọi kết luận nhóm citra |
| #648 `docs/loi/citra-anr-killed-1313.md` | Chỉ `citra/anr` + `citra/killed`, so cùng lớp với `melondsds` | Hẹp hơn, **lặp lại** §4-5 của #635; giá trị còn lại là phép so `anr` giữa hai lõi |

Khuyến nghị: gộp **#635** trước; **#648** chỉ gộp sau và nên cắt phần đã có ở #635 (giữ đúng phần "anr citra có cùng lớp với melondsds không"). Không gộp cả hai cùng lúc để tránh hai tài liệu cùng nói một nhóm với hai bộ số.

Tài liệu này đặt tên `citra-core-hien-truong-1811.md` để **không trùng** hai tệp trên.

## 8. Issue đóng được không

**Không đóng được cái nào** trong lượt này — chưa có bản sửa cho người dùng, cũng chưa có bằng chứng hết lỗi:

| Issue | Vì sao giữ mở |
|---|---|
| #301 (56) | Chưa biết mã lỗi/bản dựng; chờ §6 bước 1-3 |
| #458 (37) | Chưa có khung đã giải mã; sau §4 mới có đường giải, còn thiếu gói ký hiệu (§2) |
| #336, #328, #460 (13) | Cùng lớp ANR của `:libretrodroid` (PR #623 đã gộp), cần `adb bugreport`; chưa có |
| #404 (3) | Như #458 |
| #610 (1) | Nhãn `engine:azahar` trong thân issue là **nhãn cũ**; từ #627 báo cáo mới mang `engine:libretro:citra`. Thân issue chỉ được bot ghi đè khi có lượt mới ⇒ không căn cứ để đóng |
| #373 (1) | `unknown` = không có bản ghi `ApplicationExitInfo`; không có gì để sửa |

## 9. Giới hạn bằng chứng

- Không có `CRASH_ADMIN_TOKEN` ⇒ không đọc được báo cáo gốc (bản dựng, `reason`, `crumbs`, khung).
- BuildId `6adb3a8b…` là của bản dựng **17/11 07:45 GMT** đo hôm nay; bản kế tiếp sẽ khác — đó chính là hiện tượng đang mô tả, không phải hằng số.
- `Last-Modified` phụ thuộc máy chủ/CDN; nếu một ngày Cloudflare bỏ header, `info.txt` mất luôn dấu vết bản dựng.
- Chưa đo được tương quan bản dựng ↔ số lượt lỗi; chưa có khung nào đã giải mã cho nhóm `citra`.
