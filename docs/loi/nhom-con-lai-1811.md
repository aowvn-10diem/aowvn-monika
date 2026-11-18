# Nhóm "còn lại" (21 issue · 55 lượt): bảng ba câu cho từng nhóm, và một bản vá ở nhánh tải lõi nightly

Ngày 18/11/2026 · nhánh `loi/nhom-con-lai` · mã đọc `origin/main` **`5f3a67b6`** (configVersion 65, app 0.8.14).

Phạm vi: các nhóm **chưa có ai nhận** trong đợt 18/11 — `kirikiroid2-yuri/native`, `ppsspp/unknown`,
`desmume/anr`, `desmume/op-fail`, `snes9x` (3 loại), `mgba` (4 loại), `gambatte` (3 loại), `melonds` (2 loại),
`mkxp-z/native`, `unknown/native`. **Không** đụng `diag/Diagnostics.kt` (#655), `libretrodroid/src/main/cpp/**`
(#663), `runner/RetroActivity.kt` + `GLRetroView.kt` (#656), `azahar/*.kt` (#600), `config/`,
`app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/**`.

## 0. Số liệu, và một chỗ lệch so với phiếu giao

Đếm lại hôm nay bằng `gh issue list --state open --label crash-moi --limit 200` rồi đọc khối `aow-crash-v100`
trong thân từng issue:

| | Phiếu giao | Đếm thật 18/11 |
|---|---:|---:|
| Số issue | 19 | **21** |
| Số lượt | ~45 | **55** |

Lệch vì nhóm `desmume/anr` là **4** issue (#315, #522, #400, #333) và `snes9x` là **3** issue
(#459, #448, #418) — phiếu chỉ ghi lượt của issue đầu. Không issue nào trong 21 issue đã đóng; số lượt
trùng khít bảng §1 của `docs/loi/nhom-nho-1314.md` (14/11) ⇒ **không có lượt mới nào trong 4 ngày**.

Hai điều kiện vẫn thiếu như tài liệu 13–14/11: **không có `CRASH_ADMIN_TOKEN`** trong phiên này, và **không có
gói ký hiệu** cho 4 lõi buildbot. Vì vậy mọi cột "(a)" dưới đây là suy từ **mã nguồn + thân issue**, không từ
`reason`/`trace`/`device` của báo cáo gốc; chỗ nào chưa đo được thì ghi **[CHƯA KIỂM]**.

Dẫn chiếu `tệp:dòng` trong tệp này tính trên `5f3a67b6`, **khác** tài liệu 13–14/11 ở ba chỗ đã trôi:
`Diagnostics.collect` `:223` (`unknown` ở `:239`), `RetroActivity.onCoreFailure` `:193` (stage `core-error-N` ở
`:398`), `GLRetroView.onPause` `:377` (`runBounded` ở `:480`).

## 1. Ba câu cho từng nhóm (xếp theo lượt giảm dần)

| Nhóm (issue · lượt) | (a) Nguyên nhân khả năng nhất — `tệp:dòng` | (b) Đã sửa được chưa? ở đâu | (c) Còn thiếu gì để kết luận / đóng |
|---|---|---|---|
| **`kirikiroid2-yuri` / `native`** #385 · **13** (8 lần gửi, tái diễn 02/11) | Chết **trong `.so` của lõi**, không phải lớp chung: lõi đã ghim (`config/monika-config.json:1372` `6e61ce3-aow3` + `sha256`) nên cùng một binary; chữ ký `3ea3929f2471` chỉ gồm `module:symbol` của ≤ 3 khung. Khung nằm ở `libkrkr2yuri.so` hay ở `libretrodroid.so` thì **chưa đọc được** ⇒ cố ý **không nêu khung** | **Chưa** — và không nên sửa trước khi biết khung: khung trong `libretrodroid.so` là lớp #663/#620, không phải lỗi engine | `CRASH_ADMIN_TOKEN` để lấy id của 8 báo cáo + tải `symbols-kirikiri-16-arm64-v8a.zip` (**đã kiểm hôm nay**: release `engines-kirikiri-16` có asset này, 3 asset) rồi `scripts/crash-reports.sh <id> --symbols-dir … --symbolizer …` |
| **`ppsspp` / `unknown`** #416 · **8** (3 lần gửi) | `kind = "unknown"` chỉ sinh ở **một** nhánh `exit == null` (`diag/Diagnostics.kt:239`), mà `exitInfo` trả `null` khi API < 30 (`Diagnostics.kt:287`, `minSdk = 26`) ⇒ khả năng nhất là **native/ANR bị mất nhãn trên Android 8–10**, không phải lỗi lõi. Lõi `ppsspp` đã ghim + **có** symbols (kiểm hôm nay: `cores-ppsspp-1` có 2 asset `symbols-ppsspp-*`) | **Chưa** phần kết luận; nhưng từ lượt này **đã gỡ nút cổ chai dữ liệu**: `info.txt` ghi thêm `build_id=<hex>` (§2.2) nên báo cáo `native` mới sẽ mang BuildId của đúng `.so` đã cài | `device.sdk` của 3 báo cáo + một báo cáo **mới** (có `build_id`) để ghép `symbols-ppsspp-*`. Báo cáo cũ không cứu được vì app cũ chưa ghi `build_id` |
| **`snes9x`** #459 `anr` · 5, #448 `native` · 2, #418 `core-error` · 1 (tổng **8**) | Ba loại, ba nguyên nhân khác nhau trên **một** lõi: #459 = lớp ANR Main Thread chờ GLThread (`GLRetroView.kt:377` `onPause`, hạn chờ 2 000 ms ở `:480`); #448 = chết trong `snes9x_libretro_android.so` — lõi **trôi bản** (`config:996-1001`, `version = "1"`, không `artifacts`, không gói symbols); #418 = `core-error` qua cửa duy nhất `RetroActivity.kt:398-403` → `Diagnostics.recordCoreFailure` (`:615`), mã `0/1/-1` | #459: **đã có** #606 + #619 (bọc lời gọi JNI, hạn chờ 2 000 ms) — chưa có số đo sau phát hành. #418: **một phần, lượt này** — nhánh tải buildbot nay chặn `.so` không phải ELF / sai ABI ngay lúc cài (`runner/CoreManager.kt:136`), nên mã `0` do ZIP hỏng không còn đi qua. #448: **chưa** (không có ký hiệu thì không có gì để ghép) | `reason` của #418 (mã lỗi) — chỉ cần token; với #448 cần bản ghim + ký hiệu `snes9x` (V198/V210), hiện chưa có release nào như vậy |
| **`desmume` / `anr`** #315 · 4, #522 · 1, #400 · 1, #333 · 1 (tổng **7**) | **Cùng lớp ANR** đã phân tích ở #606/#619: Main Thread bị chặn khi `onPause()` chờ GLThread, và `LibretroDroid.pause()` chờ `coreLock`. `desmume` không có gì riêng: lõi tải buildbot (`config:821-836`), `desmume_num_cores = "2"` (`config:826`) là **luồng native trong lõi**, không giữ Java monitor — nó chỉ có thể **làm dài** đơn vị việc của GLThread. **[CHƯA KIỂM]** (chưa có `atrace`/`gfxinfo` nào) | **Chưa, và không nên sửa riêng**: 3/4 issue chỉ 1 lượt; bản sửa đúng là #606/#619 đã gộp, cần **đo lại sau phát hành** thay vì thêm mã | `trace`/`stage` của báo cáo (token) + ghim bản `desmume` (hiện `version = "1"`, hai máy cùng nhãn có thể chạy hai `.so`). #522 (1 lượt, 06/11) dưới mọi ngưỡng kết luận |
| **`mgba`** #403 `core-error` · 4, #516 `anr` · 1, #378 `unknown` · 1, #464 `op-fail` · 1 (tổng **7**) | Bốn nguyên nhân khác nhau, **không** phải lỗi lõi: #403 — lõi **đã ghim** (`config:897-916`, `cores-gb-gba-nes-1` + `sha256`) nên loại giả thuyết "trôi bản"; còn lại `.gba` hỏng/cắt cụt → `retro_load_game` trả `false` = mã `1`, hoặc `-1` từ `afterGameLoad`; #516 = lớp ANR; #378 = `unknown` như #416; #464 = `INSTALL_PACK` (`pack/PackManager.kt:173`) | #464: **đã phủ ở #626** (không triage lại). #403/#516/#378: **chưa** — #403 cần mã lỗi, hai ca còn lại là giới hạn dữ liệu | `reason` của #403 (mã `0/1/-1` là ba hướng sửa khác nhau); `device.sdk` của #378; `bước`+`op` của #464 |
| **`gambatte`** #513 `unknown` · 2, #402 `anr` · 2, #465 `op-fail` · 1 (tổng **5**) | Cùng hình dạng `mgba` (hai lõi nằm chung pack, cùng đã ghim — `config:918-948`): #513 = `unknown` (`Diagnostics.kt:239`), #402 = lớp ANR, #465 = op tải/cài lõi | #465 đã phủ ở #626; còn lại **chưa** | `device.sdk` (#513), `trace` (#402), `bước` (#465) — đều cần token |
| **`melonds`** #563 `native` · 3, #535 `anr` · 1 (tổng **4**) | `melonds` **không** phải lõi mặc định NDS (`config:45` là `melondsds`); nó chỉ được dùng khi người chơi bấm đổi lõi dự phòng (`RetroActivity.kt:202`) ⇒ giả thuyết **liên nhóm**: cùng game của #567 thất bại ở `melondsds` rồi sập tiếp ở `melonds`. #535 thì **đã được nêu đích danh** trong mã: chú thích `GLRetroView.kt:479` liệt kê `(ffdfa861, #318, #529, #533, #535)` là chữ ký ANR mà hạn chờ 2 000 ms nhắm tới | #535: **đã có theo thiết kế** (#619). #563: **chưa** — lõi buildbot (`config:839-849`), không gói symbols, không retrace được | `device` + tên game của 3 báo cáo #563 để đối chiếu #567; ghim bản `melonds` |
| **`mkxp-z` / `native`** #613 · **1** | Chết **sau khi engine đã vào game**: đây là issue duy nhất trong 21 issue có dòng `Thành phần`, và `diag/Components.kt:30` lấy `engine:rgss` từ `session.kind == "rgss"`. Lõi ghim (`config:1387` `engines-rgss-6`) | **Chưa** — release `engines-rgss-6` và `engines-rgss-7` hôm nay **chỉ có 2 asset, không có `symbols-*`** (đã kiểm); `symbols-rgss-*` vẫn là bản nháp theo cổng giấy phép G8 | Gói `symbols-rgss` không nháp (build-rgss + G8) **và** token. Lưu ý ghim đang là bản 6, repo đã có bản 7 — phải đối chiếu đúng bản đang cài |
| **`unknown` / `native`** #387 · **1** | Chết **trước khi có phiên** nên không có `core` ⇒ không biết `.so` nào; không thể sửa bằng ký hiệu | **Chưa** — không có đích để vá | `device` + `log`/`reason` của báo cáo (token là điều kiện duy nhất) |
| **`desmume` / `op-fail`** #368 · **1** | Lỗi ở **thao tác tải/cài lõi**, không phải lõi: `desmume` không khai `abis`/`artifacts` (`config:821-824`) ⇒ đi nhánh buildbot của `CoreManager.kt:107-141`; op thứ hai khả dĩ là `INSTALL_PACK` (`PackManager.kt:173`). Trước bản vá lượt này, một ZIP buildbot cắt cụt vẫn được cài rồi mới `dlopen` lỗi | **Một phần, lượt này**: `CoreManager.kt:136` kiểm ELF/ABI trước `renameTo` ⇒ ZIP hỏng thành lỗi cài lõi có thông báo (`RetroActivity.kt:317` bọc trong `Diagnostics.opSuspend(OPEN_GAME)`) thay vì `core-error` mã 0 | `bước` + `op` trong báo cáo #368 để biết là `tai-xuong` hay `cai-goi` |

## 2. Hai bản vá trong lượt (nhánh tải lõi nightly, và `build_id` của lõi đã cài)

### 2.1 Chặn `.so` hỏng / sai ABI ở nhánh tải buildbot

**Vấn đề.** `CoreManager.ensureCoreLocked` có **hai** nhánh tải lõi:

- nhánh có `artifacts` (lõi đã ghim): kiểm `sha256`, giải nén, rồi `PackTransaction.validate` — kiểm cả ABI của
  mọi `.so`;
- nhánh buildbot `nightly/latest` (nhánh của `desmume`, `snes9x`, `melonds`, `melondsds`): chỉ lấy entry `.so`
  **đầu tiên** trong ZIP rồi `renameTo` (`CoreManager.kt:107-141`), **không** kiểm gì.

Hệ quả đã ghi ở `docs/loi/loi-khac-1313.md` §4: một ZIP cắt cụt / một `.so` sai ABI vẫn được "cài", và lỗi chỉ
lộ ra khi `dlopen` ⇒ người chơi thấy `core-error` **mã 0**, còn chẩn đoán thì mất manh mối vì thân issue không
mang mã.

**Sửa (tối thiểu, trong vùng cho phép).**

| Tệp | Thay đổi |
|---|---|
| `app/src/main/java/vn/aow/monika/pack/PackTransaction.kt:173` | Tách vòng kiểm ELF trong `validate` thành `fun validateElf(lib: File, abi: String)` (magic ELF + lớp 32/64 + `e_machine` theo ABI); `validate` gọi lại hàm này ở `:165` — **không đổi hành vi** của nhánh ghim |
| `app/src/main/java/vn/aow/monika/runner/CoreManager.kt:137` | Nhánh buildbot gọi `PackTransaction.validateElf(tmp, abi)` **trước** `renameTo(so)` |

Không đổi định dạng config, không đổi thông điệp lỗi cũ, không thêm phụ thuộc. Lỗi cài lõi vẫn đi đúng đường
cũ: `RetroActivity.kt:317` (`opSuspend(OPEN_GAME)`) → `op-fail` có `bước=tai-loi:<core>`, người chơi thấy
"Không tải được lõi giả lập".

**Test (mới, chạy trên JVM).**

- `app/src/test/java/vn/aow/monika/pack/PackTransactionTest.kt` —
  `validateElfAcceptsMatchingAbiAndRejectsWrongOnes`: nhận ELF arm64/v7a đúng, **từ chối** v7a trên máy arm64,
  x86 trên arm64, trang HTML ghi đè tên `.so`, ELF cắt còn 12 byte, tệp rỗng.
- `app/src/test/java/vn/aow/monika/runner/CorePreparationCacheTest.kt` —
  `nonElfCorePayloadIsRejectedInsteadOfInstalled`: máy chủ giả trả "`.so`" không có magic ELF ⇒ `ensureCore`
  ném `IOException("… sai định dạng/ABI …")`, `isReady` vẫn `false`, **không** có `.so` nào được ghi vào
  `filesDir/cores/<id>/`.

Fixture `.so` của 3 ca cũ trong `CorePreparationCacheTest` được đổi từ `ByteArray(64){1}` thành một ELF arm64
tối thiểu (`ElfFixture.arm64()`), vì từ bản vá này "một `.so` tải về" phải là ELF thật — bản thân 3 ca đó kiểm
cache tải lại, không kiểm định dạng.

### 2.2 Ghi `build_id` của lõi đã cài vào `info.txt`

**Vấn đề.** `info.txt` (đính vào mọi báo cáo qua `Diagnostics.coreInfo`, `Diagnostics.kt:115`) chỉ có
`cfg v… · <abi> · …KB · tải <ngày> · bản dựng <Last-Modified>`. Không có `build_id` nên khi có báo cáo `native`
thì **không biết máy đó chạy `.so` nào** — gói ký hiệu đã có trên release (`symbols-ppsspp-*` trong
`cores-ppsspp-1`) cũng không dùng được, vì `scripts/crash-reports.sh` chỉ ghép khung có **PC + BuildId**.

**Sửa.** `PackTransaction.buildId(lib)` đọc segment `PT_NOTE` của `.so` (ELF32/ELF64, chỉ little-endian; đọc
`e_phoff/e_phentsize/e_phnum` rồi duyệt `p_type == PT_NOTE = 4`), lấy note `name = "GNU"`, `type = 3`
(`NT_GNU_BUILD_ID`), trả hex thường. Mọi bước đều kiểm biên và trả `null` thay vì ném: lõi đã strip (phần lớn lõi
buildbot) thì **không** ghi dòng nào. `CoreManager.writeCoreInfo` ghi `info.txt` cho **cả hai** nhánh cài:

| Trước | Sau |
|---|---|
| dòng `cfg v… · abi · KB · tải … · bản dựng …` | y hệt, giữ nguyên từng chữ (bản cũ đọc lại vẫn như trước) |
| — | thêm **một** dòng `build_id=<hex>` khi `.so` có note GNU |

Ghi ở **cả hai** nhánh là có chủ ý: `ppsspp` (214 lượt `native`) nằm ở nhánh `artifacts` (`config:977-985`), nên
chỉ sửa nhánh buildbot thì không mở khoá được nhóm đó.

**Test.**

- `PackTransactionTest.buildIdReadsGnuNoteAndIsNullWhenStripped` — `.so` có note ⇒ đúng hex; không note / tệp
  rỗng / tệp không tồn tại ⇒ `null`, không ném.
- `CorePreparationCacheTest.installedCoreWritesBuildIdLineToInfoTxt` — cài lõi xong, `info.txt` có đúng dòng
  `build_id=<hex>` **và** dòng đầu vẫn đúng dạng cũ.
- `CorePreparationCacheTest.strippedCoreWritesNoBuildIdLine` — `.so` strip ⇒ `info.txt` đúng **một** dòng, không
  có `build_id`, không ném.
- `CorePreparationCacheTest.legacyInfoFileWithoutBuildIdStillReads` — `info.txt` bản cũ (chỉ một dòng `cfg v…`)
  vẫn đọc nguyên văn, và lần mở lõi thứ hai (lõi đã sẵn) **không** ghi đè tệp.

**Kết quả đo (18/11, Windows, `local.properties` chỉ thêm cục bộ, không tính vào git).**
| Lượt chạy | Lệnh | Kết quả |
|---|---|---|
| Gốc (`5f3a67b6`, không có bản vá) | `:app:testGithubDebugUnitTest` | **19 đỏ** — đúng bộ 19 ca đã ghi ở `docs/CI-CONG-KIEM.md` §3 (6 `RarTest`, 6 `DeviceScannerTest`, 2 `CorePreparationCacheTest` vì `renameTo` không ghi đè trên Windows, 5 ca đường dẫn/symlink) |
| Gốc | `:app:testPlayDebugUnitTest` | **1 đỏ** — `PlayAskGroupTest.configuredGroupReceivesReadableImageAndQuestionThroughChooser` (`chooser == null`) |
| Có bản vá | `./gradlew testDebugUnitTest --continue` | `1272 tests completed` — github **19 đỏ** (đúng bộ trên, **không ca mới**), play **1 đỏ** (đúng ca trên) |
| Có bản vá | `PackTransactionTest` / `CorePreparationCacheTest` | 11/11 xanh / 7 ca, 2 đỏ **đúng 2 ca đã có sẵn** ở trên; 4 ca mới xanh |

## 3. Việc tiếp theo (rẻ → đắt)

1. **Giải mã #385** — đủ điều kiện nhất trong 21 issue: chỉ cần token + `symbols-kirikiri-16-arm64-v8a.zip`
   (đã có trên release). Khung trong `libkrkr2yuri.so` ⇒ mở PR mã; khung trong `libretrodroid.so` ⇒ chuyển
   sang lớp #663/#620, **không** mở việc mới cho engine.
2. **Tách #416** bằng `device.sdk` của 3 báo cáo (mục 1) — quyết định nó có phải "native mất nhãn" hay không.
3. **Đọc `reason`** của #403, #418 (mã `0/1/-1`), `bước` của #368, #464, #465 — mỗi mã là một hướng sửa khác
   nhau; không cần symbols, chỉ cần token.
4. **Đo lại lớp ANR sau v0.8.13/v0.8.14**: #315, #333, #400, #522, #459, #402, #516, #535 đều **mở trước**
   #606 và **không** có `(+N)` sau đó. Vì tất cả ≤ 5 lượt (dưới ngưỡng PATCH `delta ≥ 5` của bot), im lặng
   **không** phải bằng chứng đã hết — cần `adb bugreport` khi có lượt mới.
5. **Ghim bản + ký hiệu** cho `snes9x`, `desmume`, `melonds` (V198/V210): có hai thứ đó thì #448, #563 và 4
   issue `desmume/anr` mới đọc được khung/lõi. Đây là việc của đặc tả, không phải của lượt này.
6. **`mkxp-z`**: bổ sung bước đính `symbols-rgss-*` vào release **không nháp** (cổng G8) — ngoài phạm vi mã.

## 4. Issue đóng được trong lượt này: **0**

Không issue nào trong 21 issue có bản vá **đã gộp**, và không issue nào có bằng chứng dứt điểm:

- 12 issue là `anr`/`unknown` — thiếu `trace`/`device`; bản sửa #606/#619 mới chỉ có trên một bản phát hành,
  chưa có số đo sau phát hành.
- 4 issue `native` (#385, #448, #563, #613) — thiếu khung (token + symbols). #385 thì **có** symbols nhưng
  chưa chạy, tức "chưa kiểm", không phải "đã sửa".
- 3 issue `core-error` (#403, #418) — thiếu mã lỗi.
- 2 issue `op-fail` còn lại (#368, #464/#465 đã phủ ở #626) — thiếu `bước`/`op`; bản vá hôm nay chỉ đóng **một
  đường** sinh lỗi (ZIP buildbot hỏng), không đóng issue.

`#418` và `#368` là hai issue **gần** đóng nhất: sau khi bản vá này lên main, nếu người dùng gửi báo cáo mới mà
vẫn `core-error`/`op-fail` thì mã lỗi và `bước` sẽ chỉ ra đúng nhánh — khi đó mới đủ để đóng, không phải bây giờ.

## 5. Giới hạn bằng chứng

- Không có `CRASH_ADMIN_TOKEN`: **21/21** issue chỉ đọc được thân issue. Cột "(a)" là suy luận từ mã, không
  phải từ `reason`/`trace`/`crumbs`.
- `Lượt` là `count` **cộng dồn lịch sử**; `Máy` (số khoá `seen`) là số **lần gửi**, không phải số thiết bị
  (`scripts/crash-issue-bot.py:76`). Bot chỉ PATCH thân issue khi `delta ≥ 5` (`:118`) ⇒ "không tái diễn" ở
  đây nghĩa là "chưa đủ 5 lượt mới".
- Tình trạng release/symbols đo **hôm nay**: `engines-kirikiri-16` 3 asset (có `symbols-kirikiri-16-arm64-v8a.zip`),
  `cores-ppsspp-1` 10 asset (có 2 `symbols-ppsspp-*`), `cores-gb-gba-nes-1` **không** symbols, `engines-rgss-6`
  và `engines-rgss-7` mỗi bản 2 asset **không** symbols. Release có thể đổi — lượt sau phải kiểm lại.
- 4 lõi `desmume`/`snes9x`/`melonds`/`melondsds` vẫn `version = "1"` trỏ buildbot `nightly/latest` ⇒ cùng một
  chữ ký có thể đến từ **nhiều** `.so`; "tái diễn" không chứng minh cùng một lỗi trong cùng một binary.
- Nhận định "`desmume_num_cores` chỉ làm dài việc GLThread" là **suy từ mã + config**, chưa có `atrace`.
  **[CHƯA KIỂM]**
- 19 ca đỏ JVM trên Windows là **khác biệt môi trường** đã ghi ở `docs/CI-CONG-KIEM.md` §3 (đo lại 16/11:
  1256 test / 19 đỏ), không phải hỏng do bản vá này; ca thứ 20 (`PlayAskGroupTest`) thuộc flavor `play` và đỏ
  **cả trước và sau** bản vá (đã chạy đối chứng trên cây sạch).
